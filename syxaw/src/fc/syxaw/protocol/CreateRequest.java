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

// $Id: CreateRequest.java,v 1.5 2005/01/19 16:24:30 ctl Exp $

package fc.syxaw.protocol;

import fc.syxaw.transport.PropertySerializable;

//$EXTDOCDEP
/** Encapsulates the <code>in</code> parameters of the
 * <code>createObjects</code> RPC.
 * See the Syxaw protocol method reference in the
 * <i>"Specification of Middleware Service Set II"</i> document.
 */

public class CreateRequest implements java.io.Serializable, PropertySerializable {

  int objectCount = -1;

  /** Create empty request.
   *
   */
  public CreateRequest() {

  }

  /** Object constructor.
   * @param aCount number of objects requested
   */

  public CreateRequest(int aCount) {
    objectCount = aCount;
  }

  /** Get number of objects requested */
  public int getObjectCount( ) {
    return objectCount;
  }

  /** Set number of objects requested */
  public void setObjectCount( int aCount ) {
    objectCount = aCount;
  }

  // Propserializable
  private static final String[] PS_KEYS = new String[]{"count"};
  private static final Object[] PS_DEFAULTS = {new Integer(0)};

  public ClassInfo propInit() {
   return new PropertySerializable.ClassInfo("createRq",PS_KEYS,PS_DEFAULTS);
  }

  public Object[] propSerialize() {
    return new Object[] {new Integer(objectCount)};
  }

  public void propDeserialize(Object[] vals) {
    objectCount = ((Integer) vals[0]).intValue();
  }
}
// arch-tag: b63bd0ad4f56b166534ff684e0f6f1ce *-
