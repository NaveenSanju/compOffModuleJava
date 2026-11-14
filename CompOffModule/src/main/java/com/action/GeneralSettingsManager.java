package com.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import com.dao.CompOffConstants;
import com.dao.GeneralSettings;
import com.dao.Staffs;
import com.serviceimpl.HibernateService;
import com.serviceimpl.HibernateUtil;
import com.util.CompOffConfig;
import com.util.SqlUtil;

/**
 * GeneralSettings: the additional HR approver of Holiday Intimations ('holidayApprovalHR') and Comp Off requests
 * ('compOffApprovalHR'), and the Admin page Settings > General Settings that maintains them.
 *
 * One record per paramName: switching it on or off or changing the HR approver updates that record (status,
 * paramValue, staffID, modifiedDate); a record is only inserted when the paramName has none yet. If older databases
 * hold several rows, the newest is the one used and updated. No database constraints are used.
 */
public class GeneralSettingsManager {

    private static final Logger LOG = Logger.getLogger(GeneralSettingsManager.class.getName());

    public static final String HOLIDAY_HR = "holidayApprovalHR";
    public static final String COMP_OFF_HR = "compOffApprovalHR";
    /** paramName -> the flow it belongs to, in page order. */
    private static final String[][] PARAMS = {
            {HOLIDAY_HR, CompOffConstants.LT_HOLIDAY},
            {COMP_OFF_HR, CompOffConstants.LT_COMP_OFF}};

    private final HibernateService hs = new HibernateUtil();
    private final CompOffManager manager = new CompOffManager();

    /** paramName of a flow's HR approver setting ('Holiday Intimation' / 'Compensatory OFF'). */
    public static String hrParam(String leaveTypeName) {
        return CompOffConstants.LT_HOLIDAY.equals(leaveTypeName) ? HOLIDAY_HR : COMP_OFF_HR;
    }

