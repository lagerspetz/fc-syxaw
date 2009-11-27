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

// $Id: SyxawFile.java,v 1.39 2005/06/08 12:46:12 ctl Exp $
package fc.syxaw.fs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

//import fc.dessy.jnfsd.NFSDSyxawFile;
import fc.syxaw.api.ISyxawFile;
import fc.syxaw.api.Metadata;


/** File on the Syxaw file system. Corresponds to <code>java.io.File</code>,
 * with some extensions for Syxaw. The main extensions to <code>java.io.File</code>
 * are:<ul>
 * <li>local and link views of file</li>
 * <li>extended metadata</li>
 * <li>repository of previous versions</li>
 * <li>locking</li>
 * <li>temporary storage for new data</li>
 * </ul>
 * Each file can be though to have two separate facets: the local and the
 * link. The content of the local and link facets are interrelated, but not
 * necessarily equal. The local and link facets have separate version histories.
 * The local facet is the file seen by local users, and users linking to the
 * file. The link facet is the file uploaded to and downloaded from another
 * repository. Schematically:<pre>
 * Device A, file1               Device B, file2                Device C, file3
 * +-------+-------+  linked_to  +-------+-------+  linked_to  +-------+-------+
 * | LOCAL&lt;-&gt;LINK  |------------&gt;| LOCAL&lt;-&gt;LINK  |------------&gt;| LOCAL&lt;-&gt;LINK  |
 * +-------+-------+             +-------+-------+             +-------+-------+
 * </pre>
 * In the diagram <code>&lt;-&gt;</code> symbolizes a relationship between local and link facets
 * maintained locally by the host.
 * <p>Directory trees are a good example of the use of the local and link facets
 * <p>For the methods that are also present in <code>java.io.File</code>, the
 * semantics correspond to that of the method in <code>java.io.File</code>.
 */

public abstract class SyxawFile implements ISyxawFile/*, NFSDSyxawFile*/ {

  public static boolean FIXME_COMMIT_RECEIVED_NOT_CURRENT = false;

  protected SyxawFile() {}

  /** Create new file object named by a GUID. */
  public abstract SyxawFile newInstance( UID g );
  /** Get file metdata.
   * @throws FileNotFoundException if file not found
   */
  public abstract Metadata getMetadata() throws FileNotFoundException;

  /** Set file metdata.
   * @throws FileNotFoundException if file not found
   */

  public abstract void setMetadata(Metadata smd) throws FileNotFoundException;

  /** Get file full metdata.
  * @throws FileNotFoundException if file not found
  */
  public abstract FullMetadata getFullMetadata() throws FileNotFoundException;

  /** Get file GUID.
   * @return GUID, or <code>null</code> if file not found
   */
  public abstract GUID getGuid();

  /** Get file UID.
   *
   * @return UID, or <code>null</code> if file not found
   */
  public abstract UID getUid();

  /** See documentation for method in <code>java.io.File</code> */
  public abstract boolean canRead();

  /** See documentation for method in <code>java.io.File</code> */
  public abstract boolean canWrite();

  /** See documentation for method in <code>java.io.File</code> */
  public abstract boolean exists();

  /** See documentation for method in <code>java.io.File</code> */
  public abstract boolean createNewFile() throws IOException;

  /** See documentation for method in <code>java.io.File</code> */
  public abstract boolean delete();

  /** Deterine if content of the file has been modified. The local and
   * link facet
   * modification flags are set whenever the data in the file is changed.
   * The method may return false positives.
   * @param toLink if <code>true</code>, check link facet modification flag, otherwise
   *         check local facet modification flag.
   * @return true if link/local modification flag is set
   */

  public abstract boolean isDataModified(boolean toLink) throws FileNotFoundException;

  /** Deterine if metadata of the file has been modified. The local and
   * link facet
   * modification flags are set whenever the metadata in the file is changed.
   * The method may return false positives.
   * @param toLink if <code>true</code>, check link facet modification flag, otherwise
   *         check local facet modification flag.
   * @return true if link/local modification flag is set
   */

  public abstract boolean isMetadataModified(boolean toLink) throws FileNotFoundException;

  /** Get linked-to object of file. The link facet of the file is synchronized with
   * the linked-to object. */

  public abstract String getLink();

  // nulls for agrs = don't change, except link (use call w/o link to leave
  // link unchanged

  /** Set file link fields.
  * Does not change the linked-to object.
  *
  * @param version new version of linked-to object, or <code>null</code> if no change
  * @param metaModified new value of link facet metdata modification flag,
  * or <code>null</code> if no change
  * @param dataModified new value of link facet data modification flag,
  * or <code>null</code> if no change
  * @return <code>true</code> if successful
  */
  public abstract boolean setLink( Integer version,
      Boolean metaModified, Boolean dataModified);
  
  public abstract boolean setLink( Integer version,
                                  Boolean metaModified, Boolean dataModified, boolean datasync, boolean metasync );

