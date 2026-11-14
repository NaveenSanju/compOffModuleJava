<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Comp Off View Report'"/><s:set var="navKey" value="'compOffViewReport'"/><s:set var="reportAction" value="'compOffViewReport1'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Comp Off View Report</h2>
<p class="lead">Comp Off requests by requested date, team and staff.</p>

<div class="card">
  <h3>Comp Off View Report</h3>
  <%@ include file="layout/reportFilter.jspf" %>
</div>

<div class="card" id="res">
  <s:if test="reportRun"><h3>Report (<s:property value="reportRows.size()"/> record<s:if test="reportRows.size() != 1">s</s:if>)</h3></s:if>
  <div class="scroll"><table id="reportTable" class="colfilter" data-noun="records">
    <thead><tr><th data-filter="list">Team name</th><th data-filter="text">Staff name</th><th data-filter="text">Employee code</th><th data-filter="date">Comp off requested date</th><th data-filter="text">Comp off details</th><th data-filter="text">Comp off approvers</th><th data-filter="list">Comp off status</th></tr></thead>
    <tbody>
    <s:iterator value="reportRows">
      <tr>
        <td><s:if test="top['teamName'] != null"><s:property value="top['teamName']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:property value="top['staffName']"/></td>
        <td><s:property value="top['employeeCode']"/></td>
        <td><s:date name="top['requestedDate']" format="dd MMM yyyy"/></td>
        <td><s:date name="top['leaveDate']" format="dd MMM yyyy"/> (<s:property value="top['dayType']"/>)<br>
            <span class="note"><s:if test="top['reason'] != null && top['reason'] != ''"><s:property value="top['reason']"/></s:if><s:else>&mdash;</s:else></span></td>
        <td>
          <s:if test="top['approvers'].isEmpty()">&mdash;</s:if>
          <s:iterator value="top['approvers']" status="a"><s:property value="top['approverName']"/> <span class="badge b-<s:property value="top['approvalStatus']"/>"><s:property value="top['approvalStatus']"/></span><s:if test="!#a.last"><br></s:if></s:iterator>
        </td>
        <td><span class="badge b-<s:property value="top['status'].replace(' ', '_')"/>"><s:property value="top['status']"/></span></td>
      </tr>
    </s:iterator>
    <s:if test="reportRows.isEmpty()"><tr class="nofilt"><td colspan="7" class="empty">No record found</td></tr></s:if>
    </tbody>
  </table></div>
</div>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
