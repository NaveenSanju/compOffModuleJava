package com.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Builds literals for the SQL strings passed to HibernateService.updateSQLQuery1 / sqlqueryupdate,
 * which take a complete SQL string. Only ids, dates, numbers, fixed status values and system-generated
 * text go through here - user-entered text (reasons, notes) is always saved through entities.
 */
public final class SqlUtil {

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_\\- ]{1,50}");

    private SqlUtil() {}

    /** A fixed ENUM / status value; rejects anything else instead of escaping it. */
    public static String code(String value) {
        if (value == null || !CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("Unexpected value in SQL: " + value);
        }
        return "'" + value + "'";
    }

    public static String date(LocalDate d) {
        return "'" + DateUtil.iso(d) + "'";
    }

    public static String number(Integer n) {
        if (n == null) {
            throw new IllegalArgumentException("Missing id in SQL");
        }
        return String.valueOf(n.intValue());
    }

    public static String number(BigDecimal n) {
        return n.toPlainString();
    }

    /** System-generated text (e.g. 'Comp Off applied for: 12/10/2026'), escaped for MySQL. */
    public static String text(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }
}
