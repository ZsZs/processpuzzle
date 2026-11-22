package com.processpuzzle.calendar.entry.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A preset repetition of a recurring entry's template, as a pure function of the template's day. {@code DAILY} repeats
 * every day, {@code WEEKLY} on the weekday of the template and {@code MONTHLY} on its day of the month; a month without
 * that day has no occurrence. The series ends after {@code count} occurrences, on {@code until}, or never when neither
 * is given.
 *
 * <p>No free-text rule is accepted: the presets are all the editor offers, so every series is one of these three.
 */
public record Recurrence(Frequency frequency, Integer count, LocalDate until) {

    /** The most occurrences one series may have. */
    public static final int MAX_COUNT = 500;

    public enum Frequency {
        DAILY, WEEKLY, MONTHLY
    }

    /** Whether {@code day} is an occurrence of this rule for a template on {@code template}, ignoring the bounds. */
    public boolean matches(LocalDate template, LocalDate day) {
        if (day.isBefore(template)) {
            return false;
        }
        return switch (frequency) {
            case DAILY -> true;
            case WEEKLY -> day.getDayOfWeek() == template.getDayOfWeek();
            case MONTHLY -> day.getDayOfMonth() == template.getDayOfMonth();
        };
    }

    /** The last day the series has, or null when it never ends. Counted from the template, so the rule's count is exact. */
    public LocalDate lastDay(LocalDate template) {
        if (until != null) {
            return until;
        }
        if (count == null) {
            return null;
        }
        LocalDate last = template;
        int seen = 0;
        for (LocalDate day = template; seen < count; day = day.plusDays(1)) {
            if (matches(template, day)) {
                seen++;
                last = day;
            }
        }
        return last;
    }

    /** The occurrence days of the series that fall in {@code [first, last]}, in order. */
    public List<LocalDate> daysWithin(LocalDate template, LocalDate first, LocalDate last) {
        LocalDate end = until != null && until.isBefore(last) ? until : last;
        List<LocalDate> days = new ArrayList<>();
        int seen = 0;
        for (LocalDate day = template; !day.isAfter(end); day = day.plusDays(1)) {
            if (!matches(template, day)) {
                continue;
            }
            seen++;
            if (count != null && seen > count) {
                break;
            }
            if (!day.isBefore(first)) {
                days.add(day);
            }
        }
        return days;
    }
}
