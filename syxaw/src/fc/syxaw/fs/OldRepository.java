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

/** Repository that manually maintains a link version history. Newer 
 * applications should use {@link fc.syxaw.fs.Repository}, for which Syxaw
 * automatically handles the link history. 
 * <p>This class should be removed as soon as resources permit.
 */
public interface OldRepository extends Repository {
  
  public VersionHistory getLinkVersionHistory(SyxawFile f);

}

// arch-tag: 09eff7c3-4252-46b0-818b-54d7530f78a7