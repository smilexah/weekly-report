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

import ai.lab.weeklyreport.metric.PharmacyDirectoryEntry;

/**
 * Разбирает divisions.xlsx - справочник аптека -> дивизион -> директор. Первый лист, заголовок
 * ищется по ячейке "Адрес Аптеки" (как в {@link MonthlyReportParser}), столбцы с телефонами
 * игнорируются. Строка с пустым "Код" останавливает разбор.
 */
@Component
public class DivisionsFileParser {

    private static final String COL_ADDRESS = "адрес аптеки";
    private static final String COL_BRANCH_CODE = "код";
    private static final String COL_CITY = "город";
    private static final String COL_DIVISION_NUM = "нумерация дивизионера";
    private static final String COL_DIRECTOR = "фио дивизионера";

    public List<PharmacyDirectoryEntry> parse(InputStream xlsxStream) {
        try (Workbook workbook = new XSSFWorkbook(xlsxStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = findHeaderRow(sheet);
            if (headerRow == null) {
                throw new IllegalStateException("В divisions.xlsx не найдена строка заголовка (нет ячейки '" + COL_ADDRESS + "')");
            }
            HeaderColumns columns = HeaderColumns.from(headerRow);

            List<PharmacyDirectoryEntry> entries = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            for (int rowIdx = headerRow.getRowNum() + 1; rowIdx <= lastRowNum; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) {
                    break;
                }
                String branchCode = stringValue(row.getCell(columns.branchCodeCol()));
                if (branchCode.isEmpty()) {
                    break;
                }
                entries.add(new PharmacyDirectoryEntry(
                        branchCode,
                        stringValue(row.getCell(columns.addressCol())),
                        stringValue(row.getCell(columns.cityCol())),
                        stringValue(row.getCell(columns.divisionNumCol())),
                        stringValue(row.getCell(columns.directorCol()))));
            }
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать divisions.xlsx", e);
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
                if (COL_ADDRESS.equals(stringValue(cell).toLowerCase(Locale.ROOT))) {
                    return row;
                }
            }
        }
        return null;
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

    private record HeaderColumns(int addressCol, int branchCodeCol, int cityCol, int divisionNumCol, int directorCol) {

        static HeaderColumns from(Row headerRow) {
            int addressCol = -1;
            int branchCodeCol = -1;
            int cityCol = -1;
            int divisionNumCol = -1;
            int directorCol = -1;

            for (Cell cell : headerRow) {
                String normalized = stringValue(cell).toLowerCase(Locale.ROOT);
                switch (normalized) {
                    case COL_ADDRESS -> addressCol = cell.getColumnIndex();
                    case COL_BRANCH_CODE -> branchCodeCol = cell.getColumnIndex();
                    case COL_CITY -> cityCol = cell.getColumnIndex();
                    case COL_DIVISION_NUM -> divisionNumCol = cell.getColumnIndex();
                    case COL_DIRECTOR -> directorCol = cell.getColumnIndex();
                    default -> {
                        // столбцы с телефонами и прочее - не используются, пропускаем
                    }
                }
            }

            if (addressCol < 0 || branchCodeCol < 0 || cityCol < 0 || divisionNumCol < 0 || directorCol < 0) {
                throw new IllegalStateException(
                        "В заголовке divisions.xlsx не найдены обязательные столбцы (Адрес Аптеки/Код/Город/Нумерация Дивизионера/ФИО Дивизионера)");
            }
            return new HeaderColumns(addressCol, branchCodeCol, cityCol, divisionNumCol, directorCol);
        }
    }
}
