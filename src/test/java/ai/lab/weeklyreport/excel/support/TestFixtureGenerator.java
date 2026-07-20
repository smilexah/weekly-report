package ai.lab.weeklyreport.excel.support;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Не тест: утилита для (пере)генерации бинарной фикстуры src/test/resources/fixtures/monthly_report_test.xlsx.
 * Запускается вручную через {@code ./gradlew generateTestFixtures}, если структура фикстуры должна измениться.
 */
public final class TestFixtureGenerator {

    private TestFixtureGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path target = Path.of("src/test/resources/fixtures/monthly_report_test.xlsx");
        Files.createDirectories(target.getParent());
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeJanuarySheet(workbook);
            writeFebruarySheet(workbook);
            workbook.createSheet("Сводка");
            try (FileOutputStream out = new FileOutputStream(target.toFile())) {
                workbook.write(out);
            }
        }
        System.out.println("Фикстура записана: " + target.toAbsolutePath());
    }

    private static void writeJanuarySheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("Январь");

        Row banner = sheet.createRow(0);
        banner.createCell(0).setCellValue("Отчёт по показателям (служебная строка перед заголовком)");

        Row header = sheet.createRow(1);
        header.createCell(0).setCellValue("Код аптеки");
        header.createCell(1).setCellValue("Код филиала");
        header.createCell(2).setCellValue("Метрика");
        header.createCell(3).setCellValue("ИТОГО");
        header.createCell(4).setCellValue(1);
        header.createCell(5).setCellValue(2);
        header.createCell(6).setCellValue(3);
        header.createCell(7).setCellValue("Есть данные");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue(1001); // числовой код аптеки - проверяем корректную конвертацию в строку
        row2.createCell(1).setCellValue("F1");
        row2.createCell(2).setCellValue("1-Новых клиентов");
        row2.createCell(3).setCellValue(8);
        row2.createCell(4).setCellValue(5);
        row2.createCell(5).setCellValue(3);
        row2.createCell(6).setCellValue(0);
        row2.createCell(7).setCellValue("Да");

        Row row3 = sheet.createRow(3);
        row3.createCell(0).setCellValue(1001);
        row3.createCell(1).setCellValue("F1");
        row3.createCell(2).setCellValue("2-Начисления, кол-во");
        row3.createCell(4).setCellValue(10);
        row3.createCell(5).setCellValue(0);
        row3.createCell(6).setCellValue(2);

        Row row4 = sheet.createRow(4);
        row4.createCell(0).setCellValue("1002");
        row4.createCell(1).setCellValue("F2");
        row4.createCell(2).setCellValue("11-Заказы Daribar (кол-во)");
        row4.createCell(4).setCellValue(7);
        row4.createCell(5).setCellValue(8);
        row4.createCell(6).setCellValue(9);

        Row row5 = sheet.createRow(5);
        row5.createCell(0).setCellValue("1002");
        row5.createCell(1).setCellValue("F2");
        row5.createCell(2).setCellValue("12-Заказы Daribar (сумма)");
        row5.createCell(4).setCellValue(1000.5);
        row5.createCell(5).setCellValue(2000);
        row5.createCell(6).setCellValue(0);

        // Строка 7 (индекс 6) - пустой код аптеки: парсер должен остановиться здесь.
        sheet.createRow(6);

        // Строка 8 (индекс 7) - "мусор" после конца блока данных; не должна попасть в результат разбора.
        Row trailing = sheet.createRow(7);
        trailing.createCell(0).setCellValue("9999");
        trailing.createCell(1).setCellValue("F9");
        trailing.createCell(2).setCellValue("1-Новых клиентов");
        trailing.createCell(4).setCellValue(999);
    }

    private static void writeFebruarySheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("Февраль");
        Row header = sheet.createRow(1);
        header.createCell(0).setCellValue("Код аптеки");
        header.createCell(1).setCellValue("Код филиала");
        header.createCell(2).setCellValue("Метрика");
        header.createCell(3).setCellValue("ИТОГО");
        header.createCell(4).setCellValue(1);
        header.createCell(5).setCellValue("Есть данные");
    }
}
