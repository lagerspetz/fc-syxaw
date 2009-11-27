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

// $Id: MergeUtil.java,v 1.2 2003/10/13 11:03:52 ctl Exp $
package fc.syxaw.tdm;

import fc.syxaw.util.Util;

/** Utility class for three-way merging of primitives. All <code>merge()</code>
 * methods work in the same way: return the value that represents a change
 * with respect to the base, and signal a conflict if both values have changed,
 * and the changes differ. In pseudocode:<pre>
merge(base,v1,v2) {
&nbsp;if( v1 != base && v2 != base ) {
&nbsp;  if(v1 == v2 )
&nbsp;    return v1
&nbsp;  else
&nbsp;    signal conflict
&nbsp;} else if( v2 != base )
&nbsp;   return v2
&nbsp;else
&nbsp;   return v1
}</pre>
 */


public class MergeUtil {

  /** Exception signaling conflicting updates. */
  public static class Conflict extends Exception {}

  /** Merge <code>int</code>s.
   * @see #merge(Object,Object,Object)
   */

  public static int merge(int b, int v1, int v2) throws Conflict {
    return ((Integer) merge( new Integer(b), new Integer(v1),
                             new Integer(v2))).intValue();
  }

  /** Merge <code>long</code>s.
   * @see #merge(Object,Object,Object)
   */

  public static long merge(long b, long v1, long v2) throws Conflict {
    return ((Long) merge( new Long(b), new Long(v1),
                             new Long(v2))).longValue();
  }

  /** Merge <code>boolean</code>s.
   * @see #merge(Object,Object,Object)
   */

  public static boolean merge(boolean b, boolean v1, boolean v2) throws Conflict {
    return ((Boolean) merge( new Boolean(b), new Boolean(v1),
                             new Boolean(v2))).booleanValue();
  }

  // etc.. implement for other primitive types

  /** Merge objects. Equality is determined using the
   * <code>equals()</code> method.
   * @param b base object
   * @param v1 object from branch 1
   * @param v2 object from branch 2
   * @return merged value
   * @throws Conflict if v1 and v2 are conflicting updates
   */

  public static Object merge(Object b, Object v1, Object v2) throws Conflict {
    boolean e1 = Util.equals(b,v1), e2 = Util.equals(b,v2);
    if (e1 && e2)
      return b;
    else if (e1 && !e2)
      return v2;
    else if (!e1 && e2)
      return v1;
    else if (Util.equals(v1,v2) ) // Implicitly: !e1 && !e2
      return v1; // Equal updates
    else
      throw new Conflict();
  }

}// arch-tag: 1dbdf3617316f29e15c122456236644c *-
