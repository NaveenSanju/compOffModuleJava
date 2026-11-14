package com.dao;

import java.util.Date;
import javax.persistence.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * One Holiday Intimation or Comp Off event (CompOffBalanceDetailsHistory table, which replaced
 * HolidayWorkingHistory and CompOffHistory).
 *   keyID 'HW-<HWID>' or 'CO-<COID>'; HRID / COID = those ids (0 = none);
 *   availableDays / usedDays / balanceDays = the balance after the event;
 *   leaveHistoryActionCommands = JSON {"actorId", "from", "to", "level", "prev":"A/U/B", "remarks"} (read by the application);
 *   historyActionComments = the same event as a readable sentence (dates yyyy-MM-dd, time in GMT).
 */
@Entity
@Table(name = "CompOffBalanceDetailsHistory")
public class CompOffBalanceDetailsHistory {
    private static final Gson GSON = new Gson();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer COHID;

    private Date createdDate;

    private String keyID;

    private Integer HRID;

    private Integer COID;

    @Temporal(TemporalType.DATE)
    private Date compOffLeaveDate;

    private Integer companyID;

    private String duration;

    private String historyAction;

    @Temporal(TemporalType.DATE)
    private Date holidayWorkedDate;

    @Temporal(TemporalType.DATE)
    private Date modifiedFromDate;

    @Temporal(TemporalType.DATE)
    private Date modifiedToDate;

    private String leaveType;

    private String leaveTypeVal;

    private String partialDayTime;

    private String referenceID;

    private Integer staffID;

    private String status;

    private Integer timesheetsID;

    private Integer availableDays;

    private Integer usedDays;

    private Integer balanceDays;

    private String leaveHistoryActionCommands;

    private String historyActionComments;

    @Transient
    private JsonObject details;

    // ---- values kept in leaveHistoryActionCommands

    /** Builds the leaveHistoryActionCommands JSON; null values are left out. */
    public static String commands(Integer actorId, String from, String to, Integer level, String prev, String remarks) {
        JsonObject o = new JsonObject();
        o.addProperty("actorId", actorId);
        o.addProperty("from", from);
        o.addProperty("to", to);
        o.addProperty("level", level);
        o.addProperty("prev", prev);
        o.addProperty("remarks", remarks);
        return GSON.toJson(o);
    }

    private JsonObject details() {
        if (details == null) {
            try {
                details = leaveHistoryActionCommands == null ? null : GSON.fromJson(leaveHistoryActionCommands, JsonObject.class);
            } catch (JsonParseException e) {
                details = null;
            }
            if (details == null) {
                details = new JsonObject();
            }
        }
        return details;
    }

    private String text(String key) {
        JsonObject o = details();
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    /** staffID of the person who made the change. */
    public Integer getActorId() {
        String v = text("actorId");
        return v == null ? null : Integer.valueOf(v);
    }
    public String getFromStatus() { return text("from"); }
    public String getToStatus() { return text("to"); }
    public String getRemarks() { return text("remarks"); }
    /** Balance before the event, "Allocated / Used / Balance" (null when not recorded). */
    public String getPrevBalance() {
        String prev = text("prev");
        return prev == null ? null : prev.replace("/", " / ");
    }
    /** Balance after the event, "Allocated / Used / Balance". */
    public String getNewBalance() { return availableDays + " / " + usedDays + " / " + balanceDays; }

    // ---- columns

    public Integer getCOHID() { return COHID; }
    public void setCOHID(Integer COHID) { this.COHID = COHID; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public String getKeyID() { return keyID; }
    public void setKeyID(String keyID) { this.keyID = keyID; }

    public Integer getHRID() { return HRID; }
    public void setHRID(Integer HRID) { this.HRID = HRID; }

    public Integer getCOID() { return COID; }
    public void setCOID(Integer COID) { this.COID = COID; }

    public Date getCompOffLeaveDate() { return compOffLeaveDate; }
    public void setCompOffLeaveDate(Date compOffLeaveDate) { this.compOffLeaveDate = compOffLeaveDate; }

    public Integer getCompanyID() { return companyID; }
    public void setCompanyID(Integer companyID) { this.companyID = companyID; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getHistoryAction() { return historyAction; }
    public void setHistoryAction(String historyAction) { this.historyAction = historyAction; }

    public Date getHolidayWorkedDate() { return holidayWorkedDate; }
    public void setHolidayWorkedDate(Date holidayWorkedDate) { this.holidayWorkedDate = holidayWorkedDate; }

    public Date getModifiedFromDate() { return modifiedFromDate; }
    public void setModifiedFromDate(Date modifiedFromDate) { this.modifiedFromDate = modifiedFromDate; }

    public Date getModifiedToDate() { return modifiedToDate; }
    public void setModifiedToDate(Date modifiedToDate) { this.modifiedToDate = modifiedToDate; }

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }

    public String getLeaveTypeVal() { return leaveTypeVal; }
    public void setLeaveTypeVal(String leaveTypeVal) { this.leaveTypeVal = leaveTypeVal; }

    public String getPartialDayTime() { return partialDayTime; }
    public void setPartialDayTime(String partialDayTime) { this.partialDayTime = partialDayTime; }

    public String getReferenceID() { return referenceID; }
    public void setReferenceID(String referenceID) { this.referenceID = referenceID; }

    public Integer getStaffID() { return staffID; }
    public void setStaffID(Integer staffID) { this.staffID = staffID; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTimesheetsID() { return timesheetsID; }
    public void setTimesheetsID(Integer timesheetsID) { this.timesheetsID = timesheetsID; }

    public Integer getAvailableDays() { return availableDays; }
    public void setAvailableDays(Integer availableDays) { this.availableDays = availableDays; }

    public Integer getUsedDays() { return usedDays; }
    public void setUsedDays(Integer usedDays) { this.usedDays = usedDays; }

    public Integer getBalanceDays() { return balanceDays; }
    public void setBalanceDays(Integer balanceDays) { this.balanceDays = balanceDays; }

    public String getLeaveHistoryActionCommands() { return leaveHistoryActionCommands; }
    public String getHistoryActionComments() { return historyActionComments; }
    public void setHistoryActionComments(String historyActionComments) { this.historyActionComments = historyActionComments; }

    public void setLeaveHistoryActionCommands(String leaveHistoryActionCommands) {
        this.leaveHistoryActionCommands = leaveHistoryActionCommands;
        this.details = null;
    }
}
