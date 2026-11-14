<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Staff Comp Off Details'"/><s:set var="navKey" value="'compOffBalanceReport'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Comp Off Balance and Details Report</h2>
<p class="lead">Holiday Intimation and Comp Off details of one staff member.</p>
<div class="backrow"><a href="<s:url action='compOffBalanceReport1' namespace='/'/>" id="balanceBack">&larr; Back to Comp Off Balance and Details</a></div>

<s:if test="balanceStaff != null">
<div class="card" id="staffSummary">
  <h3><s:property value="balanceStaff['staffName']"/> (<s:property value="balanceStaff['employeeCode']"/>)</h3>
  <div class="sumgrid">
    <div><b>Team Name</b><s:if test="balanceStaff['teamName'] != null"><s:property value="balanceStaff['teamName']"/></s:if><s:else>&mdash;</s:else></div>
    <div><b>Date of Joining (DOJ)</b><s:if test="balanceStaff['dateOfJoining'] != null"><s:date name="balanceStaff['dateOfJoining']" format="dd MMM yyyy"/></s:if><s:else>&mdash;</s:else></div>
    <div><b>Employee Code</b><s:property value="balanceStaff['employeeCode']"/></div>
    <div></div>
    <div><b>Holiday Worked Days</b><span class="big"><s:property value="days(balanceStaff['allocated'])"/></span></div>
    <div><b>Comp Off Used</b><span class="big"><s:property value="days(balanceStaff['used'])"/></span></div>
    <div><b>Comp Off Balance</b><span class="big"><s:property value="days(balanceStaff['balance'])"/></span></div>
    <div></div>
  </div>
</div>

<div class="card">
  <div class="rhead">
    <h3>Staff Comp Off Details (<s:property value="reportRows.size()"/> record<s:if test="reportRows.size() != 1">s</s:if>)</h3>
    <s:if test="reportRows.isEmpty()"><button type="button" class="xl" id="staffExcel" disabled title="No records to export">Export to Excel</button></s:if>
    <s:else><a class="btn xl" id="staffExcel" href="<s:url action='compOffBalanceStaffExcel1' namespace='/'/>?sid=<s:property value='sid'/>">Export to Excel</a></s:else>
  </div>
  <s:if test="reportRows.isEmpty()"><div class="empty">No approved holiday or Comp Off yet.</div></s:if>
  <s:else>
  <div class="scroll"><table id="staffDetailTable" class="colfilter" data-noun="records">
    <thead><tr><th data-filter="text">Staff Name</th><th data-filter="text">Employee Code</th><th data-filter="date">Holiday Worked Date</th><th data-filter="date">Comp Off Date</th><th data-filter="text">Notes</th></tr></thead>
    <tbody>
    <s:iterator value="reportRows">
      <tr>
        <td><s:property value="top['staffName']"/></td>
        <td><s:property value="top['employeeCode']"/></td>
        <td><s:if test="top['workedDate'] != null"><s:date name="top['workedDate']" format="dd MMM yyyy"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:if test="top['compOffDate'] != null"><s:date name="top['compOffDate']" format="dd MMM yyyy"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:if test="top['compOffDate'] != null"><s:property value="top['notes']"/><s:if test="top['staffNotes'] != null"><span class="sn">Staff notes: <s:property value="top['staffNotes']"/></span></s:if></s:if><s:else><span class="na">Not Applied</span></s:else></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  </s:else>
</div>
</s:if>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
