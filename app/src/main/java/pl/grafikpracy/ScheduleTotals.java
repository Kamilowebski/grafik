package pl.grafikpracy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ScheduleTotals {
    final long minutes;
    final int shifts;
    final int workDays;
    final int dutyNights;
    private final EnumMap<DayPlan.Status, Integer> daysByStatus;

    private ScheduleTotals(long minutes, int shifts, int workDays, int dutyNights,
            EnumMap<DayPlan.Status, Integer> daysByStatus) {
        this.minutes = minutes;
        this.shifts = shifts;
        this.workDays = workDays;
        this.dutyNights = dutyNights;
        this.daysByStatus = daysByStatus;
    }

    static ScheduleTotals forPeriod(List<Shift> shifts, Map<LocalDate, DayPlan> days,
            LocalDate start, LocalDate endExclusive) {
        long minutes = 0;
        int shiftCount = 0;
        Set<LocalDate> workDays = new HashSet<>();
        for (Shift shift : shifts) {
            if (!shift.deleted && inPeriod(shift.date, start, endExclusive)
                    && !days.getOrDefault(shift.date, DayPlan.EMPTY).isAbsent()) {
                minutes += Duration.between(shift.startsAt(), shift.endsAt()).toMinutes();
                shiftCount++;
                workDays.add(shift.date);
            }
        }

        int dutyNights = 0;
        EnumMap<DayPlan.Status, Integer> counts = new EnumMap<>(DayPlan.Status.class);
        for (Map.Entry<LocalDate, DayPlan> day : days.entrySet()) {
            if (!inPeriod(day.getKey(), start, endExclusive)) {
                continue;
            }
            DayPlan plan = day.getValue();
            if (plan.isAbsent()) {
                counts.merge(plan.status, 1, Integer::sum);
            }
            if (plan.nightDuty) {
                dutyNights++;
            }
        }
        return new ScheduleTotals(minutes, shiftCount, workDays.size(), dutyNights, counts);
    }

    int days(DayPlan.Status status) {
        return daysByStatus.getOrDefault(status, 0);
    }

    private static boolean inPeriod(LocalDate date, LocalDate start, LocalDate endExclusive) {
        return !date.isBefore(start) && date.isBefore(endExclusive);
    }
}
