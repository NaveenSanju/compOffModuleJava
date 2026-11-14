package com.action;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.dao.CompOffConstants;
import com.dao.CompensatoryOffRequest;
import com.dao.GeneralSettings;
import com.dao.HolidayRequests;
import com.dao.MultiCompOffApprovals;
import com.dao.MultiHolidayApprovals;
import com.dao.Staffs;
import com.serviceimpl.HibernateService;
import com.serviceimpl.HibernateUtil;
import com.util.CompOffConfig;
import com.util.DateUtil;
import com.util.SqlUtil;

/**
 * Approver workflows: approve / reject holiday intimations (HolidayRequests) and comp off requests
 * (CompensatoryOffRequest), level by level. There are no level tables: the request row holds the approver it is
 * waiting with (approverID / leaveApproverID), and the level is that approver's position in the staff member's
 * LeaveSettingsCompOff chain (or the HR step). Every decision is recorded in CompOffBalanceDetailsHistory.
 *   - A level that has a next level (the next entry of the LeaveSettingsCompOff chain) is applied at once, without a
 *     MultiHolidayApprovals / MultiCompOffApprovals row: the request moves to the next approver and stays Pending,
 *     shown as "In Process".
 *   - After the last LeaveSettingsCompOff level, an Active GeneralSettings HR approver ('holidayApprovalHR' /
 *     'compOffApprovalHR') is one more step, which is then the last level. Skipped when the HR person is the
 *     requester or already in the chain (nextApprover).
 *   - The last level's Approve, and a Reject at any level, write a Multi*Approvals row (In_Progress, "In Process",
 *     approvalAction 'Approve' / 'Reject'); all rows of one click share batchID (approver code + click time). The
 *     approval servlets (HolidayApprovalServlet / CompOffApprovalServlet) carry them out one request per run
 *     (processNextHoliday / processNextCompOff): an approval makes the intimation Available and adds one day to the
 *     balance / makes the comp off request Approved; a rejection makes the request Rejected (a comp off day goes back
 *     to the balance). A rejected request goes no further: no next level is ever asked.
 *   - The servlet also sets approved Comp Off requests whose leave date has passed to Completed, and their
 *     intimations too (completePastLeaves).
 * Each step is a conditional update, so running the same step again changes nothing.
 * Reject is staged at any level (as above) and ends the request when the servlet processes it.
 *
 * All database access goes through com.serviceimpl.HibernateService (see CompOffManager for the rules).
 */
public class ApprovalManager {

    private static final Logger LOG = Logger.getLogger(ApprovalManager.class.getName());
    private static final DateTimeFormatter BATCH_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String FINAL_STEP = "Final approval being processed";
    private static final String REJECT_STEP = "Rejection being processed";
    /** Staged rows looked at per servlet run (the oldest first); at most one of them is processed. */
    private static final int NEXT_WINDOW = 50;
    private static final String DB_ERROR = "Something went wrong. Please try again, and contact the admin if it keeps happening.";

    private final HibernateService hs = new HibernateUtil();
    private final CompOffManager manager = new CompOffManager();
    private final GeneralSettingsManager settings = new GeneralSettingsManager();
    /** HolidayRequestApprovers / CompensatoryOffApprovers: who each request waits with, one row per approval step. */
    private final ApproverSteps steps = new ApproverSteps();
    /** When set: the lists (and so the decisions) only hold requests of the staff it accepts (by staffID). */
    private java.util.function.Predicate<Integer> requesterFilter;

    /** Approval pages of a non-Admin approver: only requests of staff who may use the module (ModuleAccess). Null = all. */
    public void onlyRequesters(java.util.function.Predicate<Integer> filter) {
        requesterFilter = filter;
    }

    /** One line of an approval page. id = HWRID (intimation) / COID (comp off request). */
    public static class Row {
        public final Integer id;
        public final String staffName;
        public final String employeeCode;
        public final Date date;
        public final String shiftOrDayType;
        public final Date workedDate;
        public final String reason;
        public final Date appliedOn;
        Integer approvalLevel;
        String nextStep;
        /** In Process list: a Reject is staged (shown as "Rejected - In Process"). */
        boolean rejecting;

        Row(Object[] r) {
            id = ((Number) r[0]).intValue();
            staffName = (String) r[1];
            employeeCode = (String) r[2];
            date = (Date) r[3];
            shiftOrDayType = r[4] == null ? null : r[4].toString();
            workedDate = (Date) r[5];
            reason = CompOffManager.userNotes((String) r[6]);
            appliedOn = (Date) r[7];
        }

        public Integer getId() { return id; }
        public String getStaffName() { return staffName; }
        public String getEmployeeCode() { return employeeCode; }
        public Date getDate() { return date; }
        public String getShiftOrDayType() { return shiftOrDayType; }
        public Date getWorkedDate() { return workedDate; }
        public String getReason() { return reason; }
        public Integer getApprovalLevel() { return approvalLevel; }
        public Date getAppliedOn() { return appliedOn; }
        public String getNextStep() { return nextStep; }
        public boolean isRejecting() { return rejecting; }
    }

    /** Result of a bulk decision. */
    public static class Outcome {
        public int done;
        public int skipped;
    }

    // =====================================================================
    // Approval page lists
    // =====================================================================

    /** SQL: the actorId / level kept in a CompOffBalanceDetailsHistory row's JSON. */
    private static String json(String alias, String key) {
        return "CASE WHEN JSON_VALID(" + alias + ".leaveHistoryActionCommands) THEN JSON_UNQUOTE(JSON_EXTRACT(" + alias
                + ".leaveHistoryActionCommands, '$." + key + "')) END";
    }

    /** SQL: an 'Approved' history row of the key ('HW-' / 'CO-' + id column) made by this approver. */
    private static String approvedBy(String keyExpr, Integer approverId) {
        return "SELECT 1 FROM CompOffBalanceDetailsHistory x WHERE x.keyID = " + keyExpr + " AND x.historyAction = 'Approved'"
                + " AND x.status = 'A' AND " + json("x", "actorId") + " = " + SqlUtil.text(String.valueOf(approverId));
    }

    /**
     * Holiday intimations waiting for this approver's decision: a Pending HolidayRequestApprovers step with this
     * approver, nothing staged. Column 9 = the step's level.
     */
    public List<Row> holidayRows(Integer approverId) {
        syncSteps(CompOffConstants.LT_HOLIDAY);
        List<Row> rows = rows("SELECT h.HWRID, " + CompOffConfig.nameSql("s") + ", s.employeeCode, h.workRequestDate, h.shiftType, CAST(NULL AS DATE),"
                + " h.notes, h.createdDate, h.staffID, " + ApproverSteps.pendingLevel(CompOffConstants.LT_HOLIDAY, "h.HWRID")
                + " FROM HolidayRequests h JOIN Staffs s ON s.staffID = h.staffID"
                + " WHERE " + ApproverSteps.waitingWith(CompOffConstants.LT_HOLIDAY, "h.HWRID", approverId)
                + " AND h.holidayWorkingStatus = 'Pending' AND h.holidayApproveStatus = 'Pending' AND h.recordStatus = 'A'"
                + " AND NOT EXISTS (SELECT 1 FROM MultiHolidayApprovals m WHERE m.HWID = h.HWRID"
                + "   AND m.approverStatus = 'In_Progress' AND m.status = 'A')"
                + " ORDER BY h.createdDate, h.HWRID", CompOffConstants.LT_HOLIDAY, approverId, false);
        return rows;
    }

