package ai.lab.weeklyreport.repository;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ai.lab.weeklyreport.metric.DivisionRegistryRow;
import ai.lab.weeklyreport.metric.PharmacyDirectoryEntry;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет truncate+reload семантику и агрегацию справочника по дивизионам на реальном PostgreSQL. */
@Testcontainers
class PharmacyDirectoryRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private PharmacyDirectoryRepository repository;

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

        repository = new PharmacyDirectoryRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void reloadAllReplacesPreviousContents() {
        repository.reloadAll(List.of(new PharmacyDirectoryEntry("F1", "адрес 1", "Алматы", "1", "Иванов")));
        repository.reloadAll(List.of(new PharmacyDirectoryEntry("F2", "адрес 2", "Астана", "2", "Петров")));

        List<DivisionRegistryRow> registry = repository.findDivisionRegistry();

        assertThat(registry).hasSize(1);
        assertThat(registry.get(0).divisionNum()).isEqualTo("2");
        assertThat(registry.get(0).pharmacyCount()).isEqualTo(1);
    }

    @Test
    void findDivisionRegistryAggregatesPharmacyCountAndCoverage() {
        repository.reloadAll(List.of(
                new PharmacyDirectoryEntry("F1", "адрес 1", "Алматы", "1", "Иванов Иван"),
                new PharmacyDirectoryEntry("F2", "адрес 2", "Астана", "1", "Иванов Иван"),
                new PharmacyDirectoryEntry("F3", "адрес 3", "Шымкент", "2", "Петров Пётр")));

        List<DivisionRegistryRow> registry = repository.findDivisionRegistry();

        assertThat(registry).hasSize(2);
        DivisionRegistryRow division1 = registry.stream().filter(r -> r.divisionNum().equals("1")).findFirst().orElseThrow();
        assertThat(division1.pharmacyCount()).isEqualTo(2);
        assertThat(division1.directorName()).isEqualTo("Иванов Иван");
        assertThat(division1.coverage()).isEqualTo("Алматы, Астана");
    }
}
