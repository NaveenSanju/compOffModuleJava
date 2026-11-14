package com.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringEscapeUtils;

import com.dao.CompOffBalanceDetails;
import com.dao.CompensatoryOffRequest;
import com.dao.HolidayRequests;
import com.dao.Staffs;
import com.dao.Teams;
import com.util.DateUtil;
import com.util.EmailService;
import com.util.IdCrypt;
import com.util.MailConfig;

/**
 * Builds the workflow emails and sends them through EmailService.
 *
 * A business step adds its notification here only after its database change succeeded (the conditional update
 * returned the expected count), so a repeated click or a re-run of the approval servlet never notifies twice.
 * Notifications are collected and sent by flush(): ONE email per recipient and type per click / servlet run,
 * listing all the requests concerned. Email failures are logged by EmailService and never undo, block or repeat the
 * business step (agreed failure policy); a failed email is not re-sent automatically.
 *
 * Links point to the application's own pages (APP_BASE_URL + /<action>.action), which still require login and the right
 * role; request ids in links are encrypted (IdCrypt). Approve / Reject in an approval email open the approval page with
 * that request ticked and the decision dialog open - the approver still confirms on the page.
 */
final class Notifier {

    private static final Logger LOG = Logger.getLogger(Notifier.class.getName());

    static final String HI_APPROVAL = "HOLIDAY_INTIMATION_APPROVAL";
    static final String HI_LEVEL = "HOLIDAY_INTIMATION_LEVEL_APPROVED";
    static final String HI_REJECTED = "HOLIDAY_INTIMATION_REJECTED";
    static final String HI_APPROVED = "HOLIDAY_INTIMATION_APPROVED";
    static final String HI_CANCELLED = "HOLIDAY_INTIMATION_CANCELLED";
    static final String CO_APPROVAL = "COMP_OFF_APPROVAL";
    static final String CO_LEVEL = "COMP_OFF_LEVEL_APPROVED";
    static final String CO_REJECTED = "COMP_OFF_REJECTED";
    static final String CO_APPROVED = "COMP_OFF_APPROVED";
    static final String CO_CANCELLED = "COMP_OFF_CANCELLED";
    /** Admin pages: a Comp Off applied (Approved) / cancelled by the Admin, to the approvers of the staff member's chain. */
    static final String CO_ADMIN_APPLIED = "COMP_OFF_APPLIED_BY_ADMIN";
    static final String CO_ADMIN_CANCELLED = "COMP_OFF_CANCELLED_BY_ADMIN";
    /** Comp Off cancelled by the Admin, to the staff member (with their balance). */
    static final String CO_ADMIN_CANCELLED_STAFF = "COMP_OFF_CANCELLED_BY_ADMIN_STAFF";

    /** One request inside an email: label / value lines, optional Approve / Reject links. */
    private static final class Item {
        final Map<String, String> lines = new LinkedHashMap<String, String>();
        String approveLink;
        String rejectLink;
    }

    /** All the items of one email (one recipient, one type). */
    private static final class Batch {
        final String type;
        final Staffs to;
        final List<Item> items = new ArrayList<Item>();
        String actor;           // who decided (rejections / approvals) or who cancelled
        Integer staffIdForBalance;

        Batch(String type, Staffs to) {
            this.type = type;
            this.to = to;
        }
    }

    private final CompOffManager manager;
    private final String baseUrl;
    private final Map<String, Batch> batches = new LinkedHashMap<String, Batch>();
    private final Map<Integer, String> teams = new LinkedHashMap<Integer, String>();

    /** requestBaseUrl: the current request's scheme://host[:port]/context, used when APP_BASE_URL is not set (may be null). */
    Notifier(CompOffManager manager, String requestBaseUrl) {
        this.manager = manager;
        String configured = MailConfig.appBaseUrl();
        this.baseUrl = configured != null ? configured : requestBaseUrl;
    }

    // =====================================================================
    // Holiday Intimation
    // =====================================================================

