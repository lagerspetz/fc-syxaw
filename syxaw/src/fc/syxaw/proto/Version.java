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

package fc.syxaw.proto;

import fc.syxaw.fs.Constants;
import fc.syxaw.fs.GUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import fc.syxaw.util.Util;
import java.io.Serializable;

import fc.util.StringUtil;
import fc.util.log.Log;
import java.util.LinkedList;
import java.util.Collections;

public class Version implements Serializable, Comparable {

  public static final String CURRENT_BRANCH ="//";
  public static final String TRUNK ="";

  private int number;
  private String branch; // v1/d1/ v2/d2/...
  private int localVersion=Constants.NO_VERSION;

  public Version(String branchAndNumber) {
    int splitpos = branchAndNumber.lastIndexOf('/');
    branch =
         splitpos == -1 ? TRUNK :
         branchAndNumber.substring(0, splitpos +1 );
    number = Integer.parseInt(branchAndNumber.substring(splitpos+1));
  }

  public Version(int number, String branch) {
    this.number = number;
    this.branch = branch;
  }

  public Version(String did, int base, int number) {
    this.number = number;
    this.branch = ""+base+"/"+did+"/";
  }

  public Version(int number, String branch, int localVersion) {
    this.number = number;
    setBranch( branch );
    this.localVersion = localVersion;
  }

  public int getNumber() {
    return number;
  }

  public String getBranch() {
    return branch;
  }

  public int getLocalVersion() {
    return localVersion;
  }

  public void setNumber(int number) {
    this.number = number;
  }

  public void setBranch(String branch) {
    if( !Util.isEmpty(branch) && !branch.endsWith("/") )
      Log.log("branch must end in /",Log.ASSERTFAILED);
    this.branch = branch;
  }

  public void setLocalVersion(int localVersion) {
    this.localVersion = localVersion;
  }

  public String getHereBranch() {
    return makeHereBranch(branch,number);
  }

  public static String makeBranch(String baseBranch, String did, int version) {
    return baseBranch +  version + "/" + did + "/";
  }

  public static String makeHereBranch(String baseBranch,  int version) {
    return baseBranch  + version + "/" +GUID.getCurrentLocation() + "/";
  }

  public static Set getBranches(List fullVersions) {
    Set bs = new HashSet();
    for( Iterator i = fullVersions.iterator();i.hasNext();)
      bs.add(((Version) i.next()).getBranch());
    return bs;
  }

  public int getBaseVersion() {
    String[] s = StringUtil.split(branch,'/');
    if( s.length < 2 )
      return Constants.NO_VERSION;
    return Integer.parseInt(s[s.length-3]);
  }

  public String getLID() {
    String[] s = StringUtil.split(branch,'/');
    if( s.length < 2 )
      return null;
    return s[s.length-2];
  }

  public String getBaseBranch() {
    String[] s = StringUtil.split(branch,'/');
    String b = TRUNK;
    for( int i=0;i<s.length-3;i++)
      b+=(s[i]+"/");
    return b;
  }

  public String toString() {
    return getBranch()+getNumber(); //fc.syxaw.proto.Util.toString(this);
  }

 /* public int compareTo(Object o) {
    Version v = (Version) o;
    // Not sure if this will cause trouble...
    //if( !Util.equals(v.getBranch(),getBranch()) )
    //  Log.log("Trying to order vers on different branches",Log.ASSERT_FAILED);
    int bc = getBranch() == null ? -1 :
          getBranch().compareTo(v.getBranch());
    return bc == 0 ? getNumber()-v.getNumber() : bc;
  }*/

  public static int[] getNumbers(List vers) {
    int[] nums = new int[vers.size()];
    final String MARKER ="";
    String lastBranch = MARKER;
    int pos=0;
    for( Iterator i=vers.iterator();i.hasNext(); ) {
      Version v = (Version) i.next();
      // SANITY CHECK START
      if( lastBranch == MARKER || Util.equals(lastBranch,v.getBranch()) )
        lastBranch = v.getBranch();
      else
        Log.log("Mixed branches in list (at least "+lastBranch+","+v.getBranch(),
                Log.ASSERTFAILED);
      // SANITY CHECK END
      nums[pos++] = v.getNumber();
    }
    Log.log("Vlist=",Log.DEBUG,nums);
    return nums;
  }

