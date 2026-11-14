package com.action;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.hibernate.Session;

import com.dao.Staffs;
import com.serviceimpl.HibernateUtil;
import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ActionSupport;


public class LoginAction extends ActionSupport
implements ServletRequestAware, ServletResponseAware {

private static final long serialVersionUID = 1L;
private HttpServletRequest request;
private HttpServletResponse response;
private HibernateUtil dao = new HibernateUtil();
private String userName;
private String password;

Map session;
	


public String login() {
	System.out.println("DFFFFFFFFFFFFFFFFfffff");
	 
return SUCCESS;
}
public String loginAuth() {
	System.out.println("Check 1");
	this.session = ActionContext.getContext().getSession();
	System.out.println(userName+"====userName");
	System.out.println(password+"====password");
	  try {
	  Staffs rows = (Staffs)dao.getObjectWithQuery("from Staffs where email='"+userName+"' and userIDPWD='"+password+"'");
	 
	  
	  if (rows !=null){
		  session.put("userName", rows.getFirstName()+" "+rows.getLastName());
	        session.put("staffID", rows.getStaffID());
	        session.put("companyID", String.valueOf(rows.getCompanyID()));
	        session.put("userType", rows.getUserType());
		  return "loginsuccess";
	  }
	
	  }catch (Exception e) {
		// TODO: handle exception
	     e.printStackTrace();
	}
	 
return ERROR;
}

public String logout() {
		  try {
	            this.session = ActionContext.getContext().getSession(); 
	            if (!session.isEmpty()){
	                request.getSession().invalidate();
	                session.clear();           
	            }
	            } catch (Exception e) {
	                //System.err.println("Exception=====>"+e);
	            }

	        return SUCCESS;
	   }



@Override public void setServletRequest(HttpServletRequest request) { this.request = request; }
@Override public void setServletResponse(HttpServletResponse response) { this.response = response; }


public HttpServletRequest getRequest() {
	return request;
}


public void setRequest(HttpServletRequest request) {
	this.request = request;
}


public HttpServletResponse getResponse() {
	return response;
}


public void setResponse(HttpServletResponse response) {
	this.response = response;
}


public String getUserName() {
	return userName;
}


public void setUserName(String userName) {
	this.userName = userName;
}


public String getPassword() {
	return password;
}


public void setPassword(String password) {
	this.password = password;
}

}
