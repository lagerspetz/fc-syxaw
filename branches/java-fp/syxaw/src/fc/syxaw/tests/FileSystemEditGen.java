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

// $Id: FileSystemEditGen.java,v 1.4 2003/10/13 13:30:48 ctl Exp $

package fc.syxaw.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import fc.syxaw.util.Util;
import fc.util.log.Log;

/** File system edit generator. Generates a random edit script that
 * permutates a given directory tree. Edits scripts are obtained
 * with the {@link #editFs} method.
 */


public class FileSystemEditGen {

  /** Max tree depth for directory inserts. */
  public static final int MAXDEPTH = 9;

  private Random rnd = null; //new Random(315l);
  private DirectorySync.Node rootDir = null;
  private Map dirOps = new HashMap();

  private ArrayList rndqueue = new ArrayList();
  private Map rndflags = new HashMap();
  private int rndix = 0;

  private int ins=100;

  /** Create edit generator.
   *
   * @param aRootDir Root of directory tree to permutate
   * @param seed random generator seed
   */
  public FileSystemEditGen( DirectorySync.Node aRootDir, long seed ) {
    rootDir = aRootDir;
    rnd =new Random(seed);
    DirectorySync.Node.initParents(rootDir);
  }

  /*DEPR
  public DirectorySync.Node getNode( String path ) {
    return DirectorySync.Node.getNode(rootDir,path);
  }*/

  private int depth( DirectorySync.Node n  ) {
    return n == rootDir ? 0 : 1+depth(n.parent());
  }

  /** Generate edit script.
   * <b>Note:</b> the method modifies the directory tree passed
   * to the constructor.
   * @param ops number of operations
   * @param opstring distribution of edit operations. The tokens of the
   * string are file system operations, as defined in
   * {@link DirectorySync.FileOp}. The pdf of operations is given by the
   * relative occurence of each operation token. E.g "ddm", gives a
   * delete operation with 66% probability and a move with 33% probability.
   * @return an edit script.
   */

  public DirectorySync.FileScript editFs(int ops, char[] opstring) {
    initPickList();
    DirectorySync.FileOp[] fops = new DirectorySync.FileOp[ops];
    for( int i=0;i<ops;i++) {
      char op = opstring[rnd.nextInt(opstring.length)];
      DirectorySync.FileOp fop=null;
      switch(op) {
        case 'i': {DirectorySync.TestDir d = (DirectorySync.TestDir) pickNode(F_DIR,F_DELETED,0,0);
          String name = uniqueName(d,"ins",new DirectorySync.TestFile("f"));
          DirectorySync.TestFile nf = new DirectorySync.TestFile(name);
          d.addChild(nf);
          newPickableNode(nf,F_MARK);
          fop = new DirectorySync.FileOp("i," + nf.path());
        }
          break;
        case 'I': { DirectorySync.TestDir d = null;
          for(int tries=10;
              tries > 0
              && (d=(DirectorySync.TestDir) pickNode(F_DIR,F_DELETED,0,0))!=null
              && depth(d) >= MAXDEPTH;
              tries--)
            if(tries==0)
              Log.log("Can't find shallow enough dir",Log.FATALERROR);
          String name = uniqueName(d,"ins",new DirectorySync.TestFile("d"));
          DirectorySync.TestDir nd = new DirectorySync.TestDir(name);
          d.addChild(nd);
          newPickableNode(nd,F_MARK|F_DIR);
          fop=new DirectorySync.FileOp("I,"+nd.path());}
          break;
        case 'd':
        case 'D': {DirectorySync.Node d=null;
          DirectorySync.Node dfirst=null;
          boolean ok=false;
          int nct=0;
          for( ;(d=pickNode(0,(op=='d'? F_DIR:0)+F_MARK,F_MARK,0)) !=
               dfirst; dfirst=dfirst==null ? d : dfirst ) {
            nct = traverse(d,OP_COUNTMARKED);
            ok = nct  == 1;
            if(ok) {
              int depth = depth(d);
              ok = op=='d' || depth > 3; // Only deltree on depths > 3
            }
            if( ok )
              break;
            else
              // Unmark
              rndflags.put(d,new Integer(
              (((Integer) rndflags.get(d)).intValue()&~F_MARK))) ;//Log.log("retry..",Log.INFO);
          }
          if( !ok ) {
            Log.log("Cannot find deleteable node, set="+rndflags.values(),Log.WARNING);
            i--; // Retry
            continue;
          }
          traverse(d,OP_DELETE);
          String name = d.path();
          ((DirectorySync.TestDir) d.parent()).removeChild(d);
          fop=new DirectorySync.FileOp("d,"+name);}
          break;
        case 'm': {DirectorySync.Node s=pickNode(0,F_MARK+F_ROOT,F_MARK,0);
          DirectorySync.TestDir d = (DirectorySync.TestDir) pickNode(F_DIR,F_MARK,0,0);
          if( s instanceof DirectorySync.TestDir ) {
            // Dir2dir move; make sure we're moving uptree
            if( d.path().startsWith(s.path()) ) {
              Log.log("Move downtree fixing s="+s.path()+", d="+d.path(),Log.INFO);
              DirectorySync.TestDir t= (DirectorySync.TestDir )s;
              s=d;d=t;
            }
          }
          String sp = s.path();
          String name = uniqueName(d,
                                   s.parent == d ? "ren" : null,
                                   s);
          if( s.parent() != d  ) {
            ( (DirectorySync.TestDir) s.parent()).removeChild(s);
            s.setName(name);
            d.addChild(s);
          } else {
            s.setName(name);
          }
          fop=new DirectorySync.FileOp("m,"+sp+","+s.path());}
          break;
        default:
          Log.log("Invalid op "+op,Log.FATALERROR);
      }
      fops[i]=fop;
    }
    return new DirectorySync.FileScript(fops);
  }


