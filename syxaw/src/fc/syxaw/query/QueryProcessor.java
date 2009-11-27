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

package fc.syxaw.query;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

import fc.syxaw.fs.SyxawFile;

//FIXME-P: consider usage of initing QP instance with f,linkFacet,query
// --> remove args that will be the same anyway...

public interface QueryProcessor {

  public InputStream getInputStream(SyxawFile f, String query,
                                    boolean linkFacet) throws
          FileNotFoundException;

  public OutputStream getOutputStream(SyxawFile f, String query,
                                      boolean linkFacet) throws
          FileNotFoundException;

  // Length of filtered data, Constants.NO_SIZE ok (=unknown size)
  /*public long getLength(SyxawFile f, String query,
                                      boolean linkFacet) throws
          FileNotFoundException;*/

}

// arch-tag: b6ac8e10-7f38-4c90-8a36-d6cb95febc64
