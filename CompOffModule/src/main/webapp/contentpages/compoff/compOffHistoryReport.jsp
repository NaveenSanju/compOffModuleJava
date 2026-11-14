<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="ed" uri="/WEB-INF/crypt.tld" %>
<s:set var="pageTitle" value="'Comp Off History'"/><s:set var="navKey" value="'compOffHistoryReport'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Comp Off History</h2>
<p class="lead">Holiday Intimation and Comp Off history of all staff.</p>

<div class="card">
  <h3>Comp Off History</h3>
  <p class="mut" style="margin:0 0 8px;font-size:12px">A / U / B = Allocated / Used / Balance<s:if test="reportRows.size() == historyLimit"> &middot; showing the latest <s:property value="historyLimit"/> changes</s:if></p>
  <s:if test="reportRows.isEmpty()"><div class="empty">No records</div></s:if>
  <s:else>
  <div class="scroll"><table id="historyTable" class="colfilter" data-noun="changes">
    <thead><tr><th data-filter="text">Employee</th><th data-filter="list">Type</th><th data-filter="date">Worked Date</th><th data-filter="date">Leave Date</th><th data-filter="list">Action</th><th data-filter="text">Prev A / U / B</th><th data-filter="text">New A / U / B</th><th data-filter="text">Intimation</th><th data-filter="text">Request</th><th data-filter="text">Actor</th><th data-filter="date">Date and time</th><th data-filter="text">Notes</th></tr></thead>
    <tbody>
    <s:iterator value="reportRows">
      <tr>
        <td><s:property value="top['staffName']"/> (<s:property value="top['employeeCode']"/>)</td>
        <td><s:property value="top['leaveType']"/></td>
        <td><s:if test="top['workedDate'] != null"><s:date name="top['workedDate']" format="dd MMM yyyy"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:if test="top['leaveDate'] != null"><s:date name="top['leaveDate']" format="dd MMM yyyy"/></s:if><s:else>&mdash;</s:else></td>
        <td><b><s:property value="top['action']"/></b></td>
        <td><s:if test="top['prevBalance'] != null"><s:property value="top['prevBalance']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:property value="top['newAllocated']"/> / <s:property value="top['newUsed']"/> / <s:property value="top['newBalance']"/></td>
        <td><s:if test="top['holidayWorkingId'] > 0">#<s:property value="top['holidayWorkingId']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:if test="top['compOffRequestId'] > 0">#<s:property value="top['compOffRequestId']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:if test="top['actorName'] != null"><s:property value="top['actorName']"/> (<s:property value="top['actorCode']"/>)</s:if><s:else>&mdash;</s:else></td>
        <td><s:date name="top['actionDate']" format="dd MMM yyyy HH:mm"/></td>
        <td><s:if test="top['remarks'] != null && top['remarks'] != ''"><s:property value="top['remarks']"/></s:if><s:else>&mdash;</s:else></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  </s:else>
</div>

<div class="card">
  <h3>Holiday Intimation History</h3>
  <s:if test="intimationChoices.isEmpty()"><div class="empty">No holiday intimations.</div></s:if>
  <s:else>
  <form id="hwForm" method="get" action="<s:url action='compOffHistoryReport1' namespace='/'/>">
    <s:set var="selHw" value="selectedHwId"/>
    <div class="f<s:if test="fieldErrors['hwId'] != null"> bad</s:if>" id="f_hwId" style="max-width:420px;margin-bottom:10px">
      <label for="hwId">Holiday Intimation</label>
      <select id="hwId" name="hwId">
        <s:iterator value="intimationChoices">
          <s:set var="rid" value="top['id']" scope="page"/><option value="<ed:crypt data="${rid}" type="EPT"/>"<s:if test="top['id'] == #selHw"> selected</s:if>>#<s:property value="top['id']"/> &ndash; <s:property value="top['staffName']"/> (<s:property value="top['employeeCode']"/>) &ndash; <s:date name="top['workedDate']" format="dd MMM yyyy"/></option>
        </s:iterator>
      </select>
      <div class="err"><s:property value="fieldErrors['hwId'][0]"/></div>
      <noscript><button type="submit">Show</button></noscript>
    </div>
  </form>
  <s:if test="holidayHistoryRows.isEmpty()"><div class="empty">No history rows for this intimation.</div></s:if>
  <s:else>
  <div class="scroll"><table id="hwHistoryTable" class="colfilter" data-noun="rows">
    <thead><tr><th data-filter="list">Type</th><th data-filter="list">Action</th><th data-filter="list">Previous Status</th><th data-filter="list">New Status</th><th data-filter="text">Level</th><th data-filter="text">Actor</th><th data-filter="text">Notes</th><th data-filter="date">Date</th></tr></thead>
    <tbody>
    <s:iterator value="holidayHistoryRows">
      <tr>
        <td><s:property value="top['leaveType']"/></td>
        <td><s:property value="top['action']"/></td>
        <td><s:if test="top['previousStatus'] != null"><s:property value="top['previousStatus']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:property value="top['newStatus']"/></td>
        <td><s:property value="top['level']"/></td>
        <td><s:if test="top['actorName'] != null"><s:property value="top['actorName']"/> (<s:property value="top['actorCode']"/>)</s:if><s:else>&mdash;</s:else></td>
        <td><s:if test="top['remarks'] != null && top['remarks'] != ''"><s:property value="top['remarks']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:date name="top['actionDate']" format="dd MMM yyyy HH:mm"/></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  </s:else>
  </s:else>
</div>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
