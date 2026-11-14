<%-- Comp Off cards for the Leave page, for the logged-in user (Staff / Team Leader, Manager / Admin), from the session
     (staffID, companyID), the user type and Desktop App Settings: com.action.CompOffLinks.
     Copy-and-paste friendly: no Struts tags, its own styles, works on any page of the application.
     Use it with one line where the cards should appear:
         <%@ include file="/contentpages/compoff/layout/leaveLinks.jsp" %>
     A link switched off by Desktop App Settings is shown grey and not clickable. --%>
<%
  com.action.CompOffLinks coLinks = com.action.CompOffLinks.of(request);
  String coCtx = request.getContextPath();
%>
<style>
  .co-cards { font-family: var(--co-brand-font, inherit); }
  .co-card { border: 1px solid #ddd; border-radius: 3px; background: #fff; margin: 0 0 34px; break-inside: avoid; display: inline-block; width: 100%; vertical-align: top; }
  .co-card h4 { margin: 0; background: #e5e5e5; color: #1d5d50; font-size: 14.5px; font-weight: 700; padding: 12px 20px; }
  .co-card ul { list-style: none; margin: 0; padding: 8px 24px 6px; }
  .co-card li { border-bottom: 1px dotted #bbb; padding: 8px 0 8px 15px; font-size: 12.5px; position: relative; }
  .co-card li:last-child { border-bottom: 0; }
  .co-card li:before { content: "\00BB"; position: absolute; left: 0; top: 8px; color: #777; }
  .co-card li a { color: #1d4f45; text-decoration: none; }
  .co-card li a:hover { text-decoration: underline; }
  .co-card li .co-off { color: #999; cursor: default; }
</style>
<% if (coLinks != null) { %>
<div class="co-cards">
<% if (coLinks.isEmployee()) { %>
  <div class="co-card" id="co-card-request"><h4>Comp off request</h4><ul>
    <li><% if (coLinks.isCompOffRequestOn()) { %><a href="<%= coCtx %>/applyCompOff1.action">Apply comp off</a><% } else { %><span class="co-off" title="Comp Off requests are currently disabled">Apply comp off</span><% } %></li>
    <li><a href="<%= coCtx %>/compOffBalance1.action">My comp off balance</a></li>
  </ul></div>
  <div class="co-card" id="co-card-cancel"><h4>Cancel comp off request</h4><ul>
    <li><a href="<%= coCtx %>/cancelCompOffRequest1.action">Cancel comp off request</a></li>
  </ul></div>
  <div class="co-card" id="co-card-myreport"><h4>My comp off report</h4><ul>
    <li><a href="<%= coCtx %>/compOffDayView1.action">Comp off day view</a></li>
    <li><a href="<%= coCtx %>/compOffMonthView1.action">Comp off month view</a></li>
  </ul></div>
  <div class="co-card" id="co-card-holiday"><h4>Holiday Intimation</h4><ul>
    <li><% if (coLinks.isHolidayRequestOn()) { %><a href="<%= coCtx %>/holidayIntimation1.action">Holiday Intimation</a><% } else { %><span class="co-off" title="Holiday Intimation requests are currently disabled">Holiday Intimation</span><% } %></li>
    <li><a href="<%= coCtx %>/cancelHolidayIntimation1.action">Cancel Holiday Intimation</a></li>
  </ul></div>
<% } %>
<% if (coLinks.isTeam()) { %>
  <div class="co-card" id="co-card-team"><h4>My team report</h4><ul>
    <li><a href="<%= coCtx %>/teamCompOffMonthView1.action">Team comp off month view</a></li>
  </ul></div>
<% } %>
<% if (coLinks.isHolidayApproval()) { %>
  <div class="co-card" id="co-card-holidayapproval"><h4>Holiday Intimation approvals</h4><ul>
    <li><% if (coLinks.isHolidayApprovalOn()) { %><a href="<%= coCtx %>/approveHolidayWorking1.action">Approve or reject - Holiday Intimation</a><% } else { %><span class="co-off" title="Holiday Intimation approvals are currently disabled">Approve or reject - Holiday Intimation</span><% } %></li>
  </ul></div>
<% } %>
<% if (coLinks.isCompOffApproval()) { %>
  <div class="co-card" id="co-card-compoffapproval"><h4>Comp off approvals</h4><ul>
    <li><% if (coLinks.isCompOffApprovalOn()) { %><a href="<%= coCtx %>/approveCompOff1.action">Approve or reject - Comp off request</a><% } else { %><span class="co-off" title="Comp Off approvals are currently disabled">Approve or reject - Comp off request</span><% } %></li>
  </ul></div>
<% } %>
<% if (coLinks.isAdmin()) { %>
  <div class="co-card" id="co-card-reports"><h4>Comp off reports</h4><ul>
    <li><a href="<%= coCtx %>/compOffDetailsReport1.action">Comp off details report</a></li>
    <li><a href="<%= coCtx %>/compOffBalanceReport1.action">Comp off balance and details report</a></li>
    <li><a href="<%= coCtx %>/holidayViewReport1.action">Holiday view report</a></li>
    <li><a href="<%= coCtx %>/compOffViewReport1.action">Comp off view report</a></li>
    <li><a href="<%= coCtx %>/compOffHistoryReport1.action">Comp off history</a></li>
    <li><a href="<%= coCtx %>/holidayUsageReport1.action">Holiday Intimation usage report</a></li>
  </ul></div>
  <div class="co-card" id="co-card-settings"><h4>Comp off settings</h4><ul>
    <li><a href="<%= coCtx %>/approverManagement1.action">Approver management</a></li>
    <li><a href="<%= coCtx %>/generalSettings1.action">General settings</a></li>
    <li><a href="<%= coCtx %>/compOffProcessing1.action">Comp off processing</a></li>
    <li><a href="<%= coCtx %>/adminApplyCompOff1.action">Apply comp off for staff</a></li>
    <li><a href="<%= coCtx %>/compOffCancel1.action">Comp off cancel</a></li>
  </ul></div>
<% } %>
</div>
<% } %>
