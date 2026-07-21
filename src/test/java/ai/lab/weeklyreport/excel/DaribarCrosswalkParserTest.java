package ai.lab.weeklyreport.excel;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import ai.lab.weeklyreport.metric.DaribarCrosswalkEntry;

import static org.assertj.core.api.Assertions.assertThat;

class DaribarCrosswalkParserTest {

    private final DaribarCrosswalkParser parser = new DaribarCrosswalkParser();

    @Test
    void parsesFixtureIntoExpectedEntries() throws IOException {
        List<DaribarCrosswalkEntry> entries;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/daribar_crosswalk_test.xlsx")) {
            entries = parser.parse(Objects.requireNonNull(in, "тестовая фикстура не найдена на classpath"));
        }

        // 4 строки данных (включая дубль daribarCode=1002); строка с пустым "Код аптеки в Daribar"
        // в середине листа пропускается (не останавливает разбор - в реальных файлах такие строки
        // означают "аптека ещё не сопоставлена с Daribar", а не конец данных), поэтому валидная
        // строка ПОСЛЕ неё (daribarCode=1004) всё равно попадает в результат. "Мусорная" строка
        // после настоящего разрыва (отсутствующей строки) в результат не попадает.
        assertThat(entries).hasSize(4);
        assertThat(entries).noneMatch(e -> e.daribarCode().equals("9999"));
        assertThat(entries).noneMatch(e -> e.branchCode().equals("F5")); // пропущенная строка

        DaribarCrosswalkEntry first = entries.get(0);
        assertThat(first.daribarCode()).isEqualTo("1001");
        assertThat(first.branchCode()).isEqualTo("F1");
        assertThat(first.pharmacyName()).isEqualTo("Apteka F1 Daribar");

        // Дубль daribarCode=1002 не отбрасывается парсером - обе строки возвращаются как есть,
        // в порядке файла; "последняя побеждает" проверяется на уровне репозитория.
        List<DaribarCrosswalkEntry> duplicates = entries.stream()
                .filter(e -> e.daribarCode().equals("1002"))
                .toList();
        assertThat(duplicates).hasSize(2);
        assertThat(duplicates.get(0).branchCode()).isEqualTo("F2");
        assertThat(duplicates.get(1).branchCode()).isEqualTo("F2B");
        assertThat(duplicates.get(1).comment()).isEqualTo("дубликат кода");

        DaribarCrosswalkEntry afterGap = entries.stream().filter(e -> e.daribarCode().equals("1004")).findFirst().orElseThrow();
        assertThat(afterGap.branchCode()).isEqualTo("F6");
    }
}
