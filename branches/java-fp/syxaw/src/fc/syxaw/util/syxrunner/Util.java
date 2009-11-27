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

package fc.syxaw.util.syxrunner;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Vector;

import fc.util.log.Log;

public class Util {
  /** Get printable String representation of object. Custom formatting is
   * only applied at the first level of nesting, i.e. an array within an array
   * is printed according to Java's <code>.toString</code> method. */

  public static String toString(Object o) {
    return toString(o, 1);
  }

  /** Get printable String representation of object. Custom formatting is
   * only applied at the first <code>levels</code> levels of nesting. */

  public static String toString(Object o, int levels) {
    if (o == null)
      return "null";
    if (o instanceof Throwable) {
      Throwable e = (Throwable) o;
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      PrintStream ps = new PrintStream(bos);
      e.printStackTrace(ps);
      ps.flush();
      return e.getClass().getName() + ": " + e.getMessage() + "\n" +
              bos.toString();
    }
    //if( o instanceof Object[])
    o = box(o);
    if (o.getClass().isArray()) {
      o = box(o);
      Object[] array = (Object[]) o;
      StringBuffer sb = new StringBuffer("[");
      for (int i = 0; i < array.length - 1; i++) {
        sb.append(toString(array[i], levels - 1));
        sb.append(",");
      }
      if (array.length > 0)
        sb.append(toString(array[array.length - 1], levels - 1));
      sb.append("]");
      return sb.toString();
    } else {
      if (levels == 0 )
        return o.toString();
      Vector v = new Vector();
      try {
        o.getClass().getDeclaredMethod("toString", new Class[] {});
        return o.toString(); // Has own toString, use it
      } catch (NoSuchMethodException x) {}

      try {
        Method[] m = o.getClass().getDeclaredMethods();
        for( int i=0;i<m.length;i++) {
          if( !m[i].getName().startsWith("get")
            || m[i].getDeclaringClass().equals(Object.class)
            || m[i].getParameterTypes().length > 0   )
            continue;
          String displayName = m[i].getName().substring(3);
          Object val = m[i].invoke(o, (Object[])null);
          v.add(displayName + "=" +toString(val, levels - 1));
        }
        /*
        // By reflect, do
        //java.beans.BeanInfo info = java.beans.Introspector.getBeanInfo(o.
        //getClass(), Object.class);
        Class cintrospector = Class.forName("java.beans.Introspector");
        Object info = cintrospector.getMethod("getBeanInfo", Class.class,
                                              Class.class).invoke(null,
                new Object[] {o.
                getClass(), Object.class});
        // reflect, do
        //java.beans.PropertyDescriptor[] props = info.getPropertyDescriptors();
        Object[] props =  (Object[])
        info.getClass().getMethod("getPropertyDescriptors",
                                  null).invoke(info, null);
        for (int i = 0; i < props.length; i++) {
          Object val = //props[i].getReadMethod().invoke(o, EMPTY_ARRAY);
                  ((Method) props[i].getClass().getMethod("getReadMethod", null).
                   invoke(props[i], null)).invoke(o, EMPTY_ARRAY);
          String displayName =
                  (String) props[i].getClass().getMethod("getDisplayName", null).
                  invoke(props[i], null);
          v.add(displayName + "=" +
                toString(val, levels - 1));
        }
      } catch (ClassNotFoundException x) {
        return "<<no beans>>";*/
      } catch (Exception e) {
        Log.log("Introspection bombed", Log.ERROR,e);
      }
      return v.toString();
    }
  }

  /** Empty array constant. */
  public final static Object[] EMPTY_ARRAY = new Object[0];
  /** Boxes primitive type[] arrays as Type[]. Note: byte[] is converted
   * to a hex String. */

  // FIXME: dont box byte[] to String, but to Byte[]. make hex string of Byte[]
  // in toString instead.

  public static Object box(Object o ){
    if( o instanceof byte[] )
      // byte[] boxed as hexstring
      return fc.syxaw.util.Util.getHexString((byte[]) o);
    else if( o instanceof int[] ) {
      int[] ar= (int []) o;
      Integer[] val = new Integer[ar.length];
      for( int i=0;i<ar.length;i++)
        val[i]=new Integer(ar[i]);
      return val;
    }  else if( o instanceof long[] ) {
      long[] ar = (long[]) o;
      Long[] val = new Long[ar.length];
      for (int i = 0; i < ar.length; i++)
        val[i] = new Long(ar[i]);
      return val;
    } else if( o instanceof float[] ) {
      float[] ar= (float []) o;
      Float[] val = new Float[ar.length];
      for( int i=0;i<ar.length;i++)
        val[i]=new Float(ar[i]);
      return val;
    } else if( o instanceof double[] ) {
      double[] ar= (double []) o;
      Double[] val = new Double[ar.length];
      for( int i=0;i<ar.length;i++)
        val[i]=new Double(ar[i]);
      return val;
    } else if( o instanceof char[] ) {
      char[] ar= (char []) o;
      Character[] val = new Character[ar.length];
      for( int i=0;i<ar.length;i++)
        val[i]=new Character(ar[i]);
      return val;
    } else if( o instanceof boolean[] ) {
      boolean[] ar= (boolean []) o;
      Boolean[] val = new Boolean[ar.length];
      for( int i=0;i<ar.length;i++)
        val[i]=new Boolean(ar[i]);
      return val;
    }

    return o;
  }
  /*
  public static void main(String args[]) {
    Log.log("Test: " +
            toString(new fc.syxaw.protocol.DownloadRequest("obj", new int[] {1,
            2}, true, false, true, null)),Log.INFO);
  }*/
}

// arch-tag: 008c90d6-956a-4a1c-b74a-7c552c343ca6
