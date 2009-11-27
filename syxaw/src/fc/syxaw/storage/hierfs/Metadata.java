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

package fc.syxaw.storage.hierfs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import fc.dessy.indexing.Keywords;
import fc.syxaw.proto.Version;
import fc.syxaw.storage.hfsbase.ObjectDb;
import fc.syxaw.transport.PropertySerializable;
import fc.syxaw.util.Util;
import fc.util.log.Log;
import fc.xml.xmlr.model.StringKey;

/** Metadata that is persistently stored in a non-native manner to the file 
 * system. This is usually quite similar to 
 * {@link fc.syxaw.storage.hfsbase.ForeignMetadata}, but includes any 
 * additional fields needed by the actual file system implementation
 * (e.g. <code>DKEY</code> in this particular implementation), 
 * and excludes any fields whose value will be calculated
 * (e.g. the modflags in this implementation).  
 */

public class Metadata implements Serializable, PropertySerializable {

  // Not private for Java Serialization to work
  StringKey id;
  long linkModStamp;
  long localModStamp;
  String link; // link to source
  String type;
  long dataVersion;
  long metaVersion;
  long metaModTime;
  byte[] hash;
  long linkDataVersion;
  long linkMetaVersion;
  String dkey;
  String joins;
  int metaModStamp;
  String branch = Version.TRUNK;
  Keywords keywords;

  public Metadata() {
  }

  public Metadata(StringKey id,int aFormat, String aType, int aVersion,
                  long aMetaModTime, String dkey) {
    this(aFormat, aType, aVersion, aMetaModTime);
    this.id = id;
    this.dkey =dkey;
  }

  public Metadata(int aFormat, String aType, int aVersion,
                  long aMetaModTime) {
    type = aType;
    dataVersion = aVersion;
    metaVersion = aVersion;
    metaModTime = aMetaModTime;
  }
  
  public String getLink() {
    return link;
  }

  public String getJoins() {
    return joins;
  }

  public String getType() {
    return type;
  }

  
  public int getDataVersion() {
    return (int) dataVersion;
  }
  
  public int getMetaVersion() {
    return (int) metaVersion;
  }

  public long getMetaModTime() {
    return metaModTime;
  }

  public byte[] getHash() {
    return hash;
  }

  public int getLinkDataVersion() {
    return (int) linkDataVersion;
  }
  
  public int getLinkMetaVersion() {
    return (int) linkMetaVersion;
  }


  public long getLinkModStamp() {
    return linkModStamp;
  }

  public long getLocalModStamp() {
    return localModStamp;
  }

  public StringKey getId() {
    return id;
  }

  public void setDkey(String aDkey) {
    dkey = aDkey;
  }

  public String getDkey() {
    return dkey;
  }

  public int getMetaModStamp() {
    return metaModStamp;
  }

  public String getBranch() {
    if( branch == null )
      Log.log("May not be null",Log.ASSERTFAILED);
    return branch;
  }
  
  public Keywords getKeywords(){
    return keywords;
  }
  
  // Prop-sz
  private static final String[] PS_KEYS =
          new String[] {"id", "linkModStamp", "localModStamp", "link", "type",
          "version", "metaModTime", "hash", "linkVersion", "dkey","joins",
          "metaModStamp","branch", "keywords"};

    // Never null (we need class), null values are never serialized
  private static final Object[] PS_DEFAULTS =
          new Object[] {"", new Long( -1), new Long( -1), "", "", new Long( -1),
          new Long( -1), new byte[] {}, new Long( -1), "","",
              new Integer(-1),Version.TRUNK, ""} ;

  public PropertySerializable.ClassInfo propInit() {
    return new PropertySerializable.ClassInfo("header", PS_KEYS, PS_DEFAULTS);
  }

  public Object[] propSerialize() {
    return new Object[] {id.toString(),
          new Long(linkModStamp), new Long(localModStamp),
            link, type, new Long(dataVersion),new Long(metaVersion),
            new Long(metaModTime), hash, new Long(linkDataVersion), new Long(linkMetaVersion),
            dkey, joins, new Integer(metaModStamp),branch, keywords.toStrings()};
  }

  public void propDeserialize(Object[] vals) {
    id = StringKey.createKey( (String) vals[0] );
    linkModStamp = ((Long) vals[1]).longValue();
    localModStamp = ((Long) vals[2]).longValue();
    link = (String) vals[3];
    type = (String) vals[4];
    dataVersion = ((Long) vals[5]).longValue();
    metaVersion = ((Long) vals[6]).longValue();
    metaModTime = ((Long) vals[7]).longValue();
    hash = (byte[]) vals[8];
    linkDataVersion = ((Long) vals[9]).longValue();
    linkMetaVersion = ((Long) vals[10]).longValue();
    dkey = (String) vals[11];
    joins = (String) vals[12];
    metaModStamp = ((Integer) vals[13]).intValue();
    branch = (String) vals[14];
    this.keywords = new Keywords(true, (String) vals[15]);
  }

