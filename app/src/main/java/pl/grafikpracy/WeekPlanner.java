package pl.grafikpracy;

import android.app.AlertDialog;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WeekPlanner {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, dd.MM",
            Locale.forLanguageTag("pl-PL"));

    private final MainActivity activity;
    private final Ui ui;
    private final LocalDate monday;
    private final CheckBox[] days = new CheckBox[7];
    private final TextView[] previews = new TextView[7];
    private final ShiftHours[] customHours = new ShiftHours[7];

    private ShiftHours.Controls template;
    private TextView summary;
    private TextView error;
    private Switch alarm;
    private EditText lead;

    WeekPlanner(MainActivity activity, Ui ui, LocalDate monday) {
        this.activity = activity;
        this.ui = ui;
        this.monday = monday;
    }

    void show(String weekRange) {
        LinearLayout form = ui.column();
        form.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(12));
        form.setFocusableInTouchMode(true);
        form.addView(ui.text(weekRange, 15, Ui.SECONDARY, true));
        ui.space(form, 12);
        form.addView(ui.text("Wspólne godziny", 19, Ui.INK, true));
        ui.space(form, 5);
        template = new ShiftHours.Controls(activity, ui, form, ShiftHours.DEFAULT, this::refresh);
        ui.space(form, 16);

        LinearLayout presets = ui.row();
        presets.addView(ui.button("Pon.–pt.", false, () -> select(5)), ui.weight());
        ui.gap(presets, 6);
        presets.addView(ui.button("Wszystkie", false, () -> select(7)), ui.weight());
        ui.gap(presets, 6);
        presets.addView(ui.button("Wyczyść", false, () -> select(0)), ui.weight());
        form.addView(presets);
        ui.space(form, 6);
        for (int index = 0; index < days.length; index++) {
            addDay(form, index);
        }

        ui.space(form, 8);
        summary = ui.text("", 15, Ui.ACCENT, true);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(summary);
        ui.space(form, 8);
        alarm = new Switch(activity);
        alarm.setText("Ustaw budziki");
        alarm.setMinHeight(ui.dp(48));
        alarm.setChecked(true);
        form.addView(alarm);
        LinearLayout leadGroup = ui.column();
        leadGroup.addView(ui.text("Ile minut przed każdą zmianą?", 14, Ui.SECONDARY, true));
        lead = activity.minutesField(Store.defaultLead(activity));
        leadGroup.addView(lead);
        form.addView(leadGroup);
        alarm.setOnCheckedChangeListener((view, checked) -> leadGroup.setVisibility(checked ? View.VISIBLE : View.GONE));

        error = ui.text("", 14, 0xFF9E2D26, true);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        form.addView(error);
        refresh();

        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Zaplanuj tydzień")
                .setView(activity.dialogScroll(form)).setNegativeButton("Anuluj", null)
                .setPositiveButton("Zapisz tydzień", null).create();
        dialog.show();
        form.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> save(dialog));
    }

    private void addDay(LinearLayout form, int index) {
        LinearLayout row = ui.row();
        CheckBox checkbox = new CheckBox(activity);
        checkbox.setMinHeight(ui.dp(48));
        checkbox.setText(monday.plusDays(index).format(DATE));
        checkbox.setTextColor(Ui.INK);
        checkbox.setChecked(index < 5);
        days[index] = checkbox;
        row.addView(checkbox, ui.weight());

        TextView preview = ui.button("", false, () -> customize(index));
        preview.setTextSize(13);
        previews[index] = preview;
        row.addView(preview);
        checkbox.setOnClickListener(view -> refresh());
        form.addView(row);
    }

    private void customize(int index) {
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(18), ui.dp(10), ui.dp(18), ui.dp(8));
        ShiftHours.Controls controls = new ShiftHours.Controls(activity, ui, content, hours(index), () -> {});
        new AlertDialog.Builder(activity).setTitle(monday.plusDays(index).format(DATE))
                .setView(content).setNegativeButton("Anuluj", null)
                .setNeutralButton("Wspólne godziny", (dialog, which) -> {
                    customHours[index] = null;
                    refresh();
                })
                .setPositiveButton("Ustaw", (dialog, which) -> {
                    customHours[index] = controls.value();
                    days[index].setChecked(true);
                    refresh();
                }).show();
    }

    private void select(int count) {
        for (int day = 0; day < days.length; day++) {
            days[day].setChecked(day < count);
        }
        refresh();
    }

    private ShiftHours hours(int index) {
        return customHours[index] == null ? template.value() : customHours[index];
    }

    private void refresh() {
        int count = 0;
        int total = 0;
        for (int index = 0; index < days.length; index++) {
            ShiftHours hours = hours(index);
            TextView preview = previews[index];
            preview.setText(hours.range());
            preview.setTextColor(customHours[index] == null ? Ui.SECONDARY : Ui.ACCENT);
            preview.setAlpha(days[index].isChecked() ? 1f : 0.68f);
            preview.setContentDescription("Godziny, " + monday.plusDays(index).format(DATE)
                    + ", " + hours.range() + (customHours[index] == null ? ", wspólne" : ", własne"));
            if (days[index].isChecked()) {
                count++;
                total += hours.minutes;
            }
        }
        summary.setText(MainActivity.shiftCount(count) + "  ·  " + ShiftHours.durationLabel(total));
        error.setVisibility(View.GONE);
    }

    private void save(AlertDialog dialog) {
        Integer alarmLead = alarm.isChecked() ? activity.readMinutes(lead) : Integer.valueOf(-1);
        if (alarmLead == null) {
            return;
        }
        List<Shift> shifts = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        for (int index = 0; index < days.length; index++) {
            if (!days[index].isChecked()) {
                continue;
            }
            LocalDate date = monday.plusDays(index);
            Shift shift = hours(index).toShift(date, zone, alarmLead, null);
            if (!shift.hasValidTimes()) {
                showError("Sprawdź godziny zmiany " + date.format(DATE) + " w dniu zmiany czasu.");
                return;
            }
            shifts.add(shift);
        }
        if (shifts.isEmpty()) {
            showError("Wybierz co najmniej jeden dzień.");
            return;
        }
        activity.saveWeek(monday, shifts, false);
        dialog.dismiss();
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
        error.post(() -> error.requestRectangleOnScreen(
                new android.graphics.Rect(0, 0, error.getWidth(), error.getHeight())));
    }
}
