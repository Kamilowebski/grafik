package pl.grafikpracy;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ScheduleOverview {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("LLLL yyyy",
            Locale.forLanguageTag("pl-PL"));

    private enum Period {
        WEEK("Tydzień"), MONTH("Miesiąc"), YEAR("Rok");

        final String label;

        Period(String label) {
            this.label = label;
        }
    }

    private final MainActivity activity;
    private final Ui ui;
    private LocalDate date;
    private Period period = Period.WEEK;
    private TextView range;
    private LinearLayout body;

    ScheduleOverview(MainActivity activity, Ui ui, LocalDate date) {
        this.activity = activity;
        this.ui = ui;
        this.date = date;
    }

    void show() {
        LinearLayout page = ui.column();
        page.setPadding(ui.dp(18), ui.dp(6), ui.dp(18), ui.dp(12));
        RadioGroup periods = new RadioGroup(activity);
        periods.setOrientation(RadioGroup.HORIZONTAL);
        for (Period item : Period.values()) {
            RadioButton button = new RadioButton(activity);
            button.setId(View.generateViewId());
            button.setText(item.label);
            button.setTextSize(13);
            button.setMinHeight(ui.dp(48));
            button.setOnClickListener(view -> {
                period = item;
                refresh();
            });
            periods.addView(button, new RadioGroup.LayoutParams(0, -2, 1));
            if (item == period) {
                periods.check(button.getId());
            }
        }
        page.addView(periods);
        LinearLayout navigation = ui.row();
        TextView previous = ui.button("‹", false, () -> move(-1));
        previous.setContentDescription("Poprzedni okres");
        navigation.addView(previous, new LinearLayout.LayoutParams(ui.dp(48), -2));
        range = ui.text("", 16, Ui.INK, true);
        range.setGravity(Gravity.CENTER);
        range.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        navigation.addView(range, ui.weight());
        TextView next = ui.button("›", false, () -> move(1));
        next.setContentDescription("Następny okres");
        navigation.addView(next, new LinearLayout.LayoutParams(ui.dp(48), -2));
        page.addView(navigation);

        body = ui.column();
        page.addView(body);
        refresh();
        new AlertDialog.Builder(activity).setTitle("Godziny i urlop")
                .setView(activity.dialogScroll(page)).setPositiveButton("Zamknij", null).show();
    }

    private void move(int direction) {
        switch (period) {
            case WEEK: date = date.plusWeeks(direction); break;
            case MONTH: date = date.plusMonths(direction); break;
            case YEAR: date = date.plusYears(direction); break;
        }
        refresh();
    }

    private void refresh() {
        LocalDate start;
        LocalDate end;
        switch (period) {
            case YEAR:
                start = date.withDayOfYear(1);
                end = start.plusYears(1);
                range.setText(String.valueOf(date.getYear()));
                break;
            case MONTH:
                start = date.withDayOfMonth(1);
                end = start.plusMonths(1);
                range.setText(start.format(MONTH));
                break;
            default:
                start = MainActivity.monday(date);
                end = start.plusWeeks(1);
                range.setText(MainActivity.weekLabel(start) + "\n" + start.format(DATE)
                        + " – " + end.minusDays(1).format(DATE));
                break;
        }

        List<Shift> shifts = Store.shifts(activity);
        Map<LocalDate, DayPlan> days = Store.days(activity);
        ScheduleTotals totals = ScheduleTotals.forPeriod(shifts, days, start, end);
        body.removeAllViews();
        ui.space(body, 14);
        LinearLayout metrics = ui.column();
        metrics.setBackground(ui.shape(Ui.TINT, 16, 0));
        metrics.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        metrics.addView(ui.text("Godziny pracy w grafiku", 14, Ui.SECONDARY, true));
        metrics.addView(ui.text(ShiftHours.durationLabel(totals.minutes), 28, Ui.ACCENT, true));
        metrics.addView(ui.text(MainActivity.shiftCount(totals.shifts) + "  ·  " + totals.workDays
                + (totals.workDays == 1 ? " dzień pracy" : " dni pracy"), 14, Ui.SECONDARY, false));
        body.addView(metrics);
        ui.space(body, 8);
        addRow("Noce z dyżurem", totals.dutyNights);
        addRow("Dni urlopu", totals.days(DayPlan.Status.VACATION));
        addRow("Dni L4", totals.days(DayPlan.Status.SICK));
        addRow("Dni wolne ustawowo", totals.days(DayPlan.Status.HOLIDAY));
        addRow("Pozostałe oznaczone dni wolne", totals.days(DayPlan.Status.FREE));

        int year = date.getYear();
        LocalDate yearStart = date.withDayOfYear(1);
        int used = ScheduleTotals.forPeriod(shifts, days, yearStart, yearStart.plusYears(1))
                .days(DayPlan.Status.VACATION);
        int allowance = Store.vacationAllowance(activity, year);
        ui.space(body, 14);
        body.addView(ui.text("Pula urlopu na " + year, 18, Ui.INK, true));
        addRow("Zaplanowane dni urlopu", used);
        if (allowance >= 0) {
            addRow("Roczna pula (dni)", allowance);
            addRow(used > allowance ? "Dni ponad pulę" : "Pozostałe dni urlopu", Math.abs(allowance - used));
        }
        ui.space(body, 6);
        body.addView(ui.button("Ustaw pulę urlopu", false, () -> editAllowance(year, allowance)));
    }

    private void addRow(String label, int value) {
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(34));
        row.addView(ui.text(label, 14, Ui.INK, false), ui.weight());
        row.addView(ui.text(String.valueOf(value), 15, Ui.ACCENT, true));
        body.addView(row);
    }

    private void editAllowance(int year, int allowance) {
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(10));
        content.addView(ui.text("Roczna pula urlopu w dniach", 14, Ui.SECONDARY, true));
        EditText field = activity.minutesField(allowance);
        if (allowance < 0) {
            field.setText("");
        }
        content.addView(field);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Urlop " + year)
                .setView(content).setNegativeButton("Anuluj", null).setPositiveButton("Zapisz", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            int value;
            try {
                value = Integer.parseInt(field.getText().toString().trim());
            } catch (NumberFormatException exception) {
                value = -1;
            }
            if (value < 0 || value > 366) {
                field.setError("Wpisz od 0 do 366 dni");
                return;
            }
            if (!Store.saveVacationAllowance(activity, year, value)) {
                Toast.makeText(activity, "Nie udało się zapisać puli urlopu.", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            refresh();
        });
    }
}
