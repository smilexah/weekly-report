package ai.lab.weeklyreport.metric;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Одна строка данных, извлечённая из monthly_report.xlsx: значение конкретной метрики
 * для конкретной аптеки за конкретный день.
 */
public record MetricRow(String pharmacyCode, String branchCode, int metricNum, LocalDate metricDate, BigDecimal value) {
}
