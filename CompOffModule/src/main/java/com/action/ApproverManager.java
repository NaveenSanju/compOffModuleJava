package com.action;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.dao.CompOffConstants;
import com.dao.LeaveSettingsCompOff;
import com.dao.LeaveSettingsHistory;
import com.dao.Staffs;
import com.dao.Teams;
import com.dao.UserTypes;
import com.serviceimpl.HibernateService;
import com.serviceimpl.HibernateUtil;
import com.util.CompOffConfig;
import com.util.SqlUtil;

/**
 * Approver Management (Admin): one approver chain per staff member, used for both Holiday Intimation and
 * Comp Off. The chain is stored in LeaveSettingsCompOff: one Active row per staff member (found by staffID;
 * leaveTypeID / leaveTypeName are not used: a new row gets leaveTypeID 0 and leaveTypeName NULL); every change is recorded in LeaveSettingsHistory.
 *
 * Rules (checked here, the page checks the same):
 *   - 1 to MAX_LEVELS levels, no approver twice, every approver an Active staff member with the Approver role;
 *   - a staff member cannot approve themselves; Admin users have no chain;
 *   - replacing an existing chain needs the Admin's confirmation (confirmReplace);
 *   - requests already waiting keep the approver of their current level; later levels follow the new chain.
 * Saving one staff member: their Active row is updated in place (inserted only when they have none); any other Active
 * row of theirs is set to 'I', so there are never two. If two Admins save at the same moment, the latest save wins.
 */
public class ApproverManager {

    private static final Logger LOG = Logger.getLogger(ApproverManager.class.getName());

    public static final int MAX_LEVELS = 3;
    public static final String ASSIGNED = "Assigned";
    public static final String CHANGED = "Changed";
    private static final DateTimeFormatter BATCH_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final HibernateService hs = new HibernateUtil();
    private final CompOffManager manager = new CompOffManager();
    private final ApprovalManager approvals = new ApprovalManager();

    // =====================================================================
    // Page data
    // =====================================================================

    /** One approver in a chain, for display. */
    public static class Level {
        private final int level;
        private final Integer staffId;
        private final String name;
        private final String code;
        private final String userType;
        private final boolean active;

        Level(int level, Integer staffId, String name, String code, String userType, boolean active) {
            this.level = level;
            this.staffId = staffId;
            this.name = name;
            this.code = code;
            this.userType = userType;
            this.active = active;
        }

        public int getLevel() { return level; }
        public Integer getStaffId() { return staffId; }
        public String getName() { return name; }
        public String getCode() { return code; }
        public String getUserType() { return userType; }
        public boolean isActive() { return active; }
    }

    /** One history row, for display. */
    public static class Change {
        private final Date changedOn;
        private final String changedBy;
        private final List<Level> before;
        private final List<Level> after;
        private final String reason;

        Change(Date changedOn, String changedBy, List<Level> before, List<Level> after, String reason) {
            this.changedOn = changedOn;
            this.changedBy = changedBy;
            this.before = before;
            this.after = after;
            this.reason = reason;
        }

        public Date getChangedOn() { return changedOn; }
        public String getChangedBy() { return changedBy; }
        public List<Level> getBefore() { return before; }
        public List<Level> getAfter() { return after; }
        public String getReason() { return reason; }
    }

    /** One staff member and their chain. */
    public static class Row {
        private Integer staffId;
        private String staffName;
        private String employeeCode;
        private Integer teamId;
        private String teamName;
        private Integer userTypeId;
        private String userTypeName;
        private List<Level> chain = new ArrayList<Level>();
        private Date lastChanged;
        private String lastChangedBy;
        private List<Change> history = new ArrayList<Change>();

        public Integer getStaffId() { return staffId; }
        public String getStaffName() { return staffName; }
        public String getEmployeeCode() { return employeeCode; }
        public Integer getTeamId() { return teamId; }
        public String getTeamName() { return teamName; }
        public Integer getUserTypeId() { return userTypeId; }
        public String getUserTypeName() { return userTypeName; }
        public List<Level> getChain() { return chain; }
        /** Holiday Intimation and Comp Off chains are different (saving makes them the same). */
        public Date getLastChanged() { return lastChanged; }
        public String getLastChangedBy() { return lastChangedBy; }
        public List<Change> getHistory() { return history; }
        public boolean isHasApprovers() { return !chain.isEmpty(); }

        /** Approver StaffIds in level order, comma separated (page data). */
        public String getChainIds() {
            List<String> ids = new ArrayList<String>();
            for (Level l : chain) {
                ids.add(String.valueOf(l.getStaffId()));
            }
            return String.join(",", ids);
        }

