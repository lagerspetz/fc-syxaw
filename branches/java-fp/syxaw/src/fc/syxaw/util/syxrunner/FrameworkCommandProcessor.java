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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fc.syxaw.tool.CliUtil;
import fc.syxaw.tool.CommandProcessor;
import fc.syxaw.util.TcpRendezvous;
import fc.syxaw.util.Util;
import fc.util.IOUtil;
import fc.util.StringUtil;
import fc.util.log.Log;

import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpServer;
import org.mortbay.http.NCSARequestLog;
import org.mortbay.http.SocketListener;
import org.mortbay.http.handler.ProxyHandler;

public class FrameworkCommandProcessor extends CommandProcessor {

  public static final File groot = new File(
          System.getProperty("syxrunner.root","syxaw-roots"));

  public static int port = Integer.valueOf(
          System.getProperty("syxrunner.baseport","40000")).intValue();


  int rdzPort = 41100; // Base port for rendezvous clis
  boolean ignore = false;
  private ProcessContainer pc;
  private StringBuilder prefix = new StringBuilder();
//  private static FrameworkCommandProcessor instance = new
//          FrameworkCommandProcessor();

  public FrameworkCommandProcessor(ProcessContainer g) {
    this.pc = g;
    if( !groot.exists() && !groot.mkdir() )
      Log.log("Can't create "+groot,Log.FATALERROR);
  }

/*  public static FrameworkCommandProcessor getInstance() {
    return instance;
  }*/

  public void exec(String cmd, OutputStream out) {
    cmd = cmd.trim();
    if( cmd.endsWith("\\"))
        prefix.append(cmd.substring(0, cmd.length()-1));
    else {
      if( prefix.length() >  0) {
        cmd = prefix.toString() + cmd;
        prefix.setLength(0);
      }
      exec(null,cmd,out);
    }
  }
  
  public void exec(String[] args, String cmd, OutputStream out) {
    if(args==null)
      args = CliUtil.tokenize(cmd);
    PrintStream pw = new PrintStream(out);
    if (args == null || args.length == 0 || args[0].trim().length() == 0)
      return; // Nothing to do
    if( args[0].startsWith("#") ) {
      if( args[0].equals("#!--") )
        ignore = !ignore;
      return;
    }
    if( ignore ) {
      pw.println("#: " + cmd);
      return;
    }
    args=expandProps(args);
    if( args[0].startsWith(":") ) {
      String device = args[0].substring(1);
      StringBuffer cmdBuf = new StringBuffer(args.length>1 ? args[1] : "");
      for( int i=2;i<args.length;i++) {
        cmdBuf.append(' ');
        cmdBuf.append(args[i]);
      };
      pc.rexec( pw, device, cmdBuf.toString());
      return;
    }
    String cmdName = args[0];
    try {
      Method m = null;
      Class c = getClass();
      try {
        if(args[0].indexOf('.') != -1 ) {
          int split = args[0].lastIndexOf('.');
          c=Class.forName( args[0].substring(0,split) );
          cmdName = args[0].substring(split+1);
          if( Util.isEmpty(cmdName) )
            cmdName="exec";
        }
        m=c.getMethod(cmdName, new Class[] {PrintStream.class,
                                   String[].class});
      } catch(NoSuchMethodException x) {
        ; // Deliberately empty
      }
      if( m == null )
        m = c.getMethod("_"+cmdName, new Class[] {PrintStream.class,
                               String[].class});

      m.invoke(this, new Object[] {pw, args});
    } catch (NoSuchMethodException x) {
      pw.println("Unknown command " + cmdName);
    } catch (Throwable t) {
      pw.println("--Command excepted--");
      Log.log("Command excepted", Log.ERROR, t);
      t.printStackTrace(pw);
    }
    pw.flush();
  }

  /** Echo 'Pong'. Used for testing purposes. */
  public void ping(PrintStream pw, String[] args) {
    pw.println("Pong");
  }

  /** Clean syxaw image. Usage:<br>
   * <code>cleanimage name</code><br>
   * where<br>
   * <code>name</code> is the name of the image (buzz,rex,bo etc.).
   */

  public void cleanimage(PrintStream pw, String[] args) throws IOException {
    String subimage = args[1];
    if( Util.isEmpty(args[1].trim()) )
      pw.println("Empty image name");
    File f = new File( groot, args[1]);
    IOUtil.delTree(f,false );
    pw.println("Cleaned image "+args[1]+" (in "+f+")");
  }

  /** Sleep for a given time. Usage:<br>
   * <code>sleep s</code><br>
   * where<br>
   * <code>s</code> is the time to sleep in seconds; may contain decimals.
   */

