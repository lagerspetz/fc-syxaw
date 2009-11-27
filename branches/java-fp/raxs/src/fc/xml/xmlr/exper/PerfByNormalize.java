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
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Set;

import fc.util.Debug;
import fc.util.NonListableSet;
import fc.util.Util;
import fc.util.Debug.Measure;
import fc.util.Debug.Time;
import fc.util.log.Log;
import fc.util.log.SysoutLogger;
import fc.xml.xas.ParserSource;
import fc.xml.xas.TransformSource;
import fc.xml.xas.index.SeekableKXmlSource;
import fc.xml.xas.transform.CoalesceContent;
import fc.xml.xas.transform.DataItems;
import fc.xml.xmlr.ChangeBuffer;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.KeyMap;
import fc.xml.xmlr.MutableRefTree;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.RekeyedRefTree;
import fc.xml.xmlr.XmlrDebug;
import fc.xml.xmlr.model.TreeModels;
import fc.xml.xmlr.tdm.Diff;
import fc.xml.xmlr.xas.DeweyKey;
import fc.xml.xmlr.xas.DeweyRefNode;
import fc.xml.xmlr.xas.MutableDeweyRefTree;
import fc.xml.xmlr.xas.XasSerialization;

/* In this test we compute the size of the normalized set between
 * two versions, and measure the execution time for operations on
 * these two verions. The hypothesis is that execution time will
 * be explained by normalized set size, rather than input tree size.
 * Ops that we test are
 *  1) apply()
 *  2) MAYBE: reverse-ref()
 *  3) MAYBE: normalize() itself :)
 *  4) MAYBE: RAXS commit, recall.
 */

public class PerfByNormalize extends DocEdit {

  public static void main(String[] args) throws IOException {
    Log.setLogger(new SysoutLogger());
    (new PerfByNormalize()).run();
  }

  private void run() throws IOException {
    int rev = 1;
    ParserSource s = null;
    for(;rev<842;rev++) {
      File baseFile = new File( REV_DIR,""+rev+".xml");
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
      // =======================================================
      // bt = base tree (adressable)
      // dt = new tree (reftree to bt, ie dtRbt )
      // ## Apply
      Time.stamp();
      MonitoredChangeBuffer ta;
      long applyTime = -1; 
      {
        MutableDeweyRefTree changeTree = new MutableDeweyRefTree();
        changeTree.setForcedAutoKey(DeweyKey.topLevel(0));
        MonitoredChangeBuffer t = new MonitoredChangeBuffer( changeTree, bt );
        try {
          t.apply(dt);
          applyTime = Time.since();
        } catch (NodeNotFoundException e) {
          Log.error(e);
        }
        ta = t;
      }
      // ## Reverse-ref
      long reverseTime = -1;
      {
        // KLUDGE: The nodes in dt have funny keys because decode() doesn't
        // take a proper node/key model, and we need an idaddressable variant.
        // The kludge, is just to copy the tree into a new tree
        // (ddt) that will have proper keys.
        MutableDeweyRefTree ddt = new MutableDeweyRefTree();
        ddt.setForcedAutoKey(DeweyKey.topLevel(0));
        insertAll(ddt,null,dt.getRoot());
        /*XmlrDebug.dumpTree(dt);
        XmlrDebug.dumpTree(ddt);
        Debug.sleep(10000);*/
        Time.stamp();
        MutableDeweyRefTree changeTree = new MutableDeweyRefTree();
        changeTree.setForcedAutoKey(DeweyKey.topLevel(0));
        ChangeBuffer t = new MonitoredChangeBuffer( changeTree, bt );
        try {
          for (int i=0;i<5;i++) {
            // Do it 5 times to get more even results
            storeReverseDelta( ddt, t, changeTree );
          }
          reverseTime = Time.since();
        } catch (NodeNotFoundException e) {
          Log.error(e);
        }
      }
      Log.info("# Buffer @"+rev+
          ": nodes[bt] nodes[dtRbt] nodes[dt] nlzsize apply_time(ms) reverse(ms)",
          ""+nodes(bt.getRoot())+" "+nodes(dt.getRoot())+" "+
          nodes(ta.getRoot())+
          " "+Measure.get(ta.NORMALIZE_SIZE_MEASURE)+ " "+applyTime +    
          " "+reverseTime+
          " # DATA "+getClass().getName());
    }
  }

