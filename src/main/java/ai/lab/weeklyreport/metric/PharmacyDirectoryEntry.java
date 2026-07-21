package ai.lab.weeklyreport.metric;

/** Одна строка справочника аптек из divisions.xlsx: аптека -> дивизион -> директор. */
public record PharmacyDirectoryEntry(String branchCode, String address, String city, String divisionNum, String directorName) {
}
