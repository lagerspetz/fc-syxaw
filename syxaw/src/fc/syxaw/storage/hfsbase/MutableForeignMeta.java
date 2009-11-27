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

// $Id: ForeignMetadata.java,v 1.14 2004/12/02 11:01:05 ctl Exp $
package fc.syxaw.storage.hfsbase;

import fc.dessy.indexing.Keywords;
import fc.syxaw.api.Metadata;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.GUID;
import fc.util.log.Log;

/** Metadata not implemented by underlying file system. The full metadata for
 * an object ({@link fc.syxaw.fs.FullMetadata}) is stored on the underlying
 * file system as native metadata and non-native. i.e. foreign, metadata.
 * Native metadata is such data as file size, modification time etc., foreign
 * metadata is hash, GUID, links, etc.
 */

public class MutableForeignMeta implements ForeignMetadata {

  //NOTE!!!! MAKE SURE ASSIGNMENTS in createFrom() WORK IF ADDING FIELDS!
  protected GUID guid;
  protected String name;
  protected String link; // link to source
  protected String joins;
  protected String type;
  protected int dataVersion;
  protected int metaVersion;  
  protected long metaModTime;
  protected byte[] hash;
  protected int linkDataVersion;
  protected int linkMetaVersion;
  protected long linkUId = Constants.NO_ID;
  protected int modFlags = 0x0; // 0x0 = No local/link meta or data equality
  protected String branch;
  protected Keywords keywords;

  public MutableForeignMeta() {

  }

  public static MutableForeignMeta createFrom(ForeignMetadata md) {
    MutableForeignMeta wmd = new MutableForeignMeta();
    wmd.guid=md.getGuid();
    wmd.name=md.getName();
    wmd.link=md.getLink();
    wmd.joins=md.getJoins();
    wmd.type=md.getType();
    wmd.dataVersion=md.getDataVersion();
    wmd.metaVersion=md.getMetaVersion();
    wmd.metaModTime=md.getMetaModTime();
    wmd.hash=md.getHash();
    wmd.linkDataVersion=md.getLinkDataVersion();
    wmd.linkMetaVersion=md.getLinkMetaVersion();
    wmd.linkUId=md.getLinkUId();
    wmd.modFlags=md.getModFlags();
    wmd.branch=md.getBranch();
    wmd.keywords=md.getKeywords();
    return wmd;
  }
  
  public GUID getGuid() {
    return guid;
  }

  public String getName() {
    return name;
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

  public long getMetaModTime() {
    return metaModTime;
  }

  public void setGuid(GUID aGuid) {
    guid = aGuid;
  }

  public int getDataVersion() {
    return dataVersion;
  }
  
  public int getMetaVersion() {
    return metaVersion;
  }

  public byte[] getHash() {
    return hash;
  }

  public int getLinkDataVersion() {
    return linkDataVersion;
  }

  public int getLinkMetaVersion() {
    return linkMetaVersion;
  }  

  public void setLinkUId(long aUid) {
    linkUId = aUid;
  }

  public long getLinkUId() {
    return linkUId;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public int getModFlags() {
    return modFlags;
  }

  public String getBranch() {
    return branch;
  }

  public Object clone() {
    try {
      return super.clone();
    } catch (CloneNotSupportedException x ) {
      Log.log("Clone failed",Log.FATALERROR);
    }
    return null;
  }
  
  public void setHash(byte[] aHash) {
    hash = aHash;
  }

  public void setJoins(String aJoins) {
    joins = aJoins;
  }

  public void setLink(String aLink) {
    link = aLink;
  }

  public void setLinkDataVersion(int aVersion) {
    linkDataVersion = aVersion;
  }
  
  public void setLinkMetaVersion(int aVersion) {
    linkMetaVersion = aVersion;
  }
  
  public void setMetaModTime(long aMetaModTime) {
    metaModTime = aMetaModTime;
  }

  public void setModFlags( int aModFlags ) {
    modFlags=aModFlags;
  }

  public void setName(String aName) {
    name = aName;
  }
  
  public void setType(String aType) {
    type = aType;
  }

  
  public void setDataVersion(int aVersion) {
    dataVersion = aVersion;
  }
  
  public void setMetaVersion(int aVersion) {
    metaVersion = aVersion;
  }

  public Keywords getKeywords(){
    return this.keywords;
  }
  
  public void setKeywords(Keywords keywords){
    this.keywords = keywords;
  }

  public void setFromMetadata( Metadata src ) {
    setJoins( src.getJoins() );
    setType( src.getType() );
    setKeywords( src.getKeywords() );
    setMetaModTime( src.getMetaModTime());
  }  
  
}
// arch-tag: b45ea2637d51c9ba77c0362feac6476e *-