        /** "Anita Desai (APR202) → Rahul Mehta (APR201)" (page data for the confirmation lists). */
        public String getChainText() {
            List<String> parts = new ArrayList<String>();
            for (Level l : chain) {
                parts.add(l.getName() + " (" + l.getCode() + ")");
            }
            return String.join(" → ", parts);
        }
    }

    /** An approver that can be chosen: Active staff with the Approver role. */
    public static class Option {
        private final Integer staffId;
        private final String label;

        Option(Integer staffId, String label) {
            this.staffId = staffId;
            this.label = label;
        }

        public Integer getStaffId() { return staffId; }
        public String getLabel() { return label; }
    }

    /** Everything the page shows. */
    public static class Page {
        private List<Row> rows = new ArrayList<Row>();
        private int totalStaff;
        private int withApprovers;
        private int withoutApprovers;
        private int multiLevel;
        private List<Option> approverOptions = new ArrayList<Option>();
        private List<UserTypes> userTypes = new ArrayList<UserTypes>();

        public List<Row> getRows() { return rows; }
        public int getTotalStaff() { return totalStaff; }
        public int getWithApprovers() { return withApprovers; }
        public int getWithoutApprovers() { return withoutApprovers; }
        public int getMultiLevel() { return multiLevel; }
        public List<Option> getApproverOptions() { return approverOptions; }
        public List<UserTypes> getUserTypes() { return userTypes; }
    }

    /** Lookup data shared by the page and saving. */
    private final class Data {
        final Map<Integer, Staffs> staff = new HashMap<Integer, Staffs>();
        final Map<Integer, String> teams = new HashMap<Integer, String>();
        final Map<Integer, UserTypes> userTypes = new LinkedHashMap<Integer, UserTypes>();
        /** staffId -> their Active row (the highest LSID, should there be several). */
        final Map<Integer, LeaveSettingsCompOff> settings = new HashMap<Integer, LeaveSettingsCompOff>();

        Data() {
            for (Staffs s : manager.<Staffs>list("from Staffs")) {
                staff.put(s.getStaffID(), s);
            }
            for (Teams t : manager.<Teams>list("from Teams where status = 'A' and companyID = " + SqlUtil.number(CompOffConfig.getCompanyId()))) {
                teams.put(t.getTeamID(), t.getTeamName());
            }
            for (UserTypes u : manager.<UserTypes>list("from UserTypes where status = 'A' and companyID = "
                    + SqlUtil.number(CompOffConfig.getCompanyId()) + " order by userTypeName")) {
                userTypes.put(u.getUserTypeID(), u);
            }
            for (LeaveSettingsCompOff ls : manager.<LeaveSettingsCompOff>list("from LeaveSettingsCompOff where status = 'A' and companyID = "
                    + SqlUtil.number(CompOffConfig.getCompanyId()) + " order by LSID")) {
                settings.put(ls.getStaffID(), ls);      // ordered by id: the latest wins
            }
        }

        String userTypeCode(Staffs s) {
            return s == null || s.getUserType() == null || s.getUserType().trim().isEmpty() ? "NA" : s.getUserType().trim();
        }

        /** Current chain string of a staff member ('PM~~6, TL~~5'); null = none. */
        String chainString(Integer staffId) {
            LeaveSettingsCompOff ls = settings.get(staffId);
            return ls == null || ls.approverIds().isEmpty() ? null : ls.getLeaveApproverID();
        }

        List<Level> levels(String chainString) {
            List<Level> out = new ArrayList<Level>();
            LeaveSettingsCompOff parse = new LeaveSettingsCompOff();
            parse.setLeaveApproverID(chainString);
            int n = 0;
            for (Integer id : parse.approverIds()) {
                Staffs a = staff.get(id);
                n++;
                out.add(new Level(n, id, a == null ? "Unknown staff #" + id : a.getStaffName(),
                        a == null ? "" : a.getEmployeeCode(), userTypeCode(a), a != null && CompOffConstants.ACTIVE.equals(a.getStatus())));
            }
            return out;
        }

        String name(Integer staffId) {
            Staffs s = staff.get(staffId);
            return s == null ? "#" + staffId : s.getStaffName() + " (" + s.getEmployeeCode() + ")";
        }
    }

