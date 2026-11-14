<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib prefix="ed" uri="/WEB-INF/crypt.tld" %>
<s:set var="pageTitle" value="'Apply Comp Off'"/><s:set var="navKey" value="'apply'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Apply Comp Off</h2>
<p class="lead">Apply for compensatory off against your available Comp Off balance.</p>

<div class="card">
  <div class="pagehead" style="margin-bottom:10px">
    <h3 style="margin:0">New Comp Off request</h3>
    <div class="avail">You have <span id="availDays"><s:property value="availableDays"/></span> days available for Compensatory off
      <span class="ai" tabindex="0" aria-label="Approved Comp Off days"><span class="aic">i</span><span class="tip">
        <s:if test="availableList.isEmpty()"><b>No Comp Off days available</b></s:if>
        <s:else><b>Approved Comp off</b>
          <s:iterator value="availableList" status="st"><div><s:property value="#st.count"/>. <s:date name="workRequestDate" format="dd MMM yyyy"/></div></s:iterator>
        </s:else>
      </span></span>
    </div>
  </div>

  <s:if test="balanceMismatch">
    <div class="hint warn" id="balanceWarn">Your stored Comp Off balance (<s:if test="summary != null"><s:property value="days(summary.balance)"/></s:if><s:else>0</s:else> days)
      does not match your available Comp Off days (<s:property value="availableDays"/>). Please contact the admin.</div>
  </s:if>
  <form id="coForm" method="post" novalidate action="<s:url action='submitCompOff1' namespace='/'/>"
        data-approver="<s:property value='approver.email'/>" data-available="<s:property value='availableDates'/>"
        data-applied="<s:property value='appliedLeaveDates'/>" data-maxdays="<s:property value='applyMaxDays'/>"
        data-worked="<s:property value='approvedWorkedDates'/>"
        data-balance="<s:if test="summary != null"><s:property value="days(summary.balance)"/></s:if><s:else>0</s:else>">
    <div class="row stack">
      <div class="f">
        <label for="leaveType">Leave type</label>
        <select id="leaveType"><option value="Compensatory off" selected>Compensatory off</option></select>
        <div class="err"></div>
      </div>
      <div class="f<s:if test="fieldErrors['dayType'] != null"> bad</s:if>" id="f_dayType">
        <label for="dayType">Day type <span class="req">*</span></label>
        <select id="dayType" name="dayType">
          <option value="">Select</option>
          <s:iterator value="dayTypes">
            <option value="<s:property/>"<s:if test="top == dayType"> selected</s:if>><s:property/></option>
          </s:iterator>
        </select>
        <div class="err"><s:property value="fieldErrors['dayType'][0]"/></div>
      </div>
      <div class="f<s:if test="fieldErrors['shiftType'] != null"> bad</s:if>" id="f_shiftType">
        <label for="shiftType">Shift type <span class="req">*</span></label>
        <select id="shiftType" name="shiftType">
          <option value="">Select</option>
          <s:iterator value="shiftTypes">
            <option value="<s:property/>"<s:if test="top == shiftType"> selected</s:if>><s:property/></option>
          </s:iterator>
        </select>
        <div class="err"><s:property value="fieldErrors['shiftType'][0]"/></div>
      </div>
      <div class="f<s:if test="fieldErrors['leaveDates'] != null"> bad</s:if>" id="f_leaveDates">
        <label for="leaveDates_t">Compensatory Off Date <span class="req">*</span></label>
        <div class="dpw multi">
          <input type="text" class="dpt" id="leaveDates_t" readonly placeholder="Select one or more dates" autocomplete="off">
          <input type="hidden" id="leaveDates" name="leaveDates" value="<s:property value='leaveDates'/>" data-min="<s:property value='monthStart'/>">
        </div>
        <div class="err"><s:property value="fieldErrors['leaveDates'][0]"/></div>
      </div>
      <div class="f">
        <label>Total days</label>
        <div class="ro" id="dur">&mdash;</div>
        <div class="err"></div>
      </div>
    </div>
    <div class="narrow" style="margin-top:4px">
      <div class="f<s:if test="fieldErrors['notes'] != null"> bad</s:if>" id="f_notes">
        <label for="notes">Notes <span class="req">*</span></label>
        <textarea id="notes" name="notes" maxlength="<s:property value='textMaxLength'/>" data-count autocomplete="off"
                  placeholder="Max <s:property value='textMaxLength'/> characters"><s:property value="notes"/></textarea>
        <div class="cntline"><div class="err"><s:property value="fieldErrors['notes'][0]"/></div><div class="cnt"></div></div>
      </div>
    </div>
    <div class="hint" id="fifo" style="margin-top:10px"></div>
    <div class="btns">
      <button type="submit" class="p">Request for comp off approval</button>
      <button type="reset">Reset</button>
    </div>
  </form>
</div>

<div class="card">
  <h3>My Comp Off Requests</h3>
  <s:if test="compOffRequests.isEmpty()"><div class="empty">No Comp Off requests yet.</div></s:if>
  <s:else>
  <div class="scroll"><table class="stdtbl" id="coReqTable" data-page-size="10">
    <thead><tr><th>Type</th><th>Day type</th><th>Date</th><th>Duration</th><th>Comp Off applied for</th><th class="c">Status</th><th>History</th></tr></thead>
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
    <button type="button" data-act="close-modal">Close</button>
  </div>
</template>
</s:iterator>

</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
