<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="ed" uri="/WEB-INF/crypt.tld" %>
<s:set var="pageTitle" value="'Approve or Reject - Comp Off Request from Staff'"/><s:set var="navKey" value="'approveCompOff'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Approve or Reject &ndash; Comp Off Request from Staff</h2>
<p class="lead">Comp Off requests from your staff that are waiting for your approval.</p>

<s:set var="periodAction" value="'approveCompOff1'"/><%@ include file="layout/periodPicker.jspf" %>
<s:if test="periodLabel != null">
<div class="card">
  <div class="pagehead apphead"><h3 style="margin:0">Waiting for approval &ndash; applied in <s:property value="periodLabel"/></h3><%@ include file="layout/bulkBar.jspf" %></div>
  <s:if test="approvalRows.isEmpty()"><div class="empty">No pending Comp Off requests for you applied in <s:property value="periodLabel"/>.</div></s:if>
  <s:else>
    <div class="scroll appscroll"><table id="approvalTable">
    <thead><tr><th>Staff</th><th>Leave Date</th><th>Day Type</th><th>Holiday Worked Date Used</th><th>Reason</th><th>Approval Level</th><th>Applied On</th>
      <th class="selcol"><input type="checkbox" id="selAll" title="Select all" aria-label="Select all"></th></tr></thead>
    <tbody>
    <s:iterator value="approvalRows">
      <tr>
        <td><s:property value="staffName"/> (<s:property value="employeeCode"/>)</td>
        <td><s:date name="date" format="dd MMM yyyy"/></td>
        <td><s:property value="shiftOrDayType"/></td>
        <td><s:date name="workedDate" format="dd MMM yyyy"/></td>
        <td><s:if test="reason != null && reason != ''"><s:property value="reason"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:property value="approvalLevel"/></td>
        <td><s:date name="appliedOn" format="dd MMM yyyy"/></td>
        <td class="selcol"><s:set var="rid" value="id" scope="page"/><input type="checkbox" class="sel" value="<ed:crypt data="${rid}" type="EPT"/>" aria-label="Select request of <s:property value='staffName'/>"></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  <%-- floating Approve / Reject bar: stays at the bottom of the screen while rows are ticked --%>
  <div class="bulk floatbar" data-bulk style="visibility:hidden" role="region" aria-label="Selected requests">
    <span class="bc"></span>
    <button type="button" class="sm plain" data-act="sel-clear">Clear</button>
    <button type="button" class="sm p" data-act="decide" data-decision="approve">Approve</button>
    <button type="button" class="sm d" data-act="decide" data-decision="reject">Reject</button>
  </div>
  <div class="floatpad" aria-hidden="true"></div>
  </s:else>
</div>

<s:if test="!processingRows.isEmpty()">
<div class="card" id="inProcess">
  <h3>In Process</h3>
  <div class="scroll"><table>
    <thead><tr><th>Staff</th><th>Leave Date</th><th>Day Type</th><th>Holiday Worked Date Used</th><th>Approval Level</th><th>Applied On</th><th>Status</th><th>Next step</th></tr></thead>
    <tbody>
    <s:iterator value="processingRows">
      <tr>
        <td><s:property value="staffName"/> (<s:property value="employeeCode"/>)</td>
        <td><s:date name="date" format="dd MMM yyyy"/></td>
        <td><s:property value="shiftOrDayType"/></td>
        <td><s:date name="workedDate" format="dd MMM yyyy"/></td>
        <td><s:property value="approvalLevel"/></td>
        <td><s:date name="appliedOn" format="dd MMM yyyy"/></td>
        <td><s:if test="rejecting"><span class="badge b-Rejected">Rejected &ndash; In Process</span></s:if><s:else><span class="badge b-In_Process">In Process</span></s:else></td>
        <td><s:property value="nextStep"/></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
</div>
</s:if>
</s:if>

<form id="decideForm" method="post" action="<s:url action='decideCompOff1' namespace='/'/>" hidden
      data-noun="request" data-maxlength="<s:property value='textMaxLength'/>" data-max-ids="<s:property value='maxDecisionIds'/>">
  <input type="hidden" name="ids">
  <input type="hidden" name="decision">
  <input type="hidden" name="remarks">
  <%-- back to the same month after the decision --%>
  <input type="hidden" name="month" value="<s:property value='month'/>">
  <input type="hidden" name="year" value="<s:property value='year'/>">
</form>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
