package ai.lab.weeklyreport.metric;

/** Две программы лояльности, использующие одинаковый набор из 5 показателей (см. {@link LoyaltyMeasure}). */
public enum LoyaltyProgram {
    CORE("ПЛ"),
    JANYMDA("ПЛ Janymda");

    private final String label;

    LoyaltyProgram(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
