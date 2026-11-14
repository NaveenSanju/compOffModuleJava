package com.action;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.dao.CompOffConstants;
import com.dao.Staffs;
import com.util.CompanyContext;
import com.util.SessionKeys;

/**
 * Which Comp Off cards and links the logged-in user gets on the Leave page (layout/leaveLinks.jsp). Works on any page
 * of the application, also one not run by the Comp Off module: it reads the session (staffID, companyID, userType) and
 * the Staffs row itself, with the same rules as CompOffAction.access (access level from the session's userType,
 * DesktopAppSettings, HR named in General Settings).
 */
public final class CompOffLinks {

    private final boolean employee, team, holidayApproval, compOffApproval, admin;
    private final boolean holidayRequestOn, compOffRequestOn, holidayApprovalOn, compOffApprovalOn;

    private CompOffLinks(String role, ModuleAccess access) {
        admin = CompOffConstants.ROLE_ADMIN.equals(role);
        employee = !admin && access.isSelf();
        team = CompOffConstants.ROLE_APPROVER.equals(role) && access.isTeam();
        holidayApproval = !CompOffConstants.ROLE_EMPLOYEE.equals(role) && access.isHolidayApprover();
        compOffApproval = !CompOffConstants.ROLE_EMPLOYEE.equals(role) && access.isCompOffApprover();
        holidayRequestOn = access.isHolidayRequestOn();
        compOffRequestOn = access.isCompOffRequestOn();
        holidayApprovalOn = access.isHolidayApprovalOn();
        compOffApprovalOn = access.isCompOffApprovalOn();
    }

    /**
     * The links of the logged-in user, or null when nobody is logged in, the user has no active Staffs row, or the
     * session's user type has no Comp Off access (then no card is shown).
     */
    public static CompOffLinks of(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        int staffId = SessionKeys.number(session.getAttribute(SessionKeys.STAFF_ID));
        int company = SessionKeys.number(session.getAttribute(SessionKeys.COMPANY_ID));
        Object type = session.getAttribute(SessionKeys.USER_TYPE);
        String role = com.util.CompOffConfig.accessRole(type == null ? null : String.valueOf(type).trim());
        if (staffId <= 0 || company <= 0 || role == null) {
            return null;
        }
        Integer before = CompanyContext.get();
        CompanyContext.set(company);
        try {
            Staffs staff = new CompOffManager().findStaff(staffId);
            return staff == null ? null : new CompOffLinks(role, ModuleAccess.of(staff));
        } finally {
            if (before == null) {
                CompanyContext.clear();
            } else {
                CompanyContext.set(before);
            }
        }
    }

    /** Staff pages (Holiday Intimation, Comp Off, My Comp Off Report). */
    public boolean isEmployee() { return employee; }
    /** My Team Report (Team Leader / Manager). */
    public boolean isTeam() { return team; }
    /** Holiday Intimation approvals card. */
    public boolean isHolidayApproval() { return holidayApproval; }
    /** Comp Off approvals card. */
    public boolean isCompOffApproval() { return compOffApproval; }
    /** Admin cards (reports, settings). */
    public boolean isAdmin() { return admin; }
    /** A link of a card switched off by its DesktopAppSettings flag is shown grey and not clickable. */
    public boolean isHolidayRequestOn() { return holidayRequestOn; }
    public boolean isCompOffRequestOn() { return compOffRequestOn; }
    public boolean isHolidayApprovalOn() { return holidayApprovalOn; }
    public boolean isCompOffApprovalOn() { return compOffApprovalOn; }
    /** No card at all (team not in compOffTeamIDs, HR not named in General Settings ...). */
    public boolean isNothing() { return !admin && !employee && !team && !holidayApproval && !compOffApproval; }
}
