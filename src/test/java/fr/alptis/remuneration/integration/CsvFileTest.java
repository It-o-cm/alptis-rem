package fr.alptis.remuneration.integration;

import fr.alptis.remuneration.domain.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link CsvFile}, covering the section 9.9 conventions.
 */
class CsvFileTest {

    /**
     * Parses a header and rows, honouring quoted fields and short rows.
     */
    @Test
    void parses_header_rows_and_quoted_fields() {
        String content = "a;b;c\r\n1;\"x;y\";3\n4;5\n";
        CsvFile csv = CsvFile.parse(content);

        assertEquals(3, csv.headers().size());
        assertEquals(2, csv.rows().size());
        assertEquals("x;y", csv.rows().get(0).get("b"));
        assertEquals("3", csv.rows().get(0).get("c"));
        // A short row leaves the missing trailing column empty.
        assertEquals("", csv.rows().get(1).get("c"));
    }

    /**
     * Honours doubled quotes inside a quoted field.
     */
    @Test
    void parses_escaped_quotes() {
        CsvFile csv = CsvFile.parse("a\n\"he said \"\"hi\"\"\"\n");
        assertEquals("he said \"hi\"", csv.rows().get(0).get("a"));
    }

    /**
     * Rejects a file without a header line.
     */
    @Test
    void rejects_an_empty_file() {
        assertThrows(ValidationException.class, () -> CsvFile.parse(""));
    }

    /**
     * A header-only file yields no rows (an expected empty file).
     */
    @Test
    void accepts_a_header_only_file() {
        CsvFile csv = CsvFile.parse("a;b\n");
        assertEquals(0, csv.rows().size());
    }

    /**
     * A row with more fields than the header is malformed (a structural error); a matching or shorter
     * row is not.
     */
    @Test
    void flags_a_row_with_more_fields_than_the_header() {
        CsvFile csv = CsvFile.parse("a;b\n1;2\n3;4;5\n6\n");
        assertEquals(false, csv.malformed(0));
        assertEquals(true, csv.malformed(1));
        assertEquals(false, csv.malformed(2));
    }
}
