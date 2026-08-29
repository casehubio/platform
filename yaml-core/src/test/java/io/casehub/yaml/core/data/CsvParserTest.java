package io.casehub.yaml.core.data;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvParserTest {

    @Test
    void parses_string_columns() {
        String csv = """
                name:STRING,region:STRING
                alpha,us-east
                beta,eu-west
                """;
        CsvDataSource ds = CsvParser.parse("envs", csv);
        assertThat(ds.name()).isEqualTo("envs");
        assertThat(ds.columns()).containsExactly(
                new CsvColumn("name", CsvColumnType.STRING),
                new CsvColumn("region", CsvColumnType.STRING));
        assertThat(ds.rows()).hasSize(2);
        assertThat(ds.rows().get(0)).containsEntry("name", "alpha")
                .containsEntry("region", "us-east");
        assertThat(ds.rows().get(1)).containsEntry("name", "beta")
                .containsEntry("region", "eu-west");
    }

    @Test
    void parses_integer_column() {
        String csv = """
                name:STRING,port:INTEGER
                web,8080
                api,9090
                """;
        CsvDataSource ds = CsvParser.parse("services", csv);
        assertThat(ds.rows().get(0)).containsEntry("port", 8080);
        assertThat(ds.rows().get(1)).containsEntry("port", 9090);
    }

    @Test
    void parses_boolean_column_via_truthiness() {
        String csv = """
                name:STRING,enabled:BOOLEAN
                feature-a,true
                feature-b,no
                feature-c,1
                """;
        CsvDataSource ds = CsvParser.parse("flags", csv);
        assertThat(ds.rows().get(0)).containsEntry("enabled", true);
        assertThat(ds.rows().get(1)).containsEntry("enabled", false);
        assertThat(ds.rows().get(2)).containsEntry("enabled", true);
    }

    @Test
    void parses_decimal_column() {
        String csv = """
                name:STRING,rate:DECIMAL
                gold,0.05
                silver,1.5
                """;
        CsvDataSource ds = CsvParser.parse("tiers", csv);
        assertThat(ds.rows().get(0)).containsEntry("rate", 0.05);
        assertThat(ds.rows().get(1)).containsEntry("rate", 1.5);
    }

    @Test
    void parses_mixed_types() {
        String csv = """
                name:STRING,port:INTEGER,enabled:BOOLEAN,rate:DECIMAL
                web,8080,true,0.95
                """;
        CsvDataSource ds = CsvParser.parse("mixed", csv);
        Map<String, Object> row = ds.rows().get(0);
        assertThat(row).containsEntry("name", "web")
                .containsEntry("port", 8080)
                .containsEntry("enabled", true)
                .containsEntry("rate", 0.95);
    }

    @Test
    void header_only_returns_empty_rows() {
        String csv = "name:STRING,port:INTEGER\n";
        CsvDataSource ds = CsvParser.parse("empty", csv);
        assertThat(ds.columns()).hasSize(2);
        assertThat(ds.rows()).isEmpty();
    }

    @Test
    void trims_whitespace_from_values() {
        String csv = """
                name:STRING , port:INTEGER
                 alpha , 8080
                """;
        CsvDataSource ds = CsvParser.parse("trimmed", csv);
        assertThat(ds.columns().get(0)).isEqualTo(new CsvColumn("name", CsvColumnType.STRING));
        assertThat(ds.rows().get(0)).containsEntry("name", "alpha")
                .containsEntry("port", 8080);
    }

    @Test
    void skips_blank_lines() {
        String csv = """
                name:STRING
                alpha

                beta
                """;
        CsvDataSource ds = CsvParser.parse("blanks", csv);
        assertThat(ds.rows()).hasSize(2);
        assertThat(ds.rows().get(0)).containsEntry("name", "alpha");
        assertThat(ds.rows().get(1)).containsEntry("name", "beta");
    }

    @Test
    void integer_parse_error_includes_row_and_column() {
        String csv = """
                name:STRING,port:INTEGER
                web,not-a-number
                """;
        assertThatThrownBy(() -> CsvParser.parse("bad", csv))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row 1")
                .hasMessageContaining("port")
                .hasMessageContaining("INTEGER");
    }

    @Test
    void decimal_parse_error_includes_row_and_column() {
        String csv = """
                name:STRING,rate:DECIMAL
                web,abc
                """;
        assertThatThrownBy(() -> CsvParser.parse("bad", csv))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row 1")
                .hasMessageContaining("rate")
                .hasMessageContaining("DECIMAL");
    }

    @Test
    void boolean_parse_error_includes_row_and_column() {
        String csv = """
                name:STRING,enabled:BOOLEAN
                web,maybe
                """;
        assertThatThrownBy(() -> CsvParser.parse("bad", csv))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row 1")
                .hasMessageContaining("enabled");
    }

    @Test
    void wrong_column_count_in_row_throws() {
        String csv = """
                name:STRING,port:INTEGER
                web
                """;
        assertThatThrownBy(() -> CsvParser.parse("short", csv))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("row 1")
                .hasMessageContaining("2 columns")
                .hasMessageContaining("1");
    }

    @Test
    void unknown_column_type_throws() {
        String csv = "name:BLOB\nalpha\n";
        assertThatThrownBy(() -> CsvParser.parse("bad", csv))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BLOB");
    }

    @Test
    void empty_content_throws() {
        assertThatThrownBy(() -> CsvParser.parse("empty", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whitespace_only_content_throws() {
        assertThatThrownBy(() -> CsvParser.parse("empty", "   \n  \n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missing_type_in_header_throws() {
        String csv = "name\nalpha\n";
        assertThatThrownBy(() -> CsvParser.parse("bad", csv))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("columnName:TYPE");
    }

    @Test
    void type_is_case_insensitive() {
        String csv = """
                name:string,port:integer
                web,8080
                """;
        CsvDataSource ds = CsvParser.parse("lower", csv);
        assertThat(ds.columns().get(0).type()).isEqualTo(CsvColumnType.STRING);
        assertThat(ds.rows().get(0)).containsEntry("port", 8080);
    }

    @Test
    void rows_are_unmodifiable() {
        String csv = """
                name:STRING
                alpha
                """;
        CsvDataSource ds = CsvParser.parse("immut", csv);
        assertThatThrownBy(() -> ds.rows().get(0).put("name", "hacked"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
