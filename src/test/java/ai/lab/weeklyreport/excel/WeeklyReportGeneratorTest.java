package ai.lab.weeklyreport.excel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import ai.lab.weeklyreport.metric.DivisionActivityTotal;
import ai.lab.weeklyreport.metric.DivisionMetricTotal;
import ai.lab.weeklyreport.metric.DivisionRegistryRow;
import ai.lab.weeklyreport.metric.MetricDailyTotal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WeeklyReportGeneratorTest {

    private final WeeklyReportGenerator generator = new WeeklyReportGenerator();

    private static final DivisionsReportData EMPTY_DIVISIONS_DATA = new DivisionsReportData(List.of(), List.of(), List.of());

    @Test
    void generatesFourSheetsWithCorrectTotalsAndDynamics() throws IOException {
        WeekRange currentWeek = new WeekRange(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19));
        WeekRange previousWeek = currentWeek.previousWeek();

        List<MetricDailyTotal> currentTotals = List.of(
                new MetricDailyTotal(LocalDate.of(2026, 7, 13), 11, BigDecimal.valueOf(100)), // Daribar, кол-во
                new MetricDailyTotal(LocalDate.of(2026, 7, 13), 12, BigDecimal.valueOf(5000)), // Daribar, сумма
                new MetricDailyTotal(LocalDate.of(2026, 7, 14), 11, BigDecimal.valueOf(50)),
                new MetricDailyTotal(LocalDate.of(2026, 7, 13), 1, BigDecimal.valueOf(20)), // ПЛ core, новых клиентов
                new MetricDailyTotal(LocalDate.of(2026, 7, 13), 6, BigDecimal.valueOf(5))   // ПЛ Janymda, новых клиентов
        );
        List<MetricDailyTotal> previousTotals = List.of(
                new MetricDailyTotal(LocalDate.of(2026, 7, 6), 11, BigDecimal.valueOf(80))
        );

        byte[] workbookBytes = generator.generate(currentWeek, currentTotals, previousWeek, previousTotals, EMPTY_DIVISIONS_DATA);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(sheetNames(workbook)).containsExactly("Маркетплейсы", "Программа лояльности", "Сводный", "Дивизионы");

            Sheet marketplaces = workbook.getSheet("Маркетплейсы");
            Row marketplaceTotalRow = findRowByLabel(marketplaces, "ИТОГО за неделю");
            assertThat(numeric(marketplaceTotalRow, 1)).isEqualTo(150.0); // Daribar, кол-во: 100 + 50
            assertThat(numeric(marketplaceTotalRow, 2)).isEqualTo(5000.0); // Daribar, сумма

            Sheet loyalty = workbook.getSheet("Программа лояльности");
            Row loyaltyTotalRow = findRowByLabel(loyalty, "ИТОГО за неделю");
            assertThat(numeric(loyaltyTotalRow, 1)).isEqualTo(25.0); // core(20) + janymda(5)

            Sheet summary = workbook.getSheet("Сводный");
            Row daribarSummaryRow = findRowByLabel(summary, "Daribar, кол-во");
            assertThat(numeric(daribarSummaryRow, 1)).isEqualTo(150.0); // текущая неделя
            assertThat(numeric(daribarSummaryRow, 2)).isEqualTo(80.0);  // прошлая неделя
            assertThat(numeric(daribarSummaryRow, 3)).isEqualTo(70.0);  // динамика
        }
    }

    @Test
    void buildsDivisionsSheetWithNetworkTotalsAsRatioOfSums() throws IOException {
        WeekRange currentWeek = new WeekRange(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19));
        WeekRange previousWeek = currentWeek.previousWeek();

        List<DivisionRegistryRow> registry = List.of(
                new DivisionRegistryRow("1", "Иванов", 10, "Алматы"),
                new DivisionRegistryRow("2", "Петров", 100, "Астана"));

        List<DivisionMetricTotal> metricTotals = List.of(
                new DivisionMetricTotal("1", 1, BigDecimal.valueOf(20)),  // новых клиентов, ПЛ core
                new DivisionMetricTotal("1", 3, BigDecimal.valueOf(1000)), // начислено, ПЛ core
                new DivisionMetricTotal("1", 11, BigDecimal.valueOf(100)), // Daribar, кол-во
                new DivisionMetricTotal("1", 12, BigDecimal.valueOf(5000)), // Daribar, сумма
                new DivisionMetricTotal("2", 1, BigDecimal.valueOf(10)),
                new DivisionMetricTotal("2", 3, BigDecimal.valueOf(500)),
                new DivisionMetricTotal("2", 13, BigDecimal.valueOf(50)),  // Glovo, кол-во
                new DivisionMetricTotal("2", 14, BigDecimal.valueOf(2500)), // Glovo, сумма
                new DivisionMetricTotal(null, 1, BigDecimal.valueOf(1)),
                new DivisionMetricTotal(null, 3, BigDecimal.valueOf(50)),
                new DivisionMetricTotal(null, 11, BigDecimal.valueOf(1)),
                new DivisionMetricTotal(null, 12, BigDecimal.valueOf(100)));

        // pharmacyCount здесь намеренно отличается от registry.pharmacyCount() для división 1/2 -
        // если генератор ошибочно возьмёт знаменатель отсюда вместо справочника, тест это поймает.
        List<DivisionActivityTotal> activityTotals = List.of(
                new DivisionActivityTotal("1", 8, 5),
                new DivisionActivityTotal("2", 95, 90),
                new DivisionActivityTotal(null, 5, 5));

        DivisionsReportData divisionsData = new DivisionsReportData(registry, metricTotals, activityTotals);

        byte[] workbookBytes = generator.generate(currentWeek, List.of(), previousWeek, List.of(), divisionsData);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet sheet = workbook.getSheet("Дивизионы");

            Row division1 = findRowByLabel(sheet, "1");
            assertThat(numeric(division1, 2)).isEqualTo(10.0); // Кол-во аптек - из справочника
            assertThat(numeric(division1, 4)).isEqualTo(20.0); // Новых клиентов
            assertThat(numeric(division1, 5)).isEqualTo(1000.0); // Начислено
            assertThat(numeric(division1, 6)).isCloseTo(0.5, within(1e-9)); // 5 активных / 10 в справочнике, НЕ /8
            assertThat(numeric(division1, 7)).isEqualTo(100.0); // Daribar, кол-во
            assertThat(numeric(division1, 8)).isEqualTo(5000.0); // Daribar, сумма
            assertThat(numeric(division1, 15)).isEqualTo(100.0); // МП ИТОГО, кол-во
            assertThat(numeric(division1, 16)).isEqualTo(5000.0); // МП ИТОГО, сумма

            Row division2 = findRowByLabel(sheet, "2");
            assertThat(numeric(division2, 6)).isCloseTo(0.9, within(1e-9)); // 90 / 100 в справочнике, НЕ /95

            Row withoutDivision = findRowByLabel(sheet, "Без дивизиона / склад");
            assertThat(withoutDivision.getCell(1).getStringCellValue()).isEqualTo("-");
            assertThat(numeric(withoutDivision, 2)).isEqualTo(5.0);
            assertThat(numeric(withoutDivision, 6)).isCloseTo(1.0, within(1e-9)); // 5 / 5 (сам себе знаменатель)

            Row total = findRowByLabel(sheet, "ИТОГО ПО СЕТИ");
            assertThat(numeric(total, 2)).isEqualTo(115.0); // 10 + 100 + 5
            assertThat(numeric(total, 4)).isEqualTo(31.0);  // 20 + 10 + 1
            assertThat(numeric(total, 5)).isEqualTo(1550.0); // 1000 + 500 + 50
            // Отношение сумм (5+90+5)/(10+100+5), НЕ среднее процентов (50%+90%)/2=70%.
            assertThat(numeric(total, 6)).isCloseTo(100.0 / 115.0, within(1e-9));
            assertThat(numeric(total, 7)).isEqualTo(101.0); // Daribar: 100 + 0 + 1
            assertThat(numeric(total, 8)).isEqualTo(5100.0);
            assertThat(numeric(total, 9)).isEqualTo(50.0);  // Glovo: 0 + 50 + 0
            assertThat(numeric(total, 10)).isEqualTo(2500.0);
            assertThat(numeric(total, 15)).isEqualTo(151.0); // МП ИТОГО, кол-во: 101 + 50
            assertThat(numeric(total, 16)).isEqualTo(7600.0); // МП ИТОГО, сумма: 5100 + 2500
        }
    }

    private static List<String> sheetNames(XSSFWorkbook workbook) {
        return java.util.stream.IntStream.range(0, workbook.getNumberOfSheets())
                .mapToObj(workbook::getSheetName)
                .toList();
    }

    private static Row findRowByLabel(Sheet sheet, String label) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && label.equals(row.getCell(0).getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("Строка с меткой '" + label + "' не найдена на листе " + sheet.getSheetName());
    }

    private static double numeric(Row row, int col) {
        return row.getCell(col).getNumericCellValue();
    }
}
