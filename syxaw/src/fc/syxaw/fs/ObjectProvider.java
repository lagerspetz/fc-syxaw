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

/** Interface for a class of objects provided to Syxaw. Objects exposed to
 * Syxaw trough this interface may provide a customized on-disk layout,
 * metadata, data streams, query processing, and repository of past revisions.
 */

public interface ObjectProvider {

  /** Set underlying base provider. When a storage provider for an object class
   * is installed, Syxaw calls this method to inform the object provider of
   * the previous object provider used for the class. That object provider
   * can be used to e.g. handle requests that do not need modified behavior. 
   * 
   * @param op Chained provider 
   */
  public void setBaseProvider(ObjectProvider op);
  
  /** Determine if the object is readable.
   * 
   * @param f Object
   * @return <code>true</code> if readable.
   */
  public boolean canRead(SyxawFile f);

  /** Determine if the object is writable.
   * 
   * @param f Object
   * @return <code>true</code> if writable.
   */
  public boolean canWrite(SyxawFile f);

  /** Create stream storage. Syxaw calls this method when downloading and
   * uploading new data for the object to obtain a buffer for storing the
   * received stream. 
   * 
   * @param f Object
   * @param puposeHint String hinting the purpose of the storage, e.g. "download"
   * @param isTemporary <code>true</code> if the storage is expected to be
   * short-lived.
   * @return created storage object.
   * @throws IOException
   */
  public BLOBStorage createStorage(SyxawFile f, String puposeHint,
      boolean isTemporary) throws IOException;

  /** Delete object.
   * 
   * @param f Object
   * @return <code>true</code> on success.
   * 
   */
  public boolean delete(SyxawFile f);

  /** Read object full metadata. The returned metadata need not be
   * serializable.
   * 
   * @param f Object
   * @return Object metadata
   * @throws FileNotFoundException if object is not found
   */
  public FullMetadata getFullMetadata(SyxawFile f) throws FileNotFoundException;

  /** Get object data stream for reading.
   * 
   * @param f Object
   * @param linkFacet <code>true</code> if link facet data is read, otherwise
   *  the local facet is read.
   * @return input stream to data
   * @throws FileNotFoundException if object is not found
   */
  public InputStream getInputStream(SyxawFile f, boolean linkFacet) throws FileNotFoundException;

  /** Get merger for object
   * 
   * @param f Object
   * @return merger for the object
   * @throws FileNotFoundException if object is not found
   */
  public ObjectMerger getLinkObjectMerger(SyxawFile f)
      throws FileNotFoundException;

  /** Get object data stream for writing.
   * 
   * @param f Object
   * @param append <code>true</code> if written data should be appended to 
   * existing data
   * @param linkFacet <code>true</code> if link facet data is written, otherwise
   *  the local facet is written.
   * @return output stream to data
   * @throws FileNotFoundException if object is not found
   */

  public OutputStream getOutputStream(SyxawFile f, boolean append, boolean linkFacet)
      throws FileNotFoundException;

  /** Get repository of past revisions.
   * 
   * @param f Object
   * @return repository
   * @throws FileNotFoundException if object is not found
   */
  public Repository getRepository(SyxawFile f) throws FileNotFoundException;

  // ? Its a bit unclear what parameters this one should take
  //public boolean initializeNewFile(SyxawFile f) throws IOException;

  /** Check data for modifications.
   * 
   * @param f Object
   * @param toLink <code>true</code> if testing link facet, 
   *    otherwise local facet
   * @return <code>true</code> if the data is modified 
   * @throws FileNotFoundException if object is not found
   */
  public boolean isDataModified(SyxawFile f, boolean toLink)
      throws FileNotFoundException;

  /** Check metadata for modifications.
   * 
   * @param f Object
   * @param toLink <code>true</code> if testing link facet, 
   *    otherwise local facet
   * @return <code>true</code> if the metadata is modified 
   * @throws FileNotFoundException if object is not found
   */
  public boolean isMetadataModified(SyxawFile f, boolean toLink)
      throws FileNotFoundException;

  /** Replace contents with storage contents.
   * 
   * @param f Object
   * @param s Storage previously obtained with 
   * {@link #createStorage(SyxawFile, String, boolean) createStorage}. 
   * @param toLink <code>true</code> if replacing link facet, 
   *    otherwise local facet
   * @throws IOException if object is not found
   */
  public void rebindStorage(SyxawFile f, BLOBStorage s, boolean toLink) throws IOException;

  // ? public void rebindStorage(SyxawFile f) throws IOException;

  /** Set object metadata.
   * 
   * @param f Object
   * @param smd new metadata
   * @throws FileNotFoundException if object is not found
   */
  public void setMetadata(SyxawFile f, Metadata smd)
    throws FileNotFoundException;
  
  // If we extend this with list[] and isDirectory() we could make
  // virtual dirs inside the ObjectFilter...
}

// arch-tag: 1b49820b-f2d3-41e0-a5b9-1bb792392846
