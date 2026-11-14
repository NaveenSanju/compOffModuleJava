package com.dao;

import java.util.Date;
import javax.persistence.*;

/** Team master (Teams table); Staffs.teamID refers to it. */
@Entity
@Table(name = "Teams")
public class Teams {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer teamID;

    private String teamName;

    private String description;

    private String status;

    /** The team's company (team lists show the teams of the user's company). */
    private Integer companyID;

    @Column(insertable = false, updatable = false)
    private Date createdDate;

    @Column(insertable = false, updatable = false)
    private Date updatedDate;

    public Integer getTeamID() { return teamID; }
    public void setTeamID(Integer teamID) { this.teamID = teamID; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getCompanyID() { return companyID; }
    public void setCompanyID(Integer companyID) { this.companyID = companyID; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedDate() { return createdDate; }
    public Date getUpdatedDate() { return updatedDate; }
}
