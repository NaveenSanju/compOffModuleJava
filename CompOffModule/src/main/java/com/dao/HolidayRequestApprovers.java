package com.dao;

import java.util.Date;
import javax.persistence.*;

/**
 * One approval step of a Holiday Intimation (HolidayRequestApprovers table): created when the step starts (Pending) and closed with the
 * decision (Approved / Rejected / Cancelled). approverType is the approver's user type code (TL, PM, MR, HR, ...).
 * The application writes these rows with conditional SQL (ApproverSteps); this class maps the table for reading.
 */
@Entity
@Table(name = "HolidayRequestApprovers")
public class HolidayRequestApprovers {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer HRAID;

    /** The Holiday Intimation (HolidayRequests.HWRID). */
    private Integer HWID;

    /** The requester (Staffs.staffID). */
    private Integer staffID;

    /** 1 = first level of the chain; the HR step comes after the last chain level. */
    private Integer approvalLevel;

    private String approverType;

    private Integer approverID;

    /** Pending / Approved / Rejected / Cancelled */
    private String approverStatus;

    /** The approver's notes for the decision. */
    private String notes;

    @Temporal(TemporalType.TIMESTAMP)
    private Date actionDate;

    /** 'A' or 'I' (rows are never deleted) */
    private String status;

    @Column(insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    @Column(insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedDate;

    public Integer getHRAID() { return HRAID; }
    public void setHRAID(Integer HRAID) { this.HRAID = HRAID; }

    public Integer getHWID() { return HWID; }
    public void setHWID(Integer HWID) { this.HWID = HWID; }

    public Integer getStaffID() { return staffID; }
    public void setStaffID(Integer staffID) { this.staffID = staffID; }

    public Integer getApprovalLevel() { return approvalLevel; }
    public void setApprovalLevel(Integer approvalLevel) { this.approvalLevel = approvalLevel; }

    public String getApproverType() { return approverType; }
    public void setApproverType(String approverType) { this.approverType = approverType; }

    public Integer getApproverID() { return approverID; }
    public void setApproverID(Integer approverID) { this.approverID = approverID; }

    public String getApproverStatus() { return approverStatus; }
    public void setApproverStatus(String approverStatus) { this.approverStatus = approverStatus; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Date getActionDate() { return actionDate; }
    public void setActionDate(Date actionDate) { this.actionDate = actionDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedDate() { return createdDate; }
    public Date getUpdatedDate() { return updatedDate; }
}
