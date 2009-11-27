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

// $Id: PropertyDeserializer.java,v 1.10 2005/01/20 12:06:30 ctl Exp $
package fc.syxaw.transport;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;

import fc.util.StringUtil;
import fc.util.log.Log;

/** Deserialize object from property map.
 * @see PropertySerializer for a description of the serialized format and
 * current limitations.
 */

public class PropertyDeserializer  {

  protected Map dataList = new HashMap();

  /** Create deserializer.
   * @param properties Map to deserialize from
   */

  public PropertyDeserializer( Map properties )  {
    dataList = properties;
  }

  /** Read object from map.
   * @param obj Object which is intialized with the values from the map.
   */
  public Object readObject( PropertySerializable obj ) {
    return readObject(obj,"");
  }

  protected Object readObject( PropertySerializable obj, String nameSpace ) {
    Class objC = obj.getClass();
    if( objC.isArray() )
      Log.log("Not implemented",Log.ASSERTFAILED);
    String name = null; // Can be moved inside for loop if error info is removed
    String [] psKeys = null;
    Object [] sprops = null;
    PropertySerializable.ClassInfo ci = null;
    Class types[]=null;
    try {
      ci = (PropertySerializable.ClassInfo)
           PropertySerializable.ClassInfo.classInfos.get(objC);
      if (ci == null) {
        ci = ((PropertySerializable) obj).propInit();
        PropertySerializable.ClassInfo.classInfos.put(objC, ci);
      }
      psKeys = ci.keys;
      types = ci.keyClasses;
      sprops = new Object[psKeys.length];
      System.arraycopy(ci.defaults, 0, sprops, 0, psKeys.length);
      for (int i = 0; i < psKeys.length; i++) {
        Class type = types[i];
        name = nameSpace + psKeys[i];
        if( !dataList.containsKey(name) ) {
          //Log.log("No val for: "+name,Log.INFO);
          continue; // No value for this property
        }
//        Log.log("DS "+name,Log.INFO);
        Object val = null;
        if( dataList.get(name) == null )
          val = null;
        else if( type == String.class )
          val = new String(readString(name));
        else if( type == Boolean.class || type == boolean.class )
          val = new Boolean(readBoolean(name));
        else if( type == Byte.class || type == byte.class )
          val = new Byte(readByte(name));
        else if( type == Short.class || type == short.class )
          val = new Short(readShort(name));
        else if( type == Character.class || type == char.class )
          val = new Character(readChar(name));
        else if( type == Integer.class || type == int.class )
          val = new Integer(readInt(name));
        else if( type == Long.class || type == long.class )
          val = new Long(readLong(name));
/*        else if( type == Float.class || type == float.class )
          val = new Float(readFloat(name));
        else if( type == Double.class || type == double.class )
          val = new Double(readDouble(name));*/
        else if( type == int[].class )
          val = readIntArray(name);
        else if( type == byte[].class )
          val = readByteArray(name);
        else if( type == String[].class )
          val = readStringArray(name);
        else if( PropertySerializable[].class.isAssignableFrom(type) ) {
          int len = readInt(name);
          Class entryType = type.getComponentType();
          PropertySerializable[] valarr =
                  (PropertySerializable[]) Array.newInstance(entryType,len);
          for(int j=0;j<len;j++) {
            PropertySerializable entry = (PropertySerializable)
                                         entryType.newInstance();
            readObject(entry, name + j + ".");
            valarr[j]=entry;
          }
          val = valarr;
        } else
          Log.log("Not implemented type "+type.getName(),Log.ASSERTFAILED);
        if( val != null ) {
          sprops[i] = val;
	}
      }
      obj.propDeserialize(sprops);
    } catch ( Exception x ) {
      Log.log("Failed to read object, name="+name+", map="+dataList,Log.FATALERROR,x);
    }

    return null;
  }

  // Field decoders

  protected int readInt( String key ) throws IOException {
    try {
      return Integer.parseInt((String) dataList.get(key));
    } catch (java.lang.NumberFormatException x ) {
      throw new IOException(x.getMessage());
    }
  }

  protected boolean readBoolean( String key ) {
    return (new Boolean((String) dataList.get(key))).booleanValue();
  }

  protected byte readByte( String key ) throws IOException  {
    return (byte) readInt(key);
  }

  protected short readShort( String key ) throws IOException  {
    try {
      return Short.parseShort((String) dataList.get(key));
    } catch (java.lang.NumberFormatException x ) {
      throw new IOException(x.getMessage());
    }
  }

  protected char readChar( String key ) throws IOException {
    String val = (String) dataList.get(key);
    if( val.length() != 1 )
      throw new IOException("Illegal char");
    return val.charAt(0);
  }

  protected long readLong( String key ) throws IOException  {
    try {
      return Long.parseLong((String) dataList.get(key));
    } catch (java.lang.NumberFormatException x ) {
      throw new IOException(x.getMessage());
    }
  }

  protected float readFloat( String key ) throws IOException  {
    try {
      return Float.parseFloat((String) dataList.get(key));
    } catch (java.lang.NumberFormatException x ) {
      throw new IOException(x.getMessage());
    }

  }

  protected double readDouble( String key ) throws IOException  {
    try {
      return Double.parseDouble((String) dataList.get(key));
    } catch (java.lang.NumberFormatException x ) {
      throw new IOException(x.getMessage());
    }

  }

  protected String readString( String key ) {
    return (String) dataList.get(key);
  }


  protected int[] readIntArray( String key ) throws IOException  {
    try {
      String val = (String) dataList.get(key);
      if( val.length() == 0)
        return new int[0]; // Empty array
      String[] nums = StringUtil.split( val, ' ');
      int ivals[] = new int[nums.length];
      for (int i = 0; i < nums.length; i++) {
        ivals[i] = Integer.parseInt(nums[i]);
      }
      return ivals;
    } catch (java.lang.NumberFormatException x ) {
      throw new IOException(x.getMessage());
    }
  }

  protected String[] readStringArray( String key ) throws IOException  {
    String val = (String) dataList.get(key);
    if( val.length() == 0)
      return new String[0]; // Empty array
    return StringUtil.split( val, ' ');
  }

  protected byte[] readByteArray( String key ) throws IOException {
    String val = (String) dataList.get(key);
    if( val.length() == 0)
      return new byte[0]; // Empty array
    if( val.length() %2 != 0  )
      throw new IOException("Invalid length");
    byte[] storage = new byte[val.length()/2];
    for( int i=0;i<val.length();i+=2) {
      storage[i/2]=(byte) Integer.parseInt(val.substring(i,i+2),16);
    }
    return storage;
  }

}
// arch-tag: b3bd363f563861b97280475ff9d63c30 *-
