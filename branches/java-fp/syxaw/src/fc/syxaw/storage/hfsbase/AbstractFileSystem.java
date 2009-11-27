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

// $Id: AbstractFileSystem.java,v 1.6 2005/06/08 13:03:28 ctl Exp $
package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import fc.syxaw.fs.UID;
import fc.xml.xmlr.model.StringKey;

public abstract class AbstractFileSystem {

  //NOCHANGED  private ChangeDaemon changed = null;
  protected LinkTree linkTree = null;
  protected LocalTree localTree = null;
  //protected VersionDB verDb = null;
  protected Registry registry = null;

  protected AbstractFileSystem() {
  }

  protected void init(StoredDirectoryTree.ChangeMonitor cm) {
    registry = new fc.syxaw.storage.hfsbase.Registry(Toolkit.
        REGISTRY_FOLDER_FILE);
    VersionedDirectoryTree repo = new VersionedDirectoryTree(Toolkit.
        DIRDB_FOLDER_FILE, cm);
    linkTree = new LinkTree(newFile(ServerConfig.ROOT_FOLDER_FILE), repo,
                            Toolkit.DIRDB_FOLDER_FILE, registry); // Can't used UID.DBO yet
    localTree = new LocalTree(newFile(ServerConfig.ROOT_FOLDER_FILE),
                              repo
                              /*NOCHANGED ,changed*/);
    //setNodeId(ServerConfig.ROOT_FOLDER_FILE,VersionedDirectoryTree.ROOT_ID);
  }
  // System ops

  public LinkTree getLinkTree() {
    return linkTree;
  }

  public LocalTree getLocalTree() {
    return localTree;
  }

  public Registry getRegistry() {
    return registry;
  }

  // File ops

  public abstract boolean create(File f, UID u) throws IOException;

  public abstract boolean delete(File f, UID uid);

  public abstract File getFileForUId(UID uid);

  public abstract byte[] getHash(File f);

  public abstract String getLink(File f);

  public abstract long getLinkDataVersion(File f);
  
  public abstract void setLinkDataVersion(File f, long version);
  
  public abstract long getLinkMetaVersion(File f);
  
  public abstract void setLinkMetaVersion(File f, long version);
  
  public abstract long getMetaModTime(File f);

  public abstract ForeignMetadata getMetadata(File f);

  public abstract int getModflags(File f);

  public abstract StringKey getNodeId(File f);

  public abstract String getType(File f);

  public abstract UID getUid(File f);

  public abstract long getDataVersion(File f);

  public abstract void setHash(File f, byte[] aHash);

  public abstract void setLink(File f, String aLink);



  public abstract void setMetaModTime(File f, long aMetaModTime);

  public abstract void setMetadata(File f, ForeignMetadata fmd, boolean setMetaModified);

  public abstract void setModflags(File f, int aModflags);

  public abstract void setNodeId(File f, StringKey id);

  public abstract void setType(File f, String aType);

  public abstract void setDataVersion(File f, long version);
  
  public abstract void setMetaVersion(File f, long version);

  protected abstract AbstractSyxawFile newFile(File f);
}
// arch-tag: 368420c802c67248f62b479db605efb6 *-
