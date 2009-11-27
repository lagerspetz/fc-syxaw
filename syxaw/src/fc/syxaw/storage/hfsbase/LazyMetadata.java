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

package fc.syxaw.storage.hfsbase;

import java.io.FileNotFoundException;

import fc.dessy.indexing.Keywords;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.GUID;
import fc.util.log.Log;

/** Lazily evaluated full metadata. Provides lazy evaluation of operations on
 * the target file (to improve performance).  Note: this means that the field
 * values reflect the actual state of the file, not a snapshot state when the
 * metadata was requested.
 * 
 */
public class LazyMetadata implements FullMetadata {

  protected ForeignMetadata md;
  protected AbstractSyxawFile f;
  
  public LazyMetadata(ForeignMetadata fm, AbstractSyxawFile f) {
    this.md = fm;
    this.f = f;
  }

  public String getBranch() {
    return md.getBranch();
  }

  public GUID getGuid() {
    return md.getGuid();
  }

  public byte[] getHash() {
    return md.getHash();
  }

  public String getJoins() {
    return md.getJoins();
  }

  public String getLink() {
    return md.getLink();
  }

  public long getLinkUId() {
    return md.getLinkUId();
  }

  public int getLinkDataVersion() {
    return md.getLinkDataVersion();
  }
  
  public int getLinkMetaVersion() {
    return md.getLinkMetaVersion();
  }

  public long getMetaModTime() {
    return md.getMetaModTime();
  }

  public int getModFlags() {
    return md.getModFlags();
  }

  public String getName() {
    return md.getName();
  }

  public String getType() {
    return md.getType();
  }

  public int getDataVersion() {
    return md.getDataVersion();
  }
  
  public int getMetaVersion() {
    return md.getMetaVersion();
  }
  
  public Keywords getKeywords(){
    return md.getKeywords();
  }

  public long getLength() {
    try {
      return f.isDirectory() ? Constants.NO_SIZE : 
        f.query == null ? f.length() : f.qsf.getLength(f,f.query,false);
    } catch (FileNotFoundException e) {
      Log.error("File disappeared",f.toString());
    }
    return -1l;
  }

  public boolean getReadOnly() {
    return !f.canRead();
  }

  public long getModTime() {
    return AbstractSyxawFile._n95_cap_modtime(f.lastModified());
  }

}
// arch-tag: e7f700cc-4cc1-4d75-8433-fd8d01fdc430

