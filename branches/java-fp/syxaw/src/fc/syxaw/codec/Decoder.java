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

// $Id: Decoder.java,v 1.2 2003/09/25 09:53:55 ctl Exp $
package fc.syxaw.codec;

import java.io.IOException;

import fc.syxaw.transport.ObjectInputStream;

/** Base class for transfer decoders.
 * @see Encoder
 */

public abstract class Decoder {

  /** Decode encoded input stream.
   * @param is input stream to decode
   * @throws IOException if decoding fails or the input stream cannot be read
   */
  public abstract void decode(ObjectInputStream is) throws IOException;
}
// arch-tag: 7d438cdad934e17d21e7352e42ca4c85 *-
