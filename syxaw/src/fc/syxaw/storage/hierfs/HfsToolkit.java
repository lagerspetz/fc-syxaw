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

// $Id: HfsToolkit.java,v 1.4 2005/06/08 12:46:12 ctl Exp $
package fc.syxaw.storage.hierfs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;

import fc.syxaw.storage.hfsbase.ServerConfig;
import fc.syxaw.storage.hfsbase.Toolkit;
import fc.syxaw.util.Util;
import fc.util.IOUtil;
import fc.util.log.Log;

public class HfsToolkit extends Toolkit {


  public static final File UIDDB = new File(SYSTEM_FOLDER_FILE, "uiddb");
  public static final File METADB = new File(SYSTEM_FOLDER_FILE, "metadb");
  public static final File METADB_SAVEFILE =
          new File(SYSTEM_FOLDER_FILE, ServerConfig.SAVE_METADB_NAME);
  
  public static final String DIRUID_FILE = ".syxaw-uid";

  static final FilenameFilter NO_DOT_SYXAW_PFX =
      new java.io.
             FilenameFilter() {
        public boolean accept(File f, String s) {
          if (s.startsWith(fc.syxaw.storage.hfsbase.Config.SYXAW_PREFIX))
            return false;
          return true;
        }
      };

  public HfsToolkit() {
    super("fc.syxaw.storage.hierfs.FileSystem");
  }

  public void init(boolean verbose) {
    super.init(verbose);
    if (!UIDDB.exists() &&
        !UIDDB.mkdir())
      Log.log("Can't create index " + UIDDB, Log.FATALERROR);

    if (!METADB.exists() &&
        !METADB.mkdir())
      Log.log("Can't create index " + METADB, Log.FATALERROR);
  }

  public int check(boolean repair, boolean verbose) {
    return 0;
  }

  public void clean(boolean verbose) {
    // Clean system folder
    if (SYSTEM_FOLDER_FILE.exists()) {
      try {
        IOUtil.delTree(SYSTEM_FOLDER_FILE, true);
        Log.log("Cleaned " + SYSTEM_FOLDER_FILE, Log.INFO);
      } catch (IOException x) {
        Log.log("Cannot clean " + SYSTEM_FOLDER_FILE, Log.FATALERROR, x);
      }
    }
    // Clean .syxaw-uids
    try {
      IOUtil.delTree( ServerConfig.ROOT_FOLDER_FILE, false,
                   new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.equals(DIRUID_FILE);
        }
      });
      Log.log("Cleaned " + DIRUID_FILE + " from " +
              ServerConfig.ROOT_FOLDER_FILE, Log.INFO);
    } catch (IOException x) {
      Log.log("Cannot clean " + SYSTEM_FOLDER_FILE, Log.FATALERROR, x);
    }
  }

  public void checkpoint() {
    try {
      File df = new File( SYSTEM_FOLDER_FILE,ServerConfig.SAVE_METADB_NAME);
      Log.log("Checkopint reached. Dumping meta db to "+df+"...",Log.INFO);
      PrintWriter pw = new PrintWriter(new FileOutputStream(df));
      ((FileSystem) getFs()).dumpMetaDb(ServerConfig.ROOT_FOLDER_FILE,pw);
      pw.close();
      Log.log("Dump done.",Log.INFO);
    } catch (IOException x) {
      Log.log("Exception dumping metadb",Log.FATALERROR,x);
    }
  }
}
// arch-tag: 1ed37d37bc6b336ed22b0012b4e5ad04 *-