  public void sleep(PrintStream pw, String[] args) {
    long msec = (long) (Double.parseDouble(args[1])*1000);
    try { Thread.sleep(msec); } catch (InterruptedException ex) {}
    pw.println("Slept "+msec+"ms");
  }

  /** Start a Syxaw instance. Usage:<br>
   * <code>create [--debug] root port</code><br>
   * where<br>
   * <code>--debug</code> starts the instance in a VM in JDPA server mode
   * <code>root</code> is the name of the instance
   * <code>port</code> is the port to listen for HTTP requests.
   */

  public void create(PrintStream pw, String[] args) throws Exception {
    Util.ObjectHolder debugHolder = new Util.ObjectHolder(new Boolean(false));
    Util.ObjectHolder cleanHolder = new Util.ObjectHolder(new Boolean(false));
    args = CliUtil.hasOpt(args,"debug",debugHolder);
    args = CliUtil.hasOpt(args,"clean",cleanHolder);
    if(((Boolean) cleanHolder.get()).booleanValue()) {
      cleanimage(pw,new String[] {"",args[1]});
    }
    boolean debug = ((Boolean) debugHolder.get()).booleanValue();
    String name=args[1];
    File root=groot;
    if( args.length > 2 )
      root=new File(args[2]);
    int port = -1;
    if( args.length > 3 )
      port = Integer.parseInt(args[3]);
    else
      port = fc.syxaw.transport.Config.LID_PORT_MAP.containsKey(name) ?
             ((Integer) fc.syxaw.transport.Config.LID_PORT_MAP.get(name)).
             intValue() :
             -1;
    SyxawProcess p = new SyxawProcess("localhost",port,name,root);
    //ZSyxawPanel sp = new SyxawPanel(p);
    pc.addInstance(p);
    p.setDebug(debug);
    p.start();
    pw.println("Created Syxaw "+name+" (at "+groot+", port "+port+")");
  }

  public void rdzcli(PrintStream pw, String[] args) {
    String name=args[1];
    //String[] hostPort = Util.split(args[2],':');
    int port = Integer.parseInt(args[2]);
    TcpRendezvous.start(port,rdzPort);
    SyxawProcess p = new SyxawProcess( "localhost"  ,rdzPort,name);
    p.setCLIPort(rdzPort);
    //ZSyxawPanel sp = new SyxawPanel(p);
    //sp.setMarkerWait(false);
    pc.addInstance(p);
    pw.println("Opened Rdz CLI at port "+port);
  }

  private long lastTime = -1l;
  public void time(PrintStream pw, String[] args) {
    long now = System.currentTimeMillis();
    pw.println("Now is " + new Date(now) );
    if( lastTime > 0l ) {
      long age = now - lastTime;
      pw.println("Last 'time' " + (age / 1000) + "." +
                 Util.format("" + age % 1000, -3, '0')+" sec ago");
    }
    lastTime = now;
  }

  public void proxy(PrintStream pw, String[] args) {
    int port = Integer.parseInt(args[1]);

    // Create the server
    HttpServer server = new HttpServer();

    // Create a port listener
    SocketListener listener = new SocketListener();
    listener.setPort(port);
    listener.setMaxIdleTimeMs(100000);
    server.addListener(listener);

    // Create a context
    HttpContext context = new HttpContext();
    context.setContextPath("");
    server.addContext(context);

    // Create a servlet container
    ProxyHandler proxy = new ProxyHandler();
    context.addHandler(proxy);
    context.setRequestLog(new NCSARequestLog());
    // Start the http server
    try {
      server.start();
      pw.println("Started HTTP Proxy at "+port);
    } catch (Exception x) {
      Log.log("HTTP server init failed", Log.FATALERROR, x);
      pw.println("HTTP server init failed."+port);
    }
  }



  public void breakcliwait(PrintStream pw, String[] args) {
    pc.breakCommandWait(pw,args[1]);
  }

  public void createn95(PrintStream pw, String[] args) throws Exception {
    Util.ObjectHolder debugHolder = new Util.ObjectHolder(new Boolean(false));
    args = CliUtil.hasOpt(args,"debug",debugHolder);
    boolean debug = ((Boolean) debugHolder.get()).booleanValue();
    String name=args[1];
    File root=groot;
    if( args.length > 2)
      root=new File(args[2]);
    int port = Integer.parseInt(args[3]);
    TcpRendezvous.start(port,rdzPort);
    SyxawProcess p = new SyxawN95Process("localhost",port,name,root);
    p.setCLIPort(rdzPort);
    //ZSyxawPanel sp = new SyxawPanel(p);
    //sp.remove(sp.consolePane);
    pc.addInstance(p);
    p.setDebug(debug);
    p.start();
    pw.println("Created Syxaw "+name+" (at "+groot+", port "+port+")");
  }

