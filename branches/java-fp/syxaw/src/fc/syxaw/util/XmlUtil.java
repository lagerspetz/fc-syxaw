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

// $Id: XmlUtil.java,v 1.23 2005/01/03 11:58:56 ctl Exp $
package fc.syxaw.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import fc.xml.xas.EndDocument;
import fc.xml.xas.Item;
import fc.xml.xas.ItemSource;
import fc.xml.xas.ItemTarget;
import fc.xml.xas.Qname;
import fc.xml.xas.StartDocument;
import fc.xml.xas.StartTag;
import fc.xml.xas.TransformSource;
import fc.xml.xas.XmlOutput;
import fc.xml.xas.XmlPullSource;
import fc.xml.xas.transform.DataItems;
import fc.xml.xmlr.RefTree;
import fc.xml.xmlr.RefTreeNode;
import fc.xml.xmlr.RefTreeNodeImpl;
import fc.xml.xmlr.RefTrees;
import fc.xml.xmlr.model.XasCodec;
import fc.xml.xmlr.xas.PeekableItemSource;
import fc.xml.xmlr.xas.ReferenceItemTransform;
import fc.xml.xmlr.xas.XasSerialization;

import org.kxml2.io.KXmlParser;

/** Syxaw XML utilities class. */

public class XmlUtil {

  // true => use Xebu encoder (and decoder if !auto_decoder)
  static final boolean
      XEBU_SERIALIZATION=fc.syxaw.transport.Config.XEBU_XML;

  // true => accept both textual and Xebu data
  static final boolean AUTO_DECODER=true;

  private XmlUtil() {
  }

  /** Read a reftree from file using system default parsing.
   *
   * @param f file to read
   * @param cf ContentReader for node objects
   * @throws IOException if an I/O error occurs
   * @return read tree
   */
  public static RefTree readRefTree(File f, XasCodec cf)
      throws IOException {
    FileInputStream is = new FileInputStream(f);
    try {
      return readRefTree(is, cf);
    } finally {
      is.close();
    }
  }

  // FIXME-20061016-1: Not yet ported to XAS2
  //static final CodecFactory XEBU_FACTORY = XEBU_SERIALIZATION ?
  //    CodecIndustry.getFactory("application/x-ebu+item") : null;

  /** Get system default XML reader. The default reader wraps a
   * <code>ReferenceEventSequence</code> and a
   * <code>DataEventSequence</code> around the default
   * system parser.
   *
   * @param is InputStream to connect to reader
   * @throws IOException if an I/O error occurs
   * @return reader connected to <code>is</code>
   */
  public static ItemSource getDataSource(InputStream is) throws IOException {
    ItemSource dis = new TransformSource(getXmlParser(is), new DataItems());
    ItemSource ris = new TransformSource(dis, new ReferenceItemTransform());
    return ris;
  }

  /** Get system default XML parser.
   *
   * @param is InputStream to connect to the parser
   * @throws IOException if an I/O error occurs
   * @return parser connected to <code>is</code>
   */

  public static final XmlPullSource getXmlParser(InputStream is) throws IOException {
    /* FIXME-20061016-1
    TypedXmlParser pr = AUTO_DECODER ?
        XasExtUtil.getParser(is,"iso-8859-1") :
        (XEBU_SERIALIZATION ? XEBU_FACTORY.getNewDecoder(new Object()) :
         new DefaultXmlParser());
    if( pr == null ) {
      Log.log("Stream not recognized as XML", Log.WARNING);
      throw new IOException("Stream not recognized as XML");
    }
    if( !AUTO_DECODER ) {
      try {
        pr.setInput(new InputStreamReader(is, "iso-8859-1"));
      } catch (UnsupportedEncodingException ex) {
        Log.log("iso-8859-1 unknown", Log.ASSERT_FAILED);
      } catch (XmlPullParserException ex) {
        throw new IOException(ex.toString() + ": " + ex.getMessage());
      }
    }*/
    return new XmlPullSource(new KXmlParser(),is);
  }