    /**
     * Active staff (not Admin) with their chains, filtered by team, name / code text, approver, user type and
     * "only without approvers". The summary counts always cover every Active staff member.
     */
    public Page page(Integer teamId, String query, Integer approvedBy, Integer userTypeId, boolean noneOnly) {
        Data d = new Data();
        Page p = new Page();
        p.userTypes.addAll(d.userTypes.values());
        for (Staffs a : approvers(d)) {
            String team = String.valueOf(a.getTeamID()) == null ? null : d.teams.get(a.getTeamID());
            UserTypes u = String.valueOf(a.getUserTypeID()) == null ? null : d.userTypes.get(a.getUserTypeID());
            p.approverOptions.add(new Option(a.getStaffID(), a.getStaffName() + " (" + a.getEmployeeCode()
                    + (u == null ? "" : " · " + u.getUserTypeName()) + (team == null ? "" : " · " + team) + ")"));
        }

        Map<Integer, LeaveSettingsHistory> lastChange = new HashMap<Integer, LeaveSettingsHistory>();
        Map<Integer, List<LeaveSettingsHistory>> histories = new HashMap<Integer, List<LeaveSettingsHistory>>();
        for (LeaveSettingsHistory h : manager.<LeaveSettingsHistory>list("from LeaveSettingsHistory where status = 'A' and companyID = "
                + SqlUtil.number(CompOffConfig.getCompanyId()) + " order by createdDate, LSHID")) {
            lastChange.put(h.getStaffID(), h);
            List<LeaveSettingsHistory> l = histories.get(h.getStaffID());
            if (l == null) {
                l = new ArrayList<LeaveSettingsHistory>();
                histories.put(h.getStaffID(), l);
            }
            l.add(h);
        }

        String q = query == null ? "" : query.trim().toLowerCase(Locale.ENGLISH);
        List<Staffs> people = new ArrayList<Staffs>();
        for (Staffs s : d.staff.values()) {
            if (CompOffConstants.ACTIVE.equals(s.getStatus()) && !CompOffConstants.ROLE_ADMIN.equals(s.getAccessRole()) && ownCompany(s)) {
                people.add(s);
            }
        }
        Collections.sort(people, (a, b) -> a.getStaffName().compareToIgnoreCase(b.getStaffName()));

        for (Staffs s : people) {
            Row r = new Row();
            r.staffId = s.getStaffID();
            r.staffName = s.getStaffName();
            r.employeeCode = s.getEmployeeCode();
            r.teamId = s.getTeamID();
            r.teamName = String.valueOf(s.getTeamID()) == null ? null : d.teams.get(s.getTeamID());
            r.userTypeId = s.getUserTypeID();
            UserTypes u = String.valueOf(s.getUserTypeID()) == null ? null : d.userTypes.get(s.getUserTypeID());
            r.userTypeName = u == null ? null : u.getUserTypeName();
            r.chain = d.levels(d.chainString(s.getStaffID()));
            LeaveSettingsCompOff ls = d.settings.get(s.getStaffID());
            if (ls != null) {
                r.lastChanged = ls.getModifiedDate() != null ? ls.getModifiedDate() : ls.getCreatedDate();
            }
            LeaveSettingsHistory last = lastChange.get(s.getStaffID());
            if (last != null) {
                r.lastChanged = last.getCreatedDate();
                r.lastChangedBy = d.name(last.getChangedBy());
            }
            List<LeaveSettingsHistory> hist = histories.get(s.getStaffID());
            if (hist != null) {
                for (int i = hist.size() - 1; i >= 0; i--) {           // newest first
                    LeaveSettingsHistory h = hist.get(i);
                    r.history.add(new Change(h.getCreatedDate(), d.name(h.getChangedBy()), d.levels(h.getOldLeaveApproverID()),
                            d.levels(h.getNewLeaveApproverID()), h.getReason()));
                }
            }

            p.totalStaff++;
            if (r.chain.isEmpty()) {
                p.withoutApprovers++;
            } else {
                p.withApprovers++;
            }
            if (r.chain.size() > 1) {
                p.multiLevel++;
            }

            if (teamId != null && !teamId.equals(r.teamId)) {
                continue;
            }
            if (userTypeId != null && !userTypeId.equals(r.userTypeId)) {
                continue;
            }
            if (noneOnly && !r.chain.isEmpty()) {
                continue;
            }
            if (!q.isEmpty() && !r.staffName.toLowerCase(Locale.ENGLISH).contains(q)
                    && !r.employeeCode.toLowerCase(Locale.ENGLISH).contains(q)) {
                continue;
            }
            if (approvedBy != null) {
                boolean found = false;
                for (Level l : r.chain) {
                    found |= approvedBy.equals(l.getStaffId());
                }
                if (!found) {
                    continue;
                }
            }
            p.rows.add(r);
        }
        return p;
    }

    /** True for a staff member of the current company (the logged-in Admin's). */
    private static boolean ownCompany(Staffs s) {
        return String.valueOf(s.getCompanyID()) != null && s.getCompanyID() == CompOffConfig.getCompanyId();
    }

