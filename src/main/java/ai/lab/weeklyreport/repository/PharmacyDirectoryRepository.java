package ai.lab.weeklyreport.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ai.lab.weeklyreport.metric.DivisionRegistryRow;
import ai.lab.weeklyreport.metric.PharmacyDirectoryEntry;

/**
 * Справочник аптека -> дивизион -> директор (divisions.xlsx). Загружается разово при старте
 * приложения ({@code ReferenceDataLoader}) с полной перезаписью содержимого таблицы.
 */
@Repository
public class PharmacyDirectoryRepository {

    private static final int BATCH_SIZE = 1000;

    private static final String UPSERT_SQL = """
            INSERT INTO pharmacy_directory (branch_code, address, city, division_num, director_name)
            VALUES (:branchCode, :address, :city, :divisionNum, :directorName)
            ON CONFLICT (branch_code) DO UPDATE SET
                address = EXCLUDED.address, city = EXCLUDED.city,
                division_num = EXCLUDED.division_num, director_name = EXCLUDED.director_name
            """;

    // "Закрыта"/"закрыта" - не номер дивизиона, а статус ("закрыта"), которым в источнике иногда
    // помечают аптеку вместо номера дивизиона или NULL - такие строки не должны попадать в реестр
    // дивизионов как фиктивная "дивизия"; их daily_metrics схлопываются в "Без дивизиона / склад"
    // через аналогичный CASE в DivisionReportRepository.
    private static final String REGISTRY_SQL = """
            SELECT division_num, MAX(director_name) AS director_name, COUNT(*) AS pharmacy_count,
                   STRING_AGG(DISTINCT city, ', ' ORDER BY city) AS coverage
            FROM pharmacy_directory
            WHERE division_num IS NULL OR LOWER(TRIM(division_num)) <> 'закрыта'
            GROUP BY division_num
            ORDER BY length(division_num), division_num
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PharmacyDirectoryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void reloadAll(List<PharmacyDirectoryEntry> entries) {
        jdbcTemplate.getJdbcTemplate().execute("TRUNCATE TABLE pharmacy_directory");
        for (int start = 0; start < entries.size(); start += BATCH_SIZE) {
            List<PharmacyDirectoryEntry> chunk = entries.subList(start, Math.min(start + BATCH_SIZE, entries.size()));
            SqlParameterSource[] params = chunk.stream()
                    .map(PharmacyDirectoryRepository::toParams)
                    .toArray(SqlParameterSource[]::new);
            jdbcTemplate.batchUpdate(UPSERT_SQL, params);
        }
    }

    public List<DivisionRegistryRow> findDivisionRegistry() {
        return jdbcTemplate.query(REGISTRY_SQL, (rs, rowNum) -> new DivisionRegistryRow(
                rs.getString("division_num"),
                rs.getString("director_name"),
                rs.getInt("pharmacy_count"),
                rs.getString("coverage")));
    }

    private static SqlParameterSource toParams(PharmacyDirectoryEntry entry) {
        return new MapSqlParameterSource()
                .addValue("branchCode", entry.branchCode())
                .addValue("address", entry.address())
                .addValue("city", entry.city())
                .addValue("divisionNum", entry.divisionNum())
                .addValue("directorName", entry.directorName());
    }
}
