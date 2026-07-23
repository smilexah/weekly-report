package ai.lab.weeklyreport.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ai.lab.weeklyreport.metric.DaribarCrosswalkEntry;

/**
 * Сопоставление кода аптеки в Daribar с branch_code (daribar_crosswalk.xlsx). Загружается разово
 * при старте приложения с полной перезаписью содержимого таблицы. Источник может содержать
 * дублирующиеся daribar_code (известное ограничение) - строки дедуплицируются по daribar_code перед
 * батчингом (последняя строка в файле побеждает), т.к. с reWriteBatchedInserts=true драйвер может
 * схлопнуть batch в один multi-row INSERT, а Postgres не разрешает ON CONFLICT DO UPDATE дважды
 * задевать одну и ту же строку в рамках одной команды.
 */
@Repository
public class DaribarCrosswalkRepository {

    private static final int BATCH_SIZE = 1000;

    private static final String UPSERT_SQL = """
            INSERT INTO daribar_crosswalk (daribar_code, branch_code, pharmacy_name, comment)
            VALUES (:daribarCode, :branchCode, :pharmacyName, :comment)
            ON CONFLICT (daribar_code) DO UPDATE SET
                branch_code = EXCLUDED.branch_code, pharmacy_name = EXCLUDED.pharmacy_name, comment = EXCLUDED.comment
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DaribarCrosswalkRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void reloadAll(List<DaribarCrosswalkEntry> entries) {
        jdbcTemplate.getJdbcTemplate().execute("TRUNCATE TABLE daribar_crosswalk");
        List<DaribarCrosswalkEntry> deduped = dedupeByDaribarCode(entries);
        for (int start = 0; start < deduped.size(); start += BATCH_SIZE) {
            List<DaribarCrosswalkEntry> chunk = deduped.subList(start, Math.min(start + BATCH_SIZE, deduped.size()));
            SqlParameterSource[] params = chunk.stream()
                    .map(DaribarCrosswalkRepository::toParams)
                    .toArray(SqlParameterSource[]::new);
            jdbcTemplate.batchUpdate(UPSERT_SQL, params);
        }
    }

    private static List<DaribarCrosswalkEntry> dedupeByDaribarCode(List<DaribarCrosswalkEntry> entries) {
        Map<String, DaribarCrosswalkEntry> byDaribarCode = new LinkedHashMap<>();
        for (DaribarCrosswalkEntry entry : entries) {
            byDaribarCode.put(entry.daribarCode(), entry);
        }
        return List.copyOf(byDaribarCode.values());
    }

    private static SqlParameterSource toParams(DaribarCrosswalkEntry entry) {
        return new MapSqlParameterSource()
                .addValue("daribarCode", entry.daribarCode())
                .addValue("branchCode", entry.branchCode())
                .addValue("pharmacyName", entry.pharmacyName())
                .addValue("comment", entry.comment());
    }
}
