package com.util;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * JSPs under /contentpages/ are only rendered by Struts actions (a FORWARD, after the login and
 * role checks). A browser request straight to /contentpages/... would skip those checks, so it is
 * answered with 404. Mapped for the REQUEST dispatcher only (see web.xml).
 */
public class ContentPagesFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
        // nothing to configure
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Override
    public void destroy() {
        // nothing to release
    }
}
