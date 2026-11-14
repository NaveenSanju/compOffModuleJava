<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="ed" uri="/WEB-INF/crypt.tld" %>
<s:set var="pageTitle" value="'Cancel Holiday Intimation'"/><s:set var="navKey" value="'cancelhi'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Cancel Holiday Intimation</h2>
<p class="lead">Cancel an intimation that is Pending or Approved (not yet used for a Comp Off).</p>

<div class="card">
  <h3>My Holiday Intimations</h3>
  <s:if test="intimations.isEmpty()"><div class="empty">No records</div></s:if>
  <s:else>
  <div class="scroll"><table class="stdtbl">
    <thead><tr><th>Worked Date</th><th>Shift Type</th><th>Notes</th><th>Applied On</th><th class="c">Status</th><th class="actcol">Action</th></tr></thead>
    <tbody>
    <s:iterator value="intimations">
      <tr>
        <td><s:date name="workRequestDate" format="dd MMM yyyy"/></td>
        <td><s:property value="shiftType"/></td>
        <td><s:if test="notes != null && notes != ''"><s:property value="notes"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:date name="createdDate" format="dd MMM yyyy"/></td>
        <td class="c"><span class="badge fixed b-<s:property value="displayStatus.replace(' ', '_')"/>"><s:if test="displayStatus == 'Available'">Approved</s:if><s:else><s:property value="displayStatus"/></s:else></span></td>
        <td>
          <s:if test="cancellable">
            <s:set var="rid" value="HWRID" scope="page"/><a href="#" class="actlink" data-act="cancel-hi" data-id="<ed:crypt data="${rid}" type="EPT"/>"
                    data-date="<s:date name='workRequestDate' format='dd MMM yyyy'/>" data-status="<s:property value='holidayWorkingStatus'/>">Cancel Holiday Intimation</a>
          </s:if>
          <s:elseif test="holidayWorkingStatus == 'Used'"><span class="actnote">Cancel the Comp Off first</span></s:elseif>
          <s:else><span class="mut">&mdash;</span></s:else>
        </td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  </s:else>
</div>

<form id="cancelForm" method="post" action="<s:url action='doCancelHolidayIntimation1' namespace='/'/>" hidden>
  <input type="hidden" name="holidayWorkingId" data-id>
  <input type="hidden" name="cancelReason">
</form>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