    /** A Holiday Intimation now waits with this approver (level 1 after submit, the next level / HR after a hand-over). */
    void holidayApproval(Staffs approver, HolidayRequests h, int level, boolean hr) {
        Staffs emp = manager.findStaff(h.getStaffID());
        Item it = item(emp);
        it.lines.put("Worked date", DateUtil.display(h.getWorkRequestDate()));
        it.lines.put("Shift type", h.getShiftType());
        it.lines.put("Reason", h.getNotes());
        it.lines.put("Approval step", hr ? "HR approval" : "Level " + level);
        it.approveLink = link("approveHolidayWorking1", h.getHWRID(), "approve", approver);
        it.rejectLink = link("approveHolidayWorking1", h.getHWRID(), "reject", approver);
        add(HI_APPROVAL, approver, it, null);
    }

    void holidayLevelApproved(HolidayRequests h, Integer byId, int level, Staffs next, boolean nextIsHr, String remarks) {
        Item it = new Item();
        it.lines.put("Worked date", DateUtil.display(h.getWorkRequestDate()));
        it.lines.put("Approved at", "Level " + level);
        it.lines.put("Now waiting for", next.getStaffName() + (nextIsHr ? " (HR approval)" : " (level " + (level + 1) + ")"));
        it.lines.put("Notes", remarks);
        add(HI_LEVEL, manager.findStaff(h.getStaffID()), it, name(byId));
    }

    void holidayRejected(HolidayRequests h, Integer byId, String remarks) {
        Item it = new Item();
        it.lines.put("Worked date", DateUtil.display(h.getWorkRequestDate()));
        it.lines.put("Status", "Rejected");
        it.lines.put("Notes", remarks);
        add(HI_REJECTED, manager.findStaff(h.getStaffID()), it, name(byId));
    }

    /** Final approval (approval servlet): the day is now in the balance. */
    void holidayApproved(HolidayRequests h, Integer byId, String remarks) {
        Item it = new Item();
        it.lines.put("Worked date", DateUtil.display(h.getWorkRequestDate()));
        it.lines.put("Status", "Approved");
        it.lines.put("Comp Off", "1 Comp Off day added to your balance");
        it.lines.put("Notes", remarks);
        Batch b = add(HI_APPROVED, manager.findStaff(h.getStaffID()), it, name(byId));
        if (b != null) {
            b.staffIdForBalance = h.getStaffID();
        }
    }

    /** The staff member cancelled a Pending intimation that was waiting with this approver. */
    void holidayCancelled(Integer approverId, HolidayRequests h, String reason) {
        Staffs emp = manager.findStaff(h.getStaffID());
        Item it = item(emp);
        it.lines.put("Worked date", DateUtil.display(h.getWorkRequestDate()));
        it.lines.put("Status", "Cancelled by the employee");
        it.lines.put("Cancel Notes", reason);
        add(HI_CANCELLED, manager.findStaff(approverId), it, emp == null ? null : emp.getStaffName());
    }

    // =====================================================================
    // Comp Off
    // =====================================================================

    void compOffApproval(Staffs approver, CompensatoryOffRequest r, int level, boolean hr) {
        Staffs emp = manager.findStaff(r.getStaffID());
        Item it = item(emp);
        it.lines.put("Comp Off date", DateUtil.display(r.getLeaveDate()) + " (" + r.getDayType() + ")");
        it.lines.put("Holiday worked on", r.getAttendanceDate() == null ? null : DateUtil.display(r.getAttendanceDate()));
        it.lines.put("Notes", CompOffManager.userNotes(r.getLeaveNotes()));
        CompOffBalanceDetails cod = manager.summary(r.getStaffID());
        it.lines.put("Employee's Comp Off balance", cod == null ? null : CompOffManager.plainDays(cod.getBalance()) + " day(s) after this request");
        it.lines.put("Approval step", hr ? "HR approval" : "Level " + level);
        it.approveLink = link("approveCompOff1", r.getCOID(), "approve", approver);
        it.rejectLink = link("approveCompOff1", r.getCOID(), "reject", approver);
        add(CO_APPROVAL, approver, it, null);
    }

    void compOffLevelApproved(CompensatoryOffRequest r, Integer byId, int level, Staffs next, boolean nextIsHr, String remarks) {
        Item it = new Item();
        it.lines.put("Comp Off date", DateUtil.display(r.getLeaveDate()));
        it.lines.put("Approved at", "Level " + level);
        it.lines.put("Now waiting for", next.getStaffName() + (nextIsHr ? " (HR approval)" : " (level " + (level + 1) + ")"));
        it.lines.put("Notes", remarks);
        add(CO_LEVEL, manager.findStaff(r.getStaffID()), it, name(byId));
    }