  /** Read a reftree from stream using system default parsing.
   *
   * @param is stream to read
   * @param cf ContentReader for node objects
   * @throws IOException if an I/O error occurs
   * @return read tree
   */
  public static RefTree readRefTree(InputStream is, XasCodec cf)
      throws IOException {
    ItemSource its = getDataSource(is);
    return XasSerialization.readTree(its, cf);
  }

  /** Write reftree using system default serialization.
   *
   * @param t tree to write
   * @param out file to write to
   * @param cw ContentWriter node serializer
   * @throws IOException if an I/O error occurs
   */
  public static void writeRefTree(RefTree t, File out, XasCodec cw) throws
      IOException {
    FileOutputStream os = new FileOutputStream(out);
    try {
      writeRefTree(t, os, cw);
    } finally {
      os.close();
    }
  }

  /** Get system default document serializer.
   *
   * @param out OutputStream to connect serializer to
   * @throws IOException if an I/O error occurs
   * @return an instance of system default document serializer
   */

  public static XmlOutput getDocumentSerializer(OutputStream out) throws IOException {
    return getXmlSerializer(out,0x02+(XEBU_SERIALIZATION ? 0x01 : 0x00));
  }

  /** Get system default serializer.
   *
   * @param out OutputStream to connect serializer to
   * @throws IOException if an I/O error occurs
   * @return an instance of system default serializer
   */

  public static XmlOutput getXmlSerializer(OutputStream out) throws IOException {
    return getXmlSerializer(out,0x00+(XEBU_SERIALIZATION ? 0x01 : 0x00));
  }

  private static XmlOutput getXmlSerializer(OutputStream out,int type) throws IOException {
    // FIXME-20061016-1
    /*TypedXmlSerializer ser = (type & 0x1) == 0 ? new DefaultXmlSerializer() :
        XEBU_FACTORY.getNewEncoder(new Object());
    if( ser instanceof DefaultXmlSerializer )
      ser.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
    if((type&0x02)!=0)
      ser = XasSerialization.getDocumentSerializer(ser);
    try {
      Writer pw = new OutputStreamWriter(out, "iso-8859-1");
      ser.setOutput(pw);
    } catch (UnsupportedEncodingException ex) {
      Log.log("iso-8859-1 unknown",Log.ASSERT_FAILED);
    }*/
    return new XmlOutput(out,"UTF-8");
  }

  /** Get system default writer.
   *
   * @param out OutputStream to connect writer to
   * @throws IOException if an I/O error occurs
   * @return an instance of system default writer
   */
  // FIXME-20061016-1-1
  public static ItemTarget getDataTarget(OutputStream out) throws IOException {
    return getXmlSerializer(out);
  }

  public static void writeRefTree(RefTree t, OutputStream out,
                                  XasCodec cw) throws
      IOException {
    //XmlrDebug.dumpTree(t,System.out);
    XmlOutput writer = getXmlSerializer(out);
    //writer.append(StartDocument.instance());
    /* FIXME-20061016-1: How do we establish a set of prefixes to use?
    writer.addEvent(Event.createNamespacePrefix(ReferenceEvent.REF_NS,
                                                "ref"));
    writer.addEvent(Event.createNamespacePrefix(Diff.DIFF_NS,
                                                "diff"));
                                                */
    XasSerialization.writeTree(t, writer, cw);
    //writer.append(EndDocument.instance());
    writer.flush();
  }


  
  private static SimpleXasSerializer DEFAULT_BEAN_WRITER = 
    new SimpleXasSerializer();

  /** Get simple XAS content writer for beans. Uses
   * {@link SimpleXasSerializer} to write the bean.
   * @return ContentWriter for simple beans
   */
  
  public static XasCodec simpleBeanWriter() {
    return DEFAULT_BEAN_WRITER;
  }

  /** Get simple XAS content reader for beans. Uses
   * {@link SimpleXasSerializer} to read the bean.
   *
   * @param beanClass Class of bean to read. Must have a public no-args constructor
   * @return ContentReader for the bean class
   */
  