    /** Active staff with the Approver role of the current company, by name. */
    private List<Staffs> approvers(Data d) {
        List<Staffs> out = new ArrayList<Staffs>();
        for (Staffs s : d.staff.values()) {
            if (CompOffConstants.ACTIVE.equals(s.getStatus()) && CompOffConstants.ROLE_APPROVER.equals(s.getAccessRole()) && ownCompany(s)) {
                out.add(s);
            }
        }
        Collections.sort(out, (a, b) -> a.getStaffName().compareToIgnoreCase(b.getStaffName()));
        return out;
    }

    // =====================================================================
    // Saving
    // =====================================================================

    /** Outcome of a save: how many changed, how many already had this chain, and the ones that could not be saved. */
    public static class Result {
        private int saved;
        private int unchanged;
        private int reassigned;
        private final List<String> failed = new ArrayList<String>();

        public int getSaved() { return saved; }
        public int getUnchanged() { return unchanged; }
        /** Waiting approvals set to the Admin because their approver is no longer in the chain at that level. */
        public int getReassigned() { return reassigned; }
        public List<String> getFailed() { return failed; }
    }

    /**
     * Gives every staff member in staffIds the chain approverIds (level order). Several staff = one batch
     * (shared batchID in LeaveSettingsHistory). Throws CompOffException (nothing saved) when the chain is not
     * valid, or when a staff member already has approvers and confirmReplace is false.
     */
    public Result save(Staffs admin, List<Integer> staffIds, List<Integer> approverIds, String reason, boolean confirmReplace) {
        if (staffIds.isEmpty()) {
            throw new CompOffException(null, "Select at least one staff member.");
        }
        if (approverIds.isEmpty()) {
            throw new CompOffException(null, "Select at least one approver.");
        }
        if (approverIds.size() > MAX_LEVELS) {
            throw new CompOffException(null, "At most " + MAX_LEVELS + " approval levels are allowed.");
        }
        Data d = new Data();
        Set<Integer> seen = new HashSet<Integer>();
        Map<Integer, Staffs> approverById = new HashMap<Integer, Staffs>();
        for (Staffs a : approvers(d)) {
            approverById.put(a.getStaffID(), a);
        }
        List<String> parts = new ArrayList<String>();
        for (int i = 0; i < approverIds.size(); i++) {
            Integer id = approverIds.get(i);
            Staffs a = approverById.get(id);
            if (a == null) {
                throw new CompOffException(null, "The level " + (i + 1) + " approver must be an active staff member with the Approver role.");
            }
            if (!seen.add(id)) {
                throw new CompOffException(null, a.getStaffName() + " is chosen for more than one level.");
            }
            parts.add(d.userTypeCode(a) + "~~" + id);
        }
        String chain = String.join(", ", parts);
        Staffs level1 = approverById.get(approverIds.get(0));

        // check everyone first: nothing is written when the confirmation is missing
        List<Staffs> targets = new ArrayList<Staffs>();
        Result result = new Result();
        boolean replacing = false;
        for (Integer id : new java.util.LinkedHashSet<Integer>(staffIds)) {
            Staffs s = d.staff.get(id);
            if (s == null || !CompOffConstants.ACTIVE.equals(s.getStatus()) || CompOffConstants.ROLE_ADMIN.equals(s.getAccessRole()) || !ownCompany(s)) {
                result.failed.add((s == null ? "#" + id : d.name(id)) + ": not an active staff member");
                continue;
            }
            if (seen.contains(id)) {
                result.failed.add(d.name(id) + ": cannot approve their own requests");
                continue;
            }
            replacing |= d.chainString(id) != null;
            targets.add(s);
        }
        if (replacing && !confirmReplace) {
            throw new CompOffException(null, "Confirm that the existing approvers will be replaced.");
        }

        String batchId = staffIds.size() > 1
                ? admin.getEmployeeCode() + "-" + LocalDateTime.now(CompOffConfig.getZone()).format(BATCH_TIME) : null;
        for (Staffs s : targets) {
            String old = d.chainString(s.getStaffID());
            LeaveSettingsCompOff current = d.settings.get(s.getStaffID());
            if (current != null && chain.equals(old) && current.approverIds().equals(approverIds)) {
                result.unchanged++;
                continue;
            }
            if (saveOne(admin, s, level1, chain, old, current, batchId, reason)) {
                result.saved++;
                // requests waiting with an approver who is not in the new chain at that level now wait with this Admin
                result.reassigned += approvals.reassignToAdmin(s.getStaffID(), admin.getStaffID());
            } else {
                result.failed.add(d.name(s.getStaffID()) + ": could not be saved, please try again");
            }
        }
        return result;
    }

