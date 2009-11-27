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

// $Id: StoredDirectoryTree.java,v 1.1 2005/06/08 13:14:27 ctl Exp $
// Was previously in storage/xfs as
// Id: StoredDirectoryTree.java,v 1.44 2005/02/10 13:56:11 ctl Exp

package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import fc.syxaw.fs.Constants;
import fc.syxaw.hierfs.DirectoryEntry;
import fc.syxaw.util.Util;
import fc.util.log.Log;
import fc.xml.xas.AttributeNode;
import fc.xml.xas.Item;
import fc.xml.xas.ItemTarget;
import fc.xml.xas.Qname;
import fc.xml.xas.StartTag;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.NodeNotFoundException;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.model.KeyIdentificationModel;
import fc.xml.xmlr.model.StringKey;
import fc.xml.xmlr.model.XasCodec;
import fc.xml.xmlr.xas.PeekableItemSource;

// NOTE: It is easy to make this a memory-based db by extending ChangeTree;
// be sure to grab the root node from Maintenance.init() though!

/** Syxaw persistent directory tree. A persistent directory tree
 * storing {@link StoredDirectoryTree.Node} nodes. The nodes of this
 * tree is exposed as local and link facets using the {@link LocalTree} and
 * {@link LinkTree} classes.
 */

public class StoredDirectoryTree extends PersistentRefTree {

  private static final XasCodec codec = new NodeXasCodec();
  protected ChangeMonitor cm;

  /** Create a new tree.
   *
   * @param root directory of the persistent storage
   */

  public StoredDirectoryTree(File root) {
    this(root,null);
  }

  public StoredDirectoryTree(File root, ChangeMonitor cm) {
    super(root,codec);
    this.cm = cm == null ? new ChangeMonitor() : cm;
  }

  // Mutability

  public void delete( Key id ) throws NodeNotFoundException {
    //!A assert id instanceof StringKey;
    cleanUIDs(id); //cm.delete(id);
    super.delete(id);
  }

  public Key insert(Key parentId,long pos,Key newId, Object content) {
   /*if( ((Node) content).getType() == Node.FILE &&
        Util.isEmpty(((Node) content).getUid()))
        Log.log("==NULL UID INSERT",Log.ASSERTFAILED);*/
   //cm.insert(parentId,newId,content);
    return super.insert(parentId,pos,newId,content);
  }

  public Key move(Key nodeId, Key parentId, long pos) throws
      NodeNotFoundException {
    //cm.move(nodeId,parentId);
    return super.move(nodeId, parentId, pos);
  }

  public boolean update(Key nodeId, Object content) throws
      NodeNotFoundException {
    /*if( ((Node) content).getType() == Node.FILE &&
        Util.isEmpty(((Node) content).getUid()))
        Log.log("==NULL UID UPDATE",Log.ASSERTFAILED);*/
    //cm.update(nodeId,content);
    return super.update(nodeId,content);
  }

  // Persistence

  protected boolean isLeaf(String id, Object content) {
    return ((Node) content).getType() == Node.FILE;
  }

  /*Deprecated
  protected void cacheContent(String id, Object content) {
    Object cachedContent = new NodeImpl((Node) content);
    contentCache.put(id,cachedContent);
  }*/

  protected void cleanUIDs(Key id) throws NodeNotFoundException {
    RefTreeNode n = getNode(id);
    for( Iterator i = childIterator(id); i.hasNext();) {
      cleanUIDs((Key) i.next());
    }
    Node de =(Node) n.getContent();
    if( de.getType()!=DirectoryEntry.FILE )
      return;
  //  Log.log("===Freeing UID "+de.getUid(),Log.DEBUG);
    String uid = de.getUid();
    if( !Util.isEmpty(uid) )
      cm.freeUID(uid);
    else
      Log.log("File with empty uid.",Log.WARNING,de);
/*    SyxawFile fbyUID = (SyxawFile) rootDir.newInstance(
        UID.createFromBase64(de.getUid()));
    if( fbyUID.delete() )
      Log.log("UIDCleaned "+fbyUID,Log.INFO, new Throwable());*/
  }

  // Immutable node

  /** Nodes in the stored directory tree. A node stored in the
   * directory tree  is a union of the local and link facet directory
   * entry.
   */

  public interface Node extends DirectoryEntry {

    /** Get local facet node unqiue id.
     *
     * @return id
     */
    public Key getId();

