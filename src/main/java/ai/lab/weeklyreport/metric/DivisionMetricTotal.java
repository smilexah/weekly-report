package ai.lab.weeklyreport.metric;

import java.math.BigDecimal;

/**
 * Сумма значения метрики за неделю в разрезе дивизиона - агрегат для листа "Дивизионы".
 * {@code divisionNum == null} обозначает аптеки без резолвленного branch_code ("Без дивизиона / склад").
 */
public record DivisionMetricTotal(String divisionNum, int metricNum, BigDecimal total) {
}
