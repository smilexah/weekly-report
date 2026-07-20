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

import ai.lab.weeklyreport.metric.MetricDailyTotal;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyReportGeneratorTest {

    private final WeeklyReportGenerator generator = new WeeklyReportGenerator();

    @Test
    void generatesThreeSheetsWithCorrectTotalsAndDynamics() throws IOException {
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

        byte[] workbookBytes = generator.generate(currentWeek, currentTotals, previousWeek, previousTotals);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(sheetNames(workbook)).containsExactly("Маркетплейсы", "Программа лояльности", "Сводный");

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
