package com.action;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.serviceimpl.HibernateService;
import com.serviceimpl.HibernateUtil;
import com.util.DateUtil;
import com.dao.CompOffConstants;
import com.util.CompOffConfig;
import com.util.SqlUtil;

/**
 * Admin reports (read only). All queries go through com.serviceimpl.HibernateService:
 * dates are bound as parameters (getListParameterDate), ids come from validated Integers (SqlUtil.number),
 * and only Status = 'A' rows are reported.
 * Each row is returned as a column-name -> value map, so the JSPs can use the column names directly.
 * Native SQL selecting the same column name twice (e.g. staff and approver name) must alias one of them.
 */
public class ReportManager {

    /** Most rows the Comp Off History page shows (newest first). */
    public static final int HISTORY_LIMIT = 500;

    /**
     * Status shown in reports: Pending becomes "In Process" once any level has approved (or the last one is staged);
     * Available (approved, not used yet) is shown as "Approved".
     */
    private static final String IN_PROCESS_HW = "CAST(CASE WHEN h.holidayWorkingStatus = 'Available' THEN 'Approved'"
            + " WHEN h.holidayWorkingStatus = 'Pending' AND ("
            + " EXISTS (SELECT 1 FROM CompOffBalanceDetailsHistory x WHERE x.keyID = CONCAT('HW-', h.HWRID) AND x.historyAction = 'Approved' AND x.status = 'A')"
            + " OR EXISTS (SELECT 1 FROM MultiHolidayApprovals x WHERE x.HWID = h.HWRID AND x.approverStatus = 'In_Progress' AND x.status = 'A'))"
            + " THEN 'In Process' ELSE h.holidayWorkingStatus END AS CHAR(20))";
    private static final String IN_PROCESS_CO = "CAST(CASE WHEN c.leaveStatus = 'Pending' AND ("
            + " EXISTS (SELECT 1 FROM CompOffBalanceDetailsHistory x WHERE x.keyID = CONCAT('CO-', c.COID) AND x.historyAction = 'Approved' AND x.status = 'A')"
            + " OR EXISTS (SELECT 1 FROM MultiCompOffApprovals x WHERE x.COID = c.COID AND x.approverStatus = 'In_Progress' AND x.status = 'A'))"
            + " THEN 'In Process' ELSE c.leaveStatus END AS CHAR(20))";

    private static final String DB_ERROR = "Something went wrong. Please try again, and contact the admin if it keeps happening.";

    private final HibernateService hs = new HibernateUtil();

    // =====================================================================
    // Filters
    // =====================================================================

    /** Active teams for the Team filter. */
    public List<Map<String, Object>> teams() {
        return rows(sql("SELECT teamID, teamName FROM Teams WHERE status = 'A' AND companyID = " + company() + " ORDER BY teamName"),
                "teamId", "teamName");
    }

    /** Active staff (not Admin) for the Staff filter, with their team. */
    public List<Map<String, Object>> staff() {
        return rows(sql("SELECT s.staffID, " + CompOffConfig.nameSql("s") + ", s.employeeCode, " + CompOffConfig.teamIdSql("s") + " FROM Staffs s WHERE s.status = 'A'"
                + " AND s.companyID = " + company() + " AND NOT (" + CompOffConfig.userTypeSql("s", CompOffConstants.ROLE_ADMIN) + ") ORDER BY " + CompOffConfig.nameSql("s") + ""),
                "staffId", "staffName", "employeeCode", "teamId");
    }

    // =====================================================================
    // My Team Reports (Approvers): Comp Off - Month View
    // =====================================================================

    /** Active members of one team (not Admin), by name: the approver's own full team. */
    public List<Map<String, Object>> teamMembers(Integer teamId) {
        return rows(sql("SELECT s.staffID, " + CompOffConfig.nameSql("s") + ", s.employeeCode FROM Staffs s WHERE s.status = 'A' AND s.companyID = " + company() + " AND s.teamID = "
                + SqlUtil.number(teamId) + " AND NOT (" + CompOffConfig.userTypeSql("s", CompOffConstants.ROLE_ADMIN) + ")"
                + " ORDER BY " + CompOffConfig.nameSql("s") + ", s.staffID"), "staffId", "staffName", "employeeCode");
    }

