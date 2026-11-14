<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Insert title here</title>
</head>
<body>
<%
    String userName = (String)session.getAttribute("userName");

%>
          
   
    
    Welcome, <%=userName %>
    
    <br><br>
    <form action="logout.action" method="POST">
    <input type="submit" value="Logout"/>  
    </form>
          
</body>
</html>