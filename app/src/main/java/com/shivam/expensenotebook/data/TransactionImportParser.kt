package com.shivam.expensenotebook.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.StringReader
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
            PDDocument.load(input).use { document -> PDFTextStripper().getText(document) }
        } ?: error("The selected PDF could not be opened.")
        if (text.isBlank()) error("No readable text was found. Scanned/image-only PDFs are not supported.")

        val rows = mutableListOf<ParsedRow>()
        var credits = 0
        var unparsed = 0
        var debitColumnStart: Int? = null
        var creditColumnStart: Int? = null
        text.lineSequence().forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            val headerUpper = trimmedLine.uppercase(Locale.ROOT)
            if (!DATE_AT_START.containsMatchIn(trimmedLine) &&
                (headerUpper.contains("DEBIT") || headerUpper.contains("WITHDRAWAL")) &&
                (headerUpper.contains("CREDIT") || headerUpper.contains("DEPOSIT"))
            ) {
                debitColumnStart = listOf(headerUpper.indexOf("DEBIT"), headerUpper.indexOf("WITHDRAWAL"))
                    .filter { it >= 0 }.minOrNull()
                creditColumnStart = listOf(headerUpper.indexOf("CREDIT"), headerUpper.indexOf("DEPOSIT"))
                    .filter { it >= 0 }.minOrNull()
                return@forEach
            }
            val line = trimmedLine.replace(Regex("\\s+"), " ")
            val dateMatch = DATE_AT_START.find(line) ?: return@forEach
            val date = parseDate(dateMatch.value.trim())
            if (date == null) {
                unparsed++
                return@forEach
            }
            val upper = line.uppercase(Locale.ROOT)
            val isCredit = CREDIT_WORD.containsMatchIn(upper) && !DEBIT_WORD.containsMatchIn(upper)
            if (isCredit) {
                credits++
                return@forEach
            }
            var amount = findPdfDebitAmount(line, dateMatch.range.last + 1)
            if (amount == null) {
                val debitStart = debitColumnStart
                val creditStart = creditColumnStart
                if (debitStart != null && creditStart != null && debitStart < creditStart) {
                    val debitCell = trimmedLine.substring(
                        debitStart.coerceAtMost(trimmedLine.length),
                        creditStart.coerceAtMost(trimmedLine.length)
                    )
                    amount = MONEY_TOKEN.find(debitCell)?.value?.let(::parseAmountMinor)?.let { kotlin.math.abs(it) }
                    if (amount == null) {
                        val creditCell = trimmedLine.substring(creditStart.coerceAtMost(trimmedLine.length))
                        if (MONEY_TOKEN.containsMatchIn(creditCell)) {
                            credits++
                            return@forEach
                        }
                    }
                }
            }
            if (amount == null) {
                unparsed++
                return@forEach
            }
            val description = line.substring(dateMatch.range.last + 1).trim()
            rows += ParsedRow(date, amount, description, extractReceiver(description), null)
        }
        return finalizeRows(sourceName, rows, ownIdentifiers, credits, unparsed)
    }

    private fun findPdfDebitAmount(line: String, descriptionStart: Int): Long? {
        val body = line.substring(descriptionStart)
        val explicitDebit = DEBIT_WORD.containsMatchIn(body.uppercase(Locale.ROOT))
        val signedDebit = NEGATIVE_AMOUNT.findAll(body).lastOrNull()?.value
        if (!explicitDebit && signedDebit == null) return null

        val direct = DEBIT_AMOUNT.find(body)?.groups?.get(1)?.value
            ?: AMOUNT_DEBIT.find(body)?.groups?.get(1)?.value
            ?: signedDebit
        if (direct != null) return parseAmountMinor(direct)?.let { kotlin.math.abs(it) }

        val amounts = MONEY_TOKEN.findAll(body).mapNotNull { token ->
            parseAmountMinor(token.value)?.let { kotlin.math.abs(it) }
        }.toList()
        return when {
            amounts.isEmpty() -> null
            amounts.size == 1 -> amounts.first()
            else -> amounts[amounts.lastIndex - 1]
        }
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
            val explicitReceiver = cell(row, columns.receiver).orEmpty().trim()
            val receiver = explicitReceiver.ifBlank { extractReceiver(description) }
            val sourceCategory = cell(row, columns.category).orEmpty().trim()
            val category = knownCategories.firstOrNull { it.equals(sourceCategory, ignoreCase = true) }
                ?: categorize("$sourceCategory $receiver $description")
            rows += ParsedRow(date, outgoing, description, receiver, category)
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
        var excluded = 0
        val candidates = rows.mapNotNull { row ->
            if (matchesOwnAccount(row.description, row.receiver, identifiers)) {
                excluded++
                null
            } else {
                val category = row.category ?: categorize("${row.receiver} ${row.description}")
                ImportCandidate(
                    importKey = hashOf(row.date.toEpochDay(), row.amountMinor, row.description, row.receiver),
                    amountMinor = row.amountMinor,
                    dateEpochDay = row.date.toEpochDay(),
                    note = row.description.take(120),
                    receiver = row.receiver.take(80),
                    categoryName = category
                )
            }
        }.distinctBy { it.importKey }
        return ImportPreview(sourceName, candidates, excluded, credits, unparsed)
    }

    private fun matchesOwnAccount(description: String, receiver: String, identifiers: List<String>): Boolean {
        val text = receiver.ifBlank { description }
        val normalizedText = normalize(text)
        val digits = Regex("(?<!\\d)\\d{4}(?!\\d)").findAll(text).map { it.value }.toSet()
        return identifiers.any { identifier ->
            val trimmed = identifier.trim()
            if (trimmed.matches(Regex("\\d{4}"))) trimmed in digits
            else normalize(trimmed).takeIf { it.length >= 3 }?.let(normalizedText::contains) == true
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

    private fun extractReceiver(description: String): String {
        UPI_ID.find(description)?.value?.let { return it }
        val ignored = setOf("upi", "imps", "neft", "rtgs", "ref", "txn", "payment", "debit", "transfer", "to")
        return description.split('/', '|', '-', ':')
            .map { it.trim() }
            .firstOrNull { part ->
                val simple = part.lowercase(Locale.ROOT)
                part.length in 3..80 && part.any(Char::isLetter) && simple !in ignored &&
                    !part.matches(Regex("[A-Za-z]*\\d{6,}"))
            }.orEmpty()
    }

    private fun identifyColumns(row: List<String>): Columns {
        fun find(vararg names: String): Int? {
            val aliases = names.map(::normalize).toSet()
            return row.indexOfFirst { normalize(it) in aliases }.takeIf { it >= 0 }
        }
        return Columns(
            date = find("date", "transaction date", "txn date", "value date", "posting date"),
            description = find("description", "narration", "details", "remarks", "transaction details", "particulars"),
            receiver = find("receiver", "receiver name", "payee", "beneficiary", "upi", "upi id", "merchant"),
            debit = find("debit", "debit amount", "withdrawal", "withdrawal amount", "dr amount"),
            credit = find("credit", "credit amount", "deposit", "deposit amount", "cr amount"),
            amount = find("amount", "transaction amount", "txn amount"),
            type = find("type", "transaction type", "dr cr", "debit credit", "direction"),
            category = find("category", "expense category")
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
        val receiver: String,
        val category: String?
    )

    private data class Columns(
        val date: Int?,
        val description: Int?,
        val receiver: Int?,
        val debit: Int?,
        val credit: Int?,
        val amount: Int?,
        val type: Int?,
        val category: Int?
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
    private val NEGATIVE_AMOUNT = Regex("-\\s*(?:INR|₹)?\\s*\\d[\\d,]*(?:\\.\\d{1,2})?", RegexOption.IGNORE_CASE)
    private val DEBIT_AMOUNT = Regex("(?i)(?:DR|DEBIT|WITHDRAWAL|PAID)\\s*[:-]?\\s*(?:INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)")
    private val AMOUNT_DEBIT = Regex("(?i)(?:INR|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:DR|DEBIT)")
    private val UPI_ID = Regex("[A-Za-z0-9._-]{2,}@[A-Za-z0-9._-]{2,}")
}
