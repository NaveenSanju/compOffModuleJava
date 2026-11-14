<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%-- The module's own Leave page: the Comp Off cards of the logged-in user (layout/leaveLinks.jsp). The application's
     own Leave page shows the same cards by including layout/leaveLinks.jsp. --%>
<s:set var="pageTitle" value="'Leave'"/><s:set var="navKey" value="'leave'"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page" id="co-page">
<h2>Leave</h2>
<div class="co-panel"><%@ include file="layout/leaveLinks.jsp" %></div>
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
