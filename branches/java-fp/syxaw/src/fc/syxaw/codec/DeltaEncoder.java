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

// $Id: DeltaEncoder.java,v 1.2 2004/11/26 16:28:55 ctl Exp $
package fc.syxaw.codec;

import java.io.IOException;
import java.io.InputStream;

import fc.raxs.DeltaStream;
import fc.syxaw.transport.ObjectOutputStream;
import fc.syxaw.util.Util;
import fc.util.log.Log;

/** Encode object as delta from another version of the object.
 * The encoder outputs a {@link VersionReference} object initialized with the
 * base version number, followed by an object-specific
 * delta document as a sequence of bytes. It is assumed that the object
 * knows its own delta format, and one is able to obtain
 * {@link DeltaStream} input and output streams
 * accepting this format.
 *
 * <p>Below is an illustratory example of the text representation of a
 * delta-encoded XML document. The actual format varies according to
 * the object-specific delta format.
<pre>
&lt;?xml version="1.0" encoding="ISO-8859-1"?&gt;
&lt;VersionReference reference="1008" /&gt;
&lt;?xml version="1.0" encoding="UTF-8"?&gt;
&lt;diff&gt;
&nbsp;&lt;diff:copy src="1104" dst="506" run="3"/&gt;
&nbsp;&lt;diff:insert dst="506"&gt;
&nbsp;&nbsp;&lt;file name="f-ins-8dc325d1" id="20004" object="/id/YWxjaCJQbIQiUPGr" version="-1"/&gt;
&nbsp;&lt;/diff:insert&gt;
&lt;/diff&gt;
</pre>
 */


public class DeltaEncoder extends Encoder {
  int baseVersion;
  InputStream in;

  /** Create new encoder.
   *
   * @param in input stream to the delta-encoded data. Must be an instance of
   * {@link DeltaStream}. The encoder sets the base version of the stream to
   * <code>baseVersion</code>.
   * @param baseVersion base version for delta.
   * @throws CodecException if encoding fails.
   */

  public DeltaEncoder(InputStream in, int baseVersion) throws IOException {
    if( !(in instanceof DeltaStream))
      throw new IllegalArgumentException("Requires DeltaStream");
    this.baseVersion = baseVersion;
    this.in = in;
    ((DeltaStream) in).setBaseVersion(baseVersion);
  }

  /** Write encoded data. Writes the delta from the base version given in
   * the initializer to the current version of the object to <code>os</code>.
   *
   * @param os output stream for the encoded data
   * @throws IOException if an I/O error occurs
   */
  public void write(ObjectOutputStream os) throws IOException {
    os.writeObject(new VersionReference(baseVersion));
    // Write delta
    ObjectOutputStream sos = os.writeSubStream(null,
        ( (DeltaStream) in).getDeltaSize(), false);
    try {
      int count = Util.copyStream(in, sos);
      Log.log("Delta data transmit, sent bytes=" + count, Log.INFO);
    } finally {
      sos.close();
    }
  }
}
// arch-tag: b39a50531cf80b98efceb802edf71027 *-
