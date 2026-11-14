package com.dao;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name="Staffs") 
public class Staffs  implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="staffID", unique=true, nullable=false, insertable=true, updatable=true)
	private int staffID;
	
	private int userID;
	
	private String firstName;
	
	private String lastName;
	
	private String displayName;	
		
	private String email;
	
	private int teamID;
	
	private String userType;
	
	private String designation;
				
	private String address1;
	
	private String address2;
	
	private String dailyAttenStatus;
	
	private String dailyHoursAttenStatus;
	
	private String dailyOfflineAttenStatus;
	
	private String city;
	
	private String state;	
		
	private String zippostCode;
	
	private String country;
	
	private String phoneNumber;
	
	private String mobileNumber;
	
	private String skillSet;
	
	private String projectExperience;
	
	private String hourlyRate;
	
	private String screenRecordMins;
	
	private String timeOutalertBreakMins;
			
	private String allowManualentries;
	
	private String timeOutalertLogoutMins;
	
	private String generateInvoices;
	
	private String timetoUse;
	
	private String timeZone;
	
	private String salaryCostValue;
	
	private String salaryCostType;
	
	private String fullscreenMode;
	
	private String timeFormatType;
	
	private String audioTrack;
	
	private String status;
			
	private int staffSignupID;
	
	private int invitedByID;
	
	private int companyID;
	
	@Temporal( TemporalType.TIMESTAMP)
	private Date createdDate;
	
	@Temporal( TemporalType.TIMESTAMP)
	private Date modifiedDate;

	private String multipleProjects;
	
	private String shiftTiming;
	
	private String loginUserID;
	
	private String userIDPWD;
	
	private int loginFlag;
	
	private String descriptionMandatory;
	
	private String disableDeskAppDays;
	
	private String flexiWorkHours;
	
	private int passwordResetDaysCount;
	
	private String password;
	
	private String staffBlockFlag;
	
	private String updateCheckFlag;
	
	private String addNotesInactiveScreen;
	
	private String dateformat;
	
	private String allowManualDuration;
	
	private String anyLocation;
	
	private int passwordResetDays;
	
	@Temporal( TemporalType.TIMESTAMP)
	private Date passwordResetdateAndtime;	
	
	private String employeeCode;
	
	@Temporal( TemporalType.DATE)
	private Date dateofJoining;
	
	private int userTypeID;
	
	private String sickLeaveFlag;
	
	private String casualLeaveFlag;
	
	public Staffs(){
		
	}
	
	public Staffs(int staffID,int staffSignupID,String firstName,String lastName,String shiftTiming){
		this.staffID = staffID;
		this.staffSignupID = staffSignupID;
		this.firstName = firstName;
		this.lastName = lastName;
		this.shiftTiming = shiftTiming;
	}
	
	public Date getModifiedDate() {
		return modifiedDate;
	}

	public void setModifiedDate(Date modifiedDate) {
		this.modifiedDate = modifiedDate;
	}

	public int getStaffID() {
		return staffID;
	}

	public void setStaffID(int staffID) {
		this.staffID = staffID;
	}

	public int getUserID() {
		return userID;
	}

	public void setUserID(int userID) {
		this.userID = userID;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	

	public String getUserType() {
		return userType;
	}

	public void setUserType(String userType) {
		this.userType = userType;
	}

	public String getDesignation() {
		return designation;
	}

	public void setDesignation(String designation) {
		this.designation = designation;
	}

	public String getAddress1() {
		return address1;
	}

	public void setAddress1(String address1) {
		this.address1 = address1;
	}

	public String getZippostCode() {
		return zippostCode;
	}

	public void setZippostCode(String zippostCode) {
		this.zippostCode = zippostCode;
	}

	
	public String getSkillSet() {
		return skillSet;
	}

	public void setSkillSet(String skillSet) {
		this.skillSet = skillSet;
	}

	public String getProjectExperience() {
		return projectExperience;
	}

	public void setProjectExperience(String projectExperience) {
		this.projectExperience = projectExperience;
	}

	public String getHourlyRate() {
		return hourlyRate;
	}

	public void setHourlyRate(String hourlyRate) {
		this.hourlyRate = hourlyRate;
	}

	public String getScreenRecordMins() {
		return screenRecordMins;
	}

	public void setScreenRecordMins(String screenRecordMins) {
		this.screenRecordMins = screenRecordMins;
	}

	public String getTimeOutalertBreakMins() {
		return timeOutalertBreakMins;
	}

	public void setTimeOutalertBreakMins(String timeOutalertBreakMins) {
		this.timeOutalertBreakMins = timeOutalertBreakMins;
	}

	public String getAllowManualentries() {
		return allowManualentries;
	}

	public void setAllowManualentries(String allowManualentries) {
		this.allowManualentries = allowManualentries;
	}

	public String getTimeOutalertLogoutMins() {
		return timeOutalertLogoutMins;
	}

	public void setTimeOutalertLogoutMins(String timeOutalertLogoutMins) {
		this.timeOutalertLogoutMins = timeOutalertLogoutMins;
	}

	public String getGenerateInvoices() {
		return generateInvoices;
	}

	public void setGenerateInvoices(String generateInvoices) {
		this.generateInvoices = generateInvoices;
	}

	public String getTimetoUse() {
		return timetoUse;
	}

	public void setTimetoUse(String timetoUse) {
		this.timetoUse = timetoUse;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	public String getFullscreenMode() {
		return fullscreenMode;
	}

	public void setFullscreenMode(String fullscreenMode) {
		this.fullscreenMode = fullscreenMode;
	}

	public String getSalaryCostValue() {
		return salaryCostValue;
	}

	public void setSalaryCostValue(String salaryCostValue) {
		this.salaryCostValue = salaryCostValue;
	}

	public String getSalaryCostType() {
		return salaryCostType;
	}

	public void setSalaryCostType(String salaryCostType) {
		this.salaryCostType = salaryCostType;
	}

	public Date getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(Date createdDate) {
		this.createdDate = createdDate;
	}

	public String getTimeFormatType() {
		return timeFormatType;
	}

	public void setTimeFormatType(String timeFormatType) {
		this.timeFormatType = timeFormatType;
	}

	public String getAudioTrack() {
		return audioTrack;
	}

	public void setAudioTrack(String audioTrack) {
		this.audioTrack = audioTrack;
	}		

	public int getInvitedByID() {
		return invitedByID;
	}

	public void setInvitedByID(int invitedByID) {
		this.invitedByID = invitedByID;
	}

	public int getStaffSignupID() {
		return staffSignupID;
	}

	public void setStaffSignupID(int staffSignupID) {
		this.staffSignupID = staffSignupID;
	}

	public int getCompanyID() {
		return companyID;
	}

	public void setCompanyID(int companyID) {
		this.companyID = companyID;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getMobileNumber() {
		return mobileNumber;
	}

	public void setMobileNumber(String mobileNumber) {
		this.mobileNumber = mobileNumber;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}
	
	public String getMultipleProjects() {
		return multipleProjects;
	}

	public void setMultipleProjects(String multipleProjects) {
		this.multipleProjects = multipleProjects;
	}

	public String getShiftTiming() {
		return shiftTiming;
	}

	public void setShiftTiming(String shiftTiming) {
		this.shiftTiming = shiftTiming;
	}
	
	public String getLoginUserID() {
		return loginUserID;
	}

	public void setLoginUserID(String loginUserID) {
		this.loginUserID = loginUserID;
	}

	public String getUserIDPWD() {
		return userIDPWD;
	}

	public void setUserIDPWD(String userIDPWD) {
		this.userIDPWD = userIDPWD;
	}

	public int getTeamID() {
		return teamID;
	}

	public void setTeamID(int teamID) {
		this.teamID = teamID;
	}

	public String getDisableDeskAppDays() {
		return disableDeskAppDays;
	}

	public void setDisableDeskAppDays(String disableDeskAppDays) {
		this.disableDeskAppDays = disableDeskAppDays;
	}

	public String getFlexiWorkHours() {
		return flexiWorkHours;
	}

	public void setFlexiWorkHours(String flexiWorkHours) {
		this.flexiWorkHours = flexiWorkHours;
	}

	public int getLoginFlag() {
		return loginFlag;
	}

	public void setLoginFlag(int loginFlag) {
		this.loginFlag = loginFlag;
	}

	public String getDescriptionMandatory() {
		return descriptionMandatory;
	}

	public void setDescriptionMandatory(String descriptionMandatory) {
		this.descriptionMandatory = descriptionMandatory;
	}

	public int getPasswordResetDaysCount() {
		return passwordResetDaysCount;
	}

	public void setPasswordResetDaysCount(int passwordResetDaysCount) {
		this.passwordResetDaysCount = passwordResetDaysCount;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getStaffBlockFlag() {
		return staffBlockFlag;
	}

	public void setStaffBlockFlag(String staffBlockFlag) {
		this.staffBlockFlag = staffBlockFlag;
	}

	public String getUpdateCheckFlag() {
		return updateCheckFlag;
	}

	public void setUpdateCheckFlag(String updateCheckFlag) {
		this.updateCheckFlag = updateCheckFlag;
	}
	
	public String getAddNotesInactiveScreen() {
		return addNotesInactiveScreen;
	}

	public void setAddNotesInactiveScreen(String addNotesInactiveScreen) {
		this.addNotesInactiveScreen = addNotesInactiveScreen;
	}

	public String getDateformat() {
		return dateformat;
	}

	public void setDateformat(String dateformat) {
		this.dateformat = dateformat;
	}

	public String getAllowManualDuration() {
		return allowManualDuration;
	}

	public void setAllowManualDuration(String allowManualDuration) {
		this.allowManualDuration = allowManualDuration;
	}

	public String getAnyLocation() {
		return anyLocation;
	}

	public void setAnyLocation(String anyLocation) {
		this.anyLocation = anyLocation;
	}

	public int getPasswordResetDays() {
		return passwordResetDays;
	}

	public void setPasswordResetDays(int passwordResetDays) {
		this.passwordResetDays = passwordResetDays;
	}

	public Date getPasswordResetdateAndtime() {
		return passwordResetdateAndtime;
	}

	public void setPasswordResetdateAndtime(Date passwordResetdateAndtime) {
		this.passwordResetdateAndtime = passwordResetdateAndtime;
	}

	public String getEmployeeCode() {
		return employeeCode;
	}

	public void setEmployeeCode(String employeeCode) {
		this.employeeCode = employeeCode;
	}

	public Date getDateofJoining() {
		return dateofJoining;
	}

	public void setDateofJoining(Date dateofJoining) {
		this.dateofJoining = dateofJoining;
	}
	public int getUserTypeID() {
		return userTypeID;
	}

	public void setUserTypeID(int userTypeID) {
		this.userTypeID = userTypeID;
	}
	public String getDailyAttenStatus() {
		return dailyAttenStatus;
	}

	public void setDailyAttenStatus(String dailyAttenStatus) {
		this.dailyAttenStatus = dailyAttenStatus;
	}

	public String getDailyHoursAttenStatus() {
		return dailyHoursAttenStatus;
	}

	public void setDailyHoursAttenStatus(String dailyHoursAttenStatus) {
		this.dailyHoursAttenStatus = dailyHoursAttenStatus;
	}

	public String getDailyOfflineAttenStatus() {
		return dailyOfflineAttenStatus;
	}

	public void setDailyOfflineAttenStatus(String dailyOfflineAttenStatus) {
		this.dailyOfflineAttenStatus = dailyOfflineAttenStatus;
	}

	public String getAddress2() {
		return address2;
	}

	public void setAddress2(String address2) {
		this.address2 = address2;
	}

	public String getSickLeaveFlag() {
		return sickLeaveFlag;
	}

	public void setSickLeaveFlag(String sickLeaveFlag) {
		this.sickLeaveFlag = sickLeaveFlag;
	}

	public String getCasualLeaveFlag() {
		return casualLeaveFlag;
	}

	public void setCasualLeaveFlag(String casualLeaveFlag) {
		this.casualLeaveFlag = casualLeaveFlag;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	
    public String getStaffName() {
        String f = firstName == null ? "" : firstName.trim(), l = lastName == null ? "" : lastName.trim();
        return f.isEmpty() ? l : l.isEmpty() ? f : f + " " + l;
    }
    
    public String getAccessRole() { return com.util.CompOffConfig.accessRole(userType); }
    
    public boolean isHr() { return userType != null && "HR".equalsIgnoreCase(userType.trim()); }
		
}
