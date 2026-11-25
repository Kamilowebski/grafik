package pl.grafikpracy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class Store {
    private Store() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("grafik", Context.MODE_PRIVATE);
    }

    static List<Shift> shifts(Context context) {
        List<Shift> shifts = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(prefs(context).getString("shifts", "[]"));
            for (int i = 0; i < data.length(); i++) {
                shifts.add(Shift.fromJson(data.getJSONObject(i)));
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Nie można odczytać zapisanych zmian", exception);
        }
        shifts.sort(Comparator.comparing((Shift shift) -> shift.date).thenComparing(shift -> shift.start));
        return shifts;
    }

    static Map<LocalDate, DayPlan> days(Context context) {
        Map<LocalDate, DayPlan> days = new HashMap<>();
        try {
            JSONObject data = new JSONObject(prefs(context).getString("day_plans", "{}"));
            Iterator<String> dates = data.keys();
            while (dates.hasNext()) {
                String date = dates.next();
                days.put(LocalDate.parse(date), DayPlan.fromJson(data.getJSONObject(date)));
            }
        } catch (JSONException exception) {
            throw new IllegalStateException("Nie można odczytać oznaczeń dni", exception);
        }
        return days;
    }

    static boolean isAbsent(Context context, LocalDate date) {
        try {
            JSONObject day = new JSONObject(prefs(context).getString("day_plans", "{}"))
                    .optJSONObject(date.toString());
            return day != null && DayPlan.fromJson(day).isAbsent();
        } catch (JSONException exception) {
            throw new IllegalStateException("Nie można odczytać oznaczenia dnia", exception);
        }
    }

    static int vacationAllowance(Context context, int year) {
        return prefs(context).getInt(allowanceKey(year), -1);
    }

    static boolean saveVacationAllowance(Context context, int year, int days) {
        return prefs(context).edit().putInt(allowanceKey(year), days).commit();
    }

    private static String allowanceKey(int year) {
        return "vacation_allowance_" + year;
    }

    static void save(Context context, List<Shift> shifts) {
        save(context, shifts, null);
    }

    static void save(Context context, List<Shift> shifts, Map<LocalDate, DayPlan> days) {
        JSONArray data = new JSONArray();
        SharedPreferences.Editor editor = prefs(context).edit();
        try {
            for (Shift shift : shifts) {
                data.put(shift.toJson());
            }
            if (days != null) {
                JSONObject plans = new JSONObject();
                for (Map.Entry<LocalDate, DayPlan> day : days.entrySet()) {
                    plans.put(day.getKey().toString(), day.getValue().toJson());
                }
                editor.putString("day_plans", plans.toString());
            }
        } catch (JSONException exception) {
            throw new IllegalStateException(exception);
        }
        if (!editor.putString("shifts", data.toString()).commit()) {
            throw new IllegalStateException("Nie udało się zapisać grafiku");
        }
    }

    static long calendarId(Context context) {
        return prefs(context).getLong("calendar_id", -1);
    }

    static int defaultLead(Context context) {
        return prefs(context).getInt("alarm_lead", 60);
    }
}
