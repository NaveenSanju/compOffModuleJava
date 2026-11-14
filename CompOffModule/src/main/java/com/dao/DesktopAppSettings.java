package com.dao;

import javax.persistence.*;

/**
 * One setting shared with the desktop application (DesktopAppSettings table): paramName / paramValue.
 * Used by the Comp Off module: compOffTeamIDs (comma separated Teams.teamID list) and the four Y / N flags
 * compoffRequestAllowFlag, compoffRequestApproveFlag, holidayRequestAllowFlag and holidayApprovalAllowFlag.
 * When a paramName has several rows, the one with the highest DSID is used.
 */
@Entity
@Table(name = "DesktopAppSettings")
public class DesktopAppSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer DSID;

    private String paramName;

    private String paramValue;

    public Integer getDSID() { return DSID; }
    public void setDSID(Integer DSID) { this.DSID = DSID; }

    public String getParamName() { return paramName; }
    public void setParamName(String paramName) { this.paramName = paramName; }

    public String getParamValue() { return paramValue; }
    public void setParamValue(String paramValue) { this.paramValue = paramValue; }
}
