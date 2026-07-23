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

import ai.lab.weeklyreport.metric.DivisionActivityTotal;
import ai.lab.weeklyreport.metric.DivisionMetricTotal;
import ai.lab.weeklyreport.metric.MetricRow;
import ai.lab.weeklyreport.metric.PharmacyDirectoryEntry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет LEFT JOIN daily_metrics/pharmacy_directory с группировкой по division_num (включая
 * "без дивизиона" как NULL-корзину) и FILTER для активных аптек - на реальном PostgreSQL.
 */
@Testcontainers
class DivisionReportRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private DivisionReportRepository repository;
    private DailyMetricRepository dailyMetricRepository;
    private PharmacyDirectoryRepository pharmacyDirectoryRepository;

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

        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        repository = new DivisionReportRepository(jdbcTemplate);
        dailyMetricRepository = new DailyMetricRepository(jdbcTemplate);
        pharmacyDirectoryRepository = new PharmacyDirectoryRepository(jdbcTemplate);
    }

    @Test
    void groupsMetricTotalsByDivisionAndBucketsUnresolvedUnderNull() {
        pharmacyDirectoryRepository.reloadAll(List.of(new PharmacyDirectoryEntry("F1", "адрес", "Алматы", "1", "Иванов")));
        LocalDate day = LocalDate.of(2026, 7, 20);
        dailyMetricRepository.upsertAll(List.of(
                new MetricRow("3001", "F1", 11, day, BigDecimal.valueOf(10)),
                new MetricRow("3002", "NOPE", 11, day, BigDecimal.valueOf(20))));

        List<DivisionMetricTotal> totals = repository.findMetricTotalsByDivision(day, day);

        DivisionMetricTotal division1 = totals.stream().filter(t -> "1".equals(t.divisionNum())).findFirst().orElseThrow();
        assertThat(division1.total()).isEqualByComparingTo("10");

        DivisionMetricTotal unresolved = totals.stream().filter(t -> t.divisionNum() == null).findFirst().orElseThrow();
        assertThat(unresolved.total()).isEqualByComparingTo("20");
    }

    @Test
    void activityTotalsCountDistinctPharmaciesAndFilterZeroValues() {
        pharmacyDirectoryRepository.reloadAll(List.of(new PharmacyDirectoryEntry("F1", "адрес", "Алматы", "1", "Иванов")));
        LocalDate day = LocalDate.of(2026, 7, 21);
        dailyMetricRepository.upsertAll(List.of(
                new MetricRow("3003", "F1", 11, day, BigDecimal.valueOf(5)),
                new MetricRow("3004", "F1", 11, day, BigDecimal.ZERO)));

        List<DivisionActivityTotal> totals = repository.findActivityTotalsByDivision(day, day);

        DivisionActivityTotal division1 = totals.stream().filter(t -> "1".equals(t.divisionNum())).findFirst().orElseThrow();
        assertThat(division1.pharmacyCount()).isEqualTo(2);
        assertThat(division1.activePharmacyCount()).isEqualTo(1);
    }

    @Test
    void closedStatusDivisionFoldsIntoUnresolvedBucketRegardlessOfCase() {
        pharmacyDirectoryRepository.reloadAll(List.of(
                new PharmacyDirectoryEntry("F1", "адрес", "Алматы", "1", "Иванов"),
                new PharmacyDirectoryEntry("F2", "адрес", "Павлодар", "Закрыта", "Шкурат Юлия")));
        LocalDate day = LocalDate.of(2026, 7, 22);
        dailyMetricRepository.upsertAll(List.of(
                new MetricRow("3005", "F1", 11, day, BigDecimal.valueOf(10)),
                new MetricRow("3006", "F2", 11, day, BigDecimal.valueOf(30))));

        List<DivisionMetricTotal> metricTotals = repository.findMetricTotalsByDivision(day, day);
        DivisionMetricTotal unresolvedMetric = metricTotals.stream().filter(t -> t.divisionNum() == null).findFirst().orElseThrow();
        assertThat(unresolvedMetric.total()).isEqualByComparingTo("30");
        assertThat(metricTotals).noneMatch(t -> "Закрыта".equalsIgnoreCase(t.divisionNum()));

        List<DivisionActivityTotal> activityTotals = repository.findActivityTotalsByDivision(day, day);
        DivisionActivityTotal unresolvedActivity = activityTotals.stream().filter(t -> t.divisionNum() == null).findFirst().orElseThrow();
        assertThat(unresolvedActivity.pharmacyCount()).isEqualTo(1);
        assertThat(unresolvedActivity.activePharmacyCount()).isEqualTo(1);
    }
}
