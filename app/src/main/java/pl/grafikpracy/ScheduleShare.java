package pl.grafikpracy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class ScheduleShare {
    private static final int WIDTH = 595;
    private static final int HEIGHT = 842;
    private static final int MARGIN = 34;
    private static final int RIGHT = WIDTH - MARGIN;
    private static final int SURFACE = 0xFFF5F6FC;
    private static final int TINT = 0xFFECE8FF;
    private static final int NAVY = 0xFF1F204B;
    private static final int ACCENT = 0xFF4939A8;
    private static final int MUTED = 0xFF505673;
    private static final int LEAVE = 0xFF9E3849;
    private static final int DUTY = 0xFF246579;
    private static final Locale PL = Locale.forLanguageTag("pl-PL");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", PL);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", PL);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("LLLL yyyy", PL);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);

    private final boolean detailedAbsences;
    private final Map<LocalDate, DayPlan> plans;
    private final Map<LocalDate, List<Shift>> byDate = new HashMap<>();
    private final ScheduleTotals totals;
    private final PdfDocument document;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private PdfDocument.Page page;
    private Canvas canvas;
    private int pageNumber;

    private ScheduleShare(Context context, PdfDocument document, LocalDate from, LocalDate to,
            boolean detailedAbsences) {
        this.document = document;
        this.detailedAbsences = detailedAbsences;
        plans = Store.days(context);
        List<Shift> shifts = Store.shifts(context);
        for (Shift shift : shifts) {
            if (!shift.deleted && !shift.date.isBefore(from) && shift.date.isBefore(to)
                    && !plan(shift.date).isAbsent()) {
                byDate.computeIfAbsent(shift.date, ignored -> new ArrayList<>()).add(shift);
            }
        }
        totals = ScheduleTotals.forPeriod(shifts, plans, from, to);
    }

    static Uri create(Context context, LocalDate date, boolean monthly, boolean detailedAbsences)
            throws IOException {
        LocalDate start = monthly ? date.withDayOfMonth(1) : MainActivity.monday(date);
        LocalDate end = monthly ? start.plusMonths(1) : start.plusWeeks(1);
        File folder = new File(context.getCacheDir(), ShareProvider.FOLDER);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Nie można utworzyć folderu grafiku");
        }
        File[] expired = folder.listFiles(file -> file.getName().endsWith(".pdf")
                && System.currentTimeMillis() - file.lastModified() > 7L * 24 * 60 * 60 * 1000);
        if (expired != null) {
            for (File file : expired) {
                file.delete();
            }
        }
        String period = monthly ? "miesiac-" + YearMonth.from(start)
                : "tydzien-" + MainActivity.weekNumber(start) + "-" + start.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
        String name = "grafik-" + period + "-" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";
        File file = new File(folder, name);
        PdfDocument document = new PdfDocument();
        try {
            ScheduleShare renderer = new ScheduleShare(context, document, start, end, detailedAbsences);
            try {
                if (monthly) {
                    renderer.month(start, end);
                } else {
                    renderer.details(start, end, MainActivity.weekLabel(start), true);
                }
            } finally {
                renderer.finishPage();
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                document.writeTo(output);
            }
        } finally {
            document.close();
        }
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".share")
                .appendPath(name).build();
    }

    private DayPlan plan(LocalDate day) {
        return plans.getOrDefault(day, DayPlan.EMPTY);
    }

    private String absence(DayPlan day) {
        return detailedAbsences ? day.status.label : "Wolne";
    }

    private void month(LocalDate start, LocalDate end) {
        String title = capitalize(start.format(MONTH));
        openPage(title, "Grafik miesiąca", true);
        String[] headings = {"Tydz.", "Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"};
        float columnStart = MARGIN + 35;
        float width = (RIGHT - columnStart) / 7;
        float top = 225;
        float rowHeight = 76;
        text(headings[0], MARGIN + 17, top - 10, 10, MUTED, true, Paint.Align.CENTER);
        for (int column = 0; column < 7; column++) {
            text(headings[column + 1], columnStart + (column + .5f) * width, top - 10, 10,
                    MUTED, true, Paint.Align.CENTER);
        }

        LocalDate monday = MainActivity.monday(start);
        boolean overflow = false;
        for (LocalDate row = monday; row.isBefore(end); row = row.plusWeeks(1), top += rowHeight) {
            text(String.valueOf(MainActivity.weekNumber(row)), MARGIN + 17, top + 20, 12,
                    ACCENT, true, Paint.Align.CENTER);
            for (int column = 0; column < 7; column++) {
                LocalDate date = row.plusDays(column);
                float x = columnStart + column * width;
                if (date.isBefore(start) || !date.isBefore(end)) {
                    panel(x + 1, top + 1, x + width - 1, top + rowHeight - 1, 7, 0xFFEFF0F7);
                    continue;
                }
                DayPlan day = plan(date);
                List<Shift> shifts = byDate.getOrDefault(date, Collections.emptyList());
                panel(x + 1, top + 1, x + width - 1, top + rowHeight - 1, 7, Color.WHITE);
                text(String.valueOf(date.getDayOfMonth()), x + 7, top + 16, 12, NAVY, true, Paint.Align.LEFT);
                if (day.isAbsent()) {
                    text(absence(day), x + 7, top + 32, 7.5f, LEAVE, true, Paint.Align.LEFT);
                }
                for (int i = 0; i < Math.min(2, shifts.size()); i++) {
                    text(hours(shifts.get(i), false), x + 7, top + 32 + i * 11, 7.5f, ACCENT,
                            true, Paint.Align.LEFT);
                }
                if (shifts.size() > 2) {
                    overflow = true;
                    text("+" + (shifts.size() - 2) + " w wykazie", x + 7, top + 55, 7, MUTED, false,
                            Paint.Align.LEFT);
                }
                if (day.nightDuty) {
                    text("Dyżur nocny", x + 7, top + 68, 7.5f, DUTY, true, Paint.Align.LEFT);
                }
            }
        }
        if (overflow) {
            text("Pełny wykaz godzin zmian na kolejnych stronach.", MARGIN, top + 20, 10,
                    MUTED, false, Paint.Align.LEFT);
            details(start, end, title + " · wykaz zmian", false);
        }
    }

    private void details(LocalDate start, LocalDate end, String title, boolean includeEmptyDays) {
        String range = start.format(DATE) + " – " + end.minusDays(1).format(DATE);
        openPage(title, range, includeEmptyDays);
        float top = includeEmptyDays ? 211 : 151;
        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            DayPlan day = plan(date);
            List<Shift> shifts = byDate.getOrDefault(date, Collections.emptyList());
            if (!includeEmptyDays && shifts.isEmpty() && day.isEmpty()) {
                continue;
            }
            String label = capitalize(date.format(DAY));
            int count = Math.max(1, shifts.size());
            int offset = 0;
            while (offset < count) {
                if (top + 62 > 782) {
                    openPage(title, range, false);
                    top = 151;
                }
                int available = Math.max(1, (int) ((782 - top - 33) / 19));
                int rows = Math.min(count - offset, available);
                float bottom = top + 31 + rows * 19;
                panel(MARGIN, top, RIGHT, bottom, 11, Color.WHITE);
                text(label, MARGIN + 14, top + 20, 14, NAVY, true, Paint.Align.LEFT);
                if (day.nightDuty) {
                    text("Dyżur nocny", RIGHT - 14, top + 20, 10, DUTY, true, Paint.Align.RIGHT);
                }
                if (shifts.isEmpty()) {
                    text(day.isAbsent() ? absence(day) : "Wolne", MARGIN + 14, top + 39, 11,
                            day.isAbsent() ? LEAVE : MUTED, false, Paint.Align.LEFT);
                } else {
                    for (int i = 0; i < rows; i++) {
                        Shift shift = shifts.get(offset + i);
                        text(hours(shift, true), MARGIN + 14, top + 39 + i * 19, 12, ACCENT,
                                true, Paint.Align.LEFT);
                    }
                }
                offset += rows;
                top = bottom + 8;
            }
        }
    }

    private void openPage(String title, String subtitle, boolean summary) {
        finishPage();
        page = document.startPage(new PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, ++pageNumber).create());
        canvas = page.getCanvas();
        canvas.drawColor(SURFACE);
        paint.setShader(new LinearGradient(MARGIN, 29, RIGHT, 128, NAVY, ACCENT, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(MARGIN, 29, RIGHT, 130, 16, 16, paint);
        paint.setShader(null);
        text("GRAFIK PRACY", MARGIN + 19, 55, 11, TINT, true, Paint.Align.LEFT);
        text(title, MARGIN + 19, 91, 25, Color.WHITE, true, Paint.Align.LEFT);
        text(subtitle, MARGIN + 19, 113, 11, TINT, false, Paint.Align.LEFT);
        if (summary) {
            panel(MARGIN, 143, RIGHT, 195, 12, TINT);
            text("Godziny pracy", MARGIN + 14, 161, 10, MUTED, false, Paint.Align.LEFT);
            text(ShiftHours.durationLabel(totals.minutes), MARGIN + 14, 184, 17, NAVY, true, Paint.Align.LEFT);
            text("Noce z dyżurem", WIDTH / 2f, 161, 10, MUTED, false, Paint.Align.LEFT);
            text(String.valueOf(totals.dutyNights), WIDTH / 2f, 184, 17, NAVY, true, Paint.Align.LEFT);
        }
        text("Grafik pracy", MARGIN, 813, 9, MUTED, true, Paint.Align.LEFT);
        text(String.valueOf(pageNumber), RIGHT, 813, 9, MUTED, false, Paint.Align.RIGHT);
    }

    private void finishPage() {
        if (page != null) {
            document.finishPage(page);
            page = null;
        }
    }

    private void panel(float left, float top, float right, float bottom, float radius, int color) {
        paint.setColor(color);
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
    }

    private void text(String value, float x, float y, float size, int color, boolean medium, Paint.Align align) {
        paint.setColor(color);
        paint.setTypeface(medium ? MEDIUM : NORMAL);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        canvas.drawText(value, x, y, paint);
    }

    private static String hours(Shift shift, boolean spaced) {
        return shift.start.format(ShiftHours.TIME) + (spaced ? " – " : "–") + shift.end.format(ShiftHours.TIME)
                + (shift.endNextDay ? " +1" : "");
    }

    private static String capitalize(String label) {
        return label.substring(0, 1).toUpperCase(PL) + label.substring(1);
    }
}
