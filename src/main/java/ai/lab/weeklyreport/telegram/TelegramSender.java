package ai.lab.weeklyreport.telegram;

import java.io.ByteArrayInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Отправка готовых отчётов и коротких сообщений об ошибках в Telegram. */
@Component
public class TelegramSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramSender.class);

    private final TelegramClient telegramClient;

    public TelegramSender(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    /**
     * Для достаточно длинных многословных имён Telegram сам подменяет document.file_name на
     * "безопасный" вариант (пробелы -> "_") при загрузке - это поведение серверов Telegram, не
     * этого кода (проверено: и сырой multipart с раздельными name/filename, и разные комбинации
     * символов не меняют результат). Логируем то, что реально сохранил Telegram, чтобы это было видно.
     */
    public void sendDocument(String chatId, String fileName, byte[] content, String caption) throws TelegramApiException {
        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(new ByteArrayInputStream(content), fileName))
                .caption(caption)
                .build();
        Message sent = telegramClient.execute(sendDocument);
        log.info("Отправлен документ, Telegram сохранил document.file_name='{}' (отправляли '{}')",
                sent.getDocument() != null ? sent.getDocument().getFileName() : null, fileName);
    }

    public void sendMessage(String chatId, String text) throws TelegramApiException {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        telegramClient.execute(sendMessage);
    }
}
