/**
 *(C) Copyright 2015 Hewlett-Packard Development Company, L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trafodion.dcs.serverna;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.trafodion.dcs.tmpl.serverna.ServerStatusTmpl;

public class ServerStatusServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException
    {
    DcsServer server = (DcsServer)getServletContext().getAttribute(
        DcsServer.SERVER);
    assert server != null : "No SERVER in context!";
    
    resp.setContentType("text/html");
    ServerStatusTmpl tmpl = new ServerStatusTmpl();
    if (req.getParameter("format") != null)
      tmpl.setFormat(req.getParameter("format"));
    if (req.getParameter("filter") != null)
      tmpl.setFilter(req.getParameter("filter"));
    tmpl.render(resp.getWriter(), server);
  }

}
