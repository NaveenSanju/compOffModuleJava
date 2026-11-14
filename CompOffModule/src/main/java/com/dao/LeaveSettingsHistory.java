package com.dao;

import java.util.Date;
import javax.persistence.*;

/**
 * One approver change of one staff member, made on the Approver Management page (LeaveSettingsHistory table).
 * The chain itself is in LeaveSettingsCompOff; this row records who changed it, when, why, and the chain before and after
 * ('PM~~6, TL~~5' format). Rows of one "Assign approvers to selected" share batchID.
 */
@Entity
@Table(name = "LeaveSettingsHistory")
public class LeaveSettingsHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer LSHID;

    private Integer companyID;

    private Integer staffID;

    /** Assigned (no approvers before) or Changed. */
    private String historyAction;

    private String oldLeaveApproverID;

    private String newLeaveApproverID;

    /** Other Active LeaveSettingsCompOff rows of the staff member set to 'I', comma separated (normally none). */
    private String oldLSIDs;

    /** The LeaveSettingsCompOff row written (updated in place, or created when the staff member had none). */
    private String newLSIDs;

    private String batchID;

    private String reason;

    /** staffID of the Admin who made the change. */
    private Integer changedBy;

    private Date createdDate;

    private String status;

    public Integer getLSHID() { return LSHID; }
    public void setLSHID(Integer LSHID) { this.LSHID = LSHID; }

    public Integer getCompanyID() { return companyID; }
    public void setCompanyID(Integer companyID) { this.companyID = companyID; }

    public Integer getStaffID() { return staffID; }
    public void setStaffID(Integer staffID) { this.staffID = staffID; }

    public String getHistoryAction() { return historyAction; }
    public void setHistoryAction(String historyAction) { this.historyAction = historyAction; }

    public String getOldLeaveApproverID() { return oldLeaveApproverID; }
    public void setOldLeaveApproverID(String oldLeaveApproverID) { this.oldLeaveApproverID = oldLeaveApproverID; }

    public String getNewLeaveApproverID() { return newLeaveApproverID; }
    public void setNewLeaveApproverID(String newLeaveApproverID) { this.newLeaveApproverID = newLeaveApproverID; }

    public String getOldLSIDs() { return oldLSIDs; }
    public void setOldLSIDs(String oldLSIDs) { this.oldLSIDs = oldLSIDs; }

    public String getNewLSIDs() { return newLSIDs; }
    public void setNewLSIDs(String newLSIDs) { this.newLSIDs = newLSIDs; }

    public String getBatchID() { return batchID; }
    public void setBatchID(String batchID) { this.batchID = batchID; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getChangedBy() { return changedBy; }
    public void setChangedBy(Integer changedBy) { this.changedBy = changedBy; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
