<%@ include file="/include-internal.jsp"%>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<jsp:useBean id="buildForm" type="jetbrains.buildServer.controllers.admin.projects.BuildTypeForm" scope="request"/>
<jsp:useBean id="bean" class="jetbrains.buildServer.runAs.server.RunAsBean"/>

<style type="text/css">
</style>

<tr>
  <td colspan="2"><em>This build feature allows to run build steps under the specified user account.</em></td>
</tr>

<tr>
  <th><label for="${bean.runAsUserKey}">User name: <l:star/></label></th>
  <td>
    <div class="completionIconWrapper">
      <props:textProperty name="${bean.runAsUserKey}" className="longField"/>
    </div>
    <span class="error" id="error_${bean.runAsUserKey}"></span>
    <span class="smallNote">Specify the user name in the formats "username" or "doman\username" or "username@domain"</span>
  </td>
</tr>

<tr>
  <th><label for="${bean.runAsPasswordKey}">Password: <l:star/></label></th>
  <td>
    <div class="completionIconWrapper">
      <props:passwordProperty name="${bean.runAsPasswordKey}" className="longField"/>
    </div>
    <span class="error" id="error_${bean.runAsPasswordKey}"></span>
  </td>
</tr>

<tr class="advancedSetting">
  <th><label for="${bean.runAsNoninheritableEnvironmentVariablesKey}">Noninheritable environment variables:</label></th>
  <td><c:set var="note">Enter comma- or newline-separated names of environment variables which will not be inherited from agent's process. It is important to take into account that the space character is a meaningful symbol of the variable name.</c:set>
    <props:multilineProperty
      name="${bean.runAsNoninheritableEnvironmentVariablesKey}"
      className="longField"
      linkTitle=""
      rows="3"
      cols="49"
      expanded="${true}"
      note="${note}"
  /></td>
</tr>