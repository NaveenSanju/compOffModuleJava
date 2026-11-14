package com.util;

/**
 * The company (Staffs.companyID) the current request works for. Set by AuthInterceptor from the
 * logged-in user (the session's companyID, checked against their Staffs row), and by the approval servlets once per
 * company; always cleared afterwards. Every company-scoped read and write uses it (CompOffConfig.getCompanyId()).
 */
public final class CompanyContext {

    private static final ThreadLocal<Integer> CURRENT = new ThreadLocal<Integer>();

    private CompanyContext() {}

    public static void set(Integer companyId) {
        CURRENT.set(companyId);
    }

    public static Integer get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** The current company; fails when none is set (a company-scoped step must never run for "any" company). */
    public static int require() {
        Integer id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("No company set for this request");
        }
        return id;
    }
}
