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

// $Id: Config.java,v 1.4 2004/12/03 09:31:56 ctl Exp $
package fc.syxaw.tests;

import java.io.File;

import fc.util.log.Log;

/** Configuration parameters for tests. */

public class Config {

  /** Location on local file system where the local Syxaw file system is mounted.
   *  The value is read from
   * the system property <code>syxaw.test.localroot</code>. The default value
   * is <code>/mnt</code>
   */

  public static final String TEST_LOCALROOT =
      System.getProperty("syxaw.test.localroot","/mnt3" );

  /** Location on local file system where the remote Syxaw file system is mounted.
   *  The value is read from
   * the system property <code>syxaw.test.remoteroot</code>. The default value
   * is <code>/mnt2</code>
   */

  public static final String TEST_REMOTEROOT =
      System.getProperty("syxaw.test.remoteroot","/mnt4" );

  static final File TEST_LOCALROOT_FILE;
  static final File TEST_REMOTEROOT_FILE;

  static {
    File f = null;
    if( TEST_LOCALROOT != null ) {
      f = new File(TEST_LOCALROOT);
      if (!f.exists() || !f.isDirectory())
        f = null;
    }
    if( f == null )
      Log.log("Unable to locate test localroot = "+TEST_LOCALROOT,Log.WARNING);
    TEST_LOCALROOT_FILE = f;
    if( TEST_REMOTEROOT != null ) {
      f = new File(TEST_REMOTEROOT);
      if (!f.exists() || !f.isDirectory())
        f = null;
    }
    if( f == null )
      Log.log("Unable to locate test remoteroot = "+TEST_REMOTEROOT,Log.WARNING);
    TEST_REMOTEROOT_FILE = f;
  }

}
// arch-tag: f818ae2d45a617778565d09ac42d99e4 *-
