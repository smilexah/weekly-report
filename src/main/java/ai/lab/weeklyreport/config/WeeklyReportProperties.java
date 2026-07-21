package ai.lab.weeklyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Расписание генерации недельного отчёта (вынесено из аннотации {@code @Scheduled}, чтобы
 * cron-выражение и таймзону можно было поменять через переменные окружения без пересборки) и пути
 * к справочникам дивизионов/Daribar (см. {@code ReferenceDataLoader}) - оба пути обязательны
 * (без значения по умолчанию в application.yml), но если по указанному пути файла не окажется,
 * приложение всё равно запустится - лист "Дивизионы" просто не заполнится данными.
 */
@ConfigurationProperties(prefix = "weekly-report")
public record WeeklyReportProperties(String cron, String timezone, String divisionsPath, String daribarCrosswalkPath) {
}
