<%-- Comp Off pages: the top of <body>. Included by every Comp Off page right after <body>. --%>
<%-- Page data for compoff.js (context path, today, text limit, the one-time message) --%>
<div id="co-data" hidden data-ctx="${pageContext.request.contextPath}" data-today="<s:property value='today'/>"
     data-maxlength="<s:property value='textMaxLength'/>" data-flash="<s:property value='flashMessage'/>"
     data-flash-type="<s:property value='flashType'/>"></div>
<%-- Your Leave page (the page that shows the Comp Off cards, layout/leaveLinks.jsp): action name without ".action" --%>
<s:set var="leavePage" value="'leave1'"/>

<%-- ===== YOUR APPLICATION'S TOP MENU: replace from here ... ===== --%>
<div class="co-topbar"><div class="co-in">
  <span class="co-logo">logo</span>
  <div class="who">
    <span class="who-name"><s:property value="currentStaff.staffName"/></span>
    <span class="who-meta"><s:property value="currentStaff.employeeCode"/> &middot; <s:property value="currentUserTypeName"/></span>
    <a class="logout" href="<s:url action='sessionexpire' namespace='/'/>">Log out</a>
  </div>
</div></div>
<div class="co-menubar"><div class="co-in">
  <a class="backapp" href="<s:url action='dashboard' namespace='/'/>">Home</a>
  <a class="on" href="<s:url action='%{#leavePage}' namespace='/'/>">Leave</a>
</div></div>
<%-- ===== ... to here ===== --%>

<s:if test="#navKey != 'leave'"><div class="co-crumb"><a href="<s:url action='%{#leavePage}' namespace='/'/>" id="co-back">&larr; Leave</a></div></s:if>
