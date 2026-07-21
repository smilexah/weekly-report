package ai.lab.weeklyreport.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import ai.lab.weeklyreport.config.TelegramProperties;
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

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DailyMetricRepository dailyMetricRepository;
    private final DivisionReportRepository divisionReportRepository;
    private final PharmacyDirectoryRepository pharmacyDirectoryRepository;
    private final WeeklyReportGenerator generator;
    private final TelegramSender telegramSender;
    private final TelegramProperties telegramProperties;
    private final Clock clock;

    public WeeklyReportService(DailyMetricRepository dailyMetricRepository,
                                DivisionReportRepository divisionReportRepository,
                                PharmacyDirectoryRepository pharmacyDirectoryRepository,
                                WeeklyReportGenerator generator,
                                TelegramSender telegramSender,
                                TelegramProperties telegramProperties,
                                Clock clock) {
        this.dailyMetricRepository = dailyMetricRepository;
        this.divisionReportRepository = divisionReportRepository;
        this.pharmacyDirectoryRepository = pharmacyDirectoryRepository;
        this.generator = generator;
        this.telegramSender = telegramSender;
        this.telegramProperties = telegramProperties;
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

        String fileName = "weekly_report_%s_%s.xlsx".formatted(
                currentWeek.start().format(FILE_DATE_FORMAT), currentWeek.end().format(FILE_DATE_FORMAT));
        String caption = "Недельный отчёт: %s - %s".formatted(currentWeek.start(), currentWeek.end());

        telegramSender.sendDocument(telegramProperties.reportChatId(), fileName, workbook, caption);
    }
}
