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

// $Id: HfsStorageProvider.java,v 1.3 2005/06/08 13:03:28 ctl Exp $
package fc.syxaw.storage.hierfs;

import fc.syxaw.fs.StorageProvider;

import fc.syxaw.fs.UID;
import fc.syxaw.storage.hfsbase.Toolkit;

public class HfsStorageProvider implements StorageProvider {

  public HfsStorageProvider() {
  }

  public UID getNextUid(){
    return UID.newUID();
  }
  
  private static Toolkit toolkit = new HfsToolkit();


  public fc.syxaw.hierfs.SyxawFile getFile(fc.syxaw.fs.
                                                  SyxawFile parent,
                                                  String child) {
    return new SyxawFile(parent, child);
  }

  public fc.syxaw.hierfs.SyxawFile getFile(String pathName) {
    return new SyxawFile(pathName);
  }

  public fc.syxaw.tool.Toolkit getToolkit() {
    return toolkit;
  }

  public fc.syxaw.fs.SyxawFile getFile(UID uid) {
    return new SyxawFile(uid);
  }
  
  public fc.syxaw.fs.FullMetadataImpl createMetadata(){
    return new fc.syxaw.fs.FullMetadataImpl();
  }

/*  public fc.syxaw.fs.SyxawFile getFile(UID u, String query) {
    return new SyxawFile(u,query);
  }*/

}
// arch-tag: ed9bdcbe05a693b75475589981a28124 *-
