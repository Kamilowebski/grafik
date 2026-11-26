package pl.grafikpracy;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class ShiftEditor {
    private static final Locale PL = Locale.forLanguageTag("pl-PL");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", PL);

    private final MainActivity activity;
    private final Ui ui;
    private final Shift original;
    private LocalDate date;
    private TextView dateButton;
    private ShiftHours.Controls hours;
    private TextView overnightHint;
    private LinearLayout leadGroup;
    private EditText lead;
    private Switch alarm;
    private TextView error;

    ShiftEditor(MainActivity activity, Ui ui, LocalDate date, Shift original) {
        this.activity = activity;
        this.ui = ui;
        this.original = original;
        this.date = date;
    }

    void show() {
        LinearLayout fields = ui.column();
        fields.setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(16));
        fields.setFocusableInTouchMode(true);
        dateButton = ui.button("", false, this::pickDate);
        fields.addView(ui.text("Dzień zmiany", 14, Ui.SECONDARY, true));
        dateButton.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        dateButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.drawer_month, 0, 0, 0);
        dateButton.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        dateButton.setCompoundDrawablePadding(ui.dp(12));
        ui.space(fields, 4);
        fields.addView(dateButton);
        ui.space(fields, 12);

        ShiftHours initial = original == null ? ShiftHours.DEFAULT
                : new ShiftHours(original.start, original.nominalMinutes());
        hours = new ShiftHours.Controls(activity, ui, fields, initial, this::updateFields);
        overnightHint = ui.text("Koniec zmiany następnego dnia", 13, Ui.SECONDARY, false);
        overnightHint.setPadding(0, ui.dp(6), 0, 0);
        fields.addView(overnightHint);
        ui.space(fields, 14);
        ui.divider(fields, 0);
        ui.space(fields, 5);

        alarm = ui.toggle("Ustaw budzik", original == null || original.lead >= 0);
        fields.addView(alarm);
        leadGroup = ui.column();
        leadGroup.addView(ui.text("Ile minut przed zmianą?", 14, Ui.SECONDARY, true));
        lead = activity.minutesField(original != null && original.lead >= 0
                ? original.lead : Store.defaultLead(activity));
        ui.space(leadGroup, 6);
        lead.setContentDescription("Ile minut przed zmianą?");
        leadGroup.addView(lead);
        fields.addView(leadGroup);
        leadGroup.setVisibility(alarm.isChecked() ? View.VISIBLE : View.GONE);
        alarm.setOnCheckedChangeListener((button, checked) -> leadGroup.setVisibility(checked ? View.VISIBLE : View.GONE));

        error = ui.text("", 14, 0xFF9E2D26, true);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        fields.addView(error);
        updateFields();

        AlertDialog.Builder builder = ui.sheet()
                .setTitle(original == null ? "Dodaj zmianę" : "Edytuj zmianę")
                .setView(activity.dialogScroll(fields)).setNegativeButton("Anuluj", null)
                .setPositiveButton("Zapisz", null);
        if (original != null) {
            builder.setNeutralButton("Usuń", null);
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        fields.requestFocus();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> save(dialog));
        if (original != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(ignored ->
                    ui.dialog().setTitle("Usunąć zmianę?")
                            .setMessage("Budzik i powiązane wydarzenie z tej zmiany też zostaną usunięte.")
                            .setNegativeButton("Anuluj", null).setPositiveButton("Usuń", (confirm, which) -> {
                                activity.deleteShift(original);
                                dialog.dismiss();
                            }).show());
        }
    }

    private void pickDate() {
        DatePickerDialog picker = new DatePickerDialog(activity, (view, year, month, day) -> {
            date = LocalDate.of(year, month + 1, day);
            updateFields();
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        ui.showPicker(picker);
    }

    private void updateFields() {
        dateButton.setText(date.format(DATE));
        overnightHint.setVisibility(hours.value().nextDay() ? View.VISIBLE : View.GONE);
        error.setVisibility(View.GONE);
    }

    private void save(AlertDialog dialog) {
        Integer minutes = alarm.isChecked() ? activity.readMinutes(lead) : Integer.valueOf(-1);
        if (minutes == null) {
            return;
        }
        ZoneId zone = original == null ? ZoneId.systemDefault() : original.zone;
        Shift replacement = hours.value().toShift(date, zone, minutes, original);
        if (!replacement.hasValidTimes()) {
            showError("Sprawdź godziny tej zmiany w dniu zmiany czasu.");
            return;
        }
        activity.saveShift(replacement);
        dialog.dismiss();
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }
}
