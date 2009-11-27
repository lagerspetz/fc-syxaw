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

public interface Cache {

  public static final int TEMP_LOW = 0;

  public static final int TEMP_MED = 10;

  public static final int TEMP_HIGH = 20;

  public Object get(Object key);

  public Object put(Object key, Object value);

  public Object remove(Object key);

  public boolean containsKey(Object k);

  public void clear();
}

// arch-tag: d9ec5a8c-aad2-4d77-9d14-415baa80a04a
