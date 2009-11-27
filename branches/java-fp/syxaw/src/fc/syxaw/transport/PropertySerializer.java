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

// $Id: PropertySerializer.java,v 1.11 2005/01/20 10:59:02 ctl Exp $
package fc.syxaw.transport;

import java.io.IOException;
import java.util.Map;

import fc.util.log.Log;

/** Class for serializing an object as a list of properties. The class
 * serializes an object as a map of String keys and String values.
 *
 * <b>Note:</b>The current implementation is quite limited:<ol>
 * <li>Only Java beans properties are serialized.</li>
 * <li>The Java beans properties may not be of any type. The following
 * types are supported: <code>Boolean</code>, <code>Byte</code>,
 * <code>Short</code>, <code>Character</code>,
 * <code>Integer</code>, <code>Long</code>, <code>Float</code>,
 * <code>Double</code>, and their corresponding
 * primitive types, as well as
 * <code>String</code>, <code>int[]</code>, <code>byte[]</code> and
 * <code>String[]</code>. </li>
 * <li>String arrays containing spaces cannot be serialized.</li>
 * <li>Successive invocations of <code>writeObject</code> will
 * overwrite previous map entries, if the object has
 * any Java beans properties with the same name as a previously
 * serialized object.
 * </li>
 * </ol>
 * Each bean property is serialized as a key,value pair in the hashmap.
 * The key is the Java Beans display name of the property. The value is
 * a <code>String</code>, whose encoding depends on the type of the property as
 * follows:
 * <dl>
 * <dt><code>int []</code></dt>
 * <dd>The base-10 ASCII representation of the integers, separated by spaces, eg:
 * <code>1 2 3 4 5</code></dd>
 * <dt><code>byte []</code></dt>
 * <dd>Each byte as a 2-digit hexadecimal number, e.g.
 * <code>[0,31,255,128]</code>=<code>001fff80</code></dd>
 * <dt><code>String []</code></dt>
 * <dd>Each string in the array encoded in UTF-8 and delimetered by space.
 * Spaces cannot be serialized! E.g.
 * <code>{"Hello","World"}</code>=<code>Hello World</code></dd>
 * <dt>Other</dt>
 * <dd>The String representation obtained with <code>toString()</code></dd>
 * </dl>
 * <p>Example serialization of a {@link fc.syxaw.protocol.CreateResponse}
 * (key:value):
<pre>
 initialVersion:1000
 names:/id/alc56HJhtfsiy2Wf /id/alc56Hhjtfsiy2Wf /id/alc8yyyhtfsiy2Wf
</pre>
 */

public class PropertySerializer {

  protected Map dataList= null; //new HashMap();

  /** Create stream.
   *
   * @param dataList map to serialize to
   */
  public PropertySerializer(Map dataList) {
    this.dataList = dataList;
  }

  /** Serialize object.
   *
   * @param obj object to serialize
   * @throws IOException Serialization failure
   */

  public void writeObject(PropertySerializable obj ) throws IOException {
    writeObject(obj,"");
  }

  protected String writeObject(PropertySerializable obj,
      String nameSpace) throws IOException {
    Class objC = obj.getClass();
    if( objC.isArray() )
      Log.log("Not implemented",Log.ASSERTFAILED);
    Object[] sprops=null;
    String [] psKeys = null;
    PropertySerializable.ClassInfo ci = null;
    try {
      ci = (PropertySerializable.ClassInfo)
           PropertySerializable.ClassInfo.classInfos.get(objC);
      if (ci == null) {
        ci = ((PropertySerializable) obj).propInit();
        PropertySerializable.ClassInfo.classInfos.put(objC, ci);
      }
      psKeys = ci.keys;
      sprops = ((PropertySerializable) obj).propSerialize();
      for (int i = 0; i < sprops.length; i++) {
        String name = nameSpace + psKeys[i];
        Object val = sprops[i];
        if( ci != null && (val==null || ci.defaults[i].equals(val) ) )
          continue; // Default val, no serialization
        if( val instanceof String )
          putField(name,(String) val );
        else if( val instanceof Boolean )
          putField(name,((Boolean) val).booleanValue() );
        else if( val instanceof Byte )
          putField(name,((Byte) val).byteValue() );
        else if( val instanceof Short )
          putField(name,((Short) val).shortValue() );
        else if( val instanceof Character )
          putField(name,((Character) val).charValue() );
        else if( val instanceof Integer )
          putField(name,((Integer) val).intValue() );
        else if( val instanceof Long )
          putField(name,((Long) val).longValue() );
/*        else if( val instanceof Float )
          putField(name,((Float) val).floatValue() );
        else if( val instanceof Double )
          putField(name,((Double) val).doubleValue() );*/
        else if( val instanceof int[] )
          putField(name, (int []) val );
        else if( val instanceof byte[] )
          putField(name, (byte []) val );
        else if( val instanceof String[] )
          putField(name, (String []) val );
        else if( val instanceof PropertySerializable[] ) {
          PropertySerializable[] valarr = (PropertySerializable[]) val;
          for( int j=0;j<valarr.length;j++ )
            writeObject(valarr[j],name+j+".");
          putField(name,valarr.length); // Hint length to deserializer
        } else if( val!=null ) {
            // Originally: FP_MISSING, now: ERROR
          Log.log("Unable to serialize type: "+val.getClass(),Log.ERROR);
        }
      }
    } catch ( Exception x ) {
      Log.log("Failed to introspect",Log.FATALERROR,x);
    }
    return ci!=null ? ci.serName : null;
  }

