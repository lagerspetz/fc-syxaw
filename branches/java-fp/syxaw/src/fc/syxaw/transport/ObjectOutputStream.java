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

// $Id: ObjectOutputStream.java,v 1.16 2005/01/03 11:59:03 ctl Exp $

package fc.syxaw.transport;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import fc.syxaw.fs.Constants;
import fc.syxaw.util.Util;
import fc.syxaw.util.XmlUtil;
import fc.util.log.Log;

import org.apache.axis.transport.http.ChunkedOutputStream;

/** Output stream that supports writing objects. The objects are
 * serialized using {@link fc.syxaw.util.XmlUtil#writeBean(java.io.File, Object) writeBean}, and
 * hence any object serializable by that method may be serialized by
 * this stream.
 * <p>The format of the data written to the underlying output stream
 * consists of a size header and the XML document obtained
 * by {@link fc.syxaw.util.XmlUtil#writeBean(java.io.File, Object) writeBean}.
 * The size header is the ASCII representation of the hexadecimal length of the
 * XML document terminated by CRLF (The same scheme that
 * HTTP/1.1 chunked encoding uses ).
 * <p>The stream also supports writing of sub-streams, whose length may or
 * may not be known in advance. The format for the sub-streams is HTTP/1.1
 * chunked encoding; a single chunk is used if the length is known in
 * advance.
 * <p>Below is example serialization of a
 * {@link fc.syxaw.codec.VersionReference} object:<pre>
 6f
 &lt;?xml version="1.0" encoding="ISO-8859-1"?&gt;
 &lt;VersionReference&gt;
 &lt;reference&gt;1008&lt;/reference&gt;
 &lt;/VersionReference&gt;
 </pre>
 * @see ObjectOutputStream for a description of the serialization format.
 */

public class ObjectOutputStream
    extends FilterOutputStream {

  private static final byte[] CRLF ="\r\n".getBytes();

  protected int CHUNKBUF_SIZE = Config.SENDBUF_SIZE;

  protected boolean gotLastSubstream=false;

  /** Create stream.
   *
   * @param os Underlying output stream
   */

  public ObjectOutputStream(OutputStream os) {
    super(os);
  }

  /** Write object to stream.
   *
   * @param obj Object to write
   * @throws IOException if an I/O error occurs
   */
  public void writeObject(Object obj) throws IOException {
    ByteArrayOutputStream oout = new ByteArrayOutputStream();
    XmlUtil.writeBean(oout,obj);
    //oout.write(CRLF); Xebu enc doesn't like additional whitespace, so skip it!
    writeNameLenHeader(null, oout.size());
    out.write(oout.toByteArray());
  }

  /** Write substream.
   *
   * @param name substream name. Currently unused, may be <code>null</code>
   * @param length length of stream. Use
   *  {@link fc.syxaw.fs.Constants#NO_SIZE Constants.NO_SIZE} for unknown
   * @param last <code>true</code> if this is the last substream (may enable
   * some encoding optimizations)
   * @throws IOException if an I/O error occurs
   * @return ObjectOutputStream substream to write to.
   */

  // FIXME-W: Check that len written to substream really is correct.
  public ObjectOutputStream writeSubStream(String name, long length,
                                           boolean last) throws
      IOException {

    class NoCloseStream extends ObjectOutputStream {

      NoCloseStream( OutputStream aout ) {
        super(aout);
      }

      public void close() throws IOException {
        out.flush();
      }
    };


    // FIXME-W: Should check that no more and no less than len bytes are written!
    class FixedChunkOutputStream extends ObjectOutputStream {

      long left;

      FixedChunkOutputStream( ObjectOutputStream aout, long len ) throws IOException {
        super(aout);
        left = len;
        // BUGFIX-050913-1: Do not write a nameLen header if len=0, as we'll
        // otherwise get 2 times '0' CR LF in the stream (the second emitted
        // by close)
        if( len > 0 )
          writeNameLenHeader(null, len);
        else if( len < 0 )
          throw new IllegalArgumentException("Invalid length "+len);
      }

      public void close() throws IOException {
        if( left != 0 ) {
          Log.log("Wrong number of bytes written, left=" + left, Log.ERROR);
          throw new IOException("Bad amount of data (balance "+left+" bytes)");
        }
        out.write('0');
        out.write(CRLF); // No more chunks
        out.flush();
      }

      public void write(byte[] b) throws IOException {
        out.write(b);
        left -= b.length;
      }

      public void write(byte[] b, int off, int len) throws IOException {
        out.write(b,off,len);
        left -= len;
      }

      public void write(int b) throws IOException {
        out.write(b);
        left --;
      }
    };

    name = null; // FIXME-W Name is not used
    if( last ) {
      if( gotLastSubstream )
        throw new IllegalStateException("Last substream already written.");
      gotLastSubstream = true;
    }
    if( length == Constants.NO_SIZE )
      return new ObjectOutputStream(
        new BufferedOutputStream(
         new ChunkedOutputStream(
         new NoCloseStream( this )),
         CHUNKBUF_SIZE ) );
    if( length < 0L && length != Constants.NO_SIZE )
      throw new IllegalArgumentException("Illegal length");
    return new FixedChunkOutputStream(this,length);
  }

  protected void writeNameLenHeader(String name, long length) throws
      IOException {
    out.write( (ObjectInputStream.OBJECT_HEADER + //" "+
                (Util.isEmpty(name) ? "" : name+" ") +
                Long.toString(length,
                              ObjectInputStream.RADIX)).getBytes());
    out.write(CRLF);
  }

}
// arch-tag: 53587502c76aab66dd90700477f3a107 *-
