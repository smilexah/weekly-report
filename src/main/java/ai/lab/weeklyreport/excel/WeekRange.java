package ai.lab.weeklyreport.excel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Диапазон недели пн-вс (обе границы включительно). */
public record WeekRange(LocalDate start, LocalDate end) {

    public static WeekRange containingWeekBefore(LocalDate reference) {
        LocalDate lastSunday = reference.minusDays(reference.getDayOfWeek().getValue());
        LocalDate monday = lastSunday.minusDays(6);
        return new WeekRange(monday, lastSunday);
    }

    public WeekRange previousWeek() {
        return new WeekRange(start.minusWeeks(1), end.minusWeeks(1));
    }

    public List<LocalDate> days() {
        List<LocalDate> days = new ArrayList<>(7);
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            days.add(day);
        }
        return days;
    }
}
