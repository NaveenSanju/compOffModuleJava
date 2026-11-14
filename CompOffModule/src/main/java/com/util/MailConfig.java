package com.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Email settings. Each value is read from the process environment first, then from WEB-INF/mailgun.env
 * (KEY=VALUE lines; that file is git-ignored and holds the real values on App Engine, where the only other place for
 * environment variables, appengine-web.xml, is committed). See db/README.md, "Email (Mailgun)".
 *   MAILGUN_API_KEY, MAILGUN_DOMAIN, MAILGUN_FROM_EMAIL  required to send
 *   MAILGUN_API_BASE_URL   default https://api.mailgun.net (EU accounts: https://api.eu.mailgun.net)
 *   EMAIL_DEBUG_LOGGING    True: print every generated email (full text and HTML) to the console before sending
 *   APP_BASE_URL           address of the application for links in emails, e.g. https://your-app.appspot.com
 * Registered as a ServletContextListener (web.xml) so the file is read once when the application starts.
 */
public class MailConfig implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(MailConfig.class.getName());
    private static final String FILE = "/WEB-INF/mailgun.env";
    public static final String DEFAULT_API_BASE_URL = "https://api.mailgun.net";

    private static volatile Map<String, String> fileValues = new HashMap<String, String>();

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try (InputStream in = event.getServletContext().getResourceAsStream(FILE)) {
            if (in != null) {
                fileValues = parse(in);
                LOG.info("Email settings read from " + FILE + " (" + fileValues.size() + " value(s))");
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read " + FILE, e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        // nothing to release
    }

    /** KEY=VALUE lines; blank lines and # comments ignored; surrounding quotes removed. */
    static Map<String, String> parse(InputStream in) throws IOException {
        Map<String, String> out = new HashMap<String, String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            int eq = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || eq <= 0) {
                continue;
            }
            String v = line.substring(eq + 1).trim();
            if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
                v = v.substring(1, v.length() - 1);
            }
            out.put(line.substring(0, eq).trim(), v);
        }
        return out;
    }

    /** The setting, trimmed; null when it is not set anywhere. */
    public static String get(String key) {
        String v = System.getenv(key);
        if (v == null || v.trim().isEmpty()) {
            v = fileValues.get(key);
        }
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    /** A value copied from the example file ("your_..._here", "your_sample...") is not a real setting. */
    public static boolean isPlaceholder(String v) {
        return v == null || v.toLowerCase().startsWith("your_") || v.toLowerCase().contains("_here");
    }

    public static String apiKey() { return get("MAILGUN_API_KEY"); }
    public static String domain() { return get("MAILGUN_DOMAIN"); }
    public static String fromEmail() { return get("MAILGUN_FROM_EMAIL"); }

    public static String apiBaseUrl() {
        String v = get("MAILGUN_API_BASE_URL");
        String base = v == null || isPlaceholder(v) ? DEFAULT_API_BASE_URL : v;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static boolean debugLogging() {
        String v = get("EMAIL_DEBUG_LOGGING");
        return v != null && (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes"));
    }

    /** APP_BASE_URL without a trailing slash, or null when it is not set. */
    public static String appBaseUrl() {
        String v = get("APP_BASE_URL");
        if (v == null || isPlaceholder(v)) {
            return null;
        }
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    /** What is missing or still a placeholder for sending through Mailgun ("" when everything is set). */
    public static String configurationProblem() {
        StringBuilder sb = new StringBuilder();
        if (isPlaceholder(apiKey())) {
            sb.append(apiKey() == null ? "MAILGUN_API_KEY is not set. " : "MAILGUN_API_KEY is still the placeholder. ");
        }
        if (isPlaceholder(domain())) {
            sb.append(domain() == null ? "MAILGUN_DOMAIN is not set. " : "MAILGUN_DOMAIN is still the placeholder. ");
        }
        String from = fromEmail();
        if (from == null || !from.contains("@")) {
            sb.append("MAILGUN_FROM_EMAIL is not set to an email address. ");
        }
        return sb.toString().trim();
    }
}