  public void flush()  {
    // NOP
  }

  public void close()  {
    // NOP
  }

  public void reset() {
    // NOP
  }

  // Putfields
  protected void doPutField( String name, String valueAsString ) {
    if( valueAsString == null )
      return;
    if( dataList.put(name,valueAsString) != null )
      Log.log("Non-unique fields acrosss objects not implemented",Log.FATALERROR);
  }

  protected void putField( String name, byte b)  {
    doPutField(name,String.valueOf(b));
  }

  protected void putField( String name, String[] v)  {
    StringBuffer sb = new StringBuffer();
    for( int i=0;i<v.length;i++ ) {
      if( v[i].indexOf(' ')!=-1 )
        Log.log("Can't serialize ' ' in String[]",Log.ASSERTFAILED);
      sb.append( v[i] );
      if( i!=v.length-1 )
        sb.append(' ');
    }
    doPutField(name, sb.toString() );
  }

  protected void putField( String name, byte[] v)  {
    final char[] hexDigits={'0','1','2','3','4','5','6','7','8',
    '9','a','b','c','d','e','f'};
    StringBuffer sb = new StringBuffer();
    for( int i=0;i<v.length;i++ ) {
      sb.append( hexDigits[(v[i]>>4)&0xf] );
      sb.append( hexDigits[v[i]&0xf] );
    }
    doPutField(name, sb.toString() );
  }


  protected void putField( String name, boolean v)  {
    doPutField(name,String.valueOf(v));
  }

  protected void putField( String name, char v)  {
    doPutField(name,String.valueOf(v));
  }

  protected void putField( String name, int v)  {
    doPutField(name,String.valueOf(v));
  }

  protected void putField( String name, long v)  {
    doPutField(name,String.valueOf(v));
  }

  protected void putField( String name, float v)  {
    doPutField(name,String.valueOf(v));
  }

  protected void putField( String name, double v)  {
    doPutField(name,String.valueOf(v));
  }

  protected void putField( String name, String s)  {
    doPutField(name,String.valueOf(s));
  }

  protected void putField( String name, int[] v)  {
    StringBuffer sb = new StringBuffer();
    for( int i=0;i<v.length-1;i++ ) {
      sb.append(v[i]);
      sb.append(' ');
    }
    if( v.length > 0 )
      sb.append(v[v.length-1]);
    doPutField(name,sb.toString());
  }

/* Test code for serialization of Object[]:

  public static void main(String[] args) throws java.io.IOException {
    fc.syxaw.protocol.DownloadRequest dlrq1 = new
        fc.syxaw.protocol.DownloadRequest("obj1",new int[] {1000},true,true,null);
    fc.syxaw.protocol.DownloadRequest dlrq2 = new
        fc.syxaw.protocol.DownloadRequest("obj2",new int[] {2000},false,true,null);
    fc.syxaw.protocol.DownloadRequest dlrq3 = new
        fc.syxaw.protocol.DownloadRequest("obj3",new int[] {1234},false,false,null);


    fc.syxaw.protocol.DownloadRequest dlrq = new
        fc.syxaw.protocol.DownloadRequest("root",new int[] {1000},true,true,
         //null
         new fc.syxaw.protocol.DownloadRequest[] {dlrq1,dlrq2,dlrq3}
      );
    java.util.Properties sd = new java.util.Properties();
    (new PropertySerializer(sd)).writeObject(dlrq);
    sd.store(System.out,"");

    fc.syxaw.protocol.DownloadRequest dser = new
        fc.syxaw.protocol.DownloadRequest();
    (new PropertyDeserializer(sd)).readObject(dser);

    Log.log("After dser/ser cycle:", Log.DEBUG);
    java.util.Properties sd2 = new java.util.Properties();
    (new PropertySerializer(sd2)).writeObject(dser);
    sd2.store(System.out,"");
    Log.log("Equality: "+sd2.equals(sd), Log.DEBUG);

  }*/
}
// arch-tag: 53ba07f70a5491a716e517e094834f7a *-