    /**
     * Comp Off requests of the given staff members with a leave date from..to: staff id, leave date, day type, duration
     * and the status shown ("In Process" as in the other reports); by staff and date, the latest request first in a day.
     */
    public List<Map<String, Object>> teamCompOff(List<Integer> staffIds, LocalDate from, LocalDate to) {
        if (staffIds.isEmpty()) {
            return new ArrayList<Map<String, Object>>();
        }
        StringBuilder in = new StringBuilder();
        for (Integer id : staffIds) {
            in.append(in.length() == 0 ? "" : ",").append(SqlUtil.number(id));
        }
        return rows(sql("SELECT c.staffID, c.leaveDate, c.leaveDayType, c.leaveDuration, " + IN_PROCESS_CO
                + " FROM CompensatoryOffRequest c WHERE c.status = 'A' AND c.staffID IN (" + in + ")"
                + " AND c.leaveDate BETWEEN " + SqlUtil.date(from) + " AND " + SqlUtil.date(to)
                + " ORDER BY c.staffID, c.leaveDate, c.COID DESC"), "staffId", "leaveDate", "dayType", "duration", "status");
    }

    // =====================================================================
    // 1. Comp Off Details Report
    // =====================================================================

    /** Active staff (not Admin) who have a Comp Off record (created with their first approved Holiday Intimation), with Allocated / Used / Balance. */
    public List<Map<String, Object>> compOffDetails() {
        return rows(sql("SELECT " + CompOffConfig.nameSql("s") + ", s.employeeCode, t.teamName, s.dateofJoining,"
                + " COALESCE(c.allocated, 0), COALESCE(c.used, 0), COALESCE(c.balance, 0)"
                + " FROM Staffs s"
                + " LEFT JOIN Teams t ON t.teamID = s.teamID"
                + " JOIN CompOffBalanceDetails c ON c.staffID = s.staffID AND c.status = 'A'"
                + " WHERE s.status = 'A' AND s.companyID = " + company() + " AND NOT (" + CompOffConfig.userTypeSql("s", CompOffConstants.ROLE_ADMIN) + ")"
                + " ORDER BY " + CompOffConfig.nameSql("s") + ""),
                "staffName", "employeeCode", "teamName", "dateOfJoining", "allocated", "used", "balance");
    }

    // =====================================================================
    // 1b. Comp Off Balance and Details Report
    // =====================================================================

    /**
     * Comp Off Balance and Details Report: the Comp Off Details rows (same staff, same figures) with the staffID for the
     * View Details link; only that staff member when staffId is given.
     */
    public List<Map<String, Object>> compOffBalance(Integer staffId) {
        return rows(sql("SELECT s.staffID, " + CompOffConfig.nameSql("s") + ", t.teamName, s.employeeCode, s.dateofJoining,"
                + " COALESCE(c.allocated, 0), COALESCE(c.used, 0), COALESCE(c.balance, 0)"
                + " FROM Staffs s"
                + " LEFT JOIN Teams t ON t.teamID = s.teamID"
                + " JOIN CompOffBalanceDetails c ON c.staffID = s.staffID AND c.status = 'A'"
                + " WHERE s.status = 'A' AND s.companyID = " + company() + " AND NOT (" + CompOffConfig.userTypeSql("s", CompOffConstants.ROLE_ADMIN) + ")"
                + (staffId == null ? "" : " AND s.staffID = " + SqlUtil.number(staffId))
                + " ORDER BY " + CompOffConfig.nameSql("s") + ", s.staffID"),
                "staffId", "staffName", "teamName", "employeeCode", "dateOfJoining", "allocated", "used", "balance");
    }

    /**
     * View Details of one staff member. First one row per approved holiday not used yet (HolidayRequests Approved /
     * Available: Comp Off date empty, "Not Applied"), newest worked date first; then one row per Comp Off applied
     * (CompensatoryOffRequest, status Pending, Approved or Completed), newest Comp Off date first. Holiday worked date of
     * a Comp Off = the date of the Holiday Intimation it uses (referenceID), else the request's own attendanceDate.
     */
    public List<Map<String, Object>> staffCompOffDetails(Integer staffId) {
        List<Map<String, Object>> list = rows(sql("SELECT h.HWRID, " + CompOffConfig.nameSql("s") + ", s.employeeCode, h.workRequestDate"
                + " FROM HolidayRequests h JOIN Staffs s ON s.staffID = h.staffID"
                + " WHERE h.staffID = " + SqlUtil.number(staffId) + " AND s.companyID = " + company()
                + " AND h.recordStatus = 'A' AND h.holidayApproveStatus = 'Approved' AND h.holidayWorkingStatus = 'Available'"
                + " ORDER BY h.workRequestDate DESC, h.HWRID DESC"),
                "holidayId", "staffName", "employeeCode", "workedDate");
        list.addAll(staffCompOffRows(staffId));
        return list;
    }

