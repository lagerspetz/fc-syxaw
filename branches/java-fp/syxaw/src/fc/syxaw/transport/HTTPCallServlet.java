/*
 * Copyright 2005--2008 Helsinki Institute for Information Technology
 *
 * This file is a part of Fuego middleware.  Fuego middleware is free
 * software; you can redistribute it and/or modify it under the terms
 * of the MIT license, included as the file MIT-LICENSE in the Fuego
 * middleware source distribution.  If you did not receive the MIT
 * license with the distribution, write to the Fuego Core project at
 * fuego-core-users@googlegroups.com.
 */

// $Id: HTTPCallServlet.java,v 1.3 2003/10/15 07:29:00 ctl Exp $

package fc.syxaw.transport;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet responding to call requests on a HTTP synchronous call channel.
 * Forwards the
 * requests for processing to {@link HTTPCallChannel}.
 */

public class HTTPCallServlet extends HttpServlet {

  /** Construct servlet. */
  public HTTPCallServlet() {
  }

  protected void doGet(HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    HTTPCallServer.service("GET",httpServletRequest,httpServletResponse);
  }

  protected void doHead(HttpServletRequest httpServletRequest,
                        HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    HTTPCallServer.service("HEAD",httpServletRequest,httpServletResponse);
  }

  protected void doPost(HttpServletRequest httpServletRequest,
                        HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    HTTPCallServer.service("POST",httpServletRequest,httpServletResponse);
  }

  protected void doPut(HttpServletRequest httpServletRequest,
                       HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    HTTPCallServer.service("PUT",httpServletRequest,httpServletResponse);
  }

  protected void doDelete(HttpServletRequest httpServletRequest,
                          HttpServletResponse httpServletResponse)
      throws ServletException, IOException {
    HTTPCallServer.service("DELETE",httpServletRequest,httpServletResponse);
  }
}// arch-tag: e157e53bd049c426c1591f76cbfb72dc *-
