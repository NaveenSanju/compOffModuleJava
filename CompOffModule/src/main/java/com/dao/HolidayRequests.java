package com.dao;

import java.util.Date;
import javax.persistence.*;

/**
 * One row per Holiday Intimation (HolidayRequests table, HWRID).
 *   holidayWorkingStatus : the day's lifecycle - Pending / Available / Used / Completed / Rejected / Cancelled
 *   holidayApproveStatus : the approval        - Pending / Approved / Rejected / Cancelled
 *   approverID           : the approver it is waiting with now (its level comes from the LeaveSettingsCompOff chain)
 *   recordStatus         : 'A' / 'I' (records are never deleted)
 * The Comp Off request that used the day is the CompensatoryOffRequest row whose referenceID is this HWRID.
 */
@Entity
@Table(name = "HolidayRequests")
public class HolidayRequests {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer HWRID;

    private Date createdDate;

    private Date modifiedDate;

    private Integer companyID;

    private String recordStatus;

    private Integer staffID;

    @Temporal(TemporalType.DATE)
    private Date workRequestDate;

    /** The staff member's reason. */
    private String notes;

    private String shiftType;

    private String staffName;

    private String employeeCode;

    private String holidayWorkingStatus;

    private String holidayApproveStatus;

    private Integer approverID;

    /** Set for display: "In Process" while approvals have started but the intimation is not final yet. */
    @Transient
    private String displayStatus;

    /** Set for display: what happened to the day, e.g. "Comp Off applied for: 20 Oct 2026". */
    @Transient
    private String approverNotesText;

    /** Set for display (My Holiday Intimations): each approver and their decision, e.g. {"Rahul Mehta", "Approved"}. */
    @Transient
    private java.util.List<String[]> approverSteps = new java.util.ArrayList<String[]>();

    /** Set for display: the notes approvers gave with their decision, e.g. {"Rahul Mehta", "Good work"}. */
    @Transient
    private java.util.List<String[]> approverNotes = new java.util.ArrayList<String[]>();

    /** Set for history rows: the approval level of approverId (LeaveSettingsCompOff chain position, or the HR level). */
    @Transient
    private Integer currentApprovalLevel;

    public String getDisplayStatus() { return displayStatus == null ? holidayWorkingStatus : displayStatus; }
    public void setDisplayStatus(String displayStatus) { this.displayStatus = displayStatus; }

    /** Pending or Available intimations can be cancelled; Used ones need the Comp Off cancelled first. */
    public boolean isCancellable() {
        return CompOffConstants.HW_PENDING.equals(holidayWorkingStatus)
                || CompOffConstants.HW_AVAILABLE.equals(holidayWorkingStatus);
    }

    public Integer getHWRID() { return HWRID; }
    public void setHWRID(Integer HWRID) { this.HWRID = HWRID; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public Date getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(Date modifiedDate) { this.modifiedDate = modifiedDate; }

    public Integer getCompanyID() { return companyID; }
    public void setCompanyID(Integer companyID) { this.companyID = companyID; }

    public String getRecordStatus() { return recordStatus; }
    public void setRecordStatus(String recordStatus) { this.recordStatus = recordStatus; }

    public Integer getStaffID() { return staffID; }
    public void setStaffID(Integer staffID) { this.staffID = staffID; }

    public Date getWorkRequestDate() { return workRequestDate; }
    public void setWorkRequestDate(Date workRequestDate) { this.workRequestDate = workRequestDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getShiftType() { return shiftType; }
    public void setShiftType(String shiftType) { this.shiftType = shiftType; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getHolidayWorkingStatus() { return holidayWorkingStatus; }
    public void setHolidayWorkingStatus(String holidayWorkingStatus) { this.holidayWorkingStatus = holidayWorkingStatus; }

    public String getHolidayApproveStatus() { return holidayApproveStatus; }
    public void setHolidayApproveStatus(String holidayApproveStatus) { this.holidayApproveStatus = holidayApproveStatus; }

    public Integer getApproverID() { return approverID; }
    public void setApproverID(Integer approverID) { this.approverID = approverID; }

    public java.util.List<String[]> getApproverSteps() { return approverSteps; }
    public java.util.List<String[]> getApproverNotes() { return approverNotes; }

    public String getApproverNotesText() { return approverNotesText; }
    public void setApproverNotesText(String approverNotesText) { this.approverNotesText = approverNotesText; }

    public Integer getCurrentApprovalLevel() { return currentApprovalLevel; }
    public void setCurrentApprovalLevel(Integer currentApprovalLevel) { this.currentApprovalLevel = currentApprovalLevel; }
}
