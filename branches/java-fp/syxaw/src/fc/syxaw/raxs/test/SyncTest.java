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

package fc.syxaw.raxs.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;

import fc.raxs.RandomAccessXmlStore;
import fc.raxs.RaxsConfiguration;
import fc.syxaw.api.MetadataImpl;
import fc.syxaw.fs.Syxaw;
import fc.syxaw.hierfs.SyxawFile;
import fc.syxaw.raxs.RaxsProvider;
import fc.util.log.Log;
import fc.xml.xas.Comment;
import fc.xml.xas.Item;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.MutableRefTree;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.XmlrDebug;

/** Synchronization test methods for RAXS store. The methods in this class 
 * are used by SyxRunner scripts.  
 */

public class SyncTest {
  
  public static void registerMerger(PrintStream p, String[] args ) {
    Log.info("Registering RAXS object provider");
    Syxaw.getToolkit().installObjectProvider(RaxsProvider.MIME_TYPE, 
        new RaxsProvider() );
  }
  
  public static void setupStore(PrintStream p, String[] args)
      throws IOException {
    Log.info("Setting up store");
    SyxawFile f = Syxaw.getFile(args[2]);
    if( !f.mkdir() )
      throw new IOException("Can't make "+f);
    MetadataImpl md = MetadataImpl.createFrom( f.getMetadata() );
    md.setType(RaxsProvider.MIME_TYPE);
    f.setMetadata(md);
    SyxawFile xf = Syxaw.getFile(args[1]);
    if( !xf.renameTo(f.getChildFile("content.xml")) )
      throw new IOException("Could not set up content.xml");
  }

  public static void makeMod(PrintStream p, String[] args)
    throws IOException, NodeNotFoundException {
    RandomAccessXmlStore raxs = openStore(args[1]);
    MutableRefTree t = raxs.getEditableTree();
    Item mod = new Comment("Modification: "+args[2]);
    Key k = t.insert(getRootNode(t).getId(), MutableRefTree.AUTO_KEY,mod);
    Log.info("Inserted change at "+k+":",mod);
    int v =raxs.commit(t, false);
    Log.info("Committed version is "+v);
    raxs.close();
  }

  public static void testMod(PrintStream p, String[] args) throws IOException,
      NodeNotFoundException {
    RandomAccessXmlStore raxs = openStore(args[1]);
    RefTree t = raxs.getTree();
    Item mod = new Comment("Modification: " + args[2]);
    Key found = null;
    for (Iterator i = getRootNode(t).getChildIterator(); i.hasNext();) {
      RefTreeNode n = ((RefTreeNode) i.next());
      Item scan = (Item) n.getContent();
      if (mod.equals(scan))
        found = n.getId();
    }
    raxs.close();
    if (found != null) {
      Log.info("Modification found at " + found, mod);
      FileOutputStream marker = new FileOutputStream("ok-" + args[2]);
      marker.close();
    } else {
      Log.warning("Modification not found", mod);
    }
  }

  public static void storeComp(PrintStream p, String[] args) throws IOException,
  NodeNotFoundException {
    RandomAccessXmlStore raxs1 = openStore(args[1]);
    RandomAccessXmlStore raxs2 = openStore(args[2]);
    try {
      RefTree t1 = raxs1.getTree();
      RefTree t2 = raxs2.getTree();
      if( !XmlrDebug.treeComp(t1, t2) ) {
        Log.error("Differing trees (1,2)");
        XmlrDebug.dumpTree(t1);
        XmlrDebug.dumpTree(t2);
        throw new IOException("Differing trees");
      }
    } finally {
      raxs1.close();
      raxs2.close();
    }
  }

  private static RefTreeNode getRootNode(RefTree t) {
    RefTreeNode n = t.getRoot();
    if( Item.isStartTag((Item) n.getContent() ) )
      return n;
    for(Iterator i = n.getChildIterator();i.hasNext();) {
      RefTreeNode m = (RefTreeNode) i.next();
      if( Item.isStartTag((Item) m.getContent() ) )
        return m;
    }
    return null;

  }

  private static RandomAccessXmlStore openStore(String name) throws IOException, FileNotFoundException {
    SyxawFile sf = Syxaw.getFile(name);
    RaxsConfiguration rc = RandomAccessXmlStore.getConfiguration(new File(
        name), sf.getMetadata().getType() );
    Log.info("==The tree model is",rc.getModel());
    RandomAccessXmlStore raxs = RandomAccessXmlStore.open(rc);
    raxs.open();
    return raxs;
  }
  
  
}
// arch-tag: 697e8f7c-45c0-4b4d-a65c-5e3feea43152
