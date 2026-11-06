package com.abdussamadcodes.tutorsync.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.PluralsRes;

import com.abdussamadcodes.tutorsync.R;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The ONE place that formats money, dates, months, times and numbers.
 * Output always follows the app language (English or Bangla), never the phone locale.
 *
 * <pre>
 * AppFormat f = AppFormat.of(context);
 * f.money(800000)         // ৳8,000        | ৳৮,০০০
 * f.date(millis)          // 5 Nov 2026    | ৫ নভে ২০২৬
 * f.monthYear(millis)     // Nov 2026      | নভে ২০২৬
 * f.time(960)             // 4:00 PM       | বিকাল ৪:০০
 * f.count(R.plurals.plural_classes, 1)  // 1 class | ১টি ক্লাস
 * </pre>
 *
 * Weekday numbers are Calendar style (1 = Sunday ... 7 = Saturday), as stored in the database.
 */
public final class AppFormat {

    private static volatile AppFormat english;
    private static volatile AppFormat bangla;

    private final boolean isBangla;
    private final Resources res;
    private final String[] monthsShort;
    private final String[] monthsFull;
    private final String[] daysShort;
    private final String[] daysFull;
    private final String[] periods;

    private AppFormat(boolean isBangla, @NonNull Resources res) {
        this.isBangla = isBangla;
        this.res = res;
        this.monthsShort = res.getStringArray(R.array.fmt_months_short);
        this.monthsFull = res.getStringArray(R.array.fmt_months_full);
        this.daysShort = res.getStringArray(R.array.fmt_days_short);
        this.daysFull = res.getStringArray(R.array.fmt_days_full);
        this.periods = res.getStringArray(R.array.fmt_time_periods);
    }

    /** Formatter for the current app language. Cheap: cached per language. Works with any Context. */
    @NonNull
    public static AppFormat of(@NonNull Context context) {
        boolean bn = AppPreferences.LANG_BANGLA.equals(new AppPreferences(context).getLanguageCode());
        AppFormat cached = bn ? bangla : english;
        if (cached != null) return cached;
        synchronized (AppFormat.class) {
            cached = bn ? bangla : english;
            if (cached != null) return cached;
            Context app = context.getApplicationContext();
            Configuration config = new Configuration(app.getResources().getConfiguration());
            config.setLocale(Locale.forLanguageTag(bn ? "bn" : "en"));
            AppFormat created = new AppFormat(bn, app.createConfigurationContext(config).getResources());
            if (bn) bangla = created; else english = created;
            return created;
        }
    }

    public boolean isBangla() {
        return isBangla;
    }

    // ------------------------------------------------------------------ numbers and money

    /** Replaces ASCII digits with Bengali digits in Bangla mode. */
    @NonNull
    public String digits(@NonNull String text) {
        return NumberText.localizeDigits(text, isBangla);
    }

    /** Grouped whole number: 1,234 | ১,২৩৪ (lakh grouping in Bangla). */
    @NonNull
    public String number(long value) {
        return NumberText.number(value, isBangla);
    }

    /** Money from minor units (paisa): ৳8,000 or ৳1,250.50. */
    @NonNull
    public String money(int minor) {
        return NumberText.money(minor, isBangla);
    }

    @NonNull
    public String moneyPerMonth(int minor) {
        return res.getString(R.string.fmt_per_month, money(minor));
    }

    @NonNull
    public String moneyPerClass(int minor) {
        return res.getString(R.string.fmt_per_class, money(minor));
    }

    @NonNull
    public String percent(int value) {
        return NumberText.percent(value, isBangla);
    }

    /** Plural-aware count: count(R.plurals.plural_classes, 1) gives "1 class". */
    @NonNull
    public String count(@PluralsRes int pluralsId, int n) {
        return res.getQuantityString(pluralsId, n, number(n));
    }

    @NonNull
    public String duration(int minutes) {
        return res.getString(R.string.fmt_duration_min, number(minutes));
    }

    // ------------------------------------------------------------------ dates and months

    /** 5 Nov 2026 */
    @NonNull
    public String date(long millis) {
        return date(toDate(millis));
    }

    @NonNull
    public String date(@NonNull LocalDate d) {
        return res.getString(R.string.fmt_date,
                number(d.getDayOfMonth()), monthsShort[d.getMonthValue() - 1], year(d.getYear()));
    }

    /** Thu, 5 Nov 2026 */
    @NonNull
    public String weekdayDate(long millis) {
        LocalDate d = toDate(millis);
        return res.getString(R.string.fmt_weekday_date, weekdayShort(calendarDay(d)), date(d));
    }

    /** 5 Nov 2026, 4:00 PM */
    @NonNull
    public String dateTime(long millis) {
        return res.getString(R.string.fmt_date_time, date(millis), timeOfDay(millis));
    }

    /** Nov 2026 */
    @NonNull
    public String monthYear(long millis) {
        return monthYear(YearMonth.from(toDate(millis)));
    }

    @NonNull
    public String monthYear(@NonNull YearMonth ym) {
        return res.getString(R.string.fmt_month_year, monthsShort[ym.getMonthValue() - 1], year(ym.getYear()));
    }

    /** November 2026 */
    @NonNull
    public String monthYearFull(@NonNull YearMonth ym) {
        return res.getString(R.string.fmt_month_year, monthsFull[ym.getMonthValue() - 1], year(ym.getYear()));
    }

    // ------------------------------------------------------------------ time and weekdays

    /** Clock time from minutes since midnight: 4:00 PM | বিকাল ৪:০০ */
    @NonNull
    public String time(int minutesOfDay) {
        int m = ((minutesOfDay % 1440) + 1440) % 1440;
        int hour = m / 60;
        int hour12 = hour % 12 == 0 ? 12 : hour % 12;
        String clock = digits(hour12 + ":" + (m % 60 < 10 ? "0" : "") + (m % 60));
        return res.getString(R.string.fmt_time, clock, periods[NumberText.periodIndex(hour)]);
    }

    /** Clock time of an epoch-millis instant in the device time zone. */
    @NonNull
    public String timeOfDay(long millis) {
        LocalTime t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime();
        return time(t.getHour() * 60 + t.getMinute());
    }

    /** Mon, Tue ... for a Calendar-style day (1 = Sunday). Empty string if out of range. */
    @NonNull
    public String weekdayShort(int calendarDay) {
        return calendarDay >= 1 && calendarDay <= 7 ? daysShort[calendarDay - 1] : "";
    }

    @NonNull
    public String weekdayFull(int calendarDay) {
        return calendarDay >= 1 && calendarDay <= 7 ? daysFull[calendarDay - 1] : "";
    }

    /** "Tue, Wed" from Calendar-style days, sorted Sunday to Saturday. */
    @NonNull
    public String weekdaysShort(@NonNull Collection<Integer> calendarDays) {
        List<Integer> sorted = new ArrayList<>(calendarDays);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        String sep = res.getString(R.string.fmt_list_separator);
        for (int day : sorted) {
            String name = weekdayShort(day);
            if (name.isEmpty()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(name);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ internals

    @NonNull
    private String year(int year) {
        return digits(Integer.toString(year));
    }

    @NonNull
    private static LocalDate toDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** java.time: Monday = 1 ... Sunday = 7. Calendar: Sunday = 1 ... Saturday = 7. */
    private static int calendarDay(@NonNull LocalDate d) {
        return d.getDayOfWeek().getValue() % 7 + 1;
    }
}