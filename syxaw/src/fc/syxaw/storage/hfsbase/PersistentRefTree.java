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

// $Id: PersistentRefTree.java,v 1.10 2005/02/10 14:51:07 ctl Exp $
package fc.syxaw.storage.hfsbase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import fc.syxaw.util.Util;
import fc.syxaw.util.XmlUtil;
import fc.util.log.Log;
import fc.xml.xmlr.AbstractMutableRefTree;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.Reference;
import fc.xml.xmlr.model.StringKey;
import fc.xml.xmlr.model.XasCodec;

/** Persistent mutable reftree.
 */

public class PersistentRefTree extends AbstractMutableRefTree {

  public static final String CONTENT_DB_NAME="nodes";
  public static final String PTR_DB_NAME="tree";


  private static final int CONTENT_AVG_SIZE=256;
  private static final int PTR_AVG_SIZE=4*16;
  private static final String UNSORT="u";

   // '=' to ensure that DIRFILE is not a urlencoded key

  private static final String SENTINEL = "="; // Must never be put/get


  private ObjectDb pdb;
  private ObjectDb cdb;

  private StringKey root;

  private final Object NO_CONTENT_CHANGE = new Object();

//  protected Map contentCache = new Cache(2,64,CONTENT_AVG_SIZE);
//  protected Map ptrCache = new Cache(2,1024,CONTENT_AVG_SIZE);

  /** Create a new reftree.
   *
   * @param aStore File pointing out the location of the store; must be
   * a directory.
   * @param nodeCodec ContentCodec to use for node contents
   */
  public PersistentRefTree(File aStore, XasCodec nodeCodec) {
    this(aStore,nodeCodec,false);
  }

  private PersistentRefTree(File aStore, XasCodec nodeCodec,
                             boolean inInit) {
    pdb = new PtrDb(new File(aStore,PTR_DB_NAME));
    cdb = new ContentDb(new File(aStore,CONTENT_DB_NAME),nodeCodec);
    if (!inInit) {
      String[] rootpts = getPts(SENTINEL);
      if (rootpts == null)
        Log.log("Broken tree: no root entry", Log.FATALERROR);
      root = StringKey.createKey( rootpts[2] ); // First child of dummy root
    }
  }

  //Cprotected abstract boolean isLeaf(String id, Object content);

  //Cprotected abstract void cacheContent(String id, Object content);

  // Ins at particular natural position. May not be supported
  public Key insert( Key parentId, long pos, Key newId,
                      Object content) {
    insert(parentId.toString(),pos,newId.toString(),content,null);
    return newId;
  }

  protected void insert( String parentIdz, long pos, String newId,
                         Object content, String[] newpts ) {
    String parentId = zgetFile(parentIdz);
    String newIdS = zgetFile(newId);
    if( pos != -1 )
      Log.log("This class doesn't support explicit child ordering",Log.ASSERTFAILED);
    if ( newpts==null && getPts(newIdS) != null)
      Log.log("Already in tree: " + newIdS, Log.ASSERTFAILED);
      // Last in child list
    if( newpts != null )
      newpts= new String[] {SENTINEL,SENTINEL,newpts[2],newpts[3],parentId,newpts[5]};
    else
      newpts =  new String[] {SENTINEL,SENTINEL,SENTINEL,SENTINEL,parentId,SENTINEL};
    String[] parentpts = getPts(parentId);
    if( SENTINEL != parentpts[2]  ) {
      // Already has children, need to update pts of last child
      String[] prevchpts = getPts( parentpts[3]);
      newpts[1]=parentpts[3]; // Prev of new is old last
      prevchpts[0]=newId; // old last next is new
      parentpts[3]=newId; // parent lc is new
      putPts(newpts[1],prevchpts,false);
      if( parentpts[5]==SENTINEL &&
          Util.STRINGS_BY_LENGTH.compare(newpts[1],newId) > 0)
        parentpts[5]=UNSORT; // Destroyed sort..

    } else {
      // Child list consisting of 1 node =newId
      parentpts[2]=newId;
      parentpts[3]=newId;
    }
    putPts(newId,newpts, content != NO_CONTENT_CHANGE);
    putPts(parentId,parentpts,false);

    // Put content
    if( content != NO_CONTENT_CHANGE ) {
      putContent(newId, content, true);
    }
//    invalidateChildList(parent.getId());
    //Log.log("Inserted "+getPath(getNode(newId))+" data ",Log.INFO,content);
  }

