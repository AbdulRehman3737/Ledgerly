package com.ledgerly.app.data.export

import com.ledgerly.app.domain.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class XlsxCodecTest {

    private fun row(): XlsxRow = XlsxRow(
        category = "Car",
        note = "tuning",
        amountMinor = 800_000,
        currencyCode = "USD",
        type = TxType.EXPENSE,
        dateEpochDay = 20697L, // 2026-09-01
    )

    private fun mustParse(bytes: ByteArray): ParsedXlsx = XlsxCodec.parse(bytes)

    @Test
    fun `encode then parse round trips`() {
        val rows = listOf(
            row(),
            XlsxRow("Salary", "", 29_199_300, "USD", TxType.INCOME, 20682L), // 2026-08-17
        )
        val parsed = mustParse(XlsxCodec.encode(rows)).rows

        assertEquals(2, parsed.size)
        val car = parsed.first { it.categoryName == "Car" }
        assertEquals("tuning", car.note)
        assertEquals(800_000L, car.amountMinor)
        assertEquals("USD", car.currencyCode)
        assertEquals(TxType.EXPENSE, car.type)
        assertEquals(20697L, car.dateEpochDay)

        val salary = parsed.first { it.categoryName == "Salary" }
        assertEquals(29_199_300L, salary.amountMinor)
        assertEquals(TxType.INCOME, salary.type)
    }

    @Test
    fun `amounts are exported grouped like the MoneyManager sample`() {
        val sheet = unzipSheet(XlsxCodec.encode(listOf(row())))
        assertTrue(sheet.contains("8,000"))
    }

    @Test
    fun `dates are exported as d MMM yyyy`() {
        val sheet = unzipSheet(XlsxCodec.encode(listOf(row())))
        assertTrue(sheet.contains("1 Sept 2026"))
    }

    @Test
    fun `rows are sorted by date descending`() {
        val rows = listOf(
            XlsxRow("Old", "", 100, "USD", TxType.EXPENSE, 20650L),
            XlsxRow("New", "", 200, "USD", TxType.EXPENSE, 20670L),
        )
        val parsed = mustParse(XlsxCodec.encode(rows)).rows
        assertEquals("New", parsed[0].categoryName)
        assertEquals("Old", parsed[1].categoryName)
    }

    @Test
    fun `invalid rows are skipped with warnings`() {
        val manual = manualSheet(
            listOf(
                mapOf("Category" to "Food", "Note" to "", "Amount" to "1,000", "Currency" to "USD", "Type" to "Expenses", "Date" to "1 Aug 2026"),
                mapOf("Category" to "Food", "Note" to "", "Amount" to "0", "Currency" to "USD", "Type" to "NoSuch", "Date" to "1 Aug 2026"),
            )
        )
        val parsed = mustParse(manual)
        assertEquals(1, parsed.rows.size)
        assertTrue(parsed.warnings.isNotEmpty())
    }

    @Test
    fun `rejects invalid file`() {
        try {
            XlsxCodec.parse(byteArrayOf(1, 2, 3))
            fail("Expected XlsxException")
        } catch (e: XlsxException) {
            assertEquals("This is not a valid .xlsx file.", e.message)
        }
    }

    @Test
    fun `empty bytes produce empty result with warning`() {
        val parsed = XlsxCodec.parse(ByteArray(0))
        assertTrue(parsed.rows.isEmpty())
        assertTrue(parsed.warnings.isNotEmpty())
    }

    @Test
    fun `parses the real MoneyManager sample file when present`() {
        val dir = java.io.File("..")
        val sample = dir.listFiles()?.firstOrNull {
            it.isFile && it.name.startsWith("MoneyManager_") && it.name.endsWith(".xlsx")
        } ?: return

        val parsed = XlsxCodec.parse(sample.readBytes())
        assertTrue("expected data rows from ${sample.name}", parsed.rows.isNotEmpty())
        assertEquals(29, parsed.rows.size)
        assertEquals("Car", parsed.rows.first().categoryName)
        assertEquals(800_000L, parsed.rows.first().amountMinor)
        assertTrue("expected an income row", parsed.rows.any { it.type == TxType.INCOME })
    }
}

private fun manualSheet(rows: List<Map<String, String>>): ByteArray {
    // Build a simple inline-str worksheet manually.
    val headers = XlsxCodec.HEADERS
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
    sb.append("<row r=\"1\">")
    headers.forEachIndexed { i, h ->
        sb.append("<c r=\"${columnLetter(i)}1\" t=\"inlineStr\"><is><t>$h</t></is></c>")
    }
    sb.append("</row>")
    rows.forEachIndexed { idx, map ->
        val rn = idx + 2
        sb.append("<row r=\"$rn\">")
        headers.forEachIndexed { i, h ->
            val v = map[h].orEmpty()
            sb.append("<c r=\"${columnLetter(i)}$rn\" t=\"inlineStr\"><is><t>$v</t></is></c>")
        }
        sb.append("</row>")
    }
    sb.append("</sheetData></worksheet>")

    return zipOf(
        "_rels/.rels" to XlsxCodecTestBody.RELS,
        "xl/workbook.xml" to XlsxCodecTestBody.WORKBOOK,
        "xl/_rels/workbook.xml.rels" to XlsxCodecTestBody.WORKBOOK_RELS,
        "xl/styles.xml" to XlsxCodecTestBody.STYLES,
        "xl/worksheets/sheet1.xml" to sb.toString(),
        "[Content_Types].xml" to XlsxCodecTestBody.CONTENT_TYPES,
    )
}

object XlsxCodecTestBody {
    val CONTENT_TYPES = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
        </Types>
    """.trimIndent()
    val RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()
    val WORKBOOK = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
    """.trimIndent()
    val WORKBOOK_RELS = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()
    val STYLES = """
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

private fun columnLetter(index: Int): String {
    var n = index
    val sb = StringBuilder()
    do {
        sb.insert(0, ('A' + n % 26))
        n = n / 26 - 1
    } while (n >= 0)
    return sb.toString()
}

private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    java.util.zip.ZipOutputStream(out).use { zip ->
        for ((name, content) in entries) {
            zip.putNextEntry(java.util.zip.ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun unzipSheet(bytes: ByteArray): String {
    java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "xl/worksheets/sheet1.xml") {
                return zip.readBytes().toString(Charsets.UTF_8)
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return ""
}