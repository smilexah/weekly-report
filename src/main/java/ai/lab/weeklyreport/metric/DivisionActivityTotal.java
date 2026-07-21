package ai.lab.weeklyreport.metric;

/**
 * Кол-во аптек с активностью за неделю в разрезе дивизиона - агрегат для листа "Дивизионы".
 * {@code divisionNum == null} - строка "Без дивизиона / склад".
 * {@code pharmacyCount} - кол-во аптек, замеченных в daily_metrics за неделю (используется только
 * как знаменатель для строки "Без дивизиона / склад" - у настоящих дивизионов знаменатель берётся
 * из статического справочника {@link DivisionRegistryRow#pharmacyCount()}).
 */
public record DivisionActivityTotal(String divisionNum, int pharmacyCount, int activePharmacyCount) {
}
