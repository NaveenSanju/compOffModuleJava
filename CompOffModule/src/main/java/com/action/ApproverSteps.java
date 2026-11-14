package com.action;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import com.dao.CompOffConstants;
import com.serviceimpl.HibernateService;
import com.serviceimpl.HibernateUtil;
import com.util.SqlUtil;

/**
 * HolidayRequestApprovers / CompensatoryOffApprovers: one row per approval step of a request, created only when the
 * step starts. Submitting creates the level-1 row (Pending); when a level approves, its row becomes Approved and the
 * next level's row is created (the next chain level, then the HR approver when HR approval is enabled in General
 * Settings); the last step's decision goes through the Multi...Approvals table and the approval servlet as before.
 * A rejection ends the steps (Rejected) and a cancellation by the staff member closes the open one (Cancelled).
 * approverType is the approver's user type (TL, PM, MR, HR, ...), so the HR step shows as 'HR'.
 *
 * The approval pages read the Pending rows: a request waits with the approver of its Pending row. The request's own
 * approverID / leaveApproverID column is kept the same, so everything else keeps working.
 * Rows are never deleted (Status 'A' / 'I').
 */
public class ApproverSteps {

    private static final Logger LOG = Logger.getLogger(ApproverSteps.class.getName());

    public static final String PENDING = "Pending";
    public static final String APPROVED = "Approved";
    public static final String REJECTED = "Rejected";
    public static final String CANCELLED = "Cancelled";
    /**
     * SQL: the user type code (Staffs.userType: TL, PM, MR, HR, C, ...) of the staff member, as written in
     * leaveApproverID; 'NA' when they have none. Stored in approverType.
     */
    static String userTypeOf(String staffIdExpr) {
        return "(SELECT COALESCE(MAX(NULLIF(TRIM(sx.userType), '')), 'NA') FROM Staffs sx WHERE sx.staffID = " + staffIdExpr + ")";
    }

    private final HibernateService hs = new HibernateUtil();

    /** The steps table of a flow ('Holiday Intimation' / 'Compensatory OFF'). */
    public static String table(String leaveType) {
        return CompOffConstants.LT_HOLIDAY.equals(leaveType) ? "HolidayRequestApprovers" : "CompensatoryOffApprovers";
    }

    /** The column holding the request id (HWRID / COID). */
    public static String requestColumn(String leaveType) {
        return CompOffConstants.LT_HOLIDAY.equals(leaveType) ? "HWID" : "COID";
    }

    /** SQL: the request (alias.idColumn) has a Pending step with this approver. */
    public static String waitingWith(String leaveType, String requestIdExpr, Integer approverId) {
        return "EXISTS (SELECT 1 FROM " + table(leaveType) + " st WHERE st." + requestColumn(leaveType) + " = " + requestIdExpr
                + " AND st.approverID = " + SqlUtil.number(approverId) + " AND st.approverStatus = 'Pending' AND st.status = 'A')";
    }

    /** SQL: the level of the request's Pending step (null when it has none). */
    public static String pendingLevel(String leaveType, String requestIdExpr) {
        return "(SELECT MAX(st.approvalLevel) FROM " + table(leaveType) + " st WHERE st." + requestColumn(leaveType) + " = " + requestIdExpr
                + " AND st.approverStatus = 'Pending' AND st.status = 'A')";
    }

    /**
     * Starts a step: the request now waits with this approver at this level; approverType = the approver's user type.
     * False when the row could not be written.
     */
    public boolean open(String leaveType, Integer requestId, Integer staffId, int level, Integer approverId) {
        int n = hs.updateSQLQuery1("INSERT INTO " + table(leaveType) + " (" + requestColumn(leaveType)
                + ", staffID, approvalLevel, approverType, approverID, approverStatus, status, createdDate) VALUES ("
                + SqlUtil.number(requestId) + ", " + SqlUtil.number(staffId) + ", " + level + ", "
                + userTypeOf(SqlUtil.number(approverId)) + ", " + SqlUtil.number(approverId) + ", 'Pending', 'A', NOW())");
        if (n != 1) {
            LOG.severe(table(leaveType) + ": level " + level + " step of request " + requestId + " could not be written");
        }
        return n == 1;
    }

