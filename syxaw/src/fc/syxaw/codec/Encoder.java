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

// $Id: Encoder.java,v 1.2 2003/09/25 09:53:55 ctl Exp $
package fc.syxaw.codec;

import java.io.IOException;

import fc.syxaw.fs.Constants;
import fc.syxaw.protocol.TransferHeader;
import fc.syxaw.transport.ObjectOutputStream;

/** Base class for transfer encoders.
 * @see Decoder
 */

public abstract class Encoder {

  /** Get id for the encoding produced by this encoder.
   * @return {@link fc.syxaw.protocol.TransferHeader#ENC_NONE}
   */

  public int getEncoding() {
    return TransferHeader.ENC_NONE;
  }

  /** Get size of encoded data.
   * @return {@link fc.syxaw.fs.Constants#NO_SIZE}
   */

  public long getEncodedSize() {
    return Constants.NO_SIZE;
  }

  /** Write encoded data to output stream.
   * @param os stream to write the encoded data to
   * @throws IOException if the encoded data cannot be written
   */

  public abstract void write( ObjectOutputStream os ) throws IOException;
}
// arch-tag: 6535f7d80c4a8f6db26ed7043e989bb2 *-
