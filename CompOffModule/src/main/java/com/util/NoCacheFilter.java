package com.util;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Stops the browser from keeping copies of the application's pages (login, logout and every action).
 * Without these headers, Back / Forward showed a stored copy without asking the server: the login page after
 * login, protected pages after logout or session expiry. With no-store the browser asks the server each time,
 * so the login and role checks (CompOffAction.access, with the host application's session) decide what is shown.
 *
 * Static files (/asserts/*, /js/*) are not affected and stay cacheable. Mapped to /* before the Struts filter
 * (see web.xml), so every new page is covered automatically.
 */
public class NoCacheFilter implements Filter {

    /** Static resources: no user data, safe to cache. */
    private static final String[] STATIC_PREFIXES = { "/asserts/", "/js/" };

    @Override
    public void init(FilterConfig filterConfig) {
        // nothing to set up
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        if (!isStatic(req.getRequestURI().substring(req.getContextPath().length()))) {
            HttpServletResponse res = (HttpServletResponse) response;
            res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private, max-age=0");
            res.setHeader("Pragma", "no-cache");        // HTTP/1.0 caches
            res.setDateHeader("Expires", 0);            // proxies that ignore Cache-Control
        }
        chain.doFilter(request, response);
    }

    private static boolean isStatic(String path) {
        for (String prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        // nothing to release
    }
}
