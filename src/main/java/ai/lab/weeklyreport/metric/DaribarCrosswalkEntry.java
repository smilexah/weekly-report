package ai.lab.weeklyreport.metric;

/** Одна строка сопоставления кодов из daribar_crosswalk.xlsx: код аптеки в Daribar -> branch_code. */
public record DaribarCrosswalkEntry(String daribarCode, String branchCode, String pharmacyName, String comment) {
}
