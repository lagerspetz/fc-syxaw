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

package fc.syxaw.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/** A repository for objects. */

public interface Repository {
  
  /** Commit to the repository. NOTE: <code>version</code> is not used in
   * this interface, and may be ignored. It is, however,
   * used in {@link OldRepository}.
   * 
   * @param f file to commit
   * @param data data to commit, null = no change
   * @param linkFacet facet to commit
   * @param version version to assign (for link facet only)  
   * 
   * @param md meta to commit, null = no change
   * @return Metadata of committed object
   * @throws IOException
   */
  public FullMetadata commit(SyxawFile f, InputStream data, FullMetadata md,
      boolean linkFacet, int version, boolean gotData, boolean gotMetadata) throws IOException;

  public VersionHistory getVersionHistory(SyxawFile f);

  /** Get next version number that will be assigned, assuming data has been
   * modified.  
   * 
   * @param f
   * @return next version
   * @throws FileNotFoundException
   */
  
  public int getNextVersion(SyxawFile f) throws FileNotFoundException;

}

// arch-tag: d34df67a-9b8d-4d0d-8600-f40a64029a5d