  private final String[] getPts(String id) {
    return (String[]) pdb.lookup(id);
  }

  private final void putPts(String id,String[] pts, boolean insert) {
    pdb.update(id,pts);
  }

  private List sortChildren(String pid) {
    String[] ppts=getPts(pid);
    if( SENTINEL == ppts[5] ) {
      List l = new LinkedList();
      for(String pos=ppts[2];pos !=SENTINEL;pos=getPts(pos)[0])
        l.add( new Node( StringKey.createKey( pos ) ));
      return l;
    }
    ArrayList l = new ArrayList();
    for(String pos=ppts[2];pos != SENTINEL;pos=getPts(pos)[0])
      l.add(pos);
    Collections.sort(l,Util.STRINGS_BY_LENGTH);
    // Writeback list
    int _wc=0;
    Object[] sl=l.toArray();
    for( int i=0;i<sl.length;i++) {
      String curr=(String) sl[i];
      String prev = i==0 ? SENTINEL : (String) sl[i-1];
      String next = i==sl.length-1 ? SENTINEL : (String) sl[i+1];
      String[] pts = getPts(curr);
      if( !next.equals(pts[0]) || !prev.equals(pts[1]) ) {
        pts[0] = next;
        pts[1] = prev;
        _wc++;
        putPts(curr, pts, false);
      }
    }
    // And new head/tail ptr
    ppts[2]=(String) sl[0];
    ppts[3]=(String) sl[sl.length-1];
    ppts[5]=SENTINEL; // Now sorted!
    putPts(pid,ppts,false);
    Log.log("Sorted list of "+pid+ " write/len="+_wc+"/"+sl.length,Log.INFO);
    for( ListIterator i=l.listIterator();i.hasNext();)
      i.set(new Node( StringKey.createKey( (String) i.next() ) ));
    return l;
  }

  private final void putContent(String id, Object content, boolean insert) {
    cdb.update(id,content);
  }

  private final Object getContent(String id) {
    return cdb.lookup(id);
  }


  public Key move( Key nId, Key parentId, long pos )
      throws NodeNotFoundException {
    // delete & insert structpointers for this
    String __oldPath = getPath((StringKey) nId);
    String[] pts=delete(nId.toString(),false);
    insert(parentId.toString(),pos,nId.toString(),NO_CONTENT_CHANGE,pts);
    Log.log("Moved "+__oldPath+"->"+getPath((StringKey) nId),Log.INFO);
    return nId;
  }

  public boolean update( Key nId, Object newContent )
      throws NodeNotFoundException {
    putContent(zgetFile(nId),newContent,false);
    return true;
  }

  public void delete( Key id ) throws NodeNotFoundException {
    if( !(id instanceof StringKey) )
      throw new NodeNotFoundException(id);
    delete(id.toString(),true);
  }

