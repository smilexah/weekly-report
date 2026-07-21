package ai.lab.weeklyreport.excel;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import ai.lab.weeklyreport.metric.PharmacyDirectoryEntry;

import static org.assertj.core.api.Assertions.assertThat;

class DivisionsFileParserTest {

    private final DivisionsFileParser parser = new DivisionsFileParser();

    @Test
    void parsesFixtureIntoExpectedEntries() throws IOException {
        List<PharmacyDirectoryEntry> entries;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/divisions_test.xlsx")) {
            entries = parser.parse(Objects.requireNonNull(in, "тестовая фикстура не найдена на classpath"));
        }

        // 3 строки данных; пустой "Код" в строке 5 останавливает разбор, "мусорная" строка 6 не попадает в результат.
        assertThat(entries).hasSize(3);
        assertThat(entries).noneMatch(e -> e.branchCode().equals("F9"));

        PharmacyDirectoryEntry first = entries.get(0);
        assertThat(first.branchCode()).isEqualTo("F1");
        assertThat(first.address()).isEqualTo("ул. Абая, 1");
        assertThat(first.city()).isEqualTo("Алматы");
        assertThat(first.divisionNum()).isEqualTo("1");
        assertThat(first.directorName()).isEqualTo("Иванов Иван");

        PharmacyDirectoryEntry third = entries.get(2);
        assertThat(third.branchCode()).isEqualTo("F3");
        assertThat(third.city()).isEqualTo("Астана");
        assertThat(third.divisionNum()).isEqualTo("2");
        assertThat(third.directorName()).isEqualTo("Петров Пётр");
    }
}
