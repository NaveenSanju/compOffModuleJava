package com.action;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.dao.CompOffBalanceDetailsHistory;
import com.dao.CompOffConstants;
import com.dao.CompOffBalanceDetails;
import com.dao.CompensatoryOffRequest;
import com.dao.HolidayRequests;
import com.dao.LeaveSettingsCompOff;
import com.dao.LeaveType;
import com.dao.Staffs;
import com.serviceimpl.HibernateService;
import com.serviceimpl.HibernateUtil;
import com.util.CompOffConfig;
import com.util.DateUtil;
import com.util.SqlUtil;

/**
 * Business workflows of the Comp Off module (Holiday Intimation, Apply Comp Off, login lookup).
 * All database access goes through com.serviceimpl.HibernateService.
 *
 * Every HibernateService call commits on its own, so a workflow made of several calls is kept
 * consistent by:
 *   - conditional single-statement updates ("... WHERE Balance >= 1", "... WHERE HolidayWorkingStatus
 *     = 'Available'") whose affected-row count is checked, so two simultaneous requests cannot both win;
 *   - "oldest row wins" checks instead of database constraints (the schema has none besides primary keys):
 *     a record that must be single (one open intimation / request per staff + date, one balance row per
 *     staff, one staged approval per request and level) is looked up before it is saved,
 *     and checked again right after: if an older Active one exists (two simultaneous clicks), the new
 *     one is set to Status = 'I' and the user is told it already exists;
 *   - compensating updates when a later step fails (records are set back, or to Status = 'I';
 *     nothing is ever deleted).
 * HibernateService returns null / 0 when a call fails, so every result is checked.
 */
public class CompOffManager {

    private static final Logger LOG = Logger.getLogger(CompOffManager.class.getName());
    private static final String DB_ERROR = "Something went wrong. Please try again, and contact the admin if it keeps happening.";
    private static final String CHANGED = "This record was changed by someone else just now. Please refresh the page and try again.";

    private final HibernateService hs = new HibernateUtil();
    /** HolidayRequestApprovers / CompensatoryOffApprovers (one row per approval step). */
    private final ApproverSteps steps = new ApproverSteps();

    // =====================================================================
    // Reads
    // =====================================================================