  private void insertAll(MutableDeweyRefTree ddt, Key targetKey, RefTreeNode root) {
    try {
      Key k = ddt.insert(targetKey, null, root.isReference() ?
          root.getReference() : root.getContent() );
      for (Iterator<RefTreeNode> i = root.getChildIterator();i.hasNext();) {
        insertAll(ddt, k, i.next());
      }        
    } catch (NodeNotFoundException e) {
      Log.fatal(e);
    }    
  }

  protected void storeReverseDelta(final IdAddressableRefTree deltaRefTree,
      IdAddressableRefTree currentTree,
      KeyMap newToCurr) throws NodeNotFoundException, IOException {
    if( newToCurr != null )
      Log.debug("Running algorithms with re-keyed trees");    
    
    final IdAddressableRefTree deltaRefTreeRK = newToCurr != null ?
        RekeyedRefTree.create(deltaRefTree, newToCurr) : deltaRefTree;
        
    RefTree currentAsRefTree = RefTrees.getRefTree(currentTree);
        
    Set[] usedRefs = RefTrees.normalize(currentTree,
        new RefTree[] { deltaRefTreeRK, currentAsRefTree });

    Log.debug("No of refs in new to current "+usedRefs.length);

    Set allowedContentRefs = new NonListableSet() {
      // The allowed content refs in currentRefsNew. A content ref is allowed if
      // the nodes have the same content. This is the case for a node n
      // if that node in the deltaTree refs to the backTree(=current version)

      public boolean contains(Object id) {
        RefTreeNode n = deltaRefTreeRK.getNode((Key) id);        
        return n==null ? false : n.isReference();
      }
    };

    RefTree currentRefsNewRK= RefTrees.expandRefs(currentAsRefTree,usedRefs[0],
                                                 allowedContentRefs,
                                                 currentTree);
    currentRefsNewRK.getRoot();
  }
  
  public static class MonitoredChangeBuffer extends ChangeBuffer {
    
    public final Object NORMALIZE_SIZE_MEASURE = new Object();
    
    public MonitoredChangeBuffer(IdAddressableRefTree backingTree, KeyMap kmap) {
      super(backingTree, kmap);
    }

    public MonitoredChangeBuffer(IdAddressableRefTree backingTree) {
      super(backingTree);
    }

    public MonitoredChangeBuffer(MutableRefTree changeTree, IdAddressableRefTree backingTree, KeyMap map, Key changeTreeRoot) {
      super(changeTree, backingTree, map, changeTreeRoot);
    }

    public MonitoredChangeBuffer(MutableRefTree changeTree, IdAddressableRefTree backingTree, KeyMap map) {
      super(changeTree, backingTree, map);
    }

    public MonitoredChangeBuffer(MutableRefTree changeTree, IdAddressableRefTree backingTree) {
      super(changeTree, backingTree);
    }

    @Override
    protected void expandAll(RefTreeNode n, KeyMap km) throws NodeNotFoundException {
      super.expandAll(n, km);
      int nodes = nodes(n)+nodes(changeTree.getRoot());
      Measure.set(NORMALIZE_SIZE_MEASURE, nodes);
    }
    
  }
  
  public static int nodes(RefTreeNode n) {
    int nodes = 0;
    // Count nodes in new tree
    for (Iterator<RefTreeNode> i= RefTrees.dfsIterator(n);i.hasNext();nodes++) {
      i.next();
    }
    return nodes;
  }
  
}
// arch-tag: 10833ffd-47e7-403c-8c44-d550b740a31a
//