 /** Set file local fields.
 * @param version new version, or <code>null</code> if no change
 * @param metaModified new value of local facet metdata modification flag,
 * or <code>null</code> if no change
 * @param dataModified new value of local facet data modification flag,
 * or <code>null</code> if no change
 * @return <code>true</code> if successful
 */

  public abstract boolean setLocal(Integer version, Boolean metaModified,
                           Boolean dataModified);

  /** Set file link fields.
  *
  * @param link linked-to object of file
  * @param version new version of linked-to object, or <code>null</code> if no change
  * @param metaModified new vale of link facet metdata modification flag,
  * or <code>null</code> if no change
  * @param dataModified new vale of link facet data modification flag,
  * or <code>null</code> if no change
  * @return <code>true</code> if successful
  */
  public abstract boolean setLink( String link, Integer version,
      Boolean metaModified, Boolean dataModified);
  
  public abstract boolean setLink( String link, Integer version,
                                  Boolean metaModified, Boolean dataModified, boolean datasync, boolean metasync);


  /** Get linked-to object data version. */
  public abstract int getLinkDataVersion() throws FileNotFoundException;
  
  /** Get linked-to object metadata version. */
  public abstract int getLinkMetaVersion() throws FileNotFoundException;

  public abstract String toString();

  /** Obtain an input stream to the local facet. */
  public abstract InputStream getInputStream() throws FileNotFoundException;

  /** Obtain an input stream to the link facet. */
  public abstract InputStream getLinkInputStream() throws FileNotFoundException;

  /** Obtain an output stream to the local facet.
   * @param append set to <code>true</code> to append to existing data
   */

  public abstract OutputStream getOutputStream(boolean append)
      throws FileNotFoundException ;

  /** Obtain an output stream to the link facet.
   * @param append set to <code>true</code> to append to existing data
   */

  public abstract OutputStream getLinkOutputStream(boolean append)
      throws FileNotFoundException ;

  /** Get version that will be assigned to the local fact by the next commit.
   * Returns the current version if there are no modifications to the
   * local facet of the file.
   */

  public int getCommitVersion() throws FileNotFoundException {
    if(isMetadataModified(false) | isDataModified(false) )
      return getNextVersion();
    return getFullMetadata().getDataVersion();
  }

  /** Get version that will be assigned to the local facet.
   */

  public abstract int getNextVersion() throws FileNotFoundException;
  
  
  /** Commit local facet to version repository. Commit does nothing if there
   * are no modifications to the data or metadata of the local facet.
   * @param gotData whether the version is to be applied to dataVersion.
   * @param gotMetadata whether the version is to be applied to metaVersion.
   * @return metadata of commited version
   * @throws IOException I/O error during commit
   */
  public abstract FullMetadata commit( boolean gotData, boolean gotMetadata) throws IOException;

  /** Commit link facet to version repository.
   * @param version version number to assign the committed version. Must be
   * larger than any existing version number in the link facet version history
   * for this object.
   * @param gotData whether the version is to be applied to dataVersion.
   * @param gotMetadata whether the version is to be applied to metaVersion.
   * @return metadata of commited version
   * @throws IOException I/O error during commit
   */
  public abstract Metadata commitLink( int version, boolean gotData, boolean gotMetadata ) throws IOException; // Commit file data as link data

  /** Get version history for local facet. */
  public abstract VersionHistory getVersionHistory();

  /** Get version history for link facet. 
   * @param meta Whether to get the metadata version instead of the data version. 
   * */
  public abstract VersionHistory getLinkVersionHistory(boolean meta);

  /** Create temporary data storage. */
  public abstract BLOBStorage createStorage( String puposeHint,
                                             boolean isTemporary) throws IOException;

  /** Atomically apply the contents of a BLOB storage to the local facet. The
   * BLOB storage is empty after the opration. */

  public abstract void rebindStorage(BLOBStorage s ) throws IOException;

  /** Atomically apply the contents of a BLOB storage to the link facet. The
   * BLOB storage is empty after the opration. */

  public abstract void rebindLinkStorage(BLOBStorage s ) throws IOException;

  /** Lock this file. A locked file cannot be read nor written.
   * @return a file lock
   * @see SyxawFile.Lock
   * */
  public abstract Lock lock();

  /** A file lock. */
  public static class Lock {
    /** Release the file lock. */
    public void release() {}
  }

  /** Get object merger for link facet.
   *
   * @throws FileNotFoundException if the file is not found
   * @return ObjectMerger for the link facet of the file
   */
  public abstract ObjectMerger getLinkObjectMerger() throws FileNotFoundException;

  public abstract String getBranch();

  public abstract void setBranch(String branch);
    
  public abstract ObjectProvider getObjectProvider() throws FileNotFoundException;
  
}
// arch-tag: d5d24ab9c517552c26746c1956f5112b *-
