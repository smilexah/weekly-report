package ai.lab.weeklyreport.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Получатели недельного отчёта на почту (WEEKLY_REPORT_EMAIL_RECIPIENTS - список email через
 * запятую). Пусто по умолчанию - в этом случае email не отправляется, отчёт уходит только в
 * Telegram (см. {@code WeeklyReportService}). SMTP-подключение настраивается отдельно, через
 * стандартные spring.mail.* свойства (SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD).
 */
@ConfigurationProperties(prefix = "weekly-report.email")
public record EmailProperties(List<String> recipients) {

    public EmailProperties {
        recipients = recipients == null ? List.of() : recipients;
    }
}
