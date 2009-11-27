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

package fc.xml.xmlr.test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import junit.framework.Assert;
import junit.framework.TestCase;
import fc.util.Util;
import fc.util.log.Log;
import fc.xml.xmlr.IdAddressableRefTree;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.MutableRefTree;
import fc.xml.xmlr.MutableRefTreeImpl;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.XmlrDebug;
import fc.xml.xmlr.test.RandomDirectoryTree.KeyGen;
import fc.xml.xmlr.test.RandomDirectoryTree.LongKey;

public class TestMutableTree extends TestCase {

  private static final long RND_SEED = 42L;

  protected MutableRefTree buildCandidateInstance(Key rootId) {
    return new MutableRefTreeImpl(rootId,
        MutableRefTreeImpl.ID_AS_STRINGS_BY_LENGTH_ALPHA);
    
  }
  
  public void testMutableTree() throws NodeNotFoundException {
    String PDF="iiiiiiiiiIdddddduuuuuuuuummmm";
    double DIRP=0.05;
    long TSIZE=5000;long DSIZE=5;double DPROB=0.01;double VAR=5.0;double DVAR=2.0;
    Log.info("Testing MutableRefTree");
    Log.info("In reports below, changebuffer is tree 1, facit tree 2");
    KeyGen baseKg = RandomDirectoryTree.KEY_GEN;
    int MAX_LAP=25;
    for( int lap = 0;lap < MAX_LAP;lap++) {
      Log.info("Lap "+(lap+1)+" of "+MAX_LAP);
      
      MutableRefTree dt = RandomDirectoryTree.randomDirTree(TSIZE,DSIZE,DPROB,VAR,DVAR, 
          new Random(1^RND_SEED),baseKg);
      ((RandomDirectoryTree.MutableDirectoryTree) dt).setOrdered(true);
      // Copy it
      MutableRefTree tt = buildCandidateInstance(dt.getRoot().getId());
      tt.insert(null, dt.getRoot().getId(), dt.getRoot().getContent());
      RefTrees.apply(dt, tt);
      Assert.assertTrue("Differing tree after copy", XmlrDebug.treeComp(dt,tt));        

      IdAddressableRefTree baseTree = RefTrees.getAddressableTree(
          RandomDirectoryTree.treeCopy(dt));

      // Change it
      KeyGen kg = new KeyGen(baseKg);
      //RandomDirectoryTree.edstring = "";
      RandomDirectoryTree.
      permutateTree(dt,10*lap,PDF,DPROB,new Random(lap^RND_SEED),kg);
      //Log.debug("DirTree edits",RandomDirectoryTree.edstring);
      //RandomDirectoryTree.edstring = "";
      kg = new KeyGen(baseKg);
      RandomDirectoryTree.
      permutateTree(tt,10*lap,PDF,DPROB,new Random(lap^RND_SEED),kg);
      //Log.debug("TestTree edits",RandomDirectoryTree.edstring);

      //TreeUtil.dumpTree(dt, System.out);
      //TreeUtil.dumpTree(tt, System.out);
      Assert.assertTrue("Differing tree after edit", XmlrDebug.treeComp(dt,tt));        

      
      Set /*!5 <Key> */ baseSet = buildSet(baseTree.getRoot(),
           new HashSet /*!5 <Key> */());
      Set /*!5 <Key> */ newSet = buildSet(dt.getRoot(),
          new HashSet /*!5 <Key> */());
      Set /*!5 <Key> */ newSetCt = buildSet(tt.getRoot(),
          new HashSet /*!5 <Key> */());
      Assert.assertEquals("Node sets not equal",newSet,newSetCt);
      Set /*!5 <Key> */ deletia = new HashSet /*!5 <Key> */(baseSet);
      deletia.removeAll(newSet);
      for( Iterator i = newSet.iterator(); i.hasNext(); ) {
        Key k = (Key) i.next();
        Assert.assertTrue("Missing key in changetree: "+k,tt.contains(k));
      }
      for( Iterator i = deletia.iterator(); i.hasNext();  ) {
        Key k = (Key) i.next();
        Assert.assertFalse("Key should be deleted in changetree: "+k,tt.contains(k));
      }

      int resurrects = 20;
      for( Iterator i = deletia.iterator(); i.hasNext(); ) {
        Key k = (Key) i.next();
        kg = new KeyGen((LongKey) k,1);
        RandomDirectoryTree.
        permutateTree(dt,1,"iI",DPROB,new Random(lap^resurrects^RND_SEED),kg);
        //A! assert dt.contains(k) : "Node did not appear in facit!"+k;
        kg = new KeyGen((LongKey) k,1);
        RandomDirectoryTree.
        permutateTree(tt,1,"iI",DPROB,new Random(lap^resurrects^RND_SEED),kg);
        Assert.assertTrue("Re-inserted node is missing: "+k,tt.contains(k));
        if( --resurrects < 0 )
          break;
      }
      Assert.assertTrue("Differing tree after delete resurrect",
          XmlrDebug.treeComp(tt,dt));        
    }
    //Log.info("Congratulations; you are a spiffy hacker.");
  }

  private Set /*!5 <Key> */ buildSet(RefTreeNode n, Set /*!5 <Key> */ s) {
    s.add(n.getId());
    for( Iterator i = n.getChildIterator(); i.hasNext();  )
      buildSet((RefTreeNode) i.next(),s);
    return s;
  }

}

// arch-tag: 56fe4e15-254f-47eb-a01b-f32f8cafaa97

