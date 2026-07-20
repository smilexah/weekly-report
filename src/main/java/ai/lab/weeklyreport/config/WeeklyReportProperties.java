package ai.lab.weeklyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Расписание генерации недельного отчёта. Вынесено из аннотации {@code @Scheduled},
 * чтобы cron-выражение и таймзону можно было поменять через переменные окружения без пересборки.
 */
@ConfigurationProperties(prefix = "weekly-report")
public record WeeklyReportProperties(String cron, String timezone) {
}
