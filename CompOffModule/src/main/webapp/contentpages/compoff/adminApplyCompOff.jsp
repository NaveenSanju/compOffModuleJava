<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Apply Comp Off for Staff'"/><s:set var="navKey" value="'adminApplyCompOff'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<%-- Admin: a Comp Off (Full day, Day Shift) for one or more staff members by employee code, saved as Approved by the Admin. --%>
<h2>Apply Comp Off for Staff</h2>
<p class="lead">Admin only. Apply a Comp Off for one or more staff members by employee code. It is saved as <b>Approved</b> at once
  (no approval steps) and uses each staff member's oldest available Holiday Intimation.</p>

<div class="card">
  <h3>1. Staff</h3>
  <form id="adminFindForm" method="get" novalidate action="<s:url action='adminApplyCompOff1' namespace='/'/>">
    <div class="findrow">
      <div class="f wide<s:if test="fieldErrors['employeeCodes'] != null"> bad</s:if>" id="f_employeeCodes">
        <label for="employeeCodes">Employee codes <span class="req">*</span></label>
        <input type="text" id="employeeCodes" name="employeeCodes" maxlength="4600" autocomplete="off" value="<s:property value='employeeCodes'/>"
               placeholder="e.g. EMP101,EMP103">
        <div class="err"><s:property value="fieldErrors['employeeCodes'][0]"/></div>
      </div>
      <button type="submit" class="p">Find</button>
    </div>
    <div class="mut sub">Comma separated, no spaces (e.g. EMP101,EMP103)</div>
  </form>
  <s:if test="!adminPicks.isEmpty()">
  <div class="scroll" style="margin-top:12px"><table class="stdtbl" id="pickTable">
    <thead><tr><th>Employee code</th><th>Staff</th><th>Team</th><th>Approvers</th><th class="c">Balance</th><th>Holiday Intimation to be used</th></tr></thead>
    <tbody>
    <s:iterator value="adminPicks">
      <tr data-code="<s:property value='staff.employeeCode'/>" data-name="<s:property value='staff.staffName'/>"
          data-worked="<s:date name='nextWorked' format='dd MMM yyyy'/>" data-balance="<s:property value='availableText'/>">
        <td><s:property value="staff.employeeCode"/></td>
        <td><s:property value="staff.staffName"/></td>
        <td><s:if test="teamName != null"><s:property value="teamName"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:if test="approvers != null"><s:property value="approvers"/></s:if><s:else>&mdash;</s:else></td>
        <td class="c"><s:property value="availableText"/></td>
        <td><s:date name="nextWorked" format="dd MMM yyyy"/></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  </s:if>
</div>

<%-- the Comp Off form only when every code was found with a balance --%>
<s:if test="!adminPicks.isEmpty() && fieldErrors['employeeCodes'] == null">
<div class="card">
  <h3>2. Comp Off</h3>
  <form id="adminCoForm" method="post" novalidate action="<s:url action='saveAdminCompOff1' namespace='/'/>"
        data-admin="<s:property value='currentStaff.staffName'/>">
    <input type="hidden" name="employeeCodes" value="<s:property value='employeeCodes'/>">
    <div class="row" style="grid-template-columns: minmax(200px, 300px);">
      <div class="f<s:if test="fieldErrors['compOffDate'] != null"> bad</s:if>" id="f_compOffDate">
        <label for="compOffDate_t">Compensatory Off Date <span class="req">*</span></label>
        <div class="dpw">
          <input type="text" class="dpt" id="compOffDate_t" readonly placeholder="dd Mon yyyy" autocomplete="off">
          <input type="hidden" id="compOffDate" name="compOffDate" value="<s:property value='compOffDate'/>">
        </div>
        <div class="err"><s:property value="fieldErrors['compOffDate'][0]"/></div>
      </div>
    </div>
    <div class="narrow">
      <div class="f<s:if test="fieldErrors['notes'] != null"> bad</s:if>" id="f_notes">
        <label for="notes">Notes <span class="req">*</span></label>
        <textarea id="notes" name="notes" maxlength="<s:property value='textMaxLength'/>" data-count autocomplete="off"
                  placeholder="Max <s:property value='textMaxLength'/> characters"><s:property value="notes"/></textarea>
        <div class="cntline"><div class="err"><s:property value="fieldErrors['notes'][0]"/></div><div class="cnt"></div></div>
      </div>
    </div>
    <div class="willbe">Saved as <span class="badge b-Approved">Approved</span> &middot; Approved by: <b><s:property value="currentStaff.staffName"/></b></div>
    <div class="btns">
      <button type="submit" class="p">Apply as Approved (<s:property value="adminPicks.size()"/> staff)</button>
      <a class="btn plain" href="<s:url action='adminApplyCompOff1' namespace='/'/>">Reset</a>
    </div>
  </form>
</div>
</s:if>

<div class="card">
  <h3>Comp Off applied by Admin</h3>
  <s:if test="adminApplied.isEmpty()"><div class="empty">No Comp Off applied by an Admin yet.</div></s:if>
  <s:else>
  <div class="scroll"><table class="stdtbl" id="adminAppliedTable">
    <thead><tr><th>Comp Off date</th><th>Staff</th><th>Holiday worked date used</th><th>Notes</th><th>Approved by</th><th>Applied on</th><th class="c">Status</th></tr></thead>
    <tbody>
    <s:iterator value="adminApplied">
      <tr>
        <td><s:date name="top['leaveDate']" format="dd MMM yyyy"/></td>
        <td><s:property value="top['staffName']"/> (<s:property value="top['employeeCode']"/>)</td>
        <td><s:date name="top['workedDate']" format="dd MMM yyyy"/></td>
        <td><span class="note read"><s:if test="top['notes'] != null && top['notes'] != ''"><s:property value="top['notes']"/></s:if><s:else>&mdash;</s:else></span></td>
        <td><s:if test="top['approvedBy'] != null"><s:property value="top['approvedBy']"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:date name="top['appliedOn']" format="dd MMM yyyy"/></td>
        <td class="c"><span class="badge fixed b-<s:property value="top['status'].replace(' ', '_')"/>"><s:property value="top['status']"/></span></td>
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
