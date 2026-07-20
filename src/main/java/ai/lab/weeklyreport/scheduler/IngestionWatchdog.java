package ai.lab.weeklyreport.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.lab.weeklyreport.config.IngestionWatchdogProperties;
import ai.lab.weeklyreport.config.TelegramProperties;
import ai.lab.weeklyreport.repository.IngestedFileRepository;
import ai.lab.weeklyreport.telegram.TelegramSender;

/**
 * Периодически проверяет, что monthly_report.xlsx из канала-источника всё ещё приходят
 * (в ingested_files появляются новые записи). Если файла нет дольше ingestion-watchdog.max-silence -
 * шлёт алерт в тот же Telegram-чат, что и отчёты/ошибки крона, чтобы проблему с каналом
 * заметили сразу, а не спустя дни молчания.
 * <p>
 * Алерт шлётся один раз за "эпизод молчания" (флаг сбрасывается, как только снова
 * появляется свежий файл), чтобы не спамить одним и тем же сообщением на каждой проверке.
 */
@Component
public class IngestionWatchdog {

    private static final Logger log = LoggerFactory.getLogger(IngestionWatchdog.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final IngestedFileRepository ingestedFileRepository;
    private final TelegramSender telegramSender;
    private final TelegramProperties telegramProperties;
    private final IngestionWatchdogProperties properties;
    private final Clock clock;
    private final AtomicBoolean alertAlreadySent = new AtomicBoolean(false);

    public IngestionWatchdog(IngestedFileRepository ingestedFileRepository,
                              TelegramSender telegramSender,
                              TelegramProperties telegramProperties,
                              IngestionWatchdogProperties properties,
                              Clock clock) {
        this.ingestedFileRepository = ingestedFileRepository;
        this.telegramSender = telegramSender;
        this.telegramProperties = telegramProperties;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            fixedRateString = "${ingestion-watchdog.check-interval}",
            initialDelayString = "${ingestion-watchdog.check-interval}")
    public void checkForStaleIngestion() {
        Optional<Instant> lastIngestedAt = ingestedFileRepository.findLastIngestedAt();
        Instant now = Instant.now(clock);
        Duration sinceLast = lastIngestedAt.map(t -> Duration.between(t, now)).orElse(Duration.ofDays(365));

        if (sinceLast.compareTo(properties.maxSilence()) <= 0) {
            alertAlreadySent.set(false);
            return;
        }

        if (alertAlreadySent.compareAndSet(false, true)) {
            notifyStale(lastIngestedAt, sinceLast);
        }
    }

    private void notifyStale(Optional<Instant> lastIngestedAt, Duration sinceLast) {
        String lastSeen = lastIngestedAt
                .map(t -> t.atZone(ZoneId.systemDefault()).format(TIMESTAMP_FORMAT))
                .orElse("ни разу с момента запуска бота");
        String message = "Давно не было нового monthly_report.xlsx: последний файл обработан %s (%d ч. назад). Проверьте канал-источник."
                .formatted(lastSeen, sinceLast.toHours());

        log.warn(message);
        try {
            telegramSender.sendMessage(telegramProperties.reportChatId(), message);
        } catch (Exception sendError) {
            log.error("Не удалось отправить алерт о молчании канала-источника в Telegram", sendError);
        }
    }
}