  public static Set parseJoins(String joins) {
    Set jlist = new HashSet();
    if( joins != null && joins.trim().length() > 0 ) {
      String[] jslist = StringUtil.split(joins, ' ');
      for (int i = 0; i < jslist.length; i++) {
        jlist.add(new Version(jslist[i]));
      }
    }
    return jlist;
  }

  public static String makeJoins(Set joins) {
    List l = new LinkedList(joins);
    Collections.sort(l); // Normalize order
    String s = "";
    for( Iterator i = l.iterator();i.hasNext();) {
      s+= i.next().toString();
      if( i.hasNext() )
        s+= " ";
    }
    return s;
  }

  public void addToJoins( Set joins ) {
    Version toReplace = null;
    for( Iterator i = joins.iterator();i.hasNext() &&
                      toReplace == null;) {
      Version v = (Version) i.next();
      // BUGFIX-050913-4: added the version, although a majoring element existed
      if( equals(v) || compareTo(v) < 1 )
        return; // Majoring/same version in set -> this is already included
      if( compareTo(v) > 1 )
        toReplace = v;
    }
    if( toReplace != null )
      joins.remove(toReplace);
    joins.add(this);
  }

  public String addToJoins( String joins ) {
    Set js = parseJoins(joins);
    addToJoins(js);
    return makeJoins(js);
  }

  // this>v -> 2; -2 this<v; 0 equal, -1..1 || (sign gives alphabetic order)
  // FIXME-P: should probably take an arg regarding on which branch we are comparing
  // E.g 1004 > 1003/rex/1001 on main branch, but || on branch 1003/rex
  // (unless we look at the joins field)
  // NOTE: The branching hierarchy implied by a full version may be what makes our
  // differ from a Version Vectors approach. It seems like devices on TRUNK will only
  // need the first component of the version, while those on branches would need more...
  // NOTE2: the -1 and 1 values are useful for ordering of *local* versions,
  // i.e. locally 1002 < 1002/anydevice/xxx
  public int compareTo(Object v) {
    // 1000/foo/ 2000/bar/ n
    String[] ta = StringUtil.split(toString(),'/');
    String[] va = StringUtil.split(((Version) v).toString(),'/');
    for( int i=0;i<Math.min(ta.length,va.length); i++ ) {
      if( i%2 == 0 ) {
        // Number phase
        int nt = Integer.parseInt(ta[i]);
        int nv = Integer.parseInt(va[i]);
        if( nt > nv )
          return 2;
        else if( nv > nt )
          return -2;
      } else if( !ta[i].equals(va[i]) ) {
          int cmp = ta[i].compareTo(va[i]);
          return cmp > 0 ? 1 : (cmp < 0 ? -1 : 0);
      }
    }
    return ta.length > va.length ? 1 :
            (ta.length < va.length ? -1 : 0);
  }

  public boolean equals(Object o) {
    return o instanceof Version && compareTo((Version) o)==0;
  }

  public Version getAncestor() {
    return new Version(getBaseVersion(),getBaseBranch());
  }

  public boolean onTrunk() {
    return TRUNK.equals(getBranch());
  }

/*  public static void main(String[] args) {
    Version v = new Version(10,"1/rex/2/bo/");
    Log.log("v="+v,Log.INFO);
    // The proper results are
    // [Branch=1/rex/2/bo/, LocalVersion=-1, HereBranch=1/rex/2/bo/10/angmar/,
    // BaseVersion=2, LID=bo, BaseBranch=1/rex/, Number=10]
    Log.log("props="+fc.syxaw.proto.Util.toString(v),Log.INFO);
  }*/
  /*
  public static void main(String[] args) {
    Version v = new Version(2,"1/rex/");
    Log.log("Join test (should have only two vers):"+
            v.addToJoins("1/rex/2 1/zoo/9"),Log.INFO);
    Log.log("Join test (should have only two vers):"+
            v.addToJoins("1/rex/1 1/zoo/9"),Log.INFO);

  }*/

  public static final Version CURRENT_VERSION = new Version(Constants.CURRENT_VERSION,TRUNK);
  public static final Version NO_VERSION = new Version(Constants.NO_VERSION,TRUNK);
  public static final Version ZERO_VERSION = new Version(Constants.ZERO_VERSION,TRUNK);

  /*!5 @Override */
  public int hashCode() {
    return (branch != null ? branch.hashCode() : 0)^number; 
  }

}

// arch-tag: 2bb0796b-99a8-49fe-908b-59826dfa3535
