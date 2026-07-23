package ai.lab.weeklyreport.service;

import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.lab.weeklyreport.excel.MonthlyReportParser;
import ai.lab.weeklyreport.metric.MetricRow;
import ai.lab.weeklyreport.repository.DailyMetricRepository;
import ai.lab.weeklyreport.repository.IngestedFileRepository;

/**
 * Оркестрирует приём monthly_report.xlsx: проверка идемпотентности по telegram_file_id,
 * разбор файла, батчевый upsert в daily_metrics и запись строки аудита в ingested_files.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final MonthlyReportParser parser;
    private final DailyMetricRepository dailyMetricRepository;
    private final IngestedFileRepository ingestedFileRepository;

    public IngestionService(MonthlyReportParser parser,
                             DailyMetricRepository dailyMetricRepository,
                             IngestedFileRepository ingestedFileRepository) {
        this.parser = parser;
        this.dailyMetricRepository = dailyMetricRepository;
        this.ingestedFileRepository = ingestedFileRepository;
    }

    @Transactional
    public void ingest(String telegramFileId, String fileName, InputStream xlsxStream) {
        if (ingestedFileRepository.existsByTelegramFileId(telegramFileId)) {
            log.info("Файл '{}' (telegram_file_id={}) уже был обработан ранее, пропускаю", fileName, telegramFileId);
            return;
        }

        List<MetricRow> rows = parser.parse(xlsxStream);
        log.info("Файл '{}' разобран: {} строк, записываю в БД", fileName, rows.size());
        dailyMetricRepository.upsertAll(rows);
        ingestedFileRepository.recordIngestedFile(telegramFileId, fileName, rows.size());

        log.info("Файл '{}' записан в БД: {} строк", fileName, rows.size());
    }
}