  /** Echo a string. Usage:<br>
   * <code>echo string</code>.
   */
  public void echo(PrintStream pw, String[] args) {
    for( int i=1;i<args.length;i++)
      pw.print(" "+args[i]);
    pw.println();
  }

  /** Set instance property prior to creation. Usage:<br>
   * <code>createprop prop1[=val1] ...</code><br>
   * Sets system properties for the next instance that is created. A property
   * name without a value removes that property from the list.
   */

  public void createprop(PrintStream pw, String[] args) {
    for (int i = 1; i < args.length; i++) {
      String[] keyval = StringUtil.split(args[i], '=');
      String key = keyval[0].startsWith("-D") ?
                   keyval[0].substring(2) : keyval[0];
      if (keyval.length < 2) {
        SyxawProcess.NEW_PROCESS_PROPS.remove(key);
      } else
        SyxawProcess.NEW_PROCESS_PROPS.put(key,keyval[1]);
    }
    pw.println("Process custom params are "+SyxawProcess.NEW_PROCESS_PROPS);
  }

  public void title(PrintStream pw, String[] args) {
    pc.setTitle(args[1]);
  }

  public void kill(PrintStream pw, String[] args) throws Exception {
    pc.killInstance(args[1]);
  }

  public void killall(PrintStream pw, String[] args) throws Exception {
    for( Iterator i = pc.getInstanceNames().iterator();i.hasNext();) {
      pc.killInstance((String) i.next());
    }
    SyxawProcess.NEW_PROCESS_PROPS.clear(); // For sloppy scripts!
  }
  /** Execute host OS executable.
   * <p>Usage: <code>sys program [arg0 [...]]</code>
   * <p>where<br>
   * <code>program</code> is a program name<br>
   * <code>arg0...</code> are the arguments of the program<br>
   * The working directory of the command will be <code>syxrunner.root</code>
   */

  public void sys(final PrintStream pw, String[] args) {
   if( args.length < 2 ) {
     pw.println("sys shell-cmd args");
     return;
   }

   String[] cmda = new String[args.length-1];
   System.arraycopy(args, 1, cmda, 0, args.length - 1);
   try {
     // KLUDGE: If the command has a path character, and is relative,
     // resolve it with respect to groot
     // The kludge won't work if commands in syxaw-root are ref'd w/o ./  
     if( cmda[0].indexOf(File.separatorChar)!= -1 ) {
       File f = new File(cmda[0]);
       if( !f.isAbsolute() )
         cmda[0] =new File(groot,f.getPath()).getAbsolutePath();
     }
     //pw.println("cmd="+Util.toString(cmda));
     final Process p = Runtime.getRuntime().exec(cmda,null,groot);
     BufferedReader r = new BufferedReader(new InputStreamReader(p.
             getInputStream()));
     /*(new Thread() {
       public void run() {
         try {
           BufferedReader r = new BufferedReader(new InputStreamReader(p.
             getErrorStream()));
           for (String l; (l = r.readLine()) != null; )
             pw.println(l);
         } catch( IOException e ) {
           // Deliberately empty
         }
       }
     }).start();*/
     for (String l; (l = r.readLine()) != null; )
       pw.println(l);
     try { 
       p.waitFor();
     } catch( InterruptedException ex) {
       Log.info("External process interrupted");
     }
   } catch (IOException ex) {
     Log.setLogger(new fc.util.log.SysoutLogger());
     Log.error("Can't exec " + Util.toString(args),ex);
     pw.println("Can't exec " + Util.toString(args));
   }
 }

  public void include(PrintStream pw, String[] args) {
    if( args.length <1 ) {
      pw.println("include scriptfile");
      return;
    }
    InputStream in = null;
    try {
      File inf = new File(args[1]);
      if (!inf.exists()) {
        inf = new File(System.getProperty("syxrunner.scriptdir","."), args[1]);
      }
      in = new FileInputStream(inf);
      BufferedReader r = new BufferedReader(new InputStreamReader(in));
      pw.println("Running included file "+args[1]);
      for (String l; (l = r.readLine()) != null; )
        exec(l,pw);
      pw.println("--End of include--");
    } catch( Exception e ) {
      pw.println("Include error: "+e);
      Log.error("Exception",e);
    } finally {
      try {
        if( in != null )
          in.close();
      } catch( IOException e ) {
        Log.error("Can't close file",e);
      }
    } 
  }

  public void keywait(PrintStream pw, String[] args) {
    try {
      pw.println("--Hit enter--");
      System.in.read();
    } catch ( IOException ex) {
      Log.error("I/O error",ex);
    }
  }

