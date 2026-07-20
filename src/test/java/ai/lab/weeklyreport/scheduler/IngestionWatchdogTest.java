package ai.lab.weeklyreport.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import ai.lab.weeklyreport.config.IngestionWatchdogProperties;
import ai.lab.weeklyreport.config.TelegramProperties;
import ai.lab.weeklyreport.repository.IngestedFileRepository;
import ai.lab.weeklyreport.telegram.TelegramSender;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IngestionWatchdogTest {

    private static final String REPORT_CHAT_ID = "report-chat";

    private final IngestedFileRepository ingestedFileRepository = mock(IngestedFileRepository.class);
    private final TelegramSender telegramSender = mock(TelegramSender.class);
    private final TelegramProperties telegramProperties = new TelegramProperties("token", "source-channel", REPORT_CHAT_ID);
    private final IngestionWatchdogProperties properties = new IngestionWatchdogProperties(Duration.ofHours(26), Duration.ofHours(1));
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);

    private final IngestionWatchdog watchdog =
            new IngestionWatchdog(ingestedFileRepository, telegramSender, telegramProperties, properties, clock);

    @Test
    void doesNotAlertWhenLastFileIsWithinMaxSilence() throws Exception {
        when(ingestedFileRepository.findLastIngestedAt()).thenReturn(Optional.of(Instant.parse("2026-07-19T09:00:00Z"))); // 25h ago

        watchdog.checkForStaleIngestion();

        verifyNoInteractions(telegramSender);
    }

    @Test
    void alertsOnceThenSuppressesRepeatsUntilRecoveredAndStaleAgain() throws Exception {
        Instant staleTimestamp = Instant.parse("2026-07-18T00:00:00Z"); // well over 26h ago
        when(ingestedFileRepository.findLastIngestedAt()).thenReturn(Optional.of(staleTimestamp));

        watchdog.checkForStaleIngestion();
        watchdog.checkForStaleIngestion();
        verify(telegramSender, times(1)).sendMessage(eq(REPORT_CHAT_ID), anyString());

        // Свежий файл пришёл - молчание закончилось, флаг должен сброситься.
        when(ingestedFileRepository.findLastIngestedAt()).thenReturn(Optional.of(Instant.parse("2026-07-20T09:30:00Z")));
        watchdog.checkForStaleIngestion();
        verify(telegramSender, times(1)).sendMessage(anyString(), anyString());

        // Снова стало тихо - должен сработать новый алерт.
        when(ingestedFileRepository.findLastIngestedAt()).thenReturn(Optional.of(staleTimestamp));
        watchdog.checkForStaleIngestion();
        verify(telegramSender, times(2)).sendMessage(anyString(), anyString());
    }

    @Test
    void alertsWhenNoFileWasEverIngested() throws Exception {
        when(ingestedFileRepository.findLastIngestedAt()).thenReturn(Optional.empty());

        watchdog.checkForStaleIngestion();

        verify(telegramSender).sendMessage(eq(REPORT_CHAT_ID), contains("ни разу"));
    }
}