  private void newPickableNode( DirectorySync.Node n, int flags ) {
    rndqueue.add(rndqueue.size(),n);
    rndflags.put(n,new Integer(flags));
  }

  private DirectorySync.Node pickNode(int ones, int zeros, int set, int reset  ) {
    int staix = rndix;
    while((rndix=(rndix+1)%rndqueue.size())!=staix) {
      DirectorySync.Node n = (DirectorySync.Node) rndqueue.get(rndix);
      int flags = ((Integer) rndflags.get(n)).intValue();
      if( (flags&ones)==ones &&
          (flags&zeros)==0) {
        flags|=set;
        flags&=~reset;
        rndflags.put(n,new Integer(flags));
        return n;
      }
    }
    Log.log("Can't find suitable node [ones,zeros]="+Util.toString(new int[] {ones,zeros})+
            "flags="+rndflags.values(),
            Log.FATALERROR, new Exception());
    return null;
  }

  private static final int F_DIR=0x01;
  private static final int F_MARK=0x02;
  private static final int F_ROOT=0x04;
  private static final int F_DELETED=0x08;

  private int idcounter=0;

  private String uniqueName(DirectorySync.Node dir, String hint, DirectorySync.Node src ) {
    boolean found = false;
    boolean firsttry = true;
    String name=null;
    do {
      if( firsttry )
        name = src.getName();
      else
        name = src.getName().substring(0,Math.min(8,src.getName().length())) +
          (hint==null? "":"-"+hint) +
           "-"+Integer.toHexString(rnd.nextInt());
      firsttry=false;
      DirectorySync.Node[] ch=dir.getChildren();
      found = false;
      for( int i=0;!found && ch!=null && i<ch.length;i++) {
        found = ch[i].getName().equalsIgnoreCase(name);
      }
      idcounter++;
    } while( found );
    return name;
  }

  private void initPickList() {
    rndqueue = new ArrayList();
    rndflags = new HashMap();
    traverse(rootDir,OP_BUILDPICKLIST);
    Collections.shuffle(rndqueue,rnd);
    for(int i=0;i<rndqueue.size();i++) {
      DirectorySync.Node n = (DirectorySync.Node) rndqueue.get(i);
      rndflags.put(n,new Integer(n instanceof DirectorySync.TestDir ?
                                 F_DIR : 0));
    }
    rndflags.put(rootDir,new Integer(F_DIR+F_ROOT));
    //Log.log("rndflags="+rndflags.values(),Log.INFO);
  }

  private static final int OP_BUILDPICKLIST=0;
  private static final int OP_DELETE=1;

  private static final int OP_COUNTMARKED=2;

  protected int traverse( DirectorySync.Node r, int op ) {
    int retval=0;
    switch(op) {
      case OP_BUILDPICKLIST:
        rndqueue.add(r);
        break;
      case OP_DELETE:
        rndflags.put(r,new Integer(
            ((Integer) rndflags.get(r)).intValue() | (F_MARK + F_DELETED) ));
        break;
      case OP_COUNTMARKED:
        if ((((Integer) rndflags.get(r)).intValue() & F_MARK)==F_MARK)
          retval =1;
        break;
      default:
          Log.log("invalid op",Log.FATALERROR);
    }
    DirectorySync.Node[] ch=r.getChildren();
    for(int i=0;ch!=null && i<ch.length;i++)
      retval+=traverse(ch[i],op);
    return retval;
  }
}


// arch-tag: adc72e66fc57636cce533508d8f83de5 *-