  protected String[] delete( String id, boolean reap ) throws NodeNotFoundException {
    String pts[] = getPts(id);
    if( pts == null )
      Log.log("Attempt to delete unknown node "+id,Log.FATALERROR);
    String[] ppts = null;
    // First, fix the "next/fc" pointers of prev/root
    if( pts[1] == SENTINEL ) {
      // deleting first child, need to fix parent fc
      ppts = getPts(pts[4]);
      ppts[2]=pts[0];
    } else {
      String prevpts[]= getPts(pts[1]);
      prevpts[0]=pts[0];
      putPts(pts[1],prevpts,false);
    }
    // .. then the "prev/lc" pointers of next/root
    if( pts[0] == SENTINEL ) {
      // deleting last child
      ppts = ppts == null ? getPts(pts[4]) : ppts;
      ppts[3]=pts[1];
    } else {
      String[] nextpts = getPts(pts[0]);
      nextpts[1]=pts[1];
      putPts(pts[0],nextpts,false);
    }
    if( ppts != null )
      putPts(pts[4],ppts,false);

    //Log.log("AfterDel: childlist with "+id,Log.DEBUG,childIterator(__parentId));
    /*DEBUG
    LinkedList __l = new LinkedList();
    for(String _pos=getPts(__parentId)[3];!SENTINEL.equals(_pos);_pos=getPts(_pos)[1])
      __l.add(_pos);
    Log.log("Reverse list is "+__l,Log.DEBUG);  */
    if( reap ) {
      pts[0]=SENTINEL; reap(id,pts); //NOT set pts[0] to nowhere so we don't kill
                                   // end-of-list
    }
    return pts;
    //invalidateChildList(parent.getId());
  }

  // Kill all p+c reachable from content
  private final String[] reap(String id,String[] pts) {
    pts = pts == null ? getPts(id) : pts;
    cdb.delete(zgetFile(id));
    pdb.delete(id);
    if (pts[2] != SENTINEL) {
      reap(pts[2], null);
    }
    while (pts[0] != SENTINEL) {
      pts = reap(pts[0], null);
    }
    return pts;
  }

  protected static final String zgetId( String fName ) {
    return fName;
  }

  protected static final String zgetFile( String id ) {
    return id;
  }
  
  protected static final String zgetFile( Key id ) {
    return id==null ? null : zgetFile( ((StringKey) id).toString() );
  }


 public RefTreeNode getNode( Key id ) {
   if( id instanceof StringKey &&
       getPts( zgetFile(id)) != null )
     return new Node((StringKey) id);
   return null;
 }

  public RefTreeNode getRoot() {
    return getNode(root);
  }

  void close() throws IOException {
	  cdb.close();
	  pdb.close();
  }

  // Get path for a node

  private String getPath(RefTreeNode n) {
    if( n== null )
      return null;
    else
      return (n.getParent() != null ? getPath(n.getParent()) : "")
          +File.separator+zgetFile(n.getId().toString());
  }

  private String getPath(StringKey id) {
    return id==null? null : getPath( new Node( id ));
  }

  class Node implements RefTreeNode {

    StringKey id;
    List children = null;

    public Node( StringKey aId ) {
      if( aId==null || aId.toString().length() == 0)
        Log.log("Null/\"\" is not an allowed as id",Log.ASSERTFAILED);
      id = aId;
    }

    public Iterator getChildIterator() {
      children = children == null ?
          PersistentRefTree.this.sortChildren(id.toString()) : children;
      return children.iterator();
    }

    public Object getContent() {
      return PersistentRefTree.this.getContent(zgetFile(id.toString()));
    }

    public Key getId() {
      return id;
    }

    public RefTreeNode getParent() {
      String[] pts =getPts(zgetFile(id.toString()));
      if( pts == null )
        Log.log("Node not in indexdb",Log.ASSERTFAILED);
      return pts[4] != SENTINEL ? new Node( StringKey.createKey( pts[4] ) ) : null;
    }

    public boolean isNodeRef() {
      return false;
    }

    public boolean isReference() {
      return false;
    }

    public boolean isTreeRef() {
      return false;
    }

    public Reference getReference() {
      return null;
    }
  }

  private static class PtrDb extends ObjectDb {
    public PtrDb(File aRoot) {
      super(aRoot,true);
    }

    protected byte[] serialize(Object o) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(PTR_AVG_SIZE); // check!
      try {
        String[] pts = (String[]) o;
        for (int i = 0; i < 6; i++) {
          if (pts[i] == SENTINEL) {
            bos.write(0);
          } else {
            byte[] ptsb = pts[i].getBytes();
            int len = ptsb.length + 0x100;
            if (len > 32767)
              Log.log("Too long id", Log.ASSERTFAILED);
            bos.write(len >> 8);
            bos.write(len & 0xff);
            bos.write(ptsb);
          }
        }
      } catch (IOException ex) {
        Log.log("Pointer write failed", Log.FATALERROR, ex);
      }
      return bos.toByteArray();
    }

