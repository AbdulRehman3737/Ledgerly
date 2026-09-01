package com.ledgerly.app.data.export

import com.ledgerly.app.domain.model.TxType
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal xlsx codec for the spreadsheet format used by MoneyManager. Written zip + DOM
 * based so it works on both Android and plain JVM (unit tests) with zero extra dependencies.
 *
 * Column layout (row 1 header, cells written as strings):
 *   Category | Note | Amount | Currency | Type | Account | Date | Photos
 * Datеs use "d MMM yyyy" (e.g. "1 Sept 2026"), amounts are grouped ("8,000").
 */
data class XlsxRow(
    val category: String,
    val note: String,
    val amountMinor: Long,
    val currencyCode: String,
    val type: TxType,
    val dateEpochDay: Long,
)

data class ParsedXlsxRow(
    val categoryName: String,
    val note: String,
    val amountMinor: Long,
    val currencyCode: String,
    val type: TxType,
    val dateEpochDay: Long,
    val rowNumber: Int,
)

data class ParsedXlsx(
    val rows: List<ParsedXlsxRow>,
    val warnings: MutableList<String>,
) {
    companion object {
        fun empty() = ParsedXlsx(emptyList(), mutableListOf())
    }
}

class XlsxException(message: String) : Exception(message)

object XlsxCodec {

    const val HEADER_CATEGORY = "Category"
    const val HEADER_NOTE = "Note"
    const val HEADER_AMOUNT = "Amount"
    const val HEADER_CURRENCY = "Currency"
    const val HEADER_TYPE = "Type"
    const val HEADER_ACCOUNT = "Account"
    const val HEADER_DATE = "Date"
    const val HEADER_PHOTOS = "Photos"

    val HEADERS: List<String> = listOf(
        HEADER_CATEGORY, HEADER_NOTE, HEADER_AMOUNT, HEADER_CURRENCY,
        HEADER_TYPE, HEADER_ACCOUNT, HEADER_DATE, HEADER_PHOTOS,
    )

    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val SHEET_NAME = "Sheet1"
    private val EXCEL_EPOCH = LocalDate.of(1899, 12, 30)
    private const val MIN_DAY = 0L
    private const val MAX_DAY = 84_429L // 2200-12-31 as epoch day

    // MoneyManager uses the non-standard abbreviation "Sept" (not "Sep").
    private val EXPORT_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec")

    private fun formatDate(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return "${date.dayOfMonth} ${EXPORT_MONTHS[date.monthValue - 1]} ${date.year}"
    }

    private val DATE_PATTERNS: List<DateTimeFormatter> = listOf(
        "d MMM yyyy", "d MMMM yyyy",
        "MMM d, yyyy", "MMMM d, yyyy",
        "yyyy-MM-dd", "yyyy/MM/dd",
        "dd/MM/yyyy", "MM/dd/yyyy",
        "d.M.yyyy",
    ).map { pattern ->
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .toFormatter(Locale.US)
    }

    // ---- Writing ----

