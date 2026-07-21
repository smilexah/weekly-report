package ai.lab.weeklyreport.metric;

/** Статическая сводка справочника аптек по дивизиону (pharmacy_directory) - для листа "Дивизионы". */
public record DivisionRegistryRow(String divisionNum, String directorName, int pharmacyCount, String coverage) {
}
