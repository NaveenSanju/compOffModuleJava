<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Holiday Intimation'"/><s:set var="navKey" value="'hi'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<h2>Holiday Intimation</h2>
<p class="lead">Submit an intimation for a date on which you worked.</p>

<div class="card">
  <h3>New intimation</h3>
  <form id="hiForm" method="post" novalidate action="<s:url action='submitHolidayIntimation1' namespace='/'/>"
        data-approver="<s:property value='approver.email'/>" data-taken="<s:property value='takenWorkedDates'/>">
    <div class="row stack">
      <div class="f">
        <label>Staff</label>
        <div class="ro"><s:property value="currentStaff.staffName"/> (<s:property value="currentStaff.employeeCode"/>)</div>
        <div class="err"></div>
      </div>
      <div class="f<s:if test="fieldErrors['workedDate'] != null"> bad</s:if>" id="f_workedDate">
        <label for="workedDate_t">Worked date / Holiday date <span class="req">*</span></label>
        <div class="dpw">
          <input type="text" class="dpt" id="workedDate_t" readonly placeholder="Select worked Date" autocomplete="off">
          <input type="hidden" id="workedDate" name="workedDate" value="<s:property value='workedDate'/>">
        </div>
        <div class="err"><s:property value="fieldErrors['workedDate'][0]"/></div>
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
    </div>
    <div class="narrow" style="margin-top:4px">
      <div class="f<s:if test="fieldErrors['reason'] != null"> bad</s:if>" id="f_reason">
        <label for="reason">Notes <span class="req">*</span></label>
        <textarea id="reason" name="reason" maxlength="<s:property value='textMaxLength'/>" data-count autocomplete="off"
                  placeholder="Max <s:property value='textMaxLength'/> characters"><s:property value="reason"/></textarea>
        <div class="cntline"><div class="err"><s:property value="fieldErrors['reason'][0]"/></div><div class="cnt"></div></div>
      </div>
    </div>
    <div class="btns">
      <button type="submit" class="p">Submit</button>
      <button type="reset">Reset</button>
    </div>
  </form>
</div>

<div class="card">
  <h3>My Holiday Intimations</h3>
  <s:if test="intimations.isEmpty()"><div class="empty">No records</div></s:if>
  <s:else>
  <div class="scroll"><table id="hiTable" class="stdtbl" data-page-size="10">
    <thead><tr><th>Worked Date</th><th>Shift Type</th><th>Notes</th><th>Approver Status</th><th>Approver Notes</th><th class="c">Status</th></tr></thead>
    <tbody>
    <s:iterator value="intimations">
      <tr>
        <td><s:date name="workRequestDate" format="dd MMM yyyy"/></td>
        <td><s:property value="shiftType"/></td>
        <td><s:if test="notes != null && notes != ''"><s:property value="notes"/></s:if><s:else>&mdash;</s:else></td>
        <%-- each approver and their decision, in order, then the approver it waits with --%>
        <td class="apsteps"><s:if test="approverSteps.isEmpty()">&mdash;</s:if><s:else><s:iterator value="approverSteps">
          <div><s:property value="top[0]"/> &ndash; <span class="aps aps-<s:property value='top[1]'/>"><s:property value="top[1]"/></span></div></s:iterator></s:else></td>
        <td class="apsteps"><s:if test="approverNotes.isEmpty()">&mdash;</s:if><s:else><s:iterator value="approverNotes">
          <div><b><s:property value="top[0]"/>:</b> <s:property value="top[1]"/></div></s:iterator></s:else></td>
        <%-- "Available" (approved, not used yet) is shown as "Approved" in this list --%>
        <td class="c"><span class="badge fixed b-<s:property value="displayStatus.replace(' ', '_')"/>"><s:if test="displayStatus == 'Available'">Approved</s:if><s:else><s:property value="displayStatus"/></s:else></span></td>
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
