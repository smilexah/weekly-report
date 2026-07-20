package ai.lab.weeklyreport.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import ai.lab.weeklyreport.metric.LoyaltyMeasure;
import ai.lab.weeklyreport.metric.MarketplaceChannel;
import ai.lab.weeklyreport.metric.MetricCatalog;
import ai.lab.weeklyreport.metric.MetricDailyTotal;

/**
 * Строит итоговый xlsx недельного отчёта из агрегированных сумм метрик:
 * листы "Маркетплейсы", "Программа лояльности" и "Сводный".
 * Лист "Дивизионы" не реализуется - см. TODO в миграции V2 (нет источника данных).
 */
@Component
public class WeeklyReportGenerator {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale.of("ru"));

    public byte[] generate(WeekRange currentWeek, List<MetricDailyTotal> currentTotals,
                            WeekRange previousWeek, List<MetricDailyTotal> previousTotals) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ReportStyles styles = new ReportStyles(workbook);
            MetricPivot current = new MetricPivot(currentTotals);
            MetricPivot previous = new MetricPivot(previousTotals);

            buildMarketplaceSheet(workbook, styles, currentWeek, current);
            buildLoyaltySheet(workbook, styles, currentWeek, current);
            buildSummarySheet(workbook, styles, currentWeek, current, previousWeek, previous);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сформировать xlsx недельного отчёта", e);
        }
    }

    private void buildMarketplaceSheet(XSSFWorkbook workbook, ReportStyles styles, WeekRange week, MetricPivot data) {
        XSSFSheet sheet = workbook.createSheet("Маркетплейсы");
        String[] headers = {
                "День",
                "Daribar, кол-во", "Daribar, сумма",
                "Glovo, кол-во", "Glovo, сумма",
                "Emdel, кол-во", "Emdel, сумма",
                "Wolt, кол-во", "Wolt, сумма",
                "ИТОГО, кол-во", "ИТОГО, сумма"
        };
        writeHeaderRow(sheet, styles, 0, headers);

        List<LocalDate> days = week.days();
        int rowIdx = 1;
        for (int i = 0; i < days.size(); i++, rowIdx++) {
            LocalDate day = days.get(i);
            boolean stripe = i % 2 == 1;
            XSSFRow row = sheet.createRow(rowIdx);
            writeCell(row, 0, day.format(DAY_FORMAT), styles.labelStyle(stripe));

            BigDecimal dayCountTotal = BigDecimal.ZERO;
            BigDecimal daySumTotal = BigDecimal.ZERO;
            int col = 1;
            for (MarketplaceChannel channel : MarketplaceChannel.values()) {
                BigDecimal count = data.value(day, channel.countMetricNum());
                BigDecimal sum = data.value(day, channel.sumMetricNum());
                writeCell(row, col++, count, styles.dataStyle(stripe, false));
                writeCell(row, col++, sum, styles.dataStyle(stripe, true));
                dayCountTotal = dayCountTotal.add(count);
                daySumTotal = daySumTotal.add(sum);
            }
            writeCell(row, col++, dayCountTotal, styles.dataStyle(stripe, false));
            writeCell(row, col, daySumTotal, styles.dataStyle(stripe, true));
        }

        XSSFRow totalRow = sheet.createRow(rowIdx);
        writeCell(totalRow, 0, "ИТОГО за неделю", styles.totalLabelStyle());
        BigDecimal weekCountTotal = BigDecimal.ZERO;
        BigDecimal weekSumTotal = BigDecimal.ZERO;
        int col = 1;
        for (MarketplaceChannel channel : MarketplaceChannel.values()) {
            BigDecimal count = data.sumOverDays(days, channel.countMetricNum());
            BigDecimal sum = data.sumOverDays(days, channel.sumMetricNum());
            writeCell(totalRow, col++, count, styles.totalValueStyle(false));
            writeCell(totalRow, col++, sum, styles.totalValueStyle(true));
            weekCountTotal = weekCountTotal.add(count);
            weekSumTotal = weekSumTotal.add(sum);
        }
        writeCell(totalRow, col++, weekCountTotal, styles.totalValueStyle(false));
        writeCell(totalRow, col, weekSumTotal, styles.totalValueStyle(true));

        autoSizeColumns(sheet, headers.length);
    }

    private void buildLoyaltySheet(XSSFWorkbook workbook, ReportStyles styles, WeekRange week, MetricPivot data) {
        XSSFSheet sheet = workbook.createSheet("Программа лояльности");
        String[] headers = {"День", "Новых клиентов", "Начисления, кол-во", "Начисления, сумма", "Списания, кол-во", "Списания, сумма"};
        writeHeaderRow(sheet, styles, 0, headers);

        List<LocalDate> days = week.days();
        int rowIdx = 1;
        for (int i = 0; i < days.size(); i++, rowIdx++) {
            LocalDate day = days.get(i);
            boolean stripe = i % 2 == 1;
            XSSFRow row = sheet.createRow(rowIdx);
            writeCell(row, 0, day.format(DAY_FORMAT), styles.labelStyle(stripe));
            int col = 1;
            for (LoyaltyMeasure measure : LoyaltyMeasure.values()) {
                BigDecimal value = loyaltyValue(data, day, measure);
                writeCell(row, col++, value, styles.dataStyle(stripe, isMoneyMeasure(measure)));
            }
        }

        XSSFRow totalRow = sheet.createRow(rowIdx);
        writeCell(totalRow, 0, "ИТОГО за неделю", styles.totalLabelStyle());
        int col = 1;
        for (LoyaltyMeasure measure : LoyaltyMeasure.values()) {
            BigDecimal total = days.stream()
                    .map(day -> loyaltyValue(data, day, measure))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            writeCell(totalRow, col++, total, styles.totalValueStyle(isMoneyMeasure(measure)));
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void buildSummarySheet(XSSFWorkbook workbook, ReportStyles styles,
                                    WeekRange currentWeek, MetricPivot current,
                                    WeekRange previousWeek, MetricPivot previous) {
        XSSFSheet sheet = workbook.createSheet("Сводный");
        String[] headers = {"Показатель", "Текущая неделя", "Прошлая неделя", "Динамика", "Динамика, %"};
        writeHeaderRow(sheet, styles, 0, headers);
        int rowIdx = 1;

        List<LocalDate> currentDays = currentWeek.days();
        List<LocalDate> previousDays = previousWeek.days();

        BigDecimal currentOrders = sumMarketplace(current, currentDays, false);
        BigDecimal previousOrders = sumMarketplace(previous, previousDays, false);
        BigDecimal currentRevenue = sumMarketplace(current, currentDays, true);
        BigDecimal previousRevenue = sumMarketplace(previous, previousDays, true);

        rowIdx = writeSectionHeader(sheet, styles, rowIdx, "Общие показатели");
        rowIdx = writeSummaryRow(sheet, styles, rowIdx, "Всего заказов (маркетплейсы), шт", currentOrders, previousOrders, false, false);
        rowIdx = writeSummaryRow(sheet, styles, rowIdx, "Всего сумма заказов (маркетплейсы)", currentRevenue, previousRevenue, true, false);

        rowIdx = writeSectionHeader(sheet, styles, rowIdx, "Программа лояльности");
        for (LoyaltyMeasure measure : LoyaltyMeasure.values()) {
            BigDecimal currentValue = currentDays.stream().map(d -> loyaltyValue(current, d, measure)).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal previousValue = previousDays.stream().map(d -> loyaltyValue(previous, d, measure)).reduce(BigDecimal.ZERO, BigDecimal::add);
            rowIdx = writeSummaryRow(sheet, styles, rowIdx, measure.label(), currentValue, previousValue, isMoneyMeasure(measure), false);
        }

        rowIdx = writeSectionHeader(sheet, styles, rowIdx, "Маркетплейсы");
        for (MarketplaceChannel channel : MarketplaceChannel.values()) {
            BigDecimal currentCount = current.sumOverDays(currentDays, channel.countMetricNum());
            BigDecimal previousCount = previous.sumOverDays(previousDays, channel.countMetricNum());
            rowIdx = writeSummaryRow(sheet, styles, rowIdx, channel.displayName() + ", кол-во", currentCount, previousCount, false, false);

            BigDecimal currentSum = current.sumOverDays(currentDays, channel.sumMetricNum());
            BigDecimal previousSum = previous.sumOverDays(previousDays, channel.sumMetricNum());
            rowIdx = writeSummaryRow(sheet, styles, rowIdx, channel.displayName() + ", сумма", currentSum, previousSum, true, false);
        }
        writeSummaryRow(sheet, styles, rowIdx, "ИТОГО маркетплейсы, кол-во", currentOrders, previousOrders, false, true);

        autoSizeColumns(sheet, headers.length);
    }

    private BigDecimal sumMarketplace(MetricPivot data, List<LocalDate> days, boolean money) {
        BigDecimal total = BigDecimal.ZERO;
        for (MarketplaceChannel channel : MarketplaceChannel.values()) {
            int metricNum = money ? channel.sumMetricNum() : channel.countMetricNum();
            total = total.add(data.sumOverDays(days, metricNum));
        }
        return total;
    }

    private BigDecimal loyaltyValue(MetricPivot data, LocalDate day, LoyaltyMeasure measure) {
        int coreMetricNum = MetricCatalog.LOYALTY_CORE_START + measure.ordinal();
        int janymdaMetricNum = MetricCatalog.LOYALTY_JANYMDA_START + measure.ordinal();
        return data.value(day, coreMetricNum).add(data.value(day, janymdaMetricNum));
    }

    private boolean isMoneyMeasure(LoyaltyMeasure measure) {
        return measure == LoyaltyMeasure.ACCRUAL_SUM || measure == LoyaltyMeasure.REDEMPTION_SUM;
    }

    private int writeSectionHeader(XSSFSheet sheet, ReportStyles styles, int rowIdx, String title) {
        XSSFRow row = sheet.createRow(rowIdx);
        XSSFCellStyle style = styles.sectionHeaderStyle();
        for (int col = 0; col < 5; col++) {
            writeCell(row, col, col == 0 ? title : "", style);
        }
        return rowIdx + 1;
    }

    private int writeSummaryRow(XSSFSheet sheet, ReportStyles styles, int rowIdx, String label,
                                 BigDecimal currentValue, BigDecimal previousValue, boolean money, boolean total) {
        XSSFRow row = sheet.createRow(rowIdx);
        writeCell(row, 0, label, total ? styles.totalLabelStyle() : styles.labelStyle(false));
        writeCell(row, 1, currentValue, total ? styles.totalValueStyle(money) : styles.dataStyle(false, money));
        writeCell(row, 2, previousValue, total ? styles.totalValueStyle(money) : styles.dataStyle(false, money));

        BigDecimal delta = currentValue.subtract(previousValue);
        writeCell(row, 3, delta, styles.dynamicStyle(delta.signum(), money, false));

        if (previousValue.signum() == 0) {
            writeCell(row, 4, "н/д", styles.labelStyle(false));
        } else {
            BigDecimal percent = delta.divide(previousValue.abs(), MathContext.DECIMAL64);
            writeCell(row, 4, percent, styles.dynamicStyle(percent.signum(), false, true));
        }
        return rowIdx + 1;
    }

    private void writeHeaderRow(XSSFSheet sheet, ReportStyles styles, int rowIdx, String[] headers) {
        XSSFRow row = sheet.createRow(rowIdx);
        for (int col = 0; col < headers.length; col++) {
            writeCell(row, col, headers[col], styles.headerStyle());
        }
        sheet.createFreezePane(0, rowIdx + 1);
    }

    private void writeCell(Row row, int col, String value, XSSFCellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void writeCell(Row row, int col, BigDecimal value, XSSFCellStyle style) {
        var cell = row.createCell(col);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private void autoSizeColumns(XSSFSheet sheet, int columnCount) {
        for (int col = 0; col < columnCount; col++) {
            sheet.autoSizeColumn(col);
        }
    }
}
