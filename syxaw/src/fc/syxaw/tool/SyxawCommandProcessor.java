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

// $Id: SyxawCommandProcessor.java,v 1.48 2005/06/08 13:03:28 ctl Exp $
package fc.syxaw.tool;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import fc.syxaw.api.ISyxawFile;
import fc.syxaw.api.Metadata;
import fc.syxaw.api.MetadataImpl;
import fc.syxaw.api.StatusCodes;
import fc.syxaw.api.SynchronizationException;
import fc.syxaw.fs.BLOBStorage;
import fc.syxaw.fs.Constants;
import fc.syxaw.fs.FileTransfer;
import fc.syxaw.fs.GUID;
import fc.syxaw.fs.SynchronizationEngine;
import fc.syxaw.fs.Syxaw;
import fc.syxaw.fs.VersionHistory;
import fc.syxaw.hierfs.SyxawFile;
import fc.syxaw.proto.Version;
import fc.syxaw.storage.hfsbase.ServerConfig;
import fc.syxaw.transport.PropertyDeserializer;
import fc.syxaw.transport.PropertySerializer;
import fc.syxaw.util.Util;
import fc.util.Debug;
import fc.util.StringUtil;
import fc.util.log.Log;

/** Command processor that implements the Syxaw command line interface.
 * The commands understood by the processor are the public instance methods
 * of this class (execpt <code>exec</code>, of course).
 * <p>File names passed to the commands are always absolute (in the
 * Syxaw namespace), since there is no notion of a working directory,
 *  and always use the
 * {@link fc.syxaw.api.ISyxawFile#separator Syxaw directory separator}, eg.
 * <code>ls /dir1/dir2</code>.
 *
 */

// Implementors not: to add a command, simply add a method with the
// corresponding name, taking  PrintWriter, String[] as args.
// The method is discovered and executed by introspection.

public class SyxawCommandProcessor extends CommandProcessor {

  private static Class[] CMD_ARGS = 
    new Class[] {PrintStream.class, String[].class};
  
  private static final boolean RELAX_VERIFY =
      (new Boolean(System.getProperty("syxaw.verify.relax",
                                      "false"))).booleanValue();

  private Map /*!5 <String,String> */ cdefs = new HashMap /*!5 <String,String> */();

  private static SyxawCommandProcessor instance = new SyxawCommandProcessor();

  /** Get global instance of command processor. */

  public static SyxawCommandProcessor getInstance() {
    return instance;
  }

  protected SyxawCommandProcessor() {
  }

  public void exec( String cmd, OutputStream out ) {
    String[] args = CliUtil.tokenize(cmd);
    PrintStream pw = new PrintStream(out);
//    Log.log("cmd="+cmd+"| array=",Log.INFO,args);
    if( args == null || args.length==0 || args[0].trim().length()==0 )
      return; // Nothing to do
    if( cdefs.containsKey(args[0]))
      args[0] = (String) cdefs.get(args[0]);
    String cmdName = args[0];
    Class c=getClass();
    try {
      if(args[0].indexOf('.') != -1 ) {
        int split = args[0].lastIndexOf('.');
        c=Class.forName( args[0].substring(0,split) );
        cmdName = args[0].substring(split+1);
        if( Util.isEmpty(cmdName) )
          cmdName="exec";
      }
      Method m=
        c.getMethod(cmdName, CMD_ARGS);
      m.invoke(this,new Object[] {pw,args});
    } catch (NoSuchMethodException x) {
      pw.println("Unknown command " + cmdName +" in class "+c.getName());
      Log.log("Unknown command " + cmdName +" in class "+c.getName(),
          Log.ERROR,x);
    } catch (Throwable t ) {
      pw.flush();
      try { out.write(EXCEPT_SIGNAL); } catch ( IOException ex) {
        Log.log("Couldn't write except signal to socket",Log.ERROR,ex);
      }
      Log.log("Command excepted",Log.ERROR,t);
      t.printStackTrace(pw);
    }
    pw.flush();
  }

  private static final String DF_NAME = "name ";
  private static final String DF_FLAGS = "flags ";
  private static final String DF_UID = "uid ";
  private static final String DF_LINK = "link ";

  /** List directory/file. When called for directories, the method lists the
   directory entries with metadata.
    When called for a file, metdata for the file is shown. */

  public void ls(PrintStream pw, String[] args) {
    doLs(pw,args,false);
  }

  /** Extended listing for directory/file. Same as {@link #ls ls}, but shows more
   * metadata per entry.
   */

  public void lls(PrintStream pw, String[] args) {
    doLs(pw,args,true);
  }

  protected void doLs(final PrintStream pw, String[] args, boolean detailed  ) {
    SyxawFile f = Syxaw.getFile(
          args.length < 2 ? ServerConfig.ROOT_FOLDER : args[1]);
    if( !f.exists() ) {
      notfound(args[1],pw);
      return;
    }
    SyxawFile[] files = null;
    if( f.isDirectory() && !detailed) {
      String[] content = f.list();
      files = new SyxawFile[content.length];
      for( int i=0;i<content.length;i++)
        files[i] = Syxaw.getFile(f,content[i]);
      pw.println("Content of "+f);
    } else
      files = new SyxawFile[] {f};
    String[] fields = null;
    if( detailed )
      fields = new String [] {
          DF_NAME,  DF_UID , "dataVersion", "metaVersion", "link", "linkDataVersion", "linkMetaVersion", "branch",
          DF_FLAGS, /*"format",*/"hash","length",
          /*"metadataModified",*/ "metaModTime",
          "modTime","readOnly", "type" };
    else
      fields = new String[] {
          DF_NAME,  DF_UID, "dataVersion", "metaVersion", DF_LINK, "linkDataVersion", "linkMetaVersion",
          DF_FLAGS,"branch" /*"format",*/ /*"hash",*/ /*"length",*/
          /*"metadataModified",*/ /*"metaModTime",*/
          /*"modTime","readOnly", "type"*/ };
    Formatter tf = null;
    if( detailed )
      tf = new IndentedListFormatter( fields );
    else
      tf = new TabularFormatter( fields );
    tf.setFieldEvaluator(new Formatter.FieldEvaluator() {
      final String[] flags = {"-","m","d","x"}; // nomod, metamod, datamod, both
      public String getValue(String field,Object o) {
        if( field == DF_NAME ) {
          SyxawFile f = (SyxawFile) o;
          return f.isDirectory() ? f.getName()+"/" : f.getName();
        } else if( field == DF_FLAGS ) {
          SyxawFile f = (SyxawFile) o;
          try {
            //BUG-EXPOSE-051208: f.isDataModified(true) triggers linkmetamod->true
            return flags[ (f.isDataModified(false) ? 2 : 0) +
                (f.isMetadataModified(false) ? 1 : 0)] +
                flags[ (f.isDataModified(true) ? 2 : 0) +
                (f.isMetadataModified(true) ? 1 : 0)];
          } catch (java.io.FileNotFoundException x ) {
            return "??";
          }
        } else if( field == DF_UID ) {
          SyxawFile f = (SyxawFile) o;
          return f.getUid().toBase64();
        } else if( field == DF_LINK ) {
          SyxawFile f = (SyxawFile) o;
          String l = f.getLink();
          if( Util.isEmpty(l) )
            l = "";
          return /*l.length() > 20 ? l.substring(0,17) + "..." :*/ l;
        }
        return null;
      }
    });
    for (int i = 0; i < files.length; i++) {
      try {
        tf.addLine(files[i],files[i].getFullMetadata());
      }
      catch (IOException x) {
        pw.println("Can't read metadata for " + files[i]);
      }
    }
    tf.print(pw);
  }

