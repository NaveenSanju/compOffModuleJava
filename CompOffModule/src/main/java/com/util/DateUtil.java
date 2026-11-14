package com.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Date;
import java.util.Locale;

/** Conversions between java.time.LocalDate (used in logic) and java.util.Date (used by Hibernate 4 entities). */
public final class DateUtil {

    /** Format posted by the date pickers (hidden field). */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    /** Format shown to users and written into Notes, e.g. 01 Oct 2026 (English month names). */
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);

    private DateUtil() {}

    /** Parses yyyy-MM-dd strictly; returns null for blank or invalid input. */
    public static LocalDate parseIso(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), ISO);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Date toDate(LocalDate d) {
        return d == null ? null : java.sql.Date.valueOf(d);
    }

    public static LocalDate toLocalDate(Date d) {
        if (d == null) {
            return null;
        }
        if (d instanceof java.sql.Date) {
            return ((java.sql.Date) d).toLocalDate();
        }
        return new java.sql.Date(d.getTime()).toLocalDate();
    }

    public static String display(LocalDate d) {
        return d == null ? "" : d.format(DISPLAY);
    }

    public static String display(Date d) {
        return display(toLocalDate(d));
    }

    public static String iso(LocalDate d) {
        return d == null ? "" : d.format(ISO);
    }
}