    protected Object deserialize(byte[] b) {
      if (b == null)
        return null;
      String[] pts = new String[6];
      int pos = 0;
      for (int i = 0; i < 6; i++) {
        int len0 = b[pos] & 0xff;
        pos += 1;
        if (len0 > 0) {
          int len = ( (len0-1) << 8 ) + (b[pos] & 0xff);
          pts[i] = new String(b, pos+1, len);
          pos += (len+1);
        } else
          pts[i] = SENTINEL;
      }
      return pts;
    }
  }

  private static class ContentDb extends ObjectDb {

    private XasCodec nodeCodec;

    public ContentDb(File aRoot, XasCodec nodeCodec) {
      super(aRoot,true);
      this.nodeCodec = nodeCodec;
    }

    protected byte[] serialize(Object content) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(CONTENT_AVG_SIZE); // check!
      try {
        XmlUtil.writeObject(bos,content,nodeCodec);
        /*DEBUG
         * if( nodeCodec instanceof StoredDirectoryTree.NodeXasCodec ) {
          Log.log("Wrote to contentdb: "+new String(bos.toByteArray()),Log.DEBUG);
          if( bos.toByteArray().length == 0)
            Log.log("Zero content", Log.ASSERTFAILED);
        }*/
      } catch (IOException x) {
        Log.log("Can't insert entry " +
                ((RefTrees.IdentifiableContent) content).getId() ,
                Log.ASSERTFAILED,x);
      }
      return bos.toByteArray();
    }

    protected Object deserialize(byte[] b) {
      ByteArrayInputStream bin = new ByteArrayInputStream(b);
      Object o = null;
      try {
        /*DEBUG
        if( nodeCodec instanceof StoredDirectoryTree.NodeXasCodec )
          Log.log("Reading data from contentdb: "+new String(b),Log.DEBUG);
        */
        o = XmlUtil.readObject(bin, nodeCodec);
      } catch (IOException ex) {
        Log.log("Can't read content", Log.FATALERROR, ex);
        return null;
      }
      return o;
    }
  }

  /** Maintenance class for persistent reftrees. */
  public static class Maintenance {

    /** Initialize a new persistent reftree.
     *
     * @param aStore location of the store; must be a directory
     * @param theRoot RefTreeNode root node
     * @param nodeCodec ContentCodec to use for node contents
     */
    public static void init(File aStore, RefTreeNode theRoot,
                            XasCodec nodeCodec) {

      try {
        File pdbf = new File(aStore,PTR_DB_NAME);
        File cdbf = new File(aStore,CONTENT_DB_NAME);
        if( !pdbf.exists() && !pdbf.mkdir() )
          Log.log("Can't init "+pdbf,Log.FATALERROR);
        if( !cdbf.exists() && !cdbf.mkdir() )
          Log.log("Can't init "+cdbf,Log.FATALERROR);

        PersistentRefTree t = new PersistentRefTree(aStore,nodeCodec,true);
        String rId = zgetFile(theRoot.getId().toString());
        t.putPts(SENTINEL,new String[] {SENTINEL,SENTINEL,rId,rId,SENTINEL,
                 SENTINEL},true);
        t.putPts(rId,new String[] {SENTINEL,SENTINEL,SENTINEL,SENTINEL,SENTINEL,
                 SENTINEL},
                 true);
        t.putContent(rId,theRoot.getContent(),true);
        // BUGFIX-20061023-1: Init did not close files, which breaks tree re-open
        // on some platforms (notably symbian emulator)
        t.close();
      } catch (Exception e) {
        Log.log("Failed to init root", Log.FATALERROR,e);
      }
    }
  }

}
// arch-tag: 5d71c49c7969d966121e7df1ceaac8f4 *-
