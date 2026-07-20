package ai.lab.weeklyreport.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Аудит обработанных файлов и ключ идемпотентности: telegram_file_id уникален,
 * что позволяет не обрабатывать повторно один и тот же файл при ретраях/рестартах бота.
 */
@Repository
public class IngestedFileRepository {

    private final JdbcTemplate jdbcTemplate;

    public IngestedFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByTelegramFileId(String telegramFileId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ingested_files WHERE telegram_file_id = ?", Integer.class, telegramFileId);
        return count != null && count > 0;
    }

    public void recordIngestedFile(String telegramFileId, String fileName, int rowCount) {
        jdbcTemplate.update(
                "INSERT INTO ingested_files (telegram_file_id, file_name, row_count) VALUES (?, ?, ?)",
                telegramFileId, fileName, rowCount);
    }

    public Optional<Instant> findLastIngestedAt() {
        Timestamp timestamp = jdbcTemplate.queryForObject("SELECT MAX(ingested_at) FROM ingested_files", Timestamp.class);
        return Optional.ofNullable(timestamp).map(Timestamp::toInstant);
    }
}
