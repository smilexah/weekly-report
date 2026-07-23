package ai.lab.weeklyreport.excel;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Стили корпоративного отчёта - тёмно-синяя палитра (титулы/фиксированные заголовки, группирующие
 * заголовки на тон светлее, голубая подсветка итоговых колонок), воспроизводящая дизайн эталонного
 * файла "Недельный отчет по ПЛ и маркетплейсам" один в один, включая шрифты: листы "Маркетплейсы"/
 * "Программа лояльности"/"Дивизионы" в эталоне набраны Calibri (13/9/10 pt на титуле/подзаголовке/
 * данных), а лист "Сводный" - Arial (14/10/11 pt) - это не опечатка, а разный шрифт целого листа в
 * исходнике, поэтому у "Сводного" отдельный набор {@code summary*}-методов ниже. Точные HEX-цвета
 * через {@link XSSFColor} (не {@code IndexedColors}, у неё ограниченная палитра). Стили кешируются
 * по ключу, чтобы не плодить дубликаты в workbook.
 */
final class ReportStyles {

    private static final String FONT_DEFAULT = "Calibri";
    private static final String FONT_SUMMARY = "Arial";

    private static final XSSFColor NAVY_DARK = rgb(0x1C, 0x2B, 0x4A);
    private static final XSSFColor NAVY_MEDIUM = rgb(0x2E, 0x42, 0x72);
    private static final XSSFColor STRIPE_GRAY = rgb(0xF5, 0xF5, 0xF5);
    private static final XSSFColor WHITE_BG = rgb(0xFF, 0xFF, 0xFF);
    private static final XSSFColor ACCENT_BG = rgb(0xEA, 0xF0, 0xFB);
    private static final XSSFColor BORDER_COLOR = rgb(0xCC, 0xCC, 0xCC);
    private static final XSSFColor WHITE = rgb(0xFF, 0xFF, 0xFF);
    private static final XSSFColor BLACK = rgb(0x00, 0x00, 0x00);
    private static final XSSFColor SUBTITLE_TEXT = rgb(0x66, 0x66, 0x66);
    private static final XSSFColor SUBTITLE_TEXT_SUMMARY = rgb(0x55, 0x55, 0x55);
    private static final XSSFColor ACCENT_TEXT = NAVY_DARK;

    static final String FORMAT_NUMBER = "#,##0";
    static final String FORMAT_PERCENT = "0%";

    private final XSSFWorkbook workbook;
    private final Map<String, XSSFCellStyle> cache = new HashMap<>();

    ReportStyles(XSSFWorkbook workbook) {
        this.workbook = workbook;
    }

    private static XSSFColor rgb(int r, int g, int b) {
        return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
    }

    XSSFCellStyle titleStyle() {
        return cache.computeIfAbsent("title", k -> {
            XSSFCellStyle style = noBorder();
            style.setFillForegroundColor(NAVY_DARK);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFont(font(WHITE, true, (short) 13));
            return style;
        });
    }

    XSSFCellStyle subtitleStyle() {
        return cache.computeIfAbsent("subtitle", k -> {
            XSSFCellStyle style = noBorder();
            style.setFillForegroundColor(STRIPE_GRAY);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFont(font(SUBTITLE_TEXT, false, (short) 9));
            return style;
        });
    }

