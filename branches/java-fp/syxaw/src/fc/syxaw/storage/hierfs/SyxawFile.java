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

// $Id: SyxawFile.java,v 1.97 2005/06/08 13:03:28 ctl Exp $
package fc.syxaw.storage.hierfs;

import java.io.File;

import fc.syxaw.fs.UID;
import fc.syxaw.storage.hfsbase.AbstractSyxawFile;

/** Implementation of hierarchical SyxawFile on top of a hierarchical file
 * system.
 */

public class SyxawFile extends AbstractSyxawFile {

  SyxawFile(UID id) {
    super(id);
  }


/*  SyxawFile(UID id, String query) {
    super(id, query);
  }*/

  SyxawFile(fc.syxaw.fs.SyxawFile parent, String child) {
    super(parent, child);
  }

  SyxawFile(String path) {
    super(path);
  }

  SyxawFile(File file) {
    super(file);
  }

  public String[] list() {
    // Censor .syxaw files
    return f.list(HfsToolkit.NO_DOT_SYXAW_PFX);
  }

  // stuff with constructors
  public fc.syxaw.fs.SyxawFile newInstance(UID uid) {
    return new SyxawFile(uid);
  }

  public fc.syxaw.hierfs.SyxawFile getParentFile() {
    File p = f.getParentFile();
    return p == null ? null : new SyxawFile(p);
  }


  public fc.syxaw.hierfs.SyxawFile getChildFile(String child) {
    return new SyxawFile(this,child);
  }

  public fc.syxaw.hierfs.SyxawFile newInstance(fc.syxaw.hierfs.
      SyxawFile parent, String child) {
    return new SyxawFile(parent,child);
  }

  public fc.syxaw.hierfs.SyxawFile newInstance(String name) {
    return new SyxawFile(name);
  }

  protected AbstractSyxawFile newInstance(File f) {
    return new SyxawFile(f);
  }

  /* protected Toolkit getToolkit() {
    return Toolkit.getInstance();
  }*/

  // ends
}
// arch-tag: 28dd91e46ff0c621de79c39e0740ed63 *-
