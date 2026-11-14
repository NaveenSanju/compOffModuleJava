<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Holiday Intimation Usage Report'"/><s:set var="navKey" value="'holidayUsageReport'"/><s:set var="wideMain" value="true"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Holiday Intimation Usage Report</h2>
<p class="lead">Approved Holiday Intimations of each staff member and the Comp Off applied against them.</p>

<div class="card">
  <h3>Holiday Intimation Usage Report</h3>
  <form id="usageForm" method="get" novalidate action="<s:url action='holidayUsageReport1' namespace='/'/>">
    <input type="hidden" name="run" value="1">
    <div class="row stack">
      <s:set var="selTeam" value="teamId"/><s:set var="selStaff" value="staffFilter"/>
      <div class="f<s:if test="fieldErrors['teamId'] != null"> bad</s:if>" id="f_teamId">
        <label for="teamId">Select team</label>
        <select id="teamId" name="teamId">
          <option value="">All teams</option>
          <s:iterator value="teams">
            <option value="<s:property value="top['teamId']"/>"<s:if test="top['teamId'].toString() == #selTeam"> selected</s:if>><s:property value="top['teamName']"/></option>
          </s:iterator>
        </select>
        <div class="err"><s:property value="fieldErrors['teamId'][0]"/></div>
      </div>
      <div class="f<s:if test="fieldErrors['staffFilter'] != null"> bad</s:if>" id="f_staffFilter">
        <label for="staffFilter">Select staff</label>
        <select id="staffFilter" name="staffFilter">
          <option value="">All staff</option>
          <s:iterator value="staffOptions">
            <option value="<s:property value="top['staffId']"/>" data-team="<s:property value="top['teamId']"/>"<s:if test="top['staffId'].toString() == #selStaff"> selected</s:if>><s:property value="top['staffName']"/> (<s:property value="top['employeeCode']"/>)</option>
          </s:iterator>
        </select>
        <div class="err"><s:property value="fieldErrors['staffFilter'][0]"/></div>
      </div>
    </div>
    <div class="btns">
      <button type="submit" class="p">Go</button>
      <a class="btn plain" href="<s:url action='holidayUsageReport1' namespace='/'/>">Reset</a>
    </div>
  </form>
</div>

<div class="card" id="res">
  <div class="rhead">
    <h3>Report<s:if test="reportRun"> (<s:property value="reportRows.size()"/> record<s:if test="reportRows.size() != 1">s</s:if>)</s:if></h3>
    <s:if test="reportRun">
      <s:url var="excelUrl" action="holidayUsageExcel1" namespace="/" escapeAmp="false"><s:param name="teamId" value="teamId"/><s:param name="staffFilter" value="staffFilter"/></s:url>
      <a class="btn xl" id="usageExcel" href="<s:property value='#excelUrl'/>">Export to Excel</a>
    </s:if>
    <s:else><button type="button" class="xl" id="usageExcel" disabled title="Click Go to run the report first">Export to Excel</button></s:else>
  </div>
  <div class="scroll"><table id="usageTable" class="colfilter" data-noun="records">
    <thead><tr><th data-filter="list">Team name</th><th data-filter="text">Staff name</th><th data-filter="text">Employee code</th><th data-filter="date">Holiday worked date</th><th data-filter="list">Usage status</th><th data-filter="date">Comp Off date</th><th data-filter="date">Comp Off requested on</th><th data-filter="list">Comp Off status</th></tr></thead>
    <tbody>
    <s:iterator value="reportRows">
      <tr>
        <td><s:if test="top['teamName'] != null"><s:property value="top['teamName']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:property value="top['staffName']"/></td>
        <td><s:property value="top['employeeCode']"/></td>
        <td><s:date name="top['workedDate']" format="dd MMM yyyy"/></td>
        <td><span class="badge b-<s:property value="top['usage']"/>"><s:property value="top['usage']"/></span></td>
        <s:if test="top['compOffId'] != null">
        <td><s:date name="top['leaveDate']" format="dd MMM yyyy"/> (<s:property value="top['dayType']"/>)</td>
        <td><s:date name="top['requestedDate']" format="dd MMM yyyy"/></td>
        <td><span class="badge b-<s:property value="top['compOffStatus'].replace(' ', '_')"/>"><s:property value="top['compOffStatus']"/></span></td>
        </s:if>
        <s:else><td>&mdash;</td><td>&mdash;</td><td><span class="note">Not used yet</span></td></s:else>
      </tr>
    </s:iterator>
    <s:if test="reportRows.isEmpty()"><tr class="nofilt"><td colspan="8" class="empty">No record found</td></tr></s:if>
    </tbody>
  </table></div>
</div>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
