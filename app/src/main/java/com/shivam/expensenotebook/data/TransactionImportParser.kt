package com.shivam.expensenotebook.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.zip.ZipInputStream

object TransactionImportParser {
    private const val MAX_ENTRY_BYTES = 20 * 1024 * 1024
    private val knownCategories = setOf(
        "Random Expense", "Food & Dining", "Groceries", "Home", "Rent", "Utilities & Bills",
        "EMI", "Transportation", "Fuel", "Shopping", "Health & Medicine", "Education",
        "Entertainment", "Subscriptions", "Travel", "Personal Care", "Family", "Gifts", "Work", "Other"
    )

    fun parse(context: Context, uri: Uri, sourceName: String, ownIdentifiers: String): ImportPreview {
        val extension = sourceName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        return when {
            extension == "pdf" || mimeType == "application/pdf" ->
                parsePdf(context, uri, sourceName, ownIdentifiers)
            extension == "xlsx" || mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                parseXlsx(context, uri, sourceName, ownIdentifiers)
            else -> error("Choose a PDF or .xlsx file.")
        }
    }

    private fun parsePdf(
        context: Context,
        uri: Uri,
        sourceName: String,
        ownIdentifiers: String
    ): ImportPreview {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                PDFTextStripper().apply { setSortByPosition(true) }.getText(document)
            }
        } ?: error("The selected PDF could not be opened.")
        if (text.isBlank()) error("No readable text was found. Scanned/image-only PDFs are not supported.")

        val rows = mutableListOf<ParsedRow>()
        val identifiers = ownIdentifiers.split(',', '\n', ';').map(String::trim).filter(String::isNotBlank)
        var credits = 0
        var unparsed = 0
        var currentLayout: PdfLayout? = null
        var transactionLayout: PdfLayout? = null
        val transactionLines = mutableListOf<String>()

        fun flushTransaction() {
            if (transactionLines.isEmpty()) return
            when (val parsed = parsePdfTransaction(transactionLines, transactionLayout, identifiers)) {
                is PdfTransactionResult.Expense -> rows += parsed.row
                PdfTransactionResult.Credit -> credits++
                PdfTransactionResult.Unparsed -> unparsed++
            }
            transactionLines.clear()
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            detectPdfLayout(line)?.let { detected ->
                flushTransaction()
                currentLayout = detected
                return@forEach
            }

            if (DATE_AT_START.containsMatchIn(line.trimStart())) {
                flushTransaction()
                transactionLayout = currentLayout
                transactionLines += line
            } else if (transactionLines.isNotEmpty() && line.isNotBlank() && !looksLikePdfFooter(line)) {
                transactionLines += line
            }
        }
        flushTransaction()
        return finalizeRows(sourceName, rows, ownIdentifiers, credits, unparsed)
    }

    private fun parsePdfTransaction(
        lines: List<String>,
        layout: PdfLayout?,
        ownIdentifiers: List<String>
    ): PdfTransactionResult {
        val combined = lines.joinToString(" ") { it.trim() }.replace(Regex("\\s+"), " ")
        val dateMatch = DATE_AT_START.find(combined) ?: return PdfTransactionResult.Unparsed
        val date = parseDate(dateMatch.value.trim()) ?: return PdfTransactionResult.Unparsed

        val narration = layout?.description?.let { slicePdfColumn(lines, it, layout) }
            .orEmpty().ifBlank { combined.substring(dateMatch.range.last + 1).trim() }
        val senderFromColumn = layout?.sender?.let { slicePdfColumn(lines, it, layout) }.orEmpty()
        val sender = inferCounterparty(senderFromColumn, allowPlainName = true).ifBlank {
            extractSender(narration)
        }
        val receiverCell = layout?.receiver?.let { slicePdfColumn(lines, it, layout) }.orEmpty()
        val datedLine = listOf(lines.first())
        val debit = layout?.debit?.let { parseFirstAmountMinor(slicePdfColumn(datedLine, it, layout)) }
        val credit = layout?.credit?.let { parseFirstAmountMinor(slicePdfColumn(datedLine, it, layout)) }
        val amountCell = layout?.amount?.let { parseFirstAmountMinor(slicePdfColumn(datedLine, it, layout)) }
        val type = layout?.type?.let { slicePdfColumn(datedLine, it, layout) }.orEmpty().uppercase(Locale.ROOT)

        if ((credit ?: 0L) > 0L && (debit ?: 0L) <= 0L) return PdfTransactionResult.Credit
        if (CREDIT_WORD.containsMatchIn(type) && !DEBIT_WORD.containsMatchIn(type)) {
            return PdfTransactionResult.Credit
        }

        val amount = if (layout != null) {
            when {
                debit != null && debit > 0L -> debit
                amountCell != null && amountCell < 0L -> kotlin.math.abs(amountCell)
                amountCell != null && amountCell > 0L && DEBIT_WORD.containsMatchIn(type) -> amountCell
                else -> null
            }
        } else {
            findPdfDebitAmount(combined, dateMatch.range.last + 1)
        } ?: return PdfTransactionResult.Unparsed

        val receiverFromColumn = inferCounterparty(receiverCell, allowPlainName = true)
        val receiver = receiverFromColumn.ifBlank {
            extractOutgoingReceiver(narration, sender, ownIdentifiers)
        }
        return PdfTransactionResult.Expense(
            ParsedRow(
                date = date,
                amountMinor = amount,
                description = narration,
                sender = sender,
                receiver = receiver,
                senderMatchText = senderFromColumn.ifBlank { sender },
                receiverMatchText = receiverCell.ifBlank { receiver },
                category = null
            )
        )
    }

    private fun detectPdfLayout(line: String): PdfLayout? {
        val upper = line.uppercase(Locale.ROOT)
        if (DATE_AT_START.containsMatchIn(line.trimStart())) return null
        fun position(vararg labels: String): Int? = labels.map(upper::indexOf).filter { it >= 0 }.minOrNull()

        val date = position("TRANSACTION DATE", "TXN DATE", "POSTING DATE", "VALUE DATE", "DATE")
        val description = position("NARRATION", "DESCRIPTION", "PARTICULARS", "TRANSACTION DETAILS", "DETAILS", "REMARKS")
        val sender = position(
            "SENDER VPA", "PAYER VPA", "FROM VPA", "SENDER ADDRESS", "PAYER ADDRESS",
            "REMITTER NAME", "REMITTER", "SENDER", "PAYER", "FROM ACCOUNT", "DEBITED FROM",
            "DEBIT ACCOUNT", "ACCOUNT HOLDER"
        )
        val explicitReceiver = position(
            "RECEIVER VPA", "PAYEE VPA", "BENEFICIARY VPA", "TO VPA", "RECEIVER ADDRESS",
            "PAYEE ADDRESS", "BENEFICIARY ADDRESS", "RECEIVER", "PAYEE", "BENEFICIARY",
            "RECIPIENT", "MERCHANT NAME", "MERCHANT", "TO ACCOUNT",
            "CREDIT ACCOUNT", "COUNTERPARTY"
        )
        val receiver = explicitReceiver ?: if (!upper.contains("SENDER VPA") && !upper.contains("PAYER VPA")) {
            position("VPA")
        } else null
        val debit = position("DEBIT AMOUNT", "WITHDRAWAL AMOUNT", "WITHDRAWAL", "DR AMOUNT")
            ?: if (!upper.contains("DEBIT ACCOUNT")) position("DEBIT") else null
        val credit = position("CREDIT AMOUNT", "DEPOSIT AMOUNT", "DEPOSIT", "CR AMOUNT")
            ?: if (!upper.contains("CREDIT ACCOUNT")) position("CREDIT") else null
        val type = position("TRANSACTION TYPE", "DR/CR", "CR/DR", "TYPE")
        val balance = position("CLOSING BALANCE", "RUNNING BALANCE", "AVAILABLE BALANCE", "BALANCE")
        val explicitAmount = position("TRANSACTION AMOUNT", "TXN AMOUNT")
        val amount = explicitAmount ?: if (debit == null && credit == null) position("AMOUNT") else null
        val hasMoneyDirection = debit != null || credit != null || amount != null || type != null
        val hasTransactionDetail = description != null || receiver != null
        if (date == null || !hasMoneyDirection || !hasTransactionDetail) return null
        return PdfLayout(date, description, sender, receiver, debit, credit, amount, type, balance)
    }

    private fun slicePdfColumn(lines: List<String>, start: Int, layout: PdfLayout): String {
        val end = layout.starts().filter { it > start }.minOrNull()
        return lines.mapNotNull { line ->
            if (line.length <= start) null
            else line.substring(start, (end ?: line.length).coerceAtMost(line.length)).trim().takeIf(String::isNotBlank)
        }.joinToString(" ")
    }

    private fun parseFirstAmountMinor(value: String): Long? {
        STRICT_MONEY_TOKEN.find(value)?.value?.let { return parseAmountMinor(it) }
        val tokens = MONEY_TOKEN.findAll(value).map { it.value }.toList()
        val single = tokens.singleOrNull() ?: return null
        val digits = single.filter(Char::isDigit)
        return if (digits.length <= 7) parseAmountMinor(single) else null
    }

    private fun looksLikePdfFooter(line: String): Boolean {
        val upper = line.uppercase(Locale.ROOT)
        return upper.startsWith("PAGE ") || upper.contains("END OF STATEMENT") ||
            upper.contains("THIS IS A COMPUTER GENERATED")
    }

    private fun findPdfDebitAmount(line: String, descriptionStart: Int): Long? {
        val body = line.substring(descriptionStart)
        val explicitDebit = DEBIT_WORD.containsMatchIn(body.uppercase(Locale.ROOT))
        val signedDebit = NEGATIVE_AMOUNT.findAll(body).lastOrNull()?.value
        if (!explicitDebit && signedDebit == null) return null

        val direct = DEBIT_AMOUNT.find(body)?.groups?.get(1)?.value
            ?: AMOUNT_DEBIT.find(body)?.groups?.get(1)?.value
            ?: signedDebit
        if (direct != null && isPlausibleMoneyToken(direct)) {
            return parseAmountMinor(direct)?.let { kotlin.math.abs(it) }
        }

        return null
    }

    private fun isPlausibleMoneyToken(value: String): Boolean {
        val trimmed = value.trim()
        if (STRICT_MONEY_TOKEN.matches(trimmed)) return true
        return trimmed.filter(Char::isDigit).length <= 7
    }

    private fun parseXlsx(
        context: Context,
        uri: Uri,
        sourceName: String,
        ownIdentifiers: String
    ): ImportPreview {
        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val wanted = entry.name == "xl/sharedStrings.xml" ||
                        Regex("xl/worksheets/sheet\\d+\\.xml").matches(entry.name)
                    if (wanted) {
                        val bytes = zip.readBytesLimited(MAX_ENTRY_BYTES)
                        entries[entry.name] = bytes
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("The selected spreadsheet could not be opened.")

        val sheetEntry = entries.keys.filter { it.startsWith("xl/worksheets/sheet") }.minOrNull()
            ?: error("No worksheet was found in this .xlsx file.")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val table = parseWorksheet(entries.getValue(sheetEntry), sharedStrings)
        if (table.isEmpty()) error("The spreadsheet does not contain readable rows.")

        val headerIndex = table.take(30).indexOfFirst { row -> identifyColumns(row).date != null }
        if (headerIndex < 0) error("Could not find a transaction header row. Include Date and Debit/Amount columns.")
        val columns = identifyColumns(table[headerIndex])
        if (columns.date == null || (columns.debit == null && columns.amount == null)) {
            error("The spreadsheet needs Date and Debit columns, or Date, Amount and Type columns.")
        }

        val identifiers = ownIdentifiers.split(',', '\n', ';').map(String::trim).filter(String::isNotBlank)
        val rows = mutableListOf<ParsedRow>()
        var credits = 0
        var unparsed = 0
        table.drop(headerIndex + 1).forEach { row ->
            if (row.all(String::isBlank)) return@forEach
            val date = cell(row, columns.date)?.let(::parseDate)
            if (date == null) {
                unparsed++
                return@forEach
            }
            val debit = cell(row, columns.debit)?.let(::parseAmountMinor)?.let { kotlin.math.abs(it) }
            val credit = cell(row, columns.credit)?.let(::parseAmountMinor)?.let { kotlin.math.abs(it) }
            val amountValue = cell(row, columns.amount)?.let(::parseAmountMinor)
            val type = cell(row, columns.type).orEmpty().uppercase(Locale.ROOT)
            val outgoing = when {
                debit != null && debit > 0L -> debit
                credit != null && credit > 0L -> null
                amountValue != null && amountValue < 0L -> kotlin.math.abs(amountValue)
                amountValue != null && amountValue > 0L && DEBIT_WORD.containsMatchIn(type) -> amountValue
                else -> null
            }
            if (outgoing == null) {
                if ((credit ?: 0L) > 0L || CREDIT_WORD.containsMatchIn(type)) credits++ else unparsed++
                return@forEach
            }

            val description = cell(row, columns.description).orEmpty().trim()
            val parties = extractPartiesFromStructuredRow(row, columns, description, identifiers)
            val sourceCategory = cell(row, columns.category).orEmpty().trim()
            val category = knownCategories.firstOrNull { it.equals(sourceCategory, ignoreCase = true) }
                ?: categorize("$sourceCategory ${parties.receiver} $description")
            rows += ParsedRow(
                date = date,
                amountMinor = outgoing,
                description = description,
                sender = parties.sender,
                receiver = parties.receiver,
                senderMatchText = parties.senderMatchText,
                receiverMatchText = parties.receiverMatchText,
                category = category
            )
        }
        return finalizeRows(sourceName, rows, ownIdentifiers, credits, unparsed)
    }

    private fun finalizeRows(
        sourceName: String,
        rows: List<ParsedRow>,
        ownIdentifiers: String,
        credits: Int,
        unparsed: Int
    ): ImportPreview {
        val identifiers = ownIdentifiers.split(',', '\n', ';').map(String::trim).filter(String::isNotBlank)
        val candidates = rows.map { row ->
            val identity = row.receiver.ifBlank { inferCounterparty(row.description, allowPlainName = false) }
            val (groupKey, groupLabel) = groupIdentity(identity, row.description)
            val ownTransfer = matchesOwnAccount(row.senderMatchText, identifiers) &&
                matchesOwnAccount(row.receiverMatchText.ifBlank { identity }, identifiers)
            val category = row.category ?: categorize("$identity ${row.description}")
            ImportCandidate(
                importKey = hashOf(row.date.toEpochDay(), row.amountMinor, row.description, identity),
                groupKey = groupKey,
                groupLabel = groupLabel,
                isLikelyOwnTransfer = ownTransfer,
                amountMinor = row.amountMinor,
                dateEpochDay = row.date.toEpochDay(),
                note = row.description.take(120),
                sender = row.sender.take(80),
                receiver = identity.take(80),
                categoryName = category
            )
        }.distinctBy { it.importKey }
        val groupedCandidates = candidates.groupBy(ImportCandidate::groupKey).values.flatMap { group ->
            val groupCategory = group.groupingBy(ImportCandidate::categoryName).eachCount()
                .maxByOrNull { it.value }?.key ?: "Other"
            group.map { it.copy(categoryName = groupCategory) }
        }.sortedWith(compareByDescending<ImportCandidate> { it.dateEpochDay }.thenBy { it.groupLabel })
        return ImportPreview(
            sourceName,
            groupedCandidates,
            groupedCandidates.count(ImportCandidate::isLikelyOwnTransfer),
            credits,
            unparsed
        )
    }

    private fun matchesOwnAccount(party: String, identifiers: List<String>): Boolean {
        if (party.isBlank()) return false
        val normalizedParty = normalize(party)
        val partyDigits = party.filter(Char::isDigit)
        val ignoredNames = setOf("upi", "bank", "account", "receiver", "payee", "beneficiary")
        return identifiers.any { identifier ->
            val trimmed = identifier.trim()
            val configuredDigits = trimmed.filter(Char::isDigit)
            val lastFourMatch = configuredDigits.length >= 4 &&
                partyDigits.contains(configuredDigits.takeLast(4))
            val configuredName = trimmed.filter(Char::isLetter).lowercase(Locale.ROOT)
            val nameMatch = configuredName.length >= 3 && configuredName !in ignoredNames &&
                normalizedParty.contains(configuredName)
            lastFourMatch || nameMatch
        }
    }

    private fun categorize(value: String): String {
        val text = value.lowercase(Locale.ROOT)
        fun has(vararg words: String) = words.any(text::contains)
        return when {
            has("rent", "landlord") -> "Rent"
            has("emi", "loan repayment") -> "EMI"
            has("swiggy", "zomato", "restaurant", "cafe", "coffee", "food", "domino", "pizza") -> "Food & Dining"
            has("grocery", "grofers", "bigbasket", "blinkit", "zepto", "dmart", "supermarket") -> "Groceries"
            has("transport", "uber", "ola", "rapido", "metro", "irctc", "railway", "bus", "cab", "auto ride") -> "Transportation"
            has("petrol", "diesel", "fuel", "indian oil", "iocl", "bpcl", "hpcl", "shell") -> "Fuel"
            has("electricity", "broadband", "recharge", "mobile bill", "water bill", "gas bill", "airtel", "jio", "vodafone") -> "Utilities & Bills"
            has("amazon", "flipkart", "myntra", "ajio", "shopping", "retail", "store") -> "Shopping"
            has("hospital", "clinic", "pharmacy", "medical", "medicine", "apollo", "practo") -> "Health & Medicine"
            has("school", "college", "university", "course", "tuition", "udemy", "education") -> "Education"
            has("netflix", "prime video", "hotstar", "spotify", "youtube premium", "subscription") -> "Subscriptions"
            has("cinema", "movie", "pvr", "inox", "gaming", "entertainment") -> "Entertainment"
            has("hotel", "flight", "airlines", "makemytrip", "booking.com", "travel") -> "Travel"
            has("salon", "spa", "barber", "personal care") -> "Personal Care"
            has("family") -> "Family"
            has("gift", "flowers") -> "Gifts"
            has("office", "work expense", "business") -> "Work"
            has("maintenance", "furniture", "home") -> "Home"
            else -> "Other"
        }
    }

    private fun extractPartiesFromStructuredRow(
        row: List<String>,
        columns: Columns,
        description: String,
        ownIdentifiers: List<String>
    ): ParsedParties {
        val senderFromColumns = listOfNotNull(cell(row, columns.sourceName), cell(row, columns.sourceAccount))
            .joinToString(" ")
        val sender = inferCounterparty(senderFromColumns, allowPlainName = true).ifBlank {
            extractSender(description)
        }
        val receiverFromColumn = cell(row, columns.receiver).orEmpty()
        val explicitReceiver = inferCounterparty(receiverFromColumn, allowPlainName = true)
        val receiver = explicitReceiver.ifBlank {
            extractOutgoingReceiver(description, sender, ownIdentifiers)
        }
        return ParsedParties(
            sender = sender,
            receiver = receiver,
            senderMatchText = senderFromColumns.ifBlank { sender },
            receiverMatchText = receiverFromColumn.ifBlank { receiver }
        )
    }

    private fun extractSender(narration: String): String {
        val cleaned = narration.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return ""
        val markedValue = SENDER_MARKER.find(cleaned)?.groups?.get(1)?.value.orEmpty()
        if (markedValue.isBlank()) return ""
        return inferCounterparty(markedValue.replace(TRAILING_MONEY, "").trim(), allowPlainName = true)
    }

    private fun inferCounterparty(value: String, allowPlainName: Boolean): String {
        val cleaned = value.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return ""
        UPI_ID.find(cleaned)?.value?.let { return it }
        MASKED_ID.find(cleaned)?.value?.let { return it }
        NAME_SLASH_NAME.find(cleaned)?.value?.trim()?.takeIf { candidate ->
            candidate.split('/').none { it.trim().lowercase(Locale.ROOT) in IGNORED_COUNTERPARTY_TOKENS }
        }?.let { return it }
        NAMED_COUNTERPARTY.find(cleaned)?.groups?.get(1)?.value?.trim()?.let { return it }

        cleaned.split('/', '|', ':')
            .map(String::trim)
            .firstOrNull { token ->
                val simple = token.lowercase(Locale.ROOT)
                token.length in 3..60 && token.any(Char::isLetter) && simple !in IGNORED_COUNTERPARTY_TOKENS &&
                    token.none(Char::isDigit) && !MONEY_TOKEN.matches(token)
            }?.let { return it }

        return if (allowPlainName && cleaned.length <= 80) cleaned else ""
    }

    private fun extractOutgoingReceiver(
        narration: String,
        sender: String,
        ownIdentifiers: List<String>
    ): String {
        val cleaned = narration.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return ""

        RECEIVER_MARKER.findAll(cleaned).forEach { match ->
            val markedValue = match.groups[1]?.value.orEmpty()
            val candidate = inferCounterparty(markedValue, allowPlainName = false).ifBlank {
                inferCounterparty(markedValue.replace(TRAILING_MONEY, "").trim(), allowPlainName = true)
            }
            if (candidate.isNotBlank() && !sameParty(candidate, sender)) return candidate
        }

        UPI_ID.findAll(cleaned).map { it.value }.toList().asReversed().firstOrNull { candidate ->
            !sameParty(candidate, sender)
        }?.let { return it }
        MASKED_ID.findAll(cleaned).map { it.value }.toList().asReversed().firstOrNull { candidate ->
            !sameParty(candidate, sender)
        }?.let { return it }

        MERCHANT_NARRATION.find(cleaned)?.groups?.get(1)?.value?.trim()?.takeIf { candidate ->
            !sameParty(candidate, sender)
        }?.let { return it }

        val tokens = cleaned.split('/', '|', ':', '-')
            .map(String::trim)
            .filter(String::isNotBlank)
        val directionIndex = tokens.indexOfFirst { token ->
            token.equals("DR", true) || token.equals("DEBIT", true) || token.equals("TO", true)
        }
        val candidateTokens = if (directionIndex >= 0) tokens.drop(directionIndex + 1) else tokens
        val humanCandidates = candidateTokens.filter { token ->
            val simple = token.lowercase(Locale.ROOT)
            token.length in 3..60 && token.any(Char::isLetter) && token.none(Char::isDigit) &&
                simple !in IGNORED_COUNTERPARTY_TOKENS && !sameParty(token, sender) &&
                !MONEY_TOKEN.matches(token)
        }
        humanCandidates.firstOrNull { candidate -> !matchesOwnAccount(candidate, ownIdentifiers) }
            ?.let { return it }
        humanCandidates.lastOrNull()?.let { return it }
        return ""
    }

    private fun sameParty(first: String, second: String): Boolean {
        val left = normalize(first)
        val right = normalize(second)
        if (left.isBlank() || right.isBlank()) return false
        return left == right || (minOf(left.length, right.length) >= 4 &&
            (left.contains(right) || right.contains(left)))
    }

    private fun groupIdentity(receiver: String, description: String): Pair<String, String> {
        if (receiver.isNotBlank()) {
            return "party:${normalize(receiver)}" to receiver.take(60)
        }
        val simplified = description
            .replace(UPI_ID, " ")
            .replace(Regex("\\b\\d{4,}\\b"), " ")
            .replace(MONEY_TOKEN, " ")
            .replace(Regex("(?i)\\b(?:DR|DEBIT|WITHDRAWAL|PAID|UPI|IMPS|NEFT|RTGS|REF|UTR|TXN)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val key = normalize(simplified).take(80).ifBlank { "unidentified" }
        val detail = simplified.take(44).ifBlank { "Unidentified transaction" }
        val label = "Needs review · $detail"
        return "description:$key" to label
    }

    private fun identifyColumns(row: List<String>): Columns {
        val headers = row.map(::normalize)
        fun findWhere(predicate: (String) -> Boolean): Int? = headers.indexOfFirst(predicate).takeIf { it >= 0 }
        fun isAny(value: String, vararg aliases: String) = aliases.any { value == normalize(it) }

        val date = findWhere { header ->
            isAny(header, "date", "transaction date", "txn date", "value date", "posting date") ||
                header.endsWith("transactiondate")
        }
        val description = findWhere { header ->
            isAny(header, "description", "narration", "details", "remarks", "transaction details", "particulars") ||
                header.contains("narration") || header.contains("transactiondetails")
        }
        val receiver = findWhere { header ->
            header == "to" || header.contains("receiver") || header.contains("payee") ||
                header.contains("beneficiary") || header.contains("recipient") ||
                header.contains("counterparty") || header == "vpa" || header == "upi" || header == "upiid" ||
                header.contains("upiaddress") || header.contains("tovpa") ||
                header.contains("toaccount") || header.contains("creditedto") ||
                (header.contains("merchant") && !header.contains("category") && !header.contains("code"))
        }
        val sourceName = findWhere { header ->
            header.contains("sender") || header.contains("payer") || header.contains("accountholder") ||
                header.contains("remitter") || header.contains("fromname") || header.contains("fromvpa") ||
                header.contains("sourcevpa") || header == "from" || header == "accountname" ||
                header == "customername"
        }
        val sourceAccount = findWhere { header ->
            header.contains("sourceaccount") || header.contains("debitaccount") ||
                header.contains("fromaccount") || header.contains("debitedfrom") ||
                header == "accountnumber" || header == "accountno"
        }
        val debit = findWhere { header ->
            !header.contains("account") && !header.contains("from") &&
                (header == "debit" || header == "withdrawal" || header.contains("debitamount") ||
                    header.contains("withdrawalamount") || header == "dramount")
        }
        val credit = findWhere { header ->
            !header.contains("account") && !header.contains("to") &&
                (header == "credit" || header == "deposit" || header.contains("creditamount") ||
                    header.contains("depositamount") || header == "cramount")
        }
        val balance = findWhere { it.contains("balance") }
        val amount = findWhere { header ->
            !header.contains("balance") && !header.contains("debit") && !header.contains("credit") &&
                !header.contains("withdrawal") && !header.contains("deposit") &&
                isAny(header, "amount", "transaction amount", "txn amount")
        }
        val type = findWhere { header ->
            isAny(header, "type", "transaction type", "dr cr", "debit credit", "direction")
        }
        val category = findWhere { it == "category" || it.contains("expensecategory") }
        val reference = findWhere { header ->
            header.contains("reference") || header == "transactionid" || header == "txnid" ||
                header == "utr" || header == "rrn"
        }
        return Columns(
            date = date,
            description = description,
            receiver = receiver,
            debit = debit,
            credit = credit,
            amount = amount,
            type = type,
            category = category,
            balance = balance,
            reference = reference,
            sourceName = sourceName,
            sourceAccount = sourceAccount
        )
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val values = mutableListOf<String>()
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }
        var inItem = false
        var current = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (parser.name == "si") {
                    inItem = true
                    current = StringBuilder()
                }
                XmlPullParser.TEXT -> if (inItem) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    values += current.toString()
                    inItem = false
                }
            }
            parser.next()
        }
        return values
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }
        var cells = mutableMapOf<Int, String>()
        var cellIndex = 0
        var cellType = ""
        var cellValue = StringBuilder()
        var inValue = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> cells = mutableMapOf()
                    "c" -> {
                        cellIndex = columnIndex(parser.getAttributeValue(null, "r").orEmpty())
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellValue = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) cellValue.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = cellValue.toString()
                        val value = when (cellType) {
                            "s" -> raw.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        cells[cellIndex] = value
                    }
                    "row" -> {
                        val last = cells.keys.maxOrNull() ?: -1
                        rows += if (last < 0) emptyList() else (0..last).map { cells[it].orEmpty() }
                    }
                }
            }
            parser.next()
        }
        return rows
    }

    private fun columnIndex(reference: String): Int {
        var result = 0
        reference.takeWhile(Char::isLetter).forEach { result = result * 26 + (it.uppercaseChar() - 'A' + 1) }
        return (result - 1).coerceAtLeast(0)
    }

    private fun parseDate(raw: String): LocalDate? {
        val value = raw.trim()
        value.toDoubleOrNull()?.takeIf { it in 1.0..100000.0 }?.let { serial ->
            return LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
        }
        val cleaned = value.replace(Regex("\\s+"), " ").substringBefore('T').trim()
        DATE_FORMATS.forEach { formatter ->
            try {
                return LocalDate.parse(cleaned, formatter)
            } catch (_: DateTimeParseException) {
                Unit
            }
        }
        return null
    }

    private fun parseAmountMinor(raw: String): Long? {
        val original = raw.trim()
        if (original.isBlank() || original == "-") return null
        val negative = original.startsWith("-") || (original.startsWith("(") && original.endsWith(")"))
        val number = original.replace(Regex("(?i)INR|₹|DR|CR"), "")
            .replace(",", "").replace("(", "").replace(")", "").replace("-", "").trim()
        return runCatching {
            BigDecimal(number).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
                .let { if (negative) -it else it }
        }.getOrNull()
    }

    private fun hashOf(dateEpochDay: Long, amountMinor: Long, description: String, receiver: String): String {
        val canonical = "$dateEpochDay|$amountMinor|${normalize(description)}|${normalize(receiver)}"
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
    private fun cell(row: List<String>, index: Int?): String? = index?.let(row::getOrNull)?.takeIf(String::isNotBlank)

    private fun ZipInputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) error("The spreadsheet is too large to import safely.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private data class ParsedRow(
        val date: LocalDate,
        val amountMinor: Long,
        val description: String,
        val sender: String,
        val receiver: String,
        val senderMatchText: String,
        val receiverMatchText: String,
        val category: String?
    )

    private data class ParsedParties(
        val sender: String,
        val receiver: String,
        val senderMatchText: String,
        val receiverMatchText: String
    )

    private data class PdfLayout(
        val date: Int?,
        val description: Int?,
        val sender: Int?,
        val receiver: Int?,
        val debit: Int?,
        val credit: Int?,
        val amount: Int?,
        val type: Int?,
        val balance: Int?
    ) {
        fun starts(): List<Int> = listOfNotNull(
            date, description, sender, receiver, debit, credit, amount, type, balance
        ).distinct().sorted()
    }

    private sealed class PdfTransactionResult {
        data class Expense(val row: ParsedRow) : PdfTransactionResult()
        object Credit : PdfTransactionResult()
        object Unparsed : PdfTransactionResult()
    }

    private data class Columns(
        val date: Int?,
        val description: Int?,
        val receiver: Int?,
        val debit: Int?,
        val credit: Int?,
        val amount: Int?,
        val type: Int?,
        val category: Int?,
        val balance: Int?,
        val reference: Int?,
        val sourceName: Int?,
        val sourceAccount: Int?
    )

    private val DATE_FORMATS = listOf(
        "d/M/uuuu", "dd/MM/uuuu", "d-M-uuuu", "dd-MM-uuuu", "uuuu-MM-dd", "d/M/yy", "d-M-yy",
        "d MMM uuuu", "dd MMM uuuu", "d MMMM uuuu", "dd MMMM uuuu",
        "d-MMM-uuuu", "dd-MMM-uuuu", "M/d/uuuu", "MM/dd/uuuu"
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
    private val DATE_AT_START = Regex(
        "^\\s*(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/ ](?:\\d{1,2}|[A-Za-z]{3,9})[-/ ]\\d{2,4})",
        RegexOption.IGNORE_CASE
    )
    private val DEBIT_WORD = Regex("(?:^|\\b)(?:DR|DEBIT|WITHDRAWAL|PAID|OUTGOING)(?:\\b|$)")
    private val CREDIT_WORD = Regex("(?:^|\\b)(?:CR|CREDIT|DEPOSIT|RECEIVED|INCOMING)(?:\\b|$)")
    private val MONEY_TOKEN = Regex("(?:INR|₹)?\\s*[-(]?\\d[\\d,]*(?:\\.\\d{1,2})?[)]?", RegexOption.IGNORE_CASE)
    private val STRICT_MONEY_TOKEN = Regex(
        "(?:(?:INR|₹)\\s*[-(]?\\d[\\d,]*(?:\\.\\d{1,2})?[)]?|" +
            "[-(]?\\d[\\d,]*\\.\\d{2}[)]?|[-(]?\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?[)]?)",
        RegexOption.IGNORE_CASE
    )
    private val NEGATIVE_AMOUNT = Regex("-\\s*(?:INR|₹)?\\s*\\d[\\d,]*(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
    private val DEBIT_AMOUNT = Regex("(?i)(?:DR|DEBIT|WITHDRAWAL|PAID)\\s*[:-]?\\s*(?:INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)")
    private val AMOUNT_DEBIT = Regex("(?i)(?:INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:DR|DEBIT)")
    private val UPI_ID = Regex("[A-Za-z0-9._-]{2,}@[A-Za-z0-9._-]{2,}")
    private val MASKED_ID = Regex("(?:\\*|[Xx]){2,}[ -]?\\d{4}(?:@[A-Za-z0-9._-]{2,})?")
    private val NAME_SLASH_NAME = Regex("[A-Za-z][A-Za-z .]{1,39}/[A-Za-z][A-Za-z .]{1,39}")
    private val NAMED_COUNTERPARTY = Regex(
        "(?i)(?:PAYEE|BENEFICIARY|RECEIVER|RECIPIENT|PAID TO|TRANSFERRED TO)\\s*[:-]?\\s*([A-Z][A-Z .]{2,50})"
    )
    private val RECEIVER_MARKER = Regex(
        "(?i)\\b(?:PAID TO|TRANSFERRED TO|TRANSFER TO|CREDITED TO|PAYEE|BENEFICIARY|RECEIVER|RECIPIENT|MERCHANT|TO)\\b" +
            "\\s*[:=/|-]?\\s*([^|,;]{3,80})"
    )
    private val SENDER_MARKER = Regex(
        "(?i)\\b(?:DEBITED FROM|PAID BY|SENDER|PAYER|FROM)\\b\\s*[:=/|-]?\\s*" +
            "(.{3,80}?)(?=\\b(?:PAID TO|TRANSFERRED TO|TRANSFER TO|CREDITED TO|TO|RECEIVER|PAYEE|" +
            "BENEFICIARY|RECIPIENT|MERCHANT)\\b|[|,;]|$)"
    )
    private val MERCHANT_NARRATION = Regex(
        "(?i)\\b(?:POS|ECOM|PURCHASE|CARD)\\b(?:\\s+\\d+)?\\s*[-:/]?\\s*([A-Z][A-Z &.]{2,60})"
    )
    private val TRAILING_MONEY = Regex(
        "(?i)\\s+(?:INR|₹)?\\s*[-(]?\\d[\\d,]*(?:\\.\\d{1,2})?[)]?\\s*$"
    )
    private val IGNORED_COUNTERPARTY_TOKENS = setOf(
        "upi", "imps", "neft", "rtgs", "ach", "ref", "utr", "txn", "payment", "debit",
        "transfer", "paid", "purchase", "pos", "bank", "mobile", "online", "p2a", "p2m", "p2p"
    )
}
