package com.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Approve / Reject link from an approval email opened while not logged in. The Comp Off module sends the user to the
 * host application's session-expired / login page and remembers the link in a short-lived cookie (a cookie, not the
 * session, because the host login may end the old session). After a successful login the host login action calls
 * {@link #take} and, when it returns a page, redirects there instead of its usual home page.
 *
 * <pre>
 *   // host login action, after the session attributes are set:
 *   String back = com.util.ReturnPage.take(ServletActionContext.getRequest(), ServletActionContext.getResponse());
 *   if (back != null) { compOffReturnTo = back; return "compOffReturn"; }   // result: type="redirect" /${compOffReturnTo}
 * </pre>
 */
public final class ReturnPage {

    public static final String COOKIE = "compoffReturnTo";
    private static final int MAX_AGE_SECONDS = 15 * 60;

    /** Only an approval page with ?open=<encrypted id>[&decision=approve|reject][&ap=<encrypted approver id>]. */
    public static final Pattern PAGE = Pattern.compile(
            "(approveHolidayWorking1|approveCompOff1)\\.action\\?open=[A-Za-z0-9]{1,200}(&decision=(approve|reject))?(&ap=[A-Za-z0-9]{1,200})?");

    private ReturnPage() {}

    /** Remembers the page if it is an approval link (anything else is ignored). */
    public static void remember(HttpServletRequest req, HttpServletResponse resp, String page) {
        if (page == null || !PAGE.matcher(page).matches()) {
            return;
        }
        setCookie(req, resp, encode(page), MAX_AGE_SECONDS);
    }

    /**
     * The remembered approval page (e.g. "approveCompOff1.action?open=...&decision=approve"), relative to the
     * application's context path, or null. The cookie is removed, so the page opens once only.
     */
    public static String take(HttpServletRequest req, HttpServletResponse resp) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) {
                setCookie(req, resp, "", 0);
                String page = decode(c.getValue());
                return page != null && PAGE.matcher(page).matches() ? page : null;
            }
        }
        return null;
    }

    /** Removes a remembered page without using it. */
    public static void clear(HttpServletRequest req, HttpServletResponse resp) {
        setCookie(req, resp, "", 0);
    }

    /**
     * Set-Cookie header written by hand (not javax.servlet.http.Cookie): HttpOnly and SameSite work with any servlet
     * API version on the classpath.
     */
    private static void setCookie(HttpServletRequest req, HttpServletResponse resp, String value, int maxAge) {
        String path = req.getContextPath();
        resp.addHeader("Set-Cookie", COOKIE + "=" + value + "; Path=" + (path == null || path.isEmpty() ? "/" : path)
                + "; Max-Age=" + maxAge + "; HttpOnly; SameSite=Lax" + (req.isSecure() ? "; Secure" : ""));
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String decode(String s) {
        try {
            return s == null ? null : URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return null;
        }
    }
}