  /** Synchronize file/directory.
  * <p>Usage: <code>sync file</code>
  * <p>where<br>
  * <code>file</code> is the file/directory to synchronize.
  */

  public void sync(final PrintStream pw, String[] args) throws IOException,
    ParseException {
    Util.ObjectHolder conflictIsError = new Util.ObjectHolder(true);
    args=CliUtil.hasOpt(args,"conflictfails",conflictIsError);
    SyxawFile f = Syxaw.getFile(
          args.length < 2 ? ServerConfig.ROOT_FOLDER : args[1]);
    if (!f.exists()) {
      notfound(args[1],pw);
      return;
    }
    try {
      Thread monitor =new Thread() {
        public void run() {
          SynchronizationEngine e = SynchronizationEngine.getInstance();
          try {
            for (String msg = null; (msg = e.getSyncMsg())!=null; ) {
              pw.print(msg);
              pw.flush();
            }
          } catch (InterruptedException ex) {
            // done.!
          }
        }
      };
      monitor.start();
      f.sync(true, true); // all, not only metadata
      monitor.interrupt();
    } catch( SynchronizationException x ) {
      if( x.getStatus() == StatusCodes.CONFLICT
                          && !conflictIsError.booleanValue() )
        pw.println("Warning: conflicts during sync.");
      else {
        pw.println("Synchronization error: " + x.getMessage());
        throw x;
      }
    } catch (java.io.IOException x ) {
      pw.println("Sync failed: " + x.getMessage());
      //x.printStackTrace(pw);
      throw x;
    }
  }

  /** "Touch" file/directory. Similar to UNIX <code>touch</code>.
   * <p>Usage: <code>touch file [mMdDfF [cset-text]]</code>
   * <p>where<br>
   * <code>m</code> causes the metadata of the file to be marked modified<br>
   * <code>M</code> changes the metadata modification time to now. Implies <code>m</code><br>
   * <code>d</code> causes the data of the file to be marked modified<br>
   * <code>D</code> changes the file by appending a line of text. Implies <code>d</code><br>
   * <code>f</code> corresponds to <code>md</code><br>
   * <code>F</code> corresponds to <code>MD</code><br>
   * <code>cset-text</code> is a text to put into file append changes
   */

  public void touch(PrintStream pw, String[] args) {

    if (args.length < 2 || Util.isEmpty(args[1])) {
      pw.println("Usage touch lfile|ldir [mMdDfF] [changeset-id]]");
      return;
    }
    SyxawFile f = Syxaw.getFile(args[1]);
    /*if (!f.exists()) {
      notfound(args[1],pw);
      return;
    }*/
    if( f.isDirectory() ) {
      f.setLink(null, new Boolean(true),new Boolean(true), true, true);
      f.setLocal(null, new Boolean(true),new Boolean(true));
      return;
    }
    String opts = args.length < 3 ? "f" : args[2];
    String copts = opts.toLowerCase();
    if( copts.indexOf('d') != -1 || copts.indexOf('f') != -1) {
      try {
        OutputStream os = f.getOutputStream(true);
        if( opts.indexOf('D')!=-1 || opts.indexOf('F')!=-1) {
          String cid = args.length > 3 ? args[3] :
             "mod at " +(new java.util.Date(System.currentTimeMillis()));
          os.write((GUID.getCurrentLocation() + ": "+ cid  +
                    "\n").getBytes());
        }
        os.close();
      } catch (java.io.IOException x ) {
        pw.println("Cant append to "+f);
      }
    }
    if( copts.indexOf('f') != -1 || copts.indexOf('m') != -1 ) {
      try {
        MetadataImpl md = MetadataImpl.createFrom( f.getMetadata() );
        md.setMetaModTime( opts.indexOf('M')!=-1 ||
                           opts.indexOf('F')!=-1 ?
                           System.currentTimeMillis():
                           md.getMetaModTime());
        f.setMetadata(md);
      } catch (IOException x ) {
        pw.println("Can't mod metadata of "+f);
      }
    }
    //ls(pw,new String[] {null,getPath(f)});
  }

  public void touchf(PrintStream pw, String[] args) throws IOException,
          ParseException {
    if (args.length < 2 || Util.isEmpty(args[1])) {
      pw.println("Usage touchf file [data|recipe]");
      return;
    }
    SyxawFile f = Syxaw.getFile(args[1]);
    CliUtil.Recipe r = new CliUtil.Recipe(args.length > 2 ? args[2] : "0:0" );
    OutputStream os = f.getOutputStream(false);
    Util.copyStream(r.getStream(),os);
    os.close();
    pw.println("Wrote "+r.getLength()+" bytes.");
  }
  /** Dump object contents.
   * <p>Usage:  <code>cat file</code>
   */

  public void cat(PrintStream pw, String[] args) {

    if (args.length < 1 || Util.isEmpty(args[1])) {
      pw.println("Usage cat file");
      return;
    }
    SyxawFile f = Syxaw.getFile(args[1]);
    if( !f.exists() ) {
      notfound(getPath(f), pw);
      return;
    }
    try {
      BufferedReader br =
              new BufferedReader(
                      new InputStreamReader(
                      f.getInputStream()));
      for (String line = null; (line = br.readLine()) != null; ) {
        pw.println(line);
      }
    } catch (IOException ex) {
      Log.log("Script read error: ", Log.WARNING, ex);
    }
  }
  /** Download object.
   * <p>Usage:  <code>get name [v[v]]</code>
   * <p>where<br>
   * <code>name</code> is a Syaxaw {@link fc.syxaw.fs.GUID GUID} to download<br>
   * <code>v[v]</code> is the verbosity. <code>v</code> shows the first 10 lines of the
   * downloaded object. <code>vv</code> shows the entire object.
   */

  public void get(PrintStream pw, String[] args) throws IOException {
    if (args.length < 2 || Util.isEmpty(args[1])) {
      pw.println("Usage get name [v[v[v...]]]");
      return;
    }
   GUID dest = new GUID(args[1]);
   BLOBStorage storage = new BLOBStorage() {
     java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
     public OutputStream getOutputStream( boolean append ) {
       return os;
     }
     public InputStream getInputStream() {
       return new java.io.ByteArrayInputStream(os.toByteArray());

     }
     public void delete(){}

   };
   FileTransfer.DownloadRequestBase req = FileTransfer.getInstance().download(dest,
       storage, VersionHistory.EMPTY_HISTORY,false,null, true, true);
   pw.println   ("Status: "+Util.toString(req.getStatus()) );
   pw.println   ("Header: "+Util.toString(req.getTransferHeader(),2));
   if( req.getMetadataRef() != Constants.NO_VERSION )
     pw.println ("  Meta: ref="+req.getMetadataRef());
   else
     pw.println ("  Meta: "+Util.toString(req.getMetadata(),2));
   if( req.getDataRef() != Constants.NO_VERSION )
     pw.println ("  Data: ref="+req.getDataRef());
   else {
     try {
       java.io.BufferedReader br = new java.io.BufferedReader(
           new java.io.InputStreamReader(storage.getInputStream()));
       int maxlines = args.length > 2 ? Integer.MAX_VALUE : 10;
       for (String line; (line = br.readLine()) != null && maxlines > 0;
            maxlines--)
         pw.println(line);
     } catch (java.io.IOException x ) {
       pw.println("<<IOExcept on data dump>>");
     }
   }
  }

