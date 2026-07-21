package ai.lab.weeklyreport.excel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import ai.lab.weeklyreport.metric.DaribarCrosswalkEntry;

/**
 * Разбирает daribar_crosswalk.xlsx - сопоставление кода аптеки в Daribar (= pharmacy_code из
 * monthly_report) с branch_code. Первый лист, заголовок ищется по ячейке "Код аптеки в Daribar" -
 * это единственный обязательный ключ (branch_code в источнике может быть пуст). В реальных файлах
 * "Код аптеки в Daribar" пуст у части строк в середине листа (ещё не сопоставленные аптеки) - такие
 * строки просто пропускаются (не останавливают разбор, в отличие от {@link MonthlyReportParser} и
 * {@link DivisionsFileParser}, где пустой ключ - однозначный конец данных). Дубли
 * "Код аптеки в Daribar" не отбрасываются здесь - upsert-логика (последняя строка побеждает)
 * находится в слое репозитория.
 */
@Component
public class DaribarCrosswalkParser {

    private static final String COL_DARIBAR_CODE = "код аптеки в daribar";
    private static final String COL_BRANCH_CODE = "код в стандарте";
    private static final String COL_PHARMACY_NAME = "название аптеки в daribar";
    private static final String COL_COMMENT = "комментарий";

    public List<DaribarCrosswalkEntry> parse(InputStream xlsxStream) {
        try (Workbook workbook = new XSSFWorkbook(xlsxStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = findHeaderRow(sheet);
            if (headerRow == null) {
                throw new IllegalStateException("В daribar_crosswalk.xlsx не найдена строка заголовка (нет ячейки '" + COL_DARIBAR_CODE + "')");
            }
            HeaderColumns columns = HeaderColumns.from(headerRow);

            List<DaribarCrosswalkEntry> entries = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            for (int rowIdx = headerRow.getRowNum() + 1; rowIdx <= lastRowNum; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) {
                    break;
                }
                String daribarCode = stringValue(row.getCell(columns.daribarCodeCol()));
                if (daribarCode.isEmpty()) {
                    // В реальных файлах "Код аптеки в Daribar" пуст у части строк в середине
                    // (ещё не сопоставленные аптеки) - это не конец данных, просто нет ключа для
                    // fallback-резолва, строку пропускаем и продолжаем разбор.
                    continue;
                }
                entries.add(new DaribarCrosswalkEntry(
                        daribarCode,
                        stringValue(cellAt(row, columns.branchCodeCol())),
                        stringValue(cellAt(row, columns.pharmacyNameCol())),
                        stringValue(cellAt(row, columns.commentCol()))));
            }
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать daribar_crosswalk.xlsx", e);
        }
    }

    private static Row findHeaderRow(Sheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), 9);
        for (int i = 0; i <= limit; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (COL_DARIBAR_CODE.equals(stringValue(cell).toLowerCase(Locale.ROOT))) {
                    return row;
                }
            }
        }
        return null;
    }

    private static Cell cellAt(Row row, int col) {
        return col < 0 ? null : row.getCell(col);
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

    private record HeaderColumns(int daribarCodeCol, int branchCodeCol, int pharmacyNameCol, int commentCol) {

        static HeaderColumns from(Row headerRow) {
            int daribarCodeCol = -1;
            int branchCodeCol = -1;
            int pharmacyNameCol = -1;
            int commentCol = -1;

            for (Cell cell : headerRow) {
                String normalized = stringValue(cell).toLowerCase(Locale.ROOT);
                switch (normalized) {
                    case COL_DARIBAR_CODE -> daribarCodeCol = cell.getColumnIndex();
                    case COL_BRANCH_CODE -> branchCodeCol = cell.getColumnIndex();
                    case COL_PHARMACY_NAME -> pharmacyNameCol = cell.getColumnIndex();
                    case COL_COMMENT -> commentCol = cell.getColumnIndex();
                    default -> {
                        // "Аптека" и "Token" - не используются, пропускаем
                    }
                }
            }

            if (daribarCodeCol < 0) {
                throw new IllegalStateException("В заголовке daribar_crosswalk.xlsx не найден обязательный столбец 'Код аптеки в Daribar'");
            }
            return new HeaderColumns(daribarCodeCol, branchCodeCol, pharmacyNameCol, commentCol);
        }
    }
}
