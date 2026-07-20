package ai.lab.weeklyreport.excel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.lab.weeklyreport.metric.MetricDailyTotal;

/** Быстрый доступ к агрегированным суммам метрик по дню, для построения листов отчёта. */
public final class MetricPivot {

    private final Map<LocalDate, Map<Integer, BigDecimal>> data = new HashMap<>();

    public MetricPivot(List<MetricDailyTotal> totals) {
        for (MetricDailyTotal total : totals) {
            data.computeIfAbsent(total.metricDate(), d -> new HashMap<>()).put(total.metricNum(), total.total());
        }
    }

    public BigDecimal value(LocalDate date, int metricNum) {
        return data.getOrDefault(date, Map.of()).getOrDefault(metricNum, BigDecimal.ZERO);
    }

    public BigDecimal sumOverDays(Collection<LocalDate> days, int metricNum) {
        return days.stream().map(day -> value(day, metricNum)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
