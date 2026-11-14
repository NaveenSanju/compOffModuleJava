package com.action;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.util.CompanyContext;

/**
 * Base of the approval servlets (HolidayApprovalServlet, CompOffApprovalServlet). Each run processes ONE staged
 * decision (approve or reject, the oldest click first) of its own flow, plus that flow's housekeeping. The Comp Off
 * servlet also takes ?month=&year= to process a decision held for that leave month.
 *
 * Not scheduled here: add App Engine cron entries for /processHolidayApprovals and
 * /processCompOffApprovals. Only App Engine cron (X-Appengine-Cron header, which App Engine removes from
 * outside requests) or a request from the server itself may run them.
 */
public abstract class ApprovalTaskServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"true".equals(request.getHeader("X-Appengine-Cron")) && !isLocal(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        ApprovalManager approvals = new ApprovalManager();
        approvals.setRequestBaseUrl(CompOffAction.requestBaseUrl(request));
        String result;
        try {
            // once per company (Staffs.companyID): every step reads and writes only that company's data
            List<Integer> companies = new CompOffManager().companyIds();
            StringBuilder sb = new StringBuilder();
            for (Integer company : companies) {
                CompanyContext.set(company);
                try {
                    String r = run(approvals, request);
                    sb.append(sb.length() == 0 ? "" : "\n").append(companies.size() == 1 ? r : "company " + company + ": " + r);
                } finally {
                    CompanyContext.clear();
                }
            }
            result = sb.length() == 0 ? "no company" : sb.toString();
        } catch (BadRequest e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result = e.getMessage();
        }
        response.setContentType("text/plain");
        response.getWriter().write(result);
    }

    /** One run of this servlet; returns the plain-text summary (BadRequest: answered 400 with its message). */
    protected abstract String run(ApprovalManager approvals, HttpServletRequest request);

    /** A parameter that cannot be used: the servlet answers 400 and processes nothing. */
    static final class BadRequest extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BadRequest(String message) {
            super(message);
        }
    }

    private static boolean isLocal(HttpServletRequest request) {
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (IOException e) {
            return false;
        }
    }
}
