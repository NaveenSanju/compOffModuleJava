package com.action;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.dao.CompOffConstants;
import com.util.DateUtil;

/**
 * The readable sentence kept in CompOffBalanceDetailsHistory.historyActionComments, e.g.
 * "Compensatory off requested for 2026-10-21 by Priya Sharma on 2026-10-13 05:47:33 (GMT), using holiday worked on 2026-06-14".
 * Dates are yyyy-MM-dd, the time is GMT. leaveHistoryActionCommands keeps the JSON the application reads.
 * A call site passes what only it knows (the approver's own remarks, the next approver, the event time, the leave date
 * of a completed intimation); history() adds the rest.
 */
final class HistoryComment {

    private String remarks;
    private String nextApprover;
    private boolean nextIsHr;
    private Date when;
    private Date leaveDate;
    private String processedHow;
    private Date processedAt;

    private HistoryComment() {
    }

    static HistoryComment none() {
        return new HistoryComment();
    }

    /** The approver's / staff member's own remarks (shown as "Notes: ..."). */
    static HistoryComment remarks(String remarks) {
        HistoryComment c = new HistoryComment();
        c.remarks = remarks;
        return c;
    }

    /** The request moved on to this approver (the next level, or the HR step). */
    HistoryComment sentTo(String approverName, boolean hr) {
        nextApprover = approverName;
        nextIsHr = hr;
        return this;
    }

    /** When the event happened, when that is not now (a staged approval: the approver's click). */
    HistoryComment at(Date eventTime) {
        when = eventTime;
        return this;
    }

    /** Comp Off date of a completed intimation. */
    HistoryComment leaveDate(Date date) {
        leaveDate = date;
        return this;
    }

    /**
     * Carried out manually rather than by the automatic run, e.g. "by Admin Meera Iyer" (Comp Off Processing page) or
     * "manually (servlet, month 09/2026)": noted in the sentence as ". Processed <how> on <time> (GMT)".
     */
    HistoryComment processed(String how) {
        if (how != null) {
            processedHow = how;
            processedAt = new Date();
        }
        return this;
    }

    Date when() {
        return when;
    }

    /**
     * The sentence for one history row. workedDate / compOffDate may be null when the event has none;
     * actorName is null for the approval servlet's own steps (Allocated, Completed).
     */
    String build(String leaveType, String action, Date workedDate, Date compOffDate, String actorName, Integer level, Date time) {
        boolean holiday = CompOffConstants.LT_HOLIDAY.equals(leaveType);
        String at = " on " + gmt(time) + " (GMT)";
        String by = " by " + (actorName == null ? "the system" : actorName);
        String lv = level == null ? "" : " at level " + level;
        StringBuilder s = new StringBuilder();
        if (holiday) {
            String d = iso(workedDate);
            if (CompOffConstants.HIST_SUBMITTED.equals(action)) {
                s.append("Holiday working requested for ").append(d).append(by).append(at);
            } else if (CompOffConstants.HIST_APPROVED.equals(action)) {
                s.append(nextApprover != null ? "Holiday working for " + d + " approved" + lv : "Holiday working approved for " + d)
                        .append(by).append(at).append(sentToText(level));
            } else if (CompOffConstants.HIST_ALLOCATED.equals(action)) {
                s.append("Holiday working for ").append(d).append(" approved; 1 Comp Off day added").append(at);
            } else if (CompOffConstants.HIST_REJECTED.equals(action)) {
                s.append("Holiday working for ").append(d).append(" rejected").append(lv).append(by).append(at);
            } else if (CompOffConstants.HIST_CANCELLED.equals(action)) {
                s.append("Holiday working for ").append(d).append(" cancelled").append(by).append(at);
            } else if (CompOffConstants.HIST_COMPLETED.equals(action)) {
                s.append("Holiday working for ").append(d).append(" completed")
                        .append(leaveDate == null ? "" : "; Comp Off taken on " + iso(leaveDate));
            } else {
                s.append("Holiday working for ").append(d).append(' ').append(action).append(by).append(at);
            }
        } else {
            String d = iso(compOffDate);
            if (CompOffConstants.HIST_REQUESTED.equals(action)) {
                s.append("Compensatory off requested for ").append(d).append(by).append(at)
                        .append(workedDate == null ? "" : ", using holiday worked on " + iso(workedDate));
            } else if (CompOffConstants.HIST_APPROVED.equals(action)) {
                s.append(nextApprover != null ? "Compensatory off for " + d + " approved" + lv : "Compensatory off approved for " + d)
                        .append(by).append(at).append(sentToText(level));
            } else if (CompOffConstants.HIST_REJECTED.equals(action)) {
                s.append("Compensatory off for ").append(d).append(" rejected").append(lv).append(by).append(at);
            } else if (CompOffConstants.HIST_CANCELLED.equals(action)) {
                s.append("Compensatory off cancelled for ").append(d).append(by).append(at);
            } else if (CompOffConstants.HIST_COMPLETED.equals(action)) {
                s.append("Compensatory off for ").append(d).append(" completed (leave date passed)");
            } else {
                s.append("Compensatory off for ").append(d).append(' ').append(action).append(by).append(at);
            }
        }
        if (processedHow != null) {
            s.append(". Processed ").append(processedHow).append(" on ").append(gmt(processedAt)).append(" (GMT)");
        }
        if (remarks != null && !remarks.trim().isEmpty()) {
            s.append(". Notes: ").append(remarks.trim());
        }
        return s.toString();
    }

    private String sentToText(Integer level) {
        if (nextApprover == null) {
            return "";
        }
        return ", sent to " + nextApprover + (nextIsHr ? " for HR approval" : level == null ? "" : " for level " + (level + 1));
    }

    /** yyyy-MM-dd. */
    static String iso(Date d) {
        return d == null ? "-" : DateUtil.toLocalDate(d).toString();
    }

    /** yyyy-MM-dd HH:mm:ss in GMT. */
    static String gmt(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        f.setTimeZone(TimeZone.getTimeZone("GMT"));
        return f.format(d);
    }
}
