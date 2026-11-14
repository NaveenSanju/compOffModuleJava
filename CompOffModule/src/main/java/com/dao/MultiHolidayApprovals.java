package com.dao;

import java.util.Date;
import javax.persistence.*;

/**
 * One request the approver approved, waiting for the approval servlet (MultiHolidayApprovals table).
 * All rows created by one click share batchID (approver code + click time).
 */
@Entity
@Table(name = "MultiHolidayApprovals")
public class MultiHolidayApprovals {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer MHAID;

    private String batchID;

    /** The approval level that was approved (position in the LeaveSettingsCompOff chain, or the HR level). */
    private Integer approvalLevel;

    private Integer HWID;

    private Integer approverID;

    private String approverStatus;

    private String notes;

    /** 'Approve' (null on rows written before the column existed) or 'Reject'. */
    private String approvalAction;

    @Temporal(TemporalType.TIMESTAMP)
    private Date approvedDate;

    @Temporal(TemporalType.TIMESTAMP)
    private Date processedDate;

    private String status;

    @Column(insertable = false, updatable = false)
    private Date createdDate;

    @Column(insertable = false, updatable = false)
    private Date updatedDate;

    public Integer getMHAID() { return MHAID; }
    public void setMHAID(Integer MHAID) { this.MHAID = MHAID; }

    public String getBatchID() { return batchID; }
    public void setBatchID(String batchID) { this.batchID = batchID; }


    public Integer getApprovalLevel() { return approvalLevel; }
    public void setApprovalLevel(Integer approvalLevel) { this.approvalLevel = approvalLevel; }

    public Integer getHWID() { return HWID; }
    public void setHWID(Integer HWID) { this.HWID = HWID; }

    public Integer getApproverID() { return approverID; }
    public void setApproverID(Integer approverID) { this.approverID = approverID; }

    public String getApproverStatus() { return approverStatus; }
    public void setApproverStatus(String approverStatus) { this.approverStatus = approverStatus; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getApprovalAction() { return approvalAction; }
    public void setApprovalAction(String approvalAction) { this.approvalAction = approvalAction; }
    public boolean isReject() { return com.dao.CompOffConstants.ACTION_REJECT.equals(approvalAction); }

    public Date getApprovedDate() { return approvedDate; }
    public void setApprovedDate(Date approvedDate) { this.approvedDate = approvedDate; }

    public Date getProcessedDate() { return processedDate; }
    public void setProcessedDate(Date processedDate) { this.processedDate = processedDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedDate() { return createdDate; }
    public Date getUpdatedDate() { return updatedDate; }
}
