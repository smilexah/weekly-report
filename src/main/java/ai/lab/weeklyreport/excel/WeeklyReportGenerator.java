package ai.lab.weeklyreport.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import ai.lab.weeklyreport.metric.DivisionRegistryRow;
import ai.lab.weeklyreport.metric.LoyaltyMeasure;
import ai.lab.weeklyreport.metric.MarketplaceChannel;
import ai.lab.weeklyreport.metric.MetricCatalog;
import ai.lab.weeklyreport.metric.MetricDailyTotal;

/**
 * Строит итоговый xlsx недельного отчёта из агрегированных сумм метрик: листы
 * "1. Маркетплейсы", "2. Программа лояльности", "3. Дивизионы", "4. Сводный". Дизайн (тёмно-синие
 * титульные/групповые заголовки, дни/каналы в колонках, голубая подсветка итоговых колонок)
 * воспроизводит эталонный файл "Недельный отчет по ПЛ и маркетплейсам" - см. {@link ReportStyles}.
 */
@Component
public class WeeklyReportGenerator {

    private static final DateTimeFormatter DAY_HEADER_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.of("ru"));
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM");

    private static final String[] LOYALTY_SHEET_LABELS = {
            "Новых клиентов, чел.",
            "Кол-во начислений",
            "Сумма начислений, ₸",
            "Кол-во списаний",
            "Сумма списаний, ₸"
    };

    private static final int METRIC_NEW_CLIENTS_CORE = MetricCatalog.LOYALTY_CORE_START + LoyaltyMeasure.NEW_CLIENTS.ordinal();
    private static final int METRIC_NEW_CLIENTS_JANYMDA = MetricCatalog.LOYALTY_JANYMDA_START + LoyaltyMeasure.NEW_CLIENTS.ordinal();
    private static final int METRIC_ACCRUAL_SUM_CORE = MetricCatalog.LOYALTY_CORE_START + LoyaltyMeasure.ACCRUAL_SUM.ordinal();
    private static final int METRIC_ACCRUAL_SUM_JANYMDA = MetricCatalog.LOYALTY_JANYMDA_START + LoyaltyMeasure.ACCRUAL_SUM.ordinal();

    public byte[] generate(WeekRange currentWeek, List<MetricDailyTotal> currentTotals,
                            WeekRange previousWeek, List<MetricDailyTotal> previousTotals,
                            DivisionsReportData divisionsData) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            ReportStyles styles = new ReportStyles(workbook);
            MetricPivot current = new MetricPivot(currentTotals);
            MetricPivot previous = new MetricPivot(previousTotals);
            DivisionMetricPivot divisions = new DivisionMetricPivot(divisionsData.metricTotals(), divisionsData.activityTotals());

            buildMarketplaceSheet(workbook, styles, currentWeek, current);
            buildLoyaltySheet(workbook, styles, currentWeek, current);
            buildDivisionsSheet(workbook, styles, currentWeek, divisionsData.registry(), divisions);
            buildSummarySheet(workbook, styles, currentWeek, current, previousWeek, previous);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сформировать xlsx недельного отчёта", e);
        }
    }

    private void buildMarketplaceSheet(XSSFWorkbook workbook, ReportStyles styles, WeekRange week, MetricPivot data) {
        XSSFSheet sheet = workbook.createSheet("1. Маркетплейсы");
        List<LocalDate> days = week.days();
        int lastColIndex = 1 + days.size() * 2 + 2 - 1;

        writeTitleBand(sheet, styles.titleStyle(), styles.subtitleStyle(), "Маркетплейсы — статистика по дням", "Неделя: " + formatRange(week), lastColIndex);

        int groupHeaderRowIdx = 3;
        int subHeaderRowIdx = 4;
        XSSFRow groupRow = sheet.createRow(groupHeaderRowIdx);
        writeCell(groupRow, 0, "", styles.fixedHeaderStyle());
        XSSFRow subRow = sheet.createRow(subHeaderRowIdx);
        writeCell(subRow, 0, "Канал", styles.fixedHeaderStyle());

        int col = 1;
        for (LocalDate day : days) {
            writeMergedHeader(sheet, groupRow, col, col + 1, formatDayHeader(day), styles.groupHeaderStyle());
            writeCell(subRow, col, "Заказов", styles.fixedHeaderStyle());
            writeCell(subRow, col + 1, "Сумма, ₸", styles.fixedHeaderStyle());
            col += 2;
        }
        writeMergedHeader(sheet, groupRow, col, col + 1, "ИТОГО", styles.fixedHeaderStyle());
        writeCell(subRow, col, "Заказов", styles.fixedHeaderStyle());
        writeCell(subRow, col + 1, "Сумма, ₸", styles.fixedHeaderStyle());

        MarketplaceChannel[] channels = MarketplaceChannel.values();
        BigDecimal[] dayCountTotals = new BigDecimal[days.size()];
        BigDecimal[] daySumTotals = new BigDecimal[days.size()];
        Arrays.fill(dayCountTotals, BigDecimal.ZERO);
        Arrays.fill(daySumTotals, BigDecimal.ZERO);
        BigDecimal grandCount = BigDecimal.ZERO;
        BigDecimal grandSum = BigDecimal.ZERO;

        int rowIdx = subHeaderRowIdx + 1;
        for (int i = 0; i < channels.length; i++) {
            MarketplaceChannel channel = channels[i];
            boolean stripe = i % 2 == 0;
            XSSFRow row = sheet.createRow(rowIdx++);
            writeCell(row, 0, channel.displayName(), styles.labelStyle(stripe, true));

            BigDecimal channelCount = BigDecimal.ZERO;
            BigDecimal channelSum = BigDecimal.ZERO;
            int c = 1;
            for (int d = 0; d < days.size(); d++) {
                BigDecimal count = data.value(days.get(d), channel.countMetricNum());
                BigDecimal sum = data.value(days.get(d), channel.sumMetricNum());
                writeCell(row, c, count, styles.dataStyle(stripe, ReportStyles.FORMAT_NUMBER));
                writeCell(row, c + 1, sum, styles.dataStyle(stripe, ReportStyles.FORMAT_NUMBER));
                dayCountTotals[d] = dayCountTotals[d].add(count);
                daySumTotals[d] = daySumTotals[d].add(sum);
                channelCount = channelCount.add(count);
                channelSum = channelSum.add(sum);
                c += 2;
            }
            writeCell(row, c, channelCount, styles.accentStyle(ReportStyles.FORMAT_NUMBER));
            writeCell(row, c + 1, channelSum, styles.accentStyle(ReportStyles.FORMAT_NUMBER));
            grandCount = grandCount.add(channelCount);
            grandSum = grandSum.add(channelSum);
        }

        XSSFRow totalRow = sheet.createRow(rowIdx);
        writeCell(totalRow, 0, "ИТОГО МП", styles.totalRowLabelStyle());
        int c = 1;
        for (int d = 0; d < days.size(); d++) {
            writeCell(totalRow, c, dayCountTotals[d], styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));
            writeCell(totalRow, c + 1, daySumTotals[d], styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));
            c += 2;
        }
        writeCell(totalRow, c, grandCount, styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));
        writeCell(totalRow, c + 1, grandSum, styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));

        sheet.createFreezePane(0, subHeaderRowIdx + 1);

        int[] widths = new int[lastColIndex + 1];
        widths[0] = 14;
        for (int i = 1; i < widths.length; i += 2) {
            widths[i] = 13;
            widths[i + 1] = 15;
        }
        setColumnWidths(sheet, widths);

        setRowHeight(sheet, groupHeaderRowIdx, 18f);
        setRowHeight(sheet, subHeaderRowIdx, 22f);
        for (int r = subHeaderRowIdx + 1; r < rowIdx; r++) {
            setRowHeight(sheet, r, 22f);
        }
        setRowHeight(sheet, rowIdx, 24f);
    }

    private void buildLoyaltySheet(XSSFWorkbook workbook, ReportStyles styles, WeekRange week, MetricPivot data) {
        XSSFSheet sheet = workbook.createSheet("2. Программа лояльности");
        List<LocalDate> days = week.days();
        int lastColIndex = days.size() + 1;

        writeTitleBand(sheet, styles.titleStyle(), styles.subtitleStyle(), "Программа лояльности (ПЛ) — статистика по дням", "Неделя: " + formatRange(week), lastColIndex);

        int headerRowIdx = 3;
        XSSFRow header = sheet.createRow(headerRowIdx);
        writeCell(header, 0, "Показатель", styles.fixedHeaderStyle());
        for (int d = 0; d < days.size(); d++) {
            writeCell(header, d + 1, formatDayHeader(days.get(d)), styles.groupHeaderStyle());
        }
        writeCell(header, days.size() + 1, "ИТОГО", styles.fixedHeaderStyle());

        LoyaltyMeasure[] measures = LoyaltyMeasure.values();
        int rowIdx = headerRowIdx + 1;
        for (int i = 0; i < measures.length; i++) {
            LoyaltyMeasure measure = measures[i];
            boolean stripe = i % 2 == 0;
            XSSFRow row = sheet.createRow(rowIdx++);
            writeCell(row, 0, LOYALTY_SHEET_LABELS[i], styles.labelStyle(stripe, false));
            BigDecimal weekTotal = BigDecimal.ZERO;
            for (int d = 0; d < days.size(); d++) {
                BigDecimal value = loyaltyValue(data, days.get(d), measure);
                writeCell(row, d + 1, value, styles.dataStyle(stripe, ReportStyles.FORMAT_NUMBER));
                weekTotal = weekTotal.add(value);
            }
            writeCell(row, days.size() + 1, weekTotal, styles.accentStyle(ReportStyles.FORMAT_NUMBER));
        }

        sheet.createFreezePane(0, headerRowIdx + 1);

        int[] widths = new int[lastColIndex + 1];
        widths[0] = 22;
        for (int i = 1; i < widths.length - 1; i++) {
            widths[i] = 14;
        }
        widths[widths.length - 1] = 16;
        setColumnWidths(sheet, widths);

        setRowHeight(sheet, headerRowIdx, 24f);
        for (int r = headerRowIdx + 1; r < rowIdx; r++) {
            setRowHeight(sheet, r, 22f);
        }
    }

    /**
     * Одна строка на дивизион (порядок - как пришёл из репозитория), "ИТОГО ПО СЕТИ" снизу.
     * Только текущая неделя, без разбивки по дням и без сравнения с прошлой неделей. Аптеки без
     * резолвленного branch_code (division_num = NULL, включая нормализованный статус
     * "Закрыта"/"закрыта" - см. {@link ai.lab.weeklyreport.repository.DivisionReportRepository})
     * на этот лист не попадают ни отдельной строкой, ни в "ИТОГО ПО СЕТИ" - их заказы не привязаны
     * ни к одному реальному дивизиону/директору, поэтому в разрезе по дивизионам не показываются.
     */
    private void buildDivisionsSheet(XSSFWorkbook workbook, ReportStyles styles, WeekRange week,
                                      List<DivisionRegistryRow> registry, DivisionMetricPivot data) {
        XSSFSheet sheet = workbook.createSheet("3. Дивизионы");
        MarketplaceChannel[] channels = MarketplaceChannel.values();
        int fixedCols = 7;
        int lastColIndex = fixedCols + channels.length * 2 + 2 - 1;

        writeTitleBand(sheet, styles.titleStyle(), styles.subtitleStyle(), "Результативность Директоров Дивизионов", "Период: " + formatRange(week), lastColIndex);

        int groupHeaderRowIdx = 3;
        int subHeaderRowIdx = 4;
        XSSFRow groupRow = sheet.createRow(groupHeaderRowIdx);
        writeMergedHeader(sheet, groupRow, 0, fixedCols - 4, "", styles.fixedHeaderStyle());
        writeMergedHeader(sheet, groupRow, fixedCols - 3, fixedCols - 1, "ПРОГРАММА ЛОЯЛЬНОСТИ", styles.groupHeaderStyle());
        int col = fixedCols;
        for (MarketplaceChannel channel : channels) {
            writeMergedHeader(sheet, groupRow, col, col + 1, channel.displayName(), styles.groupHeaderStyle());
            col += 2;
        }
        writeMergedHeader(sheet, groupRow, col, col + 1, "МП ИТОГО", styles.fixedHeaderStyle());

        XSSFRow subRow = sheet.createRow(subHeaderRowIdx);
        String[] fixedHeaders = {"Номер\nдивизиона", "Директор", "Кол-во\nаптек", "Охват (регионы)", "Новых\nклиентов", "Начислено, ₸", "Актив.\nаптек, %"};
        for (int i = 0; i < fixedHeaders.length; i++) {
            writeCell(subRow, i, fixedHeaders[i], styles.fixedHeaderStyle());
        }
        col = fixedCols;
        for (int i = 0; i < channels.length + 1; i++) {
            writeCell(subRow, col, "Заказов", styles.fixedHeaderStyle());
            writeCell(subRow, col + 1, "Сумма, ₸", styles.fixedHeaderStyle());
            col += 2;
        }

        int networkPharmacyCount = 0;
        int networkActivePharmacyCount = 0;
        BigDecimal networkNewClients = BigDecimal.ZERO;
        BigDecimal networkAccrual = BigDecimal.ZERO;
        BigDecimal[] networkChannelCounts = new BigDecimal[channels.length];
        BigDecimal[] networkChannelSums = new BigDecimal[channels.length];
        Arrays.fill(networkChannelCounts, BigDecimal.ZERO);
        Arrays.fill(networkChannelSums, BigDecimal.ZERO);

        int rowIdx = subHeaderRowIdx + 1;
        for (int i = 0; i < registry.size(); i++, rowIdx++) {
            DivisionRegistryRow division = registry.get(i);
            boolean stripe = i % 2 == 0;
            var activity = data.activity(division.divisionNum());

            BigDecimal[] rowChannelCounts = new BigDecimal[channels.length];
            BigDecimal[] rowChannelSums = new BigDecimal[channels.length];
            writeDivisionRow(sheet, styles, rowIdx, stripe, division.divisionNum(), division.divisionNum(), division.directorName(),
                    division.pharmacyCount(), division.coverage(), activity.activePharmacyCount(), data, channels, rowChannelCounts, rowChannelSums);

            networkPharmacyCount += division.pharmacyCount();
            networkActivePharmacyCount += activity.activePharmacyCount();
            networkNewClients = networkNewClients.add(data.metricValue(division.divisionNum(), METRIC_NEW_CLIENTS_CORE))
                    .add(data.metricValue(division.divisionNum(), METRIC_NEW_CLIENTS_JANYMDA));
            networkAccrual = networkAccrual.add(data.metricValue(division.divisionNum(), METRIC_ACCRUAL_SUM_CORE))
                    .add(data.metricValue(division.divisionNum(), METRIC_ACCRUAL_SUM_JANYMDA));
            for (int c2 = 0; c2 < channels.length; c2++) {
                networkChannelCounts[c2] = networkChannelCounts[c2].add(rowChannelCounts[c2]);
                networkChannelSums[c2] = networkChannelSums[c2].add(rowChannelSums[c2]);
            }
        }

        XSSFRow totalRow = sheet.createRow(rowIdx);
        writeMergedHeader(sheet, totalRow, 0, 1, "ИТОГО ПО СЕТИ", styles.totalRowLabelStyle());
        writeCell(totalRow, 2, BigDecimal.valueOf(networkPharmacyCount), styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));
        writeCell(totalRow, 3, "", styles.totalRowLabelStyle());
        writeCell(totalRow, 4, networkNewClients, styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));
        writeCell(totalRow, 5, networkAccrual, styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));
        writePercentCell(totalRow, 6, networkActivePharmacyCount, networkPharmacyCount,
                styles.totalRowValueStyle(ReportStyles.FORMAT_PERCENT), styles.totalRowLabelStyle());

        BigDecimal networkMpCount = BigDecimal.ZERO;
        BigDecimal networkMpSum = BigDecimal.ZERO;
        col = fixedCols;
        for (int c2 = 0; c2 < channels.length; c2++) {
            writeCell(totalRow, col++, networkChannelCounts[c2], styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));
            writeCell(totalRow, col++, networkChannelSums[c2], styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));
            networkMpCount = networkMpCount.add(networkChannelCounts[c2]);
            networkMpSum = networkMpSum.add(networkChannelSums[c2]);
        }
        writeCell(totalRow, col++, networkMpCount, styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));
        writeCell(totalRow, col, networkMpSum, styles.totalRowValueStyle(ReportStyles.FORMAT_NUMBER));

        sheet.createFreezePane(0, subHeaderRowIdx + 1);

        int[] widths = new int[lastColIndex + 1];
        widths[0] = 10;
        widths[1] = 24;
        widths[2] = 9;
        widths[3] = 38;
        widths[4] = 13;
        widths[5] = 16;
        widths[6] = 12;
        col = fixedCols;
        for (int i = 0; i < channels.length + 1; i++) {
            boolean isTotalPair = i == channels.length;
            widths[col] = isTotalPair ? 12 : 11;
            widths[col + 1] = isTotalPair ? 16 : 15;
            col += 2;
        }
        setColumnWidths(sheet, widths);

        setRowHeight(sheet, groupHeaderRowIdx, 16f);
        setRowHeight(sheet, subHeaderRowIdx, 30f);
        for (int r = subHeaderRowIdx + 1; r < rowIdx; r++) {
            setRowHeight(sheet, r, 30f);
        }
        setRowHeight(sheet, rowIdx, 24f);
    }

    private void writeDivisionRow(XSSFSheet sheet, ReportStyles styles, int rowIdx, boolean stripe,
                                   String divisionNum, String displayLabel, String directorName, int pharmacyCount, String coverage,
                                   int activePharmacyCount, DivisionMetricPivot data, MarketplaceChannel[] channels,
                                   BigDecimal[] outChannelCounts, BigDecimal[] outChannelSums) {
        XSSFRow row = sheet.createRow(rowIdx);
        writeCell(row, 0, displayLabel, styles.centerLabelStyle(stripe, true));
        writeCell(row, 1, directorName, styles.labelStyle(stripe, true));
        writeCell(row, 2, BigDecimal.valueOf(pharmacyCount), styles.dataStyle(stripe, ReportStyles.FORMAT_NUMBER));
        writeCell(row, 3, coverage, styles.wrapLabelStyle(stripe));

        BigDecimal newClients = data.metricValue(divisionNum, METRIC_NEW_CLIENTS_CORE).add(data.metricValue(divisionNum, METRIC_NEW_CLIENTS_JANYMDA));
        BigDecimal accrualSum = data.metricValue(divisionNum, METRIC_ACCRUAL_SUM_CORE).add(data.metricValue(divisionNum, METRIC_ACCRUAL_SUM_JANYMDA));
        writeCell(row, 4, newClients, styles.dataStyle(stripe, ReportStyles.FORMAT_NUMBER));
        writeCell(row, 5, accrualSum, styles.dataStyle(stripe, ReportStyles.FORMAT_NUMBER));
        writePercentCell(row, 6, activePharmacyCount, pharmacyCount, styles.dataStyle(stripe, ReportStyles.FORMAT_PERCENT), styles.labelStyle(stripe, false));

        int col = 7;
        for (int i = 0; i < channels.length; i++) {
            BigDecimal count = data.metricValue(divisionNum, channels[i].countMetricNum());
            BigDecimal sum = data.metricValue(divisionNum, channels[i].sumMetricNum());
            writeCell(row, col++, count, styles.dataStyle(stripe, ReportStyles.FORMAT_NUMBER));
            writeCell(row, col++, sum, styles.dataStyle(stripe, ReportStyles.FORMAT_NUMBER));
            outChannelCounts[i] = count;
            outChannelSums[i] = sum;
        }
        BigDecimal mpCount = Arrays.stream(outChannelCounts).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mpSum = Arrays.stream(outChannelSums).reduce(BigDecimal.ZERO, BigDecimal::add);
        writeCell(row, col++, mpCount, styles.accentStyle(ReportStyles.FORMAT_NUMBER));
        writeCell(row, col, mpSum, styles.accentStyle(ReportStyles.FORMAT_NUMBER));
    }

    private void buildSummarySheet(XSSFWorkbook workbook, ReportStyles styles,
                                    WeekRange currentWeek, MetricPivot current,
                                    WeekRange previousWeek, MetricPivot previous) {
        XSSFSheet sheet = workbook.createSheet("4. Сводный");
        int lastColIndex = 3;

        writeTitleBand(sheet, styles.summaryTitleStyle(), styles.summarySubtitleStyle(), "Общий Сводный отчет",
                "Текущая: " + formatShortRange(currentWeek) + "  |  Прошлая: " + formatShortRange(previousWeek),
                lastColIndex);

        int headerRowIdx = 3;
        XSSFRow header = sheet.createRow(headerRowIdx);
        writeCell(header, 0, "Показатель", styles.summaryFixedHeaderStyle());
        writeCell(header, 1, "Текущая\nнеделя", styles.summaryFixedHeaderStyle());
        writeCell(header, 2, "Прошлая\nнеделя", styles.summaryFixedHeaderStyle());
        writeCell(header, 3, "Динамика\n(+/-)", styles.summaryFixedHeaderStyle());

        List<LocalDate> currentDays = currentWeek.days();
        List<LocalDate> previousDays = previousWeek.days();

        BigDecimal currentOrders = sumMarketplace(current, currentDays, false);
        BigDecimal previousOrders = sumMarketplace(previous, previousDays, false);
        BigDecimal currentRevenue = sumMarketplace(current, currentDays, true);
        BigDecimal previousRevenue = sumMarketplace(previous, previousDays, true);

        int rowIdx = headerRowIdx + 1;
        int[] stripeCounter = {0};

        rowIdx = writeSectionHeader(sheet, styles, rowIdx, "ОБЩИЕ ПОКАЗАТЕЛИ", lastColIndex);
        rowIdx = writeSummaryRow(sheet, styles, rowIdx, stripeCounter, "Общая выручка — все каналы, ₸", currentRevenue, previousRevenue);
        rowIdx = writeSummaryRow(sheet, styles, rowIdx, stripeCounter, "Кол-во заказов МП — всего, шт", currentOrders, previousOrders);
        rowIdx = writeSummaryRow(sheet, styles, rowIdx, stripeCounter, "Средний чек МП, ₸",
                averageCheck(currentRevenue, currentOrders), averageCheck(previousRevenue, previousOrders));

        stripeCounter[0] = 0;
        rowIdx = writeSectionHeader(sheet, styles, rowIdx, "ПРОГРАММА ЛОЯЛЬНОСТИ", lastColIndex);
        rowIdx = writeSummaryRow(sheet, styles, rowIdx, stripeCounter, "Кол-во первых транзакций (новых клиентов), чел",
                loyaltyTotal(current, currentDays, LoyaltyMeasure.NEW_CLIENTS), loyaltyTotal(previous, previousDays, LoyaltyMeasure.NEW_CLIENTS));
        rowIdx = writeSummaryRow(sheet, styles, rowIdx, stripeCounter, "Кол-во начислений",
                loyaltyTotal(current, currentDays, LoyaltyMeasure.ACCRUAL_COUNT), loyaltyTotal(previous, previousDays, LoyaltyMeasure.ACCRUAL_COUNT));
        rowIdx = writeSummaryRow(sheet, styles, rowIdx, stripeCounter, "Сумма начислений, ₸",
                loyaltyTotal(current, currentDays, LoyaltyMeasure.ACCRUAL_SUM), loyaltyTotal(previous, previousDays, LoyaltyMeasure.ACCRUAL_SUM));
        rowIdx = writeSummaryRow(sheet, styles, rowIdx, stripeCounter, "Кол-во списаний",
                loyaltyTotal(current, currentDays, LoyaltyMeasure.REDEMPTION_COUNT), loyaltyTotal(previous, previousDays, LoyaltyMeasure.REDEMPTION_COUNT));
        rowIdx = writeSummaryRow(sheet, styles, rowIdx, stripeCounter, "Сумма списаний, ₸",
                loyaltyTotal(current, currentDays, LoyaltyMeasure.REDEMPTION_SUM), loyaltyTotal(previous, previousDays, LoyaltyMeasure.REDEMPTION_SUM));

        stripeCounter[0] = 0;
        rowIdx = writeSectionHeader(sheet, styles, rowIdx, "МАРКЕТПЛЕЙСЫ", lastColIndex);
        for (MarketplaceChannel channel : MarketplaceChannel.values()) {
            BigDecimal curCount = current.sumOverDays(currentDays, channel.countMetricNum());
            BigDecimal prevCount = previous.sumOverDays(previousDays, channel.countMetricNum());
            rowIdx = writeSummaryRow(sheet, styles, rowIdx, stripeCounter, channel.displayName() + " — кол-во заказов", curCount, prevCount);

            BigDecimal curSum = current.sumOverDays(currentDays, channel.sumMetricNum());
            BigDecimal prevSum = previous.sumOverDays(previousDays, channel.sumMetricNum());
            rowIdx = writeSummaryRow(sheet, styles, rowIdx, stripeCounter, channel.displayName() + " — сумма заказов, ₸", curSum, prevSum);
        }

        stripeCounter[0] = 0;
        rowIdx = writeSectionHeader(sheet, styles, rowIdx, "ПРОБЛЕМЫ", lastColIndex);
        rowIdx = writePlaceholderRow(sheet, styles, rowIdx, stripeCounter, "Отмены заказов МП — всего, шт");
        rowIdx = writePlaceholderRow(sheet, styles, rowIdx, stripeCounter, "Дивизионов с невыполнением плана");

        setColumnWidths(sheet, 38, 20, 20, 16);
        setRowHeight(sheet, headerRowIdx, 26f);
    }

    private int writeSectionHeader(XSSFSheet sheet, ReportStyles styles, int rowIdx, String title, int lastColIndex) {
        XSSFRow row = sheet.createRow(rowIdx);
        writeMergedHeader(sheet, row, 0, lastColIndex, title, styles.summarySectionHeaderStyle());
        setRowHeight(sheet, rowIdx, 18f);
        return rowIdx + 1;
    }

    private int writeSummaryRow(XSSFSheet sheet, ReportStyles styles, int rowIdx, int[] stripeCounter,
                                 String label, BigDecimal currentValue, BigDecimal previousValue) {
        boolean stripe = stripeCounter[0] % 2 == 0;
        stripeCounter[0]++;
        XSSFRow row = sheet.createRow(rowIdx);
        writeCell(row, 0, label, styles.summaryLabelStyle(stripe));
        writeCell(row, 1, currentValue, styles.summaryDataStyle(stripe, ReportStyles.FORMAT_NUMBER));
        writeCell(row, 2, previousValue, styles.summaryDataStyle(stripe, ReportStyles.FORMAT_NUMBER));
        writeCell(row, 3, currentValue.subtract(previousValue), styles.summaryAccentStyle(ReportStyles.FORMAT_NUMBER));
        setRowHeight(sheet, rowIdx, 20f);
        return rowIdx + 1;
    }

    /** Строка-заглушка "н/д" для показателей, для которых в БД и исходных файлах нет данных. */
    private int writePlaceholderRow(XSSFSheet sheet, ReportStyles styles, int rowIdx, int[] stripeCounter, String label) {
        boolean stripe = stripeCounter[0] % 2 == 0;
        stripeCounter[0]++;
        XSSFRow row = sheet.createRow(rowIdx);
        writeCell(row, 0, label, styles.summaryLabelStyle(stripe));
        writeCell(row, 1, "", styles.summaryLabelStyle(stripe));
        writeCell(row, 2, "", styles.summaryLabelStyle(stripe));
        writeCell(row, 3, "", styles.summaryLabelStyle(stripe));
        setRowHeight(sheet, rowIdx, 20f);
        return rowIdx + 1;
    }

    private void writeTitleBand(XSSFSheet sheet, XSSFCellStyle titleStyle, XSSFCellStyle subtitleStyle,
                                 String title, String subtitle, int lastColIndex) {
        XSSFRow titleRow = sheet.createRow(0);
        writeMergedHeader(sheet, titleRow, 0, lastColIndex, title, titleStyle);

        XSSFRow subtitleRow = sheet.createRow(1);
        writeMergedHeader(sheet, subtitleRow, 0, lastColIndex, subtitle, subtitleStyle);

        sheet.createRow(2);

        titleRow.setHeightInPoints(30f);
        subtitleRow.setHeightInPoints(14f);
        setRowHeight(sheet, 2, 5f);
    }

    private void writeMergedHeader(XSSFSheet sheet, XSSFRow row, int startCol, int endCol, String text, XSSFCellStyle style) {
        for (int col = startCol; col <= endCol; col++) {
            writeCell(row, col, col == startCol ? text : "", style);
        }
        if (endCol > startCol) {
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), startCol, endCol));
        }
    }

    private void writePercentCell(Row row, int col, int numerator, int denominator, XSSFCellStyle percentStyle, XSSFCellStyle fallbackStyle) {
        if (denominator == 0) {
            writeCell(row, col, "н/д", fallbackStyle);
            return;
        }
        BigDecimal percent = BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), MathContext.DECIMAL64);
        writeCell(row, col, percent, percentStyle);
    }

    private BigDecimal sumMarketplace(MetricPivot data, List<LocalDate> days, boolean money) {
        BigDecimal total = BigDecimal.ZERO;
        for (MarketplaceChannel channel : MarketplaceChannel.values()) {
            int metricNum = money ? channel.sumMetricNum() : channel.countMetricNum();
            total = total.add(data.sumOverDays(days, metricNum));
        }
        return total;
    }

    private BigDecimal averageCheck(BigDecimal revenue, BigDecimal orders) {
        return orders.signum() == 0 ? BigDecimal.ZERO : revenue.divide(orders, MathContext.DECIMAL64);
    }

    private BigDecimal loyaltyValue(MetricPivot data, LocalDate day, LoyaltyMeasure measure) {
        int coreMetricNum = MetricCatalog.LOYALTY_CORE_START + measure.ordinal();
        int janymdaMetricNum = MetricCatalog.LOYALTY_JANYMDA_START + measure.ordinal();
        return data.value(day, coreMetricNum).add(data.value(day, janymdaMetricNum));
    }

    private BigDecimal loyaltyTotal(MetricPivot data, List<LocalDate> days, LoyaltyMeasure measure) {
        return days.stream().map(d -> loyaltyValue(data, d, measure)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String formatDayHeader(LocalDate day) {
        String formatted = day.format(DAY_HEADER_FORMAT);
        return formatted.endsWith(".") ? formatted.substring(0, formatted.length() - 1) : formatted;
    }

    private String formatRange(WeekRange week) {
        return week.start().format(DATE_FORMAT) + " – " + week.end().format(DATE_FORMAT);
    }

    private String formatShortRange(WeekRange week) {
        return week.start().format(SHORT_DATE_FORMAT) + "–" + week.end().format(DATE_FORMAT);
    }

    private void setColumnWidths(XSSFSheet sheet, int... charWidths) {
        for (int i = 0; i < charWidths.length; i++) {
            sheet.setColumnWidth(i, charWidths[i] * 256);
        }
    }

    private void setRowHeight(XSSFSheet sheet, int rowIdx, float points) {
        XSSFRow row = sheet.getRow(rowIdx);
        if (row == null) {
            row = sheet.createRow(rowIdx);
        }
        row.setHeightInPoints(points);
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
}
