<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Comp Off - Month View'"/><s:set var="navKey" value="'teamMonthView'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Comp Off - Month View</h2>
<p class="lead">Comp Off days of your team, day by day.</p>
<s:if test="hasActionErrors()"><div class="hint warn"><s:property value="actionErrors[0]"/></div></s:if>
<s:else>
<div class="card">
  <form id="teamForm" method="get" novalidate action="<s:url action='teamCompOffMonthView1' namespace='/'/>">
    <%-- first visit: the current month and year are selected --%>
    <s:if test="month == null && year == null"><s:set var="selMonth" value="today.substring(5, 7).replaceFirst('^0', '')"/><s:set var="selYear" value="today.substring(0, 4)"/></s:if>
    <s:else><s:set var="selMonth" value="month"/><s:set var="selYear" value="year"/></s:else>
    <s:set var="selStaff" value="staffFilter == null ? 'all' : staffFilter"/><s:set var="selStatus" value="coStatus"/>
    <div class="tfil">
      <div class="f<s:if test="fieldErrors['teamId'] != null"> bad</s:if>" id="f_teamId">
        <label for="teamId">Team <span class="req">*</span></label>
        <select id="teamId" name="teamId"><option value="<s:property value='currentStaff.teamID'/>" selected><s:property value="myTeamName"/></option></select>
        <div class="err"><s:property value="fieldErrors['teamId'][0]"/></div>
      </div>
      <div class="f<s:if test="fieldErrors['staffFilter'] != null"> bad</s:if>" id="f_staffFilter">
        <label for="staffFilter">Staff <span class="req">*</span></label>
        <select id="staffFilter" name="staffFilter">
          <option value="all"<s:if test="#selStaff == 'all'"> selected</s:if>>All staff</option>
          <s:iterator value="teamStaff">
            <option value="<s:property value="top['staffId']"/>"<s:if test="top['staffId'].toString() == #selStaff"> selected</s:if>><s:property value="top['staffName']"/> (<s:property value="top['employeeCode']"/>)</option>
          </s:iterator>
        </select>
        <div class="err"><s:property value="fieldErrors['staffFilter'][0]"/></div>
      </div>
      <div class="f<s:if test="fieldErrors['coStatus'] != null"> bad</s:if>" id="f_coStatus">
        <label for="coStatus">Status</label>
        <select id="coStatus" name="coStatus">
          <option value="">All</option>
          <s:iterator value="teamStatuses"><option value="<s:property/>"<s:if test="top == #selStatus"> selected</s:if>><s:property/></option></s:iterator>
        </select>
        <div class="err"><s:property value="fieldErrors['coStatus'][0]"/></div>
      </div>
      <div class="f<s:if test="fieldErrors['month'] != null"> bad</s:if>" id="f_month">
        <label for="month">Month <span class="req">*</span></label>
        <select id="month" name="month">
          <option value="">Select month</option>
          <s:iterator value="monthNames" status="st"><option value="<s:property value='#st.count'/>"<s:if test="#st.count.toString() == #selMonth"> selected</s:if>><s:property/></option></s:iterator>
        </select>
        <div class="err"><s:property value="fieldErrors['month'][0]"/></div>
      </div>
      <div class="f<s:if test="fieldErrors['year'] != null"> bad</s:if>" id="f_year">
        <label for="year">Year <span class="req">*</span></label>
        <select id="year" name="year">
          <option value="">Select year</option>
          <s:iterator value="yearOptions"><option value="<s:property/>"<s:if test="top.toString() == #selYear"> selected</s:if>><s:property/></option></s:iterator>
        </select>
        <div class="err"><s:property value="fieldErrors['year'][0]"/></div>
      </div>
      <div class="f"><label>&nbsp;</label><button type="submit" class="p">Go</button><div class="err"></div></div>
    </div>
  </form>
</div>

<s:if test="teamReportTitle == null">
<div class="card"><div class="empty">Select the staff, status, month and year, then click Go.</div></div>
</s:if>
<s:else>
<div class="card" id="teamReport">
  <div class="rtitle"><s:property value="teamReportTitle"/></div>
  <s:if test="teamRows.isEmpty()"><div class="empty">No staff in your team.</div></s:if>
  <s:else>
  <div class="scroll"><table class="tgrid" id="teamGrid">
    <thead>
      <tr><th class="nm" rowspan="2">Name</th>
        <s:iterator value="monthDays"><th class="<s:if test="weekend">we</s:if><s:if test="today"> td</s:if>"><s:property value="dayOfMonth"/></th></s:iterator>
        <th class="tot" rowspan="2">Total</th></tr>
      <tr class="wd"><s:iterator value="monthDays"><th<s:if test="weekend"> class="we"</s:if>><s:property value="weekdayLetter"/></th></s:iterator></tr>
    </thead>
    <tbody>
    <s:iterator value="teamRows">
      <tr><td class="nm" title="<s:property value='name'/>"><s:property value="name"/></td>
        <s:iterator value="cells" status="c">
          <s:if test="marked">
            <td class="mk s-<s:property value="status.replace(' ', '_')"/>" tabindex="0"><s:property value="mark"/><template class="tipdata"><s:iterator value="requests" status="r"><div class="tipreq<s:if test="!#r.first"> more</s:if>">
              <div><b>Leave date:</b> <s:property value="top[0]"/></div>
              <div><b>Leave duration:</b> <s:property value="top[1]"/></div>
              <div><b>Status:</b> <span class="badge b-<s:property value="top[2].replace(' ', '_')"/>"><s:property value="top[2]"/></span></div>
            </div></s:iterator></template></td>
          </s:if>
          <s:else><td<s:if test="monthDays[#c.index].weekend"> class="we"</s:if>></td></s:else>
        </s:iterator>
        <td class="tot"><s:property value="total"/></td></tr>
    </s:iterator>
    </tbody>
  </table></div>
  <div class="legend">
    <span><i class="s-Approved">1d</i>Approved</span><span><i class="s-Completed">1d</i>Completed</span><span><i class="s-In_Process">1d</i>In Process</span>
    <span><i class="s-Pending">1d</i>Pending</span><span><i class="s-Rejected">1d</i>Rejected</span><span><i class="s-Cancelled">1d</i>Cancelled</span>
  </div>
  </s:else>
</div>
</s:else>
</s:else>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
