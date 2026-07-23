package ai.lab.weeklyreport.telegram;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import ai.lab.weeklyreport.config.TelegramProperties;
import ai.lab.weeklyreport.service.IngestionService;

/**
 * Слушает канал-источник (long polling) и на каждый channel_post с .xlsx-документом
 * скачивает файл и передаёт его в {@link IngestionService}. Ошибка обработки одного файла
 * логируется и не прерывает работу бота - следующий update будет обработан как обычно.
 */
@Component
public class ReportTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportTelegramBot.class);

    private final TelegramProperties properties;
    private final TelegramClient telegramClient;
    private final IngestionService ingestionService;

    public ReportTelegramBot(TelegramProperties properties, TelegramClient telegramClient, IngestionService ingestionService) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.ingestionService = ingestionService;
    }

    @Override
    public String getBotToken() {
        return properties.token();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasChannelPost()) {
            return;
        }
        Message post = update.getChannelPost();
        if (!post.hasDocument() || !isFromSourceChannel(post)) {
            return;
        }
        Document document = post.getDocument();
        String fileName = document.getFileName();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return;
        }
        try {
            downloadAndIngest(document, fileName);
        } catch (TelegramApiException | IOException | RuntimeException e) {
            log.error("Не удалось обработать файл '{}' из канала-источника", fileName, e);
        }
    }

    private void downloadAndIngest(Document document, String fileName) throws TelegramApiException, IOException {
        org.telegram.telegrambots.meta.api.objects.File telegramFile = telegramClient.execute(new GetFile(document.getFileId()));
        try (InputStream inputStream = telegramClient.downloadFileAsStream(telegramFile)) {
            log.info("Скачан файл '{}' из Telegram, начинаю обработку", fileName);
            ingestionService.ingest(document.getFileId(), fileName, inputStream);
        }
    }

    private boolean isFromSourceChannel(Message post) {
        String configured = properties.sourceChannelId();
        if (configured.equals(String.valueOf(post.getChatId()))) {
            return true;
        }
        String username = post.getChat().getUserName();
        String configuredHandle = configured.startsWith("@") ? configured.substring(1) : configured;
        return username != null && username.equalsIgnoreCase(configuredHandle);
    }

    @AfterBotRegistration
    public void afterRegistration(BotSession botSession) {
        log.info("Бот зарегистрирован, long polling запущен: {}", botSession.isRunning());
    }
}
