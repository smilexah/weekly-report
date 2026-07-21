package ai.lab.weeklyreport.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import ai.lab.weeklyreport.config.WeeklyReportProperties;
import ai.lab.weeklyreport.excel.DaribarCrosswalkParser;
import ai.lab.weeklyreport.excel.DivisionsFileParser;
import ai.lab.weeklyreport.metric.DaribarCrosswalkEntry;
import ai.lab.weeklyreport.metric.PharmacyDirectoryEntry;
import ai.lab.weeklyreport.repository.DaribarCrosswalkRepository;
import ai.lab.weeklyreport.repository.PharmacyDirectoryRepository;

/**
 * Загружает divisions.xlsx и daribar_crosswalk.xlsx разово при старте приложения (полная
 * перезапись соответствующих таблиц). Если путь не задан или файл не найден - лог-warning,
 * приложение продолжает работать без листа "Дивизионы". Каждый файл обрабатывается независимо:
 * ошибка одного не блокирует загрузку другого и не прерывает старт приложения.
 * <p>
 * {@code TelegramBotInitializer} (long-polling стартер) запускает приём апдейтов раньше, чем
 * выполняются ApplicationRunner'ы (на фазе {@code InitializingBean.afterPropertiesSet}), поэтому
 * теоретически файл из канала-источника может быть обработан до того, как загрузятся эти
 * справочники. Это осознанно не устраняется: monthly_report.xlsx всегда содержит полную историю,
 * поэтому branch_code для всех строк переразрешится при следующей успешно обработанном файле -
 * тот же принцип самовосстановления, что уже описан в CLAUDE.md.
 */
@Component
public class ReferenceDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataLoader.class);

    private final WeeklyReportProperties properties;
    private final DivisionsFileParser divisionsFileParser;
    private final DaribarCrosswalkParser daribarCrosswalkParser;
    private final PharmacyDirectoryRepository pharmacyDirectoryRepository;
    private final DaribarCrosswalkRepository daribarCrosswalkRepository;

    public ReferenceDataLoader(WeeklyReportProperties properties,
                                DivisionsFileParser divisionsFileParser,
                                DaribarCrosswalkParser daribarCrosswalkParser,
                                PharmacyDirectoryRepository pharmacyDirectoryRepository,
                                DaribarCrosswalkRepository daribarCrosswalkRepository) {
        this.properties = properties;
        this.divisionsFileParser = divisionsFileParser;
        this.daribarCrosswalkParser = daribarCrosswalkParser;
        this.pharmacyDirectoryRepository = pharmacyDirectoryRepository;
        this.daribarCrosswalkRepository = daribarCrosswalkRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        loadDivisions();
        loadDaribarCrosswalk();
    }

    private void loadDivisions() {
        String path = properties.divisionsPath();
        if (path == null || path.isBlank()) {
            log.warn("weekly-report.divisions-path не задан - лист 'Дивизионы' будет пустым");
            return;
        }
        try (InputStream in = openIfExists(path, "divisions.xlsx")) {
            if (in == null) {
                return;
            }
            List<PharmacyDirectoryEntry> entries = divisionsFileParser.parse(in);
            pharmacyDirectoryRepository.reloadAll(entries);
            log.info("Справочник дивизионов загружен: {} аптек", entries.size());
        } catch (Exception e) {
            log.error("Не удалось загрузить divisions.xlsx ({})", path, e);
        }
    }

    private void loadDaribarCrosswalk() {
        String path = properties.daribarCrosswalkPath();
        if (path == null || path.isBlank()) {
            log.warn("weekly-report.daribar-crosswalk-path не задан - резолв branch_code по коду Daribar работать не будет");
            return;
        }
        try (InputStream in = openIfExists(path, "daribar_crosswalk.xlsx")) {
            if (in == null) {
                return;
            }
            List<DaribarCrosswalkEntry> entries = daribarCrosswalkParser.parse(in);
            daribarCrosswalkRepository.reloadAll(entries);
            log.info("Сопоставление кодов Daribar загружено: {} строк", entries.size());
        } catch (Exception e) {
            log.error("Не удалось загрузить daribar_crosswalk.xlsx ({})", path, e);
        }
    }

    private InputStream openIfExists(String path, String label) throws java.io.IOException {
        Path file = Path.of(path);
        if (!Files.exists(file)) {
            log.warn("Файл {} не найден по пути {} - лист 'Дивизионы' будет неполным", label, path);
            return null;
        }
        return Files.newInputStream(file);
    }
}
