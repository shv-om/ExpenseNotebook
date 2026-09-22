# ExpenseNotebook — PDF import / receiver-column diagnosis

File in question: `TransactionImportParser.kt` (`parsePdf`, `detectPdfLayout`,
`slicePdfColumn`, `inferCounterparty`, `extractOutgoingReceiver`, `matchesOwnAccount`).

## How the PDF path currently works

1. `PDFTextStripper().setSortByPosition(true)` dumps the whole PDF to plain text,
   trying to preserve left-to-right reading order.
2. `detectPdfLayout()` looks at header lines and records the **character offset**
   at which each column label starts (`Date`, `Narration`, `Receiver`, `Debit`, …).
3. `slicePdfColumn()` then re-uses those same character offsets to cut every
   subsequent data line into columns.
4. If there's no explicit receiver value, `extractOutgoingReceiver()` tries to
   pull a counterparty out of the free-text narration with regexes.
5. `matchesOwnAccount()` compares the resolved receiver against a list of your
   own identifiers to decide internal vs. external — good, this already avoids
   using the sender field for that decision (more on this below).

## Root causes of misreads

### 1. Character-offset column slicing is fundamentally fragile for PDFs
This is the main reason receivers come out wrong or truncated. `PDFTextStripper`
does not guarantee that column *N* starts at the same character position on
every line of a table:
- Proportional fonts mean a 6-digit debit amount and a 5-digit one don't occupy
  the same horizontal space, so numeric columns drift left/right row to row.
- Right-aligned amount columns are the worst offender — the label `Debit` might
  start at offset 60, but the digits of a particular row's value can start at
  57 or 63 depending on how many digits it has.
- Wrapped receiver names (2 physical lines for one transaction) get joined with
  a single space by `slicePdfColumn`, but each physical line is sliced at the
  *same* offset independently — if the second line is indented differently
  (continuation lines often are), part of the name silently gets clipped or
  merged with the next column's text.
- A table that changes column widths from page to page (common when a bank
  switches font size or adds a column mid-statement) is not re-detected unless
  a fresh header line appears — `currentLayout` otherwise persists across pages.

**Practical effect:** on any statement where the columns aren't perfectly
monospaced/rigid, the Receiver slice can contain half of the amount column, part
of a reference number, or nothing at all.

### 2. Ambiguous single "VPA" column (the sender-lookalike problem)
```kotlin
val receiver = explicitReceiver ?: if (!upper.contains("SENDER VPA") && !upper.contains("PAYER VPA")) {
    position("VPA")
} else null
```
Several UPI statement formats expose just one column literally labelled `VPA`
with no distinction between payer/payee. On a debit row, that column is
sometimes **your own VPA**, not the counterparty's — exactly the "sender
disguised as receiver" trap you described. I patched this: if the value pulled
from that column matches one of your own identifiers, the parser now discards
it and falls back to parsing the narration text instead of importing your own
VPA as the "receiver" (see `parsePdfTransaction`, the `matchesOwnAccount(receiverFromColumn, …)`
branch).

### 3. `matchesOwnAccount` had a real false-positive bug
```kotlin
receiverDigits.contains(configuredDigits.takeLast(4))   // old
```
`.contains` checks for the last-4-digits appearing **anywhere** in the
receiver's digit string, not specifically as a suffix. So if your own account
ends `...1234`, any transaction whose *reference number, masked card, or date*
happened to contain `1234` anywhere got silently flagged as an internal
transfer and excluded from import — a genuine external expense could disappear
without any error. Fixed to `receiverDigits.endsWith(...)`, which mirrors how
masked account numbers actually look (`XXXX1234`, `...@okhdfcbank`).

The name-match side had a related issue: it normalized both strings down to
letters-only and did a raw substring check, so a short configured name like
`Raj` could match inside an unrelated word like `Rajesh`. Fixed to a
whole-word comparison.

### 4. Sender field — your instinct was right, mostly already handled
`matchesOwnAccount` (the internal/external decision) already uses **your
configured identifiers vs. the receiver**, not the sender field — so it's not
vulnerable to "sender is always you" in the way you were worried about. The
sender field is only used in one narrower place: `sameParty(candidate, sender)`
inside `extractOutgoingReceiver`, to avoid re-extracting your own name back out
of the narration as if it were the receiver. That's a legitimate, different use
— but it's only as reliable as the sender column itself, which on many
statements is blank or just says "Self"/"A/C Holder", so it quietly does
nothing rather than causing harm. Not a priority fix, just worth knowing.

### 5. Narration token-splitting is naive
```kotlin
val tokens = cleaned.split('/', '|', ':', '-')
```
Real narrations mix separators inconsistently (`UPI-DR-9876543210-JOHN DOE-HDFC0001234`,
vs `UPI/P2M/JOHNDOE/Swiggy`), and merchant names that legitimately contain a
hyphen (`Big-Bazaar`, `Café-Coffee-Day`) get exploded into fragments, so the
"first human-looking token" heuristic can grab half a merchant name.

### 6. Categorization inherits every upstream error
`categorize()` runs on `"$identity ${row.description}"` with a fixed keyword
list checked in a hardcoded order — so a wrong/blank receiver from (1)-(3)
above guarantees a wrong or "Other" category downstream, and even with a
correct receiver, overlapping keywords resolve by whichever `when` branch is
listed first (e.g. "Swiggy Instamart grocery order" hits the `Food & Dining`
branch before `Groceries` because `food` matches). This is a minor issue vs.
the extraction bugs, but worth knowing once extraction is solid.

## Edge cases worth testing deliberately
- Multi-line wrapped receiver names spanning 2–3 physical lines.
- A statement with only a single ambiguous `VPA` or `Remarks` column (no
  separate sender/receiver headers).
- Masked account numbers (`XXXXXX1234`) vs. full account numbers vs. VPAs, all
  representing the same "own account" identifier.
- A receiver name in `SURNAME/FIRSTNAME` order vs. `FIRSTNAME SURNAME`.
- Refunds/reversals where the counterparty is a merchant but the direction
  looks like a credit — should end up in `credits`, not silently miscategorized
  as an expense to that merchant.
- Reference numbers or dates that happen to share 4 digits with your own
  account number (this is exactly what bug #3 above was mishandling).
- A statement that changes table layout partway through (font size change,
  extra column) without a clean new header line for `detectPdfLayout` to catch.
- Merchant names containing hyphens, slashes, or ampersands that collide with
  the token-splitting delimiters in `extractOutgoingReceiver`.

## What I changed vs. what's still open
**Fixed in the attached file:**
- `matchesOwnAccount` — suffix-only digit match, whole-word name match.
- `parsePdfTransaction` — a receiver column value matching your own
  identifiers no longer gets imported as "the receiver"; falls back to
  narration parsing instead.

**Not changed (flagged above, needs your judgment / real statement samples):**
- Column-slicing tolerance for misaligned rows (root cause #1) — this needs
  actual sample PDFs from your bank(s) to tune safely; changing offset logic
  blindly risks breaking statements that currently parse fine.
- Narration token-splitting for merchant names with hyphens (root cause #5).
- Categorization keyword ordering (root cause #6).

I couldn't compile/run this — the sandbox has no network access for Gradle to
pull Android/PDFBox dependencies, so these edits are reviewed by hand, not
build-tested. Please run a Gradle build before relying on them, and ideally
test against 2–3 real (redacted) statement PDFs from different banks, since
that's the only real way to validate column-offset behavior.
