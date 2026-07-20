package ai.lab.weeklyreport.telegram;

import java.io.ByteArrayInputStream;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** Отправка готовых отчётов и коротких сообщений об ошибках в Telegram. */
@Component
public class TelegramSender {

    private final TelegramClient telegramClient;

    public TelegramSender(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void sendDocument(String chatId, String fileName, byte[] content, String caption) throws TelegramApiException {
        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(new ByteArrayInputStream(content), fileName))
                .caption(caption)
                .build();
        telegramClient.execute(sendDocument);
    }

    public void sendMessage(String chatId, String text) throws TelegramApiException {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
        telegramClient.execute(sendMessage);
    }
}
