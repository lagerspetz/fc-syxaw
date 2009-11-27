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

package fc.syxaw.util;

import fc.org.apache.oro.util.CacheLRU;

/** Syxaw LRU cache. The idea is that this class will implement
 * a cache that considers local system resources.
 */
public class LruCache extends CacheLRU implements Cache {

  private long lookups=0l, hits=0l, puts=0l, replaces=0l,containsK=0l;

  /** Create a cache.
   *
   * @param temperature 0-2, 0=low, 1=med, 2=high
   * @param ws working set size for base config
   * @param itemSize estimated average item size
   */

  public LruCache(int temperature, int ws, int itemSize) {
    super(ws);
  }

  public LruCache(int temperature, int ws) {
    super(ws);
  }

  public final Object get(Object key) {
    lookups++;
    Object o=super.get(key);
    hits+=o==null ? 0 : 1;
    return o;
  }

  public final Object put(Object key, Object value) {
    puts++;
    Object o = super.put(key,value);
    replaces+=o==null ? 0 : 1;
    return o;
  }

  public final Object remove(Object key) {
    puts++;
    Object elem = put(key,null);
    replaces+=elem==null ? 0 : 1;
    return elem;
  }

  public final boolean containsKey(Object k) {
    containsK++;
    return get(k)!=null;
  }
}
// arch-tag: 5247e363d195cefb0b118dfaf3cd7a05 *-
