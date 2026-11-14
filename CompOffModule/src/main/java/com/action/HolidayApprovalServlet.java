package com.action;

import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * /processHolidayApprovals: carries out ONE staged Holiday Intimation decision per run - an approval (the
 * intimation becomes Available, one Comp Off day is added) or a rejection (the intimation becomes Rejected) - then
 * gives waiting intimations whose approver is no longer valid to the Admin.
 */
public class HolidayApprovalServlet extends ApprovalTaskServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(HolidayApprovalServlet.class.getName());

    @Override
    protected String run(ApprovalManager approvals, HttpServletRequest request) {
        int handled = approvals.processNextHoliday();
        approvals.flushEmails();          // after the decision is saved; a failed email never undoes it
        int failed = approvals.getLastFailed();
        int toAdmin = approvals.reassignToAdmin(null, approvals.defaultAdminId(), true, false);
        approvals.syncSteps(com.dao.CompOffConstants.LT_HOLIDAY);      // every waiting intimation has its open HolidayRequestApprovers step
        LOG.info("Holiday approval servlet processed " + handled + " staged decision(s), " + failed
                + " failed (retried next run), set " + toAdmin + " waiting intimation(s) to the Admin");
        return "processed " + handled + ", failed " + failed + ", set to admin " + toAdmin;
    }
}
