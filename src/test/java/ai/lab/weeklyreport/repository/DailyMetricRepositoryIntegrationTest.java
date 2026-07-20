package ai.lab.weeklyreport.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
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
}
