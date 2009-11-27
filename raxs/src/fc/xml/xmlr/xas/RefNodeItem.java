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
import fc.xml.xas.Item;
import fc.xml.xas.ItemTarget;
import fc.xml.xas.Qname;
import fc.xml.xas.SerializerTarget;
import fc.xml.xas.StartTag;
import fc.xml.xmlr.NodeReference;
import fc.xml.xmlr.Reference;
import fc.xml.xmlr.model.KeyModel;

/** Reference item holding a node reference. */
public class RefNodeItem extends RefItem  {

  public static final int NODE_REFERENCE = 0x3102;
  public static final RefNodeItem END_REF = new RefNodeItem((Object) null,null);

  private Object target;
  private StartTag st;
  
  RefNodeItem(StartTag t, StartTag ctx) throws IOException {
    super(NODE_REFERENCE);
    st = new StartTag(REF_TAG_NODE,ctx);
    st.ensurePrefix(REF_NS, "ref");
    AttributeNode n = t.getAttribute(REF_ATT_TARGET);
    n = n != null ? n :  t.getAttribute(REF_ATT_ID);
    if( n== null )
      throw new IOException("Missing reference target "+REF_ATT_ID);
    st.addAttribute(n.getName(), n.getValue());
    target = n.getValue();
    //A! assert target != null;
  }

  // t =null -> end nref
  RefNodeItem(Object t, StartTag ctx) {
    super(NODE_REFERENCE);
    // BUGFIX-20070216-1
    st = new StartTag(REF_TAG_NODE,ctx);
    st.ensurePrefix(REF_NS, "ref");
    target = t;
  }
  
  RefNodeItem(EndTag t) {
    super(NODE_REFERENCE);
    st = new StartTag(REF_TAG_NODE);
    //  BUGFIX-20070216-1
    st.ensurePrefix(REF_NS, "ref");
  }

  // NOTE: Illegal to ask target of end tag
  public Object getTarget() throws IllegalStateException {
    if( target == null )
      throw new 
      IllegalStateException("Tried to ask target from node ref end tag");
    return target;
  }
  
  public boolean isEndTag() {
    return target == null;
  }

  /*!5 @Override */
  public boolean isTreeRef() {
    return false;
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
    return new NodeReference( km.makeKey( target ) );
  }

  /*!5 @Override */
  public StartTag getContext() {
    return st;
  }

  public void appendTo(ItemTarget it) throws IOException {
    Item i=null;
    if( isEndTag() ) {
      i = new EndTag(REF_TAG_NODE);
    } else {
      // JK NOTE: new code to use new method in StartTag
      if( !st.ensureAttribute(REF_ATT_ID, target.toString()).equals(target.toString()) ) {
        st.addAttribute(REF_ATT_TARGET, target.toString());
      }

      i = st;
    }
    it.append(i);    
  }

  @Override
  public String toString() {
    return isEndTag() ? "RNE()" : "RNS("+getTarget()+")";
  }

}

//arch-tag: d3c80702-ae80-4b3e-8d6d-8dff2280b5cc
