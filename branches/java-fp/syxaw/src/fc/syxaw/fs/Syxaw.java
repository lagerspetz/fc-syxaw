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

// $Id: Syxaw.java,v 1.30 2005/06/07 13:07:02 ctl Exp $
package fc.syxaw.fs;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import fc.syxaw.proto.RPC;
import fc.syxaw.storage.hfsbase.ServerConfig;
import fc.syxaw.tool.PipeCLIServer;
import fc.syxaw.tool.RendezvuousCLI;
import fc.syxaw.tool.SocketCLIServer;
import fc.syxaw.tool.Toolkit;
import fc.syxaw.util.Util;
import fc.util.IOUtil;
import fc.util.log.Log;
import fc.util.log.StreamLogger;

/** Syxaw startup class. Run the Java VM with this class to start Syxaw.
 *
 */

public class Syxaw /*implements StorageProvider statically*/ {

  protected static final StorageProvider sp;
  protected static final LockManager lm=new LockManager();

  static {
    StorageProvider temp=null;
    try {
      temp = (StorageProvider) Class.forName(Config.STORAGE_PROVIDER).newInstance();
    } catch (Exception ex) {
      Log.log("Cannot init storage provider " + Config.STORAGE_PROVIDER,
              Log.FATALERROR, ex);
    }
    sp=temp; // Use tempvar to avoid compiler complaints
  }
  /** Start Syxaw. Takes one optional command line argument, which is the
   * name of a configuration file, formatted as required by
   * <code>java.util.Properties</code>. The configuration file is used to
   * initialize the Java system properties, and may contain any Syxaw
   * configuration setting that is normally read from the system properties.
   */
  public static void main(String[] args) {
    
    String logfile = System.getProperty("fc.log.file",
        System.getProperty("syxaw.log.file"));
    PrintStream logstream = System.out;
    try {
      if( logfile != null )
        logstream =  new PrintStream(new FileOutputStream(logfile));
    } catch (IOException e) {
      System.err.println("Unable to open log stream to "+logfile);
    }
    fc.util.log.Log.setLogger(new StreamLogger(logstream));
    fc.syxaw.util.Log.time("Foo");
    if( args.length == 1 ) {
      Log.log("Loading configuration "+args[0],Log.INFO);
      try {
        Util.loadConfiguration(args[0]);
      }
      catch (IOException ex) {
        Log.log("Configuration loading failed.",Log.ERROR,ex);
      }
    }
    if( "doit".equals(System.getProperty("syxaw.mrproper")) ) {
      try {
        Log.log("Cleaning "+ServerConfig.ROOT_FOLDER_FILE,Log.INFO);
        IOUtil.delTree(ServerConfig.ROOT_FOLDER_FILE,false);
      } catch ( IOException ex ) {
        Log.log("Folder cleanup failed",Log.FATALERROR);
      }
      System.setProperty("syxaw.initfs","true");
    }

    if( Config.STARTUP_CLEANFS ) {
      getToolkit().clean(false);
      if( !Config.STARTUP_INITFS ) {
        Log.log("File system cleaned, but not selected for init -> I'm done.",
                Log.INFO);
        System.exit(0);
      }
    }

    if( Config.STARTUP_INITFS )
      getToolkit().init(false);

    if( Config.STARTUP_CHECKFS )
      getToolkit().check(true, false);

    // Test access to the fs
    getFile(fc.syxaw.storage.hfsbase.ServerConfig.ROOT_FOLDER);

    /*if( Config.STARTUP_NFSD ) {
        (new Thread(new Runnable() {
            public void run() {
                fc.dessy.jnfsd.mainline.main(new String[] {});
            }
        })).start();
    }*/
    if( Config.STARTUP_SYNCD )
      (new Thread(new SynchronizationServer(getLockManager()))).start();

    if( Config.STARTUP_RMIAPI ) {
      Log.log("RMI API",Log.ERROR);
    }

    if( false ) {
      (RPC.getInstance().new FindResponder()).start();
    }

    if( Config.STARTUP_CLISERVER || Config.CLISERVER_TYPE != null ) {
      //Log.log("CLI type is: "+Config.CLISERVER_TYPE,Log.INFO);
      if( "tcp".equalsIgnoreCase(Config.CLISERVER_TYPE) ||
          Config.CLISERVER_TYPE == null )
        SocketCLIServer.getInstance().start();
      else if ("pipe".equalsIgnoreCase(Config.CLISERVER_TYPE) )
        (new PipeCLIServer()).start(System.in,System.out);
      else if ("rdz".equalsIgnoreCase(Config.CLISERVER_TYPE) )
        RendezvuousCLI.getInstance().start();
      else
        Log.log("Unknown CLI type: "+Config.CLISERVER_TYPE,Log.FATALERROR);

    }
    //try {Thread.sleep(1000); } catch (InterruptedException ex) {}
    //SynchronizationEngine.main(null);
  }

  /** Get file by hierarchical name. Uses the configured storage
   * provider.
   *
   * @param parent parent file
   * @param child name of child file
   * @return file
   */

  public static fc.syxaw.hierfs.SyxawFile getFile(SyxawFile parent, String child) {
    return sp.getFile(parent,child);
  }

  /** Get file by hierarchical name. Uses the configured storage
   * provider.
   *
   * @param pathName pathname of file
   * @return SyxawFile
   */

  public static fc.syxaw.hierfs.SyxawFile getFile(String pathName) {
    return sp.getFile(pathName);
  }

  /** Get file by UID. Uses the configured storage
   * provider.
   *
   * @param uid UID of file
   * @return file
   */
  public static SyxawFile getFile(UID uid) {
    return sp.getFile(uid);
  }

  /** Get toolkit for the storage provider. Uses the configured storage
   * provider.
   *
   * @return toolkit
   */

  public static Toolkit getToolkit() {
    return sp.getToolkit();
  }
  
  /** Get toolkit for the storage provider. Uses the configured storage
   * provider.
   *
   * @return The storage provider currently used.
   */

  public static StorageProvider getStorageProvider() {
    return sp;
  }

  /** Get global lock manager.
   *
   * @return LockManager
   */
  public static LockManager getLockManager() {
    return lm;
  }
}
// arch-tag: bb056e0a490f854fdfb2978c7f4fde7f *-

