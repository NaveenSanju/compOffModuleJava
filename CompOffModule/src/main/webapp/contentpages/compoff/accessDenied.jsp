<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%-- Shown (HTTP 403) when a logged-in user opens a page their role may not use. --%>
<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Comp Off &ndash; Access denied</title>
<link rel="stylesheet" href="<s:url value='/asserts/css/compoff-theme.css'/>">
<link rel="stylesheet" href="<s:url value='/asserts/css/compoff.css'/>"></head>
<body class="center-page">
  <div class="card login-card">
    <h2>Access denied</h2>
    <p class="lead">You do not have permission to open this page.</p>
    <div class="btns"><a class="btn p" href="<s:url action='home1' namespace='/'/>">Go to my Comp Off home page</a>
      <a class="btn" href="<s:url action='dashboard' namespace='/'/>">Back to application</a></div>
  </div>
<% com.util.CompanyContext.clear(); %><%-- the request's company (set by CompOffAction.access) ends with the page --%>
</body>
</html>
