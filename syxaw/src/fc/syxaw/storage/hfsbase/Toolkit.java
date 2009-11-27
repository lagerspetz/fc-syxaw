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

// $Id: Toolkit.java,v 1.1 2005/06/08 13:09:10 ctl Exp $
package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fc.syxaw.fs.GUID;
import fc.syxaw.fs.ObjectMerger;
import fc.syxaw.fs.ObjectProvider;

import fc.util.log.Log;
import fc.syxaw.util.Util;
import fc.xml.xas.XasUtil;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.model.StringKey;
import fc.syxaw.merge.PlaintextMerger;
import fc.syxaw.merge.XMLMerger;

/** Toolkit.
 */

public class Toolkit extends fc.syxaw.tool.Toolkit {

  protected static final StringKey MERGE_ROOT_ID = 
    StringKey.createKey( "syxaw.mergers" );

  private String fsClass;
  protected final ObjectProvider BASE_PROVIDER = new DefaultObjectProvider();
  
  public static final File SYSTEM_FOLDER_FILE =
          new File(ServerConfig.ROOT_FOLDER_FILE, Config.SYSTEM_FOLDER);

  public static final File INDEX_FOLDER_FILE = new File(SYSTEM_FOLDER_FILE,
                                                 Config.INDEX_FOLDER);

  public static final File DIRDB_FOLDER_FILE = new File(SYSTEM_FOLDER_FILE,
                                                 Config.DIRDB_FOLDER);

  public static final File REGISTRY_FOLDER_FILE =
          new File(SYSTEM_FOLDER_FILE,"registry");

  public static final File VERSION_FOLDER_FILE = new File(SYSTEM_FOLDER_FILE,
      Config.VERSION_FOLDER);

  public static final File CONFLICT_FOLDER_FILE = new File(SYSTEM_FOLDER_FILE,
      Config.CONFLICT_FOLDER);

  public static final File TEMP_FOLDER_FILE = new File(SYSTEM_FOLDER_FILE,
      "temp");

  public static final File SYXAW_DID_FILE = new File(SYSTEM_FOLDER_FILE,
      "..syxaw-did..");

  public static final File VERMAP_DB = new File(SYSTEM_FOLDER_FILE,
  "version-maps");
  
  
  protected Map mergerFactories = new HashMap();

  private static Toolkit instance =null;
  /*FIXME: temp public access*/ public Toolkit(String fsClass) {
    this.fsClass=fsClass;
    if( instance != null )
      Log.log("Singleton object",Log.ASSERTFAILED);
    instance=this;
  }

  public static Toolkit getInstance() {
    return instance;
  }

  /** Get registered merger for a MIME type.
   *
   * @param mimeType MIME type
   * @param linkFacet <code>true</code> to obtain link facet merger
   * @return merger class name
   */

  public String getMerger( String mimeType, boolean linkFacet ) {
    RefTreeNode n = getFs().getRegistry().getNode(
        StringKey.createKey(Toolkit.MERGE_ROOT_ID,"."+mimeType) );
    return n == null ? null:
        ((Registry.KeyValue) n.getContent()).getValue();
  }

  /** Get merger for a file.
   *
   * @param f SyxawFile to get merger for
   * @param linkFacet <code>true</code> to obtain link facet merger
   * @throws FileNotFoundException if an I/O error occurs
   * @return ObjectMerger for the file, or <code>null</code> if none.
   */
  public ObjectMerger getMerger(AbstractSyxawFile f, boolean linkFacet) throws
      FileNotFoundException {
    String key = f.getFullMetadata().getType();
    Object factory = mergerFactories.get(key);
    if( factory == null ) {
      RefTreeNode n = Util.isEmpty(key) ? null :
          getFs().getRegistry().getNode( StringKey.createKey( 
              Toolkit.MERGE_ROOT_ID,"."+key) );
      if( n != null ) {
        String factClass = ((Registry.KeyValue) n.getContent()).getValue();
        try {
	  factory = (ObjectMerger.MergerFactory) Class.forName(factClass).
              newInstance();
          Log.log("Merge factory "+factory+" for type "+key,Log.INFO);
          mergerFactories.put(key,factory);
        } catch (ClassCastException ex ) {
          Log.log("Merge factory " + factory +
                  " doesn't implement ObjectMerger.MergerFactory", Log.ERROR);
        } catch (ClassNotFoundException ex) {
          Log.log("Can't load merge factory " + factory + " for type " + key,
                  Log.ERROR);
        } catch (IllegalAccessException ex) {
          Log.log("Can't access merge factory " + factory + " for type " + key,
                  Log.ERROR, ex);
        } catch (InstantiationException ex) {
          Log.log("Can't create merge factory " + factory + " for type " + key,
                  Log.ERROR, ex);
        }
      }
    }
    return factory == null ? null :
        ((ObjectMerger.MergerFactory) factory).newMerger(f,linkFacet);
  }

  public void setMerger(String mimeType,
                        String mergerfactory) {
    Registry r = getFs().getRegistry();
    setMerger(r,mimeType,mergerfactory);
  }

