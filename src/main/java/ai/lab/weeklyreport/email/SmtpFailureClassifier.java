package ai.lab.weeklyreport.email;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.SendFailedException;

/**
 * Превращает низкоуровневые SMTP-исключения в понятное русскоязычное сообщение для логов, не
 * раскрывая пароль (используются только host/port/user - публичные значения). Используется и при
 * проверке соединения на старте ({@link SmtpConnectionVerifier}), и при фактической отправке письма
 * ({@link EmailSender}), чтобы причина сбоя была видна в логах без необходимости лезть в стектрейс.
 */
final class SmtpFailureClassifier {

    private SmtpFailureClassifier() {
    }

    static String describe(Throwable e, String host, int port, String user) {
        Throwable cause = rootCause(e);

        if (cause instanceof AuthenticationFailedException) {
            return "SMTP: ошибка авторизации на %s:%d (логин '%s') - проверьте SMTP_USER и SMTP_PASSWORD (обычный пароль ящика из Plesk, не пароль внешнего приложения)"
                    .formatted(host, port, user);
        }
        if (cause instanceof SSLHandshakeException || cause instanceof SSLPeerUnverifiedException) {
            return "SMTP: ошибка проверки SSL-сертификата на %s:%d - сертификат не прошёл проверку имени сервера или не является доверенным. Не отключайте проверку TLS (rejectUnauthorized/checkserveridentity=false) - уточните у хостинга (PS.kz/Plesk), на какое имя фактически выпущен сертификат для %s"
                    .formatted(host, port, host);
        }
        if (cause instanceof SendFailedException sfe) {
            return "SMTP: сервер отклонил отправителя или получателя (недопустимые адреса: %s)"
                    .formatted(Arrays.toString(sfe.getInvalidAddresses()));
        }
        if (cause instanceof UnknownHostException) {
            return "SMTP: не удалось разрешить хост '%s' - проверьте SMTP_HOST".formatted(host);
        }
        if (cause instanceof SocketTimeoutException) {
            return "SMTP: таймаут подключения к %s:%d".formatted(host, port);
        }
        if (cause instanceof ConnectException) {
            return "SMTP: не удалось подключиться к %s:%d - сервер недоступен или порт закрыт".formatted(host, port);
        }
        return "SMTP: не удалось отправить письмо через %s:%d (%s)".formatted(host, port, cause.getMessage());
    }

    private static Throwable rootCause(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}