  /** Set link of object.
   * <p>Usage:  <code>ln name file</code>
   * <p>where<br>
   * <code>name</code> is a Syaxaw {@link fc.syxaw.fs.GUID GUID}<br>
   * <code>file</code> is the file, whose link field is set to <code>name</code>
   */

  public void ln(PrintStream pw, String[] args  ) throws ParseException {
    Util.ObjectHolder verbose = new Util.ObjectHolder(new Boolean(false));
    Util.ObjectHolder mkdir = new Util.ObjectHolder(new Boolean(false));
    Util.ObjectHolder lmod = new Util.ObjectHolder(null);
    args=CliUtil.hasOpt(args,"v",verbose);
    args=CliUtil.hasOpt(args,"dir",mkdir);
    args=CliUtil.hasOpt(args,"lmod",lmod);
    if( args.length <2 || Util.isEmpty(args[1])  ) {
      pw.println("Usage ln name [file]");
      pw.println("The Syxaw root is used if no file");
      return;
    }
    SyxawFile f = Syxaw.getFile(
          args.length < 3 ? ServerConfig.ROOT_FOLDER : args[2]);
/*    if (f.exists()) {
      pw.println("Already exists: " + args[2]);
      return;
    } */
    try {
      boolean exists =f.exists();
      if (!exists && (mkdir.booleanValue() ? !f.mkdir() : !f.createNewFile())) {
        pw.println("Can't create: " + f);
        return;
      }
      if( lmod.get() != null )
        exists = lmod.booleanValue();
      f.setLink(args[1],new Integer(Constants.NO_VERSION),
                new Boolean(exists),new Boolean(exists), true, true);
    }
    catch (java.io.IOException x) {
      pw.println("Can't create/set link for " + f);
    }
    if( ((Boolean) verbose.get()).booleanValue() )
      lls(pw,new String[] {null,getPath(f)});
   }

  /**
   * The copy operation. Works like the *nix copy. See the usage message for usage.
   * @param pw
   * @param args
   */
  
  public void cp(PrintStream pw, String[] args) {
      String usageMessage = "Usage: cp [OPTION]... [-T] SOURCE DEST\nor:  cp [OPTION]... SOURCE... DIRECTORY\nor:  cp [OPTION]... -t DIRECTORY SOURCE...\nCopy SOURCE to DEST, or multiple SOURCE(s) to DIRECTORY.\n\nMandatory arguments to long options are mandatory for short options too.\n\n"+
      /* "-a, --archive                same as -dpR\n"+
      "    --backup[=CONTROL]       make a backup of each existing destination file\n"+  
      "-b                           like --backup but does not accept an argument\n" +
      "    --copy-contents          copy contents of special files when recursive\n"+
      "-d                           same as --no-dereference --preserve=link\n"+ */
      "-f, --force                  if an existing destination file cannot be\n"+
      "                               opened, remove it and try again\n"+
      /*"-i, --interactive            prompt before overwrite\n"+
      "-H                           follow command-line symbolic links\n"+
      "-l, --link                   link files instead of copying\n"+
      "-L, --dereference            always follow symbolic links\n"+
      "-P, --no-dereference         never follow symbolic links\n"+
      "-p                           same as --preserve=mode,ownership,timestamps\n"+
      "    --preserve[=ATTR_LIST]   preserve the specified attributes (default:\n"+
      "                               mode,ownership,timestamps), if possible\n"+
      "                               additional attributes: links, all\n"+
      "-c                           same as --preserve=context\n"+
      "    --no-preserve=ATTR_LIST  don't preserve the specified attributes\n"+
      "    --parents                use full source file name under DIRECTORY\n"+*/
      "-r, --recursive              copy directories recursively\n"+
      /*"    --remove-destination     remove each existing destination file before\n"+
      "                               attempting to open it (contrast with --force)\n"+
      "    --sparse=WHEN            control creation of sparse files\n"+
      "    --strip-trailing-slashes remove any trailing slashes from each SOURCE\n"+
      "                               argument\n"+
      "-s, --symbolic-link          make symbolic links instead of copying\n"+
      "-S, --suffix=SUFFIX          override the usual backup suffix\n"+
      "-t, --target-directory=DIRECTORY  copy all SOURCE arguments into DIRECTORY\n"+
      "-T, --no-target-directory    treat DEST as a normal file\n"+*/
      "-u, --update                 copy only when the SOURCE file is newer\n"+
      "                               than the destination file or when the\n"+
      "                               destination file is missing\n"+
      "-v, --verbose                explain what is being done\n"+
      /*"-x, --one-file-system        stay on this file system\n"+
      "-Z, --context=CONTEXT        set security context of copy to CONTEXT\n"+*/
      "    --help                   display this help and exit\n"+
      "    --version                output version information and exit\n";
      int major = 0;
      int minor = 1;
      String copyVersion = major + "." + minor;
      
      boolean recurse = false, verbose = false, update = false, force = false, help = false, version = false;
      String[] supportedOpts = {"recursive", "r", "verbose", "v", "update", "u", "force", "f", "help", "", "version", ""};
      boolean[] opts = optsParse(args, supportedOpts);
      recurse = opts[0];
      verbose = opts[1];
      update  = opts[2];
      force   = opts[3];
      help    = opts[4];
      version = opts[5];
      // FIXME: debug
      StringBuffer optsbf = new StringBuffer();
      optsbf.append(recurse ? "r":"");
      optsbf.append(verbose ? "v":"");
      optsbf.append(update ? "u":"");
      optsbf.append(force ? "f":"");
      optsbf.append(help ? "\"--help\"":"");
      optsbf.append(version ? "\"--version\"":"");
       Log.log("Syxaw copy: args:" + Arrays.toString(args) + ", opts: " + optsbf , Log.INFO);
       System.out.flush();
      //FIXME: end debug
       
      if (version) {
          Log.log("Syxaw cp version " + copyVersion, Log.INFO);
          return;
      }
      if (help) {
          Log.log(usageMessage, Log.INFO);
          return;
      }
      String target = null;
      int propercount = 0; 
      for (int i = 1; i < args.length; ++i) {
          if (args[i] == null  || args[i].equals("")) {
              continue;
          }
          if (args[i].startsWith("-")) {
              Log.log("dash found in PARSED args!!", Log.ERROR);
              return;
          }
          propercount++;
      }
      if (propercount <= 0) {
          Log.log("No files to copy!", Log.ERROR);
          Log.log(usageMessage, Log.INFO);
          return;
      }
      String[] sourceNames = new String[propercount -1]; // last is target
      int counter = 0;
      for (int i = 1; i < args.length; ++i) {
          if (args[i] == null  || args[i].equals("")) {
              continue;
          }
          if (counter == propercount -1) {
              target = args[i]; 
          } else {
              sourceNames[counter] = args[i];
          }
          counter++;
          if (counter == propercount) {
              // no need to continue, all proper ones passed
              break;
          } 
      }
      SyxawFile tgt = null;
      if (target.equals(".")) {
        tgt =  Syxaw.getFile(fc.syxaw.storage.hfsbase.ServerConfig.ROOT_FOLDER); 
      }else {
        tgt = Syxaw.getFile(target);
      }
      if (sourceNames.length == 0) {
          Log.log("No files to copy!", Log.ERROR);
          Log.log(usageMessage, Log.INFO);
          return;
      }
      if (sourceNames.length > 1) {
          if (!tgt.isDirectory()) {
              Log.log("Asked to copy multiple files to a non-directory!", Log.ERROR);
              Log.log(usageMessage, Log.INFO);
              return;
          }
      }
      
      if (verbose) {
          String srcs = "";
          for (int i = 0; i< sourceNames.length; ++i) {
              srcs += sourceNames[i] + " ";
          }
          Log.log("Copying " + srcs + "to " + tgt, Log.INFO);
      }
      cp(recurse, verbose, update, force, sourceNames, tgt);
  }
  
