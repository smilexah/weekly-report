package ai.lab.weeklyreport.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import ai.lab.weeklyreport.metric.MetricDailyTotal;
import ai.lab.weeklyreport.metric.MetricRow;

/**
 * Батчевый upsert строк daily_metrics и агрегация по дням/метрикам для недельного отчёта.
 * Файл из Telegram содержит всю историю целиком, поэтому конфликтующие строки просто
 * перезаписываются актуальным значением (ON CONFLICT ... DO UPDATE), без diff-логики.
 */
@Repository
public class DailyMetricRepository {

    private static final int BATCH_SIZE = 1000;

    private static final String UPSERT_SQL = """
            INSERT INTO daily_metrics (pharmacy_code, branch_code, metric_num, metric_date, value, updated_at)
            VALUES (:pharmacyCode, :branchCode, :metricNum, :metricDate, :value, now())
            ON CONFLICT (pharmacy_code, metric_num, metric_date)
            DO UPDATE SET branch_code = EXCLUDED.branch_code, value = EXCLUDED.value, updated_at = now()
            """;

    private static final String DAILY_TOTALS_SQL = """
            SELECT metric_date, metric_num, SUM(value) AS total
            FROM daily_metrics
            WHERE metric_date BETWEEN :start AND :end
            GROUP BY metric_date, metric_num
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DailyMetricRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertAll(List<MetricRow> rows) {
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<MetricRow> chunk = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            SqlParameterSource[] params = chunk.stream()
                    .map(DailyMetricRepository::toParams)
                    .toArray(SqlParameterSource[]::new);
            jdbcTemplate.batchUpdate(UPSERT_SQL, params);
        }
    }

    public List<MetricDailyTotal> findDailyTotals(LocalDate startInclusive, LocalDate endInclusive) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", startInclusive)
                .addValue("end", endInclusive);
        return jdbcTemplate.query(DAILY_TOTALS_SQL, params, (rs, rowNum) -> new MetricDailyTotal(
                rs.getDate("metric_date").toLocalDate(),
                rs.getInt("metric_num"),
                rs.getBigDecimal("total")));
    }

    private static SqlParameterSource toParams(MetricRow row) {
        return new MapSqlParameterSource()
                .addValue("pharmacyCode", row.pharmacyCode())
                .addValue("branchCode", row.branchCode())
                .addValue("metricNum", row.metricNum())
                .addValue("metricDate", row.metricDate())
                .addValue("value", row.value(), java.sql.Types.NUMERIC);
    }
}
