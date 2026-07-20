package ai.lab.weeklyreport.metric;

/** Вид значения метрики маркетплейса: количество заказов или сумма. */
public enum MetricKind {
    COUNT("кол-во"),
    SUM("сумма");

    private final String label;

    MetricKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
