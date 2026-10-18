package fr.alptis.remuneration.documents;

import fr.alptis.remuneration.documents.StatementService.Row;
import fr.alptis.remuneration.documents.StatementService.StatementContent;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Renders a statement's content (section 9.5) as the two real documents the specification requires
 * (BRD-01): a PDF and a spreadsheet. Both are produced by hand, without any external library, so the
 * dev profile needs no extra dependency: a PDF is a plain object-and-xref text format, and an
 * {@code .xlsx} spreadsheet is a ZIP of OOXML parts the JDK can write with {@link ZipOutputStream}.
 * All methods are pure (content in, bytes out) so they are tested without a database.
 */
final class StatementDocuments {

    /** DOS date for 1980-01-01, so a workbook is reproducible and never reads the clock. */
    private static final int DOS_DATE = 0x0021;

    /** DOS time for midnight, paired with {@link #DOS_DATE}. */
    private static final int DOS_TIME = 0x0000;

    /** Page height in PDF points (A4), from which text lines are laid out top-down. */
    private static final int PAGE_HEIGHT = 842;

    /** Column headers of the spreadsheet, matching the CSV export. */
    private static final List<String> HEADERS =
            List.of("rubrique", "libelle", "assiette", "taux", "montant", "motif");

    /** Not instantiable. */
    private StatementDocuments() {
    }

    /**
     * Renders the statement as a one-page PDF: a header, one line per rubric row and the footer
     * totals of section 9.5. Text is encoded in Latin-1 under the font's WinAnsi encoding so French
     * accents render.
     *
     * @param content the statement content
     * @return the PDF bytes
     */
    static byte[] pdf(StatementContent content) {
        return pdf(content, null, null);
    }

