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

// $Id: ObjectDb.java,v 1.5 2005/04/28 14:00:53 ctl Exp $
package fc.syxaw.storage.hfsbase;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;

//import org.w3c.tools.dbm.jdbm;

import fc.syxaw.util.Cache;
import fc.syxaw.util.LruCache;
import fc.util.bytedb.Sdbm;
import fc.util.Debug;
import fc.util.log.Log;


/** Key-value database. The class implements as simple file-based
 * database that maps non-empty Strings to non-empty Strings.
 *  The database is stored in the directory given by
 * {@link fc.syxaw.storage.hfsbase.Config#INDEX_FOLDER}. To enhance
 * performance, read accesses are LRU cached.
 * <p>Implementation note: the database is consists of a collection of files,
 * one for each entry, in the database directory. The name of each file is
 * derived from the corresponding key, and the contents of the file is the
 * value. Needless to say, this is a very slow implementation. But it's
 * easy to hack with!
 */

// NOTE: _* functions are for debugging purposes (gathering cache hit/miss
// statistics )

public abstract class ObjectDb {

  private Sdbm db; // Change type here!

  /** Special symbol for null value. This symbol signifies an empty value
   * and may be read or written. Note that the empty string is not allowed.
   */
  //private static final String NULL_VALUE = "ObjectDB:<null>";

  private static final Object DELETED_ITEM = new Object();
  /** Max number of entries in read cache. */
  public static final int MEMCACHE_SIZE = 64;

  Cache cache = new LruCache(Cache.TEMP_HIGH, MEMCACHE_SIZE);
  protected File root;

  /** Create new instance.
   *
   * @param aRoot directory holding the database
   */
  public ObjectDb(File aRoot) {
    this(aRoot,false);
  }

  public ObjectDb(File aRoot, boolean updatemayInsert) {
    root = aRoot;
    try {
      db = new Sdbm(aRoot, "db", "rw");
    } catch (IOException e) {
      Log.log("Failed to initialize database "+aRoot, Log.FATALERROR, e);
    }
  }

  /** Allocate String and insert value.
   *
   * @param value value to insert
   * @return String Automatically allocated String that is the key to the inserted value.
   */

  /** Insert String,value. */
  public synchronized void insert(String key,Object value) {
    insert( key == null ? null : key.getBytes(), value);
  }

  public synchronized void insert(byte[] key,Object value) {
    try {
      if ( (cache.containsKey(key) && cache.get(key) != DELETED_ITEM)
          || db.containsKey(key))
        Log.log("Id already allocated, val="+lookup(key), Log.FATALERROR, Debug.toPrintable(key));
      db.put(key, serialize(value));
    } catch (IOException e) {
      Log.log("Failed to write ID value",Log.FATALERROR,e);
    }
    cache.put(key,value);
  }

  public synchronized void delete(String key) {
    delete(key==null ? null : key.getBytes());
  }
  /** Delete entry. */
  public synchronized void delete(byte[] key) {
    try {
      db.remove(key);
    } catch (IOException e) {
      Log.log("Failed to remove id " + key, Log.FATALERROR, e);
    }
    cache.put(key,DELETED_ITEM);
  }

  public synchronized void update(String key, Object value) {
    update(key == null ? null : key.getBytes(),value);
  }  
  /** Update value of entry. */
  public synchronized void update(byte[] key, Object value) {
/*    if( !updatemayInsert && !cache.containsKey(key) && !f.exists() )
      Log.log("Update to non-existing key "+key,Log.FATALERROR);*/
    if( cache.containsKey(key) ) _hit(); else _miss(key);
    try {
      db.put(key,serialize(value));
    } catch (IOException e ) {
      Log.log("Failed to update ID value",Log.FATALERROR);
    }
    cache.put(key,value);
  }

  /** Lookup value for String. */

  public synchronized Object lookup(String key ) {
    return lookup(key == null ? null : key.getBytes());
  }

  public synchronized Object lookup(byte[] key ) {
    if( key == null || key.length == 0 ) // Can't store empty
      return null;
    Object value = cache.get(key);
    if( value != null ) _hit();
    if( value != null )
      return value == DELETED_ITEM ? null : value;
    _miss(key);
    try {
      value = deserialize(db.get(key));
    } catch (IOException e ) {
      Log.log("Failed to lookup ID strval ["+key+"]",Log.FATALERROR,e);
    }
    cache.put(key,value == null ? DELETED_ITEM : value);
    return value;
  }

  public Iterator keys() {
      final Enumeration en = db.keys();
      return new Iterator() {
            public boolean hasNext() {
                return en.hasMoreElements();
            }

            public Object next() {
                return en.nextElement();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
  }

  // Object ser/deser

  protected abstract byte[] serialize(Object o);
  protected abstract Object deserialize(byte[] b);

  public void close() throws IOException {
	  db.close();
    //db.save();
  }
  // Performance monitoring

  private static int _hits=0, _misses =0;

  private final void _hit() {
    _hits++;
    _stat();
  }

  private final void _miss(byte[] val) {
    //Log.log("Missed "+val,Log.DEBUG);
    _misses++;
    _stat();
  }

  private final void _stat() {
//   if( (_hits+_misses)%100==0)
//      Log.log("IndexDb hit/miss ratio: "+_hits+"/"+_misses+
//              " csize="+cache.size() /*+" cache="+cache*/,Log.INFO);
  }
}
// arch-tag: 48b644148b600a3ad22742317860060a *-
