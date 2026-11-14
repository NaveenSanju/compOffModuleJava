package com.dao;

import java.util.Date;
import javax.persistence.*;

/** Leave type (LeaveType table): 'Compensatory OFF' and 'Holiday Intimation', leaveDayType FD (full day). */
@Entity
@Table(name = "LeaveType")
public class LeaveType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer leaveTypeID;

    private Integer companyID;

    private Date createdDate;

    private String leaveTypeName;

    private Date modifiedDate;

    private String status;

    private Integer userID;

    private String duration;

    private String leaveDayType;

    private String leaveDaysRestrictFlag;

    public Integer getLeaveTypeID() { return leaveTypeID; }
    public void setLeaveTypeID(Integer leaveTypeID) { this.leaveTypeID = leaveTypeID; }

    public Integer getCompanyID() { return companyID; }
    public void setCompanyID(Integer companyID) { this.companyID = companyID; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public String getLeaveTypeName() { return leaveTypeName; }
    public void setLeaveTypeName(String leaveTypeName) { this.leaveTypeName = leaveTypeName; }

    public Date getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(Date modifiedDate) { this.modifiedDate = modifiedDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getUserID() { return userID; }
    public void setUserID(Integer userID) { this.userID = userID; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getLeaveDayType() { return leaveDayType; }
    public void setLeaveDayType(String leaveDayType) { this.leaveDayType = leaveDayType; }

    public String getLeaveDaysRestrictFlag() { return leaveDaysRestrictFlag; }
    public void setLeaveDaysRestrictFlag(String leaveDaysRestrictFlag) { this.leaveDaysRestrictFlag = leaveDaysRestrictFlag; }
}
