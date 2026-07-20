package ai.lab.weeklyreport.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки "сторожа" приёма файлов: как часто проверять и после какого молчания
 * (нет новых monthly_report.xlsx) слать алерт в Telegram.
 */
@ConfigurationProperties(prefix = "ingestion-watchdog")
public record IngestionWatchdogProperties(Duration maxSilence, Duration checkInterval) {
}
