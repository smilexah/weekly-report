package ai.lab.weeklyreport.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.lab.weeklyreport.config.TelegramProperties;
import ai.lab.weeklyreport.service.WeeklyReportService;
import ai.lab.weeklyreport.telegram.TelegramSender;

/**
 * Раз в неделю (расписание берётся из конфига, а не хардкодится здесь - см. weekly-report.cron/timezone)
 * запускает генерацию и отправку недельного отчёта. Если что-то падает - полный стектрейс идёт в лог,
 * а короткое сообщение об ошибке - в тот же Telegram-чат, чтобы сбой было видно сразу, а не только в логах.
 */
@Component
public class WeeklyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportScheduler.class);

    private final WeeklyReportService weeklyReportService;
    private final TelegramSender telegramSender;
    private final TelegramProperties telegramProperties;

    public WeeklyReportScheduler(WeeklyReportService weeklyReportService,
                                  TelegramSender telegramSender,
                                  TelegramProperties telegramProperties) {
        this.weeklyReportService = weeklyReportService;
        this.telegramSender = telegramSender;
        this.telegramProperties = telegramProperties;
    }

    @Scheduled(cron = "${weekly-report.cron}", zone = "${weekly-report.timezone}")
    public void runWeeklyReport() {
        try {
            weeklyReportService.generateAndSendWeeklyReport();
        } catch (Exception e) {
            log.error("Не удалось сформировать/отправить недельный отчёт", e);
            notifyFailure(e);
        }
    }

    private void notifyFailure(Exception cause) {
        try {
            telegramSender.sendMessage(telegramProperties.reportChatId(),
                    "Ошибка формирования недельного отчёта: " + cause.getMessage()
                            + "\nПодробности - в логах приложения.");
        } catch (Exception sendError) {
            log.error("Не удалось отправить сообщение об ошибке в Telegram", sendError);
        }
    }
}
