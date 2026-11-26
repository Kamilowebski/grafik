package pl.grafikpracy;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private static final int ABSENCE = Ui.LEAVE;
    private static final int DUTY = Ui.DUTY;

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
        body.setPadding(ui.dp(16), ui.dp(5), ui.dp(16), ui.dp(16));
        LinearLayout controls = ui.row();
        TextView back = ui.button("‹  Tydzień", false, activity::closeMonthPage);
        back.setContentDescription("Wróć do tygodnia");
        controls.addView(back);
        controls.addView(new View(activity), ui.weight());
        controls.addView(ui.button("Udostępnij", true, () -> {
            activity.closeMonthPage();
            activity.shareSchedule(month.atDay(1), true);
        }));
        body.addView(controls);
        ui.space(body, 10);
        LinearLayout calendar = ui.column();
        calendar.setBackground(ui.shape(Ui.WHITE, 26, 0));
        calendar.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        LinearLayout navigation = ui.row();
        View previous = ui.arrow("Poprzedni miesiąc", true, false, () -> move(-1));
        navigation.addView(previous, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        title = ui.heading("", 23, Ui.INK);
        title.setGravity(Gravity.CENTER);
        navigation.addView(title, ui.weight());
        View next = ui.arrow("Następny miesiąc", false, false, () -> move(1));
        navigation.addView(next, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        calendar.addView(navigation);

        ui.space(calendar, 12);
        LinearLayout weekdays = ui.row();
        for (String label : new String[]{"Tydz.", "Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"}) {
            TextView cell = ui.text(label, 12, Ui.SECONDARY, true);
            cell.setGravity(Gravity.CENTER);
            weekdays.addView(cell, ui.weight());
        }
        calendar.addView(weekdays);
        ui.space(calendar, 7);
        ui.divider(calendar, 8);
        grid = ui.column();
        calendar.addView(grid);
        body.addView(calendar);
        ui.space(body, 12);
        LinearLayout overview = ui.column();
        overview.setBackground(ui.shape(Ui.TINT, 22, 0));
        overview.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        summary = ui.text("", 15, Ui.INK, true);
        overview.addView(summary);

        ui.space(overview, 8);
        LinearLayout legend = ui.row();
        addLegend(legend, "Praca", WORK);
        addLegend(legend, "Wolne", ABSENCE);
        addLegend(legend, "Dyżur nocny", DUTY);
        overview.addView(legend);
        body.addView(overview);

        SwipeScrollView page = new SwipeScrollView(activity, this::move);
        page.addView(body, new ScrollView.LayoutParams(-1, -2));
        View.OnLayoutChangeListener fitCalendar =
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        fitRows(page.getHeight() - (body.getHeight() - grid.getHeight()));
        grid.addOnLayoutChangeListener(fitCalendar);
        page.addOnLayoutChangeListener(fitCalendar);
        refresh();
        activity.showMonthPage(page);
    }

    private void fitRows(int availableHeight) {
        int rows = (grid.getChildCount() + 1) / 2;
        if (rows == 0 || availableHeight <= 0) {
            return;
        }
        int height = Math.max(ui.dp(47), (availableHeight - (rows - 1) * ui.dp(1)) / rows);
        for (int index = 0; index < grid.getChildCount(); index += 2) {
            View row = grid.getChildAt(index);
            if (row.getLayoutParams().height != height) {
                row.setLayoutParams(new LinearLayout.LayoutParams(-1, height));
            }
        }
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
        String monthLabel = first.format(MONTH);
        title.setText(monthLabel.substring(0, 1).toUpperCase(PL) + monthLabel.substring(1));
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
            if (rowStart > 0) {
                ui.divider(grid, 7);
            }
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
                row.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, ui.dp(47)));
        }
        ScheduleTotals totals = ScheduleTotals.forPeriod(shifts, plans, first, after);
        summary.setText(ShiftHours.durationLabel(totals.minutes) + " pracy  ·  "
                + (totals.dutyNights == 1 ? "1 noc z dyżurem" : totals.dutyNights + " nocy z dyżurem"));
    }

    private View dayCell(LocalDate date, LocalDate today, Set<LocalDate> workDays,
            Map<LocalDate, DayPlan> plans) {
        DayPlan plan = plans.getOrDefault(date, DayPlan.EMPTY);
        boolean work = workDays.contains(date);
        boolean isToday = date.equals(today);
        FrameLayout cell = new FrameLayout(activity);
        TextView number = ui.text(String.valueOf(date.getDayOfMonth()), 15, isToday ? Ui.ACCENT : Ui.INK, isToday);
        number.setGravity(Gravity.CENTER);
        number.setMinWidth(ui.dp(30));
        number.setPadding(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(3));
        number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (isToday) {
            number.setBackground(ui.shape(Ui.TINT, 16, 0));
        }
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
            number.setCompoundDrawables(null, null, null, indicators);
            number.setCompoundDrawablePadding(ui.dp(2));
        } else {
            number.setPadding(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(11));
        }
        cell.addView(number, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        cell.setContentDescription(date.format(DATE) + (work ? ", praca" : "")
                + (plan.isAbsent() ? ", " + plan.status.label : "") + (plan.nightDuty ? ", dyżur nocny" : ""));
        ui.action(cell, 0, 22, 0, () -> {
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
