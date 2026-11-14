package com.dao;

import java.math.BigDecimal;
import java.util.Date;
import javax.persistence.*;

/**
 * One Comp Off leave day requested by a staff member (CompensatoryOffRequest table, COID).
 *   leaveStatus     : Pending / Approved / Rejected / Cancelled / Completed
 *   leaveApproverID : the approver it is waiting with now, '<userType>~~<staffID>' (e.g. 'TL~~5'); its level comes
 *                     from the LeaveSettingsCompOff chain (or the HR step)
 *   referenceID     : HWRID of the Holiday Intimation whose day this leave uses; attendanceDate = that worked date
 *   keyID           : 'CO-<COID>' (as in CompOffBalanceDetailsHistory)
 *   leaveDate       : the Comp Off date (one row per selected date)
 *   status          : 'A' / 'I' (records are never deleted)
 */
@Entity
@Table(name = "CompensatoryOffRequest")
public class CompensatoryOffRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer COID;

    private Integer companyID;

    private Date createdDate;

    private String leaveApproverID;

    private String leaveDayType;

    private String leaveDuration;

    private String leaveNotes;

    private String leaveStatus;

    private Integer leaveTypeID;

    private Date modifiedDate;

    private Integer staffID;

    private String status;

    private Integer userID;

    private String keyID;

    @Temporal(TemporalType.DATE)
    private Date leaveDate;

    private String referenceID;

    @Temporal(TemporalType.DATE)
    private Date attendanceDate;

    private String shiftType;

    /** Set for display: "In Process" while approvals have started but the request is not final yet. */
    @Transient
    private String displayStatus;

    /** Set for display: whether the staff member may still cancel it. */
    @Transient
    private boolean cancellable;

    /** Set for My Comp Off Report: one { approver name, Approved / Rejected / Pending, notes or null } per approval step. */
    @Transient
    private java.util.List<String[]> approverSteps = new java.util.ArrayList<String[]>();

    /** Set for history rows: the approval level of the current approver. */
    @Transient
    private Integer currentApprovalLevel;

    /** HWRID of the intimation this leave uses (referenceID), or null. */
    public Integer getHolidayWorkingId() {
        return referenceID != null && referenceID.trim().matches("[0-9]{1,9}") ? Integer.valueOf(referenceID.trim()) : null;
    }
    public void setHolidayWorkingId(Integer holidayWorkingId) {
        this.referenceID = holidayWorkingId == null ? null : String.valueOf(holidayWorkingId);
    }

    /** staffID of the approver it is waiting with (leaveApproverID 'TL~~5' -> 5), or null. */
    public Integer getApproverId() {
        if (leaveApproverID == null) {
            return null;
        }
        int sep = leaveApproverID.indexOf("~~");
        String id = (sep < 0 ? leaveApproverID : leaveApproverID.substring(sep + 2)).trim();
        return id.matches("[0-9]{1,9}") ? Integer.valueOf(id) : null;
    }

    /** 'FD' is shown as "Full day", 'HD' as "Half day". */
    public String getDayType() {
        return CompOffConstants.LEAVE_DAY_FD.equals(leaveDayType) ? CompOffConstants.FULL_DAY
                : "HD".equals(leaveDayType) ? CompOffConstants.HALF_DAY : leaveDayType;
    }

    /** leaveDuration as a number of days (1.0 when not readable). */
    public BigDecimal getDuration() {
        try {
            return leaveDuration == null ? CompOffConstants.FULL_DAY_DURATION : new BigDecimal(leaveDuration.trim()).setScale(1);
        } catch (NumberFormatException e) {
            return CompOffConstants.FULL_DAY_DURATION;
        }
    }

    /** The Comp Off date of this row (one row per date). */
    public void setLeaveDate(Date leaveDate) { this.leaveDate = leaveDate; }

    public String getDisplayStatus() { return displayStatus == null ? leaveStatus : displayStatus; }
    public void setDisplayStatus(String displayStatus) { this.displayStatus = displayStatus; }

    public java.util.List<String[]> getApproverSteps() { return approverSteps; }

    /** Hours of the leave as "08:00" (duration in days x compoff.hours.fullday). */
    public String getHours() {
        return hoursText(getDuration());
    }
    /** Days as hours, "08:00" / "04:00" / "40:00". */
    public static String hoursText(BigDecimal days) {
        long minutes = days.multiply(BigDecimal.valueOf(com.util.CompOffConfig.getFullDayHours() * 60L)).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
    /** Approved, Completed, In Process and Pending days count in the report totals; Rejected and Cancelled ones do not. */
    public boolean isTaken() {
        String s = getDisplayStatus();
        return !CompOffConstants.REQ_REJECTED.equals(s) && !CompOffConstants.REQ_CANCELLED.equals(s);
    }

    public boolean isCancellable() { return cancellable; }
    public void setCancellable(boolean cancellable) { this.cancellable = cancellable; }

    public Integer getCurrentApprovalLevel() { return currentApprovalLevel; }
    public void setCurrentApprovalLevel(Integer currentApprovalLevel) { this.currentApprovalLevel = currentApprovalLevel; }

    public Integer getCOID() { return COID; }
    public void setCOID(Integer COID) { this.COID = COID; }

    public Integer getCompanyID() { return companyID; }
    public void setCompanyID(Integer companyID) { this.companyID = companyID; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public String getLeaveApproverID() { return leaveApproverID; }
    public void setLeaveApproverID(String leaveApproverID) { this.leaveApproverID = leaveApproverID; }

    public String getLeaveDayType() { return leaveDayType; }
    public void setLeaveDayType(String leaveDayType) { this.leaveDayType = leaveDayType; }

    public String getLeaveDuration() { return leaveDuration; }
    public void setLeaveDuration(String leaveDuration) { this.leaveDuration = leaveDuration; }

    /** leaveNotes as stored: "Applying Comp off for <leave date> for Holiday worked on <worked date>~~<staff notes>". */
    public String getLeaveNotes() { return leaveNotes; }
    public void setLeaveNotes(String leaveNotes) { this.leaveNotes = leaveNotes; }

    public String getLeaveStatus() { return leaveStatus; }
    public void setLeaveStatus(String leaveStatus) { this.leaveStatus = leaveStatus; }

    public Integer getLeaveTypeID() { return leaveTypeID; }
    public void setLeaveTypeID(Integer leaveTypeID) { this.leaveTypeID = leaveTypeID; }

    public Date getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(Date modifiedDate) { this.modifiedDate = modifiedDate; }

    public Integer getStaffID() { return staffID; }
    public void setStaffID(Integer staffID) { this.staffID = staffID; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getUserID() { return userID; }
    public void setUserID(Integer userID) { this.userID = userID; }

    public String getKeyID() { return keyID; }
    public void setKeyID(String keyID) { this.keyID = keyID; }

    public Date getLeaveDate() { return leaveDate; }

    public String getReferenceID() { return referenceID; }
    public void setReferenceID(String referenceID) { this.referenceID = referenceID; }

    public Date getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(Date attendanceDate) { this.attendanceDate = attendanceDate; }

    public String getShiftType() { return shiftType; }
    public void setShiftType(String shiftType) { this.shiftType = shiftType; }
}
