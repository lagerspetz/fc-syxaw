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

package fc.syxaw.storage.hfsbase;

import fc.dessy.indexing.Keywords;
import fc.syxaw.fs.GUID;

public interface ForeignMetadata {

  public static final int DATA_EQ_LOCALVER =0x1;
  public static final int DATA_EQ_LINKVER =0x2;
  public static final int META_EQ_LOCALVER =0x4;
  public static final int META_EQ_LINKVER =0x8;

  public GUID getGuid();

  public String getName();

  public String getLink();

  public String getJoins();

  public String getType();

  public long getMetaModTime();

  public int getDataVersion();
  public int getMetaVersion();

  public byte[] getHash();

  public int getLinkDataVersion();
  public int getLinkMetaVersion();

  public Keywords getKeywords();
  
  public long getLinkUId();

  public int getModFlags();

  public String getBranch();
  
}

// arch-tag: 99e5e8ba-3e75-410e-86ef-8ae57f687957
