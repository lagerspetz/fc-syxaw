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

// $Id: FullMetadataImpl.java,v 1.3 2003/09/17 21:25:41 ctl Exp $
// old file: MetadataImpl.java,v 1.5 2003/06/26 15:13:55 ctl Exp
package fc.syxaw.fs;

import fc.syxaw.api.MetadataImpl;
import fc.syxaw.api.Metadata;

/** Default implementation of <code>FullMetadata</code>. */

public class FullMetadataImpl extends MetadataImpl implements FullMetadata {

  private GUID guid;
  private String link;
  private byte[] hash;
  private long length;
  private int linkDataVersion;
  private int linkMetaVersion;
  private int dataVersion;
  private int metaVersion;
  private String branch;

  public static FullMetadataImpl createFrom(FullMetadata fmd) {
    FullMetadataImpl fmi = new FullMetadataImpl();
    fmi.guid=fmd.getGuid();
    fmi.link=fmd.getLink();
    fmi.hash=fmd.getHash();
    fmi.length=fmd.getLength();
    fmi.linkDataVersion=fmd.getLinkDataVersion();
    fmi.linkMetaVersion=fmd.getLinkMetaVersion();
    fmi.dataVersion=fmd.getDataVersion();
    fmi.metaVersion=fmd.getMetaVersion();
    fmi.branch=fmd.getBranch();
    fmi.keywords=fmd.getKeywords();
    
    fmi.readOnly=fmd.getReadOnly();
    fmi.joins=fmd.getJoins();
    fmi.type=fmd.getType();
    fmi.modTime=fmd.getModTime();
    fmi.metaModTime=fmd.getMetaModTime();
    return fmi;
  }
/*
   public static FullMetadataImpl createFrom(Metadata md) {
     FullMetadataImpl fmi = new FullMetadataImpl();
     fmi.readOnly=md.getReadOnly();
     fmi.joins=md.getJoins();
     fmi.type=md.getType();
     fmi.modTime=md.getModTime();
     fmi.metaModTime=md.getMetaModTime();
     fmi.keywords=md.getKeywords();
     return fmi;
   }
*/   

  /*
  public static FullMetadataImpl createFullFrom(Metadata md) {
    FullMetadataImpl fmi = new FullMetadataImpl();
    fmi.readOnly=md.getReadOnly();
    fmi.joins=md.getJoins();
    fmi.type=md.getType();
    fmi.modTime=md.getModTime();
    fmi.metaModTime=md.getMetaModTime();
    return fmi;
  }*/
  
  public void setGuid(GUID aGuid) {
    guid = aGuid;
  }

  public GUID getGuid() {
    return guid;
  }

  public void setLink(String aLink) {
    link = aLink;
  }

  public String getLink() {
    return link;
  }

  public void setHash(byte[] aHash) {
    hash = aHash;
  }

  public byte[] getHash() {
    return hash;
  }

  public void setLength(long aLength) {
    length = aLength;
  }

  public long getLength() {
    return length;
  }

  public void setLinkDataVersion(int aVersion) {
    linkDataVersion = aVersion;
  }

  public int getLinkDataVersion() {
    return linkDataVersion;
  }
  
  public void setLinkMetaVersion(int aVersion) {
    linkMetaVersion = aVersion;
  }

  public int getLinkMetaVersion() {
    return linkMetaVersion;
  }

  public void setDataVersion(int dataVersion) {
    this.dataVersion = dataVersion;
  }

  public int getDataVersion() {
    return dataVersion;
  }

  public void setMetaVersion(int metaVersion) {
    this.metaVersion = metaVersion;
  }

  public int getMetaVersion() {
    return metaVersion;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getBranch() {
    return branch;
  }
  
  
  /*  
  public Metadata asMetadata() {
    Metadata md = new fc.syxaw.api.MetadataImpl();
    assignTo(md);
    return md;
  }

  public void assignTo(Metadata target) {
    target.setJoins(getJoins());
    target.setMetaModTime(getMetaModTime());
    target.setModTime(getModTime());
    target.setReadOnly(getReadOnly());
    target.setType(getType());
    target.setKeywords(getKeywords());
  }
  */
  
  public void assignFrom(Metadata src) {
    setJoins(src.getJoins());
    setMetaModTime(src.getMetaModTime());
    setModTime(src.getModTime());
    setReadOnly(src.getReadOnly());
    setType(src.getType());
    setKeywords(src.getKeywords());
  }

}// arch-tag: dd863094e0cba281da3f0aa9fa65f5a8 *-
