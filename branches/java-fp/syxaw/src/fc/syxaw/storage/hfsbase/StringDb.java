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

// $Id: StringDb.java,v 1.3 2005/04/28 14:00:53 ctl Exp $
package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.util.Iterator;

import fc.util.log.Log;

/** Key-value database for Strings.
 */

public class StringDb {

  public static final String NULL_VALUE ="";

  private ObjectDb db;

  /** Create new instance.
   *
   * @param aRoot directory holding the database
   */
  public StringDb(File aRoot) {
    db = new StringDbImpl(aRoot);
  }

  public StringDb(File aRoot, boolean updatemayInsert) {
    db = new StringDbImpl(aRoot, updatemayInsert);
  }

  /** Allocate String and insert value.
   *
   * @param value value to insert
   * @return String Automaically allocated String that is the key to the inserted value.
   */

  /** Insert String,value. */
  public synchronized void insert(String key,String value) {
    db.insert(key,value);
  }

  /** Delete entry. */
  public synchronized void delete(String key) {
    db.delete(key);
  }

  /** Update value of entry. */
  public synchronized void update(String key, String value) {
    db.update(key,value);
  }

  /** Lookup value for String. */
  public synchronized String lookup(String key ) {
    return (String) db.lookup(key);
  }

  public Iterator keys() {
      return db.keys();
  }

  private static class StringDbImpl extends ObjectDb {

    private static byte[] NULL_BYTES = new byte[0];

    public StringDbImpl(File aRoot) {
      super(aRoot);
    }

    public StringDbImpl(File aRoot, boolean updatemayInsert) {
      super(aRoot, updatemayInsert);
    }

    protected byte[] serialize(Object o) {
      if( o == NULL_VALUE )
        return NULL_BYTES;
      byte[] b = o != null ? ((String) o).getBytes() : null;
      if( b== null || b.length == 0)
        Log.log("Empty strings/null unsupported",Log.ASSERTFAILED);
      return b;
    }

    protected Object deserialize(byte[] b) {
      if( b == null )
        return null;
      if( b.length == 0)
        return NULL_VALUE;
      return new String(b);
    }

  }
}
// arch-tag: 72e031fd5e6626097a48fe8a7576a8c5 *-
