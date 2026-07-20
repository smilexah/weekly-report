package ai.lab.weeklyreport.metric;

/**
 * Справочник по числовым id метрик из monthly_report.xlsx:
 * 1-5 - основная программа лояльности (ПЛ), 6-10 - ПЛ Janymda (те же 5 показателей),
 * 11-18 - заказы по маркетплейсам (пары "кол-во"+"сумма" для Daribar/Glovo/Emdel/Wolt).
 */
public final class MetricCatalog {

    public static final int LOYALTY_CORE_START = 1;
    public static final int LOYALTY_JANYMDA_START = 6;
    public static final int MARKETPLACE_START = 11;
    public static final int MARKETPLACE_END = 18;

    private MetricCatalog() {
    }

    public static boolean isLoyalty(int metricNum) {
        return metricNum >= LOYALTY_CORE_START && metricNum < MARKETPLACE_START;
    }

    public static boolean isMarketplace(int metricNum) {
        return metricNum >= MARKETPLACE_START && metricNum <= MARKETPLACE_END;
    }

    public static LoyaltyProgram loyaltyProgram(int metricNum) {
        if (!isLoyalty(metricNum)) {
            throw new IllegalArgumentException("Не показатель программы лояльности: " + metricNum);
        }
        return metricNum < LOYALTY_JANYMDA_START ? LoyaltyProgram.CORE : LoyaltyProgram.JANYMDA;
    }

    public static LoyaltyMeasure loyaltyMeasure(int metricNum) {
        int offset = loyaltyProgram(metricNum) == LoyaltyProgram.CORE
                ? metricNum - LOYALTY_CORE_START
                : metricNum - LOYALTY_JANYMDA_START;
        return LoyaltyMeasure.values()[offset];
    }

    public static MetricKind marketplaceKind(int metricNum) {
        if (!isMarketplace(metricNum)) {
            throw new IllegalArgumentException("Не метрика маркетплейса: " + metricNum);
        }
        return metricNum % 2 == 0 ? MetricKind.SUM : MetricKind.COUNT;
    }

    /** Извлекает числовой id метрики из строки вида {@code "11-Заказы Daribar (кол-во)"}. */
    public static int parseMetricNum(String metricLabel) {
        String prefix = metricLabel.split("-", 2)[0].trim();
        try {
            return Integer.parseInt(prefix);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Не удалось извлечь id метрики из: " + metricLabel, e);
        }
    }
}