    void compOffRejected(CompensatoryOffRequest r, Integer byId, String remarks) {
        Item it = new Item();
        it.lines.put("Comp Off date", DateUtil.display(r.getLeaveDate()));
        it.lines.put("Status", "Rejected (the day is back in your balance)");
        it.lines.put("Notes", remarks);
        Batch b = add(CO_REJECTED, manager.findStaff(r.getStaffID()), it, name(byId));
        if (b != null) {
            b.staffIdForBalance = r.getStaffID();
        }
    }

    /** Final approval (approval servlet). */
    void compOffApproved(CompensatoryOffRequest r, Integer byId, String remarks) {
        Item it = new Item();
        it.lines.put("Comp Off date", DateUtil.display(r.getLeaveDate()) + " (" + r.getDayType() + ")");
        it.lines.put("Status", "Approved");
        it.lines.put("Notes", remarks);
        Batch b = add(CO_APPROVED, manager.findStaff(r.getStaffID()), it, name(byId));
        if (b != null) {
            b.staffIdForBalance = r.getStaffID();
        }
    }

    /** Apply Comp Off for Staff: the staff member gets "approved" (with balance), each approver of their chain a notice. */
    void compOffAppliedByAdmin(CompensatoryOffRequest r, Staffs admin, String notes) {
        compOffApproved(r, admin.getStaffID(), notes);
        Staffs emp = manager.findStaff(r.getStaffID());
        for (Integer id : manager.chainIds(r.getStaffID())) {
            if (!id.equals(admin.getStaffID()) && !id.equals(r.getStaffID())) {
                Item it = item(emp);
                it.lines.put("Comp Off date", DateUtil.display(r.getLeaveDate()) + " (" + r.getDayType() + ")");
                it.lines.put("Status", "Approved by " + admin.getStaffName());
                it.lines.put("Notes", notes);
                add(CO_ADMIN_APPLIED, manager.findStaff(id), it, admin.getStaffName());
            }
        }
    }

    /** Comp Off Cancel (Admin): the staff member (day back in the balance) and each approver of their chain. */
    void compOffCancelledByAdmin(CompensatoryOffRequest r, Staffs admin, String reason) {
        Staffs emp = manager.findStaff(r.getStaffID());
        Item mine = new Item();
        mine.lines.put("Comp Off date", DateUtil.display(r.getLeaveDate()) + " (" + r.getDayType() + ")");
        mine.lines.put("Status", "Cancelled (the day is back in your balance)");
        mine.lines.put("Cancel Notes", reason);
        Batch b = add(CO_ADMIN_CANCELLED_STAFF, emp, mine, admin.getStaffName());
        if (b != null) {
            b.staffIdForBalance = r.getStaffID();
        }
        for (Integer id : manager.chainIds(r.getStaffID())) {
            if (!id.equals(admin.getStaffID()) && !id.equals(r.getStaffID())) {
                Item it = item(emp);
                it.lines.put("Comp Off date", DateUtil.display(r.getLeaveDate()));
                it.lines.put("Status", "Cancelled by " + admin.getStaffName());
                it.lines.put("Cancel Notes", reason);
                add(CO_ADMIN_CANCELLED, manager.findStaff(id), it, admin.getStaffName());
            }
        }
    }

    /** The staff member cancelled a Pending Comp Off request that was waiting with this approver. */
    void compOffCancelled(Integer approverId, CompensatoryOffRequest r, String reason) {
        Staffs emp = manager.findStaff(r.getStaffID());
        Item it = item(emp);
        it.lines.put("Comp Off date", DateUtil.display(r.getLeaveDate()));
        it.lines.put("Status", "Cancelled by the employee");
        it.lines.put("Cancel Notes", reason);
        add(CO_CANCELLED, manager.findStaff(approverId), it, emp == null ? null : emp.getStaffName());
    }

    // =====================================================================
    // Sending
    // =====================================================================

    /** Sends the collected emails (one per recipient and type) and forgets them. Returns how many Mailgun accepted. */
    int flush() {
        int accepted = 0;
        List<Batch> todo = new ArrayList<Batch>(batches.values());
        batches.clear();
        for (Batch b : todo) {
            try {
                String[] mail = render(b);
                if (EmailService.send(b.type, b.to.getEmail(), mail[0], mail[1], mail[2], mail[3]).accepted()) {
                    accepted++;
                }
            } catch (RuntimeException e) {
                // a template problem must not affect the business step, which is already saved
                LOG.log(Level.SEVERE, "Email " + b.type + " could not be generated", e);
            }
        }
        return accepted;
    }

