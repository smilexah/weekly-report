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

        writeDivisionsFixture();
        writeDaribarCrosswalkFixture();
    }

    private static void writeDivisionsFixture() throws IOException {
        Path target = Path.of("src/test/resources/fixtures/divisions_test.xlsx");
        Files.createDirectories(target.getParent());
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Аптеки");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Адрес Аптеки");
            header.createCell(1).setCellValue("Код");
            header.createCell(2).setCellValue("Город");
            header.createCell(3).setCellValue("Нумерация Дивизионера");
            header.createCell(4).setCellValue("ФИО Дивизионера");
            header.createCell(5).setCellValue("Телефон"); // должен игнорироваться парсером

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("ул. Абая, 1");
            row1.createCell(1).setCellValue("F1");
            row1.createCell(2).setCellValue("Алматы");
            row1.createCell(3).setCellValue("1");
            row1.createCell(4).setCellValue("Иванов Иван");
            row1.createCell(5).setCellValue("+77001234567");

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("ул. Сатпаева, 5");
            row2.createCell(1).setCellValue("F2");
            row2.createCell(2).setCellValue("Алматы");
            row2.createCell(3).setCellValue("1");
            row2.createCell(4).setCellValue("Иванов Иван");

            Row row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("пр. Абылай хана, 10");
            row3.createCell(1).setCellValue("F3");
            row3.createCell(2).setCellValue("Астана");
            row3.createCell(3).setCellValue("2");
            row3.createCell(4).setCellValue("Петров Пётр");

            // Строка 5 (индекс 4) - пустой "Код": парсер должен остановиться здесь.
            sheet.createRow(4);

            // Строка 6 (индекс 5) - "мусор" после конца блока данных; не должна попасть в результат разбора.
            Row trailing = sheet.createRow(5);
            trailing.createCell(0).setCellValue("Мусорный адрес");
            trailing.createCell(1).setCellValue("F9");
            trailing.createCell(2).setCellValue("Мусор");
            trailing.createCell(3).setCellValue("9");
            trailing.createCell(4).setCellValue("Мусорный Мусорович");

            try (FileOutputStream out = new FileOutputStream(target.toFile())) {
                workbook.write(out);
            }
        }
        System.out.println("Фикстура записана: " + target.toAbsolutePath());
    }

    private static void writeDaribarCrosswalkFixture() throws IOException {
        Path target = Path.of("src/test/resources/fixtures/daribar_crosswalk_test.xlsx");
        Files.createDirectories(target.getParent());
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Сопоставление");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Код в стандарте");
            header.createCell(1).setCellValue("Аптека"); // должен игнорироваться парсером
            header.createCell(2).setCellValue("Название аптеки в Daribar");
            header.createCell(3).setCellValue("Код аптеки в Daribar");
            header.createCell(4).setCellValue("Token"); // должен игнорироваться парсером
            header.createCell(5).setCellValue("Комментарий");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("F1");
            row1.createCell(1).setCellValue("Аптека 1");
            row1.createCell(2).setCellValue("Apteka F1 Daribar");
            row1.createCell(3).setCellValue("1001");
            row1.createCell(4).setCellValue("tok1");
            row1.createCell(5).setCellValue("");

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("F2");
            row2.createCell(1).setCellValue("Аптека 2");
            row2.createCell(2).setCellValue("Apteka F2 Daribar");
            row2.createCell(3).setCellValue("1002");
            row2.createCell(4).setCellValue("tok2");

            // Дубль "Код аптеки в Daribar" = 1002 (известное ограничение источника) - парсер должен
            // вернуть обе строки без ошибки; "последняя побеждает" - забота репозитория, не парсера.
            Row row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("F2B");
            row3.createCell(1).setCellValue("Аптека 2 (дубликат)");
            row3.createCell(2).setCellValue("Apteka F2 Daribar Dup");
            row3.createCell(3).setCellValue("1002");
            row3.createCell(4).setCellValue("tok3");
            row3.createCell(5).setCellValue("дубликат кода");

            // Строка 5 (индекс 4) - пустой "Код аптеки в Daribar" в СЕРЕДИНЕ листа (как в реальных
            // файлах: аптека ещё не сопоставлена с Daribar) - должна быть пропущена, а не
            // останавливать разбор целиком.
            Row unresolved = sheet.createRow(4);
            unresolved.createCell(0).setCellValue("F5");
            unresolved.createCell(1).setCellValue("Аптека 5");
            unresolved.createCell(5).setCellValue("нет данных Daribar");

            // Строка 6 (индекс 5) - валидная строка ПОСЛЕ пропущенной; должна попасть в результат,
            // доказывая, что разбор не остановился на пустом ключе выше.
            Row row5 = sheet.createRow(5);
            row5.createCell(0).setCellValue("F6");
            row5.createCell(1).setCellValue("Аптека 6");
            row5.createCell(2).setCellValue("Apteka F6 Daribar");
            row5.createCell(3).setCellValue("1004");

            // Строка 7 (индекс 6) намеренно не создаётся - настоящий конец данных (getRow вернёт
            // null), поэтому "мусорная" строка 8 (индекс 7) не должна попасть в результат разбора.
            Row trailing = sheet.createRow(7);
            trailing.createCell(0).setCellValue("F9");
            trailing.createCell(3).setCellValue("9999");

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
