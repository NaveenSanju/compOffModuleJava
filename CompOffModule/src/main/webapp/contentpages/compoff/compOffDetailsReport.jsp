<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Comp Off Details Report'"/><s:set var="navKey" value="'compOffDetailsReport'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Comp Off Details Report</h2>
<p class="lead">Comp Off summary for every staff member.</p>

<div class="card">
  <h3>Comp Off Details</h3>
  <s:if test="reportRows.isEmpty()"><div class="empty">No records</div></s:if>
  <s:else>
  <div class="scroll"><table id="reportTable" class="colfilter" data-noun="staff">
    <thead><tr><th data-filter="text">Name</th><th data-filter="text">Emp Code</th><th data-filter="list">Team</th><th data-filter="date">DOJ</th><th data-filter="num">Holiday Worked Days</th><th data-filter="num">Comp Off Used</th><th data-filter="num">Comp Off Balance</th></tr></thead>
    <tbody>
    <s:iterator value="reportRows">
      <tr>
        <td><s:property value="top['staffName']"/></td>
        <td><s:property value="top['employeeCode']"/></td>
        <td><s:if test="top['teamName'] != null"><s:property value="top['teamName']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:if test="top['dateOfJoining'] != null"><s:date name="top['dateOfJoining']" format="dd MMM yyyy"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:property value="days(top['allocated'])"/></td>
        <td><s:property value="days(top['used'])"/></td>
        <td><s:property value="days(top['balance'])"/></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  </s:else>
</div>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
