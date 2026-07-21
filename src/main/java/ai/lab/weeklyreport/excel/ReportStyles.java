package ai.lab.weeklyreport.excel;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Стили корпоративного отчёта: точные HEX-цвета через {@link XSSFColor} (не {@code IndexedColors},
 * т.к. у неё ограниченная палитра). Стили кешируются по ключу, чтобы не плодить дубликаты в workbook.
 */
final class ReportStyles {

    private static final XSSFColor HEADER_BG = rgb(0x1F, 0x4E, 0x79);
    private static final XSSFColor SECTION_BG = rgb(0x30, 0x54, 0x96);
    private static final XSSFColor STRIPE_BG = rgb(0xF2, 0xF2, 0xF2);
    private static final XSSFColor TOTAL_BG = rgb(0xD9, 0xE1, 0xF2);
    private static final XSSFColor BORDER_COLOR = rgb(0xBF, 0xBF, 0xBF);
    private static final XSSFColor WHITE = rgb(0xFF, 0xFF, 0xFF);
    private static final XSSFColor BLACK = rgb(0x00, 0x00, 0x00);
    private static final XSSFColor POSITIVE = rgb(0x2E, 0x7D, 0x32);
    private static final XSSFColor NEGATIVE = rgb(0xC6, 0x28, 0x28);

    private static final String FORMAT_COUNT = "#,##0";
    private static final String FORMAT_MONEY = "#,##0.00";
    private static final String FORMAT_MONEY_WHOLE = "#,##0";
    private static final String FORMAT_DYNAMIC_COUNT = "+#,##0;-#,##0;0";
    private static final String FORMAT_DYNAMIC_MONEY = "+#,##0.00;-#,##0.00;0.00";
    private static final String FORMAT_PERCENT = "+0.0%;-0.0%;0.0%";
    private static final String FORMAT_PERCENT_PLAIN = "0.0%";

    private final XSSFWorkbook workbook;
    private final Map<String, XSSFCellStyle> cache = new HashMap<>();

    ReportStyles(XSSFWorkbook workbook) {
        this.workbook = workbook;
    }

    private static XSSFColor rgb(int r, int g, int b) {
        return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
    }

    XSSFCellStyle headerStyle() {
        return cache.computeIfAbsent("header", k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(HEADER_BG);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setFont(font(WHITE, true));
            return style;
        });
    }

    XSSFCellStyle sectionHeaderStyle() {
        return cache.computeIfAbsent("section", k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(SECTION_BG);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setFont(font(WHITE, true));
            return style;
        });
    }

    XSSFCellStyle labelStyle(boolean stripe) {
        return cache.computeIfAbsent("label-" + stripe, k -> {
            XSSFCellStyle style = base();
            applyStripe(style, stripe);
            style.setFont(font(BLACK, false));
            return style;
        });
    }

    XSSFCellStyle dataStyle(boolean stripe, boolean money) {
        return cache.computeIfAbsent("data-" + stripe + "-" + money, k -> {
            XSSFCellStyle style = base();
            applyStripe(style, stripe);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(money ? FORMAT_MONEY : FORMAT_COUNT));
            style.setFont(font(BLACK, false));
            return style;
        });
    }

    XSSFCellStyle totalLabelStyle() {
        return cache.computeIfAbsent("total-label", k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(TOTAL_BG);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setFont(font(BLACK, true));
            return style;
        });
    }

    XSSFCellStyle totalValueStyle(boolean money) {
        return cache.computeIfAbsent("total-value-" + money, k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(TOTAL_BG);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(money ? FORMAT_MONEY : FORMAT_COUNT));
            style.setFont(font(BLACK, true));
            return style;
        });
    }

    XSSFCellStyle wholeMoneyStyle(boolean stripe) {
        return cache.computeIfAbsent("whole-money-" + stripe, k -> {
            XSSFCellStyle style = base();
            applyStripe(style, stripe);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(FORMAT_MONEY_WHOLE));
            style.setFont(font(BLACK, false));
            return style;
        });
    }

    XSSFCellStyle totalWholeMoneyStyle() {
        return cache.computeIfAbsent("total-whole-money", k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(TOTAL_BG);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(FORMAT_MONEY_WHOLE));
            style.setFont(font(BLACK, true));
            return style;
        });
    }

    XSSFCellStyle percentStyle(boolean stripe) {
        return cache.computeIfAbsent("percent-" + stripe, k -> {
            XSSFCellStyle style = base();
            applyStripe(style, stripe);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(FORMAT_PERCENT_PLAIN));
            style.setFont(font(BLACK, false));
            return style;
        });
    }

    XSSFCellStyle totalPercentStyle() {
        return cache.computeIfAbsent("total-percent", k -> {
            XSSFCellStyle style = base();
            style.setFillForegroundColor(TOTAL_BG);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(FORMAT_PERCENT_PLAIN));
            style.setFont(font(BLACK, true));
            return style;
        });
    }

    XSSFCellStyle dynamicStyle(int signum, boolean money, boolean percent) {
        String key = "dynamic-" + signum + "-" + money + "-" + percent;
        return cache.computeIfAbsent(key, k -> {
            XSSFCellStyle style = base();
            String format = percent ? FORMAT_PERCENT : (money ? FORMAT_DYNAMIC_MONEY : FORMAT_DYNAMIC_COUNT);
            style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(format));
            XSSFColor color = signum > 0 ? POSITIVE : signum < 0 ? NEGATIVE : BLACK;
            style.setFont(font(color, true));
            return style;
        });
    }

    private void applyStripe(XSSFCellStyle style, boolean stripe) {
        if (stripe) {
            style.setFillForegroundColor(STRIPE_BG);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
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

    private XSSFFont font(XSSFColor color, boolean bold) {
        XSSFFont font = workbook.createFont();
        font.setColor(color);
        font.setBold(bold);
        return font;
    }
}
