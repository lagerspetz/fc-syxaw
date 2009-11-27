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

// $Id: ServerConfig.java,v 1.7 2005/04/28 14:00:53 ctl Exp $

package fc.syxaw.storage.hfsbase;

import java.io.File;

import fc.syxaw.api.ISyxawFile;
import fc.util.log.Log;


/** Configuration parameters for Syxaw
 * implemented on top of an underlying hierarchical file system. */

public class ServerConfig extends Config {

  protected ServerConfig() {} // Ensure singeltoness

  /** Directory on the local file system which is the root directory of the
   * Syxaw file system. The directory is relative to the current working directory.
   * The value is read from the system property
   * <code>syxaw.rootfolder</code>.
   * Default value is
   * <code>image</code>. All other configured paths are relative to this directory.
 */

  public static final String ROOT_FOLDER;

  /** Debug: name of metadb dump file. Relative to the Syxaw system folder. */
  public static final String SAVE_METADB_NAME =
       System.getProperty("syxaw.debug.metadb",
                          "debug-syxaw-saved-metadb.txt");

  /** Debug: name of file to restore metadb from. If set, initfs tries to
   * restore the metadata db from this file. */
  public static final String RESTORE_METDADB =
       System.getProperty("syxaw.debug.restoremetadb", null);


  public static final boolean CALC_DIRMOD =
          (new Boolean(System.getProperty("syxaw.debug.calcdmod",
                                               "true"))).booleanValue();


  public static final boolean BATCH_SYNC =
    (new Boolean(System.getProperty("syxaw.debug.batchsync",
                                         "true"))).booleanValue();

  static {
    // Ensure rf NEVER ends in slash
    String rf =
      System.getProperty("syxaw.rootfolder","image");
    if( rf.endsWith(ISyxawFile.separator) )
      rf = rf.substring(0,rf.length()-1);
    String rootFolder=(new File(rf)).getAbsolutePath();
    // BUGFIX-20061019-1: trailing /. messed up check for file inside Syxaw root
    while(rootFolder.endsWith(File.separator+".")) {
      // Strip any /./. 
      rootFolder = rootFolder.substring(0,rootFolder.length()-2);
    }
    ROOT_FOLDER=rootFolder;
    if( !(new File(ROOT_FOLDER)).exists() )
      Log.log("Cannot find Syxaw FS image folder "+ROOT_FOLDER,Log.FATALERROR);
    if( CASE_INSENSITIVE_HOSTFS )
      Log.log("Hostfs is case insensitive",Log.INFO);
    if( CALC_DIRMOD && fc.syxaw.fs.Config.DEBUG_DIRMODFLAG )
      Log.log("Can't both calc and hard-set dirmod!",Log.FATALERROR);
  }

  // Convenience file object
  public static final File ROOT_FOLDER_FILE = new File(ROOT_FOLDER).
                                              getAbsoluteFile();

}
// arch-tag: a5e025ebc7cb87a5495624d36f00aa71 *-
