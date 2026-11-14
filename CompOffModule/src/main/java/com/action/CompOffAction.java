package com.action;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.SessionAware;

import com.dao.CompOffBalanceDetailsHistory;
import com.dao.CompOffConstants;
import com.dao.CompOffBalanceDetails;
import com.dao.CompensatoryOffRequest;
import com.dao.HolidayRequests;
import com.dao.Staffs;
import com.util.CompOffConfig;
import com.util.DateUtil;
import com.util.SessionKeys;
import com.opensymphony.xwork2.ActionSupport;

/**
 * Comp Off module pages. Every page method first calls access(...): the logged-in user from the host application's
 * session (staffID, companyID, userType) and the access levels the page allows (see access below):
 *   Holiday Intimation         - holidayIntimation (view) / submitHolidayIntimation (create)
 *   Cancel Holiday Intimation  - cancelHolidayIntimation (view) / doCancelHolidayIntimation (cancel)
 *   My Comp Off Balance        - compOffBalance (view)
 *   Apply Comp Off             - applyCompOff (view) / submitCompOff (create) / cancelCompOff (cancel)
 *   Approver approvals         - approveHolidayWorking / approveCompOff (view) and
 *                                decideHolidayWorking / decideCompOff (approve or reject the ticked rows)
 *   My Comp Off Report         - compOffDayView / compOffMonthView (the staff member's own Comp Off, read only)
 *   My Team Reports (Approver) - teamCompOffMonthView (Comp Off days of the approver's own team, read only)
 *   Admin reports              - compOffDetailsReport, holidayViewReport, compOffViewReport, compOffHistoryReport,
 *                                holidayUsageReport / holidayUsageExcel, compOffBalanceReport / compOffBalanceExcel,
 *                                compOffBalanceStaff / compOffBalanceStaffExcel (read only)
 *
 * Every request parameter is validated here (server side) before CompOffManager is called; rules that
 * need the database (duplicates, balance, status) are checked again by CompOffManager.
 * Records are never deleted: cancelling changes the workflow status and queries only read Status = 'A' rows.
 */
public class CompOffAction extends ActionSupport implements SessionAware {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(CompOffAction.class.getName());

    private static final String REDIRECT = "redirect";
    /** Not logged in (host session): the host application's session-expired page (global result, compoff.xml). */
    public static final String SESSION_OUT = "sessionOut";
    private static final String LOGIN = SESSION_OUT;
    /** No permission for the page: the Access denied page (global result, compoff.xml), HTTP 403. */
    public static final String FORBIDDEN = "forbidden";

    // Access levels each page allows (the user's level comes from the session's userType: CompOffConfig.accessRole)
    private static final String[] EMPLOYEE_PAGES = {CompOffConstants.ROLE_EMPLOYEE, CompOffConstants.ROLE_APPROVER};
    private static final String[] APPROVAL_PAGES = {CompOffConstants.ROLE_APPROVER, CompOffConstants.ROLE_ADMIN};
    private static final String[] TEAM_PAGES = {CompOffConstants.ROLE_APPROVER};
    private static final String[] ADMIN_PAGES = {CompOffConstants.ROLE_ADMIN};
    /** Any user with a Comp Off access level (Leave page, home). */
    private static final String[] ANY_LEVEL = {};

    private final CompOffManager manager = new CompOffManager();
    private final ApprovalManager approvals = new ApprovalManager();
    private final ReportManager reports = new ReportManager();

    /** Most rows one approve / reject click may carry (keeps one click well inside the request time limit). */
    public static final int MAX_DECISION_IDS = 300;

    private Map<String, Object> session;

    // ---- Request parameters (kept as String so bad input becomes a validation message, not a conversion error)
    private String workedDate;
    private String shiftType;
    private String reason;
    private String dayType;
    private String fromDate;
    private String toDate;
    private String notes;
    /** Apply Comp Off: the selected Compensatory Off dates, 'yyyy-MM-dd' comma separated. */
    private String leaveDates;
    private String holidayWorkingId;
    private String compOffRequestId;
    private String cancelReason;
    private String ids;
    private String decision;
    private String remarks;
    // report filters
    private String teamId;
    private String staffFilter;
    private String run;
    /** Apply Comp Off for Staff: employee codes, comma separated; the Comp Off date ('yyyy-MM-dd'). */
    private String employeeCodes;
    private String compOffDate;
    private List<CompOffManager.AdminPick> adminPicks = Collections.emptyList();
    private List<Map<String, Object>> adminApplied = Collections.emptyList();
    private List<Map<String, Object>> cancelRows = Collections.emptyList();
    private String hwId;
    private String ap;
    /** Approval pages: applied month (1-12) and year chosen with Go; open = the request of an email link. */
    private String month;
    private String year;
    private String open;
    private String periodLabel;
    private List<Integer> yearOptions = Collections.emptyList();
    /** Comp Off Processing: decisions waiting for the chosen month; mode = "one" / "all". */
    private List<ApprovalManager.HeldRow> heldRows = Collections.emptyList();
    private String mode;
    /** My Comp Off Report: the Day View date (yyyy-MM-dd); the requests shown, the Month View calendar and totals. */
    private String day;
    private String dayLabel;
    private List<CompensatoryOffRequest> reportRequests = Collections.emptyList();
    private List<CalendarDay> calendarDays = Collections.emptyList();
    /** Month View: the month shown, and the number of requests per status (in the order of the chips). */
    private java.time.YearMonth shownMonth;
    private final Map<String, Integer> statusCounts = new java.util.LinkedHashMap<String, Integer>();
    private String totalHours;
    private int fullDays;
    private int halfDays;
    /** My Team Reports: status filter ("" = All), the approver's team, the grid of the month and its title. */
    private String coStatus;
    private String myTeamName;
    private List<Map<String, Object>> teamStaff = Collections.emptyList();
    private List<TeamRow> teamRows = Collections.emptyList();
    private List<CalendarDay> monthDays = Collections.emptyList();
    private String teamReportTitle;
    /** Status filter choices of the team report ("Approved" also covers Completed). */
    public static final List<String> TEAM_STATUSES = java.util.Arrays.asList("Approved", "In Process", "Pending", "Rejected", "Cancelled");

    // ---- View data
    private Staffs currentStaff;
    /** home1: the page to open. */
    private String homeAction;
    private ModuleAccess access;
    private String currentUserTypeName;
    private Staffs approver;
    private List<HolidayRequests> intimations = Collections.emptyList();
    private CompOffBalanceDetails summary;
    private List<HolidayRequests> availableList = Collections.emptyList();
    private List<java.util.Date> workedDates = Collections.emptyList();
    private List<CompensatoryOffRequest> compOffRequests = Collections.emptyList();
    private Map<Integer, List<CompOffBalanceDetailsHistory>> historyByRequest = Collections.emptyMap();
    private Map<Integer, String> staffNames = Collections.emptyMap();
    private String flashMessage;
    private String flashType;
    private List<ApprovalManager.Row> approvalRows = Collections.emptyList();
    private List<ApprovalManager.Row> processingRows = Collections.emptyList();
    private List<Map<String, Object>> teams = Collections.emptyList();
    private List<Map<String, Object>> staffOptions = Collections.emptyList();
    private List<Map<String, Object>> reportRows = Collections.emptyList();
    private List<Map<String, Object>> intimationChoices = Collections.emptyList();
    private List<Map<String, Object>> holidayHistoryRows = Collections.emptyList();
    private Integer selectedHwId;
    private boolean reportRun;
    private java.io.InputStream excelStream;
    private String excelFileName;
    /** Comp Off Balance and Details Report: encrypted staffID of View Details, and that staff member's summary row. */
    private String sid;
    private Map<String, Object> balanceStaff;

    // ---- Approver Management (Admin)
    private final ApproverManager approverManager = new ApproverManager();
    private ApproverManager.Page approverPage;
    private final GeneralSettingsManager settingsManager = new GeneralSettingsManager();
    private List<GeneralSettingsManager.Setting> generalSettings;
    private List<Staffs> hrApproverChoices;
    private String paramName;
    private String hrApproverId;
    private String hrActive;
    private List<GeneralSettingsManager.TeamChoice> desktopTeams;
    private List<GeneralSettingsManager.Flag> desktopFlags;
    /** compOffTeamIDs as saved (their order is kept on save). */
    private String desktopTeamIds;
    /** Desktop App Settings form: the ticked team ids, and the paramNames of the flags switched on. */
    private String[] desktopTeam;
    private String[] desktopFlag;
    private String q;
    private String approvedBy;
    private String userTypeId;
    private String noneOnly;
    private String staffIds;
    private String approverIds;
    private String confirmReplace;

    // =====================================================================
    // Holiday Intimation
    // =====================================================================

