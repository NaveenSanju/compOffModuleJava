<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<s:set var="pageTitle" value="'Approver Management'"/><s:set var="navKey" value="'approverManagement'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
<%-- Admin: one approver chain (up to maxLevels levels) per staff member, used for Holiday Intimation and Comp Off.
     Editing, assigning to several staff and the history are popups built by compoff.js from the data-* attributes. --%>
<s:set var="pg" value="approverPage"/>
<h2>Approver Management</h2>
<p class="lead">Set who approves each staff member's Holiday Intimations and Comp Off requests. One approver chain per staff member, used for both.</p>

<div class="stats amstats">
  <div class="stat"><b><s:property value="#pg.totalStaff"/></b><span>Active staff</span></div>
  <div class="stat"><b><s:property value="#pg.withApprovers"/></b><span>With approvers</span></div>
  <div class="stat<s:if test="#pg.withoutApprovers > 0"> warn</s:if>"><b><s:property value="#pg.withoutApprovers"/></b><span>No approver set</span></div>
  <div class="stat"><b><s:property value="#pg.multiLevel"/></b><span>Multi-level chains</span></div>
</div>

<div class="card">
  <h3>Filter</h3>
  <s:set var="selTeam" value="teamId"/><s:set var="selApprover" value="approvedBy"/><s:set var="selUt" value="userTypeId"/>
  <form id="amFilter" method="get" action="<s:url action='approverManagement1' namespace='/'/>">
    <div class="amfilters">
      <div class="f"><label for="teamId">Team</label>
        <select id="teamId" name="teamId"><option value="">All teams</option>
          <s:iterator value="teams"><option value="<s:property value="top['teamId']"/>"<s:if test="top['teamId'].toString() == #selTeam"> selected</s:if>><s:property value="top['teamName']"/></option></s:iterator>
        </select></div>
      <div class="f"><label for="q">Staff</label>
        <input id="q" name="q" maxlength="100" placeholder="Name or employee code" value="<s:property value='q'/>" autocomplete="off"></div>
      <div class="f"><label for="approvedBy">Approved by</label>
        <select id="approvedBy" name="approvedBy"><option value="">Any approver</option>
          <s:iterator value="#pg.approverOptions"><option value="<s:property value='staffId'/>"<s:if test="staffId.toString() == #selApprover"> selected</s:if>><s:property value="label"/></option></s:iterator>
        </select></div>
      <div class="f"><label for="userTypeId">User type</label>
        <select id="userTypeId" name="userTypeId"><option value="">All user types</option>
          <s:iterator value="#pg.userTypes"><option value="<s:property value='userTypeID'/>"<s:if test="userTypeID.toString() == #selUt"> selected</s:if>><s:property value="userTypeName"/></option></s:iterator>
        </select></div>
      <label class="chk"><input type="checkbox" name="noneOnly" value="1"<s:if test='noneOnly == "1"'> checked</s:if>> Only staff without approvers</label>
    </div>
    <div class="btns">
      <button type="submit" class="p">Go</button>
      <a class="btn plain" href="<s:url action='approverManagement1' namespace='/'/>">Reset</a>
    </div>
  </form>
</div>

