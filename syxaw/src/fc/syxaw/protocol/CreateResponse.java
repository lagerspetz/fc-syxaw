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

// $Id: CreateResponse.java,v 1.9 2005/01/19 16:24:31 ctl Exp $
package fc.syxaw.protocol;

import fc.syxaw.transport.PropertySerializable;

//$EXTDOCDEP
//FIXME-W: Is initialVersion really needed? Don't we need to sync the remote
// metadata at the least (even though content = empty set?) Or pass a common
// md object for all files created?
/** Encapsulates the <code>out</code> parameters of the
 * <code>createObjects</code> RPC.
 * See the Syxaw protocol method reference in the
 * <i>"Specification of Middleware Service Set II"</i> document.
 */

public class CreateResponse implements java.io.Serializable, PropertySerializable {
  String[] objects;
  int initialVersion;

  /** Create empty response.
   *
   */
  public CreateResponse() {

  }

  /** Set initial version. The initial version number is shared by all objects
   * created. */
  public void setInitialVersion(int aVer) {
    initialVersion = aVer;
  }

  /** Get initial version.
   * @see #setInitialVersion */
  public int getInitialVersion() {
    return initialVersion;
  }

  /** Set array of created objects. Each created object is expressed as the
   * default string representation of a {@link fc.syxaw.fs.UID}.
   */

  public void setObjects(String[] aNames) {
    objects = aNames;
  }
  /** Get array of created objects.
   * @see #setObjects
   */
  public String[] getObjects() {
    return objects;
  }

  // PropertySerializable interface

  private static final String[] PS_KEYS =
      new String[]{"initVer","objs"};
  private static final Object[] PS_DEFAULTS =
      {new Integer(0),new String[] {}};

  public ClassInfo propInit() {
   return new PropertySerializable.ClassInfo("createResponse",PS_KEYS,
                                             PS_DEFAULTS);
  }

  public Object[] propSerialize() {
    return new Object[] {new Integer(initialVersion),objects};
  }

  public void propDeserialize(Object[] vals) {
    initialVersion = ((Integer) vals[0]).intValue();
    objects = (String[]) vals[1];
  }

}
// arch-tag: 7a84b99b66e3080f76bb04ecf124755e *-
