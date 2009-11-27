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

// $Id: ObjectMerger.java,v 1.18 2004/11/27 19:32:19 ctl Exp $
// History
// Conatined F IXMERepository in r1.16
package fc.syxaw.fs;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import fc.syxaw.api.Metadata;
import fc.syxaw.api.SynchronizationException;
import fc.syxaw.merge.DefaultObjectMerger;

/** Generic interface for algorithms that reconcile differing versions of a
 * data object.
 * For examples, see {@link DefaultObjectMerger} and
 * {@link fc.syxaw.hierfs.DirectoryMerger}.
 */

public interface ObjectMerger {

  /** Reconcile the contents of a file.
   * This method is responsible for either generating a reconciled version
   * of the local and downloaded data or reporting a conflict.
   *
   * @param currentChanged <code>true</code> if the contents of the object
   *                       has been modified since the last reconciliation.
   *                       Typically, if this parameter is <code>false</code>,
   *                       the reconciled data equals the downloaded data.
   * @param downloadChanged <code>true</code> if new data was downloaded.
   *                       Typically, if this parameter is <code>false</code>,
   *                       the reconciled data equals the current object data.
   * @param downloadVersion version of downloaded data.
   *                       Only used if <code>downloadChanged</code> is set.
   * @param downloadData downloaded data, which may need to be reconciled with
   *                       the current object.
   * @param dlVerRef version data that the downloaded data is identical to.
   *                       Set to {@link Constants#NO_VERSION} if the downloaded
   *                       data is not (known to be) equal to any version in
   *                       <code>history</code>. If set to any other value than
   *                       {@link Constants#NO_VERSION}, the version must exist
   *                       in <code>history</code>. In this case no data is read from
   *                       <code>downloadData</code>
   * @return version number for reconciled data. Return {@link Constants#NO_VERSION}
   *         if the reconciled data is not identical to any version in
   *         <code>history</code>. The reconciled data should
   *         have been applied to the object on return.
   * @throws IOException an I/O error occurred while reconciling
   * @throws Conflict current and downloaded data could not be reconciled
   * If this exception is thrown the data of the object
   * <b>must not</b> have been modified.
   */

  public int mergeData(boolean currentChanged, boolean downloadChanged,
                       int downloadVersion, BLOBStorage downloadData,
                       int dlVerRef) throws IOException, Conflict;

  /** Reconcile the metadata for a file.
   * This method is responsible for either generating a reconciled version
   * of the local and downloaded metadata or reporting a conflict.
   *
   * @param currentChanged <code>true</code> if the contents of the object
   *                       has been modified since the last reconciliation.
   *                       Typically, if this parameter is <code>false</code>,
   *                       the reconciled metadata equals the downloaded metadata.
   * @param downloadChanged <code>true</code> if new metadata was downloaded.
   *                       Typically, if this parameter is <code>false</code>,
   *                       the reconciled metadata equals the current object metadata.
   * @param downloadVersion version of downloaded metadata.
   *                       Only used if <code>downloadChanged</code> is set.
   * @param downloadMeta downloaded metadata, which may need to be reconciled with
   *                       the current object.
   * @param dlVerRef version of the metadata that the
   *                       downloaded metadata is identical to.
   *                       Set to {@link Constants#NO_VERSION} if the downloaded
   *                       metadata is not (known to be) equal to any version in
   *                       <code>history</code>. If set to any other value than
   *                       {@link Constants#NO_VERSION}, the version must exist
   *                       in <code>history</code>. In this case no metadata is read from
   *                       <code>downloadMeta</code>
   * @return version number for reconciled metadata. Return {@link Constants#NO_VERSION}
   *         if the reconciled metadata is not identical to any version in
   *         <code>history</code>. The reconciled metadata should
   *         have been applied to the object on return.
   * @throws IOException an I/O error occurred while reconciling
   * @throws Conflict current and downloaded metadata could not be reconciled
   * If this exception is thrown the metadata of the object
   * <b>must not</b> have been modified.
   */

  public int mergeMetadata(boolean currentChanged, boolean downloadChanged,
                           int downloadVersion, Metadata downloadMeta,
                           int dlVerRef) throws IOException, Conflict;

  /** Get input stream to reconciled data. This method provides access to
   * an in-memory cached copy of the reconciled data, if such a copy
   * is available. Return <code>getLinkInputStream()</code> if no such copy is
   * available.
   */

  public InputStream getMergedData() throws IOException;
  /** Get reconciled metadata. This method provides access to
   * an in-memory cached copy of the reconciled metadata, if such a copy
   * is available. Return <code>getFullMetadata()</code> if no such copy is
   * available.
   */

  public FullMetadata getMergedMetadata() throws IOException;

  /** Obtain objects dependent on this object that may need synchronization.
   * Used when merging objects containing file system metadata, such a
   * directory trees. For an example, see
   * {@link fc.syxaw.hierfs.DirectoryMerger}.
   * @return a superset of objects discovered to need synchronization. The set
   * should contain {@link GUID GUID} or {@link UID UID} objects. In the latter
   * case, the LID of the object is determined by {@link #getDependentLID}.
   * <code>null</code> means no such objects were discovered.
   */

  public Set getObjectsNeedingSync();

  /** Get default location for dependent object. */

  public String getDependentLID();


  /** Exception signaling a conflict while reconciling data. A reason for the
   * conflict may be associated with the exception. The format of the reason
   * is specific to the reconciliation algorithm.
   */
  public class Conflict extends SynchronizationException {

    InputStream logStream = null;

    /** Construct a new Conflict.
     */

    public Conflict() {
      super();
    }

    /** Construct a new Conflict.
     * @param log conflict log.
     */
    public Conflict(String log) {
      logStream = new ByteArrayInputStream(log.getBytes());
    }

    /** Construct a new Conflict.
     *
     * @param message short reason for conflict
     * @param log conflict log.
     */
    public Conflict(String message, String log) {
      super(message);
      logStream = new ByteArrayInputStream(log.getBytes());
    }


    /** Construct a new Conflict.
     * @param log Reason for conflict.
     */
    /*
    public Conflict(InputStream log) {
      super();
      logStream = log;
    }*/

    /** Construct a new Conflict.
     *
     * @param message short reason for conflict
     * @param log conflict log.
     */
    public Conflict(String message, InputStream log) {
      super(message);
      logStream = log;
    }

    /** Get reason for conflict as an input stream. */
    public InputStream getLog() {
      return logStream;
    }
  }

  /** Factory for object mergers. */
  public interface MergerFactory {
    /** Method to instantiate merger.
     *
     * @param f SyxawFile which the merger should merge
     * @param linkFacet <code>true</code> if the merger is for the link facet
     * @return merger for <code>f</code>
     * @throws FileNotFoundException if the file is missing
     */
    public ObjectMerger newMerger(SyxawFile f, boolean linkFacet) throws FileNotFoundException;
  }

}

// arch-tag: 6aa875c4e27cdb8ee552d2e3eeaad7b5 *-
