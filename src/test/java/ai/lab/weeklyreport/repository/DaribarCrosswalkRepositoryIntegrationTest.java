package ai.lab.weeklyreport.repository;

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

import ai.lab.weeklyreport.metric.DaribarCrosswalkEntry;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет truncate+reload и "последняя строка побеждает" при дублях daribar_code на реальном PostgreSQL. */
@Testcontainers
class DaribarCrosswalkRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private DaribarCrosswalkRepository repository;
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

        repository = new DaribarCrosswalkRepository(new NamedParameterJdbcTemplate(dataSource));
        rawJdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void reloadAllReplacesPreviousContents() {
        repository.reloadAll(List.of(new DaribarCrosswalkEntry("1001", "F1", "Аптека 1", "")));
        repository.reloadAll(List.of(new DaribarCrosswalkEntry("1002", "F2", "Аптека 2", "")));

        Integer count = rawJdbc.queryForObject("SELECT count(*) FROM daribar_crosswalk", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void duplicateDaribarCodeInSameLoadKeepsLastRow() {
        repository.reloadAll(List.of(
                new DaribarCrosswalkEntry("1002", "F2", "Первая", ""),
                new DaribarCrosswalkEntry("1002", "F2B", "Вторая (дубликат)", "дубликат")));

        String branchCode = rawJdbc.queryForObject(
                "SELECT branch_code FROM daribar_crosswalk WHERE daribar_code = ?", String.class, "1002");
        assertThat(branchCode).isEqualTo("F2B");
    }
}
