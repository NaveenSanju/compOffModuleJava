<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Insert title here</title>
</head>
<body>
<form action="loginAuth.action" method="post">
	   <label for="loginId">Email or login ID <span class="req">*</span></label>
        <input type="text" id="loginId" name="userName" maxlength="150" autocomplete="username" >
        <br>
        <label for="password">Password <span class="req">*</span></label>
        <input type="password" id="password" name="password" maxlength="100" autocomplete="current-password"><br>
        <input type="submit" value="Login"/>

</form>
</body>
</html>