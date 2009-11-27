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
import java.io.OutputStream;

import fc.syxaw.api.Metadata;

/* Object provider mapping operations back onto the file. */

public class ChainedObjectProvider implements ObjectProvider {

  protected ObjectProvider chained=null;

  public ChainedObjectProvider() {
  }

  public boolean canRead(SyxawFile f) {
    //A! assert chained != null;
    return chained.canRead(f);
  }

  public boolean canWrite(SyxawFile f) {
    //A! assert chained != null;
    return chained.canWrite(f);
  }

  public BLOBStorage createStorage(SyxawFile f, String puposeHint,
      boolean isTemporary) throws IOException {
    //A! assert chained != null;
    return chained.createStorage(f, puposeHint, isTemporary);
  }

  public boolean delete(SyxawFile f) {
    //A! assert chained != null;
    return chained.delete(f);
  }

  public FullMetadata getFullMetadata(SyxawFile f) throws FileNotFoundException {
    //A! assert chained != null;
    return chained.getFullMetadata(f);
  }

  public InputStream getInputStream(SyxawFile f, boolean linkFacet)
      throws FileNotFoundException {
    //A! assert chained != null;
    return chained.getInputStream(f, linkFacet);
  }

  public ObjectMerger getLinkObjectMerger(SyxawFile f)
      throws FileNotFoundException {
    //A! assert chained != null;
    return chained.getLinkObjectMerger(f);
  }

  public OutputStream getOutputStream(SyxawFile f, boolean append,
      boolean linkFacet) throws FileNotFoundException {
    //A! assert chained != null;
    return chained.getOutputStream(f, append, linkFacet);
  }

  public Repository getRepository(SyxawFile f) throws FileNotFoundException {
    //A! assert chained != null;
    return chained.getRepository(f);
  }

  public boolean isDataModified(SyxawFile f, boolean toLink)
      throws FileNotFoundException {
    //A! assert chained != null;
    return chained.isDataModified(f, toLink);
  }

  public boolean isMetadataModified(SyxawFile f, boolean toLink)
      throws FileNotFoundException {
    //A! assert chained != null;
    return chained.isMetadataModified(f, toLink);
  }

  public void rebindStorage(SyxawFile f, BLOBStorage s, boolean toLink)
      throws IOException {
    //A! assert chained != null;
    chained.rebindStorage(f, s, toLink);
  }

  public void setBaseProvider(ObjectProvider op) {
    chained = op;
  }

  public void setMetadata(SyxawFile f, Metadata smd)
      throws FileNotFoundException {
    //A! assert chained != null;
    chained.setMetadata(f, smd);
  }
  
}
// arch-tag: b7f6e26a-1647-4b08-8ae9-e9fa5d32bccc 
