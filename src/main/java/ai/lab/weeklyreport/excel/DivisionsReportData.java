package ai.lab.weeklyreport.excel;

import java.util.List;

import ai.lab.weeklyreport.metric.DivisionActivityTotal;
import ai.lab.weeklyreport.metric.DivisionMetricTotal;
import ai.lab.weeklyreport.metric.DivisionRegistryRow;

/** Входные данные для листа "Дивизионы" - только текущая неделя, без сравнения с прошлой. */
public record DivisionsReportData(List<DivisionRegistryRow> registry,
                                   List<DivisionMetricTotal> metricTotals,
                                   List<DivisionActivityTotal> activityTotals) {
}
