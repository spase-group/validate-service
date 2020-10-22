<jsp:useBean id="validate" class="org.spase.web.Validator" scope="page" />
<%
	validate.loadOptions(request);
	validate.setXsdUrl("https://spase-group.org/data/schema/spase-" + validate.getVersionName() + ".xsd");

// if(request.getParameter("submit") != null) {
if(validate.isReady()) {
	validate.setWriter(out);
	// validate.loadSchema(); 
	// validate.validate();
	validate.doAction();
} else {	// Show some examples
%>
  <p>Validator version: <% validate.getVersion(); %></p>
  <p>Insufficient arguments.</p> 
  <p><dt>Parameters:</dt>
      <dd><b>url</b>: URL to the SPASE description to validate.</dd>
      <dd><b>version</b>: SPASE schema version to valid with.</dd>
      <dd><b>checkid</b>: Indicate that Resource ID references are to checked.</dd>
      <dd><b>checkurl</b>: Indicate that URLs are to checked.</dd>
   </p>
<%
}
%>
