<%@ page import="javax.ws.rs.core.*" language="java" contentType="text/html; charset=ISO-8859-1"
 pageEncoding="ISO-8859-1"%>
<html>
<head>
    <title>Customer Admin Iterface</title>
</head>
<body bgcolor="#E3F6CE">
<%
      String logoutUri = UriBuilder.fromUri("http://localhost:8080/auth-server/rest/realms/demo/tokens/logout")
                                     .queryParam("redirect_uri", "http://localhost:8080/customer-portal").build().toString();
%>
<p><a href="<%=logoutUri%>">logout</a></p>
<h1>Customer Admin Interface</h1>
User <b><%=request.getUserPrincipal().getName()%></b> made this request.
</body>
</html>