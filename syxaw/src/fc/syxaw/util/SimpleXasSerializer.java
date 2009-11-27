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

// $Id: SimpleXasSerializer.java,v 1.9 2005/01/20 10:59:01 ctl Exp $

package fc.syxaw.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import fc.syxaw.transport.PropertyDeserializer;
import fc.syxaw.transport.PropertySerializable;
import fc.syxaw.transport.PropertySerializer;
import fc.util.log.Log;
import fc.xml.xas.AttributeNode;
import fc.xml.xas.EndTag;
import fc.xml.xas.Item;
import fc.xml.xas.ItemSource;
import fc.xml.xas.ItemTarget;
import fc.xml.xas.Qname;
import fc.xml.xas.SerializerTarget;
import fc.xml.xas.StartTag;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.KeyIdentificationModel;
import fc.xml.xmlr.model.XasCodec;
import fc.xml.xmlr.xas.PeekableItemSource;

/** Simple Java Beans to XAS serializer. The bean is serialized as a single
 * element <code>&lt<i>name attr<sub>1</sub>="val<sub>1</sub>" ...
 * attr<sub>n</sub>="val<sub>n</sub>"
 * </i> .../&gt</code>
 * where <i>name</i> is the local serial name of the class, if
 * the object implements
 * {@link fc.syxaw.transport.PropertySerializable}; otherwise the
 * default name <code>object</code> is used. The values are encoded as
 * specified by {@link fc.syxaw.transport.PropertySerializer}.
 * <p><b>Note on fp</b> that the runtime object must be
 * {@link fc.syxaw.transport.PropertySerializable}
 *
 * <p>Note that nested beans are serialized as any other object as an attribute
 * value using the <code>toString()</code> method.
 */

public class SimpleXasSerializer implements XasCodec {

  private static final String NAMESPACE = "";
  private Class beanClass = null;
  private Stack left = new Stack();

  public SimpleXasSerializer(Class beanClass) {
    //!A assert beanClass != null;
    this.beanClass = beanClass;
  }

  /** Create serializer.
   *
   */
  public SimpleXasSerializer() {
  }

  /** Start object.
   *
   * @param t item target to write to
   * @param n Node to write
   * @throws IOException if an I/O error occurs
   */
  public void encode(ItemTarget t, RefTreeNode n, StartTag context) throws IOException {
    Object obj = n.getContent();
    if( obj instanceof PropertySerializable )
      writeObject((PropertySerializable) obj,"",t, context);
    else
      Log.log("Object to class must be PropertySerializable "+obj.getClass(),
              Log.FATALERROR);
  }

  public void writeObject(Object obj, ItemTarget t ) 
    throws IOException {
    if( obj instanceof PropertySerializable )
      writeObject((PropertySerializable) obj, "", t,
          (t instanceof SerializerTarget ? 
              ((SerializerTarget) t).getContext() : null));
    else
      Log.log("Object to class must be PropertySerializable "+obj.getClass(),
              Log.FATALERROR);
  }
  
  protected void writeObject(PropertySerializable obj, String nameSpace, 
                            ItemTarget t, StartTag context ) throws IOException {
   Map list = new HashMap();
   XasPropSerializer ps = new XasPropSerializer(list);
   String tagName = ps.writeObject(obj,"");
   if( tagName == null ) {
     Log.log("Not PropertySerializable class: "+obj.getClass().getName()+
             ". Performance is not optimal",Log.WARNING);
     tagName = "object";
   }
   StartTag st = new StartTag( new Qname(NAMESPACE,tagName), context );
   for( Iterator i = list.entrySet().iterator();i.hasNext(); ) {
     Map.Entry e = (Map.Entry) i.next();
     st.addAttribute(new Qname(NAMESPACE,(String) e.getKey()),
                                        (String)e.getValue());
   }
   t.append(st);
   left.push(new EndTag(st.getName()));
  }

  public void finishObject(Object o, ItemTarget t) throws IOException {
    t.append((Item) left.pop());
  }

  public static void finishObject(Object o, ItemSource is) throws IOException {
    Item i = is.next();
    if( !Item.isEndTag(i) )
      throw new IOException("Expected end tag, got "+i);
  }
  
  
  private static class XasPropSerializer extends PropertySerializer {
    public XasPropSerializer(Map dataList) {
      super(dataList);
    }

    public String writeObject(PropertySerializable obj, String nameSpace) throws
        IOException {
      return super.writeObject(obj,nameSpace);
    }

  }

  public Object decode(PeekableItemSource is, KeyIdentificationModel kim) throws IOException {
    Object obj = null;
    try {
      obj=beanClass.newInstance();
      if( !(obj instanceof RefTrees.IdentifiableContent ))
        Log.log("Bean class "+(obj == null ? "<null obj>" : 
            obj.getClass().toString())+
            " does not implement RefTrees.IdentifiableContent",Log.ASSERTFAILED);    
    } catch (Exception e) {
      Log.log("Could not instantiate object of class "+beanClass,
          Log.FATALERROR);
    } 
    readObject(obj, is);  
    return obj;
  }

  public static void readObject(Object obj, PeekableItemSource is ) 
    throws IOException {
    Map dataList = new HashMap();
    Item objElement = is.next();
    if( !Item.isStartTag(objElement) ) {
      Log.log("Expected open tag, next ev="+is.next()+" then",Log.ERROR,
              new Object[] {is.next(),is.next(),is.next()});
      throw new IOException("Expected object start tag");
    }
    for( Iterator i = ((StartTag) objElement).attributes();i.hasNext();)  {
      AttributeNode an = (AttributeNode) i.next();
      dataList.put(an.getName().getName(),an.getValue());
    }
    PropertyDeserializer des = new PropertyDeserializer(dataList);
    des.readObject((PropertySerializable) obj);
  }
  
}
// arch-tag: 40d39daa7a0709a55e2f958eecf55af7 *-