    /** Get link facet node id. Currently hard-coded to be the same as
     * {@link #getId}.
     *
     * @return linkId
     */
    public StringKey getLinkId();

    /** Get link facet next id.
     *
     * @return link next id
     */
    public String getLinkNextId();

    /** Get link facet uid.
     *
     * @return link facet uid
     */
    public String getLinkUid();

    /** Get link facet version.
     *
     * @return link facet version
     */
    public int getLinkVersion();

    /** Get link facet location id.
     *
     * @return location id.
     */
    // NOTE! Only for tree as of yet!
    public String getLocationId();

    /** Get link/local facet name.
     * @return name
     */
    public String getName();

    /** Get local facet next id.
     * @return local facet next id
     */
    public String getNextId();

    /** Get link/local facet type.
     *
     * @return type
     */
    public int getType();

    /** Get local facet UID.
     *
     * @return local facet UID.
     */
    public String getUid();

    /** Get local facet version.
     *
     * @return locla facet version.
     */
    public int getVersion();

  }

  /** Implementation of StoredDirectoryTree.Node.
   */

  public static class NodeImpl implements Node {
    // Note some fields are doubled in order to save space.
    // I think this is begging for hard-to-find ser/deser problems,
    // but I did it anyway
    // NOTE2: linkid == id implementation

    protected int type;
    protected StringKey id;
    protected String name; // double as nextId
    protected String locationId;
    protected int version;
    protected String uid;

    protected int linkVersion;
    protected String linkUid; // double as linkNextId


    // private String nextId;
    // private String linkNextId;


    public int getType() {
      return type;
    }

    public Key getId() {
      return id;
    }

    public StringKey getLinkId() {
      return id;
    }

    public String getLocationId() {
      return locationId;
    }

    public String getNextId() {
      if( type != TREE )
        return null;
      return name;
    }


    public String getName() {
      if( type == TREE )
        return null;
      return name;
    }

    public String getUid() {
      return uid;
    }

    public int getVersion() {
      return version;
    }

    public int getLinkVersion() {
      return linkVersion;
    }

    public String getLinkUid() {
      if( type == TREE )
        return null;
      return linkUid;
    }

    public String getLinkNextId() {
      if( type != TREE )
        return null;
      return linkUid;
    }

    /** Create new object.
     *
     * @param aType type
     * @param aId id
     * @param aLocationId location id
     * @param aName name
     * @param aNextId next id
     * @param aUid uid
     * @param aVersion version
     * @param aLinkVersion link version
     * @param aLinkUid link uid
     * @param aLinkNextId link next id
     * @param linkId link id
     */
    public NodeImpl(int aType, StringKey aId, String aLocationId, String aName,
                       String aNextId, String aUid, int aVersion,
                       int aLinkVersion, String aLinkUid, String aLinkNextId,
                       StringKey linkId ) {
      type = aType;
      id = aId;
      locationId = aLocationId;
      name = type == TREE ? aNextId : aName;
      uid = aUid;
      version = aVersion;
      linkVersion = aLinkVersion;
      linkUid = type==TREE ? aLinkNextId :aLinkUid;
    }

    public NodeImpl(Node n) {
      type = n.getType();
      id = (StringKey) n.getId();
      locationId = n.getLocationId();
      name = type == TREE ? n.getNextId(): n.getName();
      uid = n.getUid();
      version = n.getVersion();
      linkVersion = n.getLinkVersion();
      linkUid = type == TREE ? n.getLinkNextId() : n.getLinkUid();
    }

    public boolean equals(Object o) {
      return o instanceof Node && (
          ((Node) o).getType() == getType() &&
          Util.equals( ( (Node) o).getId(), getId()) &&
          (type != Node.TREE  || // Lid indifferent for files/dirs
           Util.equals( ( (Node) o).getLocationId(), getLocationId())) &&
          (type == Node.TREE || // name indif for tree
           Util.equals( ( (Node) o).getName(), getName())) &&
          ( type != Node.FILE || // uid only in file
            Util.equals( ( (Node) o).getUid(), getUid())) &&
          ( type != Node.FILE || // version only in file
            ((Node) o).getVersion() == getVersion()) &&
          ( type != Node.FILE || // linkversion only in file
            ((Node) o).getLinkVersion() == getLinkVersion()) &&
          ( type != Node.FILE || // linkuid only in file
            Util.equals( ( (Node) o).getLinkUid(), getLinkUid()))
          );

    }

  }

