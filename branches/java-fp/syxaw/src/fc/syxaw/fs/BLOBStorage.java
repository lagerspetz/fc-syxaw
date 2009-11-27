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

// $Id: BLOBStorage.java,v 1.4 2003/09/17 16:55:29 ctl Exp $
package fc.syxaw.fs;

import java.io.OutputStream;
import java.io.InputStream;

/** Class for temporary storage of binary large objects (BLOBS). The data
 * stored in the storage is discarded at latest when the object is finalized.
 */

public abstract class BLOBStorage {

  /** Obtain output stream for storing data in the BLOB storage.
   * @param append set to <code>true</code> if data is appended
   * to data already in the storage
   */

  public abstract OutputStream getOutputStream( boolean append );

  /** Get input stream to read data in the BLOB storage.
   */

  public abstract InputStream getInputStream();

  /** Remove all data from the BLOB storage. */

  public abstract void delete();
}// arch-tag: 86fa204784aac366a8cbb6c84fcd2c7c *-
