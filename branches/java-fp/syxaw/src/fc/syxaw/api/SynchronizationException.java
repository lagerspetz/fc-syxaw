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

// $Id: SynchronizationException.java,v 1.6 2004/11/19 09:31:08 ctl Exp $

package fc.syxaw.api;

import java.io.IOException;

/** This exception signals that synchronization has failed. */

public class SynchronizationException extends IOException {

  private int status = StatusCodes.NO_STATUS;

  /** Create a new exception. */
  public SynchronizationException() {
    super();
  }

  /** Create a new exception.
   * @param message reason for exception
   */

  public SynchronizationException(String message) {
    super(message);
  }

  /** Create a new exception.
   * @param aStatus Synchronization status code. See {@link StatusCodes}
   */

  public SynchronizationException( int aStatus) {
    this("",aStatus);
  }

  /** Create a new exception.
   * @param message reason for exception
   * @param aStatus Synchronization status code. See {@link StatusCodes}
   */

  public SynchronizationException(String message, int aStatus) {
    super(message+" (Code "+aStatus+")");
    status = aStatus;
  }

  /** Get status code for the exception.
   * @see StatusCodes
   */

  public int getStatus() {
    return status;
  }
}
// arch-tag: 8f03a64a864f585c1c68b282f9ac3122 *-
