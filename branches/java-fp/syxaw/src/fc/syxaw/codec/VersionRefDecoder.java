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

//$Id: VersionRefDecoder.java,v 1.3 2003/09/25 09:53:55 ctl Exp $
package fc.syxaw.codec;

import java.io.IOException;

import fc.syxaw.fs.Constants;
import fc.syxaw.transport.ObjectInputStream;
import fc.util.log.Log;

/** Decode version reference. The version reference encoding is described
 * in {@link VersionRefEncoder}.
 */
public abstract class VersionRefDecoder {

  protected int versionRef = Constants.NO_VERSION;

  /** Create decoder. */
  public VersionRefDecoder() {
  }

  /** Get decoded version reference. */
  public int getVersionReference() {
    return versionRef;
  }

  /** Decode encoded version reference. Decoding is performed by reading a
   * {@link VersionReference} object using <code>is.readObject()</code>
   */

  public void decode(ObjectInputStream is) throws IOException {
    VersionReference refObj = new VersionReference();
    is.readObject(refObj);
    versionRef = refObj.getReference();
    Log.log("Decoderef= "+refObj.getReference(),Log.INFO );
  }
}
// arch-tag: eda340250a947e4c389a3a089a00eebb *-