    /** Заголовок группы колонок на тон светлее фиксированного (день/канал/раздел) - без переноса строк. */
    XSSFCellStyle groupHeaderStyle() {
        return cache.computeIfAbsent("group-header", k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(NAVY_MEDIUM);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFont(font(WHITE, true, (short) 9));
            return style;
        });
    }

    // --- Лист "Сводный": в эталонном файле набран Arial, с другими размерами (14/10/11 pt) ---

    XSSFCellStyle summaryTitleStyle() {
        return cache.computeIfAbsent("summary-title", k -> {
            XSSFCellStyle style = noBorder();
            style.setFillForegroundColor(NAVY_DARK);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFont(font(WHITE, true, (short) 14, FONT_SUMMARY));
            return style;
        });
    }

    XSSFCellStyle summarySubtitleStyle() {
        return cache.computeIfAbsent("summary-subtitle", k -> {
            XSSFCellStyle style = noBorder();
            style.setFillForegroundColor(STRIPE_GRAY);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFont(font(SUBTITLE_TEXT_SUMMARY, false, (short) 10, FONT_SUMMARY));
            return style;
        });
    }

    XSSFCellStyle summaryFixedHeaderStyle() {
        return cache.computeIfAbsent("summary-fixed-header", k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(NAVY_DARK);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            style.setFont(font(WHITE, true, (short) 9, FONT_SUMMARY));
            return style;
        });
    }

    XSSFCellStyle summarySectionHeaderStyle() {
        return cache.computeIfAbsent("summary-section-header", k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(NAVY_MEDIUM);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFont(font(WHITE, true, (short) 10, FONT_SUMMARY));
            return style;
        });
    }

    XSSFCellStyle summaryLabelStyle(boolean stripe) {
        return cache.computeIfAbsent("summary-label-" + stripe, k -> {
            XSSFCellStyle style = base();
            applyStripe(style, stripe);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFont(font(BLACK, false, (short) 11, FONT_SUMMARY));
            return style;
        });
    }

    XSSFCellStyle summaryDataStyle(boolean stripe, String numberFormat) {
        return cache.computeIfAbsent("summary-data-" + stripe + "-" + numberFormat, k -> {
            XSSFCellStyle style = base();
            applyStripe(style, stripe);
            style.setAlignment(HorizontalAlignment.RIGHT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(numberFormat));
            style.setFont(font(BLACK, false, (short) 11, FONT_SUMMARY));
            return style;
        });
    }

    XSSFCellStyle summaryAccentStyle(String numberFormat) {
        return cache.computeIfAbsent("summary-accent-" + numberFormat, k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(ACCENT_BG);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.RIGHT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(numberFormat));
            style.setFont(font(ACCENT_TEXT, true, (short) 11, FONT_SUMMARY));
            return style;
        });
    }

    /** Заголовок фиксированной колонки/итоговой группы - на тон темнее {@link #groupHeaderStyle()}. */
    XSSFCellStyle fixedHeaderStyle() {
        return cache.computeIfAbsent("fixed-header", k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(NAVY_DARK);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            style.setFont(font(WHITE, true, (short) 9));
            return style;
        });
    }

    XSSFCellStyle labelStyle(boolean stripe, boolean bold) {
        return cache.computeIfAbsent("label-" + stripe + "-" + bold, k -> {
            XSSFCellStyle style = base();
            applyStripe(style, stripe);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFont(font(BLACK, bold, (short) 10));
            return style;
        });
    }

    XSSFCellStyle centerLabelStyle(boolean stripe, boolean bold) {
        return cache.computeIfAbsent("center-label-" + stripe + "-" + bold, k -> {
            XSSFCellStyle style = base();
            applyStripe(style, stripe);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFont(font(BLACK, bold, (short) 10));
            return style;
        });
    }

    /** Для текста, который может перенестись на несколько строк (например, охват регионов). */
    XSSFCellStyle wrapLabelStyle(boolean stripe) {
        return cache.computeIfAbsent("wrap-label-" + stripe, k -> {
            XSSFCellStyle style = base();
            applyStripe(style, stripe);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            style.setFont(font(BLACK, false, (short) 9));
            return style;
        });
    }

    XSSFCellStyle dataStyle(boolean stripe, String numberFormat) {
        return cache.computeIfAbsent("data-" + stripe + "-" + numberFormat, k -> {
            XSSFCellStyle style = base();
            applyStripe(style, stripe);
            style.setAlignment(HorizontalAlignment.RIGHT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(numberFormat));
            style.setFont(font(BLACK, false, (short) 10));
            return style;
        });
    }

    /** Подсветка итоговой/накопительной колонки (ИТОГО за неделю, МП ИТОГО, Динамика). */
    XSSFCellStyle accentStyle(String numberFormat) {
        return cache.computeIfAbsent("accent-" + numberFormat, k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(ACCENT_BG);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.RIGHT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(numberFormat));
            style.setFont(font(ACCENT_TEXT, true, (short) 10));
            return style;
        });
    }

    XSSFCellStyle totalRowLabelStyle() {
        return cache.computeIfAbsent("total-row-label", k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(NAVY_DARK);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFont(font(WHITE, true, (short) 10));
            return style;
        });
    }

    XSSFCellStyle totalRowValueStyle(String numberFormat) {
        return cache.computeIfAbsent("total-row-value-" + numberFormat, k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(NAVY_DARK);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.RIGHT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(numberFormat));
            style.setFont(font(WHITE, true, (short) 10));
            return style;
        });
    }

    private void applyStripe(XSSFCellStyle style, boolean stripe) {
        style.setFillForegroundColor(stripe ? STRIPE_GRAY : WHITE_BG);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private XSSFCellStyle base() {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBottomBorderColor(BORDER_COLOR);
        style.setTopBorderColor(BORDER_COLOR);
        style.setLeftBorderColor(BORDER_COLOR);
        style.setRightBorderColor(BORDER_COLOR);
        return style;
    }

    private XSSFCellStyle noBorder() {
        return workbook.createCellStyle();
    }

    private XSSFFont font(XSSFColor color, boolean bold, short size) {
        return font(color, bold, size, FONT_DEFAULT);
    }

    private XSSFFont font(XSSFColor color, boolean bold, short size, String fontName) {
        XSSFFont font = workbook.createFont();
        font.setColor(color);
        font.setBold(bold);
        font.setFontHeightInPoints(size);
        font.setFontName(fontName);
        return font;
    }
}