    /**
     * Writes the chain to the staff member's Active row (updated in place; inserted only when there is none), sets any
     * other Active row of theirs to 'I', then the history row. False if the write failed.
     */
    private boolean saveOne(Staffs admin, Staffs s, Staffs level1, String chain, String old, LeaveSettingsCompOff current,
                            String batchId, String reason) {
        int company = CompOffConfig.getCompanyId();
        Date now = new Date();
        boolean sameTeam = String.valueOf(level1.getTeamID()) != null && String.valueOf(level1.getTeamID()).equals(s.getTeamID());
        Integer team = String.valueOf(s.getTeamID()) == null ? 0 : s.getTeamID();
        Integer lsid;
        if (current != null) {
            if (hs.updateSQLQuery1("UPDATE LeaveSettingsCompOff SET leaveApproverID = " + SqlUtil.text(chain)
                    + ", teamID = " + SqlUtil.number(team) + ", otherTeamApprover = " + SqlUtil.code(sameTeam ? "N" : "Y")
                    + ", teamOfApprover = " + (String.valueOf(level1.getTeamID()) == null ? "NULL" : SqlUtil.number(level1.getTeamID()))
                    + ", modifiedDate = NOW() WHERE LSID = " + SqlUtil.number(current.getLSID()) + " AND status = 'A'") != 1) {
                LOG.warning("LeaveSettingsCompOff row " + current.getLSID() + " not updated for staff " + s.getStaffID());
                return false;
            }
            lsid = current.getLSID();
        } else {
            LeaveSettingsCompOff ls = new LeaveSettingsCompOff();
            ls.setCompanyID(company);
            ls.setCreatedDate(now);
            ls.setLeaveAccuEnds("December");
            ls.setLeaveAccuPeriod("Yearly");
            ls.setLeaveAccuStarts("January");
            ls.setLeaveApproverResp("Approve");
            ls.setLeaveCarriedOver("No");
            ls.setLeaveCarriedOverPeriod("0");
            ls.setLeaveDaysPerMonth("0");
            ls.setLeaveApproverID(chain);
            ls.setStaffID(s.getStaffID());
            ls.setStatus(CompOffConstants.ACTIVE);
            ls.setTeamID(team);
            ls.setUserID(company);
            ls.setOtherTeamApprover(sameTeam ? "N" : "Y");
            ls.setTeamOfApprover(level1.getTeamID());
            ls.setLeaveTypeID(0);                       // not used (one chain for both flows); leaveTypeName stays NULL
            hs.save(ls);
            if (ls.getLSID() == null) {
                LOG.warning("LeaveSettingsCompOff row not saved for staff " + s.getStaffID());
                return false;
            }
            lsid = ls.getLSID();
        }
        // never two Active rows for one staff member: any other one becomes history
        String others = "SELECT LSID FROM LeaveSettingsCompOff WHERE staffID = " + SqlUtil.number(s.getStaffID()) + " AND companyID = "
                + SqlUtil.number(company) + " AND status = 'A' AND LSID <> " + SqlUtil.number(lsid) + " ORDER BY LSID";
        List<String> oldIds = new ArrayList<String>();
        for (Integer id : new java.util.TreeSet<Integer>(manager.ids(others))) {
            oldIds.add(String.valueOf(id));
        }
        if (!oldIds.isEmpty()) {
            hs.updateSQLQuery1("UPDATE LeaveSettingsCompOff SET status = 'I', modifiedDate = NOW() WHERE LSID IN (" + String.join(",", oldIds)
                    + ") AND status = 'A'");
        }

        LeaveSettingsHistory h = new LeaveSettingsHistory();
        h.setCompanyID(company);
        h.setStaffID(s.getStaffID());
        h.setHistoryAction(old == null ? ASSIGNED : CHANGED);
        h.setOldLeaveApproverID(old);
        h.setNewLeaveApproverID(chain);
        h.setOldLSIDs(oldIds.isEmpty() ? null : String.join(",", oldIds));
        h.setNewLSIDs(String.valueOf(lsid));
        h.setBatchID(batchId);
        h.setReason(reason);
        h.setChangedBy(admin.getStaffID());
        h.setCreatedDate(now);
        h.setStatus(CompOffConstants.ACTIVE);
        hs.save(h);
        if (h.getLSHID() == null) {
            LOG.warning("LeaveSettingsHistory row not saved for staff " + s.getStaffID() + " (approvers were changed)");
        }
        return true;
    }
}
