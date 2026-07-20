package ai.lab.weeklyreport.excel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ai.lab.weeklyreport.metric.MetricCatalog;
import ai.lab.weeklyreport.metric.MetricRow;

/**
 * Разбирает monthly_report.xlsx: листы = месяцы на русском, строка с ячейкой "Код аптеки"
 * - заголовок, дальше построчно (аптека, филиал, метрика) со значениями по дням в столбцах.
 * Файл содержит всю накопленную историю целиком (не дельту), поэтому парсер просто
 * возвращает плоский список строк - upsert-логика находится в слое репозитория.
 */
@Component
public class MonthlyReportParser {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportParser.class);

    private static final Map<String, Integer> RUSSIAN_MONTHS = Map.ofEntries(
            Map.entry("январь", 1), Map.entry("февраль", 2), Map.entry("март", 3),
            Map.entry("апрель", 4), Map.entry("май", 5), Map.entry("июнь", 6),
            Map.entry("июль", 7), Map.entry("август", 8), Map.entry("сентябрь", 9),
            Map.entry("октябрь", 10), Map.entry("ноябрь", 11), Map.entry("декабрь", 12)
    );

    private static final String COL_PHARMACY_CODE = "код аптеки";
    private static final String COL_BRANCH_CODE = "код филиала";
    private static final String COL_METRIC = "метрика";
    private static final String COL_TOTAL = "итого";
    private static final String COL_HAS_DATA = "есть данные";

    private final Clock clock;

    public MonthlyReportParser(Clock clock) {
        this.clock = clock;
    }

    public List<MetricRow> parse(InputStream xlsxStream) {
        try (Workbook workbook = new XSSFWorkbook(xlsxStream)) {
            List<MetricRow> rows = new ArrayList<>();
            for (Sheet sheet : workbook) {
                rows.addAll(parseSheet(sheet));
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать monthly_report.xlsx", e);
        }
    }

    private List<MetricRow> parseSheet(Sheet sheet) {
        Integer month = RUSSIAN_MONTHS.get(sheet.getSheetName().trim().toLowerCase(Locale.ROOT));
        if (month == null) {
            log.debug("Пропускаю лист '{}': не распознан как месяц", sheet.getSheetName());
            return List.of();
        }
        Row headerRow = findHeaderRow(sheet);
        if (headerRow == null) {
            log.warn("Пропускаю лист '{}': не найдена строка заголовка (нет ячейки '{}')", sheet.getSheetName(), COL_PHARMACY_CODE);
            return List.of();
        }
        HeaderColumns columns = HeaderColumns.from(headerRow);
        int year = resolveYear(month);
        int daysInMonth = YearMonth.of(year, month).lengthOfMonth();

        List<MetricRow> rows = new ArrayList<>();
        int lastRowNum = sheet.getLastRowNum();
        for (int rowIdx = headerRow.getRowNum() + 1; rowIdx <= lastRowNum; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) {
                break;
            }
            String pharmacyCode = stringValue(row.getCell(columns.pharmacyCodeCol()));
            if (pharmacyCode.isEmpty()) {
                break;
            }
            String metricLabel = stringValue(row.getCell(columns.metricCol()));
            if (metricLabel.isEmpty()) {
                continue;
            }
            int metricNum;
            try {
                metricNum = MetricCatalog.parseMetricNum(metricLabel);
            } catch (IllegalArgumentException e) {
                log.warn("Лист '{}', строка {}: не удалось разобрать метрику '{}', строка пропущена",
                        sheet.getSheetName(), rowIdx + 1, metricLabel);
                continue;
            }
            String branchCode = stringValue(row.getCell(columns.branchCodeCol()));

            for (Map.Entry<Integer, Integer> dayColumn : columns.dayColumns().entrySet()) {
                int day = dayColumn.getKey();
                if (day < 1 || day > daysInMonth) {
                    continue;
                }
                BigDecimal value = numericValue(row.getCell(dayColumn.getValue()));
                rows.add(new MetricRow(pharmacyCode, branchCode, metricNum, LocalDate.of(year, month, day), value));
            }
        }
        return rows;
    }

    /**
     * В файле нет явного года, поэтому он определяется от текущей даты: месяцы позже
     * текущего считаются относящимися к предыдущему году (например, лист "Декабрь",
     * встреченный в январе, - это декабрь предыдущего года).
     */
    private int resolveYear(int month) {
        LocalDate now = LocalDate.now(clock);
        return month <= now.getMonthValue() ? now.getYear() : now.getYear() - 1;
    }

    private static Row findHeaderRow(Sheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), 9);
        for (int i = 0; i <= limit; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (COL_PHARMACY_CODE.equals(stringValue(cell).toLowerCase(Locale.ROOT))) {
                    return row;
                }
            }
        }
        return null;
    }

    private static BigDecimal numericValue(Cell cell) {
        if (cell == null) {
            return BigDecimal.ZERO;
        }
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> parseNumericString(cell.getStringCellValue());
            default -> BigDecimal.ZERO;
        };
    }

    private static BigDecimal parseNumericString(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(trimmed.replace(",", "."));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String stringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case FORMULA -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) && !Double.isInfinite(d)
                        ? String.valueOf((long) d)
                        : String.valueOf(d);
            }
            default -> "";
        };
    }

    private record HeaderColumns(int pharmacyCodeCol, int branchCodeCol, int metricCol, Map<Integer, Integer> dayColumns) {

        static HeaderColumns from(Row headerRow) {
            int pharmacyCodeCol = -1;
            int branchCodeCol = -1;
            int metricCol = -1;
            Map<Integer, Integer> dayColumns = new LinkedHashMap<>();

            for (Cell cell : headerRow) {
                String normalized = stringValue(cell).toLowerCase(Locale.ROOT);
                switch (normalized) {
                    case COL_PHARMACY_CODE -> pharmacyCodeCol = cell.getColumnIndex();
                    case COL_BRANCH_CODE -> branchCodeCol = cell.getColumnIndex();
                    case COL_METRIC -> metricCol = cell.getColumnIndex();
                    case COL_TOTAL, COL_HAS_DATA -> {
                        // производные столбцы - не относятся к конкретному дню, пропускаем
                    }
                    default -> {
                        try {
                            dayColumns.put(Integer.parseInt(normalized), cell.getColumnIndex());
                        } catch (NumberFormatException ignored) {
                            // нераспознанный заголовок вне списка ожидаемых - игнорируем
                        }
                    }
                }
            }

            if (pharmacyCodeCol < 0 || branchCodeCol < 0 || metricCol < 0) {
                throw new IllegalStateException(
                        "В заголовке листа не найдены обязательные столбцы (Код аптеки/Код филиала/Метрика)");
            }
            return new HeaderColumns(pharmacyCodeCol, branchCodeCol, metricCol, dayColumns);
        }
    }
}
