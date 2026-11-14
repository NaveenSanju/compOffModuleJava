<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>Comp Off &ndash; Error</title>
<link rel="stylesheet" href="<s:url value='/asserts/css/compoff-theme.css'/>">
<link rel="stylesheet" href="<s:url value='/asserts/css/compoff.css'/>">
</head>
<body><main><div class="card"><h3>Comp Off is not available</h3>
<s:iterator value="actionErrors"><p><s:property/></p></s:iterator></div></main><% com.util.CompanyContext.clear(); %><%-- the request's company (set by CompOffAction.access) ends with the page --%>
</body>
</html>
