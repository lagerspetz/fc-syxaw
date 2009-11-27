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

// $Id: TransferHeader.java,v 1.13 2005/01/20 10:59:05 ctl Exp $
package fc.syxaw.protocol;

import fc.syxaw.fs.Constants;
import fc.syxaw.transport.PropertySerializable;

/** The <code>TransferHeader</code> structure.
 * See the Syxaw protocol data structure reference in the
 * <i>"Specification of Middleware Service Set II"</i> document.
 */

public class TransferHeader implements java.io.Serializable,
    PropertySerializable {

  /** Constants signifying no/unknown encoding of data.
  * The constant value is {@value} */
  public static final int ENC_NONE = -1;

  /** Constants signifying binary encoding of data.
  * The constant value is {@value} */
  public static final int ENC_BINARY = 0;

  /** Constants signifying delta encoding of data.
   * The constant value is {@value} */
  public static final int ENC_DELTA = 1;

  /** Constants signifying version reference encoding of data.
   * The constant value is {@value} */
  public static final int ENC_VERSIONREF = 3;

  public static final int BS_ON_BRANCH = 1;

  /** List of all supported encodings. */
  public static final int[] ALL_ENCODINGS = {TransferHeader.ENC_BINARY,
      TransferHeader.ENC_VERSIONREF, TransferHeader.ENC_DELTA};

  private static final String[] PS_KEYS =
      new String[]{"acceptedEncs","de","hash","me","size","bstate"};

  // Never null (we need class), null values are never serialized
  private static final Object[] PS_DEFAULTS =
      new Object[] {ALL_ENCODINGS,new Integer(ENC_NONE),new byte[] {},
      new Integer(ENC_NONE),new Long(-1L),new Integer(0)};

  public PropertySerializable.ClassInfo propInit() {
    return new PropertySerializable.ClassInfo("header",PS_KEYS,PS_DEFAULTS);
  }

  public Object[] propSerialize() {
    return new Object[] {acceptedEncodings,new Integer(encoding),
    hash,new Integer(metaEncoding),new Long(sizeEstimate),
    new Integer(branchState)};
  }

  public void propDeserialize(Object[] vals) {
    acceptedEncodings = (int []) vals[0];
    encoding = ((Integer) vals[1]).intValue();
    hash = (byte[]) (vals[2]==PS_DEFAULTS[2] ? null : vals[2]);
    metaEncoding = ((Integer) vals[3]).intValue();
    sizeEstimate = ((Long) vals[4]).longValue();
    branchState = ((Integer) vals[5]).intValue();
  }

  private byte[] hash;
  private long sizeEstimate;
  private int encoding;
  private int metaEncoding;
  private int[] acceptedEncodings;
  private int branchState;

  /** Create empty object.
   *
   */
  public TransferHeader() {
    this(Constants.NO_VERSION, null, Constants.NO_SIZE, ENC_NONE, ENC_NONE, null );
  }

  /** Create transfer header.
   *
   * @param version version of the object
   * @param aHash SHA-1 hash of the object
   * @param aSizeEstimate estimated size
   * @param aEncoding data encoding
   * @param aMetaEncoding matadata encoding
   * @param aAcceptedEncodings encodings accepted for subsequent transfers
   * of the object.
   */
  public TransferHeader(int version, byte[] aHash, long aSizeEstimate,
                 int aEncoding, int aMetaEncoding ,int[] aAcceptedEncodings) {
    hash = aHash;
    sizeEstimate = aSizeEstimate;
    encoding = aEncoding;
    metaEncoding = aMetaEncoding;
    acceptedEncodings = aAcceptedEncodings;
  }

  /** Set hash of object. Hash used is 160 bit SHA-1. */
  public void setHash(byte[] aHash) {
    hash = aHash;
  }

  /** Get hash of object.
   * @see #setHash */
  public byte[] getHash() {
    return hash;
  }

  /** Set size estimate of object. This field indicates the size of the
   * decoded object, to the best of the sender's knowledge. May be set to
   * {@link fc.syxaw.fs.Constants#NO_SIZE}. The field may be used to
   * pre-allocate space, display transfer progress bars etc. The real size
   * of the object may differ.
   */

  public void setSizeEstimate(long aSizeEstimate) {
    sizeEstimate = aSizeEstimate;
  }

  /** Set size estimate of object.
   * @see #getSizeEstimate
   */
  public long getSizeEstimate() {
    return sizeEstimate;
  }

  /** Set data encoding. Allowable encodings are determined by the
   * <code>ENC_*</code> constant fields in this class.
   */

  public void setEncoding(int aEncoding) {
    encoding = aEncoding;
  }

  /** Get data encoding.
   * @see #setEncoding
   */
  public int getEncoding() {
    return encoding;
  }

  /** Set metadata encoding.
   * @see #setEncoding
   */
  public void setMetaEncoding(int aEncoding) {
    metaEncoding = aEncoding;
  }

  /** Get metadata encoding.
   * @see #setEncoding
   */
  public int getMetaEncoding() {
    return metaEncoding;
  }

  /** Set array of accepted encodings. The array contains all encodings
   * accepted by the entity sending the transfer header. Allowable
   * values are determined by the
   * <code>ENC_*</code> constant fields in this class.
   */

  public void setAcceptedEncodings(int[] aEncodings) {
    acceptedEncodings = aEncodings;
  }

  public void setBranchState(int branchState) {
    this.branchState = branchState;
  }

  /** Set array of accepted encodings.
   * @see #setAcceptedEncodings
   */
  public int[] getAcceptedEncodings() {
    return acceptedEncodings;
  }

  public int getBranchState() {
    return branchState;
  }

}
// arch-tag: d8c01f1bfc38fd74c7ba4e37cda15191 *-
