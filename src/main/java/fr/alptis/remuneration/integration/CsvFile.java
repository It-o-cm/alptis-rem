package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed CSV file following the common conventions of specification section 9.9: UTF-8, semicolon
 * separator, mandatory lowercase header on the first line, double quotes around a field that
 * contains a separator, free column order. Empty means absent (never {@code NULL}).
 */
public final class CsvFile {

    /** Column names from the header, in file order. */
    private final List<String> headers;

    /** Data rows, each mapping a header to its value. */
    private final List<Map<String, String>> rows;

    /**
     * Creates a parsed file.
     *
     * @param headers the header column names
     * @param rows    the data rows keyed by header
     */
    private CsvFile(List<String> headers, List<Map<String, String>> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    /**
     * Parses the given CSV content.
     *
     * @param content the raw file content
     * @return the parsed file
     * @throws ValidationException if the file has no header line
     */
    public static CsvFile parse(String content) {
        List<String> lines = splitLines(content);
        if (lines.isEmpty()) {
            throw new ValidationException("Fichier vide : la ligne d'en-tête est obligatoire.");
        }
        List<String> headers = parseLine(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            List<String> values = parseLine(lines.get(i));
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                row.put(headers.get(c), c < values.size() ? values.get(c) : "");
            }
            rows.add(row);
        }
        return new CsvFile(headers, rows);
    }

    /**
     * Splits content into non-empty lines, tolerating both LF and CRLF endings.
     *
     * @param content the raw content
     * @return the lines, without trailing empty lines
     */
    private static List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>();
        for (String line : content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Parses one CSV line into fields, honouring double-quoted fields.
     *
     * @param line the raw line
     * @return the field values
     */
    private static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ';') {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }

    /**
     * Returns the header column names.
     *
     * @return the headers
     */
    public List<String> headers() {
        return headers;
    }

    /**
     * Returns the data rows.
     *
     * @return the rows
     */
    public List<Map<String, String>> rows() {
        return rows;
    }
}