    /** Intimations this approver has approved that are not final yet ("In Process"), with what they wait for. */
    public List<Row> holidayInProcess(Integer approverId) {
        String me = SqlUtil.number(approverId);
        return rows("SELECT h.HWRID, " + CompOffConfig.nameSql("s") + ", s.employeeCode, h.workRequestDate, h.shiftType, CAST(NULL AS DATE),"
                + " h.notes, h.createdDate, h.staffID, h.approverID,"
                + " (SELECT MAX(m.approvalLevel) FROM MultiHolidayApprovals m WHERE m.HWID = h.HWRID AND m.approverID = " + me
                + "   AND m.approverStatus = 'In_Progress' AND m.status = 'A'),"
                + " (SELECT MAX(CAST(" + json("x", "level") + " AS UNSIGNED)) FROM CompOffBalanceDetailsHistory x"
                + "   WHERE x.keyID = CONCAT('HW-', h.HWRID) AND x.historyAction = 'Approved' AND x.status = 'A'"
                + "   AND " + json("x", "actorId") + " = " + SqlUtil.text(String.valueOf(approverId)) + "),"
                + " EXISTS (SELECT 1 FROM MultiHolidayApprovals m WHERE m.HWID = h.HWRID AND m.approverStatus = 'In_Progress' AND m.status = 'A'),"
                + " (SELECT MAX(m.approvalAction) FROM MultiHolidayApprovals m WHERE m.HWID = h.HWRID AND m.approverStatus = 'In_Progress' AND m.status = 'A')"
                + " FROM HolidayRequests h JOIN Staffs s ON s.staffID = h.staffID"
                + " WHERE h.holidayWorkingStatus = 'Pending' AND h.holidayApproveStatus = 'Pending' AND h.recordStatus = 'A'"
                + " AND (EXISTS (SELECT 1 FROM MultiHolidayApprovals m WHERE m.HWID = h.HWRID AND m.approverID = " + me
                + "   AND m.approverStatus = 'In_Progress' AND m.status = 'A')"
                + "  OR EXISTS (" + approvedBy("CONCAT('HW-', h.HWRID)", approverId) + "))"
                + " ORDER BY h.createdDate, h.HWRID", CompOffConstants.LT_HOLIDAY, approverId, true);
    }

    /**
     * Comp off requests waiting for this approver's decision: a Pending CompensatoryOffApprovers step with this
     * approver, nothing staged. Column 9 = the step's level.
     */
    public List<Row> compOffRows(Integer approverId) {
        syncSteps(CompOffConstants.LT_COMP_OFF);
        return rows("SELECT c.COID, " + CompOffConfig.nameSql("s") + ", s.employeeCode, c.leaveDate,"
                + " CASE c.leaveDayType WHEN 'FD' THEN 'Full day' WHEN 'HD' THEN 'Half day' ELSE c.leaveDayType END,"
                + " c.attendanceDate, c.leaveNotes, c.createdDate, c.staffID, " + ApproverSteps.pendingLevel(CompOffConstants.LT_COMP_OFF, "c.COID")
                + " FROM CompensatoryOffRequest c JOIN Staffs s ON s.staffID = c.staffID"
                + " WHERE " + ApproverSteps.waitingWith(CompOffConstants.LT_COMP_OFF, "c.COID", approverId) + " AND c.leaveStatus = 'Pending' AND c.status = 'A'"
                + " AND NOT EXISTS (SELECT 1 FROM MultiCompOffApprovals m WHERE m.COID = c.COID"
                + "   AND m.approverStatus = 'In_Progress' AND m.status = 'A')"
                + " ORDER BY c.createdDate, c.COID", CompOffConstants.LT_COMP_OFF, approverId, false);
    }

    /** Comp off requests this approver has approved that are not final yet ("In Process"), with what they wait for. */
    public List<Row> compOffInProcess(Integer approverId) {
        String me = SqlUtil.number(approverId);
        return rows("SELECT c.COID, " + CompOffConfig.nameSql("s") + ", s.employeeCode, c.leaveDate,"
                + " CASE c.leaveDayType WHEN 'FD' THEN 'Full day' WHEN 'HD' THEN 'Half day' ELSE c.leaveDayType END,"
                + " c.attendanceDate, c.leaveNotes, c.createdDate, c.staffID,"
                + " CAST(TRIM(SUBSTRING_INDEX(c.leaveApproverID, '~~', -1)) AS UNSIGNED),"
                + " (SELECT MAX(m.approvalLevel) FROM MultiCompOffApprovals m WHERE m.COID = c.COID AND m.approverID = " + me
                + "   AND m.approverStatus = 'In_Progress' AND m.status = 'A'),"
                + " (SELECT MAX(CAST(" + json("x", "level") + " AS UNSIGNED)) FROM CompOffBalanceDetailsHistory x"
                + "   WHERE x.keyID = CONCAT('CO-', c.COID) AND x.historyAction = 'Approved' AND x.status = 'A'"
                + "   AND " + json("x", "actorId") + " = " + SqlUtil.text(String.valueOf(approverId)) + "),"
                + " EXISTS (SELECT 1 FROM MultiCompOffApprovals m WHERE m.COID = c.COID AND m.approverStatus = 'In_Progress' AND m.status = 'A'),"
                + " (SELECT MAX(m.approvalAction) FROM MultiCompOffApprovals m WHERE m.COID = c.COID AND m.approverStatus = 'In_Progress' AND m.status = 'A'),"
                + " (SELECT MAX(m.approvedDate) FROM MultiCompOffApprovals m WHERE m.COID = c.COID AND m.approverStatus = 'In_Progress' AND m.status = 'A')"
                + " FROM CompensatoryOffRequest c JOIN Staffs s ON s.staffID = c.staffID"
                + " WHERE c.leaveStatus = 'Pending' AND c.status = 'A'"
                + " AND (EXISTS (SELECT 1 FROM MultiCompOffApprovals m WHERE m.COID = c.COID AND m.approverID = " + me
                + "   AND m.approverStatus = 'In_Progress' AND m.status = 'A')"
                + "  OR EXISTS (" + approvedBy("CONCAT('CO-', c.COID)", approverId) + "))"
                + " ORDER BY c.createdDate, c.COID", CompOffConstants.LT_COMP_OFF, approverId, true);
    }

