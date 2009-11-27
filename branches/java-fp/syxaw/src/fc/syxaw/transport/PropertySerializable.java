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

package fc.syxaw.transport;

import java.util.HashMap;
import java.util.Map;

public interface PropertySerializable {

  public ClassInfo propInit();
  public Object[] propSerialize();
  public void propDeserialize(Object[] vals);

  public static class ClassInfo {

    static final Map classInfos = new HashMap();
    String serName;
    String[] keys;
    Class[] keyClasses;
    Object[] defaults;

    public ClassInfo(String serName, String[] keys, Object[] defaults) {
      this.serName = serName;
      this.keys = keys;
      this.defaults = defaults;
      keyClasses = new Class[defaults.length];
      for( int i=0;i<keys.length;i++) { // delib keys.length to cause ioob if too few defaults
        if( defaults[i] == null )
          throw new IllegalArgumentException("Null default not allowed");
        keyClasses[i]=defaults[i].getClass();
      }
    }
  }
}
// arch-tag: 7742dfef167e032c716c43303ae0ec4a *-
