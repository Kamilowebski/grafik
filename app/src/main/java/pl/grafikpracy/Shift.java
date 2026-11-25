package pl.grafikpracy;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class Shift {
    final String id;
    final LocalDate date;
    final LocalTime start;
    final LocalTime end;
    final ZoneId zone;
    final int lead;
    final boolean endNextDay;
    boolean deleted;
    boolean pending;

    Shift(String id, LocalDate date, LocalTime start, LocalTime end, ZoneId zone, int lead) {
        this(id, date, start, end, zone, lead, end.isBefore(start));
    }

    Shift(String id, LocalDate date, LocalTime start, LocalTime end, ZoneId zone, int lead,
            boolean endNextDay) {
        this.id = id;
        this.date = date;
        this.start = start;
        this.end = end;
        this.zone = zone;
        this.lead = lead;
        this.endNextDay = endNextDay;
    }

    ZonedDateTime startsAt() {
        return date.atTime(start).atZone(zone);
    }

    ZonedDateTime endsAt() {
        return endDate().atTime(end).atZone(zone);
    }

    private LocalDate endDate() {
        return endNextDay ? date.plusDays(1) : date;
    }

    int nominalMinutes() {
        return (int) Duration.between(date.atTime(start), endDate().atTime(end)).toMinutes();
    }

    boolean hasValidTimes() {
        return !zone.getRules().getValidOffsets(date.atTime(start)).isEmpty()
                && !zone.getRules().getValidOffsets(endDate().atTime(end)).isEmpty()
                && endsAt().isAfter(startsAt());
    }

    Shift copyTo(LocalDate newDate, String newId) {
        return new Shift(newId, newDate, start, end, zone, lead, endNextDay);
    }

    long alarmAt() {
        return startsAt().minusMinutes(lead).toInstant().toEpochMilli();
    }

    String marker() {
        return "grafikpracy://zmiana/" + id;
    }

    static Shift fromJson(JSONObject json) throws JSONException {
        LocalTime start = LocalTime.parse(json.getString("start"));
        LocalTime end = LocalTime.parse(json.getString("end"));
        Shift shift = new Shift(json.getString("id"), LocalDate.parse(json.getString("date")), start, end,
                ZoneId.of(json.getString("zone")), json.getInt("lead"),
                json.optBoolean("endNextDay", end.isBefore(start)));
        shift.deleted = json.optBoolean("deleted");
        shift.pending = json.optBoolean("pending");
        return shift;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("date", date.toString())
                .put("start", start.toString()).put("end", end.toString())
                .put("zone", zone.getId()).put("lead", lead).put("endNextDay", endNextDay)
                .put("deleted", deleted).put("pending", pending);
    }
}
