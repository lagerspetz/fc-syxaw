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

// $Id: DirectoryEntry.java,v 1.9 2005/02/10 10:22:06 ctl Exp $
package fc.syxaw.hierfs;

import java.io.IOException;
import java.util.Iterator;

import fc.syxaw.fs.Constants;
import fc.util.log.Log;
import fc.xml.xas.AttributeNode;
import fc.xml.xas.Item;
import fc.xml.xas.ItemTarget;
import fc.xml.xas.Qname;
import fc.xml.xas.StartTag;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.KeyIdentificationModel;
import fc.xml.xmlr.model.StringKey;
import fc.xml.xmlr.xas.PeekableItemSource;

/** Entries in a Syxaw directory tree. This class encapsulates entries for
 * the directory trees used by the synchronization protocol. A XAS codec
 * is provided for XML serialization/deserialization. */

public interface DirectoryEntry extends RefTrees.IdentifiableContent {

  /** Constant indicating missing type. */
  public static final int NONE = -1;

  /** Constant indicating directory tree root entry. */
  public static final int TREE = 1;

  /** Constant indicating directory type entry. */
  public static final int DIR= 2;

  /** Constant indicating file type entry. */
  public static final int FILE= 3;

  /** Xas codec for instances of this class. */
  public static final XasCodec XAS_CODEC = new XasCodec();

  static final XasCodec DEBUG_XAS_CODEC = new XasCodec(false);

  /** Get id.
   *
   * @return id
   */
  public Key getId();

  /** Get location.
   *
   * @return location
   */
  public String getLocationId();

  /** Get name.
   *
   * @return name
   */
  public String getName();

  /** Get next id. <b>Will be deprecated.</b>
   *
   * @return next id
   */
  public String getNextId();

  /** Get entry type. The type is one of
   * {@link #NONE NONE},{@link #TREE TREE},{@link #FILE FILE},
   * {@link #DIR DIR}.
   *
   * @return entry type.
   */
  public int getType();

  /** Get UID.
   *
   * @return UID
   */
  public String getUid();

  /** Get version.
   *
   * @return version.
   */
  public int getVersion();

  /** Default immutable implementation of <code>DirectoryEntry</code>. */
  public class DirectoryEntryImpl implements DirectoryEntry {

    protected int type;
    protected StringKey id;
    protected String name;
    protected String locationId;
    protected int version;
    protected String uid;
    protected String nextId;

    public int getType() {
      return type;
    }

    public Key getId() {
      return id;
    }

    public String getLinkId() {
      return getId().toString();
    }

    public String getLocationId() {
      return locationId;
    }

    public String getNextId() {
      return nextId;
    }

    public String getName() {
      return name;
    }

    public String getUid() {
      return uid;
    }

    public int getVersion() {
      return version;
    }

    /** Create a new entry.
     *
     * @param aType type
     * @param aId id
     * @param aLocationId location
     * @param aName name
     * @param aNextId nextid
     * @param aUid uid
     * @param aVersion version
     */
    public DirectoryEntryImpl(int aType, StringKey aId, String aLocationId, String aName,
                       String aNextId, String aUid, int aVersion ) {
      type = aType;
      id = aId;
      locationId = aLocationId;
      name = aName;
      uid = aUid;
      version = aVersion;
      nextId = aNextId;
    }
    
    public String toString() {
      return "{type="+type+", id="+id+", name="+name+
      " ,locationId="+locationId+", version="+version+", uid="+uid+
      ", nextId="+nextId+"}";
 
    }
    
  }

  /** XAS codec for {@link DirectoryEntry} */
  static class XasCodec implements fc.xml.xmlr.model.XasCodec {

    protected boolean strict = true;

    protected XasCodec() {
      this(true);
    }

    protected XasCodec(boolean strict) {
      this.strict = strict;
    }