    /**
     * Renders the statement as a PDF applying a document template's header (BRD-05/BRD-07): the template
     * logo and mentions are drawn above the statement body.
     *
     * @param content  the statement content
     * @param logo     the template logo text, or null
     * @param mentions the template mentions text, or null
     * @return the PDF bytes
     */
    static byte[] pdf(StatementContent content, String logo, String mentions) {
        byte[] stream = contentStream(content, logo, mentions);
        List<byte[]> objects = new ArrayList<>();
        objects.add(ascii("<</Type/Catalog/Pages 2 0 R>>"));
        objects.add(ascii("<</Type/Pages/Kids[3 0 R]/Count 1>>"));
        objects.add(ascii("<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 " + PAGE_HEIGHT
                + "]/Resources<</Font<</F1 5 0 R>>>>/Contents 4 0 R>>"));
        objects.add(concat(ascii("<</Length " + stream.length + ">>\nstream\n"), stream, ascii("\nendstream")));
        objects.add(ascii("<</Type/Font/Subtype/Type1/BaseFont/Helvetica/Encoding/WinAnsiEncoding>>"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, ascii("%PDF-1.4\n"));
        int[] offsets = new int[objects.size() + 1];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i + 1] = out.size();
            write(out, ascii((i + 1) + " 0 obj\n"));
            write(out, objects.get(i));
            write(out, ascii("\nendobj\n"));
        }
        int xref = out.size();
        write(out, ascii("xref\n0 " + (objects.size() + 1) + "\n"));
        write(out, ascii("0000000000 65535 f \n"));
        for (int i = 1; i <= objects.size(); i++) {
            write(out, ascii(String.format("%010d 00000 n \n", offsets[i])));
        }
        write(out, ascii("trailer\n<</Size " + (objects.size() + 1) + "/Root 1 0 R>>\nstartxref\n" + xref + "\n%%EOF"));
        return out.toByteArray();
    }

    /**
     * Builds the PDF content stream: a text block drawing the statement lines top-down.
     *
     * @param content the statement content
     * @return the content-stream bytes
     */
    private static byte[] contentStream(StatementContent content, String logo, String mentions) {
        List<String> lines = new ArrayList<>();
        if (logo != null) {
            lines.add(logo);
        }
        if (mentions != null) {
            lines.add(mentions);
        }
        lines.add("Bordereau " + content.statementNumber()
                + (content.versionNo() == null ? "" : " v" + content.versionNo()));
        lines.add("Beneficiaire : " + content.beneficiary());
        lines.add("Periode : " + content.month());
        lines.add("");
        for (Row row : content.rows()) {
            lines.add(row.section() + " | " + row.label() + " | " + plain(row.amount()));
        }
        lines.add("");
        lines.add("Total de la periode : " + plain(content.periodTotal()));
        lines.add("Solde anterieur reporte : " + plain(content.carried()));
        lines.add("Net paye : " + plain(content.net()));

        StringBuilder sb = new StringBuilder("BT\n/F1 11 Tf\n50 800 Td\n14 TL\n");
        for (String line : lines) {
            sb.append('(').append(escapePdf(line)).append(") Tj\nT*\n");
        }
        sb.append("ET");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Renders the statement as a minimal {@code .xlsx} workbook: one sheet whose first row carries the
     * column headers, one row per rubric line and three footer rows for the totals.
     *
     * @param content the statement content
     * @return the workbook bytes
     */
    static byte[] workbook(StatementContent content) {
        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> header = new ArrayList<>();
        for (String h : HEADERS) {
            header.add(Cell.text(h));
        }
        rows.add(header);
        for (Row row : content.rows()) {
            rows.add(List.of(Cell.text(row.section()), Cell.text(row.label()), Cell.number(row.basis()),
                    Cell.number(row.rate()), Cell.number(row.amount()),
                    Cell.text(row.reason() == null ? "" : row.reason())));
        }
        rows.add(footer("Total de la periode", content.periodTotal()));
        rows.add(footer("Solde anterieur reporte", content.carried()));
        rows.add(footer("Net paye", content.net()));

        List<Part> parts = List.of(
                new Part("[Content_Types].xml", CONTENT_TYPES),
                new Part("_rels/.rels", RELS),
                new Part("xl/workbook.xml", WORKBOOK),
                new Part("xl/_rels/workbook.xml.rels", WORKBOOK_RELS),
                new Part("xl/worksheets/sheet1.xml", sheet(rows)));
        return zip(parts);
    }

    /**
     * Assembles the parts into a ZIP archive using stored (uncompressed) entries, which is a valid
     * {@code .xlsx} container. Built by hand so no checked I/O exception can arise.
     *
     * @param parts the archive parts
     * @return the ZIP bytes
     */
    private static byte[] zip(List<Part> parts) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        ByteArrayOutputStream central = new ByteArrayOutputStream();
        int[] offsets = new int[parts.size()];
        long[] crcs = new long[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            byte[] name = part.name().getBytes(StandardCharsets.UTF_8);
            byte[] data = part.content().getBytes(StandardCharsets.UTF_8);
            CRC32 crc = new CRC32();
            crc.update(data);
            offsets[i] = body.size();
            crcs[i] = crc.getValue();
            write(body, le32(0x04034b50));
            write(body, localFields(name.length, data.length, crcs[i]));
            write(body, name);
            write(body, data);
        }
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            byte[] name = part.name().getBytes(StandardCharsets.UTF_8);
            byte[] data = part.content().getBytes(StandardCharsets.UTF_8);
            write(central, le32(0x02014b50));
            write(central, le16(20));
            write(central, localFields(name.length, data.length, crcs[i]));
            write(central, le16(0));
            write(central, le16(0));
            write(central, le16(0));
            write(central, le32(0));
            write(central, le32(offsets[i]));
            write(central, name);
        }
        int centralOffset = body.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, body.toByteArray());
        write(out, central.toByteArray());
        write(out, le32(0x06054b50));
        write(out, le16(0));
        write(out, le16(0));
        write(out, le16(parts.size()));
        write(out, le16(parts.size()));
        write(out, le32(central.size()));
        write(out, le32(centralOffset));
        write(out, le16(0));
        return out.toByteArray();
    }

    /**
     * Builds the common header fields shared by a local and a central ZIP entry: version, flags,
     * stored method, fixed time/date, CRC and the (equal) sizes, filename length and empty extra.
     *
     * @param nameLength the filename length
     * @param dataLength the content length
     * @param crc        the CRC-32 of the content
     * @return the field bytes
     */
    private static byte[] localFields(int nameLength, int dataLength, long crc) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, le16(20));
        write(out, le16(0));
        write(out, le16(0));
        write(out, le16(DOS_TIME));
        write(out, le16(DOS_DATE));
        write(out, le32((int) crc));
        write(out, le32(dataLength));
        write(out, le32(dataLength));
        write(out, le16(nameLength));
        write(out, le16(0));
        return out.toByteArray();
    }

    /**
     * Encodes an integer as four little-endian bytes.
     *
     * @param value the value
     * @return the bytes
     */
    private static byte[] le32(int value) {
        return new byte[]{(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)};
    }

    /**
     * Encodes the low 16 bits of a value as two little-endian bytes.
     *
     * @param value the value
     * @return the bytes
     */
    private static byte[] le16(int value) {
        return new byte[]{(byte) value, (byte) (value >> 8)};
    }

    /**
     * Builds a two-cell footer row: a label and its amount.
     *
     * @param label  the footer label
     * @param amount the amount
     * @return the row cells
     */
    private static List<Cell> footer(String label, BigDecimal amount) {
        return List.of(Cell.text(label), Cell.text(""), Cell.text(""), Cell.text(""), Cell.number(amount),
                Cell.text(""));
    }

    /**
     * Serialises the sheet rows to the worksheet XML.
     *
     * @param rows the rows, each a list of cells
     * @return the worksheet XML
     */
    private static String sheet(List<List<Cell>> rows) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            sb.append("<row r=\"").append(r + 1).append("\">");
            List<Cell> cells = rows.get(r);
            for (int c = 0; c < cells.size(); c++) {
                sb.append(cells.get(c).xml(column(c) + (r + 1)));
            }
            sb.append("</row>");
        }
        return sb.append("</sheetData></worksheet>").toString();
    }

    /**
     * Returns the spreadsheet column letter for a zero-based index (0 to 25 give A to Z).
     *
     * @param index the zero-based column index
     * @return the column letter
     */
    private static String column(int index) {
        return String.valueOf((char) ('A' + index));
    }

    /**
     * Escapes a string for a PDF literal: backslash, parentheses are prefixed with a backslash.
     *
     * @param text the raw text
     * @return the escaped text
     */
    private static String escapePdf(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\' || ch == '(' || ch == ')') {
                sb.append('\\');
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Escapes a string for XML text: ampersand, angle brackets and quotes are replaced by entities.
     *
     * @param text the raw text
     * @return the escaped text
     */
    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * Formats a nullable decimal as a plain string, empty when null.
     *
     * @param value the value, possibly null
     * @return the plain string, or empty
     */
    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /**
     * Encodes a string to ASCII bytes for the PDF structure.
     *
     * @param text the text
     * @return the bytes
     */
    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Concatenates byte arrays.
     *
     * @param parts the parts
     * @return the concatenation
     */
    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            write(out, part);
        }
        return out.toByteArray();
    }

    /**
     * Writes bytes to a buffer, wrapping the impossible in-memory failure.
     *
     * @param out   the buffer
     * @param bytes the bytes
     */
    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    /**
     * One ZIP archive part.
     *
     * @param name    the entry name
     * @param content the entry content
     */
    private record Part(String name, String content) {
    }

    /** A spreadsheet cell: either inline text or a numeric value. */
    private record Cell(String text, BigDecimal number) {

        /**
         * A text cell.
         *
         * @param value the text
         * @return the cell
         */
        static Cell text(String value) {
            return new Cell(value, null);
        }

        /**
         * A numeric cell, or an empty text cell when the value is null.
         *
         * @param value the number, possibly null
         * @return the cell
         */
        static Cell number(BigDecimal value) {
            return value == null ? new Cell("", null) : new Cell(null, value);
        }

        /**
         * Serialises the cell to its worksheet XML at the given reference.
         *
         * @param ref the cell reference (e.g. A1)
         * @return the cell XML
         */
        String xml(String ref) {
            if (number != null) {
                return "<c r=\"" + ref + "\"><v>" + number.toPlainString() + "</v></c>";
            }
            return "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + escapeXml(text)
                    + "</t></is></c>";
        }
    }

    /** The {@code [Content_Types].xml} part. */
    private static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument"
            + ".spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-"
            + "officedocument.spreadsheetml.worksheet+xml\"/></Types>";

    /** The package {@code _rels/.rels} part. */
    private static final String RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
            + "officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>";

    /** The {@code xl/workbook.xml} part. */
    private static final String WORKBOOK = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"Bordereau\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";

    /** The {@code xl/_rels/workbook.xml.rels} part. */
    private static final String WORKBOOK_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/"
            + "worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>";
}
