package ai.lab.weeklyreport.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ai.lab.weeklyreport.metric.MetricDailyTotal;
import ai.lab.weeklyreport.metric.MetricRow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет batch-upsert (ON CONFLICT ... DO UPDATE) и агрегацию по дням на реальном PostgreSQL
 * (Testcontainers), а не на H2 - синтаксис ON CONFLICT должен быть проверен по-настоящему.
 */
@Testcontainers
class DailyMetricRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private DailyMetricRepository repository;
    private JdbcTemplate rawJdbc;

    @BeforeEach
    void setUp() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        repository = new DailyMetricRepository(new NamedParameterJdbcTemplate(dataSource));
        rawJdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void secondUpsertOverwritesValueForSameKey() {
        LocalDate day = LocalDate.of(2026, 7, 13);

        repository.upsertAll(List.of(new MetricRow("1001", "F1", 11, day, BigDecimal.valueOf(10))));
        repository.upsertAll(List.of(new MetricRow("1001", "F1", 11, day, BigDecimal.valueOf(25))));

        List<MetricDailyTotal> totals = repository.findDailyTotals(day, day);

        assertThat(totals).hasSize(1);
        assertThat(totals.get(0).total()).isEqualByComparingTo("25");
    }

    @Test
    void findDailyTotalsAggregatesAcrossPharmacies() {
        LocalDate day = LocalDate.of(2026, 7, 14);

        repository.upsertAll(List.of(
                new MetricRow("1001", "F1", 11, day, BigDecimal.valueOf(10)),
                new MetricRow("1002", "F2", 11, day, BigDecimal.valueOf(20))
        ));

        List<MetricDailyTotal> totals = repository.findDailyTotals(day, day);

        assertThat(totals).hasSize(1);
        assertThat(totals.get(0).metricNum()).isEqualTo(11);
        assertThat(totals.get(0).total()).isEqualByComparingTo("30");
    }

    @Test
    void ownBranchCodeWinsEvenWithConflictingCrosswalkRow() {
        LocalDate day = LocalDate.of(2026, 7, 15);
        rawJdbc.update("INSERT INTO daribar_crosswalk (daribar_code, branch_code) VALUES (?, ?)", "2001", "WRONG");

        repository.upsertAll(List.of(new MetricRow("2001", "F1", 11, day, BigDecimal.valueOf(5))));

        String branchCode = rawJdbc.queryForObject(
                "SELECT branch_code FROM daily_metrics WHERE pharmacy_code = ? AND metric_num = 11 AND metric_date = ?",
                String.class, "2001", day);
        assertThat(branchCode).isEqualTo("F1");
    }

    @Test
    void blankBranchCodeResolvesViaCrosswalkFallback() {
        LocalDate day = LocalDate.of(2026, 7, 16);
        rawJdbc.update("INSERT INTO daribar_crosswalk (daribar_code, branch_code) VALUES (?, ?)", "2002", "F2");

        repository.upsertAll(List.of(new MetricRow("2002", "", 11, day, BigDecimal.valueOf(5))));

        String branchCode = rawJdbc.queryForObject(
                "SELECT branch_code FROM daily_metrics WHERE pharmacy_code = ? AND metric_num = 11 AND metric_date = ?",
                String.class, "2002", day);
        assertThat(branchCode).isEqualTo("F2");
    }

    @Test
    void blankBranchCodeWithoutCrosswalkMatchResolvesToNull() {
        LocalDate day = LocalDate.of(2026, 7, 17);

        repository.upsertAll(List.of(new MetricRow("2003", "", 11, day, BigDecimal.valueOf(5))));

        String branchCode = rawJdbc.queryForObject(
                "SELECT branch_code FROM daily_metrics WHERE pharmacy_code = ? AND metric_num = 11 AND metric_date = ?",
                String.class, "2003", day);
        assertThat(branchCode).isNull();
    }
}