  /** Mutable Node. */
  static class MutableNode extends NodeImpl {


    public void setType(int aType) {
      type = aType;
    }

    public void setId(StringKey aId) {
      id = aId;
    }

    public void setLocationId(String aLocationId) {
      locationId = aLocationId;
    }


    public void setName(String aName) {
      if( type == TREE )
        Log.log("Wrong type",Log.ASSERTFAILED);
      name = aName;
    }

    public void setNextId(String aNextId) {
      if( type != TREE )
        Log.log("Wrong type",Log.ASSERTFAILED);
      name = aNextId;
    }


    public void setUid(String aUid) {
      uid = aUid;
    }


    public void setVersion(int aVersion) {
      version = aVersion;
    }


    public void setLinkVersion(int aLinkVersion) {
      linkVersion = aLinkVersion;
    }


    public void setLinkUid(String aLinkUid) {
      if( type == TREE )
        Log.log("Wrong type",Log.ASSERTFAILED);
      linkUid = aLinkUid;
    }

    public void setLinkNextId(String aLinkNextId) {
      if( type != TREE )
        Log.log("Wrong type",Log.ASSERTFAILED);
      linkUid = aLinkNextId;
    }

    public MutableNode(int aType, StringKey aId, String aLocationId, String aName,
                       String aNextId, String aUid, int aVersion,
                       int aLinkVersion, String aLinkUid, String aLinkNextId,
                       StringKey aLinkId) {
      super(aType, aId, aLocationId, aName, aNextId, aUid, aVersion,
            aLinkVersion, aLinkUid, aLinkNextId, aLinkId);
    }
  }

  // DirTree node ser/deser. The mapping is selective in which attributes are
  // read/required and which are written + some fields undergo mapping (the
  // type filed) -> custom codec instead pf generic bean serializer
  // NOTE: this class is currently almost identical to the DirEntry codecs 
  // They should however be INDEPENDENT, as this class is for the internal
  // dirtree storage format, and DirEntry is part of the protocol!
  // (they just happen to be very similar)

  /** Xas codec for <code>StoredDirectoryTree.Node</code>. */
  static class NodeXasCodec implements XasCodec {

    public static final Qname TREE_TAG = new Qname("","tree");
    public static final Qname DIR_TAG = new Qname("","directory");
    public static final Qname FILE_TAG = new Qname("","file");

    public static final Qname ID_ATTR = new Qname("","id");
    public static final Qname NAME_ATTR = new Qname("","name");
    public static final Qname LID_ATTR = new Qname("","lid");
    public static final Qname UID_ATTR = new Qname("","uid");
    public static final Qname NEXTID_ATTR = new Qname("","nextid");
    public static final Qname VERSION_ATTR = new Qname("","version");
    public static final Qname LINK_VERSION_ATTR = new Qname("","linkversion");
    public static final Qname LINK_UID_ATTR = new Qname("","linkuid");
    public static final Qname LINK_NEXTID_ATTR = new Qname("","linknextid");

