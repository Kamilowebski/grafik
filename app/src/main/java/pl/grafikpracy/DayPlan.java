package pl.grafikpracy;

import org.json.JSONException;
import org.json.JSONObject;

final class DayPlan {
    enum Status {
        NORMAL("Bez oznaczenia"),
        FREE("Dzień wolny"),
        VACATION("Urlop"),
        SICK("L4"),
        HOLIDAY("Wolne ustawowe");

        final String label;

        Status(String label) {
            this.label = label;
        }
    }

    static final DayPlan EMPTY = new DayPlan(Status.NORMAL, false);
    final Status status;
    final boolean nightDuty;

    DayPlan(Status status, boolean nightDuty) {
        this.status = status;
        this.nightDuty = nightDuty;
    }

    boolean isAbsent() {
        return status != Status.NORMAL;
    }

    boolean isEmpty() {
        return !isAbsent() && !nightDuty;
    }

    static DayPlan fromJson(JSONObject json) throws JSONException {
        return new DayPlan(Status.valueOf(json.getString("status")), json.optBoolean("nightDuty"));
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("status", status.name()).put("nightDuty", nightDuty);
    }
}
