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

import java.io.FileNotFoundException;

public class QueryException extends FileNotFoundException {
  public QueryException(String message) {
    super(message);
  }
}

// arch-tag: a97528d6-e344-4867-9ecc-9ea266de3082