  private void cp(boolean recurse, boolean verbose, boolean update, boolean force, String[] sources, SyxawFile target) {
      if (sources.length > 1) {
          if (!target.isDirectory()) {
              Log.log("Asked to copy multiple files to a non-directory!", Log.ERROR);
              return;
          }
      }
      
      for (int i = 0; i < sources.length; ++i) {
          SyxawFile f = Syxaw.getFile(sources[i]);
          cp (recurse, verbose, update, force, f, Syxaw.getFile(target, f.getName()));
      }
  }
  
  private void cp(boolean recurse, boolean verbose, boolean update, boolean force, SyxawFile f, SyxawFile target) {
      boolean needUpdate = false;
      if (update && !f.isDirectory()) {
          if (target.exists()) {
              // need to check if the source is newer than the target, and copy if so.
              try {
              long tmod = target.getMetadata().getModTime();
              long smod = f.getMetadata().getModTime();
              if (smod > tmod) {
                  needUpdate = true;
              }
              } catch (FileNotFoundException fnf) {
                  Log.log("Unable to read modtimes (and update requested)!", Log.ERROR, fnf);
              }
              
          }
      }
      
      if (target.exists()) {
          if (!force && ((update && !needUpdate) || !update)) {
              if (verbose) {
                  Log.log("Not overwriting " + target + " with " + f, Log.INFO);
              }
              return;
          }
      }
      
      if (verbose) {
          Log.log(f + " -> " + target, Log.INFO);
      }
      // first copy f
      if (f.isDirectory()) {
          if (!target.exists() && !target.mkdir()) {
              Log.log("Cannot create directory " + target+ "!", Log.ERROR);
              return;
          }
          if (recurse) {
              String[] list = f.list();
              for (int j = 0; j < list.length; ++j) {
                  SyxawFile g = Syxaw.getFile(f, list[j]);
                  SyxawFile tgt = Syxaw.getFile(target, list[j]);
                  // copy subfile
                  cp(recurse, verbose, update, force, g, tgt); 
              }
          }
      }else {
          // something else than dir, let's just copy it
          try {
              InputStream src = f.getInputStream();
              OutputStream tgt = target.getOutputStream(false);
              int read = src.read();
              while (read != -1) {
                  tgt.write(read);
                  read = src.read();
              }
              tgt.flush();
              tgt.close();
              src.close();
          } catch (FileNotFoundException fnf) {
              Log.log("Unable to copy!", Log.ERROR, fnf);
          } catch (IOException ioe) {
              Log.log("Unable to copy!", Log.ERROR, ioe);
          }
      }
  }
  /**
   * Parses options for an operation. Returns an array of booleans representing the status of the option flags. (Limited to on/off options)
   * @param args The parameter array to find the options from.
   * @param supportedOpts a String[] with pairs of long and short options, one after another. Example:<code>{"recursive", "r", "verbose", "v", "version", ""};</code>
   * @return a boolean[] with the values of the options in the order of <code>supportedOpts</code>. Example: <code>{true, false, false}</code> (with the above supported options would mean a recursive operation without verbose or version message.
   */
  private boolean[] optsParse(String[] args, String[] supportedOpts) {
      // long options, --opt
      String[] longs = new String[supportedOpts.length/2 +1];
      // short options, -o 
      char[] shorts = new char[supportedOpts.length/2 +1];
      // which of the supported were true, in the order of supported ones
      boolean[] result = new boolean[supportedOpts.length/2 +1];
      
      boolean odd = false;
      int counter = 0;
      for (int i = 0; i < supportedOpts.length; ++i) {
          if (odd) { 
              if (supportedOpts[i].length() == 1) { 
                  shorts[counter] = supportedOpts[i].charAt(0);
                  
              }
              if (supportedOpts[i].length() > 1) {
                  Log.log("Invalid short option: " + supportedOpts[i], Log.ERROR);
              }
              /* if length is less than 1, the short variant for that long option does not exist.
               the char is left to be a zero char. */
              counter++;
          } else { 
              longs[counter] = "--" + supportedOpts[i]; 
          }
          odd = !odd;
      }
      for (int i = 0; i < args.length; ++i) {
          if (args[i].startsWith("-")) {
              if (args[i].startsWith("--")) {
                  // long opt
                  boolean found = false;
                  for (int j = 0; j < longs.length; ++j) {
                      if (args[i].equals(longs[j])){
                          found = true;
                          result[j] = true;
                      }
                  }
                  if (!found) {
                      Log.log("Unsupported option: "+ args[i], Log.ERROR);
                  }
                  args[i] = "";
              }else {
                  // short opt(s), start from 1, 0 is dash
                  for (int k = 1; k < args[i].length(); ++k) {
                      char opt = args[i].charAt(k);
                      boolean found = false;
                      for (int j = 0; j < shorts.length; ++j) {
                          if (opt == shorts[j]){
                              found = true;
                              result[j] = true;
                          }
                      }
                      if (!found) {
                          Log.log("Unsupported option: -"+ opt, Log.ERROR);
                      }
                  }
                  args[i] = "";
              }
          }
          else {
              // no dash, no option.
              continue;
          }
      }
      // all parsed, return array of supported ones
      return result;
  }
  
  // Reply with name of host fs is running on. Sometimes useful..

  /** Ping.
   * <p>Usage:  <code>ping</code>
   * <p>Displays the host name of the computer the command processor
   * is running on.
   */

  public void ping(PrintStream pw, String[] args) {
    pw.println(GUID.getCurrentLocation());
  }

  /** Remove object. Works as UNIX <code>rm</code>.
   * <p>Usage:  <code>rm [-rf] target</code>
   * <p>where<br>
   * <code>target</code> is the file/directory to remove<br>
   * <code>-rf</code> recursively delete without confirmation<br>
   */

  public void rm(PrintStream pw, String[] args) throws IOException {
    if (args.length > 3 || (args.length == 3 && !"-rf".equals(args[1]))) {
      pw.println("Usage rm [-rf] target");
      return;
    }
    boolean recurse = false;
    if ("-rf".equals(args[1])) {
      recurse = true;
      args[1] = args[2];
    }
    SyxawFile f = Syxaw.getFile(args[1]);
    if (recurse && f.isDirectory()) {
      String[] list = f.list();
      for (int i = 0; i < list.length; i++)
        rm(pw, new String[] {null, "-rf",
           getPath(Syxaw.getFile(f, list[i]))});
    }
    if (!f.delete()) {
      throw new IOException("Cannot remove " + f);
    }
    else
      Log.log("Deleted " + f, Log.INFO);
  }

