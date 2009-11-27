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

// $Id: MetadataImpl.java,v 1.3 2005/01/20 10:59:06 ctl Exp $
// old file: SyxawMetadataImpl.java,v 1.7 2003/06/26 15:13:45 ctl Exp
package fc.syxaw.api;
import fc.dessy.indexing.Keywords;
import fc.syxaw.transport.PropertySerializable;

/** Default implementation of <code>fc.syxaw.api.Metadata</code>.
 * See the {@link fc.syxaw.api.Metadata} class for a description
 * of the fields. */

public class MetadataImpl implements Metadata, java.io.Serializable,
    PropertySerializable {

  protected boolean readOnly;
  protected String joins;
  protected String type;
  protected long modTime;
  protected long metaModTime;
  protected Keywords keywords;
  
  public MetadataImpl() {
  }

  public MetadataImpl(String uid, boolean aReadOnly, String joins, String aType,
                       long aModTime, long aMetaModTime) {
    this.keywords = new Keywords(false, uid);
    readOnly = aReadOnly;
    this.joins = joins;
    type = aType;
    modTime = aModTime;
    metaModTime = aMetaModTime;
  }

  public static MetadataImpl createFrom(Metadata md) {
    MetadataImpl mdi = new MetadataImpl();
    mdi.readOnly=md.getReadOnly();
    mdi.joins=md.getJoins();
    mdi.type=md.getType();
    mdi.modTime=md.getModTime();
    mdi.metaModTime=md.getMetaModTime();
    mdi.keywords=md.getKeywords();
    return mdi;
  }
  
  public void setReadOnly(boolean aReadOnly) {
    readOnly = aReadOnly;
  }

  public boolean getReadOnly() {
    return readOnly;
  }

  public void setJoins(String joins) {
    this.joins = joins;
  }

  public String getJoins() {
    return joins;
  }

  public void setType(String aType) {
    type = aType;
  }

  public String getType() {
    return type;
  }


  public void setModTime(long aModTime) {
    modTime = aModTime;
  }

  public long getModTime() {
    return modTime;
  }

  public void setMetaModTime(long aMetaModTime) {
    metaModTime = aMetaModTime;
  }

  public long getMetaModTime() {
    return metaModTime;
  }
  
  public Keywords getKeywords() {
      return this.keywords;
  }
  
  public void setKeywords(Keywords keywords) {
      this.keywords = keywords;
  }
  
  // PropertySerializable interface

  private static final String[] PS_KEYS =
      new String[] {"mmodt","dmodt","readonly","type","joins", "keywords"};
  private static final Object[] PS_DEFAULTS =
      {new Long(-1l),new Long(-1l),Boolean.FALSE,"","",""};

  public PropertySerializable.ClassInfo propInit() {
   return new PropertySerializable.ClassInfo("meta",PS_KEYS,PS_DEFAULTS);
  }

  public Object[] propSerialize() {
    // For other storage providers...
    if (keywords == null) {
      return new Object[] {new Long(metaModTime),new Long(modTime),
          new Boolean(readOnly),type,joins,""};
    }
    return new Object[] {new Long(metaModTime),new Long(modTime),
        new Boolean(readOnly),type,joins,keywords.toStrings()};
  }

  public void propDeserialize(Object[] vals) {
    metaModTime = ((Long) vals[0]).longValue();
    modTime = ((Long) vals[1]).longValue();
    readOnly = ((Boolean) vals[2]).booleanValue();
    type = (String) vals[3];
    joins = (String) vals[4];
    keywords = new Keywords(true, (String) vals[5]);
  }

  
  
}
// arch-tag: a0355a01a5666765cc57818c8d078dbd *-
