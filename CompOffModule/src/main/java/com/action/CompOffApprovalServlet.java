package com.action;

import java.time.YearMonth;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import com.util.CompOffConfig;

/**
 * /processCompOffApprovals: carries out ONE staged Comp Off decision per run - an approval (the request becomes
 * Approved) or a rejection (Rejected, the day goes back to the balance) - then sets approved requests whose leave
 * date has passed to Completed and gives waiting requests whose approver is no longer valid to the Admin.
 *
 * Without parameters (the cron run) it only takes decisions the salary cut-off allows (ApprovalManager.autoEligible).
 * With month and year (e.g. ?month=9&year=2026) it processes ONE decision waiting for that leave month, oldest click
 * first, whatever the cut-off - the same as "One request" on the Admin's Comp Off Processing page; the history notes
 * "Processed manually (servlet, month 09/2026)". A bad month / year is answered 400 and nothing is processed.
 */
public class CompOffApprovalServlet extends ApprovalTaskServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(CompOffApprovalServlet.class.getName());

    @Override
    protected String run(ApprovalManager approvals, HttpServletRequest request) {
        String m = request.getParameter("month"), y = request.getParameter("year");
        YearMonth ym = null;
        if (m != null || y != null) {
            ym = month(m, y);
            if (ym == null) {
                throw new BadRequest("month must be 1-12 and year a 4-digit year (e.g. ?month=9&year=2026); nothing was processed");
            }
        }
        int handled = ym == null ? approvals.processNextCompOff()
                : approvals.processCompOffMonth(ym, false, String.format("manually (servlet, month %02d/%d)", ym.getMonthValue(), ym.getYear()));
        approvals.flushEmails();          // after the decision is saved; a failed email never undoes it
        int failed = approvals.getLastFailed();
        int completed = approvals.completePastLeaves();
        int toAdmin = approvals.reassignToAdmin(null, approvals.defaultAdminId(), false, true);
        approvals.syncSteps(com.dao.CompOffConstants.LT_COMP_OFF);     // every waiting request has its open CompensatoryOffApprovers step
        String which = ym == null ? "" : String.format(", month %02d/%d", ym.getMonthValue(), ym.getYear());
        LOG.info("Comp off approval servlet" + which + " processed " + handled + " staged decision(s), " + failed + " failed (retried next run), completed "
                + completed + " Comp Off request(s), set " + toAdmin + " waiting request(s) to the Admin");
        return "processed " + handled + ", failed " + failed + ", completed " + completed + ", set to admin " + toAdmin + which;
    }

    /** "9", "2026" -> 2026-09; null when either is missing or not valid (year 2000 .. next year). */
    static YearMonth month(String m, String y) {
        if (m == null || y == null || !m.trim().matches("\\d{1,2}") || !y.trim().matches("\\d{4}")) {
            return null;
        }
        int mm = Integer.parseInt(m.trim()), yy = Integer.parseInt(y.trim());
        if (mm < 1 || mm > 12 || yy < 2000 || yy > CompOffConfig.today().getYear() + 1) {
            return null;
        }
        return YearMonth.of(yy, mm);
    }
}