<div class="card" id="amCard" data-max-levels="<s:property value='maxLevels'/>" data-maxlength="<s:property value='textMaxLength'/>">
  <div class="bulkline"><h3>Staff and their approvers (<s:property value="#pg.rows.size()"/>)</h3></div>
  <div class="selnote" id="amSelNote" hidden></div>
  <s:if test="#pg.rows.isEmpty()"><div class="empty">No staff match the filter.</div></s:if>
  <s:else>
  <div class="scroll"><table id="amTable">
    <thead><tr><th class="selcol"><input type="checkbox" id="amAll" title="Select all on this page" aria-label="Select all on this page"></th><th>Staff</th><th>Team</th><th>User Type</th><th>Approval chain (level 1 &rarr; 2 &rarr; 3)</th><th>Last changed</th><th></th></tr></thead>
    <tbody>
    <s:iterator value="#pg.rows">
      <tr data-id="<s:property value='staffId'/>" data-code="<s:property value='employeeCode'/>" data-name="<s:property value='staffName'/>"
          data-team="<s:property value='teamName'/>" data-chain="<s:property value='chainIds'/>" data-chain-text="<s:property value='chainText'/>"
          data-usertype="<s:property value='userTypeName'/>"<s:if test="!hasApprovers"> class="noappr"</s:if>>
        <td class="selcol"><input type="checkbox" class="amSel" value="<s:property value='staffId'/>" aria-label="Select <s:property value='staffName'/>"></td>
        <td><b><s:property value="staffName"/></b><div class="mut sub"><s:property value="employeeCode"/></div></td>
        <td><s:if test="teamName != null"><s:property value="teamName"/></s:if><s:else>&mdash;</s:else></td>
        <td><s:if test="userTypeName != null"><s:property value="userTypeName"/></s:if><s:else>&mdash;</s:else></td>
        <td>
          <s:if test="hasApprovers">
            <div class="chain"><s:iterator value="chain" status="cs"><s:if test="!#cs.first"><span class="arrow">&rarr;</span></s:if><span class="lv<s:if test='!active'> lv-off</s:if>"<s:if test="!active"> title="Inactive staff member"</s:if>><b><s:property value="level"/></b><s:property value="name"/> <i><s:property value="code"/> &middot; <s:property value="userType"/></i></span></s:iterator></div>
          </s:if>
          <s:else><span class="none">No approver set</span></s:else>
        </td>
        <td class="upd"><s:if test="lastChanged != null"><s:date name="lastChanged" format="dd MMM yyyy"/><s:if test="lastChangedBy != null"> &middot; <s:property value="lastChangedBy"/></s:if></s:if><s:else>&mdash;</s:else></td>
        <td><div class="acts">
          <button type="button" class="sm p" data-act="am-edit"><s:if test="hasApprovers">Edit</s:if><s:else>Set approvers</s:else></button>
          <button type="button" class="sm" data-act="am-history">History</button>
        </div></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  <div class="pager" id="amPager" hidden></div>
  </s:else>
  <div class="selbar" id="amSelBar" hidden></div>
</div>

<%-- Approver history of each listed staff member (newest first), shown by "History" --%>
<s:iterator value="#pg.rows">
<template id="am-hist-<s:property value='staffId'/>">
  <h3>Approver history &ndash; <s:property value="staffName"/> (<s:property value="employeeCode"/>)</h3>
  <s:if test="history.isEmpty()"><div class="empty">No approver changes recorded yet.</div></s:if>
  <s:else>
  <div class="scroll"><table>
    <thead><tr><th>Changed on</th><th>Changed by</th><th>Before</th><th>After</th><th>Reason</th></tr></thead>
    <tbody>
    <s:iterator value="history">
      <tr>
        <td><s:date name="changedOn" format="dd MMM yyyy HH:mm"/></td>
        <td><s:property value="changedBy"/></td>
        <td><s:if test="before.isEmpty()">&mdash;</s:if><s:else><div class="chain"><s:iterator value="before"><span class="lv"><b><s:property value="level"/></b><s:property value="name"/> <i><s:property value="code"/></i></span></s:iterator></div></s:else></td>
        <td><div class="chain"><s:iterator value="after"><span class="lv"><b><s:property value="level"/></b><s:property value="name"/> <i><s:property value="code"/></i></span></s:iterator></div></td>
        <td><s:if test="reason != null && reason != ''"><s:property value="reason"/></s:if><s:else>&mdash;</s:else></td>
      </tr>
    </s:iterator>
    </tbody>
  </table></div>
  </s:else>
  <div class="btns" style="justify-content:flex-end"><button type="button" data-act="close-modal">Close</button></div>
</template>
</s:iterator>

<%-- Approvers that can be chosen: active staff with the Approver role --%>
<select id="amOptions" hidden aria-hidden="true">
  <s:iterator value="#pg.approverOptions"><option value="<s:property value='staffId'/>"><s:property value="label"/></option></s:iterator>
</select>

<form id="amSave" method="post" action="<s:url action='saveApprovers1' namespace='/'/>" hidden>
  <input type="hidden" name="staffIds"><input type="hidden" name="approverIds">
  <input type="hidden" name="reason"><input type="hidden" name="confirmReplace">
  <%-- back to the same filtered list afterwards --%>
  <input type="hidden" name="teamId" value="<s:property value='teamId'/>"><input type="hidden" name="q" value="<s:property value='q'/>">
  <input type="hidden" name="approvedBy" value="<s:property value='approvedBy'/>"><input type="hidden" name="userTypeId" value="<s:property value='userTypeId'/>">
  <input type="hidden" name="noneOnly" value="<s:property value='noneOnly'/>">
</form>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
