<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Month View'"/><s:set var="navKey" value="'monthView'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Month View</h2>
<p class="lead">Your Comp Off days in one month. Click a day to open its Day View.</p>

<s:set var="periodAction" value="'compOffMonthView1'"/><s:set var="monthLabel" value="'Month'"/>
<s:set var="periodHintText" value="'Select the month and year, then click Go.'"/>
<%@ include file="layout/periodPicker.jspf" %>
<s:if test="periodLabel != null">
<div class="card" id="monthCard">
  <div class="calhead">
    <div class="calnav">
      <s:if test="prevMonth != null"><a class="navb" id="prevMonth" title="Previous month" href="<s:url action='compOffMonthView1' namespace='/'><s:param name='month' value='prevMonth[0]'/><s:param name='year' value='prevMonth[1]'/></s:url>">&lsaquo;</a></s:if>
      <s:else><span class="navb off">&lsaquo;</span></s:else>
      <h3>Month View &ndash; <s:property value="periodLabel"/></h3>
      <s:if test="nextMonth != null"><a class="navb" id="nextMonth" title="Next month" href="<s:url action='compOffMonthView1' namespace='/'><s:param name='month' value='nextMonth[0]'/><s:param name='year' value='nextMonth[1]'/></s:url>">&rsaquo;</a></s:if>
      <s:else><span class="navb off">&rsaquo;</span></s:else>
      <s:if test="!currentMonthShown"><a class="todayb" id="todayMonth" href="<s:url action='compOffMonthView1' namespace='/'/>">Today</a></s:if>
    </div>
    <div class="chips" id="statusChips">
      <s:iterator value="statusChips"><span class="chip s-<s:property value="top[0].replace(' ', '_')"/>"><s:property value="top[0]"/> <b><s:property value="top[1]"/></b></span>
      </s:iterator>
      <span class="chip tot">Total <s:property value="totalHours"/> Hrs</span>
    </div>
  </div>
  <div class="cal2" id="monthCal">
    <div class="wdh we">SUN</div><div class="wdh">MON</div><div class="wdh">TUE</div><div class="wdh">WED</div><div class="wdh">THU</div><div class="wdh">FRI</div><div class="wdh we">SAT</div>
    <s:iterator value="calendarDays">
      <s:url var="dayUrl" action="compOffDayView1" namespace="/"><s:param name="day" value="iso"/></s:url>
      <div class="dc<s:if test="outside"> out</s:if><s:if test="weekend"> we</s:if><s:if test="today && !outside"> today</s:if><s:if test="!requests.isEmpty()"> has</s:if>"
           <s:if test="!requests.isEmpty()"> data-href="<s:property value='#dayUrl'/>" data-day="<s:property value='iso'/>" tabindex="0" role="link" title="Open Day View for <s:property value='label'/>"</s:if>>
        <div class="dn"><span><s:property value="dayOfMonth"/></span><s:if test="today && !outside"> <i>Today</i></s:if></div>
        <s:iterator value="requests"><s:set var="st" value="displayStatus"/>
          <div class="ev s-<s:property value="#st.replace(' ', '_')"/><s:if test="#st == 'Rejected' || #st == 'Cancelled'"> x</s:if>">
            <div class="st"><s:property value="#st"/></div>
            <div class="ty">Compensatory OFF</div>
            <div class="hr"><s:property value="dayType"/> &middot; <s:property value="hours"/> Hrs</div>
          </div>
        </s:iterator>
      </div>
    </s:iterator>
  </div>
</div>

<div class="card">
  <h3>Comp Off &ndash; <s:property value="periodLabel"/> (<s:property value="reportRequests.size()"/>)</h3>
  <s:if test="reportRequests.isEmpty()"><div class="empty">You have not applied comp off in <s:property value="periodLabel"/>.</div></s:if>
  <s:else>
  <div class="scroll"><table class="mtab" id="monthTable">
    <thead><tr><th>Date</th><th>Type</th><th>Duration</th><th>Hours</th><th class="c">Status</th></tr></thead>
    <tbody>
    <s:iterator value="reportRequests">
      <s:url var="dayUrl" action="compOffDayView1" namespace="/"><s:param name="day"><s:date name="leaveDate" format="yyyy-MM-dd"/></s:param></s:url>
      <tr<s:if test="!taken"> class="off"</s:if>>
        <td><a class="hlink" href="<s:property value='#dayUrl'/>"><s:date name="leaveDate" format="dd MMM yyyy"/></a></td>
        <td>Compensatory off</td>
        <td><s:property value="dayType"/></td>
        <td><s:property value="hours"/> Hrs</td>
        <td class="c"><span class="badge fixed b-<s:property value="displayStatus.replace(' ', '_')"/>"><s:property value="displayStatus"/></span></td>
      </tr>
    </s:iterator>
    </tbody>
    <tfoot><tr>
      <th colspan="3">Total Hours <span class="mut">(Approved, Completed, In Process and Pending)</span></th>
      <th><s:property value="totalHours"/> Hrs</th>
      <th class="c">Full day: <s:property value="fullDays"/> &nbsp;&middot;&nbsp; Half day: <s:property value="halfDays"/></th>
    </tr></tfoot>
  </table></div>
  </s:else>
</div>
</s:if>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
