package com.dao;

import java.util.Date;
import javax.persistence.*;

/**
 * One company setting (GeneralSettings table): paramName / paramValue, Active ('A') or Inactive ('I').
 * Used for the additional HR approver of each flow: paramName 'holidayApprovalHR' / 'compOffApprovalHR',
 * paramValue '<userType>~~<staffID>' (e.g. 'HR~~100'), staffID = that approver. The newest Active row of a
 * paramName is the one in use; older rows are kept (Status 'I', modifiedDate) as its history.
 */
@Entity
@Table(name = "GeneralSettings")
public class GeneralSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer GSID;

    private Integer companyID;

    private Date createdDate;

    private Date modifiedDate;

    private String paramName;

    private String paramValue;

    private String status;

    /** 0 = all teams. */
    private Integer teamID;

    /** Same as companyID. */
    private Integer userID;

    private Integer staffID;

    /** The staffID in paramValue ('HR~~100' -> 100), or null when it cannot be read. */
    public Integer valueStaffId() {
        if (paramValue == null) {
            return null;
        }
        int sep = paramValue.indexOf("~~");
        String id = (sep < 0 ? paramValue : paramValue.substring(sep + 2)).trim();
        return id.matches("[0-9]{1,9}") ? Integer.valueOf(id) : null;
    }

    /** staffID of a well-formed HR value ('HR~~100'); null for anything else (the HR step then goes to the Admin). */
    public Integer hrStaffId() {
        return paramValue != null && paramValue.trim().matches("HR~~[0-9]{1,9}")
                ? Integer.valueOf(paramValue.trim().substring(4)) : null;
    }

    public boolean isActive() { return CompOffConstants.ACTIVE.equals(status); }

    public Integer getGSID() { return GSID; }
    public void setGSID(Integer GSID) { this.GSID = GSID; }

    public Integer getCompanyID() { return companyID; }
    public void setCompanyID(Integer companyID) { this.companyID = companyID; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public Date getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(Date modifiedDate) { this.modifiedDate = modifiedDate; }

    public String getParamName() { return paramName; }
    public void setParamName(String paramName) { this.paramName = paramName; }

    public String getParamValue() { return paramValue; }
    public void setParamValue(String paramValue) { this.paramValue = paramValue; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTeamID() { return teamID; }
    public void setTeamID(Integer teamID) { this.teamID = teamID; }

    public Integer getUserID() { return userID; }
    public void setUserID(Integer userID) { this.userID = userID; }

    public Integer getStaffID() { return staffID; }
    public void setStaffID(Integer staffID) { this.staffID = staffID; }
}