    fun encode(rows: List<XlsxRow>): ByteArray {
        val sorted = rows.sortedWith(
            compareByDescending<XlsxRow> { it.dateEpochDay }
                .thenByDescending { it.category }
        )
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            writeZipEntry(zip, "_rels/.rels", RELS_XML)
            writeZipEntry(zip, "xl/workbook.xml", WORKBOOK_XML)
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            writeZipEntry(zip, "xl/styles.xml", STYLES_XML)
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildSheetXml(sorted))
            writeZipEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
        }
        return bytes.toByteArray()
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildSheetXml(rows: List<XlsxRow>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<worksheet xmlns=\"$NS_MAIN\">")
        sb.append("<sheetData>")
        appendRow(sb, 1, HEADERS.map { Cell(it) })
        rows.forEachIndexed { index, row ->
            val date = formatDate(row.dateEpochDay)
            val amount = formatAmount(row.amountMinor)
            val typeLabel = if (row.type == TxType.INCOME) "Income" else "Expenses"
            val cells = listOf(
                Cell(row.category),
                Cell(row.note),
                Cell(amount),
                Cell(row.currencyCode),
                Cell(typeLabel),
                Cell(""),
                Cell(date),
                Cell(""),
            )
            appendRow(sb, index + 2, cells)
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun appendRow(sb: StringBuilder, rowNumber: Int, cells: List<Cell>) {
        sb.append("<row r=\"$rowNumber\">")
        cells.forEachIndexed { index, cell ->
            val ref = columnLetter(index) + rowNumber
            sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>")
            sb.append(xmlEscape(cell.text))
            sb.append("</t></is></c>")
        }
        sb.append("</row>")
    }

    private data class Cell(val text: String)

    /** Formats minor units as a grouped string matching the MoneyManager sample, e.g. 8,000 or 1,618.5 */
    private fun formatAmount(amountMinor: Long): String {
        val major = BigDecimal(amountMinor).divide(BigDecimal(100L), 2, RoundingMode.HALF_UP)
        return DecimalFormat("#,##0.##").format(major)
    }

    private fun columnLetter(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    private fun xmlEscape(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\u00A0' -> append(' ')
                else -> append(ch)
            }
        }
    }

    // ---- Reading ----

    fun parse(bytes: ByteArray): ParsedXlsx {
        if (bytes.isEmpty()) return ParsedXlsx(emptyList(), mutableListOf("File is empty."))
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (entries.isEmpty()) throw XlsxException("This is not a valid .xlsx file.")

        val sheetEntry = entries.keys
            .firstOrNull { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            ?: throw XlsxException("No worksheet found in this .xlsx file.")
        val sharedStrings = entries["xl/sharedStrings.xml"]
            ?.let { parseSharedStrings(it) }
            ?: emptyList()
        return parseSheet(entries[sheetEntry]!!, sharedStrings)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val sis = doc.getElementsByTagName("si")
        val out = mutableListOf<String>()
        for (i in 0 until sis.length) {
            out += (sis.item(i) as Element).textContent ?: ""
        }
        return out
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): ParsedXlsx {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val rowList = doc.getElementsByTagName("row")
        val grid = mutableListOf<Pair<Int, MutableMap<Int, String>>>()
        for (i in 0 until rowList.length) {
            val r = rowList.item(i) as Element
            val rowNumber = r.getAttribute("r").toIntOrNull() ?: (i + 1)
            val cells = mutableMapOf<Int, String>()
            val cellList = r.getElementsByTagName("c")
            var position = 0
            for (j in 0 until cellList.length) {
                val c = cellList.item(j) as Element
                val ref = c.getAttribute("r")
                val colIndex = if (ref.isNotBlank()) columnIndexOf(ref) else position
                position = colIndex + 1
                cells[colIndex] = cellValue(c, sharedStrings)
            }
            grid += rowNumber to cells
        }
        if (grid.isEmpty()) return ParsedXlsx(emptyList(), mutableListOf("No data rows found in the sheet."))

        val columnByHeader = mutableMapOf<String, Int>()
        var headerRowNumber = -1
        for ((rowNumber, cells) in grid) {
            val known = cells.entries.firstOrNull { it.value.trim() in HEADERS }
            if (known != null) {
                headerRowNumber = rowNumber
                for ((idx, text) in cells) {
                    if (text.trim() in HEADERS) columnByHeader[text.trim()] = idx
                }
                break
            }
        }

        fun cellOf(cells: MutableMap<Int, String>, header: String): String {
            val idx = columnByHeader[header] ?: return ""
            return cells[idx] ?: ""
        }

        val warnings = mutableListOf<String>()
        val result = mutableListOf<ParsedXlsxRow>()
        for ((rowNumber, cells) in grid) {
            if (rowNumber == headerRowNumber) continue
            if (cells.isEmpty()) continue
            val hasAnyValue = columnByHeader.values.any { cells[it].orEmpty().isNotBlank() }
            if (columnByHeader.isNotEmpty() && !hasAnyValue) continue

            val categoryName = cellOf(cells, HEADER_CATEGORY).trim().ifBlank { "Other" }
            val note = cellOf(cells, HEADER_NOTE).trim()
            val currency = cellOf(cells, HEADER_CURRENCY).trim()
            val type = parseType(cellOf(cells, HEADER_TYPE))
            val amount = parseAmount(cellOf(cells, HEADER_AMOUNT))
            val date = parseDate(cellOf(cells, HEADER_DATE))

            if (type == null) {
                warnings += "Row $rowNumber: unknown type '${cellOf(cells, HEADER_TYPE)}'; skipped."
                continue
            }
            if (amount == null) {
                warnings += "Row $rowNumber: invalid amount '${cellOf(cells, HEADER_AMOUNT)}'; skipped."
                continue
            }
            if (date == null) {
                warnings += "Row $rowNumber: invalid date '${cellOf(cells, HEADER_DATE)}'; skipped."
                continue
            }
            val epochDay = date.toEpochDay()
            if (epochDay < MIN_DAY || epochDay > MAX_DAY) {
                warnings += "Row $rowNumber: date ${date} is out of range; skipped."
                continue
            }
            result += ParsedXlsxRow(
                categoryName = categoryName,
                note = note,
                amountMinor = amount,
                currencyCode = currency,
                type = type,
                dateEpochDay = epochDay,
                rowNumber = rowNumber,
            )
        }
        return ParsedXlsx(result, warnings)
    }

    private fun cellValue(c: Element, sharedStrings: List<String>): String {
        return when (c.getAttribute("t")) {
            "s" -> c.getElementsByTagName("v").item(0)?.textContent?.trim()?.toIntOrNull()
                ?.let { sharedStrings.getOrElse(it) { "" } } ?: ""
            "inlineStr" -> {
                (c.getElementsByTagName("is").item(0) as? Element)?.textContent ?: ""
            }
            "b" -> {
                val v = c.getElementsByTagName("v").item(0)?.textContent?.trim().orEmpty()
                (v == "1" || v.equals("true", ignoreCase = true)).toString()
            }
            else -> c.getElementsByTagName("v").item(0)?.textContent?.trim() ?: ""
        }
    }

    private fun columnIndexOf(ref: String): Int {
        var result = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            result = result * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return result - 1
    }

    private fun parseType(raw: String): TxType? = when (raw.trim().lowercase(Locale.ROOT)) {
        "income", "salary", "credit", "deposit" -> TxType.INCOME
        "expense", "expenses", "debit", "withdrawal", "spending" -> TxType.EXPENSE
        else -> null
    }

    private fun parseAmount(raw: String): Long? {
        val cleaned = raw.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val value = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null
        if (value.signum() < 0) return null
        val minor = runCatching {
            value.multiply(BigDecimal(100L)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull() ?: return null
        if (minor > 99_999_999_999_999L) return null
        return minor
    }

    private fun parseDate(raw: String): LocalDate? {
        var trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toLongOrNull()?.let { serial ->
            if (serial in 1..90_000) return EXCEL_EPOCH.plusDays(serial)
        }
        // Normalise the non-standard "Sept" abbreviation used by MoneyManager.
        trimmed = trimmed.replace(Regex("(?i)\\bSept\\b"), "Sep")
        for (formatter in DATE_PATTERNS) {
            try {
                return LocalDate.parse(trimmed, formatter)
            } catch (_: DateTimeParseException) {
                // try next pattern
            }
        }
        return null
    }

    // ---- Static XML parts ----

    private val CONTENT_TYPES_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent()

    private val RELS_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private val WORKBOOK_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="$SHEET_NAME" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
    """.trimIndent()

    private val WORKBOOK_RELS_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private val STYLES_XML = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
          <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
          <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
          <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
          <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
        </styleSheet>
    """.trimIndent()
}