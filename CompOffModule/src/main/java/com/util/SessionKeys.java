package com.util;

/**
 * HTTP session attributes. The login attributes are set by the host application's own login (the Comp Off module has
 * no login of its own); the module only reads them. The module's own attributes start with "compoff.".
 */
public final class SessionKeys {

    /** Set by the host application's login: Staffs.staffID of the logged-in user (Integer or text; 0 / missing = not logged in). */
    public static final String STAFF_ID = "staffID";
    /** Set by the host application's login: Staffs.companyID of the logged-in user (Integer or text). */
    public static final String COMPANY_ID = "companyID";
    /** Set by the host application's login: Staffs.userType of the logged-in user (C, S, TL ...): the access level. */
    public static final String USER_TYPE = "userType";

    /** Module message shown once on the next page. */
    public static final String FLASH_MESSAGE = "compoff.flash.message";
    public static final String FLASH_TYPE = "compoff.flash.type";

    private SessionKeys() {}

    /**
     * Number stored by the host login (Integer, Long or text), read the same way as the host application's
     * ProjectUtil.getAmount(String.valueOf(value)): missing, "null", empty or not a number = 0.
     */
    public static int number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String s = String.valueOf(value).trim();
        try {
            return s.isEmpty() || "null".equals(s) ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