    /** Tag for <code>TREE</code> type entry. */
    public static final Qname TREE_TAG = new Qname("","tree");
    /** Tag for <code>DIR</code> type entry. */
    public static final Qname DIR_TAG = new Qname("","directory");
    /** Tag for <code>FILE</code> type entry. */
    public static final Qname FILE_TAG = new Qname("","file");

    /** Attribute for <code>id</code>. */
    public static final Qname ID_ATTR = new Qname("","id");
    /** Attribute for <code>name</code>. */
    public static final Qname NAME_ATTR = new Qname("","name");
    /** Attribute for <code>location</code>. */
    public static final Qname LID_ATTR = new Qname("","lid");
    /** Attribute for <code>uid</code>. */
    public static final Qname UID_ATTR = new Qname("","uid");
    /** Attribute for <code>nextid</code>. */
    public static final Qname NEXTID_ATTR = new Qname("","nextid");
    /** Attribute for <code>version</code>. */
    public static final Qname VERSION_ATTR = new Qname("","version");

    public Object decode(PeekableItemSource is, KeyIdentificationModel kim)
        throws IOException {
      int type = DirectoryEntry.NONE;
      StringKey id = null;
      String nextId = null;
      String locationId = null;
      String name = null;
      int version = Constants.NO_VERSION;
      boolean readVersion = false;
      String uid = null;

      if ( !Item.isStartTag( is.peek() ) )
        throw new IOException("Expected directory element");
      StartTag st = (StartTag) is.next();
      Qname tagName = st.getName();
      if (TREE_TAG.equals(tagName))
        type = DirectoryEntry.TREE;
      else if (DIR_TAG.equals(tagName))
        type = DirectoryEntry.DIR;
      else if (FILE_TAG.equals(tagName))
        type = DirectoryEntry.FILE;
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
      };

      if (type == DirectoryEntry.NONE ||
          !isset[0] ||
          (type == DirectoryEntry.TREE &&
           (!isset[1] || !isset[2] || isset[3] || isset[4] || isset[5] )) ||
          (type == DirectoryEntry.FILE &&
           (isset[1] || isset[2] || !isset[3] || !isset[4] || !isset[5] )) ||
          (type == DirectoryEntry.DIR &&
           (isset[1] || isset[2] || !isset[3] || isset[4] || isset[5] ))
          )
        throw new IOException("Too many/missing attributes for id " + id);
      return new DirectoryEntryImpl(type, id, locationId, name, nextId, uid, version );
    }

    // Writer part
    public void encode(ItemTarget t, RefTreeNode m, StartTag context) throws IOException {
      Object o = m.getContent();
      if (! (o instanceof DirectoryEntry))
        Log.log("Wrong class", Log.ASSERTFAILED);
      DirectoryEntry n = (DirectoryEntry) o;
      switch (n.getType()) {
        case DirectoryEntry.FILE: {
          StartTag stf = new StartTag(FILE_TAG,context);
          stf.addAttribute( ID_ATTR, n.getId().toString());
          stf.addAttribute( NAME_ATTR, n.getName());
          stf.addAttribute( VERSION_ATTR, String.valueOf(n.getVersion()));
          stf.addAttribute( UID_ATTR, mshall(n.getUid()));
          t.append(stf);
          break;
        }
        case DirectoryEntry.DIR: {
          StartTag std = new StartTag(DIR_TAG,context);
          std.addAttribute( ID_ATTR, n.getId().toString());
          std.addAttribute(  NAME_ATTR, n.getName());
          t.append(std);
          break;
        }
        case DirectoryEntry.TREE: {
          StartTag stt = new StartTag(TREE_TAG,context);
          stt.addAttribute( ID_ATTR, n.getId().toString());
          stt.addAttribute( LID_ATTR, n.getLocationId());
          stt.addAttribute( NEXTID_ATTR, n.getNextId());
          t.append(stt);
          break;
        }
      }
    }

    protected String mshall(String s) {
      if( s == null && !strict)
        return "";
      return s;
    }

  }

}
// arch-tag: ca6ec9c378d4ebf6412f0ba132535ee3 *-
