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

package fc.syxaw.storage.hierfs;

import java.io.File;

import fc.dessy.indexing.Keywords;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.GUID;
import fc.syxaw.storage.hfsbase.ForeignMetadata;

/** Lazy foreign metadata implementation. Evaluates fields on-deman rather than
 * when created. Note: this means field value relfect object state at 
 * time-of-read, rather than at time of object creation.
 */
public class LazyForeignMeta implements ForeignMetadata {
  
  private Metadata fmd;
  private File f;
  
  public LazyForeignMeta(Metadata fmd, File f) {
    this.fmd = fmd;
    this.f = f;
  }

  public String getBranch() {
    return fmd.getBranch();
  }

  public GUID getGuid() {
    return new GUID(GUID.getCurrentLocation(),FileSystem.FS_INSTANCE.getUid(f));
  }

  public byte[] getHash() {
    return fmd.getHash();
  }

  public String getJoins() {
    return fmd.getJoins();
  }

  public String getLink() {
    return fmd.getLink();
  }

  public long getLinkUId() {
    return Constants.NO_VERSION; // FIXME- used at all?
  }

  public int getLinkDataVersion() {
    return fmd.getLinkDataVersion();
  }
  
  public int getLinkMetaVersion() {
    return fmd.getLinkMetaVersion();
  }

  public long getMetaModTime() {
    return fmd.getMetaModTime();
  }

  public int getModFlags() {
    return FileSystem.FS_INSTANCE.getModflags(f);
  }

  public String getName() {
    return f.getName();
  }

  public String getType() {
    return fmd.getType();
  }

  public int getDataVersion() {
    return fmd.getDataVersion();
  }
  
  public int getMetaVersion() {
    return fmd.getMetaVersion();
  }
  
  public Keywords getKeywords(){
    return fmd.keywords;
  }
}

// arch-tag: 2f3ed918-34f9-4665-9398-6d799b9a8a3a
