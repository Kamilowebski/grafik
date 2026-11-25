package pl.grafikpracy;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class MonthCalendar {
    private static final Locale PL = Locale.forLanguageTag("pl-PL");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("LLLL yyyy", PL);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", PL);
    private static final int WORK = Ui.ACCENT;
    private static final int ABSENCE = 0xFFB44652;
    private static final int DUTY = 0xFF276A82;

    private final MainActivity activity;
    private final Ui ui;
    private final Consumer<LocalDate> select;
    private YearMonth month;
    private TextView title;
    private LinearLayout grid;
    private TextView summary;

    MonthCalendar(MainActivity activity, Ui ui, LocalDate initial, Consumer<LocalDate> select) {
        this.activity = activity;
        this.ui = ui;
        this.month = YearMonth.from(initial);
        this.select = select;
    }

    void show() {
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(12));
        LinearLayout controls = ui.row();
        TextView back = ui.button("‹  Tydzień", false, activity::closeMonthPage);
        back.setContentDescription("Wróć do tygodnia");
        controls.addView(back, ui.weight());
        ui.gap(controls, 8);
        controls.addView(ui.button("Udostępnij", true, () -> {
            activity.closeMonthPage();
            activity.shareSchedule(month.atDay(1), true);
        }), ui.weight());
        body.addView(controls);
        ui.space(body, 10);
        LinearLayout navigation = ui.row();
        TextView previous = ui.button("‹", false, () -> move(-1));
        previous.setContentDescription("Poprzedni miesiąc");
        navigation.addView(previous, new LinearLayout.LayoutParams(ui.dp(48), -2));
        title = ui.text("", 19, Ui.INK, true);
        title.setGravity(Gravity.CENTER);
        navigation.addView(title, ui.weight());
        TextView next = ui.button("›", false, () -> move(1));
        next.setContentDescription("Następny miesiąc");
        navigation.addView(next, new LinearLayout.LayoutParams(ui.dp(48), -2));
        body.addView(navigation);

        ui.space(body, 10);
        LinearLayout weekdays = ui.row();
        for (String label : new String[]{"Tydz.", "Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"}) {
            TextView cell = ui.text(label, 12, Ui.SECONDARY, true);
            cell.setGravity(Gravity.CENTER);
            weekdays.addView(cell, ui.weight());
        }
        body.addView(weekdays);
        ui.space(body, 4);
        grid = ui.column();
        body.addView(grid);
        ui.space(body, 10);
        summary = ui.text("", 15, Ui.INK, true);
        body.addView(summary);

        ui.space(body, 7);
        LinearLayout legend = ui.row();
        addLegend(legend, "Praca", WORK);
        addLegend(legend, "Wolne", ABSENCE);
        addLegend(legend, "Dyżur nocny", DUTY);
        body.addView(legend);

        SwipeScrollView page = new SwipeScrollView(activity, this::move);
        page.addView(body);
        refresh();
        activity.showMonthPage(page);
    }

    private void move(int direction) {
        month = month.plusMonths(direction);
        refresh();
        ui.enter(grid, direction * 12, 0);
    }

    private void refresh() {
        LocalDate first = month.atDay(1);
        LocalDate after = first.plusMonths(1);
        LocalDate today = LocalDate.now();
        title.setText(first.format(MONTH));
        grid.removeAllViews();
        List<Shift> shifts = Store.shifts(activity);
        Map<LocalDate, DayPlan> plans = Store.days(activity);
        Set<LocalDate> workDays = new HashSet<>();
        for (Shift shift : shifts) {
            if (!shift.deleted && YearMonth.from(shift.date).equals(month)
                    && !plans.getOrDefault(shift.date, DayPlan.EMPTY).isAbsent()) {
                workDays.add(shift.date);
            }
        }

        int offset = first.getDayOfWeek().getValue() - 1;
        int cellCount = offset + month.lengthOfMonth();
        for (int rowStart = 0; rowStart < cellCount; rowStart += 7) {
            LinearLayout row = ui.row();
            int weekNumber = MainActivity.weekNumber(first.minusDays(offset).plusDays(rowStart));
            TextView number = ui.text(String.valueOf(weekNumber), 12, Ui.ACCENT, true);
            number.setContentDescription("Tydzień " + weekNumber);
            number.setGravity(Gravity.CENTER);
            row.addView(number, ui.weight());
            for (int column = 0; column < 7; column++) {
                int day = rowStart + column - offset + 1;
                View cell = day < 1 || day > month.lengthOfMonth() ? new View(activity)
                        : dayCell(month.atDay(day), today, workDays, plans);
                row.addView(cell, new LinearLayout.LayoutParams(0, ui.dp(44), 1));
            }
            grid.addView(row);
        }
        ScheduleTotals totals = ScheduleTotals.forPeriod(shifts, plans, first, after);
        summary.setText(ShiftHours.durationLabel(totals.minutes) + " pracy  ·  "
                + (totals.dutyNights == 1 ? "1 noc z dyżurem" : totals.dutyNights + " nocy z dyżurem"));
    }

    private View dayCell(LocalDate date, LocalDate today, Set<LocalDate> workDays,
            Map<LocalDate, DayPlan> plans) {
        DayPlan plan = plans.getOrDefault(date, DayPlan.EMPTY);
        boolean work = workDays.contains(date);
        TextView cell = ui.text(String.valueOf(date.getDayOfMonth()), 14, Ui.INK, date.equals(today));
        cell.setGravity(Gravity.CENTER);
        int[] colors = new int[3];
        int count = 0;
        if (work) {
            colors[count++] = WORK;
        }
        if (plan.isAbsent()) {
            colors[count++] = ABSENCE;
        }
        if (plan.nightDuty) {
            colors[count++] = DUTY;
        }
        if (count > 0) {
            Drawable indicators = new Dots(colors, count);
            indicators.setBounds(0, 0, ui.dp(count * 6), ui.dp(6));
            cell.setCompoundDrawables(null, null, null, indicators);
            cell.setCompoundDrawablePadding(ui.dp(2));
        }
        cell.setContentDescription(date.format(DATE) + (work ? ", praca" : "")
                + (plan.isAbsent() ? ", " + plan.status.label : "") + (plan.nightDuty ? ", dyżur nocny" : ""));
        ui.action(cell, date.equals(today) ? Ui.TINT : Ui.WHITE, 9, 0, () -> {
            activity.closeMonthPage();
            activity.getWindow().getDecorView().postOnAnimation(() -> select.accept(date));
        });
        return cell;
    }

    private static final class Dots extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors;
        private final int count;
        private int alpha = 255;

        Dots(int[] colors, int count) {
            this.colors = colors;
            this.count = count;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float width = (float) bounds.width() / count;
            for (int index = 0; index < count; index++) {
                paint.setColor(colors[index]);
                paint.setAlpha(alpha);
                canvas.drawCircle(bounds.left + (index + .5f) * width, bounds.exactCenterY(),
                        width / 3f, paint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    private void addDot(LinearLayout row, int color) {
        View dot = new View(activity);
        dot.setBackground(ui.shape(color, 3, 0));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(ui.dp(4), ui.dp(4));
        size.setMargins(ui.dp(1), 0, ui.dp(1), 0);
        row.addView(dot, size);
    }

    private void addLegend(LinearLayout legend, String label, int color) {
        addDot(legend, color);
        ui.gap(legend, 3);
        legend.addView(ui.text(label, 11, Ui.SECONDARY, false), ui.weight());
    }
}
