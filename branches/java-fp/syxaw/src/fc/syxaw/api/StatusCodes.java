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

// $Id: StatusCodes.java,v 1.6 2004/11/19 09:31:08 ctl Exp $

package fc.syxaw.api;

/** Synchronization status codes. The numerical value of the code is
 * &gt;0 for warnings, &lt;0 for errors and 0 for the <code>OK</code>
 * status code .*/

// Status codes. 0 ok, > 0 warning, <0 error
public interface StatusCodes {

  /** The file is not in conflict */
  public static final int NO_CONFLICT = 3;
  /** OK */
  public static final int OK = 0;
  /** The file was not found. */
  public static final int NOT_FOUND = -404;

  /** Batch request OK, see individual statuses for each object. */
  public static final int SEE_SUBSTATUSES = 100;

  /** A conflict occurred while synchronizing the file. */
  public static final int CONFLICT = -3;

  /** Unspecified error. */
  public static final int ERROR = -1;

  /** Operation failed due to expired softlock. */
  public static final int LOCK_EXPIRED = -4;

  /** No status available. */
  public static final int NO_STATUS = Integer.MIN_VALUE;
}
// arch-tag: afb24662a1517b5be3ea9c92f117e701 *-
