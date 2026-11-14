<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%-- A user DesktopAppSettings gives no page of the module (team not in compOffTeamIDs, HR not named in General
     Settings): only the header with the name and Log out, no menu and no text. --%>
<s:set var="pageTitle" value="'Comp Off'"/><s:set var="navKey" value="''"/>
<!doctype html>
<html lang="en">
<%@ include file="layout/header.jsp" %>
<body>
<%@ include file="layout/menu.jsp" %>
<div class="page<s:if test="#wideMain"> wide</s:if>" id="co-page">
</div>
<%@ include file="layout/footer.jsp" %>
</body>
</html>
