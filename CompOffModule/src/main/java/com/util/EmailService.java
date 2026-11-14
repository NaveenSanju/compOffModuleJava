package com.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Sends one email through the Mailgun HTTP API (POST {base}/v3/{domain}/messages, basic auth api:{key}).
 * The only place that talks to Mailgun; every notification goes through send().
 *
 * Outcome (Result.status):
 *   ACCEPTED        Mailgun answered 200 with a message id: Mailgun queued the message. This is NOT proof that the
 *                   recipient received it (delivery is reported later by Mailgun, outside this application).
 *   NOT_CONFIGURED  a setting is missing or still a placeholder: nothing was sent.
 *   FAILED          Mailgun refused it (bad key, unknown domain, invalid address, ...) or the network call failed.
 * A failed email never throws: the caller's business step has already been saved and is not repeated.
 *
 * EMAIL_DEBUG_LOGGING=True prints the complete email (type, to, from, subject, text, HTML, link) to the console
 * before the send is attempted, then the Mailgun result. The API key, passwords and session ids are never logged.
 */
public final class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class.getName());
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final String LINE = "============================================================";

    public enum Status { ACCEPTED, NOT_CONFIGURED, FAILED }

    /** What happened to one email; messageId is Mailgun's id when ACCEPTED. */
    public static final class Result {
        public final Status status;
        public final String detail;
        public final String messageId;

        Result(Status status, String detail, String messageId) {
            this.status = status;
            this.detail = detail;
            this.messageId = messageId;
        }

        public boolean accepted() { return status == Status.ACCEPTED; }

        @Override
        public String toString() {
            return status == Status.ACCEPTED ? "ACCEPTED by Mailgun (queued, id " + messageId + "); delivery to the recipient is not confirmed here"
                    : status + ": " + detail;
        }
    }

    private EmailService() {
    }

    /** Sends one email. type is a label for logs (e.g. HOLIDAY_INTIMATION_APPROVAL); link is the main application link. */
    public static Result send(String type, String to, String subject, String text, String html, String link) {
        boolean debug = MailConfig.debugLogging();
        String from = MailConfig.fromEmail();
        if (debug) {
            debugLog(type, to, from, subject, text, html, link);
        }
        Result result;
        try {
            result = deliver(to, from, subject, text, html);
        } catch (RuntimeException e) {
            result = new Result(Status.FAILED, "unexpected error: " + e.getClass().getSimpleName(), null);
            LOG.log(Level.SEVERE, "Email " + type + " could not be sent", e);
        }
        if (debug) {
            safePrint("Mailgun Result:\n" + result + "\n" + LINE + "\n");
        }
        if (result.accepted()) {
            LOG.info("Email " + type + " to " + mask(to) + ": " + result);
        } else {
            LOG.warning("Email " + type + " to " + mask(to) + " NOT sent - " + result);
        }
        return result;
    }

    private static Result deliver(String to, String from, String subject, String text, String html) {
        if (to == null || !to.contains("@")) {
            return new Result(Status.FAILED, "no valid recipient address", null);
        }
        String problem = MailConfig.configurationProblem();
        if (!problem.isEmpty()) {
            return new Result(Status.NOT_CONFIGURED, "Mailgun is not configured: " + problem, null);
        }
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("from", from);
        form.put("to", to);
        form.put("subject", subject);
        form.put("text", text);
        form.put("html", html);
        HttpURLConnection c = null;
        try {
            URL url = new URL(MailConfig.apiBaseUrl() + "/v3/" + URLEncoder.encode(MailConfig.domain(), "UTF-8") + "/messages");
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            c.setDoOutput(true);
            c.setRequestProperty("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(("api:" + MailConfig.apiKey()).getBytes(StandardCharsets.UTF_8)));
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] body = encode(form).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body);
            }
            int code = c.getResponseCode();
            String response = read(code < 400 ? c.getInputStream() : c.getErrorStream());
            if (code == 200) {
                String id = jsonField(response, "id");
                return id == null ? new Result(Status.FAILED, "Mailgun answered 200 without a message id", null)
                        : new Result(Status.ACCEPTED, jsonField(response, "message"), id);
            }
            String why = jsonField(response, "message");
            why = why == null ? shorten(response) : why;
            if (code == 401) {
                return new Result(Status.FAILED, "Mailgun refused the API key (HTTP 401). Check MAILGUN_API_KEY and the API region (MAILGUN_API_BASE_URL).", null);
            }
            if (code == 404) {
                return new Result(Status.FAILED, "Mailgun does not know the domain (HTTP 404): " + why + ". Check MAILGUN_DOMAIN and MAILGUN_API_BASE_URL.", null);
            }
            return new Result(Status.FAILED, "Mailgun refused the message (HTTP " + code + "): " + why, null);
        } catch (SocketTimeoutException e) {
            return new Result(Status.FAILED, "Mailgun did not answer in time (timeout)", null);
        } catch (IOException e) {
            return new Result(Status.FAILED, "network error reaching Mailgun: " + e.getClass().getSimpleName() + " " + e.getMessage(), null);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static String encode(Map<String, String> form) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        return sb.toString();
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0 && out.size() < 65536) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String jsonField(String json, String key) {
        try {
            JsonElement e = new JsonParser().parse(json);
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
            }
        } catch (RuntimeException ignored) {
            // not JSON
        }
        return null;
    }

    private static String shorten(String s) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }

    /** p***@company.com: enough to tell recipients apart in normal logs without printing full addresses. */
    static String mask(String email) {
        if (email == null || email.indexOf('@') < 1) {
            return "(none)";
        }
        return email.charAt(0) + "***" + email.substring(email.indexOf('@'));
    }

    private static void debugLog(String type, String to, String from, String subject, String text, String html, String link) {
        safePrint("\n" + LINE + "\nEMAIL DEBUG LOG\n" + LINE + "\n"
                + "Email Type : " + type + "\n"
                + "To         : " + to + "\n"
                + "From       : " + (from == null ? "(MAILGUN_FROM_EMAIL not set)" : from) + "\n"
                + "Subject    : " + subject + "\n\n"
                + "Plain-Text Body:\n" + text + "\n\n"
                + "HTML Body:\n" + html + "\n\n"
                + "Application Link:\n" + (link == null ? "(none)" : link) + "\n");
    }

    /** Console output for EMAIL_DEBUG_LOGGING; a failure to print never affects the email or the business step. */
    private static void safePrint(String s) {
        try {
            System.out.println(s);
        } catch (RuntimeException ignored) {
            // logging must not break the workflow
        }
    }
}
