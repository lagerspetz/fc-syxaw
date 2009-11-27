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

// $Id: ObjectInputStream.java,v 1.12 2005/01/03 11:59:02 ctl Exp $

package fc.syxaw.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import fc.syxaw.fs.Constants;
import fc.syxaw.util.StringHolder;
import fc.syxaw.util.Util;
import fc.syxaw.util.XmlUtil;
import fc.util.log.Log;

import org.apache.axis.transport.http.ChunkedInputStream;

/** Input stream that supports reading XML-encoded objects. The
 * objects must be serialized using an encoding compatible with
 * {@link ObjectOutputStream}.
 * @see ObjectOutputStream for a description of the serialization format.
 */

public class ObjectInputStream extends FilterInputStream {

  protected long bytesLeft = Long.MAX_VALUE;
  protected long markBytesLeft = Long.MAX_VALUE;
  protected boolean inEof = false;
  protected long size = Constants.NO_SIZE;
  protected String name = null;

  static final int RADIX = 16; // HTTP/1.1 chunked encoding compatibility
  static final String OBJECT_HEADER = ""; // HTTP/1.1 chunked encoding compatibility


  /** Create stream.
   * @param in underlying input stream
   */
  public ObjectInputStream( InputStream in ) {
    super(in);
  }

  protected ObjectInputStream( InputStream in, long aSize, String aName ) {
    super(in);
    size = aSize;
    bytesLeft = aSize;
    markBytesLeft = aSize;
    name = aName;
  }


  /** Read object from stream.
   * @param obj Objects, whose content is initialized from the stream.
   * @return <code>obj</code>
   * @throws IOException if the object cannot be read.
   * @throws EOFException if at end of stream
   */

  // NOTE: eof detect throw was added later, and we chose not to use null return for eof
  // as not to break compatitbility with old code.

  public Object readObject( Object obj ) throws IOException {
    long objSize=getNameLenHeader(null);
    if( objSize == -1)
      throw new EOFException();
    // NOTE: Slow but "safe" implementation -- makes sure XML reading won't
    // read bytes past end of XML (due to buffering etc) by first storing the
    // XML in a byte buffer. A faster implementation would be to implement a read
    // limit for this class that can be activated when reading objects
    // Another option is to use an XML deserializer that is known never to
    // read past the close of the root tag.
    ByteArrayOutputStream oin = new ByteArrayOutputStream();
    Util.copyStream(this,oin,objSize);
    /* DEBUG CODE
    Log.log("===Objstr is --"+oin.toString("iso-8859-1")+"--",Log.DEBUG);
    Log.log("===Objhexdump is --"+Util.toString(oin.toByteArray())+"--",Log.DEBUG);
    try {
      fc.util.xas.TypedXmlParser rd= XmlUtil.getXmlParser(
          new ByteArrayInputStream(oin.toByteArray()));
      fc.util.xas.EventStream es =
      new fc.util.xas.EventStream(rd);
      Log.log("===Esdump is "+es,Log.WARNING);
    } catch( Exception x) {
    }
    */
    XmlUtil.readBean(new ByteArrayInputStream(oin.toByteArray()),obj);
    return obj;
  }

  /** Read substream.
   *
   * @throws IOException if an I/O error occurs
   * @return ObjectInputStream substream.
   */

  public ObjectInputStream readSubStream() throws IOException {
    /*StringHolder subName = new StringHolder();
    long subSize = getNameLenHeader(subName);
    if( subSize == Constants.NO_SIZE )
       Log.log("Substream has no size",Log.FATALERROR);
    return new ObjectInputStream(this,subSize,subName.getString());*/
    return new ObjectInputStream( new ChunkedInputStream( this ));
  }

  protected long getNameLenHeader(StringHolder name) throws IOException {
    long objSize = -1L;
    // NOTE: Ugly-looking code. What is the appropriate way to
    // read a single line from in terminated by crlf?
    // Don't want to use Buffer*Streams, as they may buffer past end-of-line :(
    ByteArrayOutputStream lin = new ByteArrayOutputStream();
    int c = -1;
    for (; (c = read()) != 0x0d && c != 0x0a && c != -1; )
      lin.write(c);
    if( c == -1 )
      return -1;
    if (c == 0x0d && read() != 0x0a)
      throw new IOException("Invalid linefeed");
    String objHeader = new String(lin.toByteArray());
    if( objHeader.length()==0 ) {
      if( in.read()==-1 )
        return -1;
      else
        throw new IOException("Invalid object header: empty header");
    }
    if (!objHeader.startsWith(OBJECT_HEADER)) {
      Log.log("Invalid object header, rest of stream follows", Log.INFO);
      Util.copyStream(in, System.err);
      throw new IOException("Invalid object header: " + objHeader);
    }
    try {
      objHeader = objHeader.substring(OBJECT_HEADER.length()); // Strip header
      //Log.log("The object header is ["+objHeader+"]",Log.INFO);
      int spacePos = objHeader.indexOf(' ');
      if (spacePos != -1) {
        // Get name
        if (name != null) // Ignore if no holder
          name.setString(objHeader.substring(0, spacePos));
        objHeader = objHeader.substring(spacePos + 1);
      }
      objSize = Long.parseLong(
          objHeader.trim(), RADIX);
    }
    catch (NumberFormatException x) {
      Log.log("Invalid size in object header, dumping rest of str; hdr=",Log.ERROR,objHeader);
      Util.copyStream(in, System.err);
      throw new IOException("Invalid size in object header");
    }
    return objSize;
  }

  /*
  // Stuff for supporting a fixed-length substream
  public String getName() {
    return name;
  }

  public long getSize() {
    return size;
  }*/


  public int read() throws IOException {
    if( bytesLeft > 0 ) {
      bytesLeft--;
      int count = in.read();
      inEof = count == -1;
      return count;
    } else
      return -1;
  }

  public int read(byte b[]) throws IOException {
      return read(b, 0, b.length);
  }

  public int read(byte b[], int off, int len) throws IOException {
    if( bytesLeft < 0L)
      return -1;
    len = bytesLeft > len ? len : (int) bytesLeft;
    bytesLeft -= len;
    int count = in.read(b, off, len);
    if( count == 0 && len > 0 && in instanceof ChunkedInputStream ) {
      // The current apache chunkedis sometimes misses the eof from the
      // underlying stream.
      //Log.log("Applying Apache ChunkedInputStream count=0 fix",Log.WARNING);
      //count = -1;

      // In the Syxaw case, however, this only(?) happens when the data length in a chunk
      // won't match the header (i.e. overshot or too early eof) --> signal
      // IO error
      inEof = true; // make close work
      throw new IOException("Bad size of data chunk, data corrupted");
    }
    inEof = count == -1;
    return count;
  }

  public long skip(long n) throws IOException {
    n = bytesLeft > n ? n : (int) bytesLeft;
    return in.skip(n);
  }
  public int available() throws IOException {
    int avail = in.available();
    return avail > bytesLeft ? (int) bytesLeft : avail;
  }

  public void close() throws IOException {
    if( inEof )
      in.close();
    else
      in.skip(bytesLeft);
  }

  public boolean markSupported() {
    return in.markSupported();
  }

  public synchronized void reset() throws IOException {
    bytesLeft = markBytesLeft;
    in.reset();
  }

  public synchronized void mark(int readlimit) {
    markBytesLeft = bytesLeft;
    in.mark(readlimit);
  }

}
// arch-tag: 7ca3151450b1ec2c656420ec78312379 *-