  /** Simple database storing metadata objects. 
   */

  static class MetaDb extends ObjectDb {

    public MetaDb(File aRoot, boolean updatemayInsert) {
      super(aRoot, updatemayInsert);
    }

    protected byte[] serialize(Object o) {
      ByteArrayOutputStream bos = null;
      try {
        Metadata md = (Metadata) o;
        bos = new ByteArrayOutputStream(512);
        DataOutputStream dos = new DataOutputStream(bos);
        writeUTF(dos,md.id == null ? null : md.id.toString());
        dos.writeLong(md.linkModStamp);
        dos.writeLong(md.localModStamp);
        writeUTF(dos,md.link);
        writeUTF(dos,md.type);
        dos.writeLong(md.dataVersion);
        dos.writeLong(md.metaVersion);
        dos.writeLong(md.metaModTime);
        writeBytes(dos,md.hash);
        dos.writeLong(md.linkDataVersion);
        dos.writeLong(md.linkMetaVersion);
        writeUTF(dos,md.dkey);
        writeUTF(dos,md.joins);
        dos.writeInt(md.metaModStamp); // ARGH^2 was write(), which typechecks,
                                   // but of course only writes a single byte...
        writeUTF(dos,md.branch);
        dos.flush();
        dos.close();
        byte[] szb = bos.toByteArray();
        // Roundtrip test
        //Log.log("===Sz is "+Util.toString(szb)+", len="+szb.length,Log.INFO);
        //deserialize(bos.toByteArray());
        return szb;
      } catch (IOException ex) {
        Log.log("Md write failed", Log.FATALERROR, ex);
      }
      return bos.toByteArray();
    }

    protected Object deserialize(byte[] b) {
      //Log.log("==dsz is "+Util.toString(b)+" len="+b.length,Log.INFO);
      if( b== null ) {
        return null;
      }
      Object val=null;
      try {
        ByteArrayInputStream bin = new ByteArrayInputStream(b);
        DataInputStream din = new DataInputStream(bin);
        Metadata md = new Metadata();
        String sid = readUTF(din);
        md.id = Util.isEmpty(sid) ? null : StringKey.createKey( sid );
        md.linkModStamp = din.readLong();
        md.localModStamp = din.readLong();
        md.link = readUTF(din);
        md.type = readUTF(din);
        md.dataVersion = din.readLong();
        md.metaVersion = din.readLong();
        md.metaModTime = din.readLong();
        md.hash = readBytes(din);
        md.linkDataVersion = din.readLong();
        md.linkMetaVersion = din.readLong();
        md.dkey = readUTF(din);
        md.joins = readUTF(din);
        md.metaModStamp = din.readInt();
        md.branch = readUTF(din);
        din.close();
        val = md;
      } catch (Exception ex) {
        Log.log("Md read failed", Log.FATALERROR, ex);
      }
      return val;
    }

    private final void writeBytes( DataOutputStream dout, byte[] s )
        throws IOException{
      if( s == null ) {
        dout.writeByte(0);
        return;
      }
      int len = 0x100+s.length;
      if( len > 32767 )
        Log.log("Too long string",Log.ASSERTFAILED);
      dout.writeShort(len);
      dout.write(s);
    }

    private final byte[] readBytes( DataInputStream din )
        throws IOException {
      int len0 = din.readByte()&0xff;
      if (len0==0)
        return null;
      int len = ((len0-1)<<8) + (din.readByte()&0xff); // ARGH
        // I cannot believe i fell for the x<<8+y precedence error AGAIN!
        // That means x<<(8+y), not (x<<8)+y!!!
      byte[] b = new byte[len];
      int pos=0;
      for( int count=0;(len-pos > 0) &&
                     (count=din.read(b,pos,len-pos))>=0;pos+=count);
      if( len-pos > 0 )
        Log.log("Unexpected end of stream",Log.FATALERROR);
      return b;
    }

    private final void writeUTF( DataOutputStream dout, String s )
        throws IOException{
      writeBytes(dout,s==null ? null : s.getBytes());
    }

    private final String readUTF( DataInputStream din )
        throws IOException {
      byte[] b = readBytes(din);
      return b == null ? null : new String(b);
    }

  }
  
}
// arch-tag: aa8c8dab-def4-4d88-afb4-3bf66815faff