    /** Newest row of the paramName for this company (Active only when activeOnly), or null. */
    public GeneralSettings latest(String paramName, boolean activeOnly) {
        List<GeneralSettings> rows = manager.list("from GeneralSettings where companyID = " + SqlUtil.number(CompOffConfig.getCompanyId())
                + " and paramName = :p" + (activeOnly ? " and status = 'A'" : "") + " order by GSID desc", "p", paramName);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** staffID of the flow's Active HR approver setting, or null when there is none. */
    public Integer activeHrApproverId(String leaveTypeName) {
        GeneralSettings gs = latest(hrParam(leaveTypeName), true);
        return gs == null ? null : gs.valueStaffId();
    }

    /** staffID named by the newest row of the flow's HR setting (Active or not), or null. */
    public Integer latestHrApproverId(String leaveTypeName) {
        GeneralSettings gs = latest(hrParam(leaveTypeName), false);
        return gs == null ? null : gs.valueStaffId();
    }

    // =====================================================================
    // Admin page
    // =====================================================================

    /** One setting on the page: its record (null when there is none yet). */
    public static class Setting {
        private final String paramName;
        private final String title;
        private GeneralSettings current;

        Setting(String paramName, String title) {
            this.paramName = paramName;
            this.title = title;
        }

        public String getParamName() { return paramName; }
        public String getTitle() { return title; }
        public GeneralSettings getCurrent() { return current; }
        public boolean isActive() { return current != null && current.isActive(); }
        public Integer getApproverId() { return current == null ? null : current.valueStaffId(); }
    }

    /** Both HR approver settings. */
    public List<Setting> settings() {
        List<Setting> out = new ArrayList<Setting>();
        for (String[] p : PARAMS) {
            Setting s = new Setting(p[0], p[1]);
            s.current = latest(p[0], false);
            out.add(s);
        }
        return out;
    }

    /** The choices for an HR approver: active staff with the HR user type and the Approver role (who can approve), by name. */
    /** True for a staff member who may act as the HR approver: active, Approver role, HR user type (same rule as the dropdown). */
    public boolean isHrApprover(Staffs s) {
        if (s == null || !CompOffConstants.ACTIVE.equals(s.getStatus()) || !CompOffConstants.ROLE_APPROVER.equals(s.getAccessRole())) {
            return false;
        }
        for (Staffs c : approverChoices()) {
            if (String.valueOf(c.getStaffID()).equals(s.getStaffID())) {
                return true;
            }
        }
        return false;
    }

    public List<Staffs> approverChoices() {
        List<Staffs> out = new ArrayList<Staffs>();
        for (Staffs s : manager.<Staffs>list("from Staffs where status = 'A' and upper(trim(userType)) = 'HR' and companyID = "
                + SqlUtil.number(CompOffConfig.getCompanyId()))) {
            if (CompOffConstants.ROLE_APPROVER.equals(s.getAccessRole())) {
                out.add(s);
            }
        }
        Collections.sort(out, (a, b) -> a.getStaffName().compareToIgnoreCase(b.getStaffName()));
        return out;
    }

    /**
     * Saves one HR approver setting: on (active) needs an HR approver from approverChoices(). The paramName's record
     * is updated (status, and paramValue / staffID when an approver is chosen); it is inserted only when there is none.
     * Returns a message for the page.
     */
    public String save(String paramName, Integer approverId, boolean active) {
        String title = null;
        for (String[] p : PARAMS) {
            if (p[0].equals(paramName)) {
                title = p[1];
            }
        }
        if (title == null) {
            throw new CompOffException(null, "Unknown setting.");
        }
        Staffs approver = null;
        if (approverId != null) {
            for (Staffs s : approverChoices()) {
                if (String.valueOf(s.getStaffID()).equals(approverId)) {
                    approver = s;
                }
            }
            if (approver == null) {
                throw new CompOffException(null, "The HR approver must be an active HR staff member with the Approver role.");
            }
        }
        if (active && approver == null) {
            throw new CompOffException(null, "Select the HR approver for " + title + ".");
        }
        String status = active ? CompOffConstants.ACTIVE : CompOffConstants.INACTIVE;
        GeneralSettings row = latest(paramName, false);
        if (row == null ? approver == null
                : status.equals(row.getStatus()) && (approver == null || String.valueOf(approver.getStaffID()).equals(row.valueStaffId()))) {
            return title + ": no change.";
        }
        if (row == null) {
            row = new GeneralSettings();
            row.setCompanyID(CompOffConfig.getCompanyId());
            row.setUserID(CompOffConfig.getCompanyId());
            row.setTeamID(0);
            row.setCreatedDate(new Date());
            row.setParamName(paramName);
            row.setParamValue(userTypeCode(approver) + "~~" + approver.getStaffID());
            row.setStaffID(approver.getStaffID());
            row.setStatus(status);
            hs.save(row);
            if (row.getGSID() == null) {
                throw new CompOffException(null, "The setting could not be saved. Please try again.");
            }
        } else if (hs.updateSQLQuery1("UPDATE GeneralSettings SET status = " + SqlUtil.code(status)
                + (approver == null ? "" : ", paramValue = " + SqlUtil.text(userTypeCode(approver) + "~~" + approver.getStaffID())
                        + ", staffID = " + SqlUtil.number(approver.getStaffID()))
                + ", modifiedDate = NOW() WHERE GSID = " + SqlUtil.number(row.getGSID())) != 1) {
            throw new CompOffException(null, "The setting could not be saved. Please try again.");
        }
        // older databases may hold more rows for the paramName: only this record may be Active
        hs.updateSQLQuery1("UPDATE GeneralSettings SET status = 'I', modifiedDate = NOW() WHERE companyID = "
                + SqlUtil.number(CompOffConfig.getCompanyId()) + " AND paramName = " + SqlUtil.text(paramName)
                + " AND status = 'A' AND GSID <> " + SqlUtil.number(row.getGSID()));
        LOG.info("GeneralSettings " + paramName + " saved: " + (active ? "Active, approver " + approverId : "Inactive"));
        Integer shown = approver != null ? approver.getStaffID() : row.valueStaffId();
        Staffs who = shown == null ? null : manager.findStaff(shown);
        return title + ": HR approval " + (active ? "by " + (who == null ? "#" + shown : who.getStaffName()) + " is on." : "is off.");
    }

    // =====================================================================
    // Desktop App Settings (DesktopAppSettings table): Comp Off module access
    // =====================================================================

    /** One team of the Comp Off teams drop-down. */
    public static class TeamChoice {
        private final Integer teamId;
        private final String name;
        private final boolean active;
        private final boolean found;
        private boolean selected;

        TeamChoice(Integer teamId, String name, boolean active, boolean found) {
            this.teamId = teamId;
            this.name = name;
            this.active = active;
            this.found = found;
        }

        public Integer getTeamId() { return teamId; }
        public String getName() { return name; }
        public boolean isActive() { return active; }
        /** False for an id in compOffTeamIDs that is not in the Teams table (kept until the Admin removes it). */
        public boolean isFound() { return found; }
        public boolean isSelected() { return selected; }
    }

    /** One Y / N flag of the page. */
    public static class Flag {
        private final String paramName;
        private final String title;
        private final String detail;
        private final boolean on;

        Flag(String paramName, String title, String detail, boolean on) {
            this.paramName = paramName;
            this.title = title;
            this.detail = detail;
            this.on = on;
        }

        public String getParamName() { return paramName; }
        public String getTitle() { return title; }
        public String getDetail() { return detail; }
        public boolean isOn() { return on; }
    }

    private static final String[][] FLAG_TEXT = {
        {ModuleAccess.HOLIDAY_REQUEST, "Holiday Intimation request", "Holiday Intimation page: new intimations"},
        {ModuleAccess.HOLIDAY_APPROVAL, "Holiday Intimation approval", "Approve or Reject - Holiday Working Details"},
        {ModuleAccess.COMP_OFF_REQUEST, "Comp Off request", "Apply Comp Off page: new requests"},
        {ModuleAccess.COMP_OFF_APPROVAL, "Comp Off approval", "Approve or Reject - Comp Off"}};

    /**
     * The current company's teams (active and inactive) by name, those in compOffTeamIDs ticked; ids in compOffTeamIDs
     * that are in no Teams row are added at the end. compOffTeamIDs is shared by all companies: the other companies'
     * teams are not listed (see otherCompanyTeams) and are kept when saving.
     */
    public List<TeamChoice> desktopTeams(ModuleAccess.Settings st) {
        List<TeamChoice> out = new ArrayList<TeamChoice>();
        java.util.Set<Integer> listed = new java.util.HashSet<Integer>(otherCompanyTeams());
        for (com.dao.Teams t : manager.<com.dao.Teams>list("from Teams where companyID = " + SqlUtil.number(CompOffConfig.getCompanyId())
                + " order by teamName, teamID")) {
            TeamChoice c = new TeamChoice(t.getTeamID(), t.getTeamName(), CompOffConstants.ACTIVE.equals(t.getStatus()), true);
            c.selected = st.getTeams().contains(t.getTeamID());
            listed.add(t.getTeamID());
            out.add(c);
        }
        for (Integer id : st.getTeams()) {
            if (!listed.contains(id)) {
                TeamChoice c = new TeamChoice(id, "Team " + id + " (not found)", false, false);
                c.selected = true;
                out.add(c);
            }
        }
        return out;
    }

    /** teamIDs of the other companies' teams (and of teams with no company): never shown here, never removed. */
    private java.util.Set<Integer> otherCompanyTeams() {
        return manager.ids("SELECT teamID FROM Teams WHERE companyID IS NULL OR companyID <> " + SqlUtil.number(CompOffConfig.getCompanyId()));
    }

    public List<Flag> desktopFlags(ModuleAccess.Settings st) {
        List<Flag> out = new ArrayList<Flag>();
        for (String[] f : FLAG_TEXT) {
            out.add(new Flag(f[0], f[1], f[2], st.flag(f[0])));
        }
        return out;
    }

    /**
     * Saves the Comp Off teams (at least one; each a team of the drop-down) and the four flags (paramName -> on).
     * The newest row of each paramName is updated; a row is only inserted when the paramName has none. Teams
     * already saved keep their place in compOffTeamIDs, newly ticked ones are added at the end; the other companies'
     * teams in compOffTeamIDs are kept. Returns a message.
     */
    public String saveDesktop(List<Integer> teamIds, java.util.Map<String, Boolean> flags) {
        if (teamIds == null || teamIds.isEmpty()) {
            throw new CompOffException(null, "Select at least one Comp Off team.");
        }
        ModuleAccess.Settings st = ModuleAccess.read();
        java.util.Set<Integer> known = new java.util.HashSet<Integer>();
        for (TeamChoice c : desktopTeams(st)) {
            known.add(c.getTeamId());
        }
        java.util.Set<Integer> others = otherCompanyTeams();
        java.util.Set<Integer> chosen = new java.util.LinkedHashSet<Integer>();
        for (Integer id : st.getTeams()) {
            if (teamIds.contains(id) || others.contains(id)) {   // other companies' teams stay as they are
                chosen.add(id);
            }
        }
        for (Integer id : teamIds) {
            if (!known.contains(id)) {
                throw new CompOffException(null, "Team " + id + " was not found. Please reload the page and try again.");
            }
            chosen.add(id);
        }
        java.util.Map<String, String> values = new java.util.LinkedHashMap<String, String>();
        StringBuilder ids = new StringBuilder();
        for (Integer id : chosen) {
            ids.append(ids.length() == 0 ? "" : ",").append(id);
        }
        values.put(ModuleAccess.TEAMS, ids.toString());
        for (String[] f : FLAG_TEXT) {
            values.put(f[0], Boolean.TRUE.equals(flags.get(f[0])) ? "Y" : "N");
        }
        List<String> changed = new ArrayList<String>();
        for (java.util.Map.Entry<String, String> e : values.entrySet()) {
            List<com.dao.DesktopAppSettings> rows = manager.list("from DesktopAppSettings where paramName = :p order by DSID desc", "p", e.getKey());
            if (!rows.isEmpty() && e.getValue().equals(rows.get(0).getParamValue())) {
                continue;
            }
            if (rows.isEmpty()) {
                com.dao.DesktopAppSettings row = new com.dao.DesktopAppSettings();
                row.setParamName(e.getKey());
                row.setParamValue(e.getValue());
                hs.save(row);
                if (row.getDSID() == null) {
                    throw new CompOffException(null, "The Desktop App Settings could not be saved. Please try again.");
                }
            } else if (hs.updateSQLQuery1("UPDATE DesktopAppSettings SET paramValue = " + SqlUtil.text(e.getValue())
                    + " WHERE DSID = " + SqlUtil.number(rows.get(0).getDSID())) != 1) {
                throw new CompOffException(null, "The Desktop App Settings could not be saved. Please try again.");
            }
            changed.add(e.getKey() + " = " + e.getValue());
        }
        if (changed.isEmpty()) {
            return "Desktop App Settings: no change.";
        }
        LOG.info("DesktopAppSettings saved: " + String.join("; ", changed));
        return "Desktop App Settings saved: " + String.join(", ", changed) + ".";
    }

    private String userTypeCode(Staffs s) {
        return s.getUserType() == null || s.getUserType().trim().isEmpty() ? "NA" : s.getUserType().trim();
    }
}
