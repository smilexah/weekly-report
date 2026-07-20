package ai.lab.weeklyreport.metric;

/**
 * Пять показателей программы лояльности. Порядковый номер (ordinal) соответствует
 * смещению внутри блока метрик 1-5 (основная ПЛ) и 6-10 (ПЛ Janymda).
 */
public enum LoyaltyMeasure {
    NEW_CLIENTS("Новых клиентов"),
    ACCRUAL_COUNT("Начисления, кол-во"),
    ACCRUAL_SUM("Начисления, сумма"),
    REDEMPTION_COUNT("Списания, кол-во"),
    REDEMPTION_SUM("Списания, сумма");

    private final String label;

    LoyaltyMeasure(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