  /** View metdata for object.
   * <p>Usage:  <code>metaview file [field]</code>
   * <p>where<br>
   * <code>file</code> is the file/directory to show metadata for<br>
   * <code>field</code> is the metadata filed to show
   * (<code>type</code>, <code>format</code>, etc). If not given,
   * all fields and values are listed.
   */

  public void metaview(PrintStream pw, String[] args) {
    Log.log("",Log.ERROR);
  }

  /** Set and show metdata for object.
   * <p>Usage:  <code>meta [file [field=value [field2=value2] ...]</code>
   * <p>where<br>
   * <code>file</code> is the file/directory to set/show metadata for,
   * The syxaw root is used if left out<br>
   * <code>field=value [field=value] ...</code> is the metadata fields to
   * set along with the values, e.g.
   * <code>meta //file.xml type=xml link=/id/alchAA1234567890</code>.
   */

  public void meta(PrintStream pw, String[] args) {
    if ( args.length < 0) {
      pw.println("Usage meta file [field=value [[field=value] ...]]");
      pw.println("Sets metadata fields of a file ");
      return;
    }
    SyxawFile f = Syxaw.getFile(args.length > 1 ? args[1] :
                                ServerConfig.ROOT_FOLDER);
    try {
      MetadataImpl fm = MetadataImpl.createFrom( f.getMetadata() );
      Log.log("MD class is "+fm.getClass(),Log.INFO);
      Map p = new java.util.HashMap();
      PropertySerializer ps = new PropertySerializer(p);
      ps.writeObject(  fm  );
      if( args.length > 2 ) { // Avoid set if only view -> keep meta flags
        Boolean[] flags = null;
        int mdchanges = 0;
        for (int i = 2; i < args.length; i++) {
          String[] keyval = StringUtil.split(args[i],'=');
          if (keyval.length != 2)
            pw.println("Abort: invalid assignment " + args[i]);
          if( keyval[0].equals("flags")) {
            flags=new Boolean[4];
            String flagss = Util.format(keyval[1],2,'.').toLowerCase();
            for(int facet=0;facet<2;facet++) {
              char ch = flagss.charAt(facet);
              for(int datameta=0;datameta<2;datameta++) {
                flags[facet * 2 + datameta] =
                        ch == '.' ? null :
                        new Boolean(ch=='x' ||
                                (datameta == 0 && ch=='d') ||
                                (datameta == 1 && ch=='m'));
              }
            }
          } else {
            p.put(keyval[0], keyval[1]);
            mdchanges++;
          }
        }
        if( mdchanges > 0 ) {
          PropertyDeserializer ds = new PropertyDeserializer(p);
          ds.readObject( fm );
          f.setMetadata( fm );
        }
        if( flags != null ) {
          if (flags[0] != null || flags[1] != null)
            f.setLocal(null, flags[1], flags[0]); // 1,0 indexes NOT typo!
          if (flags[2] != null || flags[3] != null) {
            f.setLink(null, flags[3], flags[2], true, true); // 1,0 indexes NOT typo!
          }
        }
      }
      // Readback
      p.clear();
      ps = new PropertySerializer(p);
      ps.writeObject( MetadataImpl.createFrom( f.getMetadata() ) );
      pw.println("Meta: "+p);
    }
    catch (IOException x) {
      pw.println("Can't set properties." + f);
    }
  }

  /** Mount directory.
   * <p>Usage:  <code>mount name dir</code>
   * <p>where<br>
   * <code>name</code> is a Syaxaw {@link fc.syxaw.fs.GUID GUID}. <br>
   * <code>dir</code> is the directory mounted to <code>name</code>. The
   * directory must not already exist.
   */

  public void mount(PrintStream pw, String[] args) {
    if (args.length != 3) {
      pw.println("Usage mount name dir");
      return;
    }
    SyxawFile f = Syxaw.getFile(args[2]);
    try {
      // Be a little sloppy with target: allow raw guids as well
      GUID g = null;
      try {
        g = new GUID(args[1]);
      } catch(IllegalArgumentException x ) {
        Log.log("Invalid mount dest: "+g,Log.ERROR);
        return;
      }
      f.mount(g.toString());
    }
    catch (IOException x) {
      pw.println("mount failed");
    }
  }

  /** Show system usage statistics logs. Used for testing purposes.
   * <p>Usage:  <code>stat [-n#] log [field]</code>
   * <p>where<br>
   * <code>-n#</code> means show # newest entries, or <code>-nall</code> for all entries<br>
   * <code>log</code> name log to show, eg <code>sync</code> for the synchronization log.
   * The logs available in Syxaw are currently not documented.<br>
   * <code>field</code> field in log to show. All fields are shown if not given.
   */

  public void stat(PrintStream pw, String[] args) {
    int firstarg = 1, argc = args.length;
    int maxage = 1;
    if (argc < 2) {
      pw.println("Usage stat [-n#] log [field]");
      pw.println("# is number of entries (top line oldest), #=all = show all");
      return;
    }
    if( argc >= 2 && args[firstarg].startsWith("-n") ) {
      maxage = args[firstarg].equals("-nall") ? -1 :
          Integer.parseInt(args[firstarg].substring(2));
      firstarg++;
      argc--;
    }
    String log= args[firstarg];
    maxage = maxage == -1 ? fc.syxaw.util.Log.getStatSize(log) : maxage;
    if( argc == 2 ) {
      for( int i=(maxage-1);i>=0;i--)
        pw.println(fc.syxaw.util.Log.getStat(log,i));
    } else {
      for( int i=(maxage-1);i>=0;i--)
        pw.println(fc.syxaw.util.Log.getStat(log,args[firstarg + 1],i));
    }
  }

  /** Show value of system property.
   * <p>Usage:  <code>sp system_property</code>
   * <p>where<br>
   * <code>system_property</code> is the name of the Java system property
   * to show.*/

  public void sp(PrintStream pw, String[] args) {
    if (args.length < 2) {
      pw.println("Usage sp system_property");
      return;
    }
    pw.println(System.getProperty(args[1]));
  }

  /** Echo a string. Usually used to test if Syxaw is running.
   * <p>Usage:  <code>echo text</code>
   * <p>where<br>
   * <code>text</code> is the string to echo.
  */
  public void echo(PrintStream pw, String[] args) {
    for(int i=1;i<args.length;i++)
      pw.print(args[i]);
    pw.println();
  }

  /** Ouput a string to the system log.
   * <p>Usage:  <code>log text</code>
   * <p>where<br>
   * <code>text</code> is the string to log.
  */

  public void log(PrintStream pw, String[] args) {
    StringBuffer sb = new StringBuffer();
    for(int i=1;i<args.length;i++)
      sb.append(args[i]);
    Log.log(sb.toString(),Log.INFO);
  }

  /** Shut down Syxaw. Terminates Syxaw. Maybe not such
   * a good name for a command that could accidentally be typed when
   * the user wishes to close the CLI...
   * <p>Usage:  <code>quit</code>
  */

  public void quit(PrintStream pw, String[] args) {
    Log.log("Quit requested",Log.FATALERROR);
    System.exit(-1);
  }

