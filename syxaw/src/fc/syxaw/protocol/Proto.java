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

package fc.syxaw.protocol;

import java.io.Serializable;

public class Proto {
  public interface Request {}

  public static class FindReq implements Serializable, Request {
    public String guid;
    public String replyTo;
    public FindReq() {
    }

    public FindReq(String guid, String replyTo) {
      this.guid = guid;
      this.replyTo = replyTo;
    }

  }

  public static class FindRep implements Serializable {
    public String device;
    public String branches;
    public String fdest;

    public FindRep() {
    }

    public FindRep(String device,String branch, String fdest) {
      this.device = device;
      this.branches = branch;
      this.fdest = fdest;
    }

    public String toString() {
      return "{dev="+device+",branch="+branches+"}";
    }
  }
}

// arch-tag: 879c8d52-4d3b-4041-bad8-f25a50565746
