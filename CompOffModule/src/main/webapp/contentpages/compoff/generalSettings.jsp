<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'General Settings'"/><s:set var="navKey" value="'generalSettings'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<%-- Admin: additional HR approver of each flow (GeneralSettings 'holidayApprovalHR' / 'compOffApprovalHR'). --%>
<h2>General Settings</h2>
<s:iterator value="generalSettings" var="gs">
<div class="card gscard" id="gs-<s:property value='#gs.paramName'/>">
  <div class="bulkline"><h3><s:property value="#gs.title"/> &ndash; HR approval</h3>
    <span class="badge b-<s:if test='#gs.active'>Approved</s:if><s:else>Cancelled</s:else>"><s:if test="#gs.active">On</s:if><s:else>Off</s:else></span></div>
  <form method="post" action="<s:url action='saveGeneralSettings1' namespace='/'/>">
    <input type="hidden" name="paramName" value="<s:property value='#gs.paramName'/>">
    <div class="amfilters">
      <div class="f"><label for="hr-<s:property value='#gs.paramName'/>">HR approver</label>
        <select id="hr-<s:property value='#gs.paramName'/>" name="hrApproverId"><option value="">Select an HR approver</option>
          <s:iterator value="hrApproverChoices" var="c"><option value="<s:property value='#c.staffID'/>"<s:if test="#gs.approverId == #c.staffID"> selected</s:if>><s:property value="#c.staffName"/> (<s:property value="#c.employeeCode"/>)</option></s:iterator>
        </select></div>
      <%-- Enable = On, Disable = Off: one of the two is always ticked (compoff.js); only Enable is sent --%>
      <div class="f"><span class="flabel">HR approval</span>
        <div class="onoff" role="group" aria-label="HR approval">
          <label class="opt"><input type="checkbox" name="hrActive" value="1" data-onoff="on"<s:if test="#gs.active"> checked</s:if>> Enable</label>
          <label class="opt"><input type="checkbox" data-onoff="off"<s:if test="!#gs.active"> checked</s:if>> Disable</label>
        </div>
      </div>
    </div>
    <div class="btns"><button type="submit" class="p">Save</button></div>
  </form>
</div>
</s:iterator>
<%-- Admin: DesktopAppSettings (shared with the desktop application). Teams: compOffTeamIDs; the four Y / N flags. --%>
<div class="card gscard" id="gs-desktop">
  <div class="bulkline"><h3>Desktop App Settings &ndash; Comp Off module access</h3></div>
  <p class="dsnote">Users see the Comp Off module only when their team is selected. HR users are not checked by team: the HR approvers named above see their approval page and their own pages. The Administrator is not affected.</p>
  <form method="post" action="<s:url action='saveDesktopSettings1' namespace='/'/>" id="desktopForm">
    <div class="f" id="desktopTeamField"><span class="flabel" id="ms-label">Comp Off teams <span class="req">*</span></span>
      <div class="ms" id="teamPicker" data-order="<s:property value='desktopTeamIds'/>">
        <button type="button" class="msbtn" aria-haspopup="true" aria-expanded="false" aria-labelledby="ms-label"><span class="mssum">Select teams</span><span class="car" aria-hidden="true">&#9660;</span></button>
        <div class="mspanel" hidden>
          <div class="mstop"><input type="text" class="mssearch" placeholder="Search team name or ID" aria-label="Search team name or ID" autocomplete="off">
            <div class="msact"><span class="msinfo"></span><span><a href="#" class="msall">Select all</a><a href="#" class="msnone">Clear</a></span></div></div>
          <div class="mslist">
            <s:iterator value="desktopTeams" var="t">
            <label data-name="<s:property value='#t.name'/>" data-id="<s:property value='#t.teamId'/>"><input type="checkbox" name="desktopTeam" value="<s:property value='#t.teamId'/>"<s:if test="#t.selected"> checked</s:if>>
              <span class="tname<s:if test='!#t.active'> inact</s:if>"><s:property value="#t.name"/><s:if test="!#t.active && #t.found"> (inactive)</s:if></span><span class="tid">ID <s:property value="#t.teamId"/></span></label>
            </s:iterator>
            <div class="msempty" hidden>No team found</div>
          </div>
        </div>
      </div>
      <div class="chips" aria-live="polite"></div>
      <div class="mscount">Saved as compOffTeamIDs = <span class="msids"></span></div>
      <div class="err" id="desktopTeamError">Select at least one Comp Off team.</div>
    </div>
    <div class="flaggrid">
      <s:iterator value="desktopFlags" var="f">
      <div class="fl"><s:property value="#f.title"/><small><s:property value="#f.detail"/></small></div>
      <%-- Enable = Y, Disable = N: one of the two is always ticked (compoff.js); only Enable is sent --%>
      <div class="onoff" role="group" aria-label="<s:property value='#f.title'/>" data-flag="<s:property value='#f.paramName'/>">
        <label class="opt"><input type="checkbox" name="desktopFlag" value="<s:property value='#f.paramName'/>" data-onoff="on"<s:if test="#f.on"> checked</s:if>> Enable</label>
        <label class="opt"><input type="checkbox" data-onoff="off"<s:if test="!#f.on"> checked</s:if>> Disable</label>
      </div>
      </s:iterator>
    </div>
    <div class="btns"><button type="submit" class="p">Save</button></div>
  </form>
</div>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