  /** Shut down Syxaw. Immediately and in a quieter way, as opposed to quit.
   * <p>Usage:  <code>shutdown</code>
  */

  public void shutdown(PrintStream pw, String[] args) throws ParseException {
    Util.ObjectHolder<Boolean> delayed = 
      new Util.ObjectHolder<Boolean>(new Boolean(false));
    args=CliUtil.hasOpt(args,"d",delayed);    
    Log.log("Syxaw CLI shutdown",Log.INFO);
    if( delayed.get() ) {
      final int time = 2;
      pw.println("Delayed shutdown in "+time+"s");
      (new Thread() {

        @Override
        public void run() {
          Debug.sleep(time*1000);
          System.exit(0);
        }
        
      }).start();
    } else {
      System.exit(0);
    }
  }


  /** Query and modify conflict status.
   * <p>Usage: <code>conflict file [-c|-l|-r]</code>
   * <p>where<br>
   * <code>file</code> is the file to query/modify<br>
   * <code>-c</code> show conflicting data<br>
   * <code>-l</code> show conflict log<br>
   * <code>-r</code> mark conflict resolved<br>
   * <p>No option simply displays the conflict status of the file.
  */

  public void conflict(PrintStream pw, String[] args) {
     if (args.length > 3 || args.length < 2) {
       pw.println("Usage conflict file [-c|-l|-r]");
       pw.println("Prints conflict status of file, -c shows conflicting data, -l show log");
       pw.println("-r marks conflict resolved");
       return;
     }
     try {
       SyxawFile f = Syxaw.getFile(args[1]);
       int mode = 0;
       if (args.length == 3) {
         String opt = args[2];
         if ("-c".equals(opt))
           mode = 1;
         else if ("-l".equals(opt))
           mode = 2;
         else if ("-r".equals(opt))
           mode = 3;
         else {
           pw.println("Invalid option "+opt);
           return;
         }
       }

       if (mode == 0) {
         pw.println(f.hasConflicts() ? "File has conflicts" : "No conflicts");
       } else if ( !f.hasConflicts()) {
         pw.println("Error: file has no conflicts");
         return;
       }
       switch (mode) {
         case 0:
           break;
         case 1: // cfl data;
           {InputStream is = null;
           try {
             is = ((SyxawFile) f.getConflictingFile()).getInputStream();
           Util.copyStream(is,pw);
           } finally {
             is.close();
           }}
           break;
         case 2: // clog
           {InputStream is = null;
           try {
             is =
                 ((SyxawFile) f.getConflictLog()).getInputStream();
           Util.copyStream(is,pw);
           } finally {
             is.close();
           }
           }
           break;
         case 3:
           f.conflictsResolved();
               break;

       }
     } catch (IOException x) {
       pw.println("Can't find " + args[1] );
     }
   }

   /** Show directory modification flags.
    * <p>Usage: <code>treeflags root</code> */

   public void treeflags(PrintStream pw, String[] args) {
     if( args.length <2 || Util.isEmpty(args[1] )) {
       pw.println("Usage treeflags root");
       return;
     }
    SyxawFile r = Syxaw.getFile(args[1]);
    if( !r.exists() ) {
      notfound(args[1], pw);
      return;
    }
    pw.println("Flags of "+args[1]+" (true,induced)");
    try {
      treeflag(r, pw,0,true,false);
    } catch (IOException ex) {
      pw.println("--Flagging failed--");
    }
   }

   /** Calculate and set directory modification flags.
    * <p>Usage: <code>flagtree root</code> */

   public void flagtree(PrintStream pw, String[] args) {
     if( args.length <2 || Util.isEmpty(args[1] )) {
       pw.println("Usage flagtree root");
       return;
     }
    SyxawFile r = Syxaw.getFile(args[1]);
    if( !r.exists() ) {
      notfound(args[1], pw);
      return;
    }
    try {
      treeflag(r, pw,0,false,true);
    } catch (IOException ex) {
      pw.println("--Flagging failed--");
    }
   }

   /** Reset modflags of the local facet of the directory tree.
    */

   public void resetlocaltree(PrintStream pw, String[] args) {
     if( args.length <2 || Util.isEmpty(args[1] )) {
       pw.println("Usage resetlocaltree root");
       return;
     }
    SyxawFile r = Syxaw.getFile(args[1]);
    if( !r.exists() ) {
      notfound(args[1], pw);
      return;
    }
    try {
      resetlocalmodflags(r,pw,0);
    } catch (IOException ex) {
      pw.println("--Flagging failed--");
    }
   }


   protected void resetlocalmodflags(SyxawFile root, PrintStream pw,
                                int depth) throws IOException {
     ( (fc.syxaw.storage.hfsbase.AbstractSyxawFile) root).setLocal(null,
         new Boolean(false), new Boolean(false));
     if( root.isDirectory() ) {
       String[] files = root.list();
       for (int i = 0; i < files.length; i++) {
         SyxawFile f = root.newInstance(root, files[i]);
         resetlocalmodflags(f,pw,depth+1);
       }
     }
   }

   protected boolean[] treeflag(SyxawFile root, PrintStream pw,
                                int depth, boolean showAll,
                                boolean set) throws IOException {
     String[] files=root.list();
     boolean[] ismod= new boolean[] {false,false};
     for( int i=0;i<files.length;i++) {
       SyxawFile f=root.newInstance(root,files[i]);
       if( f.isDirectory() ) {
         boolean dirflags[] = treeflag(f,pw,depth+1,showAll,set);
         ismod[0]|=dirflags[0] | f.isDataModified(false);
         ismod[1]|=dirflags[1] | f.isDataModified(true);
       } else {
         if( !ismod[0] )
           ismod[0] |= f.isDataModified(false);
         if( !ismod[1] )
           ismod[1] |= f.isDataModified(true);
         if( showAll)
           pw.println(
               "                                ".substring(0,depth)+
               (f.isDataModified(false) ? "+" : ".")+
               (f.isDataModified(true) ? "X" : ".")+
               (ismod[0] ? "+" : ".")+(ismod[1] ? "X " : ". ")+f);

       }
     }
     boolean loct=false, linkt=false;
     boolean locm=root.isDataModified(false);
     boolean linkm=root.isDataModified(true);
     if( set && ismod[0] && !locm) {
       root.setLocal(null, new Boolean(true), new Boolean(true));
       loct=true;
     }
     if( set && ismod[1] && !linkm) {
       root.setLink(null,new Boolean(true), new Boolean(true), true, true);
       linkt=true;
     }
     if( showAll || loct || linkt)
       pw.println(
           "                                ".substring(0,depth)+
           (locm ? "+" : ".")+
           (linkm ? "X" : ".")+
           (ismod[0] ? "+" : ".")+(ismod[1] ? "X " : ". ")+root);
     return ismod;
   }

