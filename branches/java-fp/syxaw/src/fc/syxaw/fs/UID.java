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

// $Id: UID.java,v 1.9 2004/11/29 20:43:47 ctl Exp $
package fc.syxaw.fs;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;

import fc.syxaw.util.Util;
import fc.util.Base64;

/** Object Unique Id. */
public class UID implements Serializable {

  /** Length of UID in bytes. */
  public static final int LENGTH_BYTES = 16;

  /** UID for the device bootstrap object. */
  public static final UID DBO = createFromBytes(new byte[LENGTH_BYTES]);

  public static final String UID_SEED_PROPERTY =
          "syxaw.uid.seed";
  // NOTE: Fixed seed, although providing relicated cases,
  // will cause GUID collisions upon subsequent runs!
  private static long rndSeed =
          System.getProperty(UID_SEED_PROPERTY) != null ?
          Long.parseLong(System.getProperty(UID_SEED_PROPERTY)) :
          System.currentTimeMillis();
  private static Random rnd = new Random( rndSeed /* 314159L*/);

  private byte[] uidarr=null;

  protected UID() {
  }

  protected UID init(String s, boolean hexString) {
    // Pad missing '=' signs.
    byte[] bytes = hexString ? Util.getBytesFromHexString(s):
          Base64.decode(Util.baseMod64toBase64(s).toCharArray());
    return init(bytes);
  }

  protected UID init(byte[] bytes) {
    if( bytes.length == LENGTH_BYTES )
      uidarr = bytes;
    else if( bytes.length > LENGTH_BYTES )
      throw new IllegalArgumentException("UID too long");
    else {
      uidarr = new byte[LENGTH_BYTES];
      System.arraycopy(bytes, 0, uidarr, 0, bytes.length);
    }
    return this;
  }

  /** Create UID from byte array.
   *
   * @param bytes input byte array
   * @return created UID
   */
  public static UID createFromBytes(byte[] bytes) {
    return (new UID()).init(bytes);
  }

  /** Create UID from hex string.
   *
   * @param hex input hex string
   * @return created UID
   */

  public static UID createFromHex(String hex) {
    return (new UID()).init(hex,true);
  }

  // "" -> DBO
  /** Create UID from modified Base-64 encoded string.
   * See {@link fc.syxaw.util.Util#base64toBase64Mod} for
   * a description of the modified Base-64 format.
   *
   * @param base64m modified Base-64 string
   * @return created UID
   */

  public static UID createFromBase64(String base64m) {
    // Pad missing '=' signs.
    if( base64m.length() == 0 )
      return DBO;
    return (new UID()).init(base64m,false);
  }
  /** Allocate a new UID. Uses a probabilistic algorithm; the returned UID
   * is simply a random bitstring.
   *
   * @return new UID
   */
  public static UID newUID() {
    byte bits[] = new byte[UID.LENGTH_BYTES];
    rnd.nextBytes(bits);
    return UID.createFromBytes(bits);
  }

  /** Get UID bytes.
   *
   * @return UID bytes
   */
  public byte[] getBytes() {
    byte[] bytes = new byte[LENGTH_BYTES];
    System.arraycopy(uidarr,0,bytes,0,bytes.length);
    return bytes;
  }

  /** Convert to hex string.
   *
   * @return UID as hex string.
   */
  public String toHexString() {
    return Util.getHexString(uidarr);
  }

  /** Convert to modified Base-64 encoding. See
   * {@link fc.syxaw.util.Util#base64toBase64Mod} for
   * a description of the modified Base-64 format.
   *
   * @return modified base-64 encoding.
   */
  public String toBase64() {
    // Truncate = signs
    if( this == DBO )
      return "";
    return Util.base64toBase64Mod(new String(Base64.encode(uidarr)));
  }

  /** Convert to string. The default format differs from both the
   * hex and base64 in order to avoid confusing hex and base64.
   */

  public String toString() {
    return "UID{"+toBase64()+"}";
  }

  public boolean equals(Object o) {
    return (o instanceof UID) &&
        Arrays.equals(((UID) o).uidarr,uidarr);
  }

  public int hashCode() {
    return toHexString().hashCode();
  }
}
// arch-tag: 08cf03f5fbdf5b60426f38a7ab1f15e5 *-
