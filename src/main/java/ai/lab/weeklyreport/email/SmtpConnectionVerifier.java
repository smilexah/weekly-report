package ai.lab.weeklyreport.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import ai.lab.weeklyreport.config.EmailProperties;

/**
 * Проверяет SMTP-подключение (включая аутентификацию) один раз при старте приложения - но только
 * если задан хотя бы один получатель ({@code weekly-report.email.recipients}); если email-канал не
 * используется, проверка бессмысленна и пропускается. Как и сама отправка письма (см.
 * {@code WeeklyReportService}), это лучшая попытка поверх Telegram: неудачная проверка только
 * логируется и не мешает старту бота.
 */
@Component
public class SmtpConnectionVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SmtpConnectionVerifier.class);

    private final JavaMailSenderImpl mailSender;
    private final EmailProperties emailProperties;

    public SmtpConnectionVerifier(JavaMailSenderImpl mailSender, EmailProperties emailProperties) {
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (emailProperties.recipients().isEmpty()) {
            log.info("weekly-report.email.recipients пуст - email-канал выключен, проверка SMTP-соединения пропущена");
            return;
        }
        verify();
    }

    /** @return true, если удалось установить и закрыть SMTP-соединение (включая аутентификацию). */
    public boolean verify() {
        try {
            mailSender.testConnection();
            log.info("SMTP-соединение проверено успешно: host={}, port={}, user={}",
                    mailSender.getHost(), mailSender.getPort(), mailSender.getUsername());
            return true;
        } catch (Exception e) {
            log.error(SmtpFailureClassifier.describe(e, mailSender.getHost(), mailSender.getPort(), mailSender.getUsername()), e);
            return false;
        }
    }
}