   /** Execute host OS executable.
     * <p>Usage: <code>sys progranm [arg0 [...]]</code>
     * <p>where<br>
     * <code>program</code> is a program name<br>
     * <code>arg0...</code> are the arguments of the program
     */
   public void sys(PrintStream pw, String[] args) {
     if( args.length <1 ) {
       pw.println("sys shell-cmd args");
       pw.println("Each args starting with // will be expaned to a local filename");
       return;
     }

     for( int i=2;i<args.length;i++) {
       if(args[i].startsWith("//") )
         args[i]=Syxaw.getFile(args[i]).toString();
     }
     String[] cmda = new String[args.length-1];
     System.arraycopy(args, 1, cmda, 0, args.length - 1);
     try {
       Process p = Runtime.getRuntime().exec(cmda);
       BufferedReader r = new BufferedReader(new InputStreamReader(p.
               getInputStream()));
       for (String l; (l = r.readLine()) != null; )
         pw.println(l);
     } catch (IOException ex) {
       pw.println("Can't exec " + Util.toString(args));
     }
   }

   /*public void chat(PrintStream pw, String[] args) {
     int b = (int) (Math.random()*10000.0);
     for( int i=0;i<10;i++) {
       pw.println("Chat "+b+"-"+i);
       try { Thread.sleep(1000); } catch ( InterruptedException ex) {}
     }
   }

   public void rpc(PrintStream pw, String[] args) {
     if( args.length <1 || Util.isEmpty(args[1] )) {
       pw.println("rpc dest");
       return;
     }
     pw.println(RPC.getInstance().call(args[1], new RPC.Message("Hello world!") ).m);
   }*/

  /** Find branches.
   * <p>Usage: <code>find [file]</code>
   * <p>where<br>
   * <code>file</code> is the file to find branches for, if left out,
   * the Syxaw root is used<br>
   * NOTE: Not fully implemented!
   */
   //FIXME-P: Not fully implemented!
   public void find(PrintStream pw, String[] args) {
     List branches =
          SynchronizationEngine.getInstance().findBranches(null);
     pw.println("Discovering branches...");
     pw.flush();
     for (Iterator i = branches.iterator(); i.hasNext(); ) {
        pw.println(i.next());
        pw.flush();
     }
   }

   /** Join a branch.
    * <p>Usage: <code>join [branch [file]]</code>
    * <p>where<br>
    * <code>branch</code> is the full version of a branch to join, e.g. 1/rex/2<br>
    * <code>file</code> the file that joins; if left out,
    * the Syxaw root is used<br>
    * Note: the file argument is not implemented.
    */

   // FIXME-P: Implement file argument
   public void join(PrintStream pw, String[] args) throws Exception {
     if( args.length <1 ) {
       pw.println("join [branch]");
       pw.println("The trunk is used if no branch is given.");
       return;
     }
     Version bv = new Version(args.length > 1 ? args[1] : Version.TRUNK );
     SynchronizationEngine.getInstance().switchBranch(
             Syxaw.getFile(ServerConfig.ROOT_FOLDER),bv);
   }

   protected void hist(PrintStream pw, String[] args, boolean link) {
     SyxawFile f = Syxaw.getFile(args.length>1 ?
                   args[1] : ServerConfig.ROOT_FOLDER);
     pw.println("History of "+f);
     String branch = args.length > 2 ? args[2] : null;
     List h = link ? f.getLinkVersionHistory(false).getFullVersions(branch) :
                          f.getVersionHistory().getFullVersions(branch);
     for( Iterator i = h.iterator();i.hasNext();) {
       Version v = (Version ) i.next();
       pw.println(v+" ("+v.getLocalVersion()+")");
     }
   }

   /** List link facet history of an object.
    */
   public void lhist(PrintStream pw, String[] args) {
     hist(pw,args,true);
   }

   /** List local facet history of an object.
    */

   public void hist(PrintStream pw, String[] args) {
     hist(pw,args,false);
   }

   /** Verify status of a file.
    * <p>Usage: <code>verify file localVer linkVer flags sha1-hash meta1=value1 ...</code>
    * <p>where<br>
   * <code>file</code> is the file to verify,<br>
   * <code>localVer</code> is the required local version number,<br>
   * <code>linkVer</code> is the required link version number,<br>
   * <code>flags</code> is the required modflags (see ls command),<br>
   * <code>sha1</code> is the required SHA-1 hash of the object content<br>
   * <code>meta1=value1 ...</code> is a list of required metadata field values<br>
   * In each position, a * means any value; in the flags field a . means
   * any value in that position (e.g. <code>.-</code>
   * = no link modifications, but local modifications Ok.<p>
   * If verification fails, an failed assertion is raised -> Syxaw stops. Use
   * <code>tverify</code> for a milder version.
   */
   public void verify(PrintStream pw, String[] args) throws ParseException {
     doverify(pw,args,true);
   }

   /** Verify status of a file. Same as {@link #verify}, but failure
    * will only print an error message, not terminate Syxaw.
    */

   public void tverify(PrintStream pw, String[] args) throws ParseException {
     doverify(pw,args,false);
   }

   public void mkdir(PrintStream pw, String[] args) throws IOException {
     SyxawFile f = Syxaw.getFile(args[1]);
     if( !f.mkdir() ) {
       pw.println("Can't mkdir " + args[1]);
       throw new IOException("Can't mkdir " + args[1]);
     }
   }

   public void mv(PrintStream pw, String[] args) throws IOException {
     SyxawFile src = Syxaw.getFile(args[1]);
     SyxawFile target = Syxaw.getFile(args[2]);
     target = target.isDirectory() && target.exists() ?
       Syxaw.getFile(target,src.getName()) : target;
     if( !src.renameTo( target ) ) {
       pw.println("Can't move " + src+"->"+target);
       throw new IOException("Can't move " + src+"->"+target);
     }
   }

