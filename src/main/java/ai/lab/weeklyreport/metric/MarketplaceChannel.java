package ai.lab.weeklyreport.metric;

/**
 * Каналы маркетплейсов (метрики 11-18): для каждого канала два показателя подряд -
 * "кол-во" (нечётный номер) и "сумма" (следующий чётный номер).
 */
public enum MarketplaceChannel {
    DARIBAR(11, "Daribar"),
    GLOVO(13, "Glovo"),
    EMDEL(15, "Emdel"),
    WOLT(17, "Wolt");

    private final int countMetricNum;
    private final String displayName;

    MarketplaceChannel(int countMetricNum, String displayName) {
        this.countMetricNum = countMetricNum;
        this.displayName = displayName;
    }

    public int countMetricNum() {
        return countMetricNum;
    }

    public int sumMetricNum() {
        return countMetricNum + 1;
    }

    public String displayName() {
        return displayName;
    }

    public static MarketplaceChannel fromMetricNum(int metricNum) {
        int countNum = metricNum % 2 == 0 ? metricNum - 1 : metricNum;
        for (MarketplaceChannel channel : values()) {
            if (channel.countMetricNum == countNum) {
                return channel;
            }
        }
        throw new IllegalArgumentException("Неизвестная метрика маркетплейса: " + metricNum);
    }
}
