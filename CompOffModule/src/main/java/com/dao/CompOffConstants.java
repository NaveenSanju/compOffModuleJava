package com.dao;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Allowed values of the ENUM columns used by the Comp Off module (must match db/comp_off_schema.sql). */
public final class CompOffConstants {

    private CompOffConstants() {}

    /** Record status (Status CHAR(1)) present on every table. Records are never deleted, only set to INACTIVE. */
    public static final String ACTIVE = "A";
    public static final String INACTIVE = "I";

    /** Access levels (Staffs.getAccessRole(), from the user type code Staffs.userType via compoff.usertype.*) */
    public static final String ROLE_EMPLOYEE = "Employee";
    public static final String ROLE_APPROVER = "Approver";
    public static final String ROLE_ADMIN = "Admin";

    /** Shift type of a Comp Off applied by the Admin (Apply Comp Off for Staff: Full day, Day Shift). */
    public static final String ADMIN_SHIFT = "Day Shift";
    public static final List<String> SHIFT_TYPES =
            Collections.unmodifiableList(Arrays.asList("Day Shift", "Night Shift", "Other Shift"));

    public static final String FULL_DAY = "Full day";
    public static final String HALF_DAY = "Half day";
    /** Day types offered on Apply Comp Off. Half day (0.5) is supported by the schema but not enabled yet. */
    public static final List<String> DAY_TYPES = Collections.unmodifiableList(Arrays.asList(FULL_DAY));
    public static final BigDecimal FULL_DAY_DURATION = new BigDecimal("1.0");

    /** HolidayRequests.HolidayWorkingStatus */
    public static final String HW_PENDING = "Pending";
    public static final String HW_REJECTED = "Rejected";
    public static final String HW_AVAILABLE = "Available";
    public static final String HW_USED = "Used";
    public static final String HW_COMPLETED = "Completed";
    public static final String HW_CANCELLED = "Cancelled";

    /** HolidayRequests.holidayApproveStatus (Pending / Approved / Rejected / Cancelled) */
    public static final String APPROVAL_PENDING = "Pending";
    public static final String APPROVAL_CANCELLED = "Cancelled";

    /** Shown to users while approvals have started but the request is not final ("In Process"). */
    public static final String IN_PROCESS = "In Process";

    /** MultiHolidayApprovals / MultiCompOffApprovals.approverStatus */
    public static final String STAGED_IN_PROGRESS = "In_Progress";
    /** MultiHolidayApprovals / MultiCompOffApprovals.approvalAction: the decision the approval servlet carries out. */
    public static final String ACTION_APPROVE = "Approve";
    public static final String ACTION_REJECT = "Reject";

    /** CompensatoryOffRequest.leaveStatus */
    public static final String REQ_PENDING = "Pending";
    public static final String REQ_APPROVED = "Approved";
    public static final String REQ_CANCELLED = "Cancelled";
    public static final String REQ_REJECTED = "Rejected";
    /** Set by the approval servlet once the leave date of an Approved request has passed. */
    public static final String REQ_COMPLETED = "Completed";

    /** CompOffBalanceDetailsHistory.historyAction (both flows; leaveType tells them apart). */
    public static final String HIST_SUBMITTED = "Submitted";
    public static final String HIST_APPROVED = "Approved";
    public static final String HIST_REJECTED = "Rejected";
    public static final String HIST_ALLOCATED = "Allocated";
    public static final String HIST_REQUESTED = "Requested";
    public static final String HIST_CANCELLED = "Cancelled";
    public static final String HIST_COMPLETED = "Completed";

    /** LeaveType.leaveTypeName of each flow: selects the LeaveSettingsCompOff approver chain; CompOffBalanceDetailsHistory.leaveType. */
    public static final String LT_HOLIDAY = "Holiday Intimation";
    public static final String LT_COMP_OFF = "Compensatory OFF";
    /** LeaveType.leaveDayType / CompOffBalanceDetailsHistory.leaveTypeVal: full day. */
    public static final String LEAVE_DAY_FD = "FD";
}