  protected void setMerger(Registry r, String mimeType, String mergerfactory) {

    try {
      StringKey key = 
        StringKey.createKey(Toolkit.MERGE_ROOT_ID, "." + mimeType );
      Object content = new Registry.KeyValue(key, mergerfactory);
      if( r.getNode(key) != null ) {
        r.update(key,content);
        mergerFactories.remove(mimeType); // In case old one is cached
      } else
        r.insert(Toolkit.MERGE_ROOT_ID,key,content);
    } catch (NodeNotFoundException ex) {
      Log.log("Can't set merger",Log.ERROR);
    }

  }

  public int check(boolean repair, boolean verbose) {
    init(verbose);
    return 0;
  }

  public void clean(boolean verbose) {
    Log.log("NOP",Log.WARNING);
  }

/* Tree is
    .syxaw
      index
      dirdb
      versions
      conflicts
      registry

      */

  public void init(boolean verbose) {

    if (!SYSTEM_FOLDER_FILE.exists() &&
        !SYSTEM_FOLDER_FILE.mkdir())
      Log.log("Can't create system folder "+SYSTEM_FOLDER_FILE,Log.FATALERROR);
    // Init index folder
    if (!INDEX_FOLDER_FILE.exists() &&
        !INDEX_FOLDER_FILE.mkdir())
      Log.log("Can't create index folder " + INDEX_FOLDER_FILE, Log.FATALERROR);

    //Registry
    Registry.Maintenance.init(REGISTRY_FOLDER_FILE);
    Registry r = new Registry(REGISTRY_FOLDER_FILE); // Needed for further init

    // Init mergers
    initMerger(r);

    // Dirdb
    VersionedDirectoryTree.Maintenance.init(DIRDB_FOLDER_FILE);
    LinkTree.Maintenance.init(DIRDB_FOLDER_FILE);
    //None needed yet: LocalTree.Maintenance.init(DIRDB_FOLDER_FILE);

    // Versions
    VersionMapper.Maintenance.init(VERMAP_DB);
    VersionDB.Maintenance.init(VERSION_FOLDER_FILE);
    // Conflicts
    if (!CONFLICT_FOLDER_FILE.exists() &&
        !CONFLICT_FOLDER_FILE.mkdir())
      Log.log("Can't create conflict folder " + CONFLICT_FOLDER_FILE, Log.FATALERROR);


    // Temp folder
    if (!TEMP_FOLDER_FILE.exists() &&
        !TEMP_FOLDER_FILE.mkdir())
      Log.log("Can't create temporary folder " + TEMP_FOLDER_FILE, Log.FATALERROR);

    // Syxaw device id
    try {
      if( !SYXAW_DID_FILE.exists() && !SYXAW_DID_FILE.createNewFile() )
        throw new IOException();
      FileOutputStream os = new FileOutputStream(SYXAW_DID_FILE);
      os.write(GUID.getCurrentLocation().getBytes());
      os.close();
    } catch (IOException x) {
      Log.log("Can't create/write device ID tag file " + SYXAW_DID_FILE, Log.FATALERROR);
    }

    // Now, lock sysfolder
    /*if( !SYSTEM_FOLDER_FILE.setReadOnly() )
      Log.log("Can't set system folder redaonly",Log.WARNING);*/

  }

  void initMerger(Registry r) {
    if (r.getNode(Toolkit.MERGE_ROOT_ID) != null)
      return;
    try {
      r.insert(r.getRoot().getId(), Toolkit.MERGE_ROOT_ID,
               new Registry.KeyValue(Toolkit.MERGE_ROOT_ID, null));
      setMerger(r,fc.syxaw.storage.hfsbase.Config.DIRECTORY_TYPE,
                AbstractSyxawFile.DirectoryMergerFactory.class.getName());
      setMerger(r,XasUtil.XML_MIME_TYPE,
                XMLMerger.MergerFactory.class.getName());
      setMerger(r, PlaintextMerger.MIME_TYPE,
          PlaintextMerger.MergerFactory.class.getName());
    } catch (NodeNotFoundException ex) {
      Log.log("Can't init merger registry", Log.FATALERROR, ex);
    }
  }

  private static AbstractFileSystem fs = null;

  public AbstractFileSystem getFs() {
    if( fs !=null )
      return fs;
    try {
      fs = (AbstractFileSystem) Class.forName(fsClass).newInstance();
    } catch (Exception ex) {
      Log.log("Error instantiating file system "+fsClass,Log.FATALERROR,ex);
    }
    return fs;
  }

  public void checkpoint() {
    Log.log("Checkpoints not available.",Log.WARNING);
  }

  public void installObjectProvider(String mimeType, ObjectProvider op) {
    ObjectProvider base = (ObjectProvider) AbstractSyxawFile.objectProviders
        .get(mimeType);
    if (base == null)
      base = BASE_PROVIDER;
    op.setBaseProvider(base);
    AbstractSyxawFile.objectProviders.put(mimeType, op);
  }

}
// arch-tag: 9d37557960bf415dee46248eec092f33 *-