    public Object decode(PeekableItemSource is, KeyIdentificationModel kim) throws IOException {
      int type = MutableNode.NONE;
      StringKey id = null;
      String nextId = null;
      String locationId = null;
      String name = null;
      int version = Constants.NO_VERSION;
      boolean readVersion = false;
      String uid = null;
      int linkVersion= Constants.NO_VERSION;
      boolean readLinkVersion = false;
      String linkUid=null;
      String linkNextId=null;

      if ( !Item.isStartTag( is.peek() ) )
        throw new IOException("Expected directory element");
      StartTag st = (StartTag) is.next();
      Qname tagName = st.getName();
      if (TREE_TAG.equals(tagName))
        type = Node.TREE;
      else if (DIR_TAG.equals(tagName))
        type = Node.DIR;
      else if (FILE_TAG.equals(tagName))
        type = Node.FILE;
      else
        throw new IOException("Unknown tag: " + tagName);
      for( Iterator i = st.attributes();i.hasNext();) {
        AttributeNode an = (AttributeNode) i.next();
        Qname att = an.getName();
        String val = an.getValue().toString();
        if (ID_ATTR.equals(att))
          id = StringKey.createKey( val );
        else if (VERSION_ATTR.equals(att)) {
          try {
            version = Integer.parseInt(val);
            readVersion = true;
          } catch (NumberFormatException x) {
            throw new IOException("Invalid version: " + val);
          }
        } else if (NAME_ATTR.equals(att))
          name = val;
        else if (LID_ATTR.equals(att))
          locationId = val;
        else if (UID_ATTR.equals(att))
          uid = val;
        else if (NEXTID_ATTR.equals(att))
          nextId = val;
        else if (LINK_VERSION_ATTR.equals(att)) {
          try {
            linkVersion = Integer.parseInt(val);
            readLinkVersion = true;
          } catch (NumberFormatException x) {
            throw new IOException("Invalid link version: " + val);
          }
        } else if (LINK_UID_ATTR.equals(att))
          linkUid = val;
        else if (LINK_NEXTID_ATTR.equals(att))
          linkNextId = val;
        else
          throw new IOException("Unknown attribute: " + att);
        // Check that each type has correct atts
      }
      boolean isset[] = {
          id != null, // 0
          nextId != null, // 1
          locationId != null, // 2
          name != null, // 3
          readVersion, // 4
          uid != null, // 5
          readLinkVersion, //6
          linkUid != null, //7
          linkNextId != null //8
      };

      if (type == Node.NONE ||
          !isset[0] ||
          (type == Node.TREE &&
           (!isset[1] || !isset[2] || isset[3] || isset[4] || isset[5] ||
            isset[6] || isset[7] || !isset[8])) ||
          (type == Node.FILE &&
           (isset[1] || isset[2] || !isset[3] || !isset[4] ||
            !isset[5] || /*6,7 indif*/ isset[8])) ||
          (type == Node.DIR &&
           (isset[1] || isset[2] || !isset[3] || isset[4] || isset[5] ||
            isset[6] || isset[7] || isset[8]))
          )
        throw new IOException("Too many/missing attributes for id " + id);
      return new MutableNode(type, id, locationId, name, nextId, uid, version,
                       linkVersion, linkUid, linkNextId, id );
    }

    // Writer part
    public void encode(ItemTarget t, RefTreeNode m, StartTag context) throws IOException {
      Object o = m.getContent(); 
      if (! (o instanceof Node))
        Log.log("Wrong class", Log.ASSERTFAILED);
      Node n = (Node) o;
      switch (n.getType()) {
        case DirectoryEntry.FILE: {
          StartTag stf = new StartTag(FILE_TAG,context);
          stf.addAttribute( ID_ATTR, n.getId().toString());
          stf.addAttribute( NAME_ATTR, n.getName());
          stf.addAttribute( VERSION_ATTR, String.valueOf(n.getVersion()));
          stf.addAttribute( UID_ATTR, n.getUid());
          if( !Util.isEmpty(n.getLinkUid())) {
            stf.addAttribute( LINK_UID_ATTR, n.getLinkUid());
            stf.addAttribute( LINK_VERSION_ATTR, String.valueOf(
                n.getLinkVersion()));
          }
          t.append(stf);
          break;
        }

        case DirectoryEntry.DIR: {
          StartTag std = new StartTag(DIR_TAG,context);
          std.addAttribute( ID_ATTR, n.getId().toString());
          std.addAttribute( NAME_ATTR, n.getName());
          t.append(std);
          break;
        }
        case DirectoryEntry.TREE: {
          StartTag stt = new StartTag(TREE_TAG,context);
          stt.addAttribute( ID_ATTR, n.getId().toString()) ;
          stt.addAttribute( LID_ATTR, n.getLocationId());
          stt.addAttribute( NEXTID_ATTR, n.getNextId());
          if( !Util.isEmpty(n.getLinkNextId())) {
            stt.addAttribute( LINK_NEXTID_ATTR, n.getLinkNextId());
          }
          t.append(stt);
          break;
        }
      }
    }
  }

  /** Class for tree change monitoring. The default implementation
   *  does nothing. */

  public static class ChangeMonitor {

    public void freeUID(String uid){};

  }

  /** Stored directory tree maintenance. */
  public static class Maintenance {
    /** Initialize tree
     *
     * @param aStore directory to store tree in
     * @param theRoot root node
     */
    public static void init(File aStore, RefTreeNode theRoot ) {
      PersistentRefTree.Maintenance.init(aStore,theRoot,new NodeXasCodec());
    }
  }
}
// arch-tag: 1a2b09d3c529206a9d55ffae8ca9cd8d *-
