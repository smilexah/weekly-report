package ai.lab.weeklyreport.metric;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Сумма значения метрики за день по всем аптекам - агрегат для недельного отчёта. */
public record MetricDailyTotal(LocalDate metricDate, int metricNum, BigDecimal total) {
}
