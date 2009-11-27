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

package fc.xml.xmlr.exper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import fc.util.Util;
import fc.util.log.Log;
import fc.util.log.SysoutLogger;
import fc.xml.xas.Comment;
import fc.xml.xas.EntityRef;
import fc.xml.xas.Item;
import fc.xml.xas.ParserSource;
import fc.xml.xas.Qname;
import fc.xml.xas.StartTag;
import fc.xml.xas.Text;
import fc.xml.xas.TransformSource;
import fc.xml.xas.index.SeekableKXmlSource;
import fc.xml.xas.transform.CoalesceContent;
import fc.xml.xas.transform.DataItems;
import fc.xml.xmlr.ChangeBuffer;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.TreeModels;
import fc.xml.xmlr.tdm.Diff;
import fc.xml.xmlr.xas.DeweyKey;
import fc.xml.xmlr.xas.DeweyRefNode;
import fc.xml.xmlr.xas.MutableDeweyRefTree;
import fc.xml.xmlr.xas.XasSerialization;

/* In the docedit test we measure memory consumption for a buffered document
 * edit. Thus, for each version v to maxver -1
 * 1) load v
 * 2) load diff d
 * 3) mark memory usage = u1
 * 4) vb = changebuf(v)
 * 5) apply(d,vb)
 * 6) memory usage = u2; use = u2-u1; nodes = count changetree nodes 
 * 
 */
public class DocEdit {

  protected boolean ACCURATE_MEM = true;
  
  protected Random rnd = new Random(260175l);
  
  public static final File DATA_DIR = new File(
      System.getProperty("fc.exper.data",
          "../../../fc/docs/papers/reftrees/data/"));

  public static final File REV_DIR = new File(DATA_DIR,"+svnbook-revs-linear");
  public static final File DIFF_DIR = new File(DATA_DIR,"+svnbook-diffs-linear");

  public static void main(String[] args) throws IOException {
    Log.setLogger(new SysoutLogger());
    (new DocEdit()).editTest();
  }

  private void editTest() throws IOException {
    int rev = 1;
    ParserSource s = null;
    for(;rev<842;rev++) {
      File baseFile = new File(REV_DIR,""+rev+".xml");
      if ( !baseFile.exists() )
        break;
      s =  new SeekableKXmlSource( baseFile.getPath() );
      long beforeBt = Util.usedMemory(ACCURATE_MEM);
      IdAddressableRefTree bt =
        RefTrees.getAddressableTree(
            XasSerialization.readTree( new TransformSource(new TransformSource(s,new DataItems(true,true)),
                new CoalesceContent(false)), TreeModels.xasItemTree().swapNodeModel(DeweyRefNode.NODE_MODEL_ALT)) );
      //XmlrDebug.dumpTree(bt);
      s.getInputStream().close();
      s = null;
      long afterBt = Util.usedMemory(ACCURATE_MEM);
      s = new SeekableKXmlSource((new File(DIFF_DIR,""+rev+".xml")).getPath());
      Diff d = Diff.readDiff( new TransformSource(new TransformSource(s,new DataItems(true,true)),
          new CoalesceContent(false)) );
      s.getInputStream().close();    
      //XmlrDebug.dumpTree(d);
      RefTree dt = d.decode(bt);
      //XmlrDebug.dumpTree(dt);
      long startuse = Util.usedMemory(ACCURATE_MEM);
      MutableDeweyRefTree changeTree = new MutableDeweyRefTree();
      changeTree.setForcedAutoKey(DeweyKey.topLevel(0));
      ChangeBuffer t = new ChangeBuffer( changeTree, bt );
      try {
        t.apply(dt);
      } catch (NodeNotFoundException e) {
        Log.error(e);
      }
      long enduse = Util.usedMemory(ACCURATE_MEM);
      t.reset(); // Use cb after memory checkpoint, so it can't get GC'd
      
      //---- Random edits ----------------
      List<DeweyKey> treeKeys = new ArrayList<DeweyKey>();
      for ( DeweyKey k : Util.<DeweyKey>iterable(RefTrees.dfsKeyIterator(t))) {
        treeKeys.add(k);
      }
      Collections.shuffle(treeKeys, rnd);
      int percent1 = 250; // 2*treeKeys.size()/100;
      long rndStart = Util.usedMemory(ACCURATE_MEM), rndEnd1=-1;
      // Randomly edit first n %
      try {
        for( DeweyKey k : treeKeys ) {
          Item i = (Item) t.getNode(k).getContent();
          i = mutateItem(i);
          t.update(k, i);
          if( percent1 == 0 )
            rndEnd1 = Util.usedMemory(ACCURATE_MEM);
          if( percent1-- < 0 )
            break;
        }
      } catch (NodeNotFoundException e) {
        Log.error(e);
      }
      t.reset();
      Log.info("# Buffer @"+rev+": basesize(k), changesize(k), rndSize(k), basesize(ndes) ",
          ""+((afterBt-beforeBt)>>10)+" "+((enduse-startuse)>>10)+" "+
          ((rndEnd1-rndStart)>>10) + " " +treeKeys.size()+ 
          " # DATA "+getClass().getName());
    }
  }

  private Item mutateItem(Item i) {
    switch( i.getType() ) {
      case Item.COMMENT:
        return new Comment(((Comment) i).getText().toLowerCase());
      case Item.ENTITY_REF:
        return new EntityRef(((EntityRef) i).getName()+"1");
      case Item.START_TAG:
        StartTag st = (StartTag) i;
        return new StartTag(new Qname(st.getName().getNamespace(),
            st.getName().getName()+"1"), st.getContext() );
      case Item.TEXT:
        return new Text(((Text) i).getData().toUpperCase());
      default:
        Log.warning("Skipping mutate of",i);
    }
    return i;
  }

}
// arch-tag: 61084cc1-9a86-4517-a1e8-0ce50ff59ff8
//
