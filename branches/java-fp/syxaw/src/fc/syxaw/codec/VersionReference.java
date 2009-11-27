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

package fc.syxaw.codec;

import java.io.Serializable;

import fc.syxaw.fs.Constants;
import fc.syxaw.transport.PropertySerializable;

/** Object holding a version reference.
* A version reference is an <code>int</code> referencing some version of a
* Syxaw object. */

public class VersionReference implements Serializable, PropertySerializable {
  // The class was previously in
  // fuegocore/syxaw/protocol/VersionReference.java,v 1.2 2003/06/13 14:59:13 ctl Exp $

  private int reference = -1;

  public VersionReference() {
  }

  public VersionReference(int aReference) {
    reference = aReference;
  }

  public void setReference(int aReference) {
    reference = aReference;
  }

  public int getReference() {
    return reference;
  }

  // PropertySerializable interface

  private static final String[] PS_KEYS =
      new String[]{"ver"};
  private static final Object[] PS_DEFAULTS =
      {new Integer(Constants.NO_VERSION)};

  public PropertySerializable.ClassInfo propInit() {
   return new PropertySerializable.ClassInfo("ref",PS_KEYS,PS_DEFAULTS);
  }

  public Object[] propSerialize() {
    return new Object[] {new Integer(reference)};
  }

  public void propDeserialize(Object[] vals) {
    reference = ((Integer) vals[0]).intValue();
  }

}
// arch-tag: 7b25c7a303eecc7c2d70ffec8c7533a3 *-
