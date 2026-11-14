<%-- Comp Off pages: the end of <body>. Included by every Comp Off page right before </body>. --%>
<%-- ===== YOUR APPLICATION'S FOOTER: replace from here ... ===== --%>
<div class="co-footer">Powered by <span class="g1">G</span><span class="g2">o</span><span class="g3">o</span><span class="g1">g</span><span class="g4">l</span><span class="g2">e</span> app engine. Copyright &copy; Handdy.com</div>
<%-- ===== ... to here ===== --%>
<div class="modal" id="co-modal"><div class="box" id="co-modal-box" role="dialog" aria-modal="true"></div></div>
<div id="co-server-errors" hidden><s:iterator value="actionErrors"><s:property/> </s:iterator></div>
<% com.util.CompanyContext.clear(); %><%-- the request's company (set by CompOffAction.access) ends with the page --%>
<%-- Every JS file of the module --%>
<script src="<s:url value='/js/compoff.js'/>"></script>
