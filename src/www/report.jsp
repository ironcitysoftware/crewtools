<%@page language="java"
        import="crewtools.rpc.Proto.ReportRequest"
        import="crewtools.rpc.Proto.ReportResponse"
        import="crewtools.rpc.Proto.ReportRequest.RequestType"
        import="crewtools.flica.AwardDomicile"
        import="crewtools.flica.report.ReportServer"
        import="java.io.OutputStream"
        import="java.net.InetAddress"
        import="java.net.Socket"
        import="java.net.ConnectException"
        import="java.util.Arrays"
        import="java.util.List"
        import="org.joda.time.DateTime"
%>
<%
final int TIMEOUT_MILLISECONDS = 300 * 1000;

if (request.getScheme().equals("http")) {
  String old = request.getRequestURL().toString();
  response.sendRedirect(old.replace("http", "https"));
  return;
}
%>
<html>
<head>
<title>Reports</title>
<meta name="google" content="notranslate">
<style>
html, body {
  margin: 0;
  padding: 0;
  font-family: "Trebuchet MS, Helvetica, sans-serif";
}
table {
  border-collapse: collapse;
}
table td {
  padding: 0.5em;
  border: 1px solid black;
  vertical-align: top;
}
table th {
  padding: 0.5em;
  border: 1px solid black;
  vertical-align: top;
}
td.yellow {
  background-color: yellow;
}
td {
  text-align: right;
}
td.wide {
  max-width: 200px;
}
label {
  font-weight: bold;
}
label, select {
  display: inline-block;
  vertical-align: top;
}
</style>
</head>
<body>

<h3>Reports</h3>

<form method=POST>
  <label>Flica username:</label> <input type="text" name="flica_username" value=""><br />
  <label>Flica password:</label> <input type="password" name="flica_password" value=""><br />
  <label>Seat</label>
  <select name="rank">
  <%
  String selectedRank = request.getParameter("rank");
  if (selectedRank == null) {
    selectedRank = "";
  }
  %>
  <option value="FIRST_OFFICER" <%= selectedRank.equals("FIRST_OFFICER") ? "selected" : "" %>>First Officer</option>
  <option value="CAPTAIN" <%= selectedRank.equals("CAPTAIN") ? "selected" : "" %>>Captain</option>
  </select><br />
  <label>Report</label>
  <select name="report">
  <option value="OpenDutyPeriodDiscrepancyReport">Open Duty Period Discrepancy Report</option>
  </select><br />
  <label>Date</label>
  <select name="yearMonth">
  <option value="2019-02">2019-02</option>
  </select><br />
  <label>Domiciles</label>
  <select multiple size="<%= AwardDomicile.values().length + 1 %>" name="awardDomiciles">
  <%
    String[] selectedDomicileArray = request.getParameterValues("awardDomiciles");
    if (selectedDomicileArray == null) {
      selectedDomicileArray = new String[] { "CLT" };
    }
    List<String> selectedDomiciles = Arrays.asList(selectedDomicileArray);
    // TODO when clicking on or off 'ALL', adjust selection as necessary.
  %>
  <option value="ALL" <%= selectedDomiciles.contains("ALL") ? "selected" : "" %>>All domiciles</option>
  <%
    for (AwardDomicile awardDomicile : AwardDomicile.values()) {
      boolean isSelected = selectedDomiciles.contains(awardDomicile.name());
      out.write(String.format("<option value=\"%s\"%s>%s</option>\n",
          awardDomicile.name(),
          isSelected ? " selected" : "",
          awardDomicile.name()));
    }
  %>
  </select>
  <br />
  <input type="submit" name="Generate Report"/>
  <p>This report can take a few minutes, please be patient.
</form>

<%
if (request.getParameter("flica_username") != null && !request.getParameter("flica_username").isEmpty()
  && request.getParameter("flica_password") != null && !request.getParameter("flica_password").isEmpty()) {
  ReportRequest.Builder builder = ReportRequest.newBuilder();
  builder.setFlicaUsername(request.getParameter("flica_username"));
  builder.setFlicaPassword(request.getParameter("flica_password"));
  builder.setYearMonth(request.getParameter("yearMonth"));
  builder.setRank(request.getParameter("rank"));
  for (String awardDomicile : request.getParameterValues("awardDomiciles")) {
    if (!awardDomicile.equals("ALL")) {
      builder.addAwardDomicile(awardDomicile);
    }
  }
  builder.setRequestType(RequestType.OPEN_DUTY_PERIOD_DISCREPANCY_REPORT);
  String errorMessage = null;
  Socket socket = null;
  ReportResponse reportResponse = null;
  try {
    socket = new Socket(InetAddress.getLocalHost(), ReportServer.PORT);
    OutputStream output = socket.getOutputStream();
    builder.build().writeTo(output);
    socket.shutdownOutput();
    socket.setSoTimeout(TIMEOUT_MILLISECONDS);
    reportResponse = ReportResponse.parseFrom(socket.getInputStream());
  } catch (ConnectException e) {
    errorMessage = "Report generator not running.";
  } finally {
    if (socket != null) {
      socket.close();
    }
  }
  if (errorMessage != null) {
    out.write("Error: " + errorMessage);
  } else if (reportResponse.hasError()) {
    out.write("Error: " + reportResponse.getError());
  } else {
    out.write(reportResponse.getHtml());
  }
} else if (request.getMethod().equals("POST")) {
  out.write("Error: need a Flica username and password");
}
%>
</body>
</html>
