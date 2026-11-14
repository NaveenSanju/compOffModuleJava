<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Day View'"/><s:set var="navKey" value="'dayView'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Day View</h2>
<p class="lead">Your Comp Off for one day, with each approver's decision.</p>

<div class="card">
  <form id="dayForm" method="get" novalidate action="<s:url action='compOffDayView1' namespace='/'/>">
    <div class="row period">
      <div class="f<s:if test="fieldErrors['day'] != null"> bad</s:if>" id="f_day">
        <label for="day_t">Date <span class="req">*</span></label>
        <div class="dpw">
          <input type="text" class="dpt" id="day_t" readonly placeholder="Select Date" autocomplete="off">
          <input type="hidden" id="day" name="day" value="<s:property value='day'/>">
        </div>
        <div class="err"><s:property value="fieldErrors['day'][0]"/></div>
      </div>
      <div class="f"><label>&nbsp;</label><button type="submit" class="p">Go</button><div class="err"></div></div>
    </div>
  </form>
</div>

<s:if test="dayLabel == null">
<div class="card"><div class="empty">Select a date, then click Go.</div></div>
</s:if>
<s:else>
<div class="card" id="dayResult">
  <div class="pagehead">
    <h3>Day View &ndash; <s:property value="dayLabel"/><s:if test="reportRequests.size() > 1"> (<s:property value="reportRequests.size()"/> requests)</s:if></h3>
    <a class="hlink" href="<s:url action='compOffMonthView1' namespace='/'><s:param name='month' value='month'/><s:param name='year' value='year'/></s:url>">Month View</a>
  </div>
  <s:if test="reportRequests.isEmpty()">
    <div class="empty">You have not applied comp off for <s:property value="dayLabel"/>.</div>
  </s:if>
  <s:iterator value="reportRequests" status="rq">
    <s:if test="reportRequests.size() > 1"><div class="reqno">Request <s:property value="#rq.count"/> of <s:property value="reportRequests.size()"/><s:if test="#rq.first"> &ndash; latest</s:if></div></s:if>
    <table class="kv">
      <tr><th>Date</th><td><s:date name="leaveDate" format="dd MMM yyyy"/></td></tr>
      <tr><th>Type</th><td>Compensatory off</td></tr>
      <tr><th>Time</th><td><s:property value="dayType"/> &nbsp;&middot;&nbsp; Total <s:property value="hours"/> Hrs</td></tr>
      <tr><th>Status</th><td><div class="stline">
        <span class="badge fixed b-<s:property value="displayStatus.replace(' ', '_')"/>"><s:property value="displayStatus"/></span>
        <s:if test="!approverSteps.isEmpty()"><div class="apsteps">
          <s:iterator value="approverSteps"><div><b><s:property value="top[0]"/></b> &ndash; <span class="aps aps-<s:property value="top[1]"/>"><s:property value="top[1]"/></span> &ndash; <s:if test="top[2] != null"><s:property value="top[2]"/></s:if><s:else><span class="mut">No Comments</span></s:else></div></s:iterator>
        </div></s:if>
      </div></td></tr>
    </table>
  </s:iterator>
</div>
</s:else>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
