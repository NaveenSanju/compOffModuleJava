<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'My Comp Off Balance'"/><s:set var="navKey" value="'bal'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>My Comp Off Balance</h2>
<p class="lead">Your Comp Off balance summary. Click a count to see its records below.</p>

<%-- Each count filters the table below (compoff.js, #balCard): the statuses it counts are in data-filter. --%>
<div class="stats" id="balStats">
  <div class="stat"><span>Holidays Approved</span>
    <button type="button" class="statn" data-filter="Available,Used,Completed" data-label="Holidays Approved"
            title="Show the approved holidays" aria-pressed="false"><s:if test="summary != null"><s:property value="days(summary.allocated)"/></s:if><s:else>0</s:else></button></div>
  <div class="stat"><span>Used</span>
    <button type="button" class="statn" data-filter="Used,Completed" data-label="Used"
            title="Show the holidays used for Comp Off" aria-pressed="false"><s:if test="summary != null"><s:property value="days(summary.used)"/></s:if><s:else>0</s:else></button></div>
  <div class="stat"><span>Balance</span>
    <button type="button" class="statn" data-filter="Available" data-label="Balance"
            title="Show the holidays still available for Comp Off" aria-pressed="false"><s:if test="summary != null"><s:property value="days(summary.balance)"/></s:if><s:else>0</s:else></button></div>
</div>
<br>
<s:if test="balanceMismatch">
  <div class="hint warn" id="balanceWarn">Your stored Comp Off balance does not match your available Comp Off days
    (<s:property value="availableDays"/>). Please contact the admin.</div>
</s:if>
<div class="card" id="balCard">
  <div class="rhead">
    <h3>My Holiday Intimations</h3>
    <div class="filtnote" id="balFilter" hidden>Showing <b></b> <button type="button" class="sm" id="balReset">Show all</button></div>
  </div>
  <s:if test="intimations.isEmpty()"><div class="empty">No records</div></s:if>
  <s:else>
  <div class="scroll"><table id="balTable" class="stdtbl" data-page-size="10">
    <thead><tr><th>Worked Date</th><th class="c">Status</th><th>Notes</th></tr></thead>
    <tbody>
    <s:iterator value="intimations">
      <%-- "Available" is shown as "Approved" on this page (the day is approved and not used yet) --%>
      <s:set var="st" value="displayStatus"/>
      <tr data-status="<s:property value='#st'/>">
        <td><s:date name="workRequestDate" format="dd MMM yyyy"/></td>
        <td class="c"><span class="badge fixed b-<s:property value="#st.replace(' ', '_')"/>"><s:if test="#st == 'Available'">Approved</s:if><s:else><s:property value="#st"/></s:else></span></td>
        <td><span class="note read"><s:if test="approverNotesText != null && approverNotesText != ''"><s:property value="approverNotesText"/></s:if><s:else>&mdash;</s:else></span></td>
      </tr>
    </s:iterator>
    <tr class="nofilt" hidden><td colspan="3" class="empty">No records for this filter</td></tr>
    </tbody>
  </table></div>
  </s:else>
</div>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