    /** READ: the intimation form and the staff member's intimations. */
    public String holidayIntimation() {
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }
        loadHolidayIntimationPage();
        return SUCCESS;
    }

    /** CREATE: submit a holiday intimation to the level-1 approver. */
    public String submitHolidayIntimation() {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }

        LocalDate today = CompOffConfig.today();
        LocalDate worked = requiredDate("workedDate", workedDate, "Worked date");
        if (worked != null && worked.isAfter(today)) {
            addFieldError("workedDate", "Worked date cannot be a future date.");
        }
        String shift = requiredChoice("shiftType", shiftType, "Shift type", CompOffConstants.SHIFT_TYPES);
        String reasonText = requiredText("reason", reason, "Notes", "Notes are required.");

        if (!hasFieldErrors()) {
            try {
                Notifier mail = notifier();
                Staffs sentTo = manager.submitHolidayIntimation(currentStaff.getStaffID(), worked, shift, reasonText, mail);
                mail.flush();
                return redirectTo("Your Holiday Intimation request has been submitted to " + sentTo.getEmail()
                        + ". Status: Pending.", "ok");
            } catch (CompOffException e) {
                addBusinessError(e);
            } catch (RuntimeException e) {
                addActionError(unexpected("submit holiday intimation", e));
            }
        }
        loadHolidayIntimationPage();
        return INPUT;
    }

    /** READ: intimations with a Cancel button for Pending / Available ones. */
    public String cancelHolidayIntimation() {
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }
        intimations = manager.intimations(currentStaff.getStaffID());
        return SUCCESS;
    }

    /** CANCEL: cancel a Pending or Available intimation (status change, nothing is deleted). */
    public String doCancelHolidayIntimation() {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }
        Integer id = requiredId(holidayWorkingId, "Holiday intimation");
        String why = requiredText("cancelReason", cancelReason, "Cancel Notes", "Cancel Notes are required.");
        if (hasFieldErrors() || hasActionErrors()) {
            return redirectTo(firstError(), "er");
        }
        try {
            Notifier mail = notifier();
            manager.cancelHolidayIntimation(currentStaff.getStaffID(), id, why, mail);
            mail.flush();
            return redirectTo("Holiday Intimation cancelled.", "ok");
        } catch (CompOffException e) {
            return redirectTo(e.getMessage(), "er");
        } catch (RuntimeException e) {
            return redirectTo(unexpected("cancel holiday intimation", e), "er");
        }
    }

    // =====================================================================
    // My Comp Off Balance
    // =====================================================================

    /** READ: Allocated / Used / Balance and the intimations behind them. */
    public String compOffBalance() {
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }
        summary = manager.summary(currentStaff.getStaffID());
        availableList = manager.available(currentStaff.getStaffID());
        checkBalance();
        intimations = new ArrayList<HolidayRequests>(manager.intimations(currentStaff.getStaffID()));
        Collections.sort(intimations, (a, b) -> b.getWorkRequestDate().compareTo(a.getWorkRequestDate()));
        return SUCCESS;
    }

    // =====================================================================
    // Apply Comp Off
    // =====================================================================

    /** READ: balance, the apply form, and the staff member's Comp Off requests with history. */
    public String applyCompOff() {
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }
        loadApplyCompOffPage();
        return SUCCESS;
    }

    /** CREATE: one Comp Off request per day from..to, each using the oldest Available intimation (FIFO). */
    public String submitCompOff() {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }

        String type = trimToNull(dayType);
        if (type == null) {
            addFieldError("dayType", "Day type is required.");
        } else if (CompOffConstants.HALF_DAY.equals(type)) {
            addFieldError("dayType", "Half day is not available yet. Please apply for a Full day.");
        } else if (!CompOffConstants.DAY_TYPES.contains(type)) {
            addFieldError("dayType", "Select a valid day type.");
        }
        String shift = requiredChoice("shiftType", shiftType, "Shift type", CompOffConstants.SHIFT_TYPES);

        // Compensatory Off dates: one or more, each any day of the current month or a later month (never a past month)
        List<LocalDate> dates = leaveDateList();
        if (dates != null) {
            try {
                // worked dates, balance, already-applied dates - each date checked, reported with the other field errors
                String problem = manager.applyProblem(currentStaff.getStaffID(), dates);
                if (problem != null) {
                    addFieldError("leaveDates", problem);
                }
            } catch (CompOffException e) {
                addBusinessError(e);
            }
        }
        String notesText = requiredText("notes", notes, "Notes", "Notes are required.");

        if (!hasFieldErrors()) {
            try {
                Notifier mail = notifier();
                CompOffManager.ApplyResult result = manager.applyCompOff(currentStaff.getStaffID(), type, shift, dates, notesText, mail);
                mail.flush();
                if (result.stoppedReason != null) {
                    return redirectTo("Comp Off applied for " + displayDates(result.applied) + " only. "
                            + result.stoppedReason, "er");
                }
                return redirectTo("Your Comp Off request has been submitted for approval to " + result.approver.getEmail() + ".", "ok");
            } catch (CompOffException e) {
                addBusinessError(e);
            } catch (RuntimeException e) {
                addActionError(unexpected("apply comp off", e));
            }
        }
        loadApplyCompOffPage();
        return INPUT;
    }

    /** Cancel Comp Off page: the staff member's Comp Off requests with a Cancel link for the ones that can be cancelled. */
    public String cancelCompOffRequest() {
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }
        Integer id = currentStaff.getStaffID();
        compOffRequests = manager.requests(id, CompOffConfig.today());
        historyByRequest = manager.historyByRequest(id);
        staffNames = manager.staffNames();
        return SUCCESS;
    }

    /** CANCEL: cancel a Pending / Approved Comp Off request; the day returns to the balance. */
    public String cancelCompOff() {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }
        Integer id = requiredId(compOffRequestId, "Comp Off request");
        String why = requiredText("cancelReason", cancelReason, "Cancel Notes", "Cancel Notes are required.");
        if (hasFieldErrors() || hasActionErrors()) {
            return redirectTo(firstError(), "er");
        }
        try {
            Notifier mail = notifier();
            manager.cancelCompOff(currentStaff.getStaffID(), id, why, CompOffConfig.today(), mail);
            mail.flush();
            return redirectTo("Comp Off cancelled. Holiday Intimation is Approved again; balance restored.", "ok");
        } catch (CompOffException e) {
            return redirectTo(e.getMessage(), "er");
        } catch (RuntimeException e) {
            return redirectTo(unexpected("cancel comp off", e), "er");
        }
    }

    /** READ: the page of a user who may open none of the module's pages (header with name and Log out only). */
    public String start() {
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }
        return SUCCESS;
    }

    /**
     * Entry point of the module (the host application's Comp Off menu links here: home1.action): the first page
     * the user may open (Admin: Comp Off Details Report; others: per DesktopAppSettings, or the empty page start1).
     */
    public String home() {
        String denied = access(ANY_LEVEL);
        if (denied != null) {
            return denied;
        }
        homeAction = ModuleAccess.of(currentStaff).homeAction();
        return "home";
    }

    public String getHomeAction() { return homeAction; }

    /** The module's Leave page (leave1): the Comp Off cards of the logged-in user (layout/leaveLinks.jsp). */
    public String leave() {
        String denied = access(ANY_LEVEL);
        if (denied != null) {
            return denied;
        }
        return SUCCESS;
    }

    // =====================================================================
    // Approver: Approve or Reject
    // =====================================================================

    /** READ: holiday intimation levels waiting for the logged-in approver. */
    public String approveHolidayWorking() {
        String denied = access(APPROVAL_PAGES);
        if (denied != null) {
            return denied;
        }
        if (!linkIsForMe()) {
            return redirectTo(LINK_FOR_OTHER, "er");
        }
        approvals.onlyRequesters(getAccess().requesterFilter());
        showPeriod(approvals.holidayRows(currentStaff.getStaffID()), approvals.holidayInProcess(currentStaff.getStaffID()));
        return SUCCESS;
    }

    /** APPROVE / REJECT the ticked holiday intimation rows. */
    public String decideHolidayWorking() {
        return decide(true);
    }

    /** READ: comp off requests waiting for the logged-in approver. */
    public String approveCompOff() {
        String denied = access(APPROVAL_PAGES);
        if (denied != null) {
            return denied;
        }
        if (!linkIsForMe()) {
            return redirectTo(LINK_FOR_OTHER, "er");
        }
        approvals.onlyRequesters(getAccess().requesterFilter());
        showPeriod(approvals.compOffRows(currentStaff.getStaffID()), approvals.compOffInProcess(currentStaff.getStaffID()));
        return SUCCESS;
    }

    /** APPROVE / REJECT the ticked comp off requests. */
    public String decideCompOff() {
        return decide(false);
    }

    private String decide(boolean holiday) {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(APPROVAL_PAGES);
        if (denied != null) {
            return denied;
        }
        String kind = trimToNull(decision);
        boolean approve = "approve".equals(kind);
        if (!approve && !"reject".equals(kind)) {
            return redirectTo("Choose Approve or Reject.", "er");
        }
        List<Integer> selected = parseIds(ids);
        if (selected == null) {
            return redirectTo("Select at least one request.", "er");
        }
        if (selected.size() > MAX_DECISION_IDS) {
            return redirectTo(tooMany(selected.size()), "er");
        }
        String text = trimToNull(remarks);
        if (!approve && text == null) {
            return redirectTo("Notes are mandatory when rejecting.", "er");
        }
        if (text != null && text.length() > CompOffConfig.getTextMaxLength()) {
            return redirectTo("Notes can be at most " + CompOffConfig.getTextMaxLength() + " characters.", "er");
        }
        try {
            ApprovalManager.Outcome o;
            approvals.onlyRequesters(getAccess().requesterFilter());   // decisions only on requests the page shows
            approvals.setRequestBaseUrl(requestBaseUrl(ServletActionContext.getRequest()));
            if (holiday) {
                o = approve ? approvals.approveHoliday(currentStaff, selected, text) : approvals.rejectHoliday(currentStaff, selected, text);
            } else {
                o = approve ? approvals.approveCompOff(currentStaff, selected, text) : approvals.rejectCompOff(currentStaff, selected, text);
            }
            approvals.flushEmails();   // after the decisions are saved: one email per recipient for this click
            if (o.done == 0) {
                return redirectTo("None of the selected requests could be " + (approve ? "approved" : "rejected")
                        + ". They may have been cancelled or already decided. Please refresh the page.", "er");
            }
            String msg = o.done + " request" + (o.done > 1 ? "s" : "") + " " + (approve ? "approved" : "rejected") + " successfully."
                    + " Status: In Process.";
            if (o.skipped > 0) {
                msg += " " + o.skipped + " skipped (cancelled or already decided).";
            }
            return redirectTo(msg, "ok");
        } catch (CompOffException e) {
            return redirectTo(e.getMessage(), "er");
        } catch (RuntimeException e) {
            return redirectTo(unexpected((approve ? "approve " : "reject ") + (holiday ? "holiday intimations" : "comp off requests"), e), "er");
        }
    }

    /**
     * The selected Compensatory Off dates (leaveDates = 'yyyy-MM-dd,yyyy-MM-dd,...'), sorted; null after adding a
     * field error: none selected, a date that is not valid, a date selected twice, a date in a past month, or more
     * dates than compoff.apply.max.days.
     */
    private List<LocalDate> leaveDateList() {
        String v = trimToNull(leaveDates);
        if (v == null) {
            addFieldError("leaveDates", "Compensatory Off date is required.");
            return null;
        }
        String[] parts = v.split(",", -1);
        if (parts.length > CompOffConfig.getApplyMaxDays()) {
            addFieldError("leaveDates", "You can apply for at most " + CompOffConfig.getApplyMaxDays() + " days at a time.");
            return null;
        }
        LocalDate monthStart = CompOffManager.monthStart(CompOffConfig.today());
        java.util.TreeSet<LocalDate> out = new java.util.TreeSet<LocalDate>();
        for (String part : parts) {
            LocalDate d = DateUtil.parseIso(part.trim());
            if (d == null) {
                addFieldError("leaveDates", "Compensatory Off date is not a valid date.");
                return null;
            }
            if (!out.add(d)) {
                addFieldError("leaveDates", DateUtil.display(d) + " is selected more than once.");
                return null;
            }
        }
        for (LocalDate d : out) {
            if (d.isBefore(monthStart)) {
                addFieldError("leaveDates", "Comp Off cannot be applied for a past month (" + DateUtil.display(d) + "). Choose dates from "
                        + DateUtil.display(monthStart) + " onwards.");
                return null;
            }
        }
        return new ArrayList<LocalDate>(out);
    }

    /** Encrypted request ids "a,b,c" (IdCrypt) -> [12, 15, 18]; null when empty or any value is not a valid encrypted id. */
    private static List<Integer> parseIds(String value) {
        String v = trimToNull(value);
        if (v == null) {
            return null;
        }
        java.util.LinkedHashSet<Integer> out = new java.util.LinkedHashSet<Integer>();
        for (String part : v.split(",", -1)) {          // checked part by part: one regex over a long list can overflow the stack
            Integer id = com.util.IdCrypt.dec(part);       // the page sends encrypted request ids
            if (id == null) {
                return null;
            }
            out.add(id);
        }
        return out.isEmpty() ? null : new ArrayList<Integer>(out);
    }

    /** Message when more than MAX_DECISION_IDS requests are ticked (same text as compoff.js). */
    static String tooMany(int n) {
        return n + " requests are selected. You can approve or reject at most " + MAX_DECISION_IDS
                + " requests at a time. Please select fewer and repeat for the rest.";
    }

    public int getMaxDecisionIds() { return MAX_DECISION_IDS; }

    // =====================================================================
    // Admin reports (read only)
    // =====================================================================

    /** Comp Off Details Report: Allocated / Used / Balance of every staff member who has a Comp Off record. */
    public String compOffDetailsReport() {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        reportRows = reports.compOffDetails();
        return SUCCESS;
    }

    /** Comp Off Balance and Details Report: Holiday Worked Days / Comp Off Used / Balance of every staff member. */
    public String compOffBalanceReport() {
        return balanceReport(false);
    }

    /** The Comp Off Balance and Details Report as an Excel file (all rows). */
    public String compOffBalanceExcel() {
        return balanceReport(true);
    }

    private String balanceReport(boolean excel) {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        try {
            reportRows = reports.compOffBalance(null);
            if (excel) {
                LocalDate today = CompOffConfig.today();
                excelStream = new java.io.ByteArrayInputStream(ReportExcel.compOffBalance(reportRows, today));
                excelFileName = "Comp_Off_Balance_and_Details_" + today + ".xlsx";
                return "excel";
            }
        } catch (RuntimeException e) {
            addActionError(unexpected(excel ? "export report" : "run report", e));
        }
        return SUCCESS;
    }

    /**
     * View Details of the Comp Off Balance and Details Report: the staff member's approved Holiday Intimations with the
     * Comp Off that uses each one. sid = encrypted staffID; an unknown or invalid one goes back to the report with a message.
     */
    public String compOffBalanceStaff() {
        return balanceStaff(false);
    }

    /** View Details of one staff member as an Excel file. */
    public String compOffBalanceStaffExcel() {
        return balanceStaff(true);
    }

    private String balanceStaff(boolean excel) {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        try {
            Integer staffId = com.util.IdCrypt.dec(sid);
            List<Map<String, Object>> one = staffId == null ? Collections.<Map<String, Object>>emptyList() : reports.compOffBalance(staffId);
            if (one.isEmpty()) {
                addActionError("The staff member was not found. Choose View Details again.");
                reportRows = reports.compOffBalance(null);
                return "list";
            }
            balanceStaff = one.get(0);
            reportRows = reports.staffCompOffDetails(staffId);
            if (excel) {
                LocalDate today = CompOffConfig.today();
                excelStream = new java.io.ByteArrayInputStream(ReportExcel.staffCompOffDetails(balanceStaff, reportRows, today));
                excelFileName = "Comp_Off_Details_" + balanceStaff.get("employeeCode") + "_" + today + ".xlsx";
                return "excel";
            }
        } catch (RuntimeException e) {
            addActionError(unexpected(excel ? "export report" : "run report", e));
        }
        return SUCCESS;
    }

    /** Holiday View Report: intimations by worked date, team and staff (runs when Go is clicked). */
    public String holidayViewReport() {
        return viewReport(true);
    }

    /** Comp Off View Report: comp off requests by requested date, team and staff (runs when Go is clicked). */
    public String compOffViewReport() {
        return viewReport(false);
    }

    private String viewReport(boolean holiday) {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        teams = reports.teams();
        staffOptions = reports.staff();
        if (run == null) {
            return SUCCESS;                       // first visit: show the filters only
        }
        LocalDate from = requiredDate("fromDate", fromDate, "From date");
        LocalDate to = requiredDate("toDate", toDate, "To date");
        if (from != null && to != null && to.isBefore(from)) {
            addFieldError("toDate", "To date cannot be before From date.");
        }
        Integer team = optionalChoice("teamId", teamId, teams, "teamId", "Select a valid team.");
        Integer staff = optionalChoice("staffFilter", staffFilter, staffOptions, "staffId", "Select a valid staff member.");
        if (team != null && staff != null && !team.equals(teamOf(staff))) {
            addFieldError("staffFilter", "The selected staff member is not in the selected team.");
        }
        if (hasFieldErrors()) {
            return SUCCESS;
        }
        try {
            reportRows = holiday ? reports.holidayView(from, to, team, staff) : reports.compOffView(from, to, team, staff);
            reportRun = true;
        } catch (RuntimeException e) {
            addActionError(unexpected("run report", e));
        }
        return SUCCESS;
    }

    /** Holiday Intimation Usage Report: approved intimations and the Comp Off using each one (runs when Go is clicked). */
    public String holidayUsageReport() {
        return holidayUsage(false);
    }

    /** The Holiday Intimation Usage Report, same filters, as an Excel file (back to the page if a filter is invalid). */
    public String holidayUsageExcel() {
        return holidayUsage(true);
    }

    private String holidayUsage(boolean excel) {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        teams = reports.teams();
        staffOptions = reports.staff();
        Integer team = optionalChoice("teamId", teamId, teams, "teamId", "Select a valid team.");
        Integer staff = optionalChoice("staffFilter", staffFilter, staffOptions, "staffId", "Select a valid staff member.");
        if (team != null && staff != null && !team.equals(teamOf(staff))) {
            addFieldError("staffFilter", "The selected staff member is not in the selected team.");
        }
        if (hasFieldErrors() || (!excel && run == null)) {
            return SUCCESS;                       // first visit: header and "No record found" only
        }
        try {
            reportRows = reports.holidayUsage(team, staff);
            reportRun = true;
            if (excel) {
                LocalDate today = CompOffConfig.today();
                excelStream = new java.io.ByteArrayInputStream(ReportExcel.holidayUsage(reportRows, filterText(team, staff), today));
                excelFileName = "Holiday_Intimation_Usage_" + today + ".xlsx";
                return "excel";
            }
        } catch (RuntimeException e) {
            addActionError(unexpected(excel ? "export report" : "run report", e));
        }
        return SUCCESS;
    }

    /** "Team: X | Staff: Y" for the Excel header. */
    private String filterText(Integer team, Integer staff) {
        String teamName = "All teams", staffName = "All staff";
        for (Map<String, Object> t : teams) {
            if (team != null && ((Number) t.get("teamId")).intValue() == team) {
                teamName = String.valueOf(t.get("teamName"));
            }
        }
        for (Map<String, Object> m : staffOptions) {
            if (staff != null && ((Number) m.get("staffId")).intValue() == staff) {
                staffName = m.get("staffName") + " (" + m.get("employeeCode") + ")";
            }
        }
        return "Team: " + teamName + "  |  Staff: " + staffName;
    }

    /** Comp Off History: latest balance changes of all staff, plus the history of one chosen intimation. */
    public String compOffHistoryReport() {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        reportRows = reports.compOffHistory();
        intimationChoices = reports.intimationChoices();
        Integer plainHw = hwId == null ? null : com.util.IdCrypt.dec(hwId);
        if (hwId != null && plainHw == null) {
            addFieldError("hwId", "Select a valid holiday intimation.");
        }
        Integer chosen = plainHw == null ? null : optionalChoice("hwId", String.valueOf(plainHw), intimationChoices, "id", "Select a valid holiday intimation.");
        if (chosen == null && !intimationChoices.isEmpty()) {
            chosen = ((Number) intimationChoices.get(0).get("id")).intValue();
        }
        if (chosen != null) {
            selectedHwId = chosen;
            holidayHistoryRows = reports.holidayHistory(chosen);
        }
        return SUCCESS;
    }

    // =====================================================================
    // Approver Management (Admin)
    // =====================================================================

    /** The staff list with their approver chains, filters and summary counts. */
    public String approverManagement() {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        teams = reports.teams();
        approverPage = approverManager.page(idOrNull(teamId), trimToNull(q), idOrNull(approvedBy), idOrNull(userTypeId),
                "1".equals(noneOnly));
        return SUCCESS;
    }

    /**
     * Saves one chain (approverIds, level order) for one or several staff (staffIds). Replacing existing
     * approvers needs confirmReplace = "yes" (asked on the page). Returns to the page with the same filters.
     */
    public String saveApprovers() {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        List<Integer> staff = idList(staffIds), chain = idList(approverIds);
        if (staff == null || chain == null) {
            return redirectTo("The selection could not be read. Please try again.", "er");
        }
        String why = trimToNull(reason);
        if (why != null && why.length() > CompOffConfig.getTextMaxLength()) {
            return redirectTo("Reason for change can be at most " + CompOffConfig.getTextMaxLength() + " characters.", "er");
        }
        try {
            ApproverManager.Result r = approverManager.save(currentStaff, staff, chain, why, "yes".equals(confirmReplace));
            StringBuilder msg = new StringBuilder();
            if (r.getSaved() > 0) {
                msg.append("Approvers saved for ").append(r.getSaved()).append(r.getSaved() == 1 ? " staff member." : " staff.");
            }
            if (r.getUnchanged() > 0) {
                msg.append(msg.length() > 0 ? " " : "").append(r.getUnchanged())
                        .append(r.getUnchanged() == 1 ? " staff member already had" : " staff already had").append(" these approvers.");
            }
            if (r.getReassigned() > 0) {
                msg.append(msg.length() > 0 ? " " : "").append(r.getReassigned())
                        .append(r.getReassigned() == 1 ? " waiting request now has" : " waiting requests now have")
                        .append(" you as approver, because the previous approver is no longer set for that level.");
            }
            if (!r.getFailed().isEmpty()) {
                msg.append(msg.length() > 0 ? " " : "").append("Not saved: ").append(String.join("; ", r.getFailed())).append('.');
            }
            return redirectTo(msg.toString(), r.getFailed().isEmpty() ? "ok" : "er");
        } catch (CompOffException e) {
            return redirectTo(e.getMessage(), "er");
        } catch (RuntimeException e) {
            return redirectTo(unexpected("save approvers", e), "er");
        }
    }

    /** Admin: Settings > General Settings (additional HR approver of each flow). */
    public String generalSettings() {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        generalSettings = settingsManager.settings();
        hrApproverChoices = settingsManager.approverChoices();
        ModuleAccess.Settings st = ModuleAccess.read();
        desktopTeams = settingsManager.desktopTeams(st);
        desktopFlags = settingsManager.desktopFlags(st);
        desktopTeamIds = st.getTeams().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return SUCCESS;
    }

    /**
     * Admin: Comp Off Processing. Comp Off decisions the automatic run does not process (an earlier month clicked after
     * its salary cut-off, or a later month) wait In Process; here the Admin picks a month (leave date) and sees them.
     */
    public String compOffProcessing() {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        java.time.YearMonth ym = pickMonth();
        if (ym != null) {
            heldRows = approvals.heldCompOff(ym);
        }
        return SUCCESS;
    }

    // =====================================================================
    // My Comp Off Report (Employees and Approvers: their own Comp Off)
    // =====================================================================

    /** Day View: the Comp Off request(s) for one leave date (after Go), each with its approvers' decisions. */
    public String compOffDayView() {
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }
        if (day == null) {
            return SUCCESS;                          // first visit: the date picker only
        }
        LocalDate d = DateUtil.parseIso(day);
        if (d == null) {
            addFieldError("day", trimToNull(day) == null ? "Select a date." : "Enter a valid date.");
            day = null;
            return SUCCESS;
        }
        day = DateUtil.iso(d);
        dayLabel = DateUtil.display(d);
        month = String.valueOf(d.getMonthValue());
        year = String.valueOf(d.getYear());
        reportRequests = manager.reportRequests(currentStaff.getStaffID(), d, d);
        return SUCCESS;
    }

    /** Month View: a calendar of the chosen month (after Go) with the Comp Off of each day, and the same days as a table. */
    public String compOffMonthView() {
        String denied = access(EMPLOYEE_PAGES);
        if (denied != null) {
            return denied;
        }
        if (month == null && year == null) {          // opened from the menu: the current month
            LocalDate now = CompOffConfig.today();
            month = String.valueOf(now.getMonthValue());
            year = String.valueOf(now.getYear());
        }
        java.time.YearMonth ym = pickMonth();
        if (ym == null) {
            return SUCCESS;
        }
        month = String.valueOf(ym.getMonthValue());
        year = String.valueOf(ym.getYear());
        shownMonth = ym;
        reportRequests = manager.reportRequests(currentStaff.getStaffID(), ym.atDay(1), ym.atEndOfMonth());
        Map<LocalDate, List<CompensatoryOffRequest>> byDay = new java.util.HashMap<LocalDate, List<CompensatoryOffRequest>>();
        BigDecimal taken = BigDecimal.ZERO;
        for (CompensatoryOffRequest r : reportRequests) {
            LocalDate d = DateUtil.toLocalDate(r.getLeaveDate());
            List<CompensatoryOffRequest> l = byDay.get(d);
            if (l == null) {
                l = new ArrayList<CompensatoryOffRequest>();
                byDay.put(d, l);
            }
            l.add(r);
            Integer n = statusCounts.get(r.getDisplayStatus());
            statusCounts.put(r.getDisplayStatus(), n == null ? 1 : n + 1);
            if (r.isTaken()) {
                taken = taken.add(r.getDuration());
                if (r.getDuration().compareTo(CompOffConstants.FULL_DAY_DURATION) >= 0) {
                    fullDays++;
                } else {
                    halfDays++;
                }
            }
        }
        totalHours = CompensatoryOffRequest.hoursText(taken);
        // whole weeks from Sunday to Saturday; days of the previous / next month are shown greyed (outside)
        LocalDate today = CompOffConfig.today();
        calendarDays = new ArrayList<CalendarDay>();
        LocalDate start = ym.atDay(1).minusDays(ym.atDay(1).getDayOfWeek().getValue() % 7);
        LocalDate end = ym.atEndOfMonth().plusDays(6 - ym.atEndOfMonth().getDayOfWeek().getValue() % 7);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            List<CompensatoryOffRequest> l = java.time.YearMonth.from(d).equals(ym) ? byDay.get(d) : null;
            CalendarDay c = new CalendarDay(d, d.equals(today), l == null ? Collections.<CompensatoryOffRequest>emptyList() : l);
            c.outside = !java.time.YearMonth.from(d).equals(ym);
            calendarDays.add(c);
        }
        return SUCCESS;
    }

    /** One day of the Month View calendar. */
    public static class CalendarDay {
        private final LocalDate date;
        private final boolean today;
        private final List<CompensatoryOffRequest> requests;
        /** A day of the previous / next month that fills the first / last week. */
        private boolean outside;

        CalendarDay(LocalDate date, boolean today, List<CompensatoryOffRequest> requests) {
            this.date = date;
            this.today = today;
            this.requests = requests;
        }
        public boolean isBlank() { return date == null; }
        public int getDayOfMonth() { return date == null ? 0 : date.getDayOfMonth(); }
        public String getIso() { return date == null ? "" : DateUtil.iso(date); }
        public String getLabel() { return date == null ? "" : DateUtil.display(date); }
        public boolean isToday() { return today; }
        public boolean isWeekend() { return date != null && date.getDayOfWeek().getValue() >= 6; }
        /** S M T W T F S */
        public String getWeekdayLetter() { return date == null ? "" : "SMTWTFS".substring(date.getDayOfWeek().getValue() % 7, date.getDayOfWeek().getValue() % 7 + 1); }
        public List<CompensatoryOffRequest> getRequests() { return requests; }
        public boolean isOutside() { return outside; }
    }

    /**
     * My Team Reports, Comp Off - Month View (Approvers): one row per member of the approver's own team (or the one staff
     * member chosen), one column per day of the month; a day with Comp Off shows 1d / ½d in its status colour.
     */
    public String teamCompOffMonthView() {
        String denied = access(TEAM_PAGES);
        if (denied != null) {
            return denied;
        }
        if (!isApproverMenu()) {
            return "forbidden";
        }
        Integer team = currentStaff.getTeamID();
        for (Map<String, Object> t : reports.teams()) {
            if (team != null && ((Number) t.get("teamId")).intValue() == team) {
                myTeamName = String.valueOf(t.get("teamName"));
            }
        }
        if (myTeamName == null) {
            addActionError("You are not in a team, so there is no team report. Please contact the admin.");
            return SUCCESS;
        }
        teamStaff = reports.teamMembers(team);
        java.time.YearMonth ym = pickMonth();
        if (month == null && year == null) {
            return SUCCESS;                          // first visit: the filters only
        }
        if (trimToNull(teamId) == null || !teamId.trim().equals(String.valueOf(team))) {
            addFieldError("teamId", "Select your team.");
        }
        Integer staff = null;
        if (trimToNull(staffFilter) == null) {
            addFieldError("staffFilter", "Select a staff member.");
        } else if (!"all".equals(staffFilter.trim())) {
            staff = optionalChoice("staffFilter", staffFilter, teamStaff, "staffId", "Select a staff member of your team.");
        }
        String st = trimToNull(coStatus);
        if (st != null && !TEAM_STATUSES.contains(st)) {
            addFieldError("coStatus", "Select a valid status.");
        }
        if (hasFieldErrors() || ym == null) {
            periodLabel = null;
            return SUCCESS;
        }
        List<Integer> ids = new ArrayList<Integer>();
        String staffName = "All staff";
        for (Map<String, Object> m : teamStaff) {
            int id = ((Number) m.get("staffId")).intValue();
            if (staff == null || staff == id) {
                ids.add(id);
            }
            if (staff != null && staff == id) {
                staffName = String.valueOf(m.get("staffName"));
            }
        }
        teamReportTitle = myTeamName + " report for " + staffName + " for " + periodLabel;
        // one CalendarDay per day of the month (the column headings)
        monthDays = new ArrayList<CalendarDay>();
        LocalDate today = CompOffConfig.today();
        for (LocalDate d = ym.atDay(1); !d.isAfter(ym.atEndOfMonth()); d = d.plusDays(1)) {
            monthDays.add(new CalendarDay(d, d.equals(today), Collections.<CompensatoryOffRequest>emptyList()));
        }
        Map<Integer, TeamRow> byStaff = new java.util.LinkedHashMap<Integer, TeamRow>();
        teamRows = new ArrayList<TeamRow>();
        for (Map<String, Object> m : teamStaff) {
            int id = ((Number) m.get("staffId")).intValue();
            if (ids.contains(id)) {
                TeamRow r = new TeamRow(m.get("staffName") + " (" + m.get("employeeCode") + ")", monthDays.size());
                byStaff.put(id, r);
                teamRows.add(r);
            }
        }
        for (Map<String, Object> c : reports.teamCompOff(ids, ym.atDay(1), ym.atEndOfMonth())) {
            String shown = String.valueOf(c.get("status"));
            boolean match = st == null || st.equals(shown) || ("Approved".equals(st) && CompOffConstants.REQ_COMPLETED.equals(shown));
            TeamRow r = byStaff.get(((Number) c.get("staffId")).intValue());
            if (match && r != null) {
                r.add(DateUtil.toLocalDate((java.util.Date) c.get("leaveDate")), (String) c.get("dayType"), c.get("duration"), shown);
            }
        }
        return SUCCESS;
    }

    /** One staff member's row of the team report: a cell per day of the month and the total of the days taken. */
    public static class TeamRow {
        private final String name;
        private final List<TeamCell> cells = new ArrayList<TeamCell>();
        private BigDecimal taken = BigDecimal.ZERO;

        TeamRow(String name, int days) {
            this.name = name;
            for (int i = 0; i < days; i++) {
                cells.add(new TeamCell());
            }
        }
        /** Adds a request (requests come latest first within a day: the first one is the one shown). */
        void add(LocalDate date, String dayType, Object duration, String status) {
            BigDecimal days;
            try {
                days = duration == null ? CompOffConstants.FULL_DAY_DURATION : new BigDecimal(duration.toString().trim());
            } catch (NumberFormatException e) {
                days = CompOffConstants.FULL_DAY_DURATION;
            }
            String type = CompOffConstants.LEAVE_DAY_FD.equals(dayType) ? CompOffConstants.FULL_DAY
                    : "HD".equals(dayType) ? CompOffConstants.HALF_DAY : dayType;
            cells.get(date.getDayOfMonth() - 1).requests.add(new String[] { DateUtil.display(date),
                    type + " (" + CompensatoryOffRequest.hoursText(days) + " Hrs)", status,
                    days.compareTo(CompOffConstants.FULL_DAY_DURATION) >= 0 ? "1d" : "\u00bdd" });
            if (!CompOffConstants.REQ_REJECTED.equals(status) && !CompOffConstants.REQ_CANCELLED.equals(status)) {
                taken = taken.add(days);
            }
        }
        public String getName() { return name; }
        public List<TeamCell> getCells() { return cells; }
        /** Days taken in the month (Approved, Completed, In Process, Pending), e.g. "3d" / "2.5d". */
        public String getTotal() { return CompOffManager.plainDays(taken).replaceAll("\\.0$", "") + "d"; }
    }

    /** One day of a team report row: its requests, latest first ({ leave date, duration, status, 1d / ½d }). */
    public static class TeamCell {
        private final List<String[]> requests = new ArrayList<String[]>();
        public List<String[]> getRequests() { return requests; }
        public boolean isMarked() { return !requests.isEmpty(); }
        public String getMark() { return requests.isEmpty() ? "" : requests.get(0)[3]; }
        public String getStatus() { return requests.isEmpty() ? "" : requests.get(0)[2]; }
    }

    /** Admin: carries out the oldest decision waiting for the month (mode "one") or all of them (mode "all"). */
    public String processCompOffMonth() {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        java.time.YearMonth ym = pickMonth();
        if (ym == null) {
            return redirectTo("Select a month and year.", "er");
        }
        if (!"one".equals(mode) && !"all".equals(mode)) {
            return redirectTo("Choose One request or All requests.", "er");
        }
        try {
            approvals.setRequestBaseUrl(requestBaseUrl(ServletActionContext.getRequest()));
            int done = approvals.processCompOffMonth(ym, "all".equals(mode), "by Admin " + currentStaff.getStaffName());
            int failed = approvals.getLastFailed();
            approvals.flushEmails();   // after the decisions are saved
            int left = approvals.heldCompOff(ym).size();
            if (done == 0 && failed == 0) {
                return redirectTo("Nothing is waiting for " + periodLabel + ".", "er");
            }
            String msg = done + " request" + (done == 1 ? "" : "s") + " for " + periodLabel + " processed."
                    + (failed > 0 ? " " + failed + " could not be processed and stay In Process (see the server log)." : "")
                    + " " + left + " still In Process.";
            return redirectTo(msg, done == 0 ? "er" : "ok");
        } catch (CompOffException e) {
            return redirectTo(e.getMessage(), "er");
        } catch (RuntimeException e) {
            return redirectTo(unexpected("process comp off month", e), "er");
        }
    }

    /** Month / year of the Comp Off Processing page (any month, no cut-off for the Admin). Field errors when invalid. */
    private java.time.YearMonth pickMonth() {
        int now = CompOffConfig.today().getYear();
        yearOptions = new ArrayList<Integer>();
        for (int y = now + 1; y >= now - 3; y--) {
            yearOptions.add(y);
        }
        if (month == null && year == null) {
            return null;
        }
        Integer m = parseIntOrNull(trimToNull(month)), y = parseIntOrNull(trimToNull(year));
        if (m == null || m < 1 || m > 12) {
            addFieldError("month", "Select a month.");
        }
        if (y == null || y < 2000 || y > now + 1) {
            addFieldError("year", "Select a year.");
        }
        if (hasFieldErrors()) {
            return null;
        }
        if (!yearOptions.contains(y)) {
            yearOptions.add(y);
        }
        periodLabel = MONTHS[m - 1] + " " + y;
        return java.time.YearMonth.of(y, m);
    }

    /** Saves one HR approver setting (paramName, hrApproverId, hrActive = "1" for on). */
    public String saveGeneralSettings() {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        String id = trimToNull(hrApproverId);
        if (id != null && !id.matches("[0-9]{1,9}")) {
            return redirectTo("The approver could not be read. Please try again.", "er");
        }
        try {
            return redirectTo(settingsManager.save(paramName, id == null ? null : Integer.valueOf(id), "1".equals(hrActive)), "ok");
        } catch (CompOffException e) {
            return redirectTo(e.getMessage(), "er");
        } catch (RuntimeException e) {
            return redirectTo(unexpected("save general settings", e), "er");
        }
    }

    /** Saves the Desktop App Settings section: Comp Off teams (desktopTeam) and the flags switched on (desktopFlag). */
    public String saveDesktopSettings() {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        List<Integer> teams = new ArrayList<Integer>();
        for (String t : desktopTeam == null ? new String[0] : desktopTeam) {
            if (t == null || !t.trim().matches("[0-9]{1,9}")) {
                return redirectTo("The selected teams could not be read. Please try again.", "er");
            }
            if (!teams.contains(Integer.valueOf(t.trim()))) {
                teams.add(Integer.valueOf(t.trim()));
            }
        }
        Map<String, Boolean> flags = new HashMap<String, Boolean>();
        for (String f : desktopFlag == null ? new String[0] : desktopFlag) {
            flags.put(f, Boolean.TRUE);
        }
        try {
            return redirectTo(settingsManager.saveDesktop(teams, flags), "ok");
        } catch (CompOffException e) {
            return redirectTo(e.getMessage(), "er");
        } catch (RuntimeException e) {
            return redirectTo(unexpected("save the Desktop App Settings", e), "er");
        }
    }

    // =====================================================================
    // Admin: Apply Comp Off for Staff
    // =====================================================================

    /** Most employee codes in one Apply Comp Off for Staff. */
    static final int MAX_ADMIN_CODES = 100;

    /** READ: Apply Comp Off for Staff; with employeeCodes (Find), the staff members found. */
    public String adminApplyCompOff() {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        if (employeeCodes != null) {
            adminPicks();
        }
        adminApplied = reports.adminAppliedCompOffs();
        return SUCCESS;
    }

    /**
     * SAVE: one Comp Off (Full day, Day Shift) on compOffDate for every staff member in employeeCodes, Approved by the
     * logged-in Admin. All staff and the date are checked first; nothing is saved while one of them has a problem.
     */
    public String saveAdminCompOff() {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        List<CompOffManager.AdminPick> picks = adminPicks();
        LocalDate day = null;
        String d = trimToNull(compOffDate);
        if (d == null) {
            addFieldError("compOffDate", "Compensatory Off Date is required.");
        } else if ((day = DateUtil.parseIso(d)) == null) {
            addFieldError("compOffDate", "Compensatory Off Date is not a valid date.");
        }
        String notesText = requiredText("notes", notes, "Notes", "Notes are required.");
        if (day != null && picks != null && !hasFieldErrors()) {
            for (CompOffManager.AdminPick p : picks) {
                String problem = manager.applyProblem(p.getStaff().getStaffID(), java.util.Collections.singletonList(day));
                if (problem != null) {
                    addFieldError("compOffDate", p.getStaff().getStaffName() + " (" + p.getStaff().getEmployeeCode() + "): "
                            + problem.replace("your ", "the "));
                    break;
                }
            }
        }
        if (!hasFieldErrors()) {
            List<String> done = new ArrayList<String>();
            try {
                Notifier mail = notifier();
                for (CompOffManager.AdminPick p : picks) {
                    manager.applyApprovedByAdmin(currentStaff, p.getStaff(), day, notesText, mail);
                    done.add(p.getStaff().getEmployeeCode());
                }
                mail.flush();
                return redirectTo("Comp Off for " + DateUtil.display(DateUtil.toDate(day)) + " saved as Approved for " + done.size()
                        + " staff member" + (done.size() == 1 ? "" : "s") + " (" + String.join(", ", done) + ").", "ok");
            } catch (CompOffException e) {
                String msg = done.isEmpty() ? e.getMessage() : "Saved for " + String.join(", ", done) + " only. " + e.getMessage();
                if (done.isEmpty()) {
                    addBusinessError(e);
                } else {
                    return redirectTo(msg, "er");
                }
            } catch (RuntimeException e) {
                addActionError(unexpected("apply comp off for staff", e));
            }
        }
        adminApplied = reports.adminAppliedCompOffs();
        return INPUT;
    }

    /**
     * employeeCodes -> the staff found (field error "employeeCodes" when the codes are not "CODE,CODE" - letters and
     * digits only, no spaces - or a code is not found / has no available day). Null when there is an error.
     */
    private List<CompOffManager.AdminPick> adminPicks() {
        String v = employeeCodes == null ? "" : employeeCodes;
        if (v.isEmpty()) {
            addFieldError("employeeCodes", "Employee codes are required.");
            return null;
        }
        if (!v.matches("[A-Za-z0-9]{1,45}(,[A-Za-z0-9]{1,45})*")) {
            addFieldError("employeeCodes", "Enter employee codes separated by commas only: no spaces or other characters (e.g. EMP101,EMP103).");
            return null;
        }
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<String>();
        for (String c : v.split(",")) {
            codes.add(c.toUpperCase(java.util.Locale.ENGLISH));
        }
        if (codes.size() > MAX_ADMIN_CODES) {
            addFieldError("employeeCodes", "Enter at most " + MAX_ADMIN_CODES + " employee codes at a time.");
            return null;
        }
        List<String> notFound = new ArrayList<String>(), noBalance = new ArrayList<String>();
        adminPicks = manager.adminPicks(new ArrayList<String>(codes), notFound, noBalance);
        if (!notFound.isEmpty() || !noBalance.isEmpty()) {
            addFieldError("employeeCodes", (notFound.isEmpty() ? "" : "Not found in your company: " + String.join(", ", notFound) + ". ")
                    + (noBalance.isEmpty() ? "" : "No available Holiday Intimation (balance 0): " + String.join(", ", noBalance) + ". ")
                    + "Correct the codes and click Find again.");
            return null;
        }
        return adminPicks;
    }

    // =====================================================================
    // Admin: Comp Off Cancel
    // =====================================================================

    /** READ: Comp Off Cancel: Pending / In Process / Approved Comp Off in a period (runs when Go is clicked). */
    public String compOffCancel() {
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        teams = reports.teams();
        staffOptions = reports.staff();
        if (run == null) {
            return SUCCESS;
        }
        LocalDate from = requiredDate("fromDate", fromDate, "From date");
        LocalDate to = requiredDate("toDate", toDate, "To date");
        if (from != null && to != null && to.isBefore(from)) {
            addFieldError("toDate", "To date cannot be before From date.");
        }
        Integer team = optionalChoice("teamId", teamId, teams, "teamId", "Select a valid team.");
        Integer staff = optionalChoice("staffFilter", staffFilter, staffOptions, "staffId", "Select a valid staff member.");
        if (team != null && staff != null && !team.equals(teamOf(staff))) {
            addFieldError("staffFilter", "The selected staff member is not in the selected team.");
        }
        if (!hasFieldErrors()) {
            cancelRows = reports.cancellableCompOffs(from, to, team, staff);
            reportRun = true;
        }
        return SUCCESS;
    }

    /** CANCEL (Admin): one Comp Off (compOffRequestId, encrypted) with Cancel Notes; back to the same filters. */
    public String cancelCompOffByAdmin() {
        if (!isPost()) {
            return redirectTo(null, null);
        }
        String denied = access(ADMIN_PAGES);
        if (denied != null) {
            return denied;
        }
        Integer id = requiredId(compOffRequestId, "Comp Off request");
        String why = requiredText("cancelReason", cancelReason, "Cancel Notes", "Cancel Notes are required.");
        if (hasFieldErrors() || hasActionErrors()) {
            return redirectTo(firstError(), "er");
        }
        try {
            Notifier mail = notifier();
            manager.cancelByAdmin(currentStaff, id, why, mail);
            mail.flush();
            return redirectTo("Comp Off cancelled. The Holiday Intimation is Approved again and the balance is restored.", "ok");
        } catch (CompOffException e) {
            return redirectTo(e.getMessage(), "er");
        } catch (RuntimeException e) {
            return redirectTo(unexpected("cancel comp off", e), "er");
        }
    }

    /** "6,5,9" -> ids in order (null when not a plain comma separated list of ids, at most 5000). */
    private static List<Integer> idList(String value) {
        String v = trimToNull(value);
        if (v == null) {
            return new ArrayList<Integer>();
        }
        String[] parts = v.split(",", -1);
        if (parts.length > 5000) {
            return null;
        }
        List<Integer> out = new ArrayList<Integer>();
        for (String p : parts) {
            if (!p.matches("[0-9]{1,9}")) {
                return null;
            }
            out.add(Integer.valueOf(p));
        }
        return out;
    }

    /** Filter id: null when blank or not a number (an unknown id simply matches nothing). */
    private static Integer idOrNull(String value) {
        String v = trimToNull(value);
        return v != null && v.matches("[0-9]{1,9}") ? Integer.valueOf(v) : null;
    }

    /** Optional id filter: null when blank; a field error when it is not one of the offered choices. */
    private Integer optionalChoice(String field, String value, List<Map<String, Object>> choices, String key, String message) {
        String v = trimToNull(value);
        if (v == null) {
            return null;
        }
        if (v.matches("[0-9]{1,9}")) {
            int id = Integer.parseInt(v);
            for (Map<String, Object> c : choices) {
                if (((Number) c.get(key)).intValue() == id) {
                    return id;
                }
            }
        }
        addFieldError(field, message);
        return null;
    }

    private Integer teamOf(Integer staffId) {
        for (Map<String, Object> s : staffOptions) {
            if (((Number) s.get("staffId")).intValue() == staffId) {
                Object t = s.get("teamId");
                return t == null ? null : ((Number) t).intValue();
            }
        }
        return null;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * The only check of the module, called first by every page method. Reads what the host application's login put in
     * the session and decides whether the user may open the page:
     * <ul>
     * <li>staffID (number or text; missing / 0 = not logged in) -> "sessionOut" (the host application's session-expired
     *     page). An Approve / Reject link from an email is remembered first (ReturnPage), so the host login can open it
     *     after login.</li>
     * <li>userType -> access level (CompOffConfig.accessRole: C = Admin; T, S, QC = Employee; TL, PM, MR, HR = Approver);
     *     no level, or a level the page does not allow -> "forbidden" (Access denied, HTTP 403).</li>
     * <li>companyID -> the company every query of this request works for (CompanyContext).</li>
     * <li>Desktop App Settings (team list, the page's Y / N flag, HR named in General Settings), not for the Admin ->
     *     "forbidden".</li>
     * </ul>
     * Loads the user's Staffs row (name, team, approver chain) and the one-time message.
     *
     * @param allowedLevels the access levels the page allows; none = any level
     * @return null when the user may open the page, otherwise the result to return
     */
    private String access(String... allowedLevels) {
        int staffId = SessionKeys.number(session == null ? null : session.get(SessionKeys.STAFF_ID));
        int companyId = SessionKeys.number(session == null ? null : session.get(SessionKeys.COMPANY_ID));
        Object type = session == null ? null : session.get(SessionKeys.USER_TYPE);
        if (staffId <= 0 || companyId <= 0) {
            rememberApprovalLink();
            return SESSION_OUT;
        }
        String level = CompOffConfig.accessRole(type == null ? null : String.valueOf(type).trim());
        if (level == null || allowedLevels.length > 0 && !java.util.Arrays.asList(allowedLevels).contains(level)) {
            return forbidden();
        }
        com.util.CompanyContext.set(companyId);   // cleared at the end of the page (layout/footer.jsp)
        currentStaff = manager.findStaff(staffId);
        if (currentStaff == null) {
            return SESSION_OUT;   // no active Staffs row for the session's staffID
        }
        String action = com.opensymphony.xwork2.ActionContext.getContext().getName();
        if (!CompOffConstants.ROLE_ADMIN.equals(level) && ModuleAccess.covers(action)) {
            access = ModuleAccess.of(currentStaff);
            if (!access.allows(action)) {
                return forbidden();
            }
        }
        flashMessage = (String) session.remove(SessionKeys.FLASH_MESSAGE);
        flashType = (String) session.remove(SessionKeys.FLASH_TYPE);
        return null;
    }

    private static String forbidden() {
        ServletActionContext.getResponse().setStatus(javax.servlet.http.HttpServletResponse.SC_FORBIDDEN);
        return FORBIDDEN;
    }

    /** An Approve / Reject link from an email opened without login is remembered (ReturnPage); nothing else is. */
    private static void rememberApprovalLink() {
        try {
            javax.servlet.http.HttpServletRequest req = ServletActionContext.getRequest();
            if (req != null && "GET".equalsIgnoreCase(req.getMethod())) {
                String page = com.opensymphony.xwork2.ActionContext.getContext().getName() + ".action"
                        + (req.getQueryString() == null ? "" : "?" + req.getQueryString());
                com.util.ReturnPage.remember(req, ServletActionContext.getResponse(), page);
            }
        } catch (RuntimeException ignored) {
            // remembering the link is a convenience only
        }
    }

    private void loadHolidayIntimationPage() {
        intimations = manager.intimations(currentStaff.getStaffID());
        approver = manager.holidayIntimationApprover(currentStaff.getStaffID());
    }

    private void loadApplyCompOffPage() {
        Integer id = currentStaff.getStaffID();
        summary = manager.summary(id);
        availableList = manager.available(id);
        checkBalance();
        workedDates = manager.approvedWorkedDates(id);
        compOffRequests = manager.requests(id, CompOffConfig.today());
        historyByRequest = manager.historyByRequest(id);
        staffNames = manager.staffNames();
        approver = manager.compOffApprover(id);
    }

    /** Logs when the stored balance (CompOffBalanceDetails) and the Available intimation records disagree. */
    private void checkBalance() {
        if (isBalanceMismatch()) {
            LOG.warning("Comp Off balance mismatch for staff " + currentStaff.getStaffID() + ": CompOffBalanceDetails.balance="
                    + (summary == null ? "none" : summary.getBalance()) + ", Available intimations=" + availableList.size());
        }
    }

    /**
     * Sends the approval request email.
     * TODO: wire to the mail service (e.g. SendGrid, already in WEB-INF/lib).
     */
    /** Emails of this request (sent by flush() after the business step has been saved). */
    private Notifier notifier() {
        return new Notifier(manager, requestBaseUrl(ServletActionContext.getRequest()));
    }

    /** scheme://host[:port]/context of the current request: the links' address when APP_BASE_URL is not set. */
    static String requestBaseUrl(javax.servlet.http.HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        int port = req.getServerPort();
        boolean std = port <= 0 || ("http".equals(req.getScheme()) && port == 80) || ("https".equals(req.getScheme()) && port == 443);
        return req.getScheme() + "://" + req.getServerName() + (std ? "" : ":" + port) + req.getContextPath();
    }

    private static String displayDates(List<LocalDate> dates) {
        List<String> out = new ArrayList<String>();
        for (LocalDate d : dates) {
            out.add(DateUtil.display(d));
        }
        return String.join(", ", out);
    }

    private boolean isPost() {
        return "POST".equalsIgnoreCase(ServletActionContext.getRequest().getMethod());
    }

    private static final String[] MONTHS = { "January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December" };

    /**
     * Approval pages show the requests APPLIED in the month and year chosen with Go (both lists). Before Go only the
     * picker is shown. An email link (open=...) shows the month its request was applied in, so the request can be ticked.
     */
    private void showPeriod(List<ApprovalManager.Row> waiting, List<ApprovalManager.Row> inProcess) {
        LocalDate today = CompOffConfig.today();
        java.util.TreeSet<Integer> years = new java.util.TreeSet<Integer>(Collections.reverseOrder());
        years.add(today.getYear());
        for (List<ApprovalManager.Row> l : java.util.Arrays.asList(waiting, inProcess)) {
            for (ApprovalManager.Row r : l) {
                if (r.getAppliedOn() != null) {
                    years.add(DateUtil.toLocalDate(r.getAppliedOn()).getYear());
                }
            }
        }
        if (month == null && year == null && open != null) {
            Integer id = com.util.IdCrypt.dec(open);
            for (ApprovalManager.Row r : waiting) {
                if (r.getId().equals(id) && r.getAppliedOn() != null) {
                    LocalDate d = DateUtil.toLocalDate(r.getAppliedOn());
                    month = String.valueOf(d.getMonthValue());
                    year = String.valueOf(d.getYear());
                }
            }
        }
        yearOptions = new ArrayList<Integer>(years);
        if (month == null && year == null) {
            return;                                  // first visit: the picker only
        }
        Integer m = parseIntOrNull(trimToNull(month)), y = parseIntOrNull(trimToNull(year));
        if (m == null || m < 1 || m > 12) {
            addFieldError("month", "Select a month.");
        }
        if (y == null || y < 2000 || y > today.getYear()) {
            addFieldError("year", "Select a year.");
        }
        if (hasFieldErrors()) {
            return;
        }
        if (!years.contains(y)) {
            years.add(y);
            yearOptions = new ArrayList<Integer>(years);
        }
        java.time.YearMonth ym = java.time.YearMonth.of(y, m);
        approvalRows = appliedIn(waiting, ym);
        processingRows = appliedIn(inProcess, ym);
        periodLabel = MONTHS[m - 1] + " " + y;
        month = String.valueOf(m);
        year = String.valueOf(y);
    }

    private static List<ApprovalManager.Row> appliedIn(List<ApprovalManager.Row> rows, java.time.YearMonth ym) {
        List<ApprovalManager.Row> out = new ArrayList<ApprovalManager.Row>();
        for (ApprovalManager.Row r : rows) {
            if (r.getAppliedOn() != null && java.time.YearMonth.from(DateUtil.toLocalDate(r.getAppliedOn())).equals(ym)) {
                out.add(r);
            }
        }
        return out;
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || !s.matches("\\d{1,4}")) {
            return null;
        }
        return Integer.valueOf(s);
    }

    private static final String LINK_FOR_OTHER = "This approval link was sent to another approver. Sign in as that approver to use it.";

    /**
     * An email link names its approver (ap=...) and is only for that approver. No ap (older links, the menu) is fine.
     * The decision itself is still checked by decide(): only requests waiting with the logged-in approver go through.
     */
    private boolean linkIsForMe() {
        if (ap == null || ap.isEmpty()) {
            return true;
        }
        Integer id = com.util.IdCrypt.dec(ap);
        return id != null && id.equals(currentStaff.getStaffID());
    }

    private String redirectTo(String message, String type) {
        if (message != null) {
            session.put(SessionKeys.FLASH_MESSAGE, message);
            session.put(SessionKeys.FLASH_TYPE, type);
        }
        return REDIRECT;
    }

    /** Trims, normalises textarea line breaks (CRLF -> LF, as counted by the browser) and maps blank to null. */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.replace("\r\n", "\n").trim();
        return t.isEmpty() ? null : t;
    }

    private LocalDate requiredDate(String field, String value, String label) {
        String v = trimToNull(value);
        if (v == null) {
            addFieldError(field, label + " is required.");
            return null;
        }
        LocalDate d = DateUtil.parseIso(v);
        if (d == null) {
            addFieldError(field, label + " is not a valid date.");
        }
        return d;
    }

    private String requiredChoice(String field, String value, String label, List<String> allowed) {
        String v = trimToNull(value);
        if (v == null) {
            addFieldError(field, label + " is required.");
            return null;
        }
        if (!allowed.contains(v)) {
            addFieldError(field, "Select a valid " + label.toLowerCase() + ".");
            return null;
        }
        return v;
    }

    private String requiredText(String field, String value, String label, String requiredMessage) {
        String v = trimToNull(value);
        if (v == null) {
            addFieldError(field, requiredMessage);
            return null;
        }
        return maxLength(field, v, label);
    }

    private String optionalText(String field, String value, String label) {
        String v = trimToNull(value);
        return v == null ? null : maxLength(field, v, label);
    }

    private String maxLength(String field, String v, String label) {
        int max = CompOffConfig.getTextMaxLength();
        if (v.length() > max) {
            addFieldError(field, label + " can be at most " + max + " characters.");
            return null;
        }
        return v;
    }

    /** The request id behind an encrypted value from the page (IdCrypt); an action error when missing or invalid. */
    private Integer requiredId(String value, String label) {
        Integer id = com.util.IdCrypt.dec(value);
        if (id != null) {
            return id;
        }
        addActionError(label + " is missing or invalid.");
        return null;
    }

    private String firstError() {
        if (hasActionErrors()) {
            return getActionErrors().iterator().next();
        }
        for (List<String> messages : getFieldErrors().values()) {
            if (!messages.isEmpty()) {
                return messages.get(0);
            }
        }
        return "Please check the details and try again.";
    }

    private void addBusinessError(CompOffException e) {
        if (e.getField() != null) {
            addFieldError(e.getField(), e.getMessage());
        } else {
            addActionError(e.getMessage());
        }
    }

    private String unexpected(String what, RuntimeException e) {
        LOG.log(Level.SEVERE, "Failed to " + what + " for " + (currentStaff == null ? "?" : currentStaff.getStaffID()), e);
        return "Something went wrong. Please try again, and contact the admin if it keeps happening.";
    }

    // =====================================================================
    // Values used by the JSPs
    // =====================================================================

    public List<String> getShiftTypes() { return CompOffConstants.SHIFT_TYPES; }
    public List<String> getDayTypes() { return CompOffConstants.DAY_TYPES; }
    public int getTextMaxLength() { return CompOffConfig.getTextMaxLength(); }
    public int getApplyMaxDays() { return CompOffConfig.getApplyMaxDays(); }
    public String getToday() { return DateUtil.iso(CompOffConfig.today()); }

    /** First day of the current month (yyyy-MM-dd): the earliest leave date that can be applied for. */
    public String getMonthStart() { return DateUtil.iso(CompOffManager.monthStart(CompOffConfig.today())); }

    /** Days shown as available: one Full day per Available intimation listed under the (i) icon. */
    public String getAvailableDays() { return days(BigDecimal.valueOf(availableList.size())); }

    /** True when CompOffBalanceDetails.Balance does not match the Available intimation records. */
    public boolean isBalanceMismatch() {
        BigDecimal stored = summary == null ? BigDecimal.ZERO : summary.getBalance();
        return stored.compareTo(BigDecimal.valueOf(availableList.size())) != 0;
    }

    /** Comp Off amount with one decimal ("2.0"), whatever scale the database column has. */
    public String days(Object value) {
        BigDecimal v = value instanceof BigDecimal ? (BigDecimal) value
                : value instanceof Number ? new BigDecimal(value.toString()) : BigDecimal.ZERO;
        return CompOffManager.plainDays(v);
    }

    /** Name of the logged-in user's user type (e.g. "Team leader"), shown in the top bar. */
    public String getCurrentUserTypeName() {
        if (currentUserTypeName == null && currentStaff != null) {
            currentUserTypeName = manager.userTypeName(currentStaff);
        }
        return currentUserTypeName;
    }

    /** DesktopAppSettings access of the logged-in user (worked out once per request; null when not logged in). */
    public ModuleAccess getAccess() {
        if (access == null && currentStaff != null) {
            access = ModuleAccess.of(currentStaff);
        }
        return access;
    }

    /** The user's own pages (team in compOffTeamIDs, or a named HR approver). */
    public boolean isEmployeeMenu() {
        return currentStaff != null && !CompOffConstants.ROLE_ADMIN.equals(currentStaff.getAccessRole()) && getAccess().isSelf();
    }
    /** My Team Reports. */
    public boolean isApproverMenu() {
        return currentStaff != null && CompOffConstants.ROLE_APPROVER.equals(currentStaff.getAccessRole()) && getAccess().isTeam();
    }
    /** Approve or Reject - Holiday Working Details in the menu (greyed out when switched off). */
    public boolean isHolidayApprovalMenu() {
        return currentStaff != null && !CompOffConstants.ROLE_EMPLOYEE.equals(currentStaff.getAccessRole()) && getAccess().isHolidayApprover();
    }
    /** Approve or Reject - Comp Off in the menu (greyed out when switched off). */
    public boolean isCompOffApprovalMenu() {
        return currentStaff != null && !CompOffConstants.ROLE_EMPLOYEE.equals(currentStaff.getAccessRole()) && getAccess().isCompOffApprover();
    }
    public boolean isAdminMenu() {
        return currentStaff != null && CompOffConstants.ROLE_ADMIN.equals(currentStaff.getAccessRole());
    }

    /** Worked dates (yyyy-MM-dd, comma separated) that already have an open intimation, for the client-side duplicate check. */
    public String getTakenWorkedDates() {
        List<String> dates = new ArrayList<String>();
        for (HolidayRequests h : intimations) {
            String st = h.getHolidayWorkingStatus();
            if (!CompOffConstants.HW_REJECTED.equals(st) && !CompOffConstants.HW_CANCELLED.equals(st)) {
                dates.add(DateUtil.iso(DateUtil.toLocalDate(h.getWorkRequestDate())));
            }
        }
        return String.join(",", dates);
    }

    /** Available worked dates (FIFO order) for the client-side balance / FIFO hint. */
    public String getAvailableDates() {
        List<String> dates = new ArrayList<String>();
        for (HolidayRequests h : availableList) {
            dates.add(DateUtil.iso(DateUtil.toLocalDate(h.getWorkRequestDate())));
        }
        return String.join(",", dates);
    }

    /** Worked dates (yyyy-MM-dd) of approved Holiday Intimations: Comp Off cannot be applied for these days. */
    public String getApprovedWorkedDates() {
        List<String> dates = new ArrayList<String>();
        for (java.util.Date d : workedDates) {
            dates.add(DateUtil.iso(DateUtil.toLocalDate(d)));
        }
        return String.join(",", dates);
    }

    /** Leave dates (yyyy-MM-dd) already applied (Pending / Approved / Completed), for the client-side duplicate check. */
    public String getAppliedLeaveDates() {
        List<String> dates = new ArrayList<String>();
        for (CompensatoryOffRequest r : compOffRequests) {
            if (CompOffConstants.REQ_PENDING.equals(r.getLeaveStatus())
                    || CompOffConstants.REQ_APPROVED.equals(r.getLeaveStatus())
                    || CompOffConstants.REQ_COMPLETED.equals(r.getLeaveStatus())) {
                dates.add(DateUtil.iso(DateUtil.toLocalDate(r.getLeaveDate())));
            }
        }
        return String.join(",", dates);
    }

    // ---- SessionAware
    @Override
    public void setSession(Map<String, Object> session) { this.session = session; }

    // ---- Getters / setters
    public String getWorkedDate() { return workedDate; }
    public void setWorkedDate(String workedDate) { this.workedDate = workedDate; }

    public String getShiftType() { return shiftType; }
    public void setShiftType(String shiftType) { this.shiftType = shiftType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDayType() { return dayType; }
    public void setDayType(String dayType) { this.dayType = dayType; }

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }

    public String getLeaveDates() { return leaveDates; }
    public void setLeaveDates(String leaveDates) { this.leaveDates = leaveDates; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getHolidayWorkingId() { return holidayWorkingId; }
    public void setHolidayWorkingId(String holidayWorkingId) { this.holidayWorkingId = holidayWorkingId; }

    public String getCompOffRequestId() { return compOffRequestId; }
    public void setCompOffRequestId(String compOffRequestId) { this.compOffRequestId = compOffRequestId; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public void setIds(String ids) { this.ids = ids; }
    public void setDecision(String decision) { this.decision = decision; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public List<ApprovalManager.Row> getApprovalRows() { return approvalRows; }
    public List<ApprovalManager.Row> getProcessingRows() { return processingRows; }

    /** Name of the running action, used to highlight its menu entry. */
    public String getActionName() { return com.opensymphony.xwork2.ActionContext.getContext().getName(); }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getStaffFilter() { return staffFilter; }
    public void setStaffFilter(String staffFilter) { this.staffFilter = staffFilter; }
    public void setRun(String run) { this.run = run; }
    public void setHwId(String hwId) { this.hwId = hwId; }
    public String getMonth() { return month; }
    /** Digits only (anything else counts as not selected), so no free text reaches the page or the redirect. */
    public void setMonth(String month) { this.month = digitsOrEmpty(month); }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = digitsOrEmpty(year); }

    private static String digitsOrEmpty(String v) {
        return v == null ? null : v.trim().matches("\\d{1,4}") ? v.trim() : "";
    }
    public void setOpen(String open) { this.open = open; }
    /** "October 2026" once a month is shown; null before Go. */
    public String getPeriodLabel() { return periodLabel; }
    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }
    public String getDayLabel() { return dayLabel; }
    public List<CompensatoryOffRequest> getReportRequests() { return reportRequests; }
    public List<CalendarDay> getCalendarDays() { return calendarDays; }
    /** Status chips of the Month View: { status, count } for each status the month has, in a fixed order. */
    public List<String[]> getStatusChips() {
        List<String[]> chips = new ArrayList<String[]>();
        for (String st : java.util.Arrays.asList("Approved", "Completed", "In Process", "Pending", "Rejected", "Cancelled")) {
            if (statusCounts.containsKey(st)) {
                chips.add(new String[] { st, String.valueOf(statusCounts.get(st)) });
            }
        }
        return chips;
    }
    /** Previous / next month of the Month View ({ month, year }), or null outside the years the picker offers. */
    public String[] getPrevMonth() { return shownMonth == null ? null : monthLink(shownMonth.minusMonths(1)); }
    public String[] getNextMonth() { return shownMonth == null ? null : monthLink(shownMonth.plusMonths(1)); }
    private String[] monthLink(java.time.YearMonth m) {
        return yearOptions.contains(m.getYear()) ? new String[] { String.valueOf(m.getMonthValue()), String.valueOf(m.getYear()) } : null;
    }
    /** True when the Month View shows the current month (the Today button is then not needed). */
    public boolean isCurrentMonthShown() { return java.time.YearMonth.from(CompOffConfig.today()).equals(shownMonth); }
    public String getTotalHours() { return totalHours; }
    public int getFullDays() { return fullDays; }
    public int getHalfDays() { return halfDays; }
    public String getCoStatus() { return coStatus; }
    public void setCoStatus(String coStatus) { this.coStatus = coStatus; }
    public String getMyTeamName() { return myTeamName; }
    public List<Map<String, Object>> getTeamStaff() { return teamStaff; }
    public List<TeamRow> getTeamRows() { return teamRows; }
    public List<CalendarDay> getMonthDays() { return monthDays; }
    public String getTeamReportTitle() { return teamReportTitle; }
    public List<String> getTeamStatuses() { return TEAM_STATUSES; }
    public List<Integer> getYearOptions() { return yearOptions; }
    public List<ApprovalManager.HeldRow> getHeldRows() { return heldRows; }
    public void setMode(String mode) { this.mode = mode; }
    public String[] getMonthNames() { return MONTHS.clone(); }
    public void setAp(String ap) { this.ap = ap; }

    public List<Map<String, Object>> getTeams() { return teams; }
    public List<Map<String, Object>> getStaffOptions() { return staffOptions; }
    public List<Map<String, Object>> getReportRows() { return reportRows; }
    public List<Map<String, Object>> getIntimationChoices() { return intimationChoices; }
    public List<Map<String, Object>> getHolidayHistoryRows() { return holidayHistoryRows; }
    public Integer getSelectedHwId() { return selectedHwId; }
    public boolean isReportRun() { return reportRun; }
    public String getSid() { return sid; }
    public void setSid(String sid) { this.sid = sid; }
    public Map<String, Object> getBalanceStaff() { return balanceStaff; }
    public java.io.InputStream getExcelStream() { return excelStream; }
    public String getExcelFileName() { return excelFileName; }
    public int getHistoryLimit() { return ReportManager.HISTORY_LIMIT; }

    public ApproverManager.Page getApproverPage() { return approverPage; }
    public List<GeneralSettingsManager.Setting> getGeneralSettings() { return generalSettings; }
    public List<Staffs> getHrApproverChoices() { return hrApproverChoices; }
    public String getParamName() { return paramName; }
    public void setParamName(String paramName) { this.paramName = paramName; }
    public String getHrApproverId() { return hrApproverId; }
    public void setHrApproverId(String hrApproverId) { this.hrApproverId = hrApproverId; }
    public String getEmployeeCodes() { return employeeCodes; }
    public void setEmployeeCodes(String employeeCodes) { this.employeeCodes = employeeCodes; }
    public String getCompOffDate() { return compOffDate; }
    public void setCompOffDate(String compOffDate) { this.compOffDate = compOffDate; }
    public List<CompOffManager.AdminPick> getAdminPicks() { return adminPicks; }
    public List<Map<String, Object>> getAdminApplied() { return adminApplied; }
    public List<Map<String, Object>> getCancelRows() { return cancelRows; }
    public List<GeneralSettingsManager.TeamChoice> getDesktopTeams() { return desktopTeams; }
    public List<GeneralSettingsManager.Flag> getDesktopFlags() { return desktopFlags; }
    public String getDesktopTeamIds() { return desktopTeamIds; }
    public void setDesktopTeam(String[] desktopTeam) { this.desktopTeam = desktopTeam; }
    public void setDesktopFlag(String[] desktopFlag) { this.desktopFlag = desktopFlag; }
    public String getHrActive() { return hrActive; }
    public void setHrActive(String hrActive) { this.hrActive = hrActive; }
    public int getMaxLevels() { return ApproverManager.MAX_LEVELS; }
    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public String getUserTypeId() { return userTypeId; }
    public void setUserTypeId(String userTypeId) { this.userTypeId = userTypeId; }
    public String getNoneOnly() { return noneOnly; }
    public void setNoneOnly(String noneOnly) { this.noneOnly = noneOnly; }
    public void setStaffIds(String staffIds) { this.staffIds = staffIds; }
    public void setApproverIds(String approverIds) { this.approverIds = approverIds; }
    public void setConfirmReplace(String confirmReplace) { this.confirmReplace = confirmReplace; }

    public Staffs getCurrentStaff() { return currentStaff; }
    public Staffs getApprover() { return approver; }
    public List<HolidayRequests> getIntimations() { return intimations; }
    public CompOffBalanceDetails getSummary() { return summary; }
    public List<HolidayRequests> getAvailableList() { return availableList; }
    public List<CompensatoryOffRequest> getCompOffRequests() { return compOffRequests; }
    public Map<Integer, List<CompOffBalanceDetailsHistory>> getHistoryByRequest() { return historyByRequest; }
    public Map<Integer, String> getStaffNames() { return staffNames; }
    public String getFlashMessage() { return flashMessage; }
    public String getFlashType() { return flashType; }
}
