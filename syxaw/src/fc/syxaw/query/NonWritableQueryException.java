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

public class NonWritableQueryException extends QueryException {

  public NonWritableQueryException() {
    super("Query is not writable");
  }
}

// arch-tag: 44b3af15-558c-489b-91ee-1b99b90ba885
