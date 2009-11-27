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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import fc.syxaw.api.Metadata;
import fc.syxaw.fs.BLOBStorage;
import fc.syxaw.fs.FullMetadata;
import fc.syxaw.fs.ObjectMerger;
import fc.syxaw.fs.ObjectProvider;
import fc.syxaw.fs.Repository;
import fc.syxaw.fs.SyxawFile;

public class DefaultObjectProvider implements ObjectProvider {

  public void setBaseProvider(ObjectProvider op) {
    throw new IllegalStateException(); // This should never be chained
  }

  public boolean canRead(SyxawFile f) {
    return ((AbstractSyxawFile) f).canRead(null);
  }

  public boolean canWrite(SyxawFile f) {
    return ((AbstractSyxawFile) f).canWrite(null);
  }

  public BLOBStorage createStorage(SyxawFile f, String puposeHint, boolean isTemporary) throws IOException {
    return ((AbstractSyxawFile) f).createStorage(null, puposeHint, isTemporary);
  }

  public boolean delete(SyxawFile f) {
    return ((AbstractSyxawFile) f).delete(null);
  }

  public FullMetadata getFullMetadata(SyxawFile f) throws FileNotFoundException {
    return ((AbstractSyxawFile) f).getFullMetadata(null);
  }

  public InputStream getInputStream(SyxawFile f, boolean linkFacet) throws FileNotFoundException {
    return ((AbstractSyxawFile) f).getInputStream(null,linkFacet); 
  }

  public ObjectMerger getLinkObjectMerger(SyxawFile f) throws FileNotFoundException {
    return ((AbstractSyxawFile) f).getLinkObjectMerger(null);
  }

  public OutputStream getOutputStream(SyxawFile f, boolean append, boolean linkFacet) throws FileNotFoundException {
    return ((AbstractSyxawFile) f).getOutputStream(null, append, linkFacet);
  }

  public Repository getRepository(SyxawFile f) throws FileNotFoundException {
    return ((AbstractSyxawFile) f).getRepository(null);
  }

  public boolean isDataModified(SyxawFile f, boolean toLink) throws FileNotFoundException {
    return ((AbstractSyxawFile) f).isDataModified(null, toLink);
  }

  public boolean isMetadataModified(SyxawFile f, boolean toLink) throws FileNotFoundException {
    return ((AbstractSyxawFile) f).isMetadataModified(null, toLink);
  }

  public void rebindStorage(SyxawFile f, BLOBStorage s, boolean toLink) throws IOException {
    ((AbstractSyxawFile) f).rebindStorage(null, s, toLink);
  }

  public void setMetadata(SyxawFile f, Metadata smd) throws FileNotFoundException {
    ((AbstractSyxawFile) f).setMetadata(null, smd);
  }
}

// arch-tag: 6dc64c42-9b7b-4cb7-b4ea-ab980d072c7a