    /**
     * Ends the request's Pending step with the decision (Approved / Rejected / Cancelled), the approver's notes and the
     * time they decided. Returns the rows changed (0 when no step was Pending).
     */
    public int close(String leaveType, Integer requestId, String status, String remarks, Date decidedAt) {
        return hs.updateSQLQuery1("UPDATE " + table(leaveType) + " SET approverStatus = " + SqlUtil.code(status)
                + ", notes = " + SqlUtil.text(remarks)
                + ", actionDate = " + (decidedAt == null ? "NOW()" : "'" + new java.sql.Timestamp(decidedAt.getTime()) + "'")
                + " WHERE " + requestColumn(leaveType) + " = " + SqlUtil.number(requestId)
                + " AND approverStatus = 'Pending' AND status = 'A'");
    }

    /** The request's Pending step moves to another approver (e.g. the Admin standing in for one who left), with their user type. */
    public int reassign(String leaveType, Integer requestId, Integer toApproverId) {
        return hs.updateSQLQuery1("UPDATE " + table(leaveType) + " SET approverID = " + SqlUtil.number(toApproverId)
                + ", approverType = " + userTypeOf(SqlUtil.number(toApproverId))
                + " WHERE " + requestColumn(leaveType) + " = " + SqlUtil.number(requestId)
                + " AND approverStatus = 'Pending' AND status = 'A'");
    }

    /** All steps of a request become Inactive (the request itself was set Inactive while it was being created). */
    public void inactivate(String leaveType, Integer requestId) {
        hs.updateSQLQuery1("UPDATE " + table(leaveType) + " SET status = 'I' WHERE " + requestColumn(leaveType) + " = "
                + SqlUtil.number(requestId) + " AND status = 'A'");
    }

    /**
     * Waiting requests whose open step is missing or names another approver than the request (rows created outside
     * the application, or before these tables existed): { request id, staff id, approver id, Pending step id or null }.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> outOfStep(String leaveType) {
        boolean holiday = CompOffConstants.LT_HOLIDAY.equals(leaveType);
        String approver = holiday ? "r.approverID" : "CAST(TRIM(SUBSTRING_INDEX(r.leaveApproverID, '~~', -1)) AS UNSIGNED)";
        String step = "(SELECT MAX(st." + (holiday ? "HRAID" : "COAID") + ") FROM " + table(leaveType)
                + " st WHERE st." + requestColumn(leaveType) + " = r." + (holiday ? "HWRID" : "COID")
                + " AND st.approverStatus = 'Pending' AND st.status = 'A')";
        String sql = holiday
                ? "SELECT r.HWRID, r.staffID, " + approver + ", " + step + " FROM HolidayRequests r"
                        + " WHERE r.holidayWorkingStatus = 'Pending' AND r.holidayApproveStatus = 'Pending' AND r.recordStatus = 'A'"
                        + " AND r.approverID IS NOT NULL AND " + com.util.CompOffConfig.companyStaffSql("r.staffID")
                : "SELECT r.COID, r.staffID, " + approver + ", " + step + " FROM CompensatoryOffRequest r"
                        + " WHERE r.leaveStatus = 'Pending' AND r.status = 'A' AND r.leaveApproverID LIKE '%~~%'"
                        + " AND " + com.util.CompOffConfig.companyStaffSql("r.staffID");
        List<Object[]> rows = hs.getSQLList(sql + " AND (" + step + " IS NULL OR NOT EXISTS (SELECT 1 FROM " + table(leaveType)
                + " st WHERE st." + requestColumn(leaveType) + " = r." + (holiday ? "HWRID" : "COID") + " AND st.approverID = " + approver
                + " AND st.approverStatus = 'Pending' AND st.status = 'A'))");
        return rows == null ? new java.util.ArrayList<Object[]>() : rows;
    }
}
