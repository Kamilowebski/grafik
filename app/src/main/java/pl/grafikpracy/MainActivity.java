package pl.grafikpracy;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class MainActivity extends Activity {
    private static final Locale PL = Locale.forLanguageTag("pl-PL");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE", PL);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", PL);
    private static final DateTimeFormatter DAY_NUMBER = DateTimeFormatter.ofPattern("dd.MM", PL);
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", PL);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", PL);
    private static final int CALENDAR_PERMISSION = 1;
    private static final ExecutorService DISK = Executors.newSingleThreadExecutor();

    private Ui ui;
    private LocalDate week;
    private FrameLayout host;
    private LinearLayout root;
    private SideMenu sideMenu;
    private Runnable removeMenuBackCallback;
    private WeekPager pager;
    private View monthPage;
    private Runnable removeMonthBackCallback;
    private TextView calendarStatus;
    private TextView calendarButton;
    private TextView calendarRetry;
    private final Runnable onRingingChanged = () -> render(true);

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        NotificationChannels.register(this);
        week = state == null ? weekFromIntent(getIntent(), monday(LocalDate.now()))
                : LocalDate.parse(state.getString("week"));
        host = new FrameLayout(this);
        host.setBackgroundColor(Ui.BACKGROUND);
        root = ui.column();
        host.addView(root, new FrameLayout.LayoutParams(-1, -1));
        host.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsets.CONSUMED;
            }
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        setContentView(host);
        host.requestApplyInsets();
    }

    @Override
    protected void onStart() {
        super.onStart();
        AlarmService.onRingingChanged = onRingingChanged;
    }

    @Override
    protected void onStop() {
        if (AlarmService.onRingingChanged == onRingingChanged) {
            AlarmService.onRingingChanged = null;
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (monthPage == null) {
            render(true);
        }
        Alarms.restore(this);
        DISK.execute(() -> {
            Reminders.restore(getApplicationContext());
            GoogleCalendar.sync(getApplicationContext(), false);
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("week", week.toString());
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Reminders.OPEN_WEEK.equals(intent.getAction())) {
            week = weekFromIntent(intent, week);
            render(false);
        }
    }

    private static LocalDate weekFromIntent(Intent intent, LocalDate fallback) {
        String date = intent.getStringExtra("date");
        if (!Reminders.OPEN_WEEK.equals(intent.getAction()) || date == null) {
            return fallback;
        }
        try {
            LocalDate opened = LocalDate.parse(date);
            return opened.getYear() >= 1 && opened.getYear() <= 9999 ? monday(opened) : fallback;
        } catch (java.time.format.DateTimeParseException exception) {
            return fallback;
        }
    }

    static LocalDate monday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static int weekNumber(LocalDate date) {
        return date.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    static String weekLabel(LocalDate date) {
        return "Tydzień " + weekNumber(date) + " · " + date.get(WeekFields.ISO.weekBasedYear());
    }

    private void render(boolean preserveScroll) {
        int previousScroll = preserveScroll && pager != null ? currentPage().getScrollY() : 0;
        closeMonthPage();
        if (sideMenu != null && AlarmService.ringing) {
            sideMenu.dismiss();
        }
        root.removeAllViews();
        LocalDate today = LocalDate.now();

        LinearLayout header = ui.row();
        header.setPadding(ui.dp(9), ui.dp(3), ui.dp(12), ui.dp(3));
        ImageView menu = new ImageView(this);
        menu.setImageResource(R.drawable.drawer_menu);
        menu.setContentDescription("Otwórz menu");
        menu.setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11));
        ui.action(menu, Ui.TINT, 13, 0, this::showWeekMenu);
        header.addView(menu, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        ui.gap(header, 10);
        TextView title = ui.text("Grafik pracy", 25, Ui.INK, true);
        title.setLetterSpacing(-0.025f);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, ui.weight());
        TextView todayShortcut = ui.button("Dziś", false, () -> changeWeek(monday(LocalDate.now())));
        todayShortcut.setContentDescription("Wróć do bieżącego tygodnia");
        todayShortcut.setMinWidth(ui.dp(52));
        todayShortcut.setPadding(ui.dp(9), 0, ui.dp(9), 0);
        header.addView(todayShortcut, new LinearLayout.LayoutParams(-2, ui.dp(48)));
        ui.gap(header, 5);
        TextView add = icon("+", "Dodaj zmianę", true,
                () -> edit(monday(today).equals(week) ? today : week, null));
        header.addView(add, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        root.addView(header);

        if (AlarmService.ringing) {
            TextView stop = ui.button("Wyłącz budzik", true,
                    () -> stopService(new Intent(this, AlarmService.class)));
            LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(-1, -2);
            position.setMargins(ui.dp(12), 0, ui.dp(12), ui.dp(6));
            root.addView(stop, position);
        }

        List<Shift> shifts = Store.shifts(this);
        Map<LocalDate, DayPlan> plans = Store.days(this);
        pager = new WeekPager(this, week, start -> buildWeekPage(start, today, shifts, plans),
                selected -> week = selected);
        root.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
        if (previousScroll > 0) {
            WeekPage current = currentPage();
            current.post(() -> current.scrollTo(0, previousScroll));
        }
    }

    private WeekPage buildWeekPage(LocalDate start, LocalDate today, List<Shift> shifts,
            Map<LocalDate, DayPlan> plans) {
        WeekPage scroll = new WeekPage();
        List<Shift> visible = shifts.stream().filter(shift -> !shift.deleted
                && !shift.date.isBefore(start) && shift.date.isBefore(start.plusWeeks(1)))
                .collect(java.util.stream.Collectors.toList());
        LinearLayout page = ui.column();
        page.setPadding(ui.dp(12), ui.dp(2), ui.dp(12), ui.dp(8));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        page.addView(weekCard(start, visible, plans));
        ui.space(page, 8);
        for (int day = 0; day < 7; day++) {
            LocalDate date = start.plusDays(day);
            if (day > 0) {
                ui.space(scroll.days, 4);
            }
            scroll.days.addView(dayCard(date, today, visible, plans.getOrDefault(date, DayPlan.EMPTY)),
                    new LinearLayout.LayoutParams(-1, -2, 1));
        }
        page.addView(scroll.days, new LinearLayout.LayoutParams(-1, -2, 1));
        return scroll;
    }

    private void showWeekMenu() {
        if (sideMenu != null) {
            return;
        }
        SideMenu menu = new SideMenu(this, ui, () -> {
            sideMenu = null;
            root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            if (removeMenuBackCallback != null) {
                removeMenuBackCallback.run();
                removeMenuBackCallback = null;
            }
        });
        menu.add(R.drawable.drawer_month, "Widok miesiąca",
                () -> new MonthCalendar(this, ui, overviewDate(), this::openDay).show());
        menu.add(R.drawable.drawer_hours, "Godziny i urlop",
                () -> new ScheduleOverview(this, ui, overviewDate()).show());
        menu.divider();
        menu.add(R.drawable.drawer_plan, "Zaplanuj tydzień",
                () -> new WeekPlanner(this, ui, week).show(weekRange(week) + " · " + weekLabel(week)));
        menu.add(R.drawable.drawer_mark, "Oznaczenia tygodnia", this::weekActions);
        menu.add(R.drawable.drawer_copy, "Kopiuj poprzedni tydzień", this::copyWeek);
        menu.add(R.drawable.drawer_share, "Udostępnij tydzień", () -> shareSchedule(week, false));
        menu.divider();
        if (!Alarms.allowed(this)) {
            menu.add(R.drawable.drawer_alarm, "Włącz dokładne budziki", this::exactPermission);
        }
        menu.add(R.drawable.drawer_settings, "Ustawienia", this::settings);
        sideMenu = menu;
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        menu.show(host);
        if (Build.VERSION.SDK_INT >= 33) {
            removeMenuBackCallback = registerOverlayBack(menu::dismiss);
        }
    }

    private TextView icon(String symbol, String description, boolean primary, Runnable action) {
        TextView icon = ui.text(symbol, 23, primary ? Ui.WHITE : Ui.ACCENT, true);
        icon.setGravity(Gravity.CENTER);
        icon.setContentDescription(description);
        ui.action(icon, primary ? Ui.ACCENT : Ui.TINT, 12, 0, action);
        return icon;
    }

    private WeekPage currentPage() {
        return (WeekPage) pager.currentPage();
    }

    private final class WeekPage extends ScrollView {
        final LinearLayout days = ui.column();

        WeekPage() {
            super(MainActivity.this);
            setFillViewport(true);
            setVerticalScrollBarEnabled(false);
            setBackgroundColor(Ui.BACKGROUND);
        }
    }

    private LinearLayout weekCard(LocalDate start, List<Shift> shifts, Map<LocalDate, DayPlan> plans) {
        LinearLayout card = ui.column();
        card.setBackground(ui.weekBackground());
        card.setPadding(ui.dp(7), ui.dp(5), ui.dp(7), ui.dp(8));
        LinearLayout navigation = ui.row();

        ImageButton previous = weekArrow(true, () -> changeWeek(start.minusWeeks(1)));
        navigation.addView(previous, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        LinearLayout labels = ui.column();
        labels.setPadding(ui.dp(4), 0, ui.dp(4), 0);
        TextView weekNumber = ui.text(weekLabel(start), 12, Ui.LAVENDER, true);
        weekNumber.setGravity(Gravity.CENTER);
        labels.addView(weekNumber);
        TextView dates = ui.text(weekRange(start), 17, Ui.WHITE, true);
        dates.setGravity(Gravity.CENTER);
        labels.addView(dates);
        navigation.addView(labels, ui.weight());
        ImageButton next = weekArrow(false, () -> changeWeek(start.plusWeeks(1)));
        navigation.addView(next, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        card.addView(navigation);

        ScheduleTotals totals = ScheduleTotals.forPeriod(shifts, plans, start, start.plusWeeks(1));
        String summary = ShiftHours.durationLabel(totals.minutes) + "  ·  " + shiftCount(totals.shifts);
        if (totals.dutyNights > 0) {
            summary += "  ·  Dyżur: " + totals.dutyNights + (totals.dutyNights == 1 ? " noc" : " nocy");
        }
        TextView hours = ui.text(summary, 13, Ui.WHITE, true);
        hours.setGravity(Gravity.CENTER);
        hours.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), 0);
        card.addView(hours);
        Instant now = Instant.now();
        shifts.stream().filter(shift -> shift.startsAt().toInstant().isAfter(now)
                        && !plans.getOrDefault(shift.date, DayPlan.EMPTY).isAbsent())
                .min(Comparator.comparing(shift -> shift.startsAt().toInstant()))
                .ifPresent(shift -> {
                    TextView upcoming = ui.text("Następna zmiana  ·  " + shift.date.format(DAY_NUMBER)
                            + " o " + shift.start.format(TIME), 12, Ui.LAVENDER, false);
                    upcoming.setGravity(Gravity.CENTER);
                    card.addView(upcoming);
                });
        return card;
    }

    private ImageButton weekArrow(boolean previous, Runnable action) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.week_chevron);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setScaleX(previous ? -1 : 1);
        button.setContentDescription(previous ? "Poprzedni tydzień" : "Następny tydzień");
        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF),
                ui.shape(0x14FFFFFF, 12, 0x55FFFFFF), ui.shape(Ui.WHITE, 12, 0));
        button.setBackground(new InsetDrawable(ripple, ui.dp(6)));
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private String weekRange(LocalDate start) {
        LocalDate end = start.plusDays(6);
        if (start.getMonth() == end.getMonth()) {
            return start.getDayOfMonth() + "–" + end.format(DAY_MONTH);
        }
        return start.format(DAY_NUMBER) + "–" + end.format(DAY_NUMBER);
    }

    static String shiftCount(int count) {
        int last = count % 10;
        int lastTwo = count % 100;
        if (count == 1) {
            return "1 zmiana";
        }
        if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) {
            return count + " zmiany";
        }
        return count + " zmian";
    }

    private void changeWeek(LocalDate newWeek) {
        pager.show(newWeek, () -> currentPage().smoothScrollTo(0, 0));
    }

    private LinearLayout dayCard(LocalDate date, LocalDate today, List<Shift> shifts, DayPlan plan) {
        LinearLayout card = ui.column();
        card.setGravity(Gravity.CENTER_VERTICAL);
        boolean isToday = date.equals(today);
        ui.action(card, isToday ? Ui.TINT : Ui.WHITE, 13, isToday ? 0 : Ui.BORDER,
                () -> dayActions(date));
        String description = "Opcje dnia, " + date.format(FULL_DATE);
        if (plan.isAbsent()) {
            description += ", " + plan.status.label;
        }
        if (plan.nightDuty) {
            description += ", dyżur nocny";
        }
        card.setContentDescription(description);
        card.setPadding(ui.dp(9), ui.dp(1), ui.dp(12), ui.dp(1));
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(48));
        TextView number = ui.text(String.valueOf(date.getDayOfMonth()), 17,
                isToday ? Ui.WHITE : Ui.ACCENT, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(ui.shape(isToday ? Ui.ACCENT : Ui.TINT, 12, 0));
        number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(number, new LinearLayout.LayoutParams(ui.dp(34), ui.dp(36)));
        ui.gap(row, 8);
        String day = date.format(DAY);
        LinearLayout dateLabel = ui.column();
        dateLabel.addView(ui.text(day.substring(0, 1).toUpperCase(PL) + day.substring(1), 15, Ui.INK, true));
        if (isToday) {
            dateLabel.addView(ui.text("Dzisiaj", 12, Ui.ACCENT, true));
        }
        if (plan.isAbsent()) {
            dateLabel.addView(ui.text(plan.status.label, 12, 0xFF9E3849, true));
        }
        if (plan.nightDuty) {
            dateLabel.addView(ui.text("Dyżur nocny", 12, 0xFF246579, true));
        }
        row.addView(dateLabel, ui.weight());

        LinearLayout entries = ui.column();
        for (Shift shift : shifts) {
            if (!date.equals(shift.date)) {
                continue;
            }
            entries.addView(shiftButton(date, shift, plan.isAbsent()));
        }
        if (entries.getChildCount() == 0 && plan.isEmpty()) {
            TextView free = ui.text("Wolne", 14, Ui.SECONDARY, false);
            free.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            row.addView(free);
        } else if (entries.getChildCount() != 0) {
            row.addView(entries, new LinearLayout.LayoutParams(-2, -2));
        }

        card.addView(row);
        return card;
    }

    private LinearLayout shiftButton(LocalDate date, Shift shift, boolean paused) {
        LinearLayout button = ui.column();
        button.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        button.setMinimumHeight(ui.dp(48));
        button.setPadding(ui.dp(5), ui.dp(1), ui.dp(3), ui.dp(1));
        String hours = shift.start.format(TIME) + " – " + shift.end.format(TIME)
                + (shift.endNextDay ? " +1" : "");
        button.addView(ui.text(hours, 15, Ui.INK, true));
        java.time.ZonedDateTime alarmTime = shift.startsAt().minusMinutes(shift.lead);
        String alarm = shift.lead < 0 ? "Bez budzika" : "Budzik " + alarmTime.format(TIME);
        if (shift.lead >= 0 && !alarmTime.toLocalDate().equals(date)) {
            alarm += " · " + alarmTime.format(DAY_NUMBER);
        }
        if (paused) {
            alarm = "Wstrzymana zmiana";
        }
        button.addView(ui.text(alarm, 12, Ui.SECONDARY, false));
        button.setContentDescription("Edytuj zmianę, " + date.format(FULL_DATE) + ", " + hours + ", " + alarm);
        ui.action(button, 0, 9, 0, () -> edit(date, shift));
        return button;
    }

    void showMonthPage(View view) {
        for (int index = 0; index < root.getChildCount(); index++) {
            root.getChildAt(index).setVisibility(View.GONE);
        }
        monthPage = view;
        root.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
        if (Build.VERSION.SDK_INT >= 33) {
            removeMonthBackCallback = registerOverlayBack(this::closeMonthPage);
        }
    }

    void closeMonthPage() {
        if (monthPage == null) {
            return;
        }
        root.removeView(monthPage);
        monthPage = null;
        for (int index = 0; index < root.getChildCount(); index++) {
            root.getChildAt(index).setVisibility(View.VISIBLE);
        }
        if (removeMonthBackCallback != null) {
            removeMonthBackCallback.run();
            removeMonthBackCallback = null;
        }
    }

    @android.annotation.TargetApi(33)
    private Runnable registerOverlayBack(Runnable close) {
        android.window.OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
        android.window.OnBackInvokedCallback callback = close::run;
        dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
    }

    @Override
    public void onBackPressed() {
        if (sideMenu != null) {
            sideMenu.dismiss();
        } else if (monthPage != null) {
            closeMonthPage();
        } else {
            super.onBackPressed();
        }
    }

    private LocalDate overviewDate() {
        LocalDate today = LocalDate.now();
        return monday(today).equals(week) ? today : week;
    }

    private void openDay(LocalDate date) {
        pager.show(monday(date), () -> {
            WeekPage current = currentPage();
            View row = current.days.getChildAt((date.getDayOfWeek().getValue() - 1) * 2);
            current.post(() -> current.smoothScrollTo(0, current.days.getTop() + row.getTop()));
        });
    }

    void shareSchedule(LocalDate date, boolean monthly) {
        boolean[] detailedAbsences = {false};
        new AlertDialog.Builder(this).setTitle(monthly ? "Udostępnij miesiąc" : "Udostępnij tydzień")
                .setMultiChoiceItems(new String[]{"Pokaż rodzaje dni wolnych (np. urlop i L4)"},
                        detailedAbsences, (dialog, which, checked) -> detailedAbsences[0] = checked)
                .setNegativeButton("Anuluj", null).setPositiveButton("Udostępnij PDF", (dialog, which) ->
                        DISK.execute(() -> {
                            try {
                                Uri pdf = ScheduleShare.create(getApplicationContext(), date, monthly,
                                        detailedAbsences[0]);
                                runOnUiThread(() -> openShareChooser(pdf));
                            } catch (java.io.IOException | RuntimeException exception) {
                                android.util.Log.e("GrafikPracy", "Nie udało się utworzyć PDF", exception);
                                runOnUiThread(() -> toast("Nie udało się przygotować PDF."));
                            }
                        })).show();
    }

    private void openShareChooser(Uri pdf) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND).setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, pdf)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(android.content.ClipData.newRawUri("Grafik pracy", pdf));
        startActivity(Intent.createChooser(send, "Udostępnij grafik"));
    }

    private void dayActions(LocalDate date) {
        DayPlan plan = Store.days(this).getOrDefault(date, DayPlan.EMPTY);
        new AlertDialog.Builder(this).setTitle(date.format(FULL_DATE))
                .setItems(new String[]{"Dodaj zmianę", "Oznacz dzień",
                        plan.nightDuty ? "Usuń dyżur nocny" : "Dodaj dyżur nocny"}, (dialog, which) -> {
                    if (which == 0) {
                        edit(date, null);
                    } else if (which == 1) {
                        chooseStatus(status -> applyStatus(Collections.singleton(date), status));
                    } else {
                        updateDays(Collections.singleton(date),
                                previous -> new DayPlan(previous.status, !plan.nightDuty));
                    }
                }).show();
    }

    private void weekActions() {
        LocalDate selectedWeek = week;
        Set<LocalDate> dates = new HashSet<>();
        for (int day = 0; day < 7; day++) {
            dates.add(selectedWeek.plusDays(day));
        }
        new AlertDialog.Builder(this).setTitle("Oznaczenia tygodnia")
                .setItems(new String[]{"Dyżur nocny na cały tydzień", "Usuń dyżury z tygodnia",
                        "Oznacz dni wolne w tygodniu"}, (dialog, which) -> {
                    if (which == 2) {
                        chooseStatus(status -> selectDays(selectedWeek, status));
                    } else {
                        updateDays(dates, previous -> new DayPlan(previous.status, which == 0));
                    }
                }).show();
    }

    private void chooseStatus(Consumer<DayPlan.Status> selected) {
        DayPlan.Status[] statuses = DayPlan.Status.values();
        String[] labels = new String[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            labels[i] = statuses[i].label;
        }
        new AlertDialog.Builder(this).setTitle("Oznaczenie dnia")
                .setItems(labels, (dialog, which) -> selected.accept(statuses[which]))
                .setNegativeButton("Anuluj", null).show();
    }

    private void selectDays(LocalDate start, DayPlan.Status status) {
        Set<LocalDate> selected = new HashSet<>();
        String[] labels = new String[7];
        for (int day = 0; day < labels.length; day++) {
            labels[day] = start.plusDays(day).format(FULL_DATE);
        }
        AlertDialog picker = new AlertDialog.Builder(this).setTitle(status.label)
                .setMultiChoiceItems(labels, null, (dialog, which, checked) -> {
                    LocalDate date = start.plusDays(which);
                    if (checked) {
                        selected.add(date);
                    } else {
                        selected.remove(date);
                    }
                }).setNegativeButton("Anuluj", null).setPositiveButton("Zapisz", null).create();
        picker.show();
        picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            if (selected.isEmpty()) {
                toast("Wybierz co najmniej jeden dzień.");
                return;
            }
            picker.dismiss();
            applyStatus(selected, status);
        });
    }

    private void applyStatus(Set<LocalDate> dates, DayPlan.Status status) {
        Runnable save = () -> updateDays(dates, previous -> new DayPlan(status, previous.nightDuty));
        Map<LocalDate, DayPlan> days = Store.days(this);
        boolean hasActiveShifts = status != DayPlan.Status.NORMAL && Store.shifts(this).stream().anyMatch(shift ->
                !shift.deleted && dates.contains(shift.date)
                        && !days.getOrDefault(shift.date, DayPlan.EMPTY).isAbsent());
        if (hasActiveShifts) {
            new AlertDialog.Builder(this).setTitle("Oznaczyć jako „" + status.label + "”?")
                    .setMessage("Zmiany rozpoczynające się w tych dniach zostaną wstrzymane wraz z budzikami, przypomnieniami i powiązanymi wydarzeniami „Praca”.")
                    .setNegativeButton("Anuluj", null)
                    .setPositiveButton("Oznacz", (dialog, which) -> save.run()).show();
        } else {
            save.run();
        }
    }

    private void updateDays(Set<LocalDate> dates, UnaryOperator<DayPlan> editDay) {
        write(() -> {
            Map<LocalDate, DayPlan> days = Store.days(this);
            List<Shift> shifts = Store.shifts(this);
            Set<LocalDate> changedAvailability = new HashSet<>();
            for (LocalDate date : dates) {
                DayPlan previous = days.getOrDefault(date, DayPlan.EMPTY);
                DayPlan updated = editDay.apply(previous);
                if (updated.isEmpty()) {
                    days.remove(date);
                } else {
                    days.put(date, updated);
                }
                if (previous.isAbsent() != updated.isAbsent()) {
                    changedAvailability.add(date);
                }
            }
            List<Shift> affected = new ArrayList<>();
            for (Shift shift : shifts) {
                if (!shift.deleted && changedAvailability.contains(shift.date)) {
                    shift.pending = true;
                    affected.add(shift);
                }
            }
            Store.save(this, shifts, days);
            for (Shift shift : affected) {
                if (days.getOrDefault(shift.date, DayPlan.EMPTY).isAbsent()) {
                    Alarms.cancel(this, shift.id);
                    Reminders.cancel(this, shift.id);
                } else {
                    Alarms.schedule(this, shift);
                    Reminders.schedule(this, shift);
                }
            }
        }, () -> render(true));
    }

    private void edit(LocalDate date, Shift original) {
        new ShiftEditor(this, ui, date, original).show();
    }

    void saveShift(Shift replacement) {
        write(() -> {
            List<Shift> shifts = Store.shifts(this);
            Shift previous = shifts.stream().filter(shift -> shift.id.equals(replacement.id))
                    .findFirst().orElse(null);
            if (previous != null) {
                shifts.remove(previous);
            }
            replacement.pending = true;
            shifts.add(replacement);
            Store.save(this, shifts);
            Alarms.cancel(this, replacement.id);
            Reminders.cancel(this, replacement.id);
            if (!Store.isAbsent(this, replacement.date)) {
                Alarms.schedule(this, replacement);
                Reminders.schedule(this, replacement);
            }
        }, () -> {
            week = monday(replacement.date);
            render(true);
            ui.enter(currentPage().days, 0, 8);
            if (Store.isAbsent(this, replacement.date)) {
                toast("Zapisano zmianę jako wstrzymaną.");
                return;
            }
            long now = System.currentTimeMillis();
            if (replacement.lead >= 0 && replacement.alarmAt() <= now
                    && replacement.startsAt().toInstant().toEpochMilli() > now) {
                toast("Zapisano zmianę. Pora budzika już minęła.");
            }
            if (replacement.lead >= 0 && replacement.alarmAt() > now) {
                if (!Alarms.allowed(this)) {
                    toast("Zapisano zmianę. Włącz dokładne budziki w menu bocznym.");
                } else if (needsNotificationPermission()
                        && !Store.prefs(this).getBoolean("asked_notifications", false)) {
                    showNotificationsPermission();
                }
            }
        });
    }

    private void copyWeek() {
        LocalDate destination = week;
        List<Shift> candidates = new ArrayList<>();
        Map<LocalDate, DayPlan> days = Store.days(this);
        for (Shift source : Store.shifts(this)) {
            if (source.deleted || days.getOrDefault(source.date, DayPlan.EMPTY).isAbsent()
                    || source.date.isBefore(destination.minusWeeks(1))
                    || !source.date.isBefore(destination)) {
                continue;
            }
            Shift copy = source.copyTo(source.date.plusWeeks(1), UUID.randomUUID().toString());
            if (copy.hasValidTimes()) {
                candidates.add(copy);
            }
        }
        if (candidates.isEmpty()) {
            toast("Brak zmian do skopiowania z poprzedniego tygodnia.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Kopiuj poprzedni tydzień")
                .setMessage("Skopiować godziny i ustawienia budzików do pustych dni w tym tygodniu?")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Kopiuj", (dialog, which) -> saveWeek(destination, candidates, true))
                .show();
    }

    void saveWeek(LocalDate destination, List<Shift> candidates, boolean emptyDaysOnly) {
        List<Shift> added = new ArrayList<>();
        write(() -> {
            List<Shift> existing = Store.shifts(this);
            Map<LocalDate, DayPlan> days = Store.days(this);
            Set<LocalDate> occupied = new HashSet<>();
            for (Shift shift : existing) {
                if (!shift.deleted) {
                    occupied.add(shift.date);
                }
            }
            for (Shift candidate : candidates) {
                if (days.getOrDefault(candidate.date, DayPlan.EMPTY).isAbsent()
                        || (emptyDaysOnly && occupied.contains(candidate.date))) {
                    continue;
                }
                boolean duplicate = existing.stream().anyMatch(shift -> !shift.deleted
                        && shift.date.equals(candidate.date) && shift.start.equals(candidate.start)
                        && shift.end.equals(candidate.end) && shift.endNextDay == candidate.endNextDay);
                if (duplicate) {
                    continue;
                }
                candidate.pending = true;
                existing.add(candidate);
                added.add(candidate);
            }
            if (!added.isEmpty()) {
                Store.save(this, existing);
                for (Shift shift : added) {
                    Alarms.schedule(this, shift);
                    Reminders.schedule(this, shift);
                }
            }
        }, () -> {
            if (added.isEmpty()) {
                toast("Nie dodano zmian. Wybrane wpisy lub dni są już zajęte.");
                return;
            }
            week = destination;
            render(false);
            ui.enter(currentPage().days, 0, 12);
            boolean futureAlarm = added.stream().anyMatch(shift -> shift.lead >= 0
                    && shift.alarmAt() > System.currentTimeMillis());
            String result = "Dodano " + shiftCount(added.size()) + ".";
            if (added.size() < candidates.size()) {
                result += " Pominięto " + (candidates.size() - added.size()) + ".";
            }
            if (futureAlarm && !Alarms.allowed(this)) {
                result += " Włącz dokładne budziki w menu bocznym.";
            }
            toast(result);
            if (futureAlarm && Alarms.allowed(this) && needsNotificationPermission()
                    && !Store.prefs(this).getBoolean("asked_notifications", false)) {
                showNotificationsPermission();
            }
        });
    }

    void deleteShift(Shift original) {
        write(() -> {
            List<Shift> shifts = Store.shifts(this);
            for (Shift shift : shifts) {
                if (shift.id.equals(original.id)) {
                    shift.deleted = true;
                    shift.pending = true;
                    break;
                }
            }
            if (Store.calendarId(this) < 0) {
                shifts.removeIf(shift -> shift.deleted);
            }
            Store.save(this, shifts);
            Alarms.cancel(this, original.id);
            Reminders.cancel(this, original.id);
        }, () -> render(true));
    }

    private void write(Runnable change, Runnable onSuccess) {
        DISK.execute(() -> {
            try {
                change.run();
            } catch (RuntimeException exception) {
                android.util.Log.e("GrafikPracy", "Błąd zapisu", exception);
                runOnUiThread(() -> toast("Nie udało się zapisać zmian."));
                return;
            }
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    onSuccess.run();
                }
            });
            GoogleCalendar.sync(getApplicationContext(), false);
        });
    }

    private void settings() {
        EditText lead = minutesField(Store.defaultLead(this));
        LinearLayout fields = ui.column();
        fields.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), ui.dp(12));
        fields.addView(ui.text("Domyślny budzik przed nową zmianą (minuty)", 15, Ui.INK, true));
        fields.addView(lead);
        ui.space(fields, 12);

        Switch reminders = new Switch(this);
        reminders.setText("Powiadomienia o zmianach");
        reminders.setMinHeight(ui.dp(48));
        reminders.setChecked(Reminders.enabled(this));
        fields.addView(reminders);
        LinearLayout reminderTime = ui.column();
        reminderTime.addView(ui.text("Ile minut przed zmianą?", 14, Ui.SECONDARY, true));
        EditText reminderLead = minutesField(Reminders.lead(this));
        reminderTime.addView(reminderLead);
        reminderTime.setVisibility(reminders.isChecked() ? View.VISIBLE : View.GONE);
        reminders.setOnCheckedChangeListener((view, checked) ->
                reminderTime.setVisibility(checked ? View.VISIBLE : View.GONE));
        fields.addView(reminderTime);
        ui.space(fields, 6);
        fields.addView(ui.button("Kategorie powiadomień", false, this::notificationCategories));
        ui.space(fields, 12);

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Ustawienia")
                .setView(dialogScroll(fields)).setNegativeButton("Zamknij", null)
                .setPositiveButton("Zapisz", null).create();
        fields.addView(ui.text("Kalendarz Google", 18, Ui.INK, true));
        ui.space(fields, 5);
        calendarStatus = ui.text("", 14, Ui.SECONDARY, false);
        fields.addView(calendarStatus);
        ui.space(fields, 7);
        calendarButton = ui.button("", false, this::connectCalendar);
        fields.addView(calendarButton);
        ui.space(fields, 6);
        calendarRetry = ui.button("Ponów zapis w kalendarzu", false, () -> DISK.execute(() -> {
            boolean saved = GoogleCalendar.sync(getApplicationContext(), true);
            runOnUiThread(() -> toast(saved ? "Zaktualizowano wydarzenia w kalendarzu telefonu."
                    : "Nie udało się zapisać wszystkich zmian. Sprawdź ustawienia kalendarza."));
        }));
        fields.addView(calendarRetry);
        updateCalendarSettings();

        if (!Alarms.allowed(this)) {
            ui.space(fields, 10);
            fields.addView(ui.button("Włącz dokładne budziki", false, this::exactPermission));
        }
        if (!Reminders.canNotify(this)) {
            ui.space(fields, 8);
            fields.addView(ui.button("Włącz powiadomienia w Androidzie", false, this::requestNotificationAccess));
        }
        if (Build.VERSION.SDK_INT >= 34
                && !getSystemService(NotificationManager.class).canUseFullScreenIntent()) {
            ui.space(fields, 8);
            fields.addView(ui.button("Włącz ekran budzika na blokadzie", false, () ->
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, appUri()))));
        }

        ui.space(fields, 14);
        fields.addView(ui.button("O aplikacji", false, this::about));

        dialog.show();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(ignored -> {
            Integer minutes = readMinutes(lead);
            if (minutes == null) {
                return;
            }
            boolean enabled = reminders.isChecked();
            Integer reminderMinutes = enabled ? readMinutes(reminderLead) : Integer.valueOf(Reminders.lead(this));
            if (reminderMinutes == null) {
                return;
            }
            boolean changed = Reminders.enabled(this) != enabled || Reminders.lead(this) != reminderMinutes;
            Store.prefs(this).edit().putInt("alarm_lead", minutes).putBoolean("reminders_enabled", enabled)
                    .putInt("reminders_lead", reminderMinutes).apply();
            if (changed) {
                DISK.execute(() -> Reminders.reconfigure(getApplicationContext()));
            }
            dialog.dismiss();
            if (enabled && !Reminders.canNotify(this)) {
                requestNotificationAccess();
            }
        });
    }

    private void about() {
        String version;
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long build = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            version = "Wersja " + info.versionName + " (kompilacja " + build + ")";
        } catch (PackageManager.NameNotFoundException exception) {
            version = "Numer wersji niedostępny";
        }
        new AlertDialog.Builder(this).setTitle("Grafik pracy")
                .setMessage(version + "\n\nPlanuj pojedyncze zmiany lub cały tydzień. Aplikacja podpowiada godzinę końca, ustawia budziki i wysyła powiadomienia przed zmianą.\n\nW widoku miesiąca sprawdzisz zmiany, dni wolne i dyżury. W podsumowaniu ustawisz roczną pulę urlopu.\n\nOznaczenia dni są zapisane na telefonie. Zmiany można synchronizować z Kalendarzem Google.")
                .setPositiveButton("Zamknij", null).show();
    }

    private void updateCalendarSettings() {
        if (calendarStatus == null) {
            return;
        }
        boolean connected = Store.calendarId(this) >= 0;
        calendarStatus.setText(connected ? Store.prefs(this).getString("calendar_name", "Kalendarz Google")
                : "Wybierz kalendarz, aby zapisywać w nim zmiany.");
        calendarButton.setText(connected ? "Zmień kalendarz" : "Połącz kalendarz Google");
        calendarRetry.setVisibility(connected && GoogleCalendar.permitted(this) ? View.VISIBLE : View.GONE);
    }

    EditText minutesField(int minutes) {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setSelectAllOnFocus(true);
        field.setSingleLine();
        field.setText(String.valueOf(minutes));
        return field;
    }

    Integer readMinutes(EditText field) {
        try {
            int value = Integer.parseInt(field.getText().toString().trim());
            if (value >= 0 && value <= 1440) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }
        field.setError("Wpisz od 0 do 1440 minut");
        field.requestFocus();
        return null;
    }

    ScrollView dialogScroll(LinearLayout fields) {
        ScrollView container = new ScrollView(this);
        container.addView(fields);
        return container;
    }

    private void connectCalendar() {
        if (!GoogleCalendar.permitted(this)) {
            if (Store.prefs(this).getBoolean("asked_calendar", false)
                    && !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALENDAR)
                    && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri()));
            } else {
                Store.prefs(this).edit().putBoolean("asked_calendar", true).apply();
                requestPermissions(GoogleCalendar.PERMISSIONS, CALENDAR_PERMISSION);
            }
            return;
        }
        chooseCalendar();
    }

    private void chooseCalendar() {
        DISK.execute(() -> {
            try {
                List<GoogleCalendar.Choice> choices = GoogleCalendar.choices(getApplicationContext());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (choices.isEmpty()) {
                        new AlertDialog.Builder(this).setTitle("Brak dostępnego kalendarza Google")
                                .setMessage("Dodaj konto Google w ustawieniach telefonu i włącz synchronizację Kalendarza.")
                                .setPositiveButton("OK", null).show();
                        return;
                    }
                    String[] names = choices.stream().map(choice -> choice.label).toArray(String[]::new);
                    new AlertDialog.Builder(this).setTitle("Wybierz kalendarz Google")
                            .setItems(names, (dialog, index) -> setCalendar(choices.get(index)))
                            .setNegativeButton("Anuluj", null).show();
                });
            } catch (RuntimeException exception) {
                runOnUiThread(() -> toast("Nie udało się odczytać kalendarzy telefonu."));
            }
        });
    }

    private void setCalendar(GoogleCalendar.Choice choice) {
        long current = Store.calendarId(this);
        String question = current >= 0 && current != choice.id
                ? "Zmiany zapisane przez aplikację zostaną przeniesione do tego kalendarza."
                : "Twoje zapisane zmiany pojawią się w tym kalendarzu.";
        new AlertDialog.Builder(this).setTitle(choice.label).setMessage(question)
                .setNegativeButton("Anuluj", null).setPositiveButton("Połącz", (dialog, ignored) ->
                        DISK.execute(() -> {
                            if (!Store.prefs(this).edit().putLong("calendar_id", choice.id)
                                    .putString("calendar_name", choice.label).putBoolean("calendar_refresh", true).commit()) {
                                runOnUiThread(() -> toast("Nie udało się zapisać wyboru kalendarza."));
                                return;
                            }
                            boolean saved = GoogleCalendar.sync(getApplicationContext(), true);
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    updateCalendarSettings();
                                    toast(saved ? "Wybrano kalendarz Google."
                                            : "Wybrano kalendarz. Część zmian czeka na zapis.");
                                }
                            });
                        })).show();
    }

    private boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    private void notificationCategories() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannels.register(this);
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(12));
        AlertDialog categories = new AlertDialog.Builder(this).setTitle("Kategorie powiadomień")
                .setView(dialogScroll(content)).setPositiveButton("Zamknij", null).create();
        addNotificationCategory(content, categories, manager, NotificationChannels.REMINDER,
                "Przypomnienia o zmianach", "Godziny zmiany i opcja Za 15 min.");
        ui.space(content, 8);
        addNotificationCategory(content, categories, manager, NotificationChannels.ALARM,
                "Budzik przed pracą", "Powiadomienie z przyciskiem wyłączenia budzika.");
        categories.show();
    }

    private void addNotificationCategory(LinearLayout content, AlertDialog categories,
            NotificationManager manager, String id, String title, String detail) {
        android.app.NotificationChannel channel = manager.getNotificationChannel(id);
        boolean enabled = manager.areNotificationsEnabled() && channel != null
                && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        LinearLayout card = ui.column();
        card.setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9));
        card.addView(ui.text(title, 16, Ui.INK, true));
        card.addView(ui.text(detail, 13, Ui.SECONDARY, false));
        String status = enabled ? "Włączone w Androidzie" : "Wyłączone w Androidzie";
        TextView statusLabel = ui.text(status, 13, Ui.ACCENT, true);
        statusLabel.setPadding(0, ui.dp(4), 0, 0);
        card.addView(statusLabel);
        card.setContentDescription(title + ", " + detail + " " + status + ", otwórz ustawienia kanału");
        ui.action(card, Ui.TINT, 13, 0, () -> {
            categories.dismiss();
            openSystemNotificationSettings(id);
        });
        content.addView(card);
    }

    private void requestNotificationAccess() {
        if (needsNotificationPermission() && (!Store.prefs(this).getBoolean("asked_notifications", false)
                || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            showNotificationsPermission();
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        openSystemNotificationSettings(manager.areNotificationsEnabled()
                ? NotificationChannels.REMINDER : null);
    }

    private void openSystemNotificationSettings(String channelId) {
        if (channelId != null) {
            Intent channel = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
            if (openSettings(channel)) {
                return;
            }
        }
        Intent notifications = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        if (!openSettings(notifications)
                && !openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri()))) {
            toast("Ustawienia powiadomień otworzysz w ustawieniach telefonu.");
        }
    }

    private boolean openSettings(Intent settings) {
        try {
            startActivity(settings);
            return true;
        } catch (ActivityNotFoundException exception) {
            return false;
        }
    }

    private void showNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            Store.prefs(this).edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }
    }

    private void exactPermission() {
        if (Build.VERSION.SDK_INT >= 31 && !Alarms.allowed(this)) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri()));
        }
    }

    private Uri appUri() {
        return Uri.parse("package:" + getPackageName());
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] result) {
        super.onRequestPermissionsResult(request, permissions, result);
        if (request == 2 && Reminders.canNotify(this)) {
            DISK.execute(() -> Reminders.restore(getApplicationContext()));
        }
        if (request == CALENDAR_PERMISSION && GoogleCalendar.permitted(this)) {
            chooseCalendar();
        }
    }
}
