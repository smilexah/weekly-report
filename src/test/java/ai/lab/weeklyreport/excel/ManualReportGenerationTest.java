package ai.lab.weeklyreport.excel;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import ai.lab.weeklyreport.metric.MetricDailyTotal;
import ai.lab.weeklyreport.metric.PharmacyDirectoryEntry;
import ai.lab.weeklyreport.repository.DailyMetricRepository;
import ai.lab.weeklyreport.repository.DivisionReportRepository;
import ai.lab.weeklyreport.repository.PharmacyDirectoryRepository;

/** Временный скрипт: перезагружает divisions.xlsx и генерирует реальный недельный отчёт для ручной проверки. */
class ManualReportGenerationTest {

    @Test
    void reloadDivisionsAndGenerateRealReport() throws IOException {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://localhost:6868/weeklyreport");
        dataSource.setUser("weeklyreport");
        dataSource.setPassword("weeklyreport");
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        DailyMetricRepository dailyMetricRepository = new DailyMetricRepository(jdbcTemplate);
        DivisionReportRepository divisionReportRepository = new DivisionReportRepository(jdbcTemplate);
        PharmacyDirectoryRepository pharmacyDirectoryRepository = new PharmacyDirectoryRepository(jdbcTemplate);
        WeeklyReportGenerator generator = new WeeklyReportGenerator();

        DivisionsFileParser divisionsFileParser = new DivisionsFileParser();
        try (FileInputStream in = new FileInputStream("data/divisions.xlsx")) {
            List<PharmacyDirectoryEntry> entries = divisionsFileParser.parse(in);
            pharmacyDirectoryRepository.reloadAll(entries);
            System.out.println("RELOADED_DIVISIONS_ENTRIES=" + entries.size());
        }

        WeekRange currentWeek = new WeekRange(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19));
        WeekRange previousWeek = currentWeek.previousWeek();

        List<MetricDailyTotal> currentTotals = dailyMetricRepository.findDailyTotals(currentWeek.start(), currentWeek.end());
        List<MetricDailyTotal> previousTotals = dailyMetricRepository.findDailyTotals(previousWeek.start(), previousWeek.end());

        DivisionsReportData divisionsData = new DivisionsReportData(
                pharmacyDirectoryRepository.findDivisionRegistry(),
                divisionReportRepository.findMetricTotalsByDivision(currentWeek.start(), currentWeek.end()),
                divisionReportRepository.findActivityTotalsByDivision(currentWeek.start(), currentWeek.end()));

        byte[] workbook = generator.generate(currentWeek, currentTotals, previousWeek, previousTotals, divisionsData);

        Path out = Path.of("C:/Users/MEIIRZ~1/AppData/Local/Temp/claude/D--Spring-Java-Work-weeklyreport/66166d34-c4bf-49d5-a51c-e849cb9d160a/scratchpad/weekly_report_2026-07-13_2026-07-19_v7.xlsx");
        Files.write(out, workbook);
        System.out.println("WROTE_REPORT_TO=" + out.toAbsolutePath());
    }
}
