<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="ed" uri="/WEB-INF/crypt.tld" %>
<s:set var="pageTitle" value="'Cancel Comp Off'"/><s:set var="navKey" value="'cancelco'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Cancel Comp Off</h2>
<p class="lead">Cancel a Pending or Approved Comp Off request for the current or a later month; the day returns to your balance.</p>

<div class="card">
  <h3>My Comp Off Requests</h3>
  <s:if test="compOffRequests.isEmpty()"><div class="empty">No Comp Off requests yet.</div></s:if>
  <s:else>
  <div class="scroll"><table class="stdtbl">
    <thead><tr><th>Type</th><th>Day type</th><th>Date</th><th>Duration</th><th>Comp Off applied for</th><th class="c">Status</th><th>History</th><th>Action</th></tr></thead>
    <tbody>
    <s:iterator value="compOffRequests">
      <tr>
        <td>Compensatory off</td>
        <td><s:property value="dayType"/></td>
        <td><s:date name="leaveDate" format="dd MMM yyyy"/></td>
        <td><s:property value="days(duration)"/> day</td>
        <td><s:date name="leaveDate" format="dd MMM yyyy"/></td>
        <td class="c"><span class="badge fixed b-<s:property value="displayStatus.replace(' ', '_')"/>"><s:property value="displayStatus"/></span></td>
        <td><s:set var="rid" value="COID" scope="page"/><a href="#" class="hlink" data-act="history" data-id="<ed:crypt data="${rid}" type="EPT"/>">View all history</a></td>
        <td>
          <s:if test="cancellable">
            <a href="#" class="clink" data-act="cancel-co" data-id="<ed:crypt data="${rid}" type="EPT"/>"
               data-date="<s:date name='leaveDate' format='dd MMM yyyy'/>">Cancel Comp Off</a>
          </s:if>
          <s:else><span class="mut">&mdash;</span></s:else>
        </td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  </s:else>
</div>

<%-- History of each request, shown in a modal by "View all history" --%>
<s:iterator value="compOffRequests">
<s:set var="rid" value="COID" scope="page"/><template id="hist-<ed:crypt data="${rid}" type="EPT"/>">
  <h3>History &ndash; leave <s:date name="leaveDate" format="dd MMM yyyy"/></h3>
  <p>Status: <span class="badge b-<s:property value="displayStatus.replace(' ', '_')"/>"><s:property value="displayStatus"/></span>
     &nbsp; <span class="mut">Holiday worked date used: <s:date name="attendanceDate" format="dd MMM yyyy"/></span></p>
  <p class="mut" style="margin:0 0 8px;font-size:12px">A / U / B = Allocated / Used / Balance</p>
  <s:set var="rows" value="historyByRequest[COID]"/>
  <s:if test="#rows == null || #rows.isEmpty()"><div class="empty">No history yet.</div></s:if>
  <s:else>
  <div class="scroll"><table>
    <thead><tr><th>Date and time</th><th>Action</th><th>A / U / B</th><th>Actor</th><th>Notes</th></tr></thead>
    <tbody>
    <s:iterator value="#rows">
      <tr>
        <td><s:date name="createdDate" format="dd MMM yyyy HH:mm"/></td>
        <td><b><s:property value="historyAction"/></b></td>
        <td><s:if test="prevBalance != null"><s:property value="prevBalance"/> &rarr; </s:if><s:property value="newBalance"/></td>
        <td><s:property value="staffNames[actorId]"/></td>
        <td><s:if test="remarks != null && remarks != ''"><s:property value="remarks"/></s:if><s:else>&mdash;</s:else></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  </s:else>
  <div class="btns" style="justify-content:flex-end">
    <s:if test="cancellable">
      <button type="button" class="d" data-act="cancel-co" data-id="<ed:crypt data="${rid}" type="EPT"/>"
              data-date="<s:date name='leaveDate' format='dd MMM yyyy'/>">Cancel this comp off</button>
    </s:if>
    <button type="button" data-act="close-modal">Close</button>
  </div>
</template>
</s:iterator>

<form id="cancelForm" method="post" action="<s:url action='cancelCompOff1' namespace='/'/>" hidden>
  <input type="hidden" name="compOffRequestId" data-id>
  <input type="hidden" name="cancelReason">
</form>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
