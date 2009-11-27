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

// $Id: Constants.java,v 1.21 2005/01/04 15:11:41 ctl Exp $
package fc.syxaw.fs;

/** General constants used by Syxaw */

public class Constants {

  /** Constant signifying the absence of an ID */
  public static final long NO_ID = -1L;

  // NOTE ABOUT VERSION CONSTANTS:
  // We assume NO_VERSION < ZERO_VERSION < FIRST_VERSION troughout the system!
  /** Constant signifying the absence of a version number. */
  public static final int NO_VERSION = -1; //Integer.MIN_VALUE;

  // Version used in the proto for relinked files; does fairly well in
  // combination with other kludges to get the files properly synced
  // However, a full implementation will need to take the vhist of
  // the re-linked object into account and go from to merge, encode deltas etc.

  public static final int FIXMEP_RELINK_VERSION = -1;

  /** Constant signifying the cuurent version. There are cases when the
   * current version hasn't been assigned a version number yet; in those
   * cases this constant may be used to retrieve it. */
  public static final int CURRENT_VERSION = -2; //Integer.MIN_VALUE;

  /** Constant signifying the "zeroth version". When an object is created,
   * and before it has any data, it is assigned this version number.
   */
  public static final int ZERO_VERSION = 0; // Version with 0 bytes

  /** Constant signifying the absence of a size value. */
  public static final int NO_SIZE = -1; //Integer.MIN_VALUE;

  /** First version number assigned to objects. */
  public static final int FIRST_VERSION =
      Integer.parseInt(System.getProperty("syxaw.firstversion","1000"));

  // Synchronous call names

  /** String identifier for download RPC. */
  public static final String SYNC_DOWNLOAD = "download";

  /** String identifier for upload RPC. */
  public static final String SYNC_UPLOAD = "upload";

  /** String identifier for create RPC. */
  public static final String SYNC_CREATE = "create";

  /** Syxaw version string. Current value is {@value}. */
  public static final String SYXAW_VERSION = "1.97";
}
// arch-tag: 16f869dc2736c4b6b6de287ae8dad1a8 *-
