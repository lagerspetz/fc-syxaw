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

package fc.syxaw.fs;

import fc.syxaw.api.ISyxawFile;
import fc.syxaw.util.Util;

public class QueriedUID extends UID {

  private boolean isDBO = false;
  private String query;

  public String getQuery() {
    return query;
  }

  /* Hiding this is not entirely working, since e.g. the fs wants to use the
  // uid portion only*/
  public String toBase64() {
    if( Util.isEmpty(query) )
      return (isDBO ? "" : super.toBase64());
    else
      throw new UnsupportedOperationException();
  }

  public String toBase64Q() {
    if( query == null )
      return (isDBO ? "" : super.toBase64());
    else
      return (isDBO ? "" : super.toBase64())+ISyxawFile.querySeparator+query;
  }

  public UID getUid() {
    return isDBO ? DBO : (new UID()).init(getBytes());
  }

  public static QueriedUID createFromBase64Q(String uidq) {
    int qpos = uidq.indexOf(ISyxawFile.querySeparatorChar);
    QueriedUID u = new QueriedUID();
    String base64m = qpos > -1 ? uidq.substring(0,qpos) : uidq;
    if( base64m.length() > 0 )
      u.init( base64m, false);
    else
      u.isDBO = true;
    u.query = qpos > -1 ? uidq.substring(qpos+1) : null;
    return u;
  }

  public String toString() {
    return toBase64Q();
  }
}

// arch-tag: 463a30cd-cd2e-41ee-842a-0e26e05f0857
