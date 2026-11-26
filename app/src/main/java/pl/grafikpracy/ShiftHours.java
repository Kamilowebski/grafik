package pl.grafikpracy;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

final class ShiftHours {
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    static final ShiftHours DEFAULT = new ShiftHours(LocalTime.of(8, 0), 8 * 60);
    final LocalTime start;
    final int minutes;

    ShiftHours(LocalTime start, int minutes) {
        this.start = start;
        this.minutes = minutes;
    }

    LocalTime end() {
        return start.plusMinutes(minutes);
    }

    boolean nextDay() {
        return start.toSecondOfDay() / 60 + minutes >= 24 * 60;
    }

    String range() {
        return start.format(TIME) + " – " + end().format(TIME) + (nextDay() ? " +1" : "");
    }

    static String durationLabel(long minutes) {
        long hours = minutes / 60;
        long remainder = minutes % 60;
        if (hours == 0) {
            return remainder + " min";
        }
        return hours + " godz." + (remainder == 0 ? "" : " " + remainder + " min");
    }

    Shift toShift(LocalDate date, ZoneId zone, int lead, Shift existing) {
        return new Shift(existing == null ? UUID.randomUUID().toString() : existing.id,
                date, start, end(), zone, lead, nextDay());
    }

    static final class Controls {
        private final MainActivity activity;
        private final Ui ui;
        private final TextView startButton;
        private final TextView durationButton;
        private final TextView endButton;
        private final Runnable onChange;
        private ShiftHours value;

        Controls(MainActivity activity, Ui ui, LinearLayout container, ShiftHours initial, Runnable onChange) {
            this.activity = activity;
            this.ui = ui;
            this.value = initial;
            this.onChange = onChange;

            startButton = ui.text("", 17, Ui.ACCENT, true);
            durationButton = ui.text("", 17, Ui.ACCENT, true);
            endButton = ui.text("", 17, Ui.ACCENT, true);
            LinearLayout row = ui.row();
            row.setGravity(Gravity.TOP);
            row.setPadding(ui.dp(4), ui.dp(5), ui.dp(4), ui.dp(5));
            row.setBackground(ui.shape(Ui.BACKGROUND, 20, 0));
            addField(row, "Początek", startButton, () -> timePicker(true));
            ui.gap(row, 3);
            addField(row, "Czas pracy", durationButton, this::durationPicker);
            ui.gap(row, 3);
            addField(row, "Koniec", endButton, () -> timePicker(false));
            container.addView(row);
            render();
        }

        ShiftHours value() {
            return value;
        }

        private void addField(LinearLayout row, String label, TextView button, Runnable action) {
            LinearLayout field = ui.column();
            TextView caption = ui.text(label, 12, Ui.SECONDARY, true);
            caption.setGravity(Gravity.CENTER);
            field.addView(caption);
            button.setGravity(Gravity.CENTER);
            button.setMinimumHeight(ui.dp(48));
            ui.action(button, 0, 16, 0, action);
            button.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
            field.addView(button);
            row.addView(field, ui.weight());
        }

        private void update(ShiftHours hours) {
            value = hours;
            render();
            ui.enter(endButton, 0, 4);
            onChange.run();
        }

        private void render() {
            startButton.setText(value.start.format(TIME));
            startButton.setContentDescription("Początek, " + value.start.format(TIME));
            durationButton.setText(durationLabel(value.minutes));
            durationButton.setContentDescription("Czas pracy, " + durationLabel(value.minutes));
            String end = value.end().format(TIME);
            endButton.setText(value.nextDay() ? end + " +1" : end);
            endButton.setContentDescription("Koniec, " + end + (value.nextDay() ? ", następnego dnia" : ""));
        }

        private void timePicker(boolean pickingStart) {
            LocalTime time = pickingStart ? value.start : value.end();
            TimePickerDialog picker = new TimePickerDialog(activity, (view, hour, minute) -> {
                LocalTime selected = LocalTime.of(hour, minute);
                int interval = Math.floorMod(selected.toSecondOfDay() / 60
                        - value.start.toSecondOfDay() / 60, 1440);
                update(pickingStart ? new ShiftHours(selected, value.minutes)
                        : new ShiftHours(value.start, interval == 0 ? 1440 : interval));
            }, time.getHour(), time.getMinute(), true);
            ui.showPicker(picker);
        }

        private void durationPicker() {
            LinearLayout row = ui.row();
            row.setGravity(Gravity.CENTER);
            NumberPicker hours = picker(24, value.minutes / 60);
            NumberPicker minutes = picker(59, value.minutes % 60);
            addPicker(row, hours, "godziny");
            ui.gap(row, 24);
            addPicker(row, minutes, "minuty");
            AlertDialog dialog = ui.dialog().setTitle("Ile trwa zmiana?")
                    .setView(row).setNegativeButton("Anuluj", null).setPositiveButton("Ustaw", null).create();
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                row.requestFocus();
                hours.clearFocus();
                minutes.clearFocus();
                int total = hours.getValue() * 60 + minutes.getValue();
                if (total < 1 || total > 1440) {
                    android.widget.Toast.makeText(activity, "Wybierz od 1 minuty do 24 godzin.",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                update(new ShiftHours(value.start, total));
                dialog.dismiss();
            });
        }

        private NumberPicker picker(int max, int initial) {
            NumberPicker picker = new NumberPicker(activity);
            picker.setMinValue(0);
            picker.setMaxValue(max);
            picker.setValue(initial);
            return picker;
        }

        private void addPicker(LinearLayout row, View picker, String label) {
            LinearLayout column = ui.column();
            column.setGravity(Gravity.CENTER);
            picker.setContentDescription(label);
            column.addView(picker);
            column.addView(ui.text(label, 13, Ui.SECONDARY, true));
            row.addView(column);
        }
    }
}
