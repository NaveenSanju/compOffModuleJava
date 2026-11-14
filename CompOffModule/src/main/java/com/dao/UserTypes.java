package com.dao;

import java.util.Date;
import javax.persistence.*;

/** Designation (UserTypes table): userType is the code (TL, S, MR, PM, QC, HR), userTypeName the name. Staffs.userTypeID refers to it. */
@Entity
@Table(name = "UserTypes")
public class UserTypes {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer userTypeID;

    private Integer companyID;

    private Date createdDate;

    private Date modifiedDate;

    private String status;

    private String userType;

    private String userTypeName;

    private String userTypeCreatedBy;

    public Integer getUserTypeID() { return userTypeID; }
    public void setUserTypeID(Integer userTypeID) { this.userTypeID = userTypeID; }

    public Integer getCompanyID() { return companyID; }
    public void setCompanyID(Integer companyID) { this.companyID = companyID; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public Date getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(Date modifiedDate) { this.modifiedDate = modifiedDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getUserTypeName() { return userTypeName; }
    public void setUserTypeName(String userTypeName) { this.userTypeName = userTypeName; }

    public String getUserTypeCreatedBy() { return userTypeCreatedBy; }
    public void setUserTypeCreatedBy(String userTypeCreatedBy) { this.userTypeCreatedBy = userTypeCreatedBy; }
}
