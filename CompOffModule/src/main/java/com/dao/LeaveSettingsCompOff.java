package com.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.persistence.*;

/**
 * Approver chain of one staff member for one leave type (LeaveSettingsCompOff table).
 * leaveApproverID lists the approvers in level order as '<userType>~~<staffID>', comma separated,
 * e.g. 'PM~~6, TL~~5' = level 1 staffID 6, level 2 staffID 5.
 */
@Entity
@Table(name = "LeaveSettingsCompOff")
public class LeaveSettingsCompOff {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer LSID;

    private Integer companyID;

    private Date createdDate;

    private String leaveAccuEnds;

    private String leaveAccuPeriod;

    private String leaveAccuStarts;

    private String leaveApproverID;

    private String leaveApproverResp;

    private String leaveCarriedOver;

    private String leaveCarriedOverPeriod;

    private String leaveDaysPerMonth;

    private Date modifiedDate;

    private Integer staffID;

    private String status;

    private Integer teamID;

    private Integer userID;

    private Integer leaveTypeID;

    private String leaveTypeName;

    private String otherTeamApprover;

    private Integer teamOfApprover;

    /**
     * Approver StaffIds in level order (index 0 = level 1), read from leaveApproverID.
     * Entries that are not '<code>~~<number>' are skipped.
     */
    public List<Integer> approverIds() {
        if (leaveApproverID == null || leaveApproverID.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> ids = new ArrayList<Integer>();
        for (String entry : leaveApproverID.split(",")) {
            int sep = entry.indexOf("~~");
            String id = (sep < 0 ? entry : entry.substring(sep + 2)).trim();
            if (id.matches("[0-9]{1,9}")) {
                ids.add(Integer.valueOf(id));
            }
        }
        return ids;
    }

    public Integer getLSID() { return LSID; }
    public void setLSID(Integer LSID) { this.LSID = LSID; }

    public Integer getCompanyID() { return companyID; }
    public void setCompanyID(Integer companyID) { this.companyID = companyID; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public String getLeaveAccuEnds() { return leaveAccuEnds; }
    public void setLeaveAccuEnds(String leaveAccuEnds) { this.leaveAccuEnds = leaveAccuEnds; }

    public String getLeaveAccuPeriod() { return leaveAccuPeriod; }
    public void setLeaveAccuPeriod(String leaveAccuPeriod) { this.leaveAccuPeriod = leaveAccuPeriod; }

    public String getLeaveAccuStarts() { return leaveAccuStarts; }
    public void setLeaveAccuStarts(String leaveAccuStarts) { this.leaveAccuStarts = leaveAccuStarts; }

    public String getLeaveApproverID() { return leaveApproverID; }
    public void setLeaveApproverID(String leaveApproverID) { this.leaveApproverID = leaveApproverID; }

    public String getLeaveApproverResp() { return leaveApproverResp; }
    public void setLeaveApproverResp(String leaveApproverResp) { this.leaveApproverResp = leaveApproverResp; }

    public String getLeaveCarriedOver() { return leaveCarriedOver; }
    public void setLeaveCarriedOver(String leaveCarriedOver) { this.leaveCarriedOver = leaveCarriedOver; }

    public String getLeaveCarriedOverPeriod() { return leaveCarriedOverPeriod; }
    public void setLeaveCarriedOverPeriod(String leaveCarriedOverPeriod) { this.leaveCarriedOverPeriod = leaveCarriedOverPeriod; }

    public String getLeaveDaysPerMonth() { return leaveDaysPerMonth; }
    public void setLeaveDaysPerMonth(String leaveDaysPerMonth) { this.leaveDaysPerMonth = leaveDaysPerMonth; }

    public Date getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(Date modifiedDate) { this.modifiedDate = modifiedDate; }

    public Integer getStaffID() { return staffID; }
    public void setStaffID(Integer staffID) { this.staffID = staffID; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTeamID() { return teamID; }
    public void setTeamID(Integer teamID) { this.teamID = teamID; }

    public Integer getUserID() { return userID; }
    public void setUserID(Integer userID) { this.userID = userID; }

    public Integer getLeaveTypeID() { return leaveTypeID; }
    public void setLeaveTypeID(Integer leaveTypeID) { this.leaveTypeID = leaveTypeID; }

    public String getLeaveTypeName() { return leaveTypeName; }
    public void setLeaveTypeName(String leaveTypeName) { this.leaveTypeName = leaveTypeName; }

    public String getOtherTeamApprover() { return otherTeamApprover; }
    public void setOtherTeamApprover(String otherTeamApprover) { this.otherTeamApprover = otherTeamApprover; }

    public Integer getTeamOfApprover() { return teamOfApprover; }
    public void setTeamOfApprover(Integer teamOfApprover) { this.teamOfApprover = teamOfApprover; }
}
