<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="ed" uri="/WEB-INF/crypt.tld" %>
<s:set var="pageTitle" value="'Comp Off Cancel'"/><s:set var="navKey" value="'compOffCancel'"/><s:set var="reportAction" value="'compOffCancel1'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<%-- Admin: cancel Pending / In Process / Approved Comp Off of any month (Comp Off date between From and To). --%>
<h2>Comp Off Cancel</h2>
<p class="lead">Cancel a Pending, In Process or Approved Comp Off of any month. The Holiday Intimation becomes Approved again and the
  day goes back to the staff member's balance.</p>

<div class="card">
  <h3>Comp Off date</h3>
  <%@ include file="layout/reportFilter.jspf" %>
</div>

<s:if test="reportRun">
<div class="card" id="res">
  <h3>Comp Off that can be cancelled (<s:property value="cancelRows.size()"/>)</h3>
  <s:if test="cancelRows.isEmpty()"><div class="empty">No Pending, In Process or Approved Comp Off in this period.</div></s:if>
  <s:else>
  <div class="scroll"><table class="stdtbl" id="cancelTable">
    <thead><tr><th>Comp Off date</th><th>Team</th><th>Staff</th><th>Holiday worked date</th><th>Notes</th><th>Applied on</th><th class="c">Status</th><th class="actcol">Action</th></tr></thead>
    <tbody>
    <s:iterator value="cancelRows">
      <tr>
        <td><s:date name="top['leaveDate']" format="dd MMM yyyy"/></td>
        <td><s:if test="top['teamName'] != null"><s:property value="top['teamName']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:property value="top['staffName']"/> (<s:property value="top['employeeCode']"/>)</td>
        <td><s:date name="top['workedDate']" format="dd MMM yyyy"/></td>
        <td><span class="note read"><s:if test="top['notes'] != null && top['notes'] != ''"><s:property value="top['notes']"/></s:if><s:else>&mdash;</s:else></span></td>
        <td><s:date name="top['appliedOn']" format="dd MMM yyyy"/></td>
        <td class="c"><span class="badge fixed b-<s:property value="top['status'].replace(' ', '_')"/>"><s:property value="top['status']"/></span></td>
        <td><s:set var="rid" value="top['id']" scope="page"/><a href="#" class="clink" data-act="cancel-coa" data-id="<ed:crypt data="${rid}" type="EPT"/>"
               data-date="<s:date name="top['leaveDate']" format='dd MMM yyyy'/>" data-staff="<s:property value="top['staffName']"/> (<s:property value="top['employeeCode']"/>)">Cancel Comp Off</a></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  </s:else>
</div>
</s:if>

<form id="cancelForm" method="post" action="<s:url action='cancelCompOffByAdmin1' namespace='/'/>" hidden>
  <input type="hidden" name="compOffRequestId" data-id>
  <input type="hidden" name="cancelReason">
  <%-- back to the same list --%>
  <input type="hidden" name="fromDate" value="<s:property value='fromDate'/>"><input type="hidden" name="toDate" value="<s:property value='toDate'/>">
  <input type="hidden" name="teamId" value="<s:property value='teamId'/>"><input type="hidden" name="staffFilter" value="<s:property value='staffFilter'/>">
  <input type="hidden" name="run" value="<s:property value='run'/>">
</form>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