   // Verify file ver lver flags sha [metafield=xxx,...]
   // * at any pos = let it be
   protected void doverify(PrintStream pw, String[] args, boolean critical)
     throws ParseException  {
     Util.ObjectHolder warnOnly = new Util.ObjectHolder(false);
     Util.ObjectHolder existTest = new Util.ObjectHolder(true);
     Util.ObjectHolder conflicts = new Util.ObjectHolder(false);
     Util.ObjectHolder conflictStream = new Util.ObjectHolder(false);
     args=CliUtil.hasOpt(args,"exist", existTest );
     args=CliUtil.hasOpt(args,"conflict", conflicts );
     args=CliUtil.hasOpt(args,"conflictstream", conflictStream );
     args=CliUtil.hasOpt(args,"warn", warnOnly );
     critical = critical & !warnOnly.booleanValue();
     try {
       final String[] FLAGS = {"-", "m", "d", "x"}; // nomod, metamod, datamod, both
       SyxawFile f = Syxaw.getFile(args.length > 1 ?
                                   args[1] : ServerConfig.ROOT_FOLDER);
       if( existTest.booleanValue()^f.exists() ) {
         verifyError(pw,
                     "File should " + (existTest.booleanValue() ? "" : "not ") +
                     "exist: " + f, critical);
         return;
       }
       // ver
       int IX = 2;
       if (args.length > IX && !"*".equals(args[IX])) {
         int ver = Integer.parseInt(args[IX]);
         int rver = f.getFullMetadata().getDataVersion();
         if (!RELAX_VERIFY && ver != rver )
           verifyError(pw, "Wrong version "+rver+", expected="+ver, critical);
         else if (RELAX_VERIFY && !(ver <= rver) )
           verifyError(pw, "Wrong relaxed version "+rver+", expected="+ver, critical);
       }
       // lver
       IX = 3;
       if (args.length > IX && !"*".equals(args[IX])) {
         int ver = Integer.parseInt(args[IX]);
         int rver = f.getLinkDataVersion();//FIXME-versions:
         if (!RELAX_VERIFY && ver != rver )
           verifyError(pw, "Wrong linkversion "+rver+", expected="+ver, critical);
         else if (RELAX_VERIFY && !(ver <= rver) )
           verifyError(pw, "Wrong relaxed linkversion "+rver+", expected="+ver, critical);
       }
       // vflags, note that we allow a regexp for flags!
       IX = 4;
       if (args.length > IX && !"*".equals(args[IX])) {
         String flags =
                 FLAGS[(f.isDataModified(false) ? 2 : 0) +
                 (f.isMetadataModified(false) ? 1 : 0)] +
                 FLAGS[(f.isDataModified(true) ? 2 : 0) +
                 (f.isMetadataModified(true) ? 1 : 0)];
         Log.log("No modflag check in fp. Flags are "+flags+
        	 ", should match "+args[IX],Log.WARNING );
         /*
         if (!RELAX_VERIFY && !flags.matches(args[IX]))
           verifyError(pw, "Wrong modflags " + flags + ", expected=" + args[IX],
                       critical);
         if( RELAX_VERIFY ) {
             args[IX]=args[IX].replace('d','D').replace('m','M');
             args[IX]=args[IX].replaceAll("D","(d|x)");
             args[IX]=args[IX].replaceAll("M","(m|x)");
             args[IX]=args[IX].replaceAll("-","(-|m|d|x)");
             if( !flags.matches(args[IX]) )
                 verifyError(pw, "Wrong relaxed modflags " + flags + ", expected=" + args[IX],
                       critical);
         }*/
       }
       // Conflict status
       // sha
       IX = 5;
       if( existTest.booleanValue() &&
           f.hasConflicts() != conflicts.booleanValue() ) {
         verifyError(pw,"File has "+(conflicts.booleanValue() ?
                     "no " : "")+"conflicts, expected opposite",critical);
       }
       if (args.length > IX && !"*".equals(args[IX])) {
         String target = null;
         if( conflictStream.booleanValue() && f.hasConflicts()) {
           target= doHash((SyxawFile) f.getConflictingFile());
         } else
           target= doHash(f);
         if( args[IX].indexOf(':') != -1 ) {
           CliUtil.Recipe r = new CliUtil.Recipe(args[IX]);
           byte[] hash = Util.copyAndDigestStream(r.getStream(),
                   new Util.Sink());
           if( !Util.getHexString(hash).equals(target) )
             verifyError(pw, "Wrong hash on recipie (got "+target+
                         ", expected="+Util.getHexString(hash)+")",critical);
         } else {
           if (!target.equals(args[IX]))
             verifyError(pw, "Wrong hash (got "+target+")",critical);
         }
       }
       // metas
       IX = 6;
       if (args.length > IX && !"*".equals(args[IX])) {
         MetadataImpl fm = MetadataImpl.createFrom( f.getMetadata() );
         Map facit = new java.util.HashMap();
         PropertySerializer ps = new PropertySerializer(facit);
         ps.writeObject( fm  );
         for (int i = IX; i < args.length; i++) {
           String[] keyval = StringUtil.split(args[i], '=');
           if( keyval.length == 1 ) {
             if( facit.get(keyval[0]) != null )
                     verifyError(pw, "Invalid meta on key " + keyval[0] +
                                 ", expected NULL", critical);
             else
               continue;
           }
           if (keyval.length != 2)
             pw.println("Abort: invalid condition " + args[i]);
           if (!Util.equals(facit.get(keyval[0]), keyval[1]))
             verifyError(pw, "Invalid meta on key " + keyval[0] + ", got val " +
                         facit.get(keyval[0]),critical);
         }
       }
       pw.println("--Ok!");
     } catch (Exception ex) {
       Log.log("Verify error exception is",Log.ERROR,ex);
       verifyError(pw,"Exception",critical);
     }
   }

   private void verifyError(PrintStream pw,String s, boolean critical) {
     pw.println(s);
     pw.flush();
     if( critical )
       throw new Error("Verification failed.");
       //Log.log("VEFRIFY ERROR: "+s,Log.ASSERT_FAILED);

   }

   /** Calculate SHA-1 hash of object contents. */
   public void hash(PrintStream pw, String[] args) throws IOException {
     SyxawFile f = Syxaw.getFile(args.length>1 ?
                   args[1] : ServerConfig.ROOT_FOLDER);

     pw.println("Hash of "+f);
     pw.println(doHash(f));
   }

   public void memstat(PrintStream pw, String[] args) {
     // Report local time
       fc.syxaw.util.Log.memstat(pw, args.length > 1 ? args[1] : null,
                 args.length > 2 ? args[2] : null);
   }
   
   public void cdef(PrintStream pw, String[] args) {
     if ( args.length < 0) {
       pw.println("Usage cdef cmd=fullname [cmd2=...]]");
       return;
     }
     // Update
     for (int i = 1; i < args.length; i++) {
       String[] keyval = StringUtil.split(args[i], '=');
       if( keyval.length!=2) {
         pw.println("Abort: invalid assignment " + args[i]);
         break;
       }
       cdefs.put(keyval[0], keyval[1]);
     }
   }
   
   public void alias(PrintStream pw, String[] args) {
     if ( args.length < 0) {
       pw.println("Usage alias [did=host[:port] [did2=...]]");
       pw.println("List or manipulate table of did aliases");
       return;
     }
     Map hosts = fc.syxaw.transport.Config.LID_HOST_MAP;
     Map ports = fc.syxaw.transport.Config.LID_PORT_MAP;
     // Update
     for (int i = 1; i < args.length; i++) {
       String[] keyhostport = StringUtil.split(args[i], '=');
       if( keyhostport.length!=2) {
         pw.println("Abort: invalid assignment " + args[i]);
         break;
       }
       String[] hostport = StringUtil.split(keyhostport[1],':');
       int port = fc.syxaw.transport.Config.REQUEST_PORT;
       if(hostport.length>1) {
         try {
           port = Integer.parseInt(hostport[1]);
         } catch ( NumberFormatException ex) {
           pw.println("Abort: invalid port" + hostport[1]);
           break;
         }
       }
       hosts.put(keyhostport[0],hostport[0]);
       ports.put(keyhostport[0],new Integer(port));
     }
     // Dump
     if( args.length == 1 ) {
       for( Iterator keyi = hosts.entrySet().iterator();keyi.hasNext();) {
         Map.Entry e = (Map.Entry) keyi.next();
         pw.println(""+e.getKey()+" -> "+e.getValue()+":"+ports.get(e.getKey()));
       }
     }
   }
   
   private String doHash( SyxawFile f) throws IOException {
     InputStream is = f.getInputStream();
     try {
       byte[] hash = Util.copyAndDigestStream(is, new Util.Sink());
       return Util.getHexString(hash);
     } finally {
       is.close();
     }
   }

   protected String getPath(SyxawFile f) {
     SyxawFile p = f.getParentFile();
     return (p == null ? "" :
          getPath(p) + ISyxawFile.separator) + f.getName();
   }


   protected void notfound(String file, PrintStream pw ) {
     pw.println("Not found: " + file);
     pw.println("Note: this CLI only sees files inside "+ServerConfig.ROOT_FOLDER );
   }
}
// arch-tag: 7477b9b1096610d41368c0dad5a5926c *-
