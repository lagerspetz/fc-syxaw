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


public class SynchronizedCache implements Cache {

  private Cache c;

  public SynchronizedCache(Cache c) {
    this.c = c;
  }

  public synchronized boolean containsKey(Object k) {
    return c.containsKey(k);
  }

  public synchronized Object get(Object key) {
    return c.get(key);
  }

  public synchronized Object put(Object key, Object value) {
    return c.put(key, value);
  }

  public synchronized Object remove(Object key) {
    return c.remove(key);
  }

  public synchronized void clear() {
    c.clear();
  }
   
}

// arch-tag: 411c6b58-6999-4a90-81c2-e1ce160c43b7
