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

package fc.xml.xmlr.xas;

import java.io.IOException;
import java.util.Iterator;

import fc.xml.xas.AttributeNode;
import fc.xml.xas.EndTag;
import fc.xml.xas.ItemTarget;
import fc.xml.xas.Qname;
import fc.xml.xas.StartTag;
import fc.xml.xmlr.Reference;
import fc.xml.xmlr.TreeReference;
import fc.xml.xmlr.model.KeyModel;

/** Reference item holding a tree reference. */
public class RefTreeItem extends RefItem {
  
  private static final long serialVersionUID = 6720141072659354612L;

  public static final int TREE_REFERENCE = 0x3101;
  private Object target; 
  private StartTag st;

  RefTreeItem(StartTag t, StartTag ctx) throws IOException {
    super(TREE_REFERENCE);
    st = new StartTag(REF_TAG_TREE,ctx);
    st.ensurePrefix(REF_NS, "ref");
    AttributeNode n = t.getAttribute(REF_ATT_TARGET);
    n = n != null ? n :  t.getAttribute(REF_ATT_ID);
    if( n== null )
      throw new IOException("Missing reference target "+REF_ATT_ID);
    st.addAttribute(n.getName(), n.getValue());
    target = n.getValue(); 
  }

  RefTreeItem(Object t, StartTag ctx) {
    super(TREE_REFERENCE);
    st = new StartTag(REF_TAG_TREE,ctx);
    st.ensurePrefix(REF_NS, "ref");
    target = t;
  }
  
  public Object getTarget() {
    return target;
  }

  /*!5 @Override */
  public boolean isTreeRef() {
    return true;
  }

  /*!5 @Override */
  public Iterator getExtraAttributes() {
    return st.attributes();
  }

  /*!5 @Override */
  public AttributeNode getAttribute (Qname n) {
    return st.getAttribute(n);
  }
  
  /*!5 @Override */
  public void addExtraAttribute(AttributeNode aan) {
    st.addAttribute(aan.getName(), aan.getValue());
  }

  /*!5 @Override */
  public Reference createReference(KeyModel km) throws IOException {
    return new TreeReference( km.makeKey( target ) );
  }

  /*!5 @Override */
  public StartTag getContext() {
    return st;
  }

  public void appendTo(ItemTarget it) throws IOException {
    if( !st.ensureAttribute(REF_ATT_ID, target.toString()).equals(target.toString()) ) {
      st.addAttribute(REF_ATT_TARGET, target.toString());
    }
    EndTag et = new EndTag(REF_TAG_TREE);
    it.append(st);
    //  BUGFIX-20070216-1
    it.append(et);
  }

  @Override
  public String toString() {
    return "RT("+getTarget()+")";
  }

}

//arch-tag: 374869b3-5e60-4c60-b56c-7b74545eee85