  public static XasCodec simpleBeanReader( final Class beanClass ) {
    return new SimpleXasSerializer(beanClass); 
  }

  /** Serialize bean as XML to an output stream.
   */

  public static void writeBean(OutputStream aos, Object o) throws
      IOException {
    XmlOutput xo = getXmlSerializer(aos);
    xo.append(StartDocument.instance());
    DEFAULT_BEAN_WRITER.writeObject(o, xo);
    DEFAULT_BEAN_WRITER.finishObject(o, xo);
    xo.append(EndDocument.instance());
    xo.flush();
  }

  /** Serialize bean as XML to a file.
   */

  public static void writeBean(File f, Object o) throws IOException {
    FileOutputStream fos = new FileOutputStream(f);
    try {
      writeBean(fos, o);
    } finally {
      fos.close();
    }
  }

  /** Deserialize bean from an XML file.
   */

  public static void readBean(File f, Object o) throws IOException {
    FileInputStream fin = new FileInputStream(f);
    try {
      readBean(fin,o);
    } finally {
      fin.close();
    }
  }

  /** Deserialize bean in XML format from an input stream.
   */

  public static void readBean(InputStream in, Object o) throws
      IOException {
    PeekableItemSource is = new PeekableItemSource( getDataSource(in) );
    Item i = is.peek();
    if( i != null && i.getType() == Item.START_DOCUMENT )
      is.next(); // Eat startdoc
    SimpleXasSerializer.readObject(o,is);
    SimpleXasSerializer.finishObject(o,is);
  }


  /** Deserialize object from an XML file.
   */

  public static Object readObject(File f, XasCodec xc) throws
      IOException {
    FileInputStream fin = new FileInputStream(f);
    try {
      return readObject(fin,xc);
    } finally {
      fin.close();
    }
  }

  /** Deserialize object in XML format from an input stream.
   */

  public static Object readObject(InputStream in, XasCodec decoder) throws
      IOException {
    ItemSource r = getDataSource(in);
    RefTree t = XasSerialization.readTree(r, decoder);
    if( t.getRoot() == null || t.getRoot().getContent() == null )
      throw new IOException("Could not read object");
    return t.getRoot().getContent();
  }

  /** Serialize object as XML to an output stream.
   */

  public static void writeObject(OutputStream aos, final Object o, 
        XasCodec encoder) throws
      IOException {
    RefTree t = new RefTree() {

      public RefTreeNode getRoot() {
        return new RefTreeNodeImpl(null,null,o);
      }
      
    };
    //XmlrDebug.dumpTree(t, System.out);
    XmlOutput w = getXmlSerializer(aos);
    //w.append(StartDocument.instance());
    XasSerialization.writeTree(t, w, encoder);
    //w.append(EndDocument.instance());
    w.flush();
  }

  /** Serialize object as XML to a file.
   */

  public static void writeObject(File f, Object o, XasCodec encoder) throws
      IOException {
    FileOutputStream fos = new FileOutputStream(f);
    try {
      writeObject(fos, o, encoder);
    } finally {
      fos.close();
    }
  }

  public static final XasCodec.EncoderOnly ANY_CONTENT_WRITER =
      new AnyContentWriter();

  private static class AnyContentWriter extends XasCodec.EncoderOnly {

    public void encode(ItemTarget t, RefTreeNode n, StartTag context) throws IOException {
      Object o = n.getContent();
      StartTag st = new StartTag(new Qname("","node"), context);
      if( o instanceof RefTrees.IdentifiableContent )
        st.addAttribute(new Qname("","id"),
            // BUGFIX-200610120-2: Tried to write nonstring attr 	
            o!= null ? ((RefTrees.IdentifiableContent) o).getId().toString() 
        	    : "<null>" );
      st.addAttribute(new Qname("","class"),
          o!= null ? o.getClass().getName() : "null");
      
    }

  }
}
// arch-tag: af49645b64392361d6c4b8e6bc33f238 *-
