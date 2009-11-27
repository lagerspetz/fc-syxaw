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

// $Id: VersionRefEncoder.java,v 1.5 2004/11/26 16:28:58 ctl Exp $
package fc.syxaw.codec;

import java.io.IOException;

import fc.syxaw.transport.ObjectOutputStream;
import fc.util.log.Log;

/** Encode version reference.
 * Encoding is performed by initializing a {@link VersionReference}
 * object with the reference, and writing this object to the encoded stream
 * using the stream's <code>writeObject</code> method.
 * <p>Assuming the reference is 42 and an output stream that serializes objects
 * as XML, the encoded stream could look something like this:<pre>
 * &lt;VersionReference reference="42"/&gt;
 * </pre>
 */


public class VersionRefEncoder extends Encoder {

  int verRef;

  public VersionRefEncoder(int averref) {
    verRef = averref;
  }

  public void write(ObjectOutputStream os ) throws IOException {
    os.writeObject(new VersionReference(verRef));
    Log.log("Verref="+verRef,Log.INFO);
  }

}
// arch-tag: b7625ae20532824aa4956c7618a9d92b *-
