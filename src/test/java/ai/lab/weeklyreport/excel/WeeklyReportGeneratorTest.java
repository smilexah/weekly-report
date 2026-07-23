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

    // Sheet "1. Маркетплейсы": колонка 0 - канал, затем пары (Заказов,Сумма) на каждый день недели
    // (пн=1..вс=7), последняя пара (15,16) - "ИТОГО" за неделю по каналу.
    private static final int MP_COL_MON_COUNT = 1;
    private static final int MP_COL_MON_SUM = 2;
    private static final int MP_COL_TOTAL_COUNT = 15;
    private static final int MP_COL_TOTAL_SUM = 16;

    // Sheet "2. Программа лояльности": колонка 0 - показатель, 1..7 - дни недели, 8 - "ИТОГО".
    private static final int PL_COL_MON = 1;
    private static final int PL_COL_TOTAL = 8;

    @Test
    void generatesFourSheetsInDesignOrder() throws IOException {
        WeekRange currentWeek = new WeekRange(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19));
        WeekRange previousWeek = currentWeek.previousWeek();

        byte[] workbookBytes = generator.generate(currentWeek, List.of(), previousWeek, List.of(), EMPTY_DIVISIONS_DATA);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(sheetNames(workbook)).containsExactly(
                    "1. Маркетплейсы", "2. Программа лояльности", "3. Дивизионы", "4. Сводный");
        }
    }

    @Test
    void marketplaceSheetPivotsChannelsAsRowsAndDaysAsColumnsWithWeeklyTotal() throws IOException {
        WeekRange currentWeek = new WeekRange(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19));
        WeekRange previousWeek = currentWeek.previousWeek();

        List<MetricDailyTotal> currentTotals = List.of(
                new MetricDailyTotal(LocalDate.of(2026, 7, 13), 11, BigDecimal.valueOf(100)), // Daribar, кол-во, пн
                new MetricDailyTotal(LocalDate.of(2026, 7, 13), 12, BigDecimal.valueOf(5000)), // Daribar, сумма, пн
                new MetricDailyTotal(LocalDate.of(2026, 7, 14), 11, BigDecimal.valueOf(50))     // Daribar, кол-во, вт
        );

        byte[] workbookBytes = generator.generate(currentWeek, currentTotals, previousWeek, List.of(), EMPTY_DIVISIONS_DATA);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet marketplaces = workbook.getSheet("1. Маркетплейсы");

            Row daribarRow = findRowByLabel(marketplaces, "Daribar");
            assertThat(numeric(daribarRow, MP_COL_MON_COUNT)).isEqualTo(100.0);
            assertThat(numeric(daribarRow, MP_COL_MON_SUM)).isEqualTo(5000.0);
            assertThat(numeric(daribarRow, MP_COL_TOTAL_COUNT)).isEqualTo(150.0); // 100 (пн) + 50 (вт)
            assertThat(numeric(daribarRow, MP_COL_TOTAL_SUM)).isEqualTo(5000.0);

            Row totalRow = findRowByLabel(marketplaces, "ИТОГО МП");
            assertThat(numeric(totalRow, MP_COL_MON_COUNT)).isEqualTo(100.0);
            assertThat(numeric(totalRow, MP_COL_TOTAL_COUNT)).isEqualTo(150.0);
            assertThat(numeric(totalRow, MP_COL_TOTAL_SUM)).isEqualTo(5000.0);
        }
    }

    @Test
    void loyaltySheetPivotsMeasuresAsRowsAndSumsCoreAndJanymdaPerDay() throws IOException {
        WeekRange currentWeek = new WeekRange(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19));
        WeekRange previousWeek = currentWeek.previousWeek();

        List<MetricDailyTotal> currentTotals = List.of(
                new MetricDailyTotal(LocalDate.of(2026, 7, 13), 1, BigDecimal.valueOf(20)), // ПЛ core, новых клиентов, пн
                new MetricDailyTotal(LocalDate.of(2026, 7, 13), 6, BigDecimal.valueOf(5)),  // ПЛ Janymda, новых клиентов, пн
                new MetricDailyTotal(LocalDate.of(2026, 7, 14), 1, BigDecimal.valueOf(3))   // ПЛ core, новых клиентов, вт
        );

        byte[] workbookBytes = generator.generate(currentWeek, currentTotals, previousWeek, List.of(), EMPTY_DIVISIONS_DATA);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet loyalty = workbook.getSheet("2. Программа лояльности");

            Row newClientsRow = findRowByLabel(loyalty, "Новых клиентов, чел.");
            assertThat(numeric(newClientsRow, PL_COL_MON)).isEqualTo(25.0); // core(20) + janymda(5)
            assertThat(numeric(newClientsRow, PL_COL_TOTAL)).isEqualTo(28.0); // 25 (пн) + 3 (вт)
        }
    }

    @Test
    void summarySheetComparesCurrentAndPreviousWeekTotals() throws IOException {
        WeekRange currentWeek = new WeekRange(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19));
        WeekRange previousWeek = currentWeek.previousWeek();

        List<MetricDailyTotal> currentTotals = List.of(
                new MetricDailyTotal(LocalDate.of(2026, 7, 13), 11, BigDecimal.valueOf(100)),
                new MetricDailyTotal(LocalDate.of(2026, 7, 14), 11, BigDecimal.valueOf(50)));
        List<MetricDailyTotal> previousTotals = List.of(
                new MetricDailyTotal(LocalDate.of(2026, 7, 6), 11, BigDecimal.valueOf(80)));

        byte[] workbookBytes = generator.generate(currentWeek, currentTotals, previousWeek, previousTotals, EMPTY_DIVISIONS_DATA);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet summary = workbook.getSheet("4. Сводный");

            Row daribarCountRow = findRowByLabel(summary, "Daribar — кол-во заказов");
            assertThat(numeric(daribarCountRow, 1)).isEqualTo(150.0); // текущая неделя
            assertThat(numeric(daribarCountRow, 2)).isEqualTo(80.0);  // прошлая неделя
            assertThat(numeric(daribarCountRow, 3)).isEqualTo(70.0);  // динамика

            Row ordersRow = findRowByLabel(summary, "Кол-во заказов МП — всего, шт");
            assertThat(numeric(ordersRow, 1)).isEqualTo(150.0);
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
            Sheet sheet = workbook.getSheet("3. Дивизионы");

            // Колонки: 0 номер, 1 директор, 2 кол-во аптек, 3 охват, 4 новых клиентов, 5 начислено,
            // 6 актив.%, 7-14 по каналам (Daribar/Glovo/Emdel/Wolt), 15-16 МП ИТОГО.
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
            assertThat(numeric(division2, 9)).isEqualTo(50.0); // Glovo, кол-во
            assertThat(numeric(division2, 10)).isEqualTo(2500.0); // Glovo, сумма

            // Аптеки без резолвленного дивизиона (divisionNum = null в тестовых данных) не должны
            // попадать на этот лист ни отдельной строкой, ни в "ИТОГО ПО СЕТИ".
            assertThat(findRowIndexByLabel(sheet, "Без дивизиона / склад")).isEmpty();

            Row total = findRowByLabel(sheet, "ИТОГО ПО СЕТИ");
            assertThat(numeric(total, 2)).isEqualTo(110.0); // 10 + 100, БЕЗ null-бакета
            assertThat(numeric(total, 4)).isEqualTo(30.0);  // 20 + 10
            assertThat(numeric(total, 5)).isEqualTo(1500.0); // 1000 + 500
            // Отношение сумм (5+90)/(10+100), НЕ среднее процентов (50%+90%)/2=70%.
            assertThat(numeric(total, 6)).isCloseTo(95.0 / 110.0, within(1e-9));
            assertThat(numeric(total, 7)).isEqualTo(100.0); // Daribar: 100 + 0, БЕЗ null-бакета
            assertThat(numeric(total, 8)).isEqualTo(5000.0);
            assertThat(numeric(total, 9)).isEqualTo(50.0);  // Glovo: 0 + 50
            assertThat(numeric(total, 10)).isEqualTo(2500.0);
            assertThat(numeric(total, 15)).isEqualTo(150.0); // МП ИТОГО, кол-во: 100 + 50
            assertThat(numeric(total, 16)).isEqualTo(7500.0); // МП ИТОГО, сумма: 5000 + 2500
        }
    }

    private static List<String> sheetNames(XSSFWorkbook workbook) {
        return java.util.stream.IntStream.range(0, workbook.getNumberOfSheets())
                .mapToObj(workbook::getSheetName)
                .toList();
    }

    private static Row findRowByLabel(Sheet sheet, String label) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && row.getCell(0).getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                    && label.equals(row.getCell(0).getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("Строка с меткой '" + label + "' не найдена на листе " + sheet.getSheetName());
    }

    private static java.util.Optional<Row> findRowIndexByLabel(Sheet sheet, String label) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && row.getCell(0).getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                    && label.equals(row.getCell(0).getStringCellValue())) {
                return java.util.Optional.of(row);
            }
        }
        return java.util.Optional.empty();
    }

    private static double numeric(Row row, int col) {
        return row.getCell(col).getNumericCellValue();
    }
}
