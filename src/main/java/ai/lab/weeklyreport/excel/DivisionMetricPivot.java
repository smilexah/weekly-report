package ai.lab.weeklyreport.excel;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.lab.weeklyreport.metric.DivisionActivityTotal;
import ai.lab.weeklyreport.metric.DivisionMetricTotal;

/**
 * Быстрый доступ к агрегатам daily_metrics в разрезе дивизиона, для листа "Дивизионы".
 * Ключ {@code null} обозначает "Без дивизиона / склад" - {@link HashMap} допускает null-ключ.
 */
final class DivisionMetricPivot {

    private final Map<String, Map<Integer, BigDecimal>> metricData = new HashMap<>();
    private final Map<String, DivisionActivityTotal> activityData = new HashMap<>();

    DivisionMetricPivot(List<DivisionMetricTotal> metricTotals, List<DivisionActivityTotal> activityTotals) {
        for (DivisionMetricTotal total : metricTotals) {
            metricData.computeIfAbsent(total.divisionNum(), d -> new HashMap<>()).put(total.metricNum(), total.total());
        }
        for (DivisionActivityTotal total : activityTotals) {
            activityData.put(total.divisionNum(), total);
        }
    }

    BigDecimal metricValue(String divisionNum, int metricNum) {
        return metricData.getOrDefault(divisionNum, Map.of()).getOrDefault(metricNum, BigDecimal.ZERO);
    }

    DivisionActivityTotal activity(String divisionNum) {
        return activityData.getOrDefault(divisionNum, new DivisionActivityTotal(divisionNum, 0, 0));
    }
}
