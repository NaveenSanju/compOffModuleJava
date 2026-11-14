package com.util;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Reads compoff.properties from the classpath once. */
public final class CompOffConfig {

    private static final Properties PROPS = load();

    private CompOffConfig() {}

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = CompOffConfig.class.getClassLoader().getResourceAsStream("compoff.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
        return p;
    }

    public static List<String> getCssFiles() {
        List<String> files = new ArrayList<String>();
        for (String f : PROPS.getProperty("compoff.css.files", "css/compoff-theme.css,css/compoff.css").split(",")) {
            if (!f.trim().isEmpty()) {
                files.add(f.trim());
            }
        }
        return Collections.unmodifiableList(files);
    }

    public static ZoneId getZone() {
        return ZoneId.of(PROPS.getProperty("compoff.timezone", "Asia/Kolkata"));
    }

    /** "Today" in the configured time zone, not the server's. */
    public static LocalDate today() {
        return LocalDate.now(getZone());
    }

    public static int getTextMaxLength() {
        return Integer.parseInt(PROPS.getProperty("compoff.text.maxlength", "500"));
    }

    /**
     * Access level from the user type code in Staffs.userType (UserTypes.userType), case-insensitive:
     * compoff.usertype.admin (C) = Admin, compoff.usertype.employee (T, S, QC) = Employee,
     * compoff.usertype.approver (TL, PM, MR, HR) = Approver. Any other code, or none, = null (no access).
     */
    public static String accessRole(String userType) {
        if (userType == null || userType.trim().isEmpty()) {
            return null;
        }
        if (listed("compoff.usertype.admin", ADMIN_TYPES, userType)) {
            return com.dao.CompOffConstants.ROLE_ADMIN;
        }
        if (listed("compoff.usertype.employee", EMPLOYEE_TYPES, userType)) {
            return com.dao.CompOffConstants.ROLE_EMPLOYEE;
        }
        if (listed("compoff.usertype.approver", APPROVER_TYPES, userType)) {
            return com.dao.CompOffConstants.ROLE_APPROVER;
        }
        return null;
    }

    private static final String ADMIN_TYPES = "C", EMPLOYEE_TYPES = "T,S,QC", APPROVER_TYPES = "TL,PM,MR,HR";

    /** SQL condition on a Staffs alias for an access level (alias.userType compared with the configured lists). */
    public static String userTypeSql(String alias, String access) {
        String col = "LOWER(TRIM(" + (alias == null ? "" : alias + ".") + "userType))";
        String key = com.dao.CompOffConstants.ROLE_ADMIN.equals(access) ? "compoff.usertype.admin"
                : com.dao.CompOffConstants.ROLE_EMPLOYEE.equals(access) ? "compoff.usertype.employee" : "compoff.usertype.approver";
        String def = com.dao.CompOffConstants.ROLE_ADMIN.equals(access) ? ADMIN_TYPES
                : com.dao.CompOffConstants.ROLE_EMPLOYEE.equals(access) ? EMPLOYEE_TYPES : APPROVER_TYPES;
        return col + " IN (" + inList(key, def) + ")";
    }

    private static boolean listed(String key, String def, String value) {
        for (String n : PROPS.getProperty(key, def).split(",")) {
            if (n.trim().equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String inList(String key, String def) {
        StringBuilder sb = new StringBuilder();
        for (String n : PROPS.getProperty(key, def).split(",")) {
            if (!n.trim().isEmpty()) {
                sb.append(sb.length() > 0 ? ", " : "").append(SqlUtil.text(n.trim().toLowerCase()));
            }
        }
        return sb.length() == 0 ? "''" : sb.toString();
    }

    /** The companyID of the current request: the logged-in user's Staffs.companyID (CompanyContext). */
    public static int getCompanyId() {
        return CompanyContext.require();
    }

    /** SQL: the name of a Staffs row (alias may be null): firstName + ' ' + lastName, as Staffs.getStaffName(). */
    public static String nameSql(String alias) {
        String p = alias == null ? "" : alias + ".";
        return "TRIM(CONCAT_WS(' ', NULLIF(TRIM(" + p + "firstName), ''), NULLIF(TRIM(" + p + "lastName), '')))";
    }

    /** SQL: Staffs.teamID (a text column) as a number, NULL when it is empty or not a number (as Staffs.getTeamID()). */
    public static String teamIdSql(String alias) {
        String c = (alias == null ? "" : alias + ".") + "teamID";
        return "CASE WHEN TRIM(" + c + ") REGEXP '^[0-9]{1,9}$' THEN CAST(TRIM(" + c + ") AS UNSIGNED) END";
    }

    /** SQL condition: the staffID expression is a staff member of the current company (Staffs.companyID). */
    public static String companyStaffSql(String staffIdExpr) {
        return staffIdExpr + " IN (SELECT cs.staffID FROM Staffs cs WHERE cs.companyID = " + SqlUtil.number(getCompanyId()) + ")";
    }

    /** Working hours of one Full day (compoff.hours.fullday, default 8). */
    public static int getFullDayHours() {
        return Integer.parseInt(PROPS.getProperty("compoff.hours.fullday", "8").trim());
    }

    public static int getApplyMaxDays() {
        return Integer.parseInt(PROPS.getProperty("compoff.apply.maxdays", "31"));
    }
}