    /** The Comp Off rows of View Details (see staffCompOffDetails). */
    private List<Map<String, Object>> staffCompOffRows(Integer staffId) {
        List<Map<String, Object>> list = rows(sql("SELECT c.COID, " + CompOffConfig.nameSql("s") + ", s.employeeCode,"
                + " COALESCE(h.workRequestDate, c.attendanceDate), c.leaveDate, c.leaveNotes"
                + " FROM CompensatoryOffRequest c"
                + " JOIN Staffs s ON s.staffID = c.staffID"
                + " LEFT JOIN HolidayRequests h ON CAST(h.HWRID AS CHAR) = c.referenceID AND h.staffID = c.staffID"
                + " WHERE c.staffID = " + SqlUtil.number(staffId) + " AND s.companyID = " + company()
                + " AND c.status = 'A' AND c.leaveStatus IN ('Pending', 'Approved', 'Completed')"
                + " ORDER BY c.leaveDate DESC, c.COID DESC"),
                "id", "staffName", "employeeCode", "workedDate", "compOffDate", "leaveNotes");
        for (Map<String, Object> row : list) {
            Object leave = row.get("compOffDate"), worked = row.get("workedDate");
            if (leave instanceof java.util.Date) {
                row.put("notes", "Applying Comp Off for " + DateUtil.display((java.util.Date) leave)
                        + (worked instanceof java.util.Date ? " for the holiday worked on " + DateUtil.display((java.util.Date) worked) : ""));
                String own = CompOffManager.userNotes((String) row.get("leaveNotes"));
                row.put("staffNotes", own == null || own.trim().isEmpty() ? null : own.trim());
            }
        }
        return list;
    }

    // =====================================================================
    // 2. Holiday View Report
    // =====================================================================

    /** Intimations whose worked date is between from and to, optionally for one team / staff member; newest first. */
    public List<Map<String, Object>> holidayView(LocalDate from, LocalDate to, Integer teamId, Integer staffId) {
        List<Map<String, Object>> list = rows(byDates("SELECT h.HWRID, t.teamName, " + CompOffConfig.nameSql("s") + ", s.employeeCode,"
                + " h.workRequestDate, h.notes, " + IN_PROCESS_HW + ", h.approverID, h.holidayApproveStatus"
                + " FROM HolidayRequests h"
                + " JOIN Staffs s ON s.staffID = h.staffID"
                + " LEFT JOIN Teams t ON t.teamID = s.teamID"
                + " WHERE h.recordStatus = 'A' AND h.workRequestDate BETWEEN :fromDate AND :toDate" + scope(teamId, staffId)
                + " ORDER BY h.workRequestDate DESC, h.HWRID DESC", from, to),
                "id", "teamName", "staffName", "employeeCode", "workedDate", "notes", "status", "waitingWith", "approveStatus");
        addApprovers(list, "HW-");
        return list;
    }

