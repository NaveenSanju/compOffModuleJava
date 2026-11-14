<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Comp Off Processing'"/><s:set var="navKey" value="'compOffProcessing'"/><s:set var="wideMain" value="true"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Comp Off Processing</h2>

<s:set var="periodAction" value="'compOffProcessing1'"/><s:set var="monthLabel" value="'Leave month'"/>
<s:set var="periodHintText" value="'Select the leave month and year, then click Go.'"/>
<%@ include file="layout/periodPicker.jspf" %>
<s:if test="periodLabel != null">
<div class="card">
  <h3>In Process &ndash; leave dates in <s:property value="periodLabel"/> (<s:property value="heldRows.size()"/>)</h3>
  <s:if test="heldRows.isEmpty()"><div class="empty">Nothing is waiting for <s:property value="periodLabel"/>.</div></s:if>
  <s:else>
  <div class="scroll"><table id="heldTable">
    <thead><tr><th>Staff</th><th>Leave Date</th><th>Decision</th><th>Decided by</th><th>Decided on</th><th>Notes</th><th>Automatic run</th></tr></thead>
    <tbody>
    <s:iterator value="heldRows">
      <tr>
        <td><s:property value="staffName"/> (<s:property value="employeeCode"/>)</td>
        <td><s:date name="leaveDate" format="dd MMM yyyy"/></td>
        <td><s:if test="reject"><span class="badge b-Rejected">Reject</span></s:if><s:else><span class="badge b-Approved">Approve</span></s:else></td>
        <td><s:property value="approverName"/></td>
        <td><s:date name="clickedOn" format="dd MMM yyyy HH:mm"/></td>
        <td><s:if test="notes != null && notes != ''"><s:property value="notes"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:if test="automatic">Yes</s:if><s:else>No &ndash; waits for this page</s:else></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  <form id="processForm" method="post" action="<s:url action='processCompOffMonth1' namespace='/'/>" data-period="<s:property value='periodLabel'/>" data-count="<s:property value='heldRows.size()'/>">
    <input type="hidden" name="month" value="<s:property value='month'/>">
    <input type="hidden" name="year" value="<s:property value='year'/>">
    <div class="procmode">
      <span class="lbl">Process</span>
      <label><input type="radio" name="mode" value="one" checked> One request (oldest first)</label>
      <label><input type="radio" name="mode" value="all"> All requests</label>
      <button type="submit" class="p">Process</button>
    </div>
  </form>
  </s:else>
</div>
</s:if>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
