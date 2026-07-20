package ai.lab.weeklyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки Telegram-бота: токен, канал-источник monthly_report.xlsx и чат для готовых отчётов.
 * Идентификаторы чатов хранятся как String, т.к. Telegram Bot API принимает как числовой chat_id,
 * так и @username канала в одном и том же поле.
 */
@ConfigurationProperties(prefix = "telegram.bot")
public record TelegramProperties(String token, String sourceChannelId, String reportChatId) {
}
