package com.action;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.dao.CompOffConstants;
import com.dao.DesktopAppSettings;
import com.dao.GeneralSettings;
import com.dao.Staffs;
import com.serviceimpl.HibernateService;
import com.serviceimpl.HibernateUtil;

/**
 * Who may use which page of the Comp Off module, from the DesktopAppSettings table (read again on every request,
 * so a change applies at once). The Administrator is never restricted. For everyone else:
 * <ul>
 * <li>Own pages (Holiday Intimation, Comp Off, My Comp Off Report, My Team Reports): only when Staffs.teamID is in
 * compOffTeamIDs. An HR user (Staffs.userType 'HR') is not checked by team: they have them only when they are the HR
 * approver named in the Active GeneralSettings 'holidayApprovalHR' and / or 'compOffApprovalHR'.</li>
 * <li>Approval pages: any approver, whatever their own team (the lists then only hold requests of staff who may use
 * the module); an HR user only for the flow they are named in.</li>
 * <li>The four Y / N flags switch the request pages and the approval pages off (anything but 'Y' = off).</li>
 * </ul>
 * A missing table, row or value means no access (teams) or switched off (flags). With no team in compOffTeamIDs,
 * nobody but the Administrator sees anything of the module (approval pages included).
 */
public final class ModuleAccess {

    private static final Logger LOG = Logger.getLogger(ModuleAccess.class.getName());

    public static final String TEAMS = "compOffTeamIDs";
    public static final String HOLIDAY_REQUEST = "holidayRequestAllowFlag";
    public static final String HOLIDAY_APPROVAL = "holidayApprovalAllowFlag";
    public static final String COMP_OFF_REQUEST = "compoffRequestAllowFlag";
    public static final String COMP_OFF_APPROVAL = "compoffRequestApproveFlag";
    /** The four flags, in the order of the General Settings page. */
    public static final String[] FLAGS = {HOLIDAY_REQUEST, HOLIDAY_APPROVAL, COMP_OFF_REQUEST, COMP_OFF_APPROVAL};

    /** Page for a user who may open none of the module's pages: the header with name and Log out only. */
    public static final String NO_ACCESS_ACTION = "start1";

    private static final String SELF = "self", TEAM = "team", HOLIDAY = "holiday", COMP_OFF = "compOff";
    /** Struts action -> {what it needs, the flag that must be 'Y' (or null)}. Actions not listed are not restricted here. */
    private static final Map<String, String[]> PAGES = new HashMap<String, String[]>();
    /** The pages a user can land on after login, in menu order. */
    private static final String[] HOME_PAGES = {"holidayIntimation1", "cancelHolidayIntimation1", "compOffBalance1", "applyCompOff1",
        "cancelCompOffRequest1", "compOffDayView1", "compOffMonthView1", "teamCompOffMonthView1", "approveHolidayWorking1", "approveCompOff1"};

    static {
        page(SELF, HOLIDAY_REQUEST, "holidayIntimation1", "submitHolidayIntimation1");
        page(SELF, COMP_OFF_REQUEST, "applyCompOff1", "submitCompOff1");
        page(SELF, null, "cancelHolidayIntimation1", "doCancelHolidayIntimation1", "compOffBalance1", "cancelCompOffRequest1",
                "cancelCompOff1", "compOffDayView1", "compOffMonthView1");
        page(TEAM, null, "teamCompOffMonthView1");
        page(HOLIDAY, HOLIDAY_APPROVAL, "approveHolidayWorking1", "decideHolidayWorking1");
        page(COMP_OFF, COMP_OFF_APPROVAL, "approveCompOff1", "decideCompOff1");
    }

    private static void page(String need, String flag, String... actions) {
        for (String a : actions) {
            PAGES.put(a, new String[] {need, flag});
        }
    }

    // =====================================================================
    // Settings (one read per request)
    // =====================================================================

    /** The settings as read at one moment. */
    public static final class Settings {
        /** paramName -> paramValue of its highest DSID. */
        private final Map<String, String> values;
        private final Set<Integer> teams;
        private final Integer holidayHr;
        private final Integer compOffHr;

        Settings(Map<String, String> values, Integer holidayHr, Integer compOffHr) {
            this.values = values;
            this.teams = teamIds(values.get(TEAMS));
            this.holidayHr = holidayHr;
            this.compOffHr = compOffHr;
        }

        /** True only for 'Y' (a missing or other value = off). */
        public boolean flag(String paramName) {
            String v = values.get(paramName);
            return v != null && "Y".equalsIgnoreCase(v.trim());
        }

        public boolean has(String paramName) { return values.containsKey(paramName); }

        /** compOffTeamIDs as read, in their order (ids that are not numbers are left out). */
        public Set<Integer> getTeams() { return teams; }
    }

