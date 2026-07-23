package ai.lab.weeklyreport.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import ai.lab.weeklyreport.metric.DivisionActivityTotal;
import ai.lab.weeklyreport.metric.DivisionMetricTotal;

/**
 * Агрегаты daily_metrics в разрезе дивизиона (join с pharmacy_directory по branch_code) -
 * для листа "Дивизионы". LEFT JOIN оставляет division_num = NULL для строк, чей branch_code либо
 * не задан, либо не найден в справочнике - это единая корзина "Без дивизиона / склад". Значение
 * "Закрыта"/"закрыта" в division_num - это статус, а не номер дивизиона (см.
 * {@link PharmacyDirectoryRepository#findDivisionRegistry()}), поэтому здесь оно тоже приводится
 * к NULL и схлопывается в ту же корзину "Без дивизиона / склад", а не остаётся фиктивной "дивизией".
 */
@Repository
public class DivisionReportRepository {

    private static final String DIVISION_NUM_EXPR = "CASE WHEN LOWER(TRIM(pd.division_num)) = 'закрыта' THEN NULL ELSE pd.division_num END";

    private static final String METRIC_TOTALS_SQL = """
            SELECT %s AS division_num, dm.metric_num AS metric_num, SUM(dm.value) AS total
            FROM daily_metrics dm
            LEFT JOIN pharmacy_directory pd ON pd.branch_code = dm.branch_code
            WHERE dm.metric_date BETWEEN :start AND :end
            GROUP BY %s, dm.metric_num
            """.formatted(DIVISION_NUM_EXPR, DIVISION_NUM_EXPR);

    private static final String ACTIVITY_TOTALS_SQL = """
            SELECT %s AS division_num,
                   COUNT(DISTINCT dm.pharmacy_code) AS pharmacy_count,
                   COUNT(DISTINCT dm.pharmacy_code) FILTER (WHERE dm.value <> 0) AS active_pharmacy_count
            FROM daily_metrics dm
            LEFT JOIN pharmacy_directory pd ON pd.branch_code = dm.branch_code
            WHERE dm.metric_date BETWEEN :start AND :end
            GROUP BY %s
            """.formatted(DIVISION_NUM_EXPR, DIVISION_NUM_EXPR);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DivisionReportRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DivisionMetricTotal> findMetricTotalsByDivision(LocalDate startInclusive, LocalDate endInclusive) {
        SqlParameterSource params = rangeParams(startInclusive, endInclusive);
        return jdbcTemplate.query(METRIC_TOTALS_SQL, params, (rs, rowNum) -> new DivisionMetricTotal(
                rs.getString("division_num"),
                rs.getInt("metric_num"),
                rs.getBigDecimal("total")));
    }

    public List<DivisionActivityTotal> findActivityTotalsByDivision(LocalDate startInclusive, LocalDate endInclusive) {
        SqlParameterSource params = rangeParams(startInclusive, endInclusive);
        return jdbcTemplate.query(ACTIVITY_TOTALS_SQL, params, (rs, rowNum) -> new DivisionActivityTotal(
                rs.getString("division_num"),
                rs.getInt("pharmacy_count"),
                rs.getInt("active_pharmacy_count")));
    }

    private static SqlParameterSource rangeParams(LocalDate start, LocalDate end) {
        return new MapSqlParameterSource()
                .addValue("start", start)
                .addValue("end", end);
    }
}
