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

// $Id: Syxaw.java,v 1.5 2004/11/19 09:31:08 ctl Exp $
package fc.syxaw.api;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import fc.syxaw.fs.ObjectMerger;
import fc.syxaw.tool.Toolkit;

/** API to Syxaw file system level operations. This class provides access to
 * operations that globally affect the Syxaw file system. The user code must
 * be run in the same VM as the file system. */

public class Syxaw {

  private Toolkit toolkit=fc.syxaw.fs.Syxaw.getToolkit();

  private static final Syxaw instance = new Syxaw();

  private Syxaw() {
  }

  /** Get instance of API.
   * @return Syxaw API instance
   */

  public static Syxaw getInstance() {
    return instance;
  }

  /** Set merger for a MIME type. Installs a merging algorithm for the given
   *  MIME type. The <code>merger</code> parameter is the fully qualified class
   * name of a file merger. The merger must implement {@link Syxaw.Merger}, as
   * well as have a public no-args constructor (to allow instantiation).
   * @param mimeType MIME Type to install merger for
   * @param merger Fully qualified class name of the merger.
   */

  public void setMerger(String mimeType, String merger) {
    toolkit.setMerger(mimeType,merger);
  }

  /** Get merger for MIME type. The method is useful for determining if a merger
   * is already installed for a particular MIME type. It does <b>not</b>
   * necessarily return the name of a merger implementing the {@link Syxaw.Merger}
   * interface, as Syxaw internally supports other mergers.
   * @param mimeType MIME type to get merger for
   * @return String Fully qualified class name of merger, or <code>null</code>
   * if none.
   */
  public String getMerger(String mimeType) {
    return toolkit.getMerger(mimeType,true);
  }

  /** Base class for file mergers. Extend this class to write customized
   * mergers. Merging of file contents happens in the
   * {@link #mergeData mergeData} method. */

  public static abstract class Merger implements ObjectMerger.MergerFactory {

    /** Merge file data.
     *
     * @param currentChanged flag indicating if local file has changed since last sync
     * @param downloadChanged flag indicating if remote file has changed since last sync
     * @param base stream to base version, or <code>null</code> if
     *  the base stream is unavailable
     * @param currentData stream to current local version, or <code>null</code> if
     *  the current stream is unavailable
     * @param downloadData stream to current remote version, or <code>null</code> if
     *  the remote stream is unavailable
     * @throws MergeConflict if a merging conflict occurs
     * @throws IOException if an I/O error occurs during merge
     * @return InputStream to the merged data
     */
    public abstract InputStream mergeData(boolean currentChanged,
                                          boolean downloadChanged,
                                          InputStream base,
                                          InputStream currentData,
                                          InputStream downloadData) throws
        MergeConflict,
        IOException;

    /** Instantiate merger. Used by Syxaw to instantiate the merger with
     * an appropriate type. May not be overridden.
     */

    public final ObjectMerger newMerger(fc.syxaw.fs.SyxawFile f,
                                        boolean linkFacet) throws
        FileNotFoundException {
      return new fc.syxaw.api.impl.ExternalMerger(f,
          f.getLinkDataVersion(),this);
    }

  }

  /** Merging conflict. */
  public static class MergeConflict extends IOException {

    /** Create a new conflict.
     * @param reason reason for the conflict. The reason will be appended to the
     * conflict log for the file.
     */
    public MergeConflict( String reason ) {
      super(reason );
    }
  }
}
// arch-tag: 60eb4cecb373983e99bc8a43e70fba82 *-