    /**
     * Rows of an approval page. Columns 0-7 as in Row, 8 = the requester's staffID; In Process lists also have
     * 9 = the approver it waits with now, 10 = the level this approver staged, 11 = the level this approver approved,
     * 12 = whether a decision is staged (by anyone), 13 = its approvalAction; comp off: 14 = when it was clicked.
     */
    @SuppressWarnings("unchecked")
    private List<Row> rows(String sql, String leaveType, Integer approverId, boolean inProcess) {
        List<Object[]> raw = hs.getSQLList(sql);
        if (raw == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        Map<Integer, String> names = new HashMap<Integer, String>();
        List<Row> out = new ArrayList<Row>();
        for (Object[] r : raw) {
            int staffId = ((Number) r[8]).intValue();
            if (requesterFilter != null && !requesterFilter.test(staffId)) {
                continue;
            }
            Row row = new Row(r);
            if (!inProcess) {
                // the level of the approver's Pending step (worked out from the chain only if the step has none)
                row.approvalLevel = r.length > 9 && r[9] != null ? ((Number) r[9]).intValue()
                        : levelFor(leaveType, staffId, approverId, (CompOffConstants.LT_HOLIDAY.equals(leaveType) ? "HW-" : "CO-") + row.id);
            } else {
                Integer staged = r[10] == null ? null : ((Number) r[10]).intValue();
                row.approvalLevel = staged != null ? staged : r[11] == null ? null : ((Number) r[11]).intValue();
                if (r[12] != null && ((Number) r[12]).intValue() > 0) {
                    row.rejecting = CompOffConstants.ACTION_REJECT.equals(r[13]);
                    row.nextStep = row.rejecting ? REJECT_STEP : FINAL_STEP;
                    if (CompOffConstants.LT_COMP_OFF.equals(leaveType) && r.length > 14 && row.date != null) {
                        LocalDate leave = DateUtil.toLocalDate(row.date);
                        if (!autoEligible(leave, (Date) r[14])) {
                            row.nextStep = leave.isBefore(CompOffConfig.today().withDayOfMonth(1))
                                    ? "Waiting for Admin processing (" + monthName(leave) + ")"
                                    : "Processed in " + monthName(leave);
                        }
                    }
                } else if (r[9] != null) {
                    int waiting = ((Number) r[9]).intValue();
                    if (!names.containsKey(waiting)) {
                        Staffs s = manager.findStaff(waiting);
                        names.put(waiting, s == null ? "#" + waiting : s.getStaffName());
                    }
                    row.nextStep = "Waiting for level " + manager.levelOf(staffId, waiting) + " - " + names.get(waiting);
                } else {
                    row.nextStep = CompOffConstants.IN_PROCESS;
                }
            }
            out.add(row);
        }
        return out;
    }

    /** Level of each waiting row (its Pending step), by request id. */
    private static Map<Integer, Integer> levels(List<Row> waiting) {
        Map<Integer, Integer> m = new HashMap<Integer, Integer>();
        for (Row r : waiting) {
            m.put(r.id, r.approvalLevel);
        }
        return m;
    }

    /** Ids from the list that are still waiting for this approver (anything else is skipped). */
    private Set<Integer> eligible(List<Row> waiting, List<Integer> ids) {
        Set<Integer> open = new LinkedHashSet<Integer>();
        for (Row r : waiting) {
            open.add(r.id);
        }
        Set<Integer> out = new LinkedHashSet<Integer>();
        for (Integer id : ids) {
            if (open.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static String batchId(Staffs approver) {
        return approver.getEmployeeCode() + "-" + LocalDateTime.now(CompOffConfig.getZone()).format(BATCH_TIME);
    }

    /**
     * Approver of the level after `level`: the next LeaveSettingsCompOff level; after the last chain level, the additional
     * HR approver of the flow (GeneralSettings 'holidayApprovalHR' / 'compOffApprovalHR', Status 'A'), unless the HR
     * person is the requester, already in the chain, or the approver who is deciding now (the HR step itself).
     * An HR person who is inactive or has no Approver role is replaced by the Admin. Null = `level` is the last level.
     */
    private Staffs nextApprover(String leaveTypeName, Integer staffId, int level, Integer currentApproverId) {
        List<Integer> chain = manager.chainIds(staffId);
        if (level < chain.size()) {
            Staffs next = manager.approverAtLevel(staffId, level + 1);
            if (next != null) {
                return next;
            }
            // a later chain level whose approver is not valid (inactive, or the requester) is never skipped: the Admin takes it
            return adminInstead(currentApproverId, leaveTypeName + " level " + (level + 1) + " of staff " + staffId + " has no valid approver");
        }
        if (level > chain.size()) {
            return null;                          // this was the HR step (or the Admin standing in for it): the last level
        }
        GeneralSettings hr = settings.latest(GeneralSettingsManager.hrParam(leaveTypeName), true);
        if (hr == null) {
            return null;                          // no active HR setting: the chain is the whole workflow
        }
        Integer named = hr.valueStaffId();       // the staff member the value names, whatever its role prefix
        if (named != null && (named.equals(staffId) || chain.contains(named) || named.equals(currentApproverId))) {
            return null;                          // the HR person is the requester or already approved in the chain
        }
        Integer hrId = hr.hrStaffId();            // must be 'HR~~<staffId>' to be used as the HR step
        Staffs person = hrId == null ? null : manager.findStaff(hrId);
        if (person != null && settings.isHrApprover(person)) {
            return person;
        }
        // an active HR setting with a malformed value or a person who cannot approve as HR never bypasses the HR step
        return adminInstead(currentApproverId, "HR setting " + hr.getParamName() + " (" + hr.getParamValue() + ") is not a valid HR approver");
    }

    /** The Admin when a required approver is missing; null when the Admin is the one approving now (that covers the step). */
    private Staffs adminInstead(Integer currentApproverId, String why) {
        Integer adminId = defaultAdminId();
        if (adminId == null || adminId.equals(currentApproverId)) {
            return null;
        }
        LOG.warning(why + "; Admin " + adminId + " used instead");
        return manager.findStaff(adminId);
    }

    /**
     * Level the approver acts at: their position in the staff member's chain; for an approver outside the chain (the HR
     * person, or the Admin standing in for a missing approver) the step after the approvals already recorded for the
     * request (keyID 'HW-n' / 'CO-n'), so an Admin standing in for level 1 of 2 still hands over to level 2.
     */
    private int levelFor(String leaveTypeName, Integer staffId, Integer approverId, String keyId) {
        int i = manager.chainIds(staffId).indexOf(approverId);
        if (i >= 0) {
            return i + 1;
        }
        Integer done = manager.firstId("SELECT COUNT(*) FROM CompOffBalanceDetailsHistory WHERE keyID = " + SqlUtil.text(keyId)
                + " AND historyAction = 'Approved' AND status = 'A'");
        return (done == null ? 0 : done) + 1;
    }

    /** True when the level comes after the staff member's LeaveSettingsCompOff chain, i.e. it is the additional HR level. */
    private boolean isHrLevel(String leaveTypeName, Integer staffId, int level) {
        return level > manager.chainIds(staffId).size();
    }

    /** Remarks for a history row, with a note in front ("HR approval: ..."); either may be null. */
    private static String withNote(String note, String remarks) {
        if (note == null) {
            return remarks;
        }
        return remarks == null ? note : note + ": " + remarks;
    }

    // =====================================================================
    // Holiday intimation: approve / reject
    // =====================================================================

    /**
     * Approves the ticked HWRIDs. A level with a next level hands over to the next approver at once; the last level
     * writes one MultiHolidayApprovals row each and stays In_Progress for the approval servlet.
     */
    public Outcome approveHoliday(Staffs approver, List<Integer> ids, String remarks) {
        Outcome out = new Outcome();
        List<Row> waiting = holidayRows(approver.getStaffID());
        Map<Integer, Integer> stepLevel = levels(waiting);
        Set<Integer> ok = eligible(waiting, ids);
        out.skipped = ids.size() - ok.size();
        String batchId = batchId(approver);
        for (Integer hwId : ok) {
            HolidayRequests h = (HolidayRequests) hs.get(HolidayRequests.class.getName(), hwId);
            int level = stepLevel.get(hwId);
            Staffs next = nextApprover(CompOffConstants.LT_HOLIDAY, h.getStaffID(), level, approver.getStaffID());
            if (next != null) {
                // not the last level: hand over to the next approver now (no MultiHolidayApprovals row)
                if (handOverHoliday(h, approver.getStaffID(), level, next, remarks, null)) {
                    out.done++;
                } else {
                    out.skipped++;
                }
                continue;
            }
            // last level: staged In_Progress ("In Process") until the approval servlet runs
            if (stageHoliday(batchId, level, hwId, approver.getStaffID(), remarks, CompOffConstants.ACTION_APPROVE)) {
                out.done++;
            } else {
                out.skipped++;
            }
        }
        return out;
    }

    /**
     * Writes the staged (In_Progress) decision for an intimation. Only one decision can be staged per intimation:
     * when another click staged one at the same moment, the oldest stays and this one is discarded (false).
     */
    private boolean stageHoliday(String batchId, int level, Integer hwId, Integer approverId, String remarks, String action) {
        MultiHolidayApprovals m = new MultiHolidayApprovals();
        m.setBatchID(batchId);
        m.setApprovalLevel(level);
        m.setHWID(hwId);
        m.setApproverID(approverId);
        m.setApproverStatus(CompOffConstants.STAGED_IN_PROGRESS);
        m.setApprovalAction(action);
        m.setNotes(remarks);
        m.setApprovedDate(new Date());
        m.setStatus(CompOffConstants.ACTIVE);
        hs.save(m);
        if (m.getMHAID() == null) {
            return false;
        }
        if (!manager.isOldest("SELECT MIN(MHAID) FROM MultiHolidayApprovals WHERE HWID = "
                + SqlUtil.number(hwId) + " AND approverStatus = 'In_Progress' AND status = 'A'", m.getMHAID())) {
            discardStaged("MultiHolidayApprovals", SqlUtil.number(m.getMHAID()));
            return false;
        }
        return true;
    }

    /**
     * Moves the intimation from `fromApprover` (at `level`) to the next approver, once: only when it is still waiting
     * with `fromApprover`. Records the 'Approved' history row. False when it was already decided.
     */
    private boolean handOverHoliday(HolidayRequests h, Integer fromApprover, int level, Staffs next, String remarks, Date decidedAt) {
        if (hs.updateSQLQuery1("UPDATE HolidayRequests SET approverID = " + SqlUtil.number(next.getStaffID())
                + ", modifiedDate = NOW() WHERE HWRID = " + SqlUtil.number(h.getHWRID())
                + " AND approverID = " + SqlUtil.number(fromApprover)
                + " AND holidayWorkingStatus = 'Pending' AND holidayApproveStatus = 'Pending' AND recordStatus = 'A'") != 1) {
            return false;
        }
        // this level's step is Approved; the next level's step (the next chain level, or HR) starts now
        steps.close(CompOffConstants.LT_HOLIDAY, h.getHWRID(), ApproverSteps.APPROVED, remarks, decidedAt);
        steps.open(CompOffConstants.LT_HOLIDAY, h.getHWRID(), h.getStaffID(), level + 1, next.getStaffID());
        String note = isHrLevel(CompOffConstants.LT_HOLIDAY, h.getStaffID(), level + 1) ? "Sent for HR approval to " + next.getStaffName()
                : null;
        h.setApproverID(fromApprover);
        h.setCurrentApprovalLevel(level);
        manager.holidayHistory(h, CompOffConstants.HIST_APPROVED, CompOffConstants.HW_PENDING, CompOffConstants.HW_PENDING,
                fromApprover, withNote(note, remarks), BigDecimal.ZERO,
                HistoryComment.remarks(remarks).sentTo(next.getStaffName(), isHrLevel(CompOffConstants.LT_HOLIDAY, h.getStaffID(), level + 1)));
        boolean hr = isHrLevel(CompOffConstants.LT_HOLIDAY, h.getStaffID(), level + 1);
        notifier().holidayLevelApproved(h, fromApprover, level, next, hr, remarks);
        notifier().holidayApproval(next, h, level + 1, hr);        // the next approver must act now
        return true;
    }

    /**
     * Processes one staged (last level) holiday approval (pattern C). Safe to run more than once:
     * every step only changes rows that are still in the expected state.
     */
    public boolean processHoliday(MultiHolidayApprovals m) {
        String mid = SqlUtil.number(m.getMHAID());
        try {
            HolidayRequests h = (HolidayRequests) hs.get(HolidayRequests.class.getName(), m.getHWID());
            if (h == null || !CompOffConstants.ACTIVE.equals(h.getRecordStatus()) || !CompOffConstants.HW_PENDING.equals(h.getHolidayApproveStatus())
                    || !CompOffConstants.HW_PENDING.equals(h.getHolidayWorkingStatus())) {
                // already final (Approved), or cancelled by the staff / rejected before it was processed
                boolean approved = h != null && "Approved".equals(h.getHolidayApproveStatus());
                finishStaged("MultiHolidayApprovals", mid, approved ? "Completed" : "Cancelled");
                return true;
            }
            if (m.isReject()) {
                return processHolidayReject(m, mid);
            }
            int level = m.getApprovalLevel() == null ? 1 : m.getApprovalLevel();
            Staffs next = nextApprover(CompOffConstants.LT_HOLIDAY, h.getStaffID(), level, m.getApproverID());
            if (next != null) {
                // a level was added (chain or HR) after this was staged: the next level must approve
                if (!handOverHoliday(h, m.getApproverID(), level, next, m.getNotes(), m.getApprovedDate())) {
                    LOG.warning("Staged holiday approval " + m.getMHAID() + ": intimation no longer waits with approver " + m.getApproverID());
                }
                finishStaged("MultiHolidayApprovals", mid, "Completed");
                return true;
            }
            String hid = SqlUtil.number(h.getHWRID());
            manager.ensureBalanceRow(h.getStaffID());
            // final approval: the intimation becomes Available and one Comp Off day is added to the balance in ONE
            // statement, so a crash can never leave an approved intimation without its day (or the other way round)
            int changed = hs.updateSQLQuery1("UPDATE HolidayRequests h JOIN CompOffBalanceDetails b ON b.staffID = h.staffID AND b.status = 'A'"
                    + " SET h.holidayWorkingStatus = 'Available', h.holidayApproveStatus = 'Approved', h.approverID = " + SqlUtil.number(m.getApproverID())
                    + ", h.modifiedDate = NOW(), b.allocated = b.allocated + 1, b.balance = b.balance + 1"
                    + " WHERE h.HWRID = " + hid + " AND h.holidayWorkingStatus = 'Pending' AND h.holidayApproveStatus = 'Pending' AND h.recordStatus = 'A'");
            if (changed == 0) {
                // changed meanwhile (e.g. cancelled): the next run sees its new state and closes this row
                LOG.warning("Staged holiday approval " + m.getMHAID() + ": intimation changed while processing; retried on the next run");
                return false;
            }
            if (changed != 2) {
                throw new IllegalStateException("Unexpected update count " + changed + " for staged holiday approval " + m.getMHAID());
            }
            {
                steps.close(CompOffConstants.LT_HOLIDAY, h.getHWRID(), ApproverSteps.APPROVED, m.getNotes(), m.getApprovedDate());
                h.setApproverID(m.getApproverID());
                h.setCurrentApprovalLevel(level);
                manager.holidayHistory(h, CompOffConstants.HIST_APPROVED, CompOffConstants.HW_PENDING, CompOffConstants.HW_PENDING,
                        m.getApproverID(), withNote(isHrLevel(CompOffConstants.LT_HOLIDAY, h.getStaffID(), level) ? "HR approval" : null,
                                m.getNotes()), BigDecimal.ZERO, HistoryComment.remarks(m.getNotes()).at(m.getApprovedDate()));
                h.setHolidayWorkingStatus(CompOffConstants.HW_AVAILABLE);
                manager.holidayHistory(h, CompOffConstants.HIST_ALLOCATED, CompOffConstants.HW_PENDING, CompOffConstants.HW_AVAILABLE,
                        m.getApproverID(), m.getNotes(), BigDecimal.ONE, HistoryComment.none());
                notifier().holidayApproved(h, m.getApproverID(), m.getNotes());
            }
            finishStaged("MultiHolidayApprovals", mid, "Completed");
            return true;
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Staged holiday approval " + m.getMHAID() + " failed; left In_Progress for retry", e);
            return false;
        }
    }

    /** A staged Reject: the intimation becomes Rejected (no Comp Off day); history and email as for a direct reject. */
    private boolean processHolidayReject(MultiHolidayApprovals m, String mid) {
        Integer hwId = m.getHWID();
        if (hs.updateSQLQuery1("UPDATE HolidayRequests SET holidayWorkingStatus = 'Rejected', holidayApproveStatus = 'Rejected',"
                + " modifiedDate = NOW() WHERE HWRID = " + SqlUtil.number(hwId) + " AND approverID = " + SqlUtil.number(m.getApproverID())
                + " AND holidayWorkingStatus = 'Pending' AND holidayApproveStatus = 'Pending' AND recordStatus = 'A'") != 1) {
            LOG.warning("Staged holiday rejection " + m.getMHAID() + ": intimation changed while processing; retried on the next run");
            return false;
        }
        steps.close(CompOffConstants.LT_HOLIDAY, hwId, ApproverSteps.REJECTED, m.getNotes(), m.getApprovedDate());
        HolidayRequests h = (HolidayRequests) hs.get(HolidayRequests.class.getName(), hwId);
        manager.holidayHistory(h, CompOffConstants.HIST_REJECTED, CompOffConstants.HW_PENDING, CompOffConstants.HW_REJECTED,
                m.getApproverID(), m.getNotes(), BigDecimal.ZERO, HistoryComment.remarks(m.getNotes()).at(m.getApprovedDate()));
        notifier().holidayRejected(h, m.getApproverID(), m.getNotes());
        finishStaged("MultiHolidayApprovals", mid, "Completed");
        return true;
    }

    /**
     * Rejects each ticked HWRID (any level): staged In_Progress ("Rejected - In Process") until the holiday approval
     * servlet makes the intimation Rejected (no Comp Off day). The request goes to no further level.
     */
    public Outcome rejectHoliday(Staffs approver, List<Integer> ids, String remarks) {
        Outcome out = new Outcome();
        List<Row> waiting = holidayRows(approver.getStaffID());
        Map<Integer, Integer> stepLevel = levels(waiting);
        Set<Integer> ok = eligible(waiting, ids);
        out.skipped = ids.size() - ok.size();
        String batchId = batchId(approver);
        for (Integer hwId : ok) {
            int level = stepLevel.get(hwId);
            if (stageHoliday(batchId, level, hwId, approver.getStaffID(), remarks, CompOffConstants.ACTION_REJECT)) {
                out.done++;
            } else {
                out.skipped++;
            }
        }
        return out;
    }

    // =====================================================================
    // Comp off: approve / reject
    // =====================================================================

    /**
     * Approves the ticked COIDs. A level with a next level hands over to the next approver at once; the last level
     * writes one MultiCompOffApprovals row each and stays In_Progress for the approval servlet.
     */
    public Outcome approveCompOff(Staffs approver, List<Integer> ids, String remarks) {
        Outcome out = new Outcome();
        List<Row> waiting = compOffRows(approver.getStaffID());
        Map<Integer, Integer> stepLevel = levels(waiting);
        Set<Integer> ok = eligible(waiting, ids);
        out.skipped = ids.size() - ok.size();
        String batchId = batchId(approver);
        for (Integer coId : ok) {
            CompensatoryOffRequest r = (CompensatoryOffRequest) hs.get(CompensatoryOffRequest.class.getName(), coId);
            int level = stepLevel.get(coId);
            Staffs next = nextApprover(CompOffConstants.LT_COMP_OFF, r.getStaffID(), level, approver.getStaffID());
            if (next != null) {
                // not the last level: hand over to the next approver now (no MultiCompOffApprovals row)
                if (handOverCompOff(r, approver.getStaffID(), level, next, remarks, null)) {
                    out.done++;
                } else {
                    out.skipped++;
                }
                continue;
            }
            // last level: staged In_Progress ("In Process") until the approval servlet runs
            if (stageCompOff(batchId, level, r, approver.getStaffID(), remarks, CompOffConstants.ACTION_APPROVE)) {
                out.done++;
            } else {
                out.skipped++;
            }
        }
        return out;
    }

    /** Writes the staged (In_Progress) decision for a comp off request; one per request, the oldest wins (as stageHoliday). */
    private boolean stageCompOff(String batchId, int level, CompensatoryOffRequest r, Integer approverId, String remarks, String action) {
        MultiCompOffApprovals m = new MultiCompOffApprovals();
        m.setBatchID(batchId);
        m.setApprovalLevel(level);
        m.setCOID(r.getCOID());
        m.setStaffID(r.getStaffID());
        m.setApproverID(approverId);
        m.setApproverStatus(CompOffConstants.STAGED_IN_PROGRESS);
        m.setApprovalAction(action);
        m.setNotes(remarks);
        m.setApprovedDate(new Date());
        m.setStatus(CompOffConstants.ACTIVE);
        hs.save(m);
        if (m.getMCOAID() == null) {
            return false;
        }
        if (!manager.isOldest("SELECT MIN(MCOAID) FROM MultiCompOffApprovals WHERE COID = "
                + SqlUtil.number(r.getCOID()) + " AND approverStatus = 'In_Progress' AND status = 'A'", m.getMCOAID())) {
            discardStaged("MultiCompOffApprovals", SqlUtil.number(m.getMCOAID()));
            return false;
        }
        return true;
    }

    /**
     * Moves the request from `fromApprover` (at `level`) to the next approver, once: only when it is still waiting
     * with `fromApprover`. Records the 'Approved' history row. False when it was already decided.
     */
    private boolean handOverCompOff(CompensatoryOffRequest r, Integer fromApprover, int level, Staffs next, String remarks, Date decidedAt) {
        if (hs.updateSQLQuery1("UPDATE CompensatoryOffRequest c SET c.leaveApproverID = " + SqlUtil.text(manager.approverValue(next))
                + ", c.modifiedDate = NOW() WHERE c.COID = " + SqlUtil.number(r.getCOID())
                + " AND " + CompOffManager.waitingWith("c", fromApprover) + " AND c.leaveStatus = 'Pending' AND c.status = 'A'") != 1) {
            return false;
        }
        // this level's step is Approved; the next level's step (the next chain level, or HR) starts now
        steps.close(CompOffConstants.LT_COMP_OFF, r.getCOID(), ApproverSteps.APPROVED, remarks, decidedAt);
        steps.open(CompOffConstants.LT_COMP_OFF, r.getCOID(), r.getStaffID(), level + 1, next.getStaffID());
        r.setCurrentApprovalLevel(level);
        manager.compOffHistory(r, CompOffConstants.HIST_APPROVED, CompOffConstants.REQ_PENDING, CompOffConstants.REQ_PENDING,
                r.getAttendanceDate(), fromApprover, BigDecimal.ZERO,
                "Level " + level + " approved"
                        + (isHrLevel(CompOffConstants.LT_COMP_OFF, r.getStaffID(), level + 1) ? ", sent for HR approval to " + next.getStaffName() : "")
                        + (remarks == null ? "" : ": " + remarks),
                HistoryComment.remarks(remarks).sentTo(next.getStaffName(), isHrLevel(CompOffConstants.LT_COMP_OFF, r.getStaffID(), level + 1)));
        boolean hr = isHrLevel(CompOffConstants.LT_COMP_OFF, r.getStaffID(), level + 1);
        notifier().compOffLevelApproved(r, fromApprover, level, next, hr, remarks);
        notifier().compOffApproval(next, r, level + 1, hr);         // the next approver must act now
        return true;
    }

    /** Processes one staged (last level) comp off approval (pattern F). Safe to run more than once. */
    public boolean processCompOff(MultiCompOffApprovals m) {
        String mid = SqlUtil.number(m.getMCOAID());
        try {
            CompensatoryOffRequest r = (CompensatoryOffRequest) hs.get(CompensatoryOffRequest.class.getName(), m.getCOID());
            if (r == null || !CompOffConstants.ACTIVE.equals(r.getStatus()) || !CompOffConstants.REQ_PENDING.equals(r.getLeaveStatus())) {
                // already final, or cancelled / rejected before it was processed
                boolean approved = r != null && (CompOffConstants.REQ_APPROVED.equals(r.getLeaveStatus())
                        || CompOffConstants.REQ_COMPLETED.equals(r.getLeaveStatus()));
                finishStaged("MultiCompOffApprovals", mid, approved ? "Completed" : "Cancelled");
                return true;
            }
            if (m.isReject()) {
                return processCompOffReject(m, r, mid);
            }
            int level = m.getApprovalLevel() == null ? 1 : m.getApprovalLevel();
            Staffs next = nextApprover(CompOffConstants.LT_COMP_OFF, r.getStaffID(), level, m.getApproverID());
            if (next != null) {
                // a level was added (chain or HR) after this was staged: the next level must approve
                if (!handOverCompOff(r, m.getApproverID(), level, next, m.getNotes(), m.getApprovedDate())) {
                    LOG.warning("Staged comp off approval " + m.getMCOAID() + ": request no longer waits with approver " + m.getApproverID());
                }
                finishStaged("MultiCompOffApprovals", mid, "Completed");
                return true;
            }
            Staffs approver = manager.findStaff(m.getApproverID());
            if (hs.updateSQLQuery1("UPDATE CompensatoryOffRequest SET leaveStatus = 'Approved', modifiedDate = NOW()"
                    + (approver == null ? "" : ", leaveApproverID = " + SqlUtil.text(manager.approverValue(approver)))
                    + " WHERE COID = " + SqlUtil.number(r.getCOID()) + " AND leaveStatus = 'Pending' AND status = 'A'") == 1) {
                steps.close(CompOffConstants.LT_COMP_OFF, r.getCOID(), ApproverSteps.APPROVED, m.getNotes(), m.getApprovedDate());
                r.setCurrentApprovalLevel(level);
                manager.compOffHistory(r, CompOffConstants.HIST_APPROVED, CompOffConstants.REQ_PENDING, CompOffConstants.REQ_APPROVED,
                        r.getAttendanceDate(), m.getApproverID(), BigDecimal.ZERO,
                        withNote(isHrLevel(CompOffConstants.LT_COMP_OFF, r.getStaffID(), level) ? "HR approval" : null, m.getNotes()),
                        HistoryComment.remarks(m.getNotes()).at(m.getApprovedDate()).processed(manualNote));
                notifier().compOffApproved(r, m.getApproverID(), m.getNotes());
            }
            finishStaged("MultiCompOffApprovals", mid, "Completed");
            return true;
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Staged comp off approval " + m.getMCOAID() + " failed; left In_Progress for retry", e);
            return false;
        }
    }

    /**
     * A staged Reject: in one statement the request becomes Rejected, its intimation Available again and the day goes
     * back to the balance (or nothing changes); history and email as for a direct reject.
     */
    private boolean processCompOffReject(MultiCompOffApprovals m, CompensatoryOffRequest r, String mid) {
        if (!manager.releaseCompOff(r, CompOffConstants.REQ_PENDING, CompOffConstants.REQ_REJECTED,
                " AND " + CompOffManager.waitingWith("c", m.getApproverID()))) {
            LOG.warning("Staged comp off rejection " + m.getMCOAID() + ": request changed, or intimation / balance out of sync; retried on the next run");
            return false;
        }
        steps.close(CompOffConstants.LT_COMP_OFF, r.getCOID(), ApproverSteps.REJECTED, m.getNotes(), m.getApprovedDate());
        manager.compOffHistory(r, CompOffConstants.HIST_REJECTED, CompOffConstants.REQ_PENDING, CompOffConstants.REQ_REJECTED,
                r.getAttendanceDate(), m.getApproverID(), r.getDuration().negate(), m.getNotes(),
                HistoryComment.remarks(m.getNotes()).at(m.getApprovedDate()).processed(manualNote));
        notifier().compOffRejected(r, m.getApproverID(), m.getNotes());
        finishStaged("MultiCompOffApprovals", mid, "Completed");
        return true;
    }

    /**
     * Rejects each ticked COID (any level): the request becomes Rejected, the intimation is Available again and the
     * day returns to the balance.
     */
    public Outcome rejectCompOff(Staffs approver, List<Integer> ids, String remarks) {
        Outcome out = new Outcome();
        List<Row> waiting = compOffRows(approver.getStaffID());
        Map<Integer, Integer> stepLevel = levels(waiting);
        Set<Integer> ok = eligible(waiting, ids);
        out.skipped = ids.size() - ok.size();
        String batchId = batchId(approver);
        for (Integer coId : ok) {
            CompensatoryOffRequest r = (CompensatoryOffRequest) hs.get(CompensatoryOffRequest.class.getName(), coId);
            int level = stepLevel.get(coId);
            if (r != null && stageCompOff(batchId, level, r, approver.getStaffID(), remarks, CompOffConstants.ACTION_REJECT)) {
                out.done++;
            } else {
                out.skipped++;
            }
        }
        return out;
    }

    // =====================================================================
    // Approval servlet entry point and helpers
    // =====================================================================

    /** Staged rows the last processNext...() call could not finish (left In_Progress, retried on a later run). */
    private int lastFailed;

    public int getLastFailed() { return lastFailed; }

    /**
     * HolidayApprovalServlet: processes ONE staged holiday decision (approve or reject), the oldest click first.
     * When the oldest cannot be processed it stays In_Progress and the next one is tried, so one bad row never
     * blocks the queue. Returns 1 when a request was processed, else 0; failures: getLastFailed().
     */
    @SuppressWarnings("unchecked")
    public int processNextHoliday() {
        lastFailed = 0;
        List<MultiHolidayApprovals> next = hs.getListPaging("from MultiHolidayApprovals where approverStatus = 'In_Progress' and status = 'A'"
                + " and HWID in (select h.HWRID from HolidayRequests h where h.staffID in (select s.staffID from Staffs s where s.companyID = "
                + SqlUtil.number(CompOffConfig.getCompanyId()) + "))"
                + " order by approvedDate, MHAID", 0, NEXT_WINDOW);
        for (MultiHolidayApprovals m : next == null ? new ArrayList<MultiHolidayApprovals>() : next) {
            if (processHoliday(m)) {
                return 1;
            }
            lastFailed++;
        }
        return 0;
    }

    /**
     * CompOffApprovalServlet: processes ONE staged comp off decision (approve or reject), as processNextHoliday, but
     * only one the automatic run may process (autoEligible): leave date in the current month, or in an earlier month
     * with the decision clicked before that month's salary cut-off. The others wait In Process for the Admin (Comp Off
     * Processing page) - or, for a later month, for their month.
     */
    public int processNextCompOff() {
        lastFailed = 0;
        for (Integer id : ids("SELECT m.MCOAID FROM MultiCompOffApprovals m JOIN CompensatoryOffRequest c ON c.COID = m.COID"
                + " WHERE m.approverStatus = 'In_Progress' AND m.status = 'A' AND " + autoEligibleSql()
                + " AND " + CompOffConfig.companyStaffSql("c.staffID")
                + " ORDER BY m.approvedDate, m.MCOAID LIMIT " + NEXT_WINDOW)) {
            MultiCompOffApprovals m = (MultiCompOffApprovals) hs.get(MultiCompOffApprovals.class.getName(), id);
            if (m == null) {
                continue;
            }
            if (processCompOff(m)) {
                return 1;
            }
            lastFailed++;
        }
        return 0;
    }

    // =====================================================================
    // Comp Off salary cut-off: what the automatic run may process, and the Admin's manual run
    // =====================================================================

    /**
     * Salary is processed on the 1st of each month for the month before. A staged comp off decision is processed by
     * the automatic run when its leave date is in the current month, or in an earlier month and it was clicked before
     * that month's cut-off (00:00 on the 1st of the next month, company time zone). Anything else - an earlier month
     * clicked after its cut-off, or a later month - stays In Process. Approvals and rejections alike.
     */
    static boolean autoEligible(LocalDate leaveDate, Date clicked) {
        LocalDate first = CompOffConfig.today().withDayOfMonth(1);
        if (!leaveDate.isBefore(first)) {
            return leaveDate.isBefore(first.plusMonths(1));
        }
        java.time.Instant cutoff = leaveDate.withDayOfMonth(1).plusMonths(1).atStartOfDay(CompOffConfig.getZone()).toInstant();
        return clicked != null && clicked.toInstant().isBefore(cutoff);
    }

    /** autoEligible in SQL (m = MultiCompOffApprovals, c = CompensatoryOffRequest); approvedDate is server-local time. */
    private static String autoEligibleSql() {
        LocalDate first = CompOffConfig.today().withDayOfMonth(1);
        String cutoff = "CONVERT_TZ(DATE_ADD(DATE_FORMAT(c.leaveDate, '%Y-%m-01'), INTERVAL 1 MONTH), "
                + SqlUtil.text(offset(CompOffConfig.getZone())) + ", " + SqlUtil.text(offset(java.time.ZoneId.systemDefault())) + ")";
        return "((c.leaveDate >= " + SqlUtil.date(first) + " AND c.leaveDate < " + SqlUtil.date(first.plusMonths(1)) + ")"
                + " OR (c.leaveDate < " + SqlUtil.date(first) + " AND m.approvedDate < " + cutoff + "))";
    }

    private static String offset(java.time.ZoneId zone) {
        String id = zone.getRules().getOffset(java.time.Instant.now()).getId();
        return "Z".equals(id) ? "+00:00" : id;
    }

    private static String monthName(LocalDate d) {
        return d.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + d.getYear();
    }

    /** One line of the Admin's Comp Off Processing page: a decision waiting In Process. */
    public static class HeldRow {
        public final Integer id;
        public final String staffName;
        public final String employeeCode;
        public final Date leaveDate;
        public final String action;
        public final Integer level;
        public final String approverName;
        public final Date clickedOn;
        public final String notes;
        public final boolean automatic;

        HeldRow(Object[] r) {
            id = ((Number) r[0]).intValue();
            staffName = (String) r[1];
            employeeCode = (String) r[2];
            leaveDate = (Date) r[3];
            action = r[4] == null ? CompOffConstants.ACTION_APPROVE : (String) r[4];
            level = r[5] == null ? null : ((Number) r[5]).intValue();
            approverName = (String) r[6];
            clickedOn = (Date) r[7];
            notes = (String) r[8];
            automatic = leaveDate != null && autoEligible(DateUtil.toLocalDate(leaveDate), clickedOn);
        }

        public Integer getId() { return id; }
        public String getStaffName() { return staffName; }
        public String getEmployeeCode() { return employeeCode; }
        public Date getLeaveDate() { return leaveDate; }
        public String getAction() { return action; }
        public boolean isReject() { return CompOffConstants.ACTION_REJECT.equals(action); }
        public Integer getLevel() { return level; }
        public String getApproverName() { return approverName; }
        public Date getClickedOn() { return clickedOn; }
        public String getNotes() { return notes; }
        public boolean isAutomatic() { return automatic; }
    }

    /** Comp off decisions waiting In Process whose leave date is in the month, oldest click first (Admin page). */
    @SuppressWarnings("unchecked")
    public List<HeldRow> heldCompOff(java.time.YearMonth ym) {
        List<Object[]> raw = hs.getSQLList("SELECT m.MCOAID, " + CompOffConfig.nameSql("s") + ", s.employeeCode, c.leaveDate, m.approvalAction,"
                + " m.approvalLevel, " + CompOffConfig.nameSql("a") + " AS ApproverName, m.approvedDate, m.notes"
                + " FROM MultiCompOffApprovals m JOIN CompensatoryOffRequest c ON c.COID = m.COID"
                + " JOIN Staffs s ON s.staffID = c.staffID LEFT JOIN Staffs a ON a.staffID = m.approverID"
                + " WHERE m.approverStatus = 'In_Progress' AND m.status = 'A' AND s.companyID = " + SqlUtil.number(CompOffConfig.getCompanyId())
                + " AND c.leaveDate >= " + SqlUtil.date(ym.atDay(1)) + " AND c.leaveDate < " + SqlUtil.date(ym.plusMonths(1).atDay(1))
                + " ORDER BY m.approvedDate, m.MCOAID");
        if (raw == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        List<HeldRow> out = new ArrayList<HeldRow>();
        for (Object[] r : raw) {
            out.add(new HeldRow(r));
        }
        return out;
    }

    /** How a manual run is noted in the history ("by Admin ..." / "manually (servlet, ...)"); null for the automatic run. */
    private String manualNote;

    /**
     * Manual run for a month (by leave date), whatever the cut-off: the Admin's Comp Off Processing page, or the Comp
     * Off servlet called with month / year. The oldest decision (all = false; when it cannot be processed the next is
     * tried) or all of them. note = how it is recorded in the history. Returns the number processed; failures:
     * getLastFailed().
     */
    public int processCompOffMonth(java.time.YearMonth ym, boolean all, String note) {
        lastFailed = 0;
        int n = 0;
        manualNote = note;
        try {
            for (HeldRow h : heldCompOff(ym)) {
                MultiCompOffApprovals m = (MultiCompOffApprovals) hs.get(MultiCompOffApprovals.class.getName(), h.id);
                if (m == null || !CompOffConstants.STAGED_IN_PROGRESS.equals(m.getApproverStatus())) {
                    continue;
                }
                if (processCompOff(m)) {
                    n++;
                    if (!all) {
                        break;
                    }
                } else {
                    lastFailed++;
                }
            }
        } finally {
            manualNote = null;
        }
        return n;
    }

    /** Ids from a one-column SQL query. */
    @SuppressWarnings("unchecked")
    private List<Integer> ids(String sql) {
        List<Object> raw = hs.getSQLList(sql);
        List<Integer> out = new ArrayList<Integer>();
        for (Object o : raw == null ? new ArrayList<Object>() : raw) {
            out.add(((Number) o).intValue());
        }
        return out;
    }

    /**
     * Approved Comp Off requests whose leave date is before today become Completed (approval servlet), and so do the
     * Used intimations whose day they took. Each step is a conditional update, so running it again changes nothing;
     * the second query also finishes intimations left behind by an interrupted run. Returns the requests completed.
     */
    @SuppressWarnings("unchecked")
    public int completePastLeaves() {
        LocalDate today = CompOffConfig.today();
        int n = 0;
        for (Integer id : manager.ids("SELECT COID FROM CompensatoryOffRequest WHERE leaveStatus = 'Approved' AND status = 'A'"
                + " AND leaveDate < " + SqlUtil.date(today) + " AND " + CompOffConfig.companyStaffSql("staffID"))) {
            try {
                if (hs.updateSQLQuery1("UPDATE CompensatoryOffRequest SET leaveStatus = 'Completed', modifiedDate = NOW() WHERE COID = "
                        + SqlUtil.number(id) + " AND leaveStatus = 'Approved' AND status = 'A'") != 1) {
                    continue;
                }
                CompensatoryOffRequest r = (CompensatoryOffRequest) hs.get(CompensatoryOffRequest.class.getName(), id);
                manager.compOffHistory(r, CompOffConstants.HIST_COMPLETED, CompOffConstants.REQ_APPROVED, CompOffConstants.REQ_COMPLETED,
                        r.getAttendanceDate(), null, BigDecimal.ZERO, "Leave date passed", HistoryComment.none());
                n++;
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Comp off request " + id + " could not be completed; retried on the next run", e);
            }
        }
        List<Object[]> used = hs.getSQLList("SELECT h.HWRID, c.leaveDate FROM HolidayRequests h"
                + " JOIN CompensatoryOffRequest c ON c.referenceID = CAST(h.HWRID AS CHAR) AND c.status = 'A' AND c.leaveStatus = 'Completed'"
                + " WHERE h.holidayWorkingStatus = 'Used' AND h.recordStatus = 'A' AND " + CompOffConfig.companyStaffSql("h.staffID"));
        if (used == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        for (Object[] u : used) {
            Integer id = ((Number) u[0]).intValue();
            try {
                if (hs.updateSQLQuery1("UPDATE HolidayRequests SET holidayWorkingStatus = 'Completed', modifiedDate = NOW() WHERE HWRID = "
                        + SqlUtil.number(id) + " AND holidayWorkingStatus = 'Used' AND recordStatus = 'A'") != 1) {
                    continue;
                }
                HolidayRequests h = (HolidayRequests) hs.get(HolidayRequests.class.getName(), id);
                manager.holidayHistory(h, CompOffConstants.HIST_COMPLETED, CompOffConstants.HW_USED, CompOffConstants.HW_COMPLETED, null,
                        "Comp Off taken on " + DateUtil.display((Date) u[1]), BigDecimal.ZERO, HistoryComment.none().leaveDate((Date) u[1]));
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Holiday intimation " + id + " could not be completed; retried on the next run", e);
            }
        }
        return n;
    }

    // =====================================================================
    // Waiting requests whose approver is no longer valid: set to the Admin
    // =====================================================================

    /**
     * Waiting requests (Pending, nothing staged) whose approver is inactive, no longer has the Approver role, or is
     * neither in the staff member's LeaveSettingsCompOff chain nor the flow's HR approver, get the Admin as approver.
     * staffId limits it to one staff member's requests (Approver Management save); null checks every request
     * (approval servlet). Requests already with an Admin are left as they are. Returns the requests moved.
     */
    public int reassignToAdmin(Integer staffId, Integer adminId) {
        return reassignToAdmin(staffId, adminId, true, true);
    }

    /** As reassignToAdmin, for one flow only (each approval servlet checks its own requests). */
    public int reassignToAdmin(Integer staffId, Integer adminId, boolean holidays, boolean compOffs) {
        return reassign(staffId, adminId, holidays, compOffs);
    }

    @SuppressWarnings("unchecked")
    private int reassign(Integer staffId, Integer adminId, boolean holidays, boolean compOffs) {
        if (adminId == null) {
            return 0;
        }
        Staffs admin = manager.findStaff(adminId);
        if (admin == null) {
            return 0;
        }
        int n = 0;
        List<Object[]> hol = !holidays ? null : hs.getSQLList("SELECT h.HWRID, h.staffID, h.approverID, a.status, a.userType"
                + " FROM HolidayRequests h LEFT JOIN Staffs a ON a.staffID = h.approverID"
                + " WHERE h.holidayWorkingStatus = 'Pending' AND h.holidayApproveStatus = 'Pending' AND h.recordStatus = 'A'"
                + " AND " + CompOffConfig.companyStaffSql("h.staffID")
                + (staffId == null ? "" : " AND h.staffID = " + SqlUtil.number(staffId))
                + " AND NOT EXISTS (SELECT 1 FROM MultiHolidayApprovals m WHERE m.HWID = h.HWRID"
                + "   AND m.approverStatus = 'In_Progress' AND m.status = 'A')");
        for (Object[] row : hol == null ? new ArrayList<Object[]>() : hol) {
            if (stillValid(CompOffConstants.LT_HOLIDAY, row)) {
                continue;
            }
            int moved = hs.updateSQLQuery1("UPDATE HolidayRequests SET approverID = " + SqlUtil.number(adminId) + ", modifiedDate = NOW()"
                    + " WHERE HWRID = " + SqlUtil.number(((Number) row[0]).intValue()) + " AND approverID = "
                    + SqlUtil.number(((Number) row[2]).intValue()) + " AND holidayWorkingStatus = 'Pending' AND recordStatus = 'A'");
            if (moved == 1) {
                steps.reassign(CompOffConstants.LT_HOLIDAY, ((Number) row[0]).intValue(), adminId);   // the open step moves with it
            }
            n += moved;
        }
        List<Object[]> cos = !compOffs ? null : hs.getSQLList("SELECT c.COID, c.staffID, CAST(TRIM(SUBSTRING_INDEX(c.leaveApproverID, '~~', -1)) AS UNSIGNED),"
                + " a.status, a.userType, c.leaveApproverID"
                + " FROM CompensatoryOffRequest c LEFT JOIN Staffs a ON a.staffID = CAST(TRIM(SUBSTRING_INDEX(c.leaveApproverID, '~~', -1)) AS UNSIGNED)"
                + " WHERE c.leaveStatus = 'Pending' AND c.status = 'A'"
                + " AND " + CompOffConfig.companyStaffSql("c.staffID")
                + (staffId == null ? "" : " AND c.staffID = " + SqlUtil.number(staffId))
                + " AND NOT EXISTS (SELECT 1 FROM MultiCompOffApprovals m WHERE m.COID = c.COID"
                + "   AND m.approverStatus = 'In_Progress' AND m.status = 'A')");
        for (Object[] row : cos == null ? new ArrayList<Object[]>() : cos) {
            if (stillValid(CompOffConstants.LT_COMP_OFF, row)) {
                continue;
            }
            int moved = hs.updateSQLQuery1("UPDATE CompensatoryOffRequest SET leaveApproverID = " + SqlUtil.text(manager.approverValue(admin))
                    + ", modifiedDate = NOW() WHERE COID = " + SqlUtil.number(((Number) row[0]).intValue())
                    + " AND leaveApproverID = " + SqlUtil.text((String) row[5]) + " AND leaveStatus = 'Pending' AND status = 'A'");
            if (moved == 1) {
                steps.reassign(CompOffConstants.LT_COMP_OFF, ((Number) row[0]).intValue(), adminId);   // the open step moves with it
            }
            n += moved;
        }
        if (n > 0) {
            LOG.info(n + " waiting approval(s) set to Admin " + adminId + (staffId == null ? "" : " for staff " + staffId));
        }
        return n;
    }

    /**
     * Repairs the approver steps of waiting requests: a request without a Pending step gets one for the approver it
     * waits with (level and type worked out as for a decision), and a Pending step that names another approver than
     * the request is moved to that approver. Covers requests created before the step tables existed or outside the
     * application. Run by each approval servlet and before an approval page is read. Returns the steps written.
     */
    public int syncSteps(String leaveType) {
        int n = 0;
        for (Object[] r : steps.outOfStep(leaveType)) {
            if (r[2] == null) {
                continue;
            }
            Integer id = ((Number) r[0]).intValue(), staffId = ((Number) r[1]).intValue(), approverId = ((Number) r[2]).intValue();
            if (r[3] != null) {
                n += steps.reassign(leaveType, id, approverId);
            } else {
                int level = levelFor(leaveType, staffId, approverId, (CompOffConstants.LT_HOLIDAY.equals(leaveType) ? "HW-" : "CO-") + id);
                n += steps.open(leaveType, id, staffId, level, approverId) ? 1 : 0;
            }
        }
        if (n > 0) {
            LOG.info(n + " " + ApproverSteps.table(leaveType) + " step(s) written for waiting requests");
        }
        return n;
    }

    /** row = id, staffId, approverId, approver Status, approver user type: true when the request can stay with its approver. */
    private boolean stillValid(String leaveType, Object[] row) {
        String access = CompOffConfig.accessRole((String) row[4]);
        if (row[2] == null || CompOffConstants.ROLE_ADMIN.equals(access)) {
            return true;                           // no approver recorded / already with an Admin
        }
        if (!CompOffConstants.ACTIVE.equals(String.valueOf(row[3])) || !CompOffConstants.ROLE_APPROVER.equals(access)) {
            return false;
        }
        Integer staffId = ((Number) row[1]).intValue(), approverId = ((Number) row[2]).intValue();
        // in the chain, or the HR person of the flow's GeneralSettings record (an HR step already handed over stays)
        return manager.chainIds(staffId).contains(approverId) || approverId.equals(settings.latestHrApproverId(leaveType));
    }

    /** The Admin who gets waiting requests found by the approval servlet: the first active Admin (lowest staffID) of the company. */
    public Integer defaultAdminId() {
        return manager.firstId("SELECT MIN(staffID) FROM Staffs WHERE " + CompOffConfig.userTypeSql(null, CompOffConstants.ROLE_ADMIN)
                + " AND status = 'A' AND companyID = " + SqlUtil.number(CompOffConfig.getCompanyId()));
    }

    /** A staged row that lost to an older one for the same request and level: Cancelled and Inactive. */
    private void discardStaged(String table, String id) {
        hs.updateSQLQuery1("UPDATE " + table + " SET approverStatus = 'Cancelled', processedDate = NOW(), status = 'I' WHERE "
                + (table.startsWith("MultiHoliday") ? "MHAID" : "MCOAID") + " = " + id);
    }

    private void finishStaged(String table, String id, String status) {
        hs.updateSQLQuery1("UPDATE " + table + " SET approverStatus = " + SqlUtil.code(status) + ", processedDate = NOW()"
                + " WHERE " + (table.startsWith("MultiHoliday") ? "MHAID" : "MCOAID") + " = " + id
                + " AND approverStatus = 'In_Progress'");
    }

    /**
     * Tells the staff member about the decision.
     * TODO: wire to the mail service (e.g. SendGrid, already in WEB-INF/lib).
     */
    /** Emails collected by this manager; sent by flushEmails() at the end of each click / servlet run. */
    private Notifier notifier;
    private String requestBaseUrl;

    /** Address of the current request (scheme://host[:port]/context), used for links when APP_BASE_URL is not set. */
    public void setRequestBaseUrl(String url) {
        this.requestBaseUrl = url;
    }

    private Notifier notifier() {
        if (notifier == null) {
            notifier = new Notifier(manager, requestBaseUrl);
        }
        return notifier;
    }

    /** Sends the emails collected so far (one per recipient and type); never throws. */
    public void flushEmails() {
        if (notifier != null) {
            try {
                notifier.flush();
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Emails could not be sent", e);
            }
        }
    }
}