    /** '478, 535' -> {478, 535}; anything that is not a number is ignored. */
    static Set<Integer> teamIds(String value) {
        Set<Integer> out = new LinkedHashSet<Integer>();
        if (value != null) {
            for (String p : value.split(",")) {
                if (p.trim().matches("[0-9]{1,9}")) {
                    out.add(Integer.valueOf(p.trim()));
                }
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** Reads DesktopAppSettings and the Active HR approver settings. Never throws. */
    public static Settings read() {
        HibernateService hs = new HibernateUtil();
        Map<String, String> values = new HashMap<String, String>();
        try {
            @SuppressWarnings("unchecked")
            List<DesktopAppSettings> rows = hs.getList("from DesktopAppSettings order by DSID");
            if (rows == null) {
                LOG.warning("DesktopAppSettings could not be read: the Comp Off module is closed for everyone but the Administrator");
            } else {
                for (DesktopAppSettings r : rows) {
                    if (r.getParamName() != null) {
                        values.put(r.getParamName().trim(), r.getParamValue());   // a later (higher) DSID wins
                    }
                }
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "DesktopAppSettings could not be read", e);
        }
        Integer holidayHr = null, compOffHr = null;
        try {
            GeneralSettingsManager gsm = new GeneralSettingsManager();
            GeneralSettings h = gsm.latest(GeneralSettingsManager.HOLIDAY_HR, true), c = gsm.latest(GeneralSettingsManager.COMP_OFF_HR, true);
            holidayHr = h == null ? null : h.valueStaffId();
            compOffHr = c == null ? null : c.valueStaffId();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "HR approver settings could not be read", e);
        }
        return new Settings(values, holidayHr, compOffHr);
    }

    // =====================================================================
    // One user's access
    // =====================================================================

    private final Settings settings;
    private final boolean admin;
    private final boolean approver;
    private final boolean self;
    private final boolean holidayApprover;
    private final boolean compOffApprover;

    private ModuleAccess(Staffs s, Settings st) {
        settings = st;
        String role = s == null ? null : s.getAccessRole();
        admin = CompOffConstants.ROLE_ADMIN.equals(role);
        approver = CompOffConstants.ROLE_APPROVER.equals(role);
        // no team in compOffTeamIDs (missing row, empty value or no table): the module is closed to every non-Admin
        boolean known = role != null && !st.teams.isEmpty();
        Integer id = s == null ? null : s.getStaffID();
        boolean hr = s != null && s.isHr();
        boolean namedHoliday = id != null && id.equals(st.holidayHr), namedCompOff = id != null && id.equals(st.compOffHr);
        self = known && !admin && (hr ? namedHoliday || namedCompOff : String.valueOf(s.getTeamID()) != null && st.teams.contains(s.getTeamID()));
        holidayApprover = admin || known && approver && (!hr || namedHoliday);
        compOffApprover = admin || known && approver && (!hr || namedCompOff);
    }

    /** Access of a staff member with freshly read settings. */
    public static ModuleAccess of(Staffs s) {
        return new ModuleAccess(s, read());
    }

    public static ModuleAccess of(Staffs s, Settings st) {
        return new ModuleAccess(s, st);
    }

    public Settings getSettings() { return settings; }

    /** True for the pages these rules cover (the others are left to the role check). */
    public static boolean covers(String action) {
        return PAGES.containsKey(action);
    }

    /** May the user see the page in the menu (it may still be switched off)? */
    public boolean shows(String action) {
        String[] rule = PAGES.get(action);
        if (admin || rule == null) {
            return true;
        }
        switch (rule[0]) {
            case SELF: return self;
            case TEAM: return self && approver;
            case HOLIDAY: return holidayApprover;
            default: return compOffApprover;
        }
    }

    /** May the user open the page (or post to the action) now? */
    public boolean allows(String action) {
        String[] rule = PAGES.get(action);
        return admin || rule == null || shows(action) && (rule[1] == null || settings.flag(rule[1]));
    }

    /** Page to open after login: the first page of the menu the user may open, or the empty page. */
    public String homeAction() {
        if (admin) {
            return "compOffDetailsReport1";
        }
        for (String a : HOME_PAGES) {
            boolean approverPage = a.startsWith("approve") || a.startsWith("team");
            if ((approver || !approverPage) && allows(a)) {
                return a;
            }
        }
        return NO_ACCESS_ACTION;
    }

    /** For an approver's lists: may this requester use the module (so their requests are shown)? Admin: everyone. */
    public Predicate<Integer> requesterFilter() {
        if (admin) {
            return null;
        }
        HibernateService hs = new HibernateUtil();
        Map<Integer, Boolean> seen = new HashMap<Integer, Boolean>();
        return staffId -> seen.computeIfAbsent(staffId, id -> {
            Staffs s = (Staffs) hs.get(Staffs.class.getName(), id);   // also staff made inactive since
            return s != null && new ModuleAccess(s, settings).self;
        });
    }

    // Leave page cards (CompOffLinks) and the Comp Off pages
    public boolean isAdmin() { return admin; }
    /** The user's own pages (Holiday Intimation, Comp Off, My Comp Off Report). */
    public boolean isSelf() { return self; }
    /** My Team Reports. */
    public boolean isTeam() { return shows("teamCompOffMonthView1"); }
    public boolean isHolidayApprover() { return holidayApprover; }
    public boolean isCompOffApprover() { return compOffApprover; }
    public boolean isHolidayRequestOn() { return allows("holidayIntimation1"); }
    public boolean isCompOffRequestOn() { return allows("applyCompOff1"); }
    public boolean isHolidayApprovalOn() { return allows("approveHolidayWorking1"); }
    public boolean isCompOffApprovalOn() { return allows("approveCompOff1"); }
    /** Nothing in the menu: the page shows only the header with the name and Log out. */
    public boolean isNothing() { return !admin && !self && !(approver && (holidayApprover || compOffApprover)); }
}
