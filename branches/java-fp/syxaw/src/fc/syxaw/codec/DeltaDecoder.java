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

// $Id: DeltaDecoder.java,v 1.2 2004/11/26 16:28:55 ctl Exp $

package fc.syxaw.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import fc.raxs.DeltaStream;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.transport.ObjectInputStream;
import fc.syxaw.util.Util;
import fc.util.log.Log;

/** Generic delta decoder. See {@link DeltaEncoder}
 * for a description of the generic delta format.
 */

public class DeltaDecoder extends Decoder {

  private OutputStream decodeStream;

  /** Create and initialize new decoder.
   *
   * @param decoded output stream that accepts deltas. The
   * <code>baseVersion</code> of the stream will be set by
   * {@link #decode decode}
   * @param h version history for the obejct. Currently not used
   * @throws CodecException if decoding fails
   */
  public DeltaDecoder(OutputStream decoded, VersionHistory h) throws
      CodecException {
    if (! (decoded instanceof DeltaStream))
      throw new IllegalArgumentException("Can only decode to DeltaStreams");
    decodeStream = decoded;
  }

  /** Decode delta. See {@link DeltaEncoder}
   * for a description of the generic delta format.
   *
   * @param is input stream with delta
   * @throws IOException if an I/O error occurs
   */

  public void decode(ObjectInputStream is) throws IOException {
    VersionReference ref = new VersionReference();
    is.readObject(ref);
    Log.log("Base version is "+ref.getReference(),Log.INFO);
    ((DeltaStream) decodeStream).setBaseVersion(ref.getReference());
    InputStream deltais = is.readSubStream();
    try {
      Util.copyStream(deltais, decodeStream);
    } finally {
      deltais.close();
    }
  }
}
// arch-tag: dddb4549fab4659259887a42e59bea9f *-
