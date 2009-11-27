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

// $Id: StringHolder.java,v 1.3 2004/12/02 09:30:36 ctl Exp $
package fc.syxaw.util;

/** Holder class for a string.
 */
public class StringHolder {
  String str;

  /** Create a new holder.
   *
   * @param aStr String to hold
   */
  public StringHolder(String aStr) {
    str=aStr;
  }

  /** Set string.
   *
   * @param aStr String
   */
  public void setString(String aStr) {
    str=aStr;
  }

  /** Get string.
   *
   * @return String
   */
  public String getString() {
    return str;
  }

  /** Get string.
   *
   * @return String
   */
  public String toString() {
    return str;
  }
}
// arch-tag: 203a0dc98c29329d3b6a00dc6cee85a0 *-