    private Batch add(String type, Staffs to, Item it, String actor) {
        if (to == null || to.getEmail() == null || to.getEmail().trim().isEmpty()) {
            LOG.warning("Email " + type + " skipped: recipient missing, inactive or without an email address");
            return null;
        }
        String key = type + "|" + to.getStaffID() + "|" + (actor == null ? "" : actor);
        Batch b = batches.get(key);
        if (b == null) {
            b = new Batch(type, to);
            b.actor = actor;
            batches.put(key, b);
        }
        b.items.add(it);
        return b;
    }

    /** subject, plain text, HTML, main link */
    private String[] render(Batch b) {
        int n = b.items.size();
        String who = b.actor == null ? "" : b.actor;
        String subject, headline, linkLabel, link;
        switch (b.type) {
            case HI_APPROVAL:
                subject = n == 1 ? "Holiday Intimation approval required - " + first(b, "Employee") : n + " Holiday Intimations waiting for your approval";
                headline = n == 1 ? "Holiday Intimation request from " + first(b, "Employee") : n + " Holiday Intimation requests are waiting for your approval";
                linkLabel = "Open the approval page";
                link = page("approveHolidayWorking1");
                break;
            case HI_LEVEL:
                subject = "Holiday Intimation approved at " + first(b, "Approved at").toLowerCase() + " by " + who;
                headline = "Your Holiday Intimation " + plural(n, "request was", "requests were") + " approved by " + who + " and " + (n == 1 ? "is" : "are") + " In Process";
                linkLabel = "View your Holiday Intimations";
                link = page("holidayIntimation1");
                break;
            case HI_REJECTED:
                subject = "Holiday Intimation rejected by " + who;
                headline = "Your Holiday Intimation " + plural(n, "request was", "requests were") + " <span style=\"color:#b42318\">rejected</span> by " + esc(who);
                linkLabel = "View your Holiday Intimations";
                link = page("holidayIntimation1");
                break;
            case HI_APPROVED:
                subject = "Holiday Intimation approved - Comp Off day added";
                headline = "Your Holiday Intimation " + plural(n, "request was", "requests were") + " <span style=\"color:green\">approved</span> by " + esc(who);
                linkLabel = "View your Comp Off balance";
                link = page("compOffBalance1");
                break;
            case HI_CANCELLED:
                subject = who + " cancelled a Holiday Intimation request";
                headline = esc(who) + " cancelled " + plural(n, "a Holiday Intimation request", "Holiday Intimation requests") + " that " + (n == 1 ? "was" : "were") + " waiting for your approval";
                linkLabel = "Open the approval page";
                link = page("approveHolidayWorking1");
                break;
            case CO_APPROVAL:
                subject = n == 1 ? "Comp Off approval required - " + first(b, "Employee") : n + " Comp Off requests waiting for your approval";
                headline = n == 1 ? "Comp Off request from " + first(b, "Employee") : n + " Comp Off requests are waiting for your approval";
                linkLabel = "Open the approval page";
                link = page("approveCompOff1");
                break;
            case CO_LEVEL:
                subject = "Comp Off approved at " + first(b, "Approved at").toLowerCase() + " by " + who;
                headline = "Your Comp Off " + plural(n, "request was", "requests were") + " approved by " + who + " and " + (n == 1 ? "is" : "are") + " In Process";
                linkLabel = "View your Comp Off requests";
                link = page("applyCompOff1");
                break;
            case CO_REJECTED:
                subject = "Comp Off request rejected by " + who;
                headline = "Your Comp Off " + plural(n, "request was", "requests were") + " <span style=\"color:#b42318\">rejected</span> by " + esc(who);
                linkLabel = "View your Comp Off requests";
                link = page("applyCompOff1");
                break;
            case CO_APPROVED:
                subject = "Comp Off request approved";
                headline = "Your Comp Off " + plural(n, "request was", "requests were") + " <span style=\"color:green\">approved</span> by " + esc(who);
                linkLabel = "View your Comp Off requests";
                link = page("applyCompOff1");
                break;
            case CO_ADMIN_APPLIED:
                subject = n == 1 ? "Comp Off approved by " + who + " - " + first(b, "Employee") : n + " Comp Offs approved by " + who;
                headline = esc(who) + " applied and <span style=\"color:green\">approved</span> " + plural(n, "a Comp Off", "Comp Offs")
                        + " for your staff";
                linkLabel = "Open My Team Reports";
                link = page("teamCompOffMonthView1");
                break;
            case CO_ADMIN_CANCELLED:
                subject = n == 1 ? "Comp Off cancelled by " + who + " - " + first(b, "Employee") : n + " Comp Offs cancelled by " + who;
                headline = esc(who) + " cancelled " + plural(n, "a Comp Off", "Comp Offs") + " of your staff";
                linkLabel = "Open My Team Reports";
                link = page("teamCompOffMonthView1");
                break;
            case CO_ADMIN_CANCELLED_STAFF:
                subject = "Comp Off cancelled by " + who;
                headline = "Your Comp Off " + plural(n, "was", "were") + " <span style=\"color:#b42318\">cancelled</span> by " + esc(who);
                linkLabel = "View your Comp Off balance";
                link = page("compOffBalance1");
                break;
            default:   // CO_CANCELLED
                subject = who + " cancelled a Comp Off request";
                headline = esc(who) + " cancelled " + plural(n, "a Comp Off request", "Comp Off requests") + " that " + (n == 1 ? "was" : "were") + " waiting for your approval";
                linkLabel = "Open the approval page";
                link = page("approveCompOff1");
        }
        // the two "In Process" headlines are built from plain text
        if (HI_LEVEL.equals(b.type) || CO_LEVEL.equals(b.type)) {
            headline = esc(headline);
        } else if (HI_APPROVAL.equals(b.type) || CO_APPROVAL.equals(b.type)) {
            headline = esc(headline);
        }

        Map<String, String> balance = new LinkedHashMap<String, String>();
        if (b.staffIdForBalance != null) {
            CompOffBalanceDetails cod = manager.summary(b.staffIdForBalance);
            if (cod != null) {
                balance.put("Holidays Approved", CompOffManager.plainDays(cod.getAllocated()));
                balance.put("Used", CompOffManager.plainDays(cod.getUsed()));
                balance.put("Balance", CompOffManager.plainDays(cod.getBalance()));
            }
        }

        StringBuilder html = new StringBuilder();
        StringBuilder text = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>")
            .append("<body style=\"margin:0;padding:0;background:#f4f6fa\">")
            .append("<div style=\"max-width:700px;width:100%;margin:20px auto 0 auto;border:1px solid #ccc;background:#fff;font-family:Arial,sans-serif;font-size:14px;color:#000\">")
            .append("<div style=\"background-color:#0090cc;min-height:50px;border-bottom:1px solid #c2c2c2;padding:15px 20px 0 45px;color:#fff;font-size:18px;font-weight:bold\">Comp Off Management</div>")
            .append("<div style=\"margin:20px 50px\">")
            .append("<p><span style=\"font-size:15px\">Dear <strong>").append(esc(b.to.getStaffName())).append(",</strong></span></p>")
            .append("<p><strong><span style=\"font-size:15px\">").append(headline).append("</span></strong></p>");
        text.append("Dear ").append(b.to.getStaffName()).append(",\n\n").append(stripTags(headline)).append("\n");

        int i = 0;
        for (Item it : b.items) {
            i++;
            html.append("<div style=\"margin:14px 0;padding:12px 14px;border:1px solid #e3e8ef;border-radius:6px\">");
            if (n > 1) {
                html.append("<div style=\"font-weight:bold;margin-bottom:6px\">").append(i).append(".</div>");
            }
            text.append("\n").append(n > 1 ? i + ".\n" : "");
            for (Map.Entry<String, String> e : it.lines.entrySet()) {
                if (e.getValue() == null || e.getValue().trim().isEmpty()) {
                    continue;
                }
                html.append("<div style=\"margin:3px 0\">").append(esc(e.getKey())).append(":&nbsp;<strong>").append(esc(e.getValue())).append("</strong></div>");
                text.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            if (it.approveLink != null) {
                html.append("<div style=\"margin-top:12px\">")
                    .append("<a href=\"").append(esc(it.approveLink)).append("\" style=\"background:#ff7e00;border-radius:5px;font-size:13px;color:#fff;font-weight:700;padding:6px 18px;text-decoration:none;display:inline-block\">Approve</a>")
                    .append("<a href=\"").append(esc(it.rejectLink)).append("\" style=\"background:#0596f7;border-radius:5px;font-size:13px;color:#fff;font-weight:700;padding:6px 18px;text-decoration:none;display:inline-block;margin-left:16px\">Reject</a>")
                    .append("</div>");
                text.append("Approve: ").append(it.approveLink).append("\nReject: ").append(it.rejectLink).append("\n");
            }
            html.append("</div>");
        }
        if (!balance.isEmpty()) {
            html.append("<div style=\"margin:14px 0\">Your Comp Off balance:&nbsp;");
            text.append("\nYour Comp Off balance: ");
            boolean firstOne = true;
            for (Map.Entry<String, String> e : balance.entrySet()) {
                html.append(firstOne ? "" : " &nbsp;|&nbsp; ").append(esc(e.getKey())).append(" <strong>").append(esc(e.getValue())).append("</strong>");
                text.append(firstOne ? "" : ", ").append(e.getKey()).append(" ").append(e.getValue());
                firstOne = false;
            }
            html.append("</div>");
            text.append("\n");
        }
        if (link != null) {
            html.append("<p><a href=\"").append(esc(link)).append("\" style=\"color:#329fd5\">").append(esc(linkLabel)).append("</a></p>");
            text.append("\n").append(linkLabel).append(": ").append(link).append("\n");
        }
        if (HI_APPROVAL.equals(b.type) || CO_APPROVAL.equals(b.type)) {
            html.append("<p style=\"color:#555;font-size:12px\">Approve / Reject open the approval page after you sign in; confirm your decision there.</p>");
        }
        html.append("<p>Regards,<br>Your team at HanddyTimesheets</p></div>")
            .append("<div style=\"background-color:#d0e9f3;padding:20px 50px;margin-top:25px\">This is an automated email.</div></div></body></html>");
        text.append("\nRegards,\nYour team at HanddyTimesheets\n\nThis is an automated email.");
        return new String[] {stripTags(subject), text.toString(), html.toString(), link};
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Item item(Staffs emp) {
        Item it = new Item();
        it.lines.put("Employee", emp == null ? null : emp.getStaffName() + " (" + emp.getEmployeeCode() + ")");
        it.lines.put("Team name", emp == null ? null : teamName(emp.getTeamID()));
        return it;
    }

    private String name(Integer staffId) {
        Staffs s = staffId == null ? null : manager.findStaff(staffId);
        return s == null ? "the approver" : s.getStaffName();
    }

    private String teamName(Integer teamId) {
        if (teamId == null) {
            return null;
        }
        if (!teams.containsKey(teamId)) {
            List<Teams> rows = manager.list("from Teams where teamID = " + com.util.SqlUtil.number(teamId));
            teams.put(teamId, rows.isEmpty() ? null : rows.get(0).getTeamName());
        }
        return teams.get(teamId);
    }

    /** Page link (null when the application address is unknown). */
    private String page(String action) {
        return baseUrl == null ? null : baseUrl + "/" + action + ".action";
    }

    /** Approve / Reject link of one request: the approval page with that (encrypted) request ticked and the decision open. */
    private String link(String action, Integer id, String decision, Staffs approver) {
        // ap = the approver the email is for: the login page fills in (and locks) that approver's login, and the link
        // only works for that approver
        return baseUrl == null ? null : baseUrl + "/" + action + ".action?open=" + IdCrypt.enc(id) + "&decision=" + decision
                + (approver == null ? "" : "&ap=" + IdCrypt.enc(approver.getStaffID()));
    }

    private static String first(Batch b, String key) {
        String v = b.items.get(0).lines.get(key);
        return v == null ? "" : v;
    }

    private static String plural(int n, String one, String many) {
        return n == 1 ? one : many;
    }

    private static String esc(String s) {
        return s == null ? "" : StringEscapeUtils.escapeHtml(s);
    }

    private static String stripTags(String s) {
        return StringEscapeUtils.unescapeHtml(s.replaceAll("<[^>]+>", ""));
    }
}