  public void _if(PrintStream pw, String[] args) {
    doCond(pw, args, false);
  }

  public void unless(PrintStream pw, String[] args) {
    doCond(pw, args, true);
  }
  
  public void copy(PrintStream pw, String[] args) throws IOException, ParseException {
    Util.ObjectHolder recurseHolder = new Util.ObjectHolder(new Boolean(false));
    args=CliUtil.hasOpt(args, "r", recurseHolder);
    if( args.length != 3 ) {
      pw.println("Usage: copy [-r] src dest");
      return;
    } 
    doCopy(getSafeFile(args[1]), getSafeFile(args[2]), 
        recurseHolder.booleanValue());
  }

  private void doCopy( File sf, File tf, boolean recurse ) throws IOException {
    boolean isDir = sf.isDirectory();
    if( isDir )
      tf.mkdir();
    else if( !sf.getName().startsWith(".syxaw") ){
      InputStream in = null;
      OutputStream out = null;
      try {
        in = new FileInputStream(sf);
        out = new FileOutputStream(tf);
        Util.copyStream(in, out);
      } finally {
        IOUtil.closeStream(in);
        IOUtil.closeStream(out);
      }
    }
    if( isDir && recurse ) {
      File[] sfs = sf.listFiles();
      for( int i=0; i< sfs.length; i++ ) {
	File nsf = sfs[i];
        doCopy(nsf,new File(tf,nsf.getName()),recurse);
      }
    }
  }
  
  private OutputStream getFileOStream(String file) throws IOException {
    File f = getSafeFile(file);
    return new FileOutputStream(f);
  }

  private File getSafeFile(String file) {
    File f = new File(groot,file);
    return f;
  }

  private InputStream getFileIStream(String file) throws IOException {
    File f = new File(groot,file);
    return new FileInputStream(f);
  }

  private void doCond(PrintStream pw, String[] args, boolean negate) {
    if( args.length < 2 ) {
      pw.println("if test cmd arg0 ...");
      return;
    }
    if( !Util.isEmpty(args[1])^negate) {
      String[] args2=new String[args.length-2];
      System.arraycopy(args, 2, args2, 0, args.length-2);
      exec(args2,"?",pw);
    }
  }
  
  // Understands args @@propname or anytext@@{propname}moretext
  // @@@name expands to @@name
  private static String[] expandProps(String[] a) { 
    for(int i=0;i<a.length;i++) {
      int pos = -1;
      // BUGFIX-20061218-1: no multiple expansion
      while((pos=a[i].indexOf("@@{"))!=-1 || a[i].startsWith("@@") ) {
        int end = pos == -1 ? a[i].length() : a[i].indexOf('}',pos+3);
        int findPos = pos;
        int start = pos = pos==-1 ? 2 : pos+3 ;
        String key = a[i].substring(start,end);
        String val = key.startsWith("@") ? key.substring(1) : 
          ((String) SyxawProcess.NEW_PROCESS_PROPS.get(key));
        if(val==null) 
          val=System.getProperty(key,"");
        if( val != null) {
          String[] vala = CliUtil.tokenize(val);
          if( vala.length == 0) {
            a[i]="";
            continue;
          }
          String startS = (findPos<1 ? "" : a[i].substring(0,findPos))+vala[0];
          String endS = (end == a[i].length() ? "" : a[i].substring(end+1));
          a[i] = startS;
          if( vala.length > 1) {
            String[] na = new String[a.length+vala.length-1];
            System.arraycopy(a, 0, na, 0, i+1);
            System.arraycopy(a, i+1, na, i+vala.length, a.length-(i+1));
            a=na;
            int vpos=1;
            for(int imax = i+vala.length-1 ;i<imax;i++)
              a[i+1]=vala[vpos++];            
          }
          // BUGFIX-20061218-2: prefix was stripped on multiple expansion 
          a[i]+=endS;
        }
      }
    }
    return a;
  }

  /*
  public static void main(String args[]) {
    SyxawProcess.NEW_PROCESS_PROPS.put("quux", "foo");
    SyxawProcess.NEW_PROCESS_PROPS.put("zonk", "zonk");
    String[] exp = expandProps(new String[] {"@@{quux}bar"});
    System.out.println("Expanded="+exp[0]);
    SyxawProcess.NEW_PROCESS_PROPS.put("SyncTest", "fc.syxaw");
    exp = expandProps(new String[] {"","@@{zonk}@@{quux}"});
    System.out.println("Expanded0="+exp[0]+", expanded1="+exp[1]);
  }*/
}

// arch-tag: 7d8a9dbf-0c1a-4169-8d61-8f533b321aab
