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

// $Id: Registry.java,v 1.3 2005/02/10 13:56:14 ctl Exp $
package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.io.IOException;

import fc.util.log.Log;
import fc.xml.xas.Item;
import fc.xml.xas.ItemTarget;
import fc.xml.xas.Qname;
import fc.xml.xas.StartTag;
import fc.xml.xmlr.Key;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTreeNodeImpl;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.KeyIdentificationModel;
import fc.xml.xmlr.model.StringKey;
import fc.xml.xmlr.model.XasCodec;
import fc.xml.xmlr.xas.PeekableItemSource;

/** Syxaw file system registry. Used to store persistent metadata about
 * the file system, such as the list of mergers.
 * <p>The data stored are pairs of string <key,value>s, as encoded to Xas
 * by the {@link Registry.KeyValue} class.
 */
public class Registry extends PersistentRefTree {

  /** Id of root. */
  public static final StringKey ROOT_KEY =  StringKey.createKey( "syxaw" );

  private static final XasCodec codec = new RegistryXasCodec();

  /** Instantiate registry.
   *
   * @param root root directory of registry.
   */
  public Registry(File root) {
    super(root,codec);
  }

  /*
  public Registry() {
    super(codec);
  }*/

  protected boolean isLeaf(String id, Object content) {
    return ((KeyValue) content).getValue() != null;
  }

  /* Deprecated
  protected void cacheContent(String id, Object content) {
    contentCache.put(id,content); // since its immutable
  }*/

  /** Syxaw registry key,value pair. */
  public static final class KeyValue implements RefTrees.IdentifiableContent {
    private StringKey key;
    private String val;

    /** Create new pair.
     *
     * @param key key string
     * @param val value string
     */
    public KeyValue(StringKey key,String val) {
      this.key = key;
      this.val = val;
    }

    /** Get key.
     *
     * @return key
     */
    public StringKey getKey() {
      return key;
    }

    /** Get id. Returns same as {@link #getKey}. Used for Xas serialization.
     *
     * @return id
     */
    public Key getId() {
      return key;
    }

    /** Get value.
     *
     * @return value
     */
    public String getValue() {
      return val;
    }
  }

  static class RegistryXasCodec implements XasCodec {
    
    public static final Qname ITEM = new Qname("","item");
    public static final Qname ID = new Qname("","id");
    public static final Qname VALUE = new Qname("","value");
    
    public void encode(ItemTarget t, RefTreeNode n, StartTag context) throws IOException {
      KeyValue kv = (KeyValue) n.getContent();
      boolean hasvalue = kv.getValue() != null;
      StartTag st = new StartTag(ITEM,context);
      st.addAttribute(ID,kv.getId().toString());
      if( hasvalue )
        st.addAttribute(VALUE, kv.getValue());
      t.append(st);
    }


    public Object decode(PeekableItemSource is, KeyIdentificationModel kim)
        throws IOException {
      if (!Item.isStartTag(is.peek()))
        throw new IOException("Expected start tag, got " + is.peek());
      StartTag st = (StartTag) is.next();
      return new KeyValue(StringKey.createKey( (String)
          st.getAttributeValue(ID) ),
          (String) st.getAttributeValue(VALUE));
    }
  }

  /** Maintenance code for the Syxaw Registry. */
  public static class Maintenance {
    /** Initialize a new registry.
     *
     * @param aStore root directory to store registry
     */
    public static void init(File aStore ) {
      if (!aStore.exists() && !aStore.mkdir())
        Log.log("Can't create " + aStore, Log.FATALERROR);
      RefTreeNode theRoot = new RefTreeNodeImpl(null, ROOT_KEY,
              new KeyValue(ROOT_KEY, null));
      PersistentRefTree.Maintenance.init(aStore,theRoot,new RegistryXasCodec());
    }
  }

}
// arch-tag: e3101510d22ee8525a566c42a9fe73b3 *-
