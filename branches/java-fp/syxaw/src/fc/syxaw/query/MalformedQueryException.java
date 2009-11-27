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

package fc.syxaw.query;

public class MalformedQueryException extends QueryException {

  public MalformedQueryException(String query) {
    super("Malformed query: "+query);
  }
}

// arch-tag: 7caed5b4-66e5-4d3d-9f56-0db272cb0e9e