    /** Active staff member by staffID, or null. */
    public Staffs findStaff(Integer staffId) {
        if (staffId == null) {
            return null;
        }
        List<Staffs> rows = list("from Staffs where staffID = " + SqlUtil.number(staffId) + " and status = 'A'");
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Active staff member whose email or loginUserID matches (case-insensitive), or null (also when two match). */
    public Staffs findByLogin(String loginId) {
        List<Staffs> rows = list("from Staffs where status = 'A'"
                + " and (lower(email) = :login or lower(loginUserID) = :login)",
                "login", loginId.trim().toLowerCase());
        return rows.size() == 1 ? rows.get(0) : null;
    }

    /** staffID -> "Name (employeeCode)" of every staff member, for showing actors in history tables. */
    /** The companies that have staff (Staffs.companyID), for the approval servlets' one run per company. */
    public List<Integer> companyIds() {
        return new ArrayList<Integer>(new java.util.TreeSet<Integer>(ids("SELECT DISTINCT companyID FROM Staffs WHERE companyID IS NOT NULL")));
    }

    public Map<Integer, String> staffNames() {
        Map<Integer, String> names = new HashMap<Integer, String>();
        for (Staffs s : this.<Staffs>list("from Staffs")) {
            names.put(s.getStaffID(), s.getStaffName() + " (" + s.getEmployeeCode() + ")");
        }
        return names;
    }

    public Staffs holidayIntimationApprover(Integer staffId) {
        return approverAtLevel(staffId, 1);
    }

    public Staffs compOffApprover(Integer staffId) {
        return approverAtLevel(staffId, 1);
    }

    /**
     * The active approver at the given level (1 = first) of the staff member's chain: the level-th entry of
     * LeaveSettingsCompOff.leaveApproverID. Null when there is no such level, or that approver is inactive or the staff member.
     */
    public Staffs approverAtLevel(Integer staffId, int level) {
        List<Integer> chain = chainIds(staffId);
        if (level < 1 || level > chain.size() || chain.get(level - 1).equals(staffId)) {
            return null;
        }
        return findStaff(chain.get(level - 1));
    }

    /**
     * StaffIds of the staff member's approver chain, in level order. One chain per staff member, used for both Holiday
     * Intimation and Comp Off: the Active LeaveSettingsCompOff row of the staffID (leaveTypeID / leaveTypeName are not
     * used; should there be several Active rows, the highest LSID).
     */
    public List<Integer> chainIds(Integer staffId) {
        List<LeaveSettingsCompOff> rows = list("from LeaveSettingsCompOff s where s.staffID = " + SqlUtil.number(staffId) + " and s.status = 'A'"
                + " and s.companyID = " + SqlUtil.number(CompOffConfig.getCompanyId()) + " order by s.LSID desc");
        return rows.isEmpty() ? new ArrayList<Integer>() : rows.get(0).approverIds();
    }

    /**
     * Approval level of the approver a request is waiting with: its position in the staff member's chain (1 = first);
     * an approver outside the chain (the GeneralSettings HR approver, or an Admin) is the level after the chain.
     */
    public int levelOf(Integer staffId, Integer approverId) {
        List<Integer> chain = chainIds(staffId);
        int i = approverId == null ? -1 : chain.indexOf(approverId);
        return i >= 0 ? i + 1 : chain.size() + 1;
    }

    /** '<userType>~~<staffID>' of a staff member (e.g. 'TL~~5'), as kept in CompensatoryOffRequest.leaveApproverID. */
    public String approverValue(Staffs s) {
        String code = s.getUserType() == null || s.getUserType().trim().isEmpty() ? "NA" : s.getUserType().trim();
        return code + "~~" + s.getStaffID();
    }

    /**
     * Name of a staff member's user type (UserTypes.userTypeName): the row of Staffs.userTypeID when its code is
     * Staffs.userType, else the first row with that code (one code can have several names, e.g. TL); else the code.
     */
    public String userTypeName(Staffs s) {
        String code = s == null || s.getUserType() == null ? "" : s.getUserType().trim();
        if (code.isEmpty()) {
            return "";
        }
        if (String.valueOf(s.getUserTypeID()) != null) {
            List<com.dao.UserTypes> own = list("from UserTypes where userTypeID = " + SqlUtil.number(s.getUserTypeID()));
            if (!own.isEmpty() && code.equalsIgnoreCase(own.get(0).getUserType()) && own.get(0).getUserTypeName() != null) {
                return own.get(0).getUserTypeName();
            }
        }
        List<com.dao.UserTypes> byCode = list("from UserTypes where userType = :c and companyID = "
                + SqlUtil.number(CompOffConfig.getCompanyId()) + " order by userTypeID", "c", code);
        return byCode.isEmpty() || byCode.get(0).getUserTypeName() == null ? code : byCode.get(0).getUserTypeName();
    }

    /** LeaveType id of 'Compensatory OFF' for this company. */
    public Integer compOffLeaveTypeId() {
        List<LeaveType> rows = list("from LeaveType where leaveTypeName = :n and status = 'A' and companyID = "
                + SqlUtil.number(CompOffConfig.getCompanyId()) + " order by leaveTypeID", "n", CompOffConstants.LT_COMP_OFF);
        if (rows.isEmpty()) {
            throw new CompOffException(null, "Leave type 'Compensatory OFF' is not set up. Please contact the administrator.");
        }
        return rows.get(0).getLeaveTypeID();
    }

    /** SQL condition: the CompensatoryOffRequest c is waiting with this staffID (leaveApproverID '<code>~~<id>'). */
    static String waitingWith(String alias, Integer staffId) {
        return "TRIM(SUBSTRING_INDEX(" + alias + ".leaveApproverID, '~~', -1)) = " + SqlUtil.text(String.valueOf(staffId));
    }

    /** The staff member's active intimations, newest first, with "In Process" and what their day was used for. */
    @SuppressWarnings("unchecked")
    public List<HolidayRequests> intimations(Integer staffId) {
        List<HolidayRequests> rows = list("from HolidayRequests where staffID = " + SqlUtil.number(staffId)
                + " and recordStatus = 'A' order by createdDate desc, HWRID desc");
        java.util.Set<Integer> inProcess = ids("SELECT h.HWRID FROM HolidayRequests h"
                + " WHERE h.staffID = " + SqlUtil.number(staffId) + " AND h.holidayWorkingStatus = 'Pending' AND h.recordStatus = 'A' AND ("
                + " EXISTS (SELECT 1 FROM CompOffBalanceDetailsHistory x WHERE x.keyID = CONCAT('HW-', h.HWRID)"
                + "   AND x.historyAction = 'Approved' AND x.status = 'A')"
                + " OR EXISTS (SELECT 1 FROM MultiHolidayApprovals m WHERE m.HWID = h.HWRID"
                + "   AND m.approverStatus = 'In_Progress' AND m.status = 'A'))");
        Map<Integer, java.util.Date> usedFor = new HashMap<Integer, java.util.Date>();
        List<Object[]> uses = hs.getSQLList("SELECT c.referenceID, c.leaveDate FROM CompensatoryOffRequest c WHERE c.staffID = "
                + SqlUtil.number(staffId) + " AND c.status = 'A' AND c.leaveStatus IN ('Pending', 'Approved', 'Completed')");
        if (uses == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        for (Object[] u : uses) {
            if (u[0] != null && u[0].toString().trim().matches("[0-9]{1,9}")) {
                usedFor.put(Integer.valueOf(u[0].toString().trim()), (java.util.Date) u[1]);
            }
        }
        Map<Integer, HolidayRequests> byId = new HashMap<Integer, HolidayRequests>();
        for (HolidayRequests h : rows) {
            byId.put(h.getHWRID(), h);
        }
        addApproverSteps(staffId, byId);
        for (HolidayRequests h : rows) {
            if (inProcess.contains(h.getHWRID())) {
                h.setDisplayStatus(CompOffConstants.IN_PROCESS);
            }
            java.util.Date used = usedFor.get(h.getHWRID());
            if (used != null && (CompOffConstants.HW_USED.equals(h.getHolidayWorkingStatus())
                    || CompOffConstants.HW_COMPLETED.equals(h.getHolidayWorkingStatus()))) {
                h.setNotes("Comp Off applied for: " + DateUtil.display(used));
            }
        }
        return rows;
    }

    /**
     * "Approver Status" and "Approver Notes" of the staff member's intimations (My Holiday Intimations): in order, each
     * approver who decided (from the history: Approved / Rejected, with their notes), a decision waiting In Process for
     * the approval servlet (the approver's click: Approved / Rejected), and the approver it is waiting with now (Pending).
     */
    @SuppressWarnings("unchecked")
    private void addApproverSteps(Integer staffId, Map<Integer, HolidayRequests> byId) {
        if (byId.isEmpty()) {
            return;
        }
        String sid = SqlUtil.number(staffId);
        String actor = "CAST(JSON_UNQUOTE(JSON_EXTRACT(x.leaveHistoryActionCommands, '$.actorId')) AS UNSIGNED)";
        List<Object[]> done = hs.getSQLList("SELECT x.HRID, x.historyAction, " + CompOffConfig.nameSql("s") + ","
                + " JSON_UNQUOTE(JSON_EXTRACT(x.leaveHistoryActionCommands, '$.remarks'))"
                + " FROM CompOffBalanceDetailsHistory x LEFT JOIN Staffs s ON s.staffID = " + actor
                + " WHERE x.staffID = " + sid + " AND x.leaveType = " + SqlUtil.text(CompOffConstants.LT_HOLIDAY)
                + " AND x.historyAction IN ('Approved', 'Rejected') AND x.status = 'A' AND JSON_VALID(x.leaveHistoryActionCommands)"
                + " ORDER BY x.COHID");
        List<Object[]> staged = hs.getSQLList("SELECT m.HWID, m.approvalAction, " + CompOffConfig.nameSql("s") + ", m.notes"
                + " FROM MultiHolidayApprovals m JOIN HolidayRequests h ON h.HWRID = m.HWID"
                + " LEFT JOIN Staffs s ON s.staffID = m.approverID"
                + " WHERE h.staffID = " + sid + " AND m.approverStatus = 'In_Progress' AND m.status = 'A'");
        // the approver of the Pending HolidayRequestApprovers step (the request's approverID if it has none yet)
        List<Object[]> waiting = hs.getSQLList("SELECT h.HWRID, " + CompOffConfig.nameSql("s") + " FROM HolidayRequests h"
                + " LEFT JOIN HolidayRequestApprovers st ON st.HWID = h.HWRID AND st.approverStatus = 'Pending' AND st.status = 'A'"
                + " JOIN Staffs s ON s.staffID = COALESCE(st.approverID, h.approverID)"
                + " WHERE h.staffID = " + sid + " AND h.holidayApproveStatus = 'Pending' AND h.recordStatus = 'A'");
        if (done == null || staged == null || waiting == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        java.util.Set<Integer> hasStaged = new java.util.HashSet<Integer>();
        for (Object[] r : done) {
            step(byId, r[0], (String) r[2], CompOffConstants.HIST_REJECTED.equals(r[1]) ? "Rejected" : "Approved", approverNote((String) r[3]));
        }
        for (Object[] r : staged) {
            hasStaged.add(((Number) r[0]).intValue());
            step(byId, r[0], (String) r[2], CompOffConstants.ACTION_REJECT.equals(r[1]) ? "Rejected" : "Approved", (String) r[3]);
        }
        for (Object[] r : waiting) {
            if (!hasStaged.contains(((Number) r[0]).intValue())) {
                step(byId, r[0], (String) r[1], "Pending", null);
            }
        }
    }

    private static void step(Map<Integer, HolidayRequests> byId, Object id, String approver, String status, String notes) {
        HolidayRequests h = id == null ? null : byId.get(((Number) id).intValue());
        if (h == null) {
            return;
        }
        String name = approver == null ? "Approver" : approver;
        h.getApproverSteps().add(new String[] { name, status });
        if (notes != null && !notes.trim().isEmpty()) {
            h.getApproverNotes().add(new String[] { name, notes.trim() });
        }
    }

    /** The approver's own notes from a history row's remarks (without "HR approval" / "Sent for HR approval to ..."). */
    private static String approverNote(String remarks) {
        if (remarks == null) {
            return null;
        }
        String r = remarks.replaceFirst("^(HR approval|Sent for HR approval to [^:]*)(: )?", "").trim();
        return r.isEmpty() ? null : r;
    }

    /** Worked dates of the staff member's approved Holiday Intimations (Available / Used / Completed), oldest first. */
    public List<java.util.Date> approvedWorkedDates(Integer staffId) {
        return list("select h.workRequestDate from HolidayRequests h where h.staffID = " + SqlUtil.number(staffId)
                + " and h.recordStatus = 'A' and h.holidayWorkingStatus in ('Available', 'Used', 'Completed') order by h.workRequestDate");
    }

    /** Balance summary, or null when nothing has been allocated yet. */
    public CompOffBalanceDetails summary(Integer staffId) {
        List<CompOffBalanceDetails> rows = list("from CompOffBalanceDetails where staffID = " + SqlUtil.number(staffId) + " and status = 'A'");
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Available intimations in FIFO order (oldest worked date first). */
    public List<HolidayRequests> available(Integer staffId) {
        return list("from HolidayRequests where staffID = " + SqlUtil.number(staffId) + " and holidayWorkingStatus = 'Available'"
                + " and recordStatus = 'A' order by workRequestDate, HWRID");
    }

    /** The staff member's active Comp Off requests, latest leave date first. */
    public List<CompensatoryOffRequest> requests(Integer staffId, LocalDate today) {
        List<CompensatoryOffRequest> rows = list("from CompensatoryOffRequest where staffID = " + SqlUtil.number(staffId) + " and status = 'A'"
                + " order by leaveDate desc, COID desc");
        java.util.Set<Integer> inProcess = ids("SELECT c.COID FROM CompensatoryOffRequest c"
                + " WHERE c.staffID = " + SqlUtil.number(staffId) + " AND c.leaveStatus = 'Pending' AND c.status = 'A' AND ("
                + " EXISTS (SELECT 1 FROM CompOffBalanceDetailsHistory x WHERE x.keyID = CONCAT('CO-', c.COID)"
                + "   AND x.historyAction = 'Approved' AND x.status = 'A')"
                + " OR EXISTS (SELECT 1 FROM MultiCompOffApprovals m WHERE m.COID = c.COID"
                + "   AND m.approverStatus = 'In_Progress' AND m.status = 'A'))");
        for (CompensatoryOffRequest r : rows) {
            r.setCancellable(isCancellable(r, today));
            if (inProcess.contains(r.getCOID())) {
                r.setDisplayStatus(CompOffConstants.IN_PROCESS);
            }
        }
        return rows;
    }

    /**
     * My Comp Off Report: the staff member's Comp Off requests with a leave date from..to (both included), by leave date,
     * the latest request first within a day, each with its approver steps (see addCompOffSteps).
     */
    public List<CompensatoryOffRequest> reportRequests(Integer staffId, LocalDate from, LocalDate to) {
        List<CompensatoryOffRequest> rows = new ArrayList<CompensatoryOffRequest>();
        for (CompensatoryOffRequest r : requests(staffId, CompOffConfig.today())) {
            LocalDate d = DateUtil.toLocalDate(r.getLeaveDate());
            if (d != null && !d.isBefore(from) && !d.isAfter(to)) {
                rows.add(r);
            }
        }
        Collections.sort(rows, new java.util.Comparator<CompensatoryOffRequest>() {
            @Override
            public int compare(CompensatoryOffRequest a, CompensatoryOffRequest b) {
                int c = a.getLeaveDate().compareTo(b.getLeaveDate());
                return c != 0 ? c : b.getCOID().compareTo(a.getCOID());
            }
        });
        addCompOffSteps(staffId, rows);
        return rows;
    }

    /**
     * Approver steps of Comp Off requests, as on My Holiday Intimations: each approver who decided (history Approved /
     * Rejected, with their notes), a decision waiting In Process for the approval servlet, then the approver a Pending
     * request is waiting with now.
     */
    @SuppressWarnings("unchecked")
    private void addCompOffSteps(Integer staffId, List<CompensatoryOffRequest> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Map<Integer, CompensatoryOffRequest> byId = new HashMap<Integer, CompensatoryOffRequest>();
        for (CompensatoryOffRequest r : rows) {
            byId.put(r.getCOID(), r);
        }
        String sid = SqlUtil.number(staffId);
        String actor = "CAST(JSON_UNQUOTE(JSON_EXTRACT(x.leaveHistoryActionCommands, '$.actorId')) AS UNSIGNED)";
        List<Object[]> done = hs.getSQLList("SELECT x.COID, x.historyAction, " + CompOffConfig.nameSql("s") + ","
                + " JSON_UNQUOTE(JSON_EXTRACT(x.leaveHistoryActionCommands, '$.remarks'))"
                + " FROM CompOffBalanceDetailsHistory x LEFT JOIN Staffs s ON s.staffID = " + actor
                + " WHERE x.staffID = " + sid + " AND x.leaveType = " + SqlUtil.text(CompOffConstants.LT_COMP_OFF)
                + " AND x.historyAction IN ('Approved', 'Rejected') AND x.status = 'A' AND JSON_VALID(x.leaveHistoryActionCommands)"
                + " ORDER BY x.COHID");
        List<Object[]> staged = hs.getSQLList("SELECT m.COID, m.approvalAction, " + CompOffConfig.nameSql("s") + ", m.notes"
                + " FROM MultiCompOffApprovals m LEFT JOIN Staffs s ON s.staffID = m.approverID"
                + " WHERE m.staffID = " + sid + " AND m.approverStatus = 'In_Progress' AND m.status = 'A'"
                + " ORDER BY m.approvalLevel, m.MCOAID");
        if (done == null || staged == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        java.util.Set<Integer> hasStaged = new java.util.HashSet<Integer>();
        for (Object[] r : done) {
            coStep(byId, r[0], (String) r[2], CompOffConstants.HIST_REJECTED.equals(r[1]) ? "Rejected" : "Approved", approverNote((String) r[3]));
        }
        for (Object[] r : staged) {
            hasStaged.add(((Number) r[0]).intValue());
            coStep(byId, r[0], (String) r[2], CompOffConstants.ACTION_REJECT.equals(r[1]) ? "Rejected" : "Approved", (String) r[3]);
        }
        // the approver of the Pending CompensatoryOffApprovers step (the request's leaveApproverID if it has none yet)
        Map<Integer, Integer> stepApprover = new HashMap<Integer, Integer>();
        List<Object[]> open = hs.getSQLList("SELECT st.COID, st.approverID FROM CompensatoryOffApprovers st"
                + " WHERE st.staffID = " + sid + " AND st.approverStatus = 'Pending' AND st.status = 'A'");
        for (Object[] o : open == null ? new ArrayList<Object[]>() : open) {
            stepApprover.put(((Number) o[0]).intValue(), ((Number) o[1]).intValue());
        }
        for (CompensatoryOffRequest r : rows) {
            Integer waitingWith = stepApprover.containsKey(r.getCOID()) ? stepApprover.get(r.getCOID()) : r.getApproverId();
            if (CompOffConstants.REQ_PENDING.equals(r.getLeaveStatus()) && !hasStaged.contains(r.getCOID())
                    && waitingWith != null) {
                Staffs a = findStaff(waitingWith);
                coStep(byId, r.getCOID(), a == null ? null : a.getStaffName(), "Pending", null);
            }
        }
    }

    private static void coStep(Map<Integer, CompensatoryOffRequest> byId, Object id, String approver, String status, String notes) {
        CompensatoryOffRequest r = id == null ? null : byId.get(((Number) id).intValue());
        if (r != null) {
            r.getApproverSteps().add(new String[] { approver == null ? "Approver" : approver, status,
                    notes == null || notes.trim().isEmpty() ? null : notes.trim() });
        }
    }

    /** History rows of the staff member's Comp Off requests, keyed by COID (COID), oldest first. */
    public Map<Integer, List<CompOffBalanceDetailsHistory>> historyByRequest(Integer staffId) {
        Map<Integer, List<CompOffBalanceDetailsHistory>> map = new LinkedHashMap<Integer, List<CompOffBalanceDetailsHistory>>();
        for (CompOffBalanceDetailsHistory h : this.<CompOffBalanceDetailsHistory>list("from CompOffBalanceDetailsHistory where staffID = "
                + SqlUtil.number(staffId) + " and COID > 0 and status = 'A' order by createdDate, COHID")) {
            List<CompOffBalanceDetailsHistory> l = map.get(h.getCOID());
            if (l == null) {
                l = new ArrayList<CompOffBalanceDetailsHistory>();
                map.put(h.getCOID(), l);
            }
            l.add(h);
        }
        return map;
    }

    // =====================================================================
    // Holiday Intimation
    // =====================================================================

    /** Creates the intimation (Pending, waiting with the level-1 approver) and the 'Submitted' history row. */
    public Staffs submitHolidayIntimation(Integer staffId, LocalDate workedDate, String shiftType, String reason, Notifier notifier) {
        Staffs approver = holidayIntimationApprover(staffId);
        if (approver == null) {
            throw new CompOffException(null, "No Holiday Intimation approver is configured for you. Please contact the admin.");
        }
        if (hasOpenIntimation(staffId, workedDate)) {
            throw new CompOffException("workedDate", "An intimation already exists for this date.");
        }
        Staffs me = findStaff(staffId);

        HolidayRequests h = new HolidayRequests();
        h.setCreatedDate(new java.util.Date());
        h.setCompanyID(CompOffConfig.getCompanyId());
        h.setStaffID(staffId);
        h.setStaffName(me == null ? null : me.getStaffName());
        h.setEmployeeCode(me == null ? null : me.getEmployeeCode());
        h.setWorkRequestDate(DateUtil.toDate(workedDate));
        h.setShiftType(shiftType);
        h.setNotes(reason);
        h.setHolidayWorkingStatus(CompOffConstants.HW_PENDING);
        h.setHolidayApproveStatus(CompOffConstants.APPROVAL_PENDING);
        h.setApproverID(approver.getStaffID());
        h.setRecordStatus(CompOffConstants.ACTIVE);
        hs.save(h);
        if (h.getHWRID() == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        String id = SqlUtil.number(h.getHWRID());
        // another intimation for the same date saved at the same moment: the oldest one stays
        if (!isOldest("SELECT MIN(HWRID) FROM HolidayRequests WHERE staffID = " + SqlUtil.number(staffId)
                + " AND workRequestDate = " + SqlUtil.date(workedDate) + " AND recordStatus = 'A'"
                + " AND holidayWorkingStatus NOT IN ('Rejected', 'Cancelled')", h.getHWRID())) {
            hs.updateSQLQuery1("UPDATE HolidayRequests SET recordStatus = 'I', modifiedDate = NOW() WHERE HWRID = " + id);
            throw new CompOffException("workedDate", "An intimation already exists for this date.");
        }
        h.setCurrentApprovalLevel(1);
        // the level-1 approver's step (HolidayRequestApprovers): the request now waits with them
        if (!steps.open(CompOffConstants.LT_HOLIDAY, h.getHWRID(), staffId, 1, approver.getStaffID())
                || !holidayHistory(h, CompOffConstants.HIST_SUBMITTED, null, CompOffConstants.HW_PENDING, staffId, null, BigDecimal.ZERO,
                HistoryComment.none())) {
            hs.updateSQLQuery1("UPDATE HolidayRequests SET recordStatus = 'I', modifiedDate = NOW() WHERE HWRID = " + id);
            steps.inactivate(CompOffConstants.LT_HOLIDAY, h.getHWRID());
            throw new CompOffException(null, DB_ERROR);
        }
        if (notifier != null) {
            notifier.holidayApproval(approver, h, 1, false);
        }
        return approver;
    }

    /**
     * Cancels a Pending or Available intimation.
     * Pending: the approval is withdrawn (holidayApproveStatus Cancelled), with any approval staged for the servlet.
     * Available: reverses the allocated Comp Off day (Allocated - 1, Balance - 1).
     */
    public void cancelHolidayIntimation(Integer staffId, int holidayWorkingId, String cancelReason, Notifier notifier) {
        HolidayRequests h = (HolidayRequests) hs.get(HolidayRequests.class.getName(), holidayWorkingId);
        if (h == null || !staffId.equals(h.getStaffID()) || !CompOffConstants.ACTIVE.equals(h.getRecordStatus())) {
            throw new CompOffException(null, "Holiday intimation not found.");
        }
        String previous = h.getHolidayWorkingStatus();
        String id = SqlUtil.number(h.getHWRID());

        if (CompOffConstants.HW_USED.equals(previous)) {
            throw new CompOffException(null, "This intimation is used by a Comp Off request. Cancel the Comp Off request first.");
        }
        if (!h.isCancellable()) {
            throw new CompOffException(null, "A " + previous + " intimation cannot be cancelled.");
        }
        if (CompOffConstants.HW_PENDING.equals(previous)) {
            // Claim the cancellation: only succeeds if the status is still what we read
            if (hs.updateSQLQuery1("UPDATE HolidayRequests SET holidayWorkingStatus = 'Cancelled', holidayApproveStatus = 'Cancelled',"
                    + " modifiedDate = NOW() WHERE HWRID = " + id + " AND holidayWorkingStatus = 'Pending' AND recordStatus = 'A'") != 1) {
                throw new CompOffException(null, CHANGED);
            }
            // an approval clicked by the approver but not yet processed by the servlet must not go through
            int staged = hs.updateSQLQuery1("UPDATE MultiHolidayApprovals SET approverStatus = 'Cancelled', processedDate = NOW()"
                    + " WHERE HWID = " + id + " AND approverStatus = 'In_Progress' AND status = 'A'");
            steps.close(CompOffConstants.LT_HOLIDAY, h.getHWRID(), ApproverSteps.CANCELLED, null, null);
            if (notifier != null && staged == 0 && h.getApproverID() != null) {
                notifier.holidayCancelled(h.getApproverID(), h, cancelReason);   // it was waiting with that approver
            }
        } else if (hs.updateSQLQuery1("UPDATE HolidayRequests h JOIN CompOffBalanceDetails b ON b.staffID = h.staffID AND b.status = 'A'"
                + " SET h.holidayWorkingStatus = 'Cancelled', h.modifiedDate = NOW(), b.allocated = b.allocated - 1, b.balance = b.balance - 1"
                + " WHERE h.HWRID = " + id + " AND h.holidayWorkingStatus = 'Available' AND h.recordStatus = 'A' AND b.balance >= 1") != 2) {
            // one statement: the intimation and the day it gave are taken back together, or nothing changes
            HolidayRequests now = (HolidayRequests) hs.get(HolidayRequests.class.getName(), holidayWorkingId);
            if (now != null && CompOffConstants.HW_AVAILABLE.equals(now.getHolidayWorkingStatus())) {
                throw new CompOffException(null, "Your Comp Off balance does not include this day. Please contact the admin.");
            }
            throw new CompOffException(null, CHANGED);
        }
        // one history row; an Available intimation also gave back its Comp Off day (Allocated - 1)
        BigDecimal allocated = CompOffConstants.HW_PENDING.equals(previous) ? BigDecimal.ZERO : BigDecimal.ONE.negate();
        h.setHolidayWorkingStatus(CompOffConstants.HW_CANCELLED);
        holidayHistory(h, CompOffConstants.HIST_CANCELLED, previous, CompOffConstants.HW_CANCELLED, staffId, cancelReason, allocated,
                HistoryComment.remarks(cancelReason));
    }

    private boolean hasOpenIntimation(Integer staffId, LocalDate workedDate) {
        return !listByDates("SELECT HWRID FROM HolidayRequests WHERE staffID = " + SqlUtil.number(staffId)
                + " AND workRequestDate = :date AND recordStatus = 'A' AND holidayWorkingStatus NOT IN ('Rejected', 'Cancelled')",
                "date", workedDate).isEmpty();
    }

    // =====================================================================
    // Apply Comp Off
    // =====================================================================

    /** Outcome of applyCompOff: the approver and the leave dates that were applied. */
    public static class ApplyResult {
        public final Staffs approver;
        public final List<LocalDate> applied = new ArrayList<LocalDate>();
        /** The requests created, in date order. */
        public final List<CompensatoryOffRequest> requests = new ArrayList<CompensatoryOffRequest>();
        /** Set when a later day could not be applied (the earlier days stay applied). */
        public String stoppedReason;

        ApplyResult(Staffs approver) {
            this.approver = approver;
        }
    }

    /**
     * One CompensatoryOffRequest row per selected date (dates sorted, no repeats); each date uses the oldest
     * Available intimation (FIFO). Used / Balance change on submission. All dates are checked first (applyProblem);
     * if a later date then fails (e.g. another request took the last day at the same moment), the earlier dates stay
     * applied and stoppedReason says why the rest were not.
     */
    public ApplyResult applyCompOff(Integer staffId, String dayType, String shiftType, List<LocalDate> dates, String notes, Notifier notifier) {
        Staffs approver = compOffApprover(staffId);
        if (approver == null) {
            throw new CompOffException(null, "No Comp Off approver is configured for you. Please contact the admin.");
        }
        String problem = applyProblem(staffId, dates);
        if (problem != null) {
            throw new CompOffException("leaveDates", problem);
        }

        ApplyResult result = new ApplyResult(approver);
        for (LocalDate day : dates) {
            String failure = applyOneDay(staffId, approver, day, dayType, shiftType, notes, result.requests, staffId);
            if (failure != null) {
                if (result.applied.isEmpty()) {
                    throw new CompOffException("leaveDates", failure);
                }
                result.stoppedReason = failure;
                break;
            }
            result.applied.add(day);
        }
        if (notifier != null) {
            for (CompensatoryOffRequest r : result.requests) {
                notifier.compOffApproval(approver, r, 1, false);
            }
        }
        return result;
    }

    /**
     * Why the selected dates cannot be applied, or null when they can. Read only, so the action can report it
     * together with the other field errors; applyCompOff checks it again before saving. Each date is checked, in this
     * order: not the worked date of one of the staff member's approved Holiday Intimations, not already applied
     * (Pending / Approved / Completed); then each date needs one Full day of balance - the days available are the
     * balance, never more than the Available intimations behind it. (Past months and the most dates per request are
     * checked by the action.)
     */
    public String applyProblem(Integer staffId, List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return "Compensatory Off date is required.";
        }
        LocalDate first = dates.get(0), last = dates.get(dates.size() - 1);
        java.util.Set<LocalDate> selected = new java.util.HashSet<LocalDate>(dates);
        for (java.util.Date w : this.<java.util.Date>listByDates("SELECT workRequestDate FROM HolidayRequests WHERE staffID = "
                + SqlUtil.number(staffId) + " AND recordStatus = 'A' AND holidayWorkingStatus IN ('Available', 'Used', 'Completed')"
                + " AND workRequestDate BETWEEN :from AND :to ORDER BY workRequestDate", "from", first, "to", last)) {
            if (selected.contains(DateUtil.toLocalDate(w))) {
                return workedDateMessage(w);
            }
        }
        for (java.util.Date t : this.<java.util.Date>listByDates("SELECT leaveDate FROM CompensatoryOffRequest WHERE staffID = "
                + SqlUtil.number(staffId) + " AND status = 'A' AND leaveStatus IN ('Pending', 'Approved', 'Completed')"
                + " AND leaveDate BETWEEN :from AND :to ORDER BY leaveDate", "from", first, "to", last)) {
            if (selected.contains(DateUtil.toLocalDate(t))) {
                return "Comp Off is already applied for " + DateUtil.display(t) + ".";
            }
        }
        int n = dates.size();
        CompOffBalanceDetails cod = summary(staffId);
        BigDecimal balance = cod == null ? BigDecimal.ZERO : cod.getBalance();
        BigDecimal availableDays = balance.min(BigDecimal.valueOf(available(staffId).size()));
        if (availableDays.compareTo(CompOffConstants.FULL_DAY_DURATION.multiply(BigDecimal.valueOf(n))) < 0) {
            return "You have selected (" + n + " day" + (n > 1 ? "s" : "") + "), but your available Comp Off balance is only ("
                    + days(availableDays) + ").";
        }
        return null;
    }

    /** Comp Off cannot be taken on a day the staff member has an approved Holiday Intimation for. */
    static String workedDateMessage(java.util.Date workedDate) {
        return "You worked on " + DateUtil.display(workedDate) + " (Holiday Intimation), so Comp Off cannot be applied for this date.";
    }

    /** 1 day / 2 days (same wording as the page). */
    private static String days(BigDecimal d) {
        return plainDays(d) + (d.compareTo(BigDecimal.ONE) == 0 ? " day" : " days");
    }

    /** A number of days without decimals: 2, 0 (a half day, if one is ever stored, stays 0.5). */
    static String plainDays(BigDecimal d) {
        BigDecimal v = d.setScale(1, java.math.RoundingMode.HALF_UP);
        return v.signum() == 0 ? "0" : v.stripTrailingZeros().toPlainString();
    }

    /**
     * Applies one leave day. Returns null on success, or the reason it could not be applied (nothing left behind).
     * requestedBy = who applied it (the staff member, or the Admin on Apply Comp Off for Staff): the Requested history row.
     */
    private String applyOneDay(Integer staffId, Staffs approver, LocalDate day, String dayType, String shiftType, String notes,
                               List<CompensatoryOffRequest> made, Integer requestedBy) {
        List<HolidayRequests> fifo = available(staffId);
        if (fifo.isEmpty()) {
            return "No Comp Off day is available for " + DateUtil.display(day) + ".";
        }
        BigDecimal duration = CompOffConstants.FULL_DAY_DURATION;

        CompensatoryOffRequest r = new CompensatoryOffRequest();
        r.setCompanyID(CompOffConfig.getCompanyId());
        r.setUserID(CompOffConfig.getCompanyId());
        r.setCreatedDate(new java.util.Date());
        r.setStaffID(staffId);
        r.setLeaveTypeID(compOffLeaveTypeId());
        r.setHolidayWorkingId(fifo.get(0).getHWRID());
        r.setAttendanceDate(fifo.get(0).getWorkRequestDate());
        r.setLeaveDate(DateUtil.toDate(day));
        r.setLeaveDayType(CompOffConstants.HALF_DAY.equals(dayType) ? "HD" : CompOffConstants.LEAVE_DAY_FD);
        r.setLeaveDuration("1");
        r.setShiftType(shiftType);
        r.setLeaveStatus(CompOffConstants.REQ_PENDING);
        r.setLeaveNotes(leaveNotes(day, fifo.get(0).getWorkRequestDate(), notes));
        r.setLeaveApproverID(approverValue(approver));
        r.setStatus(CompOffConstants.ACTIVE);
        hs.save(r);
        if (r.getCOID() == null) {
            return DB_ERROR;
        }
        String rid = SqlUtil.number(r.getCOID());
        r.setKeyID("CO-" + r.getCOID());
        hs.updateSQLQuery1("UPDATE CompensatoryOffRequest SET keyID = " + SqlUtil.text(r.getKeyID()) + " WHERE COID = " + rid);
        // another open request for the same leave date saved at the same moment: the oldest one stays
        if (!isOldest("SELECT MIN(COID) FROM CompensatoryOffRequest WHERE staffID = " + SqlUtil.number(staffId)
                + " AND leaveDate = " + SqlUtil.date(day) + " AND status = 'A' AND leaveStatus IN ('Pending', 'Approved', 'Completed')",
                r.getCOID())) {
            inactivateRequest(rid);
            return "Comp Off is already applied for " + DateUtil.display(day) + ".";
        }
        // the level-1 approver's step (CompensatoryOffApprovers): the request now waits with them
        if (!steps.open(CompOffConstants.LT_COMP_OFF, r.getCOID(), staffId, 1, approver.getStaffID())) {
            inactivateRequest(rid);
            return DB_ERROR;
        }
        String line = "Comp Off applied for: " + DateUtil.display(day);

        // Claim the oldest intimation that is still Available (another request may take one first)
        HolidayRequests used = null;
        for (HolidayRequests h : fifo) {
            String hid = SqlUtil.number(h.getHWRID());
            if (!h.getHWRID().equals(r.getHolidayWorkingId())) {
                hs.updateSQLQuery1("UPDATE CompensatoryOffRequest SET referenceID = " + SqlUtil.text(String.valueOf(h.getHWRID()))
                        + ", attendanceDate = " + SqlUtil.date(DateUtil.toLocalDate(h.getWorkRequestDate()))
                        + ", leaveNotes = " + SqlUtil.text(leaveNotes(day, h.getWorkRequestDate(), notes)) + " WHERE COID = " + rid);
                r.setHolidayWorkingId(h.getHWRID());
                r.setAttendanceDate(h.getWorkRequestDate());
            }
            // one statement: the intimation becomes Used and the day is taken from the balance together, or nothing changes
            if (hs.updateSQLQuery1("UPDATE HolidayRequests h JOIN CompOffBalanceDetails b ON b.staffID = h.staffID AND b.status = 'A'"
                    + " SET h.holidayWorkingStatus = 'Used', h.modifiedDate = NOW(), b.used = b.used + " + SqlUtil.number(duration)
                    + ", b.balance = b.balance - " + SqlUtil.number(duration)
                    + " WHERE h.HWRID = " + hid + " AND h.staffID = " + SqlUtil.number(staffId)
                    + " AND h.holidayWorkingStatus = 'Available' AND h.recordStatus = 'A' AND b.balance >= " + SqlUtil.number(duration)) == 2) {
                used = h;
                break;
            }
            CompOffBalanceDetails now = summary(staffId);
            if (now == null || now.getBalance().compareTo(duration) < 0) {
                inactivateRequest(rid);
                return "Your Comp Off balance is not enough for " + DateUtil.display(day) + ".";
            }
        }
        if (used == null) {
            inactivateRequest(rid);
            return "No Comp Off day is available for " + DateUtil.display(day) + ".";
        }
        r.setCurrentApprovalLevel(1);
        compOffHistory(r, CompOffConstants.HIST_REQUESTED, null, CompOffConstants.REQ_PENDING, used.getWorkRequestDate(), requestedBy,
                duration, line, HistoryComment.none());
        made.add(r);
        return null;
    }

    /** Text the application puts in front of the staff member's notes in CompensatoryOffRequest.leaveNotes. */
    private static final java.util.regex.Pattern NOTES_PREFIX = java.util.regex.Pattern.compile(
            "^Applying Comp off for \\d{2} [A-Za-z]{3} \\d{4} for Holiday worked on \\d{2} [A-Za-z]{3} \\d{4}(~~|$)");

    /**
     * CompensatoryOffRequest.leaveNotes: "Applying Comp off for 08 Sep 2026 for Holiday worked on 03 Aug 2026~~<notes>"
     * (the notes the staff member entered; without "~~" when there are none).
     */
    static String leaveNotes(LocalDate day, java.util.Date workedDate, String notes) {
        String line = "Applying Comp off for " + DateUtil.display(day) + " for Holiday worked on " + DateUtil.display(workedDate);
        return notes == null || notes.isEmpty() ? line : line + "~~" + notes;
    }

    /** The staff member's own notes from a leaveNotes value (the generated "Applying Comp off for ..." part removed). */
    public static String userNotes(String leaveNotes) {
        if (leaveNotes == null) {
            return null;
        }
        java.util.regex.Matcher m = NOTES_PREFIX.matcher(leaveNotes);
        return m.find() ? leaveNotes.substring(m.end()) : leaveNotes;
    }

    private void inactivateRequest(String rid) {
        hs.updateSQLQuery1("UPDATE CompensatoryOffRequest SET status = 'I', modifiedDate = NOW() WHERE COID = " + rid);
        steps.inactivate(CompOffConstants.LT_COMP_OFF, Integer.valueOf(rid));
    }

    /** Cancels a Pending / Approved request for today or later: the intimation is Available again and the day returns to the balance. */
    public void cancelCompOff(Integer staffId, int compOffRequestId, String cancelReason, LocalDate today, Notifier notifier) {
        CompensatoryOffRequest r = (CompensatoryOffRequest) hs.get(CompensatoryOffRequest.class.getName(), compOffRequestId);
        if (r == null || !staffId.equals(r.getStaffID()) || !CompOffConstants.ACTIVE.equals(r.getStatus())) {
            throw new CompOffException(null, "Comp Off request not found.");
        }
        if (!isCancellable(r, today)) {
            throw new CompOffException(null, "Only Pending or Approved Comp Off requests for the current or a future month can be cancelled.");
        }
        String rid = SqlUtil.number(r.getCOID());
        String previous = r.getLeaveStatus();
        BigDecimal duration = r.getDuration();

        if (!releaseCompOff(r, previous, CompOffConstants.REQ_CANCELLED, "")) {
            CompensatoryOffRequest now = (CompensatoryOffRequest) hs.get(CompensatoryOffRequest.class.getName(), compOffRequestId);
            if (now == null || !previous.equals(now.getLeaveStatus())) {
                throw new CompOffException(null, CHANGED);
            }
            throw new CompOffException(null, "This Comp Off request is out of sync with your balance. Please contact the admin.");
        }
        // anything staged for the approval servlet must not go through
        int staged = hs.updateSQLQuery1("UPDATE MultiCompOffApprovals SET approverStatus = 'Cancelled', processedDate = NOW()"
                + " WHERE COID = " + rid + " AND approverStatus = 'In_Progress' AND status = 'A'");
        steps.close(CompOffConstants.LT_COMP_OFF, r.getCOID(), ApproverSteps.CANCELLED, null, null);
        if (notifier != null && staged == 0 && CompOffConstants.REQ_PENDING.equals(previous) && r.getApproverId() != null) {
            notifier.compOffCancelled(r.getApproverId(), r, cancelReason);     // it was waiting with that approver
        }

        compOffHistory(r, CompOffConstants.HIST_CANCELLED, previous, CompOffConstants.REQ_CANCELLED, r.getAttendanceDate(),
                staffId, duration.negate(), cancelReason, HistoryComment.remarks(cancelReason));
    }

    // =====================================================================
    // Admin: Apply Comp Off for Staff / Comp Off Cancel
    // =====================================================================

    /** One staff member found on Apply Comp Off for Staff (by employee code). */
    public static class AdminPick {
        private final String code;
        private final Staffs staff;
        private final String teamName;
        private final String approvers;
        private final BigDecimal available;
        private final java.util.Date nextWorked;

        AdminPick(String code, Staffs staff, String teamName, String approvers, BigDecimal available, java.util.Date nextWorked) {
            this.code = code;
            this.staff = staff;
            this.teamName = teamName;
            this.approvers = approvers;
            this.available = available;
            this.nextWorked = nextWorked;
        }

        public String getCode() { return code; }
        public Staffs getStaff() { return staff; }
        public String getTeamName() { return teamName; }
        /** "1 Rahul Mehta (TL) -> 2 Anita Desai (PM)", or null when no approver is set. */
        public String getApprovers() { return approvers; }
        /** Comp Off days that can be applied: the balance, never more than the Available intimations. */
        public BigDecimal getAvailable() { return available; }
        public String getAvailableText() { return plainDays(available); }
        /** Worked date of the intimation the next Comp Off uses (oldest Available), or null. */
        public java.util.Date getNextWorked() { return nextWorked; }
    }

    /**
     * Apply Comp Off for Staff: the active, non-Admin staff of the current company with these employee codes (in the
     * order given, case-insensitive). Codes found nowhere go to notFound, staff with no available day to noBalance.
     */
    public List<AdminPick> adminPicks(List<String> codes, List<String> notFound, List<String> noBalance) {
        List<AdminPick> out = new ArrayList<AdminPick>();
        for (String code : codes) {
            List<Staffs> found = this.<Staffs>list("from Staffs where status = 'A' and companyID = " + SqlUtil.number(CompOffConfig.getCompanyId())
                    + " and lower(employeeCode) = :c", "c", code.toLowerCase());
            Staffs st = found.size() == 1 ? found.get(0) : null;
            if (st == null || CompOffConstants.ROLE_ADMIN.equals(st.getAccessRole())) {
                notFound.add(code);
                continue;
            }
            CompOffBalanceDetails cod = summary(st.getStaffID());
            List<HolidayRequests> avail = available(st.getStaffID());
            BigDecimal days = (cod == null ? BigDecimal.ZERO : cod.getBalance()).min(BigDecimal.valueOf(avail.size()));
            if (days.compareTo(CompOffConstants.FULL_DAY_DURATION) < 0) {
                noBalance.add(st.getEmployeeCode() + " " + st.getStaffName());
                continue;
            }
            StringBuilder chain = new StringBuilder();
            int level = 0;
            for (Integer id : chainIds(st.getStaffID())) {
                Staffs a = (Staffs) hs.get(Staffs.class.getName(), id);
                chain.append(level == 0 ? "" : " \u2192 ").append(++level).append(' ').append(a == null ? "#" + id : a.getStaffName()
                        + (a.getUserType() == null ? "" : " (" + a.getUserType().trim() + ")"));
            }
            String team = null;
            if (String.valueOf(st.getTeamID()) != null) {
                List<com.dao.Teams> t = list("from Teams where teamID = " + SqlUtil.number(st.getTeamID()));
                team = t.isEmpty() ? null : t.get(0).getTeamName();
            }
            out.add(new AdminPick(code, st, team, chain.length() == 0 ? null : chain.toString(), days, avail.get(0).getWorkRequestDate()));
        }
        return out;
    }

    /**
     * Apply Comp Off for Staff: one Full day, Day Shift Comp Off for the staff member on the date, saved as Approved at
     * once by the Admin (the request's approver, its one approval step Approved, history Requested + Approved by the
     * Admin). It uses the oldest Available intimation, like the staff member's own request; the same checks apply
     * (applyProblem), any month. Emails: the staff member and the approvers of their chain.
     */
    public CompensatoryOffRequest applyApprovedByAdmin(Staffs admin, Staffs staff, LocalDate day, String notes, Notifier notifier) {
        String problem = applyProblem(staff.getStaffID(), java.util.Collections.singletonList(day));
        if (problem != null) {
            throw new CompOffException("leaveDates", staff.getStaffName() + " (" + staff.getEmployeeCode() + "): " + problem.replace("your ", "the "));
        }
        List<CompensatoryOffRequest> made = new ArrayList<CompensatoryOffRequest>();
        String failure = applyOneDay(staff.getStaffID(), admin, day, CompOffConstants.FULL_DAY, CompOffConstants.ADMIN_SHIFT, notes, made,
                admin.getStaffID());
        if (failure != null) {
            throw new CompOffException("leaveDates", staff.getStaffName() + " (" + staff.getEmployeeCode() + "): " + failure.replace("Your ", "The "));
        }
        CompensatoryOffRequest r = made.get(0);
        if (hs.updateSQLQuery1("UPDATE CompensatoryOffRequest SET leaveStatus = 'Approved', modifiedDate = NOW() WHERE COID = "
                + SqlUtil.number(r.getCOID()) + " AND leaveStatus = 'Pending' AND status = 'A'") != 1) {
            throw new CompOffException(null, CHANGED);
        }
        steps.close(CompOffConstants.LT_COMP_OFF, r.getCOID(), ApproverSteps.APPROVED, notes, new java.util.Date());
        r.setLeaveStatus(CompOffConstants.REQ_APPROVED);
        r.setCurrentApprovalLevel(1);
        compOffHistory(r, CompOffConstants.HIST_APPROVED, CompOffConstants.REQ_PENDING, CompOffConstants.REQ_APPROVED,
                r.getAttendanceDate(), admin.getStaffID(), BigDecimal.ZERO, notes, HistoryComment.remarks(notes));
        if (notifier != null) {
            notifier.compOffAppliedByAdmin(r, admin, notes);
        }
        return r;
    }

    /**
     * Comp Off Cancel (Admin): cancels a Pending, In Process (a decision staged for the approval servlet) or Approved
     * request of any month. Its intimation becomes Available again and the day goes back to the balance (one
     * statement); a staged decision is Cancelled; the open approval step is Cancelled; history Cancelled by the Admin.
     * Emails: the staff member and the approvers of their chain.
     */
    public void cancelByAdmin(Staffs admin, int compOffRequestId, String reason, Notifier notifier) {
        CompensatoryOffRequest r = (CompensatoryOffRequest) hs.get(CompensatoryOffRequest.class.getName(), compOffRequestId);
        Staffs staff = r == null ? null : (Staffs) hs.get(Staffs.class.getName(), r.getStaffID());
        if (r == null || !CompOffConstants.ACTIVE.equals(r.getStatus()) || staff == null || String.valueOf(staff.getCompanyID()) == null
                || staff.getCompanyID() != CompOffConfig.getCompanyId()) {
            throw new CompOffException(null, "Comp Off request not found.");
        }
        String previous = r.getLeaveStatus();
        if (!CompOffConstants.REQ_PENDING.equals(previous) && !CompOffConstants.REQ_APPROVED.equals(previous)) {
            throw new CompOffException(null, "Only Pending, In Process or Approved Comp Off requests can be cancelled.");
        }
        BigDecimal duration = r.getDuration();
        if (!releaseCompOff(r, previous, CompOffConstants.REQ_CANCELLED, "")) {
            CompensatoryOffRequest now = (CompensatoryOffRequest) hs.get(CompensatoryOffRequest.class.getName(), compOffRequestId);
            if (now == null || !previous.equals(now.getLeaveStatus())) {
                throw new CompOffException(null, CHANGED);
            }
            throw new CompOffException(null, "This Comp Off request is out of sync with the staff member's balance (Holiday Intimation or"
                    + " Comp Off balance). Please check the Comp Off Balance and Details Report.");
        }
        hs.updateSQLQuery1("UPDATE MultiCompOffApprovals SET approverStatus = 'Cancelled', processedDate = NOW()"
                + " WHERE COID = " + SqlUtil.number(r.getCOID()) + " AND approverStatus = 'In_Progress' AND status = 'A'");
        steps.close(CompOffConstants.LT_COMP_OFF, r.getCOID(), ApproverSteps.CANCELLED, reason, null);
        compOffHistory(r, CompOffConstants.HIST_CANCELLED, previous, CompOffConstants.REQ_CANCELLED, r.getAttendanceDate(),
                admin.getStaffID(), duration.negate(), reason, HistoryComment.remarks(reason));
        r.setLeaveStatus(CompOffConstants.REQ_CANCELLED);
        if (notifier != null) {
            notifier.compOffCancelledByAdmin(r, admin, reason);
        }
    }

    /**
     * Ends a Comp Off request (fromStatus -> toStatus), sets its intimation back to Available and gives the day back
     * to the balance (Used - duration, Balance + duration) in ONE statement, so the three always change together.
     * requestWhere adds conditions on the request (alias c). False when nothing changed (already decided, or the
     * intimation / balance does not match the request).
     */
    boolean releaseCompOff(CompensatoryOffRequest r, String fromStatus, String toStatus, String requestWhere) {
        Integer hw = r.getHolidayWorkingId();
        if (hw == null) {
            return false;
        }
        String d = SqlUtil.number(r.getDuration());
        return hs.updateSQLQuery1("UPDATE CompensatoryOffRequest c"
                + " JOIN HolidayRequests h ON h.HWRID = " + SqlUtil.number(hw)
                + " JOIN CompOffBalanceDetails b ON b.staffID = c.staffID AND b.status = 'A'"
                + " SET c.leaveStatus = " + SqlUtil.code(toStatus) + ", c.modifiedDate = NOW(),"
                + " h.holidayWorkingStatus = 'Available', h.modifiedDate = NOW(), b.used = b.used - " + d + ", b.balance = b.balance + " + d
                + " WHERE c.COID = " + SqlUtil.number(r.getCOID()) + " AND c.leaveStatus = " + SqlUtil.code(fromStatus)
                + " AND c.status = 'A' AND h.holidayWorkingStatus = 'Used' AND h.recordStatus = 'A' AND h.staffID = c.staffID"
                + " AND b.used >= " + d + requestWhere) == 3;
    }

    /**
     * A Pending / Approved request can be cancelled while its leave date is in the current month or a later one;
     * requests for past months cannot be cancelled.
     */
    static boolean isCancellable(CompensatoryOffRequest r, LocalDate today) {
        boolean open = CompOffConstants.REQ_PENDING.equals(r.getLeaveStatus())
                || CompOffConstants.REQ_APPROVED.equals(r.getLeaveStatus());
        return open && !DateUtil.toLocalDate(r.getLeaveDate()).isBefore(monthStart(today));
    }

    /** First day of today's month: the earliest leave date that can be applied for or cancelled. */
    public static LocalDate monthStart(LocalDate today) {
        return today.withDayOfMonth(1);
    }

    // =====================================================================
    // History rows
    // =====================================================================

    /**
     * History row of a Holiday Intimation event (keyID 'HW-<HWRID>', leave type 'Holiday Intimation').
     * allocatedDelta = the change just made to Allocated / Balance (+1 allocated, -1 reversed, 0 none).
     * The level is h.currentApprovalLevel when set, else the level of the approver it is waiting with;
     * referenceID = that approver.
     */
    boolean holidayHistory(HolidayRequests h, String action, String previous, String next, Integer actorId, String remarks,
                           BigDecimal allocatedDelta, HistoryComment comment) {
        Integer level = h.getCurrentApprovalLevel() != null ? h.getCurrentApprovalLevel()
                : levelOf(h.getStaffID(), h.getApproverID());
        return history(CompOffConstants.LT_HOLIDAY, "HW-" + h.getHWRID(), action, h.getStaffID(), h.getHWRID(), 0,
                h.getWorkRequestDate(), null, h.getApproverID(), actorId, previous, next, level, allocatedDelta, BigDecimal.ZERO, remarks,
                comment);
    }

    /**
     * History row of a Comp Off request event (keyID 'CO-<COID>', leave type 'Compensatory OFF').
     * usedDelta = the change just made to Used (+duration requested, -duration cancelled / rejected, 0 none).
     */
    void compOffHistory(CompensatoryOffRequest r, String action, String previous, String next, java.util.Date workedDate, Integer actorId,
                        BigDecimal usedDelta, String remarks, HistoryComment comment) {
        Integer level = r.getCurrentApprovalLevel() != null ? r.getCurrentApprovalLevel()
                : levelOf(r.getStaffID(), r.getApproverId());
        history(CompOffConstants.LT_COMP_OFF, "CO-" + r.getCOID(), action, r.getStaffID(), r.getHolidayWorkingId(),
                r.getCOID(), workedDate, r.getLeaveDate(), r.getApproverId(), actorId, previous, next, level, BigDecimal.ZERO,
                usedDelta, remarks, comment);
    }

    /**
     * Writes one CompOffBalanceDetailsHistory row. The balance columns hold the balance AFTER the event;
     * the balance before it is worked back from the deltas and kept in leaveHistoryActionCommands ("prev").
     */
    private boolean history(String leaveType, String keyId, String action, Integer staffId, Integer hrId, Integer coId,
                            java.util.Date fromDate, java.util.Date leaveToDate, Integer referenceId, Integer actorId,
                            String previous, String next, Integer level, BigDecimal allocatedDelta, BigDecimal usedDelta, String remarks,
                            HistoryComment comment) {
        CompOffBalanceDetails cod = summary(staffId);
        BigDecimal allocated = cod == null ? BigDecimal.ZERO : cod.getAllocated();
        BigDecimal used = cod == null ? BigDecimal.ZERO : cod.getUsed();
        BigDecimal balance = cod == null ? BigDecimal.ZERO : cod.getBalance();
        String prev = allocated.subtract(allocatedDelta).intValue() + "/" + used.subtract(usedDelta).intValue() + "/"
                + balance.subtract(allocatedDelta.subtract(usedDelta)).intValue();

        CompOffBalanceDetailsHistory row = new CompOffBalanceDetailsHistory();
        // whole seconds: MySQL rounds the milliseconds of a DATETIME, so createdDate and the comment's time stay equal
        row.setCreatedDate(new java.util.Date(System.currentTimeMillis() / 1000 * 1000));
        HistoryComment c = comment == null ? HistoryComment.none() : comment;
        Staffs actor = actorId == null ? null : findStaff(actorId);
        row.setHistoryActionComments(c.build(leaveType, action, fromDate, leaveToDate, actor == null ? null : actor.getStaffName(),
                level, c.when() == null ? row.getCreatedDate() : c.when()));
        row.setKeyID(keyId);
        row.setHRID(hrId == null ? 0 : hrId);
        row.setCOID(coId == null ? 0 : coId);
        row.setHolidayWorkedDate(fromDate);
        row.setCompOffLeaveDate(leaveToDate);
        row.setCompanyID(CompOffConfig.getCompanyId());
        row.setDuration("1");
        row.setHistoryAction(action);
        row.setLeaveType(leaveType);
        row.setLeaveTypeVal(CompOffConstants.LEAVE_DAY_FD);
        row.setReferenceID(referenceId == null ? null : String.valueOf(referenceId));
        row.setStaffID(staffId);
        row.setStatus(CompOffConstants.ACTIVE);
        row.setTimesheetsID(0);
        row.setAvailableDays(allocated.intValue());
        row.setUsedDays(used.intValue());
        row.setBalanceDays(balance.intValue());
        row.setLeaveHistoryActionCommands(CompOffBalanceDetailsHistory.commands(actorId, previous, next, level, prev, remarks));
        hs.save(row);
        if (row.getCOHID() == null) {
            LOG.warning("History row not saved: " + action + " " + keyId + " for staff " + staffId);
            return false;
        }
        return true;
    }

    /**
     * Creates the staff member's balance row (0 / 0 / 0) when there is none. One Active row per staff member:
     * if two were created at the same moment, the newer one is set to Status = 'I' (it is still 0 / 0 / 0).
     */
    void ensureBalanceRow(Integer staffId) {
        if (summary(staffId) != null) {
            return;
        }
        CompOffBalanceDetails row = new CompOffBalanceDetails();
        row.setStaffID(staffId);
        row.setAllocated(BigDecimal.ZERO);
        row.setUsed(BigDecimal.ZERO);
        row.setBalance(BigDecimal.ZERO);
        row.setStatus(CompOffConstants.ACTIVE);
        hs.save(row);
        if (row.getCOBDID() != null && !isOldest("SELECT MIN(COBDID) FROM CompOffBalanceDetails WHERE staffID = "
                + SqlUtil.number(staffId) + " AND status = 'A'", row.getCOBDID())) {
            hs.updateSQLQuery1("UPDATE CompOffBalanceDetails SET status = 'I' WHERE COBDID = " + SqlUtil.number(row.getCOBDID()));
        }
    }

    /**
     * "Oldest row wins" check used instead of a unique key: true when id is the smallest id returned by minSql
     * (a SELECT MIN(id) ... over the Active rows that must be single). Values in the SQL must come from SqlUtil.
     */
    boolean isOldest(String minSql, Integer id) {
        Integer oldest = firstId(minSql);
        return oldest == null || oldest.equals(id);
    }

    /** First column of the first row of a native SQL query (an id), or null. Values in the SQL must come from SqlUtil. */
    Integer firstId(String sql) {
        java.util.Set<Integer> found = ids(sql);
        return found.isEmpty() ? null : found.iterator().next();
    }

    // =====================================================================
    // Query helpers
    // =====================================================================

    /** First column of a native SQL query as a set of ints (ids). Other values in the SQL must come from SqlUtil. */
    @SuppressWarnings("unchecked")
    java.util.Set<Integer> ids(String sql) {
        List<Object> rows = hs.getSQLList(sql);
        if (rows == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        java.util.Set<Integer> out = new java.util.HashSet<Integer>();
        for (Object o : rows) {
            Object v = o instanceof Object[] ? ((Object[]) o)[0] : o;
            if (v != null) {                       // e.g. SELECT MIN(...) over no rows
                out.add(((Number) v).intValue());
            }
        }
        return out;
    }

    /**
     * Native SQL with named date parameters (name, LocalDate, ...) through HibernateService.getListParameterDate.
     * Other values in the SQL must come from SqlUtil. Throws if the database call failed.
     */
    @SuppressWarnings("unchecked")
    <T> List<T> listByDates(String sql, Object... nameValuePairs) {
        List<String> names = new ArrayList<String>();
        List<java.util.Date> values = new ArrayList<java.util.Date>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            names.add((String) nameValuePairs[i]);
            values.add(DateUtil.toDate((LocalDate) nameValuePairs[i + 1]));
        }
        List<T> rows = hs.getListParameterDate(sql, names, values);
        if (rows == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        return rows;
    }

    /**
     * HQL query with named String parameters (name, value, name, value, ...). Throws if the database call failed.
     * HibernateService.getListWithParameter binds String values only, so numeric ids are written into the
     * query with SqlUtil.number instead of being passed as parameters.
     */
    @SuppressWarnings("unchecked")
    <T> List<T> list(String hql, String... nameValuePairs) {
        List<String> names = new ArrayList<String>();
        List<String> values = new ArrayList<String>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            names.add(nameValuePairs[i]);
            values.add(nameValuePairs[i + 1]);
        }
        List<T> rows = names.isEmpty() ? hs.getList(hql) : hs.getListWithParameter(hql, names, values);
        if (rows == null) {
            throw new CompOffException(null, DB_ERROR);
        }
        return rows;
    }
}
