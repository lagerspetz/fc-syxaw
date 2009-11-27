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

package fc.pp.syxaw;

import java.io.IOException;

import fc.syxaw.fs.Constants;
import fc.syxaw.storage.hfsbase.ServerConfig;
import fc.syxaw.tool.PipeCLIServer;
import fc.util.IOUtil;
import fc.util.log.Log;

/** Nokia 9500 Communicator Syxaw startup class. */

public class Syxaw {
  public static void main(String[] args) {
    boolean specialInit = System.getProperty("syxaw.n95init") != null;
    GUI g=new GUI();
    // Don't stress the ui subsystem too much ....
    try { Thread.sleep(1000); } catch ( InterruptedException ex ) {}
    if( specialInit ) {
      try {
        Log.log("Cleaning "+ServerConfig.ROOT_FOLDER_FILE,Log.INFO);
        IOUtil.delTree(ServerConfig.ROOT_FOLDER_FILE,false);
      } catch ( IOException ex ) {
        Log.log("Folder cleanup failed",Log.FATALERROR);
      }
      System.setProperty("syxaw.initfs","true");
    }
    fc.syxaw.fs.Syxaw.main(args);
    if( specialInit ) {
      try { Thread.sleep(2000); } catch ( InterruptedException ex ) {}
      if (!fc.syxaw.fs.Syxaw.getFile(ServerConfig.ROOT_FOLDER).setLink(
              "alchemy.hiit.fi/", new Integer(Constants.NO_VERSION),
                new Boolean(false),new Boolean(false), true, true))
        Log.log("Can't link root", Log.FATALERROR);
      Log.log("Created link, hope it sticks...",Log.INFO);
      try { Thread.sleep(2000); } catch ( InterruptedException ex ) {}
      //System.exit(-1);
    }
    // Try again, just to be sure!
    if (!fc.syxaw.fs.Syxaw.getFile(ServerConfig.ROOT_FOLDER).setLink(
            "alchemy.hiit.fi/", new Integer(Constants.NO_VERSION),
              new Boolean(false),new Boolean(false), true, true))
      Log.log("Can't link root-2", Log.FATALERROR);
    g.tabCLI.txtCLI.setText("--Started--");
    try {
      (new PipeCLIServer()).start(g.getCLIInputStream(), g.getCLIOutputStream());
    } catch (IOException ex) {
      //Log.log("Cant connect CLI pipes",Log.ERROR);
    }
  }
}

// arch-tag: 20050729172033ctl
