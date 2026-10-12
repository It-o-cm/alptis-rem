package fr.alptis.remuneration.documents;

import fr.alptis.remuneration.documents.StatementService.Row;
import fr.alptis.remuneration.documents.StatementService.StatementContent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link StatementDocuments}: the real PDF and spreadsheet produced from a
 * statement content (BRD-01), including escaping, numeric versus empty cells and the optional
 * version number. Runs without a database or container.
 */
class StatementDocumentsTest {

    /** A content with a version, a numeric row, a row needing escaping and an empty-amount row. */
    private static StatementContent sample() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Commissions", "AD-1 (C1) \\ Z", new BigDecimal("1000.00"), new BigDecimal("0.10"),
                new BigDecimal("100.00"), "R&D \\ <x>"));
        rows.add(new Row("Reprises", "CB-2", null, null, null, null));
        return new StatementContent("BORD-1", 1, "DISTRIBUTOR #7", LocalDate.of(2026, 9, 1), rows,
                new BigDecimal("100.00"), new BigDecimal("0.00"), new BigDecimal("100.00"));
    }

    /**
     * The PDF is a valid, self-contained document with its header, objects, cross-reference table and
     * trailer, and it escapes parentheses and backslashes in the drawn text.
     */
    @Test
    void renders_a_valid_pdf() {
        byte[] pdf = StatementDocuments.pdf(sample());
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("%PDF-1.4"));
        assertTrue(text.contains("/Type/Catalog"));
        assertTrue(text.contains("/BaseFont/Helvetica/Encoding/WinAnsiEncoding"));
        assertTrue(text.contains("xref"));
        assertTrue(text.contains("trailer"));
        assertTrue(text.endsWith("%%EOF"));
        // The label "AD-1 (C1) \ Z" has its parentheses and backslash escaped in the content stream.
        assertTrue(text.contains("AD-1 \\(C1\\) \\\\ Z"));
        assertTrue(text.contains("Bordereau BORD-1 v1"));
    }

    /**
     * A content without a version number (a member's informative statement) drops the "v" suffix.
     */
    @Test
    void renders_a_pdf_without_version() {
        StatementContent content = new StatementContent("RELEVE-X", null, "Code X", LocalDate.of(2026, 9, 1),
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        String text = new String(StatementDocuments.pdf(content), StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("Bordereau RELEVE-X") && !text.contains("RELEVE-X v"));
    }

    /**
     * The workbook is a real {@code .xlsx}: a ZIP of the five OOXML parts, whose sheet carries the
     * header, numeric cells for amounts, empty inline cells for missing amounts and XML-escaped text.
     */
    @Test
    void renders_a_valid_workbook() {
        byte[] bytes = StatementDocuments.workbook(sample());
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
        Map<String, String> parts = unzip(bytes);
        assertTrue(parts.containsKey("[Content_Types].xml"));
        assertTrue(parts.containsKey("_rels/.rels"));
        assertTrue(parts.containsKey("xl/workbook.xml"));
        assertTrue(parts.containsKey("xl/_rels/workbook.xml.rels"));
        String sheet = parts.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("<t xml:space=\"preserve\">rubrique</t>"));
        assertTrue(sheet.contains("<v>1000.00</v>"));
        assertTrue(sheet.contains("<v>100.00</v>"));
        // The escaped special characters of "R&D \\ <x>".
        assertTrue(sheet.contains("R&amp;D \\ &lt;x&gt;"));
        // The footer rows.
        assertTrue(sheet.contains("Net paye"));
    }

    /** Unzips the workbook into a name-to-content map. */
    private static Map<String, String> unzip(byte[] bytes) {
        Map<String, String> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                parts.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return parts;
    }
}
