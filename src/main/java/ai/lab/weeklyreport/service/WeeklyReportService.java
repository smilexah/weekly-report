package ai.lab.weeklyreport.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import ai.lab.weeklyreport.config.EmailProperties;
import ai.lab.weeklyreport.config.TelegramProperties;
import ai.lab.weeklyreport.email.EmailSender;
import ai.lab.weeklyreport.excel.DivisionsReportData;
import ai.lab.weeklyreport.excel.WeekRange;
import ai.lab.weeklyreport.excel.WeeklyReportGenerator;
import ai.lab.weeklyreport.metric.MetricDailyTotal;
import ai.lab.weeklyreport.repository.DailyMetricRepository;
import ai.lab.weeklyreport.repository.DivisionReportRepository;
import ai.lab.weeklyreport.repository.PharmacyDirectoryRepository;
import ai.lab.weeklyreport.telegram.TelegramSender;

/** Собирает недельный отчёт (пн-вс прошлой недели) из БД и отправляет его в Telegram-чат. */
@Service
public class WeeklyReportService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportService.class);

    private static final DateTimeFormatter FILE_DAY_MONTH_FORMAT = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter FILE_DAY_FORMAT = DateTimeFormatter.ofPattern("dd");

    private final DailyMetricRepository dailyMetricRepository;
    private final DivisionReportRepository divisionReportRepository;
    private final PharmacyDirectoryRepository pharmacyDirectoryRepository;
    private final WeeklyReportGenerator generator;
    private final TelegramSender telegramSender;
    private final TelegramProperties telegramProperties;
    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final Clock clock;

    public WeeklyReportService(DailyMetricRepository dailyMetricRepository,
                                DivisionReportRepository divisionReportRepository,
                                PharmacyDirectoryRepository pharmacyDirectoryRepository,
                                WeeklyReportGenerator generator,
                                TelegramSender telegramSender,
                                TelegramProperties telegramProperties,
                                EmailSender emailSender,
                                EmailProperties emailProperties,
                                Clock clock) {
        this.dailyMetricRepository = dailyMetricRepository;
        this.divisionReportRepository = divisionReportRepository;
        this.pharmacyDirectoryRepository = pharmacyDirectoryRepository;
        this.generator = generator;
        this.telegramSender = telegramSender;
        this.telegramProperties = telegramProperties;
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
        this.clock = clock;
    }

    public void generateAndSendWeeklyReport() throws TelegramApiException {
        LocalDate today = LocalDate.now(clock);
        WeekRange currentWeek = WeekRange.containingWeekBefore(today);
        WeekRange previousWeek = currentWeek.previousWeek();

        List<MetricDailyTotal> currentTotals = dailyMetricRepository.findDailyTotals(currentWeek.start(), currentWeek.end());
        List<MetricDailyTotal> previousTotals = dailyMetricRepository.findDailyTotals(previousWeek.start(), previousWeek.end());

        // Лист "Дивизионы" показывает только текущую неделю, без сравнения с прошлой.
        DivisionsReportData divisionsData = new DivisionsReportData(
                pharmacyDirectoryRepository.findDivisionRegistry(),
                divisionReportRepository.findMetricTotalsByDivision(currentWeek.start(), currentWeek.end()),
                divisionReportRepository.findActivityTotalsByDivision(currentWeek.start(), currentWeek.end()));

        byte[] workbook = generator.generate(currentWeek, currentTotals, previousWeek, previousTotals, divisionsData);

        String fileName = "Недельный отчет по ПЛ и маркетплейсам (%s).xlsx".formatted(fileNameRange(currentWeek));
        String caption = "Недельный отчёт: %s - %s".formatted(currentWeek.start(), currentWeek.end());

        log.info("Отправка недельного отчёта: fileName='{}'", fileName);
        telegramSender.sendDocument(telegramProperties.reportChatId(), fileName, workbook, caption);

        // Email - лучшая попытка в дополнение к Telegram: если недоступна почта или список
        // получателей пуст, основной канал (Telegram) уже получил отчёт, поэтому не роняем метод.
        if (!emailProperties.recipients().isEmpty()) {
            try {
                emailSender.sendReport(emailProperties.recipients(), fileName, caption, fileName, workbook);
            } catch (Exception e) {
                // Причина сбоя (авторизация/SSL/таймаут/отклонённый адрес) уже залогирована с
                // деталями внутри EmailSender - здесь только фиксируем, что канал деградировал.
                log.warn("Отчёт на почту не отправлен, но уже ушёл в Telegram");
            }
        }
    }

    /** "13-19.07" в пределах одного месяца (как в эталонном файле), иначе "27.07-02.08". */
    private static String fileNameRange(WeekRange week) {
        if (week.start().getMonth() == week.end().getMonth()) {
            return "%s-%s".formatted(week.start().format(FILE_DAY_FORMAT), week.end().format(FILE_DAY_MONTH_FORMAT));
        }
        return "%s-%s".formatted(week.start().format(FILE_DAY_MONTH_FORMAT), week.end().format(FILE_DAY_MONTH_FORMAT));
    }
}
