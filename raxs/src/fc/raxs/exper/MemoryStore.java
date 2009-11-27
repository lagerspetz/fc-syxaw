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

package fc.raxs.exper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fc.raxs.Measurements;
import fc.raxs.Store;
import fc.util.Debug.Time;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.MutableRefTree;
import fc.xml.xmlr.model.TreeModel;
import fc.xml.xmlr.xas.DeweyKey;

public class MemoryStore extends Store {

  protected static Map /*!5 <File,MutableRefTree> */ trees=new HashMap();
  
  protected MutableRefTree t;
  protected TreeModel tm;
  
  public static void forgetTrees() {
    trees.clear();
  }
  
  /*!5 @Override */
  public void close() throws IOException {
  }

  /*!5 @Override */
  public IdAddressableRefTree getTree() {
    return t;
  }

  /*!5 @Override */
  public TreeModel getTreeModel() {
    return tm;
  }

  /*!5 @Override */
  public boolean isWritable() {
    return true;
  }
  
  /*!5 @Override */
  public void open() throws IOException {
    init();
  }

  
  
  protected void init() {
    if( Measurements.STORE_TIMINGS ) 
      Time.stamp( Measurements.H_STORE_INIT );
  }
  
}
// arch-tag: 1ba36480-9fb4-4f77-b03b-f56bb1a9907b
//