    /**
     * Adds "approvers" to every row: one entry per approval step (level, approver name, Approved / Rejected) from
     * CompOffBalanceDetailsHistory, then the approver a Pending request is waiting with ("Pending").
     */
    private void addApprovers(List<Map<String, Object>> list, String keyPrefix) {
        if (list.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<String>();
        for (Map<String, Object> row : list) {
            keys.add("'" + keyPrefix + ((Number) row.get("id")).intValue() + "'");
        }
        Map<String, List<Map<String, Object>>> steps = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (Map<String, Object> a : rows(sql("SELECT h.keyID, " + detail("level") + ", " + CompOffConfig.nameSql("a") + ", h.historyAction"
                + " FROM CompOffBalanceDetailsHistory h" + ACTOR_JOIN
                + " WHERE h.status = 'A' AND h.historyAction IN ('Approved', 'Rejected') AND h.keyID IN (" + String.join(",", keys) + ")"
                + " ORDER BY h.createdDate, h.COHID"), "key", "level", "approverName", "approvalStatus")) {
            String key = (String) a.get("key");
            if (!steps.containsKey(key)) {
                steps.put(key, new ArrayList<Map<String, Object>>());
            }
            steps.get(key).add(a);
        }
        Map<Integer, String> names = new LinkedHashMap<Integer, String>();
        for (Map<String, Object> s : rows(sql("SELECT staffID, " + CompOffConfig.nameSql(null) + " FROM Staffs"), "id", "name")) {
            names.put(((Number) s.get("id")).intValue(), (String) s.get("name"));
        }
        for (Map<String, Object> row : list) {
            List<Map<String, Object>> chain = steps.get(keyPrefix + ((Number) row.get("id")).intValue());
            chain = chain == null ? new ArrayList<Map<String, Object>>() : chain;
            Object waiting = row.get("waitingWith");
            if ("Pending".equals(row.get("approveStatus")) && waiting != null) {
                Map<String, Object> p = new LinkedHashMap<String, Object>();
                p.put("level", null);
                p.put("approverName", names.get(((Number) waiting).intValue()));
                p.put("approvalStatus", "Pending");
                chain.add(p);
            }
            row.put("approvers", chain);
        }
    }

    // =====================================================================
    // 3. Comp Off View Report
    // =====================================================================

    /** Comp off requests created (requested) between from and to, optionally for one team / staff member; newest first. */
    public List<Map<String, Object>> compOffView(LocalDate from, LocalDate to, Integer teamId, Integer staffId) {
        List<Map<String, Object>> list = rows(byDates("SELECT c.COID, t.teamName, " + CompOffConfig.nameSql("s") + ", s.employeeCode, c.createdDate,"
                + " c.leaveDate, CASE c.leaveDayType WHEN 'FD' THEN 'Full day' WHEN 'HD' THEN 'Half day' ELSE c.leaveDayType END,"
                + " c.leaveNotes, " + IN_PROCESS_CO + ", CAST(TRIM(SUBSTRING_INDEX(c.leaveApproverID, '~~', -1)) AS UNSIGNED), c.leaveStatus"
                + " FROM CompensatoryOffRequest c"
                + " JOIN Staffs s ON s.staffID = c.staffID"
                + " LEFT JOIN Teams t ON t.teamID = s.teamID"
                + " WHERE c.status = 'A' AND c.createdDate >= :fromDate AND c.createdDate < :toDate" + scope(teamId, staffId)
                + " ORDER BY c.createdDate DESC, c.COID DESC", from, to.plusDays(1)),
                "id", "teamName", "staffName", "employeeCode", "requestedDate", "leaveDate", "dayType", "reason", "status",
                "waitingWith", "approveStatus");
        for (Map<String, Object> row : list) {
            row.put("reason", CompOffManager.userNotes((String) row.get("reason")));
        }
        addApprovers(list, "CO-");
        return list;
    }

    // =====================================================================
    // Admin: Apply Comp Off for Staff / Comp Off Cancel
    // =====================================================================

    /** History value of a row aliased x (JSON leaveHistoryActionCommands), as text. */
    private static String xDetail(String key) {
        return "CAST(CASE WHEN JSON_VALID(x.leaveHistoryActionCommands)"
                + " THEN JSON_UNQUOTE(JSON_EXTRACT(x.leaveHistoryActionCommands, '$." + key + "')) END AS CHAR(50))";
    }

    /**
     * Comp Off applied by an Admin (Apply Comp Off for Staff): requests whose Requested history row was made by someone
     * other than the staff member. Newest first, at most HISTORY_LIMIT; the current company only.
     */
    public List<Map<String, Object>> adminAppliedCompOffs() {
        List<Map<String, Object>> list = rows(sql("SELECT c.COID, c.leaveDate, " + CompOffConfig.nameSql("s") + ", s.employeeCode, c.attendanceDate,"
                + " c.leaveNotes, (SELECT " + CompOffConfig.nameSql("a") + " FROM CompOffBalanceDetailsHistory x JOIN Staffs a ON a.staffID = CAST("
                + xDetail("actorId") + " AS UNSIGNED) WHERE x.keyID = CONCAT('CO-', c.COID) AND x.historyAction = 'Approved' AND x.status = 'A'"
                + " ORDER BY x.COHID DESC LIMIT 1) AS ApprovedBy, c.createdDate, " + IN_PROCESS_CO
                + " FROM CompensatoryOffRequest c JOIN Staffs s ON s.staffID = c.staffID"
                + " WHERE c.status = 'A' AND s.companyID = " + company()
                + " AND EXISTS (SELECT 1 FROM CompOffBalanceDetailsHistory x WHERE x.keyID = CONCAT('CO-', c.COID) AND x.historyAction = 'Requested'"
                + "   AND x.status = 'A' AND " + xDetail("actorId") + " <> CAST(c.staffID AS CHAR))"
                + " ORDER BY c.createdDate DESC, c.COID DESC LIMIT " + HISTORY_LIMIT),
                "id", "leaveDate", "staffName", "employeeCode", "workedDate", "notes", "approvedBy", "appliedOn", "status");
        for (Map<String, Object> row : list) {
            row.put("notes", CompOffManager.userNotes((String) row.get("notes")));
        }
        return list;
    }

    /**
     * Comp Off Cancel: Pending (shown as In Process once a level approved or a decision is staged) and Approved requests
     * whose Comp Off date is between from and to, optionally for one team / staff member; by date, then name.
     */
    public List<Map<String, Object>> cancellableCompOffs(LocalDate from, LocalDate to, Integer teamId, Integer staffId) {
        List<Map<String, Object>> list = rows(byDates("SELECT c.COID, t.teamName, " + CompOffConfig.nameSql("s") + ", s.employeeCode, c.leaveDate,"
                + " c.attendanceDate, c.createdDate, c.leaveNotes, " + IN_PROCESS_CO
                + " FROM CompensatoryOffRequest c"
                + " JOIN Staffs s ON s.staffID = c.staffID"
                + " LEFT JOIN Teams t ON t.teamID = s.teamID"
                + " WHERE c.status = 'A' AND c.leaveStatus IN ('Pending', 'Approved')"
                + " AND c.leaveDate BETWEEN :fromDate AND :toDate" + scope(teamId, staffId)
                + " ORDER BY c.leaveDate, " + CompOffConfig.nameSql("s") + ", c.COID", from, to),
                "id", "teamName", "staffName", "employeeCode", "leaveDate", "workedDate", "appliedOn", "notes", "status");
        for (Map<String, Object> row : list) {
            row.put("notes", CompOffManager.userNotes((String) row.get("notes")));
        }
        return list;
    }

    // =====================================================================
    // 4. Comp Off History
    // =====================================================================

    /** A value kept in CompOffBalanceDetailsHistory.leaveHistoryActionCommands (JSON), as text; NULL if missing or not JSON. */
    private static String detail(String key) {
        return "CAST(CASE WHEN JSON_VALID(h.leaveHistoryActionCommands)"
                + " THEN JSON_UNQUOTE(JSON_EXTRACT(h.leaveHistoryActionCommands, '$." + key + "')) END AS CHAR(500))";
    }

    /** Join for the person who made the change (actorId in leaveHistoryActionCommands). */
    private static final String ACTOR_JOIN = " LEFT JOIN Staffs a ON a.staffID = CAST(" + detail("actorId") + " AS UNSIGNED)";

    /** The latest HISTORY_LIMIT CompOffBalanceDetailsHistory rows of all staff (both flows), newest first. */
    public List<Map<String, Object>> compOffHistory() {
        return rows(sql("SELECT " + CompOffConfig.nameSql("s") + ", s.employeeCode, h.leaveType, h.holidayWorkedDate, h.compOffLeaveDate, h.historyAction,"
                + " REPLACE(" + detail("prev") + ", '/', ' / ') AS PrevBalance, h.availableDays, h.usedDays, h.balanceDays, h.HRID, h.COID,"
                + " " + CompOffConfig.nameSql("a") + " AS ActorName, a.employeeCode AS ActorCode, h.createdDate, " + detail("remarks") + " AS notes"
                + " FROM CompOffBalanceDetailsHistory h"
                + " JOIN Staffs s ON s.staffID = h.staffID"
                + ACTOR_JOIN
                + " WHERE h.status = 'A' AND s.companyID = " + company()
                + " ORDER BY h.createdDate DESC, h.COHID DESC LIMIT " + HISTORY_LIMIT),
                "staffName", "employeeCode", "leaveType", "workedDate", "leaveDate", "action", "prevBalance",
                "newAllocated", "newUsed", "newBalance", "holidayWorkingId", "compOffRequestId", "actorName", "actorCode",
                "actionDate", "remarks");
    }

    /** Intimations for the "Holiday Intimation History" picker, newest first. */
    public List<Map<String, Object>> intimationChoices() {
        return rows(sql("SELECT h.HWRID, " + CompOffConfig.nameSql("s") + ", s.employeeCode, h.workRequestDate"
                + " FROM HolidayRequests h JOIN Staffs s ON s.staffID = h.staffID"
                + " WHERE h.recordStatus = 'A' AND s.companyID = " + company() + " ORDER BY h.HWRID DESC LIMIT " + HISTORY_LIMIT),
                "id", "staffName", "employeeCode", "workedDate");
    }

    /** History rows of one intimation (HRID), oldest first: its own events and those of the Comp Off request that used it. */
    public List<Map<String, Object>> holidayHistory(Integer holidayWorkingId) {
        return rows(sql("SELECT h.leaveType, h.historyAction, " + detail("from") + " AS FromStatus, " + detail("to") + " AS ToStatus,"
                + " " + detail("level") + " AS approvalLevel, " + CompOffConfig.nameSql("a") + ", a.employeeCode, " + detail("remarks") + " AS notes, h.createdDate"
                + " FROM CompOffBalanceDetailsHistory h" + ACTOR_JOIN
                + " WHERE h.status = 'A' AND h.HRID = " + SqlUtil.number(holidayWorkingId) + " AND " + CompOffConfig.companyStaffSql("h.staffID")
                + " ORDER BY h.createdDate, h.COHID"),
                "leaveType", "action", "previousStatus", "newStatus", "level", "actorName", "actorCode", "remarks", "actionDate");
    }

    // =====================================================================
    // 5. Holiday Intimation Usage Report
    // =====================================================================

    /**
     * Approved Holiday Intimations (not cancelled), optionally for one team / staff member, each with the live Comp Off
     * request that uses its day (CompensatoryOffRequest.referenceID = HWRID, Pending / Approved / Completed), if any.
     * Rejected and cancelled requests are left out: they gave the day back. Ordered by staff, newest worked date first.
     */
    public List<Map<String, Object>> holidayUsage(Integer teamId, Integer staffId) {
        return rows(sql("SELECT h.HWRID, t.teamName, " + CompOffConfig.nameSql("s") + ", s.employeeCode, h.workRequestDate,"
                + " CASE h.holidayWorkingStatus WHEN 'Available' THEN 'Approved' ELSE h.holidayWorkingStatus END,"
                + " c.COID, c.createdDate, c.leaveDate,"
                + " CASE c.leaveDayType WHEN 'FD' THEN 'Full day' WHEN 'HD' THEN 'Half day' ELSE c.leaveDayType END,"
                + " CASE WHEN c.COID IS NULL THEN NULL ELSE " + IN_PROCESS_CO + " END"
                + " FROM HolidayRequests h"
                + " JOIN Staffs s ON s.staffID = h.staffID"
                + " LEFT JOIN Teams t ON t.teamID = s.teamID"
                + " LEFT JOIN CompensatoryOffRequest c ON c.referenceID = CAST(h.HWRID AS CHAR) AND c.staffID = h.staffID"
                + "   AND c.status = 'A' AND c.leaveStatus IN ('Pending', 'Approved', 'Completed')"
                + " WHERE h.recordStatus = 'A' AND h.holidayApproveStatus = 'Approved'"
                + " AND h.holidayWorkingStatus IN ('Available', 'Used', 'Completed')" + scope(teamId, staffId)
                + " ORDER BY " + CompOffConfig.nameSql("s") + ", s.staffID, h.workRequestDate DESC, h.HWRID DESC"),
                "id", "teamName", "staffName", "employeeCode", "workedDate", "usage", "compOffId", "requestedDate",
                "leaveDate", "dayType", "compOffStatus");
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** The report's own company: s = the Staffs row of the request's staff member; then the team / staff filters. */
    private static String scope(Integer teamId, Integer staffId) {
        return " AND s.companyID = " + company()
                + (teamId == null ? "" : " AND s.teamID = " + SqlUtil.number(teamId))
                + (staffId == null ? "" : " AND s.staffID = " + SqlUtil.number(staffId));
    }

    /** The current company (the logged-in Admin's), as an SQL number. */
    private static String company() {
        return SqlUtil.number(CompOffConfig.getCompanyId());
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> sql(String query) {
        return check(hs.getSQLList(query));
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> byDates(String query, LocalDate from, LocalDate to) {
        List<Date> values = Arrays.<Date>asList(DateUtil.toDate(from), DateUtil.toDate(to));
        return check(hs.getListParameterDate(query, Arrays.asList("fromDate", "toDate"), values));
    }

    private static List<Object[]> check(List<Object[]> raw) {
        if (raw == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        return raw;
    }

    /** Object[] rows -> maps keyed by the given column names (single-column results arrive as plain values). */
    private static List<Map<String, Object>> rows(List<?> raw, String... names) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Object r : raw) {
            Object[] cols = r instanceof Object[] ? (Object[]) r : new Object[] {r};
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            for (int i = 0; i < names.length; i++) {
                m.put(names[i], cols[i]);
            }
            out.add(m);
        }
        return out;
    }
}
