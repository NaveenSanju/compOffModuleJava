<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="ed" uri="/WEB-INF/crypt.tld" %>
<s:set var="pageTitle" value="'Comp Off Balance and Details Report'"/><s:set var="navKey" value="'compOffBalanceReport'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Comp Off Balance and Details Report</h2>
<p class="lead">Holiday Worked Days, Comp Off Used and Comp Off Balance of every staff member. Click View Details for the dates.</p>

<div class="card">
  <div class="rhead">
    <h3>Comp Off Balance and Details</h3>
    <s:if test="reportRows.isEmpty()"><button type="button" class="xl" id="balanceExcel" disabled title="No records to export">Export to Excel</button></s:if>
    <s:else><a class="btn xl" id="balanceExcel" href="<s:url action='compOffBalanceExcel1' namespace='/'/>">Export to Excel</a></s:else>
  </div>
  <s:if test="reportRows.isEmpty()"><div class="empty">No records</div></s:if>
  <s:else>
  <div class="scroll"><table id="balanceTable" class="colfilter" data-noun="staff">
    <thead><tr><th data-filter="text">Name</th><th data-filter="list">Team Name</th><th data-filter="text">Employee Code</th><th data-filter="date">Date of Joining (DOJ)</th><th data-filter="num">Holiday Worked Days</th><th data-filter="num">Comp Off Used</th><th data-filter="num">Comp Off Balance</th><th>View</th></tr></thead>
    <tbody>
    <s:iterator value="reportRows">
      <tr>
        <td><s:property value="top['staffName']"/></td>
        <td><s:if test="top['teamName'] != null"><s:property value="top['teamName']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:property value="top['employeeCode']"/></td>
        <td><s:if test="top['dateOfJoining'] != null"><s:date name="top['dateOfJoining']" format="dd MMM yyyy"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:property value="days(top['allocated'])"/></td>
        <td><s:property value="days(top['used'])"/></td>
        <td><s:property value="days(top['balance'])"/></td>
        <td><s:set var="sidv" value="top['staffId']" scope="page"/><a class="vlink" href="<s:url action='compOffBalanceStaff1' namespace='/'/>?sid=<ed:crypt data="${sidv}" type="EPT"/>">View Details</a></td>
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
