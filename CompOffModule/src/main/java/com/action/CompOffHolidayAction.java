package com.action;

import java.time.LocalDate;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import com.dao.CompOffConstants;
import com.dao.Staffs;
import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionSupport;
import com.util.CompOffConfig;

@SuppressWarnings("deprecation")
	public class CompOffHolidayAction extends ActionSupport implements ServletRequestAware, ServletResponseAware{
	 
		Map session;	
		private HttpServletRequest request;
		private HttpServletResponse response;
		private final static Logger LOGGER = Logger.getLogger(LoginAction.class.getName());
		//HibernateUtil hiberDAO = new HibernateUtil();
		
	    public String holidayIntimation() {
//	        if (!loadCommon()) {
//	            return LOGIN;
//	        }
	       // loadHolidayIntimationPage();
	    	if (checkSession().equalsIgnoreCase("true")) {
	    		return SUCCESS;
	    	}
	        return "sessionOut";
	    }

	    /** CREATE: submit a holiday intimation to the level-1 approver. */
	    public String submitHolidayIntimation() {
//	        if (!isPost()) {
//	            return redirectTo(null, null);
//	        }
//	        if (!loadCommon()) {
//	            return LOGIN;
//	        }

//	        LocalDate today = CompOffConfig.today();
//	        LocalDate worked = requiredDate("workedDate", workedDate, "Worked date");
//	        if (worked != null && worked.isAfter(today)) {
//	            addFieldError("workedDate", "Worked date cannot be a future date.");
//	        }
//	        String shift = requiredChoice("shiftType", shiftType, "Shift type", CompOffConstants.SHIFT_TYPES);
//	        String reasonText = requiredText("reason", reason, "Notes", "Notes are required.");
//
//	        if (!hasFieldErrors()) {
//	            try {
//	                Notifier mail = notifier();
//	                Staffs sentTo = manager.submitHolidayIntimation(currentStaff.getStaffID(), worked, shift, reasonText, mail);
//	                mail.flush();
//	                return redirectTo("Your Holiday Intimation request has been submitted to " + sentTo.getEmail()
//	                        + ". Status: Pending.", "ok");
//	            } catch (CompOffException e) {
//	                addBusinessError(e);
//	            } catch (RuntimeException e) {
//	                addActionError(unexpected("submit holiday intimation", e));
//	            }
//	        }
//	        loadHolidayIntimationPage();
//	        return INPUT;
	        return SUCCESS;
	    }

	    /** READ: intimations with a Cancel button for Pending / Available ones. */
	    public String cancelHolidayIntimation() {
//	        if (!loadCommon()) {
//	            return LOGIN;
//	        }
//	        intimations = manager.intimations(currentStaff.getStaffID());
	        return SUCCESS;
	    }

	    /** CANCEL: cancel a Pending or Available intimation (status change, nothing is deleted). */
	    public String doCancelHolidayIntimation() {
//	        if (!isPost()) {
//	            return redirectTo(null, null);
//	        }
//	        if (!loadCommon()) {
//	            return LOGIN;
//	        }
//	        Integer id = requiredId(holidayWorkingId, "Holiday intimation");
//	        String why = requiredText("cancelReason", cancelReason, "Cancel Notes", "Cancel Notes are required.");
//	        if (hasFieldErrors() || hasActionErrors()) {
//	            return redirectTo(firstError(), "er");
//	        }
//	        try {
//	            Notifier mail = notifier();
//	            manager.cancelHolidayIntimation(currentStaff.getStaffID(), id, why, mail);
//	            mail.flush();
//	            return redirectTo("Holiday Intimation cancelled.", "ok");
//	        } catch (CompOffException e) {
//	            return redirectTo(e.getMessage(), "er");
//	        } catch (RuntimeException e) {
//	            return redirectTo(unexpected("cancel holiday intimation", e), "er");
//	        }
	    	return SUCCESS;
	    }
	    
	    private void loadHolidayIntimationPage() {
//	        intimations = manager.intimations(currentStaff.getStaffID());
//	        approver = manager.holidayIntimationApprover(currentStaff.getStaffID());
	    }

	    
	    public String checkSession() {
	    	this.session = ActionContext.getContext().getSession();				
	    	if (session.isEmpty()) {
	    		return "false";
	    	} else {		    			
	    		return "true";
	    	}
	    }

		@Override
		public void setServletRequest(HttpServletRequest request) {
			this.setRequest(request);
		}
	 
		@Override
		public void setServletResponse(HttpServletResponse response) {
			this.response = response;
		}
	 
		public void setRequest(HttpServletRequest request) {
			this.request = request;
		}
	 
		public HttpServletRequest getRequest() {
			return request;
		}
	}
	
