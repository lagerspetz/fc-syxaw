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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

import fc.syxaw.util.Util;
import fc.util.StringUtil;
import fc.util.log.Log;

public class SyxRunner {

  public static void main(String[] args) throws IOException {
    SyxawProcess.initLidMap();
    String batch = System.getProperty("syxrunner.scripts",null);
    if( batch != null ) {
        String halt = System.getProperty("syxrunner.shutdown");
        if (halt == null || halt.equals("true")) {
            System.exit( runScripts(batch) );
        }else {
            runScripts(batch);
        }
    } else
      new GUI();
  }

  public static int runScripts(String scriptLine ) throws IOException {
    int retcode=0;
    int total=0;
    String filters = System.getProperty("syxrunner.streams");
    List failed = new LinkedList();
    boolean failFast = Boolean.getBoolean("syxrunner.failfast");
    String[] scripts = StringUtil.splitWords(scriptLine);
    String dir = System.getProperty("syxrunner.scriptdir","");
    if( dir.length() > 0 && !dir.endsWith(File.separator))
      dir += File.separator;
    String logsFile = System.getProperty("syxrunner.logfile");
    OutputStream logsd = logsFile == null ? (OutputStream) System.err :
                        new FileOutputStream(logsFile);
    OutputStream clisd = (OutputStream) System.out;

    try {
      LabeledOutputStream logs = new LabeledOutputStream(logsd);
      fc.syxaw.util.Log.setOut(new PrintStream(logs.getLabeledStream("[  mlog] ")));
      LabeledOutputStream clis = new LabeledOutputStream(clisd);
      if( filters != null )
        clis.setFilters(StringUtil.split(filters,','));
      PrintWriter mw = new PrintWriter(clis.getLabeledStream("[ minfo] "),true);
      OutputStream mcli = clis.getLabeledStream("[master] ");
      PrintWriter mclip = new PrintWriter(mcli,true);
      SimpleContainer sc = new SimpleContainer(logs,clis);
      FrameworkCommandProcessor fcp = new FrameworkCommandProcessor(sc);
      mw.println("Running scripts "+Util.toString(scripts,1));
      out:
      for( int i=0;i<scripts.length;i++) {
        String fname = scripts[i];
        File fin = new File(fname);
        if(!fin.exists()) {
          fin = new File(dir,fname);
        }
        try {
          FileInputStream sf = new FileInputStream(fin);
          BufferedReader br = new BufferedReader( new InputStreamReader( sf));
          mw.println("Script "+fname);
          int lineno=1;
          total++;
          for(String line;(line=br.readLine())!=null;lineno++) {
            try {
              mclip.println(line);
              fcp.exec(line,mcli);
            } catch ( Throwable t ) {
              mw.println("Error: Script "+fname+" failed ");
              mw.println("Ending script...");
              retcode=1;
              failed.add(fname+" line "+lineno);
              if( failFast )
                break out;
              else
                break;
            }
          }
          // Cleanup if not last
          if( i<scripts.length-1) {
            //mw.println("Cleanup");
            try {
              fcp.exec("killall",mcli);
            } catch ( Throwable t ) {
              mw.println("Error: Cleanup failed ");
              mw.println("Ending test...");
              retcode=2;
              break out;
            }
            //mw.println("Verify no javas running...then hit enter");
            //try{ Thread.sleep(2000); } catch ( InterruptedException ex ) {};
          }
        } catch ( IOException ex ) {
          mw.println("Error: Can't read script "+fname);
          Log.log("Error reading "+fname,Log.ERROR,ex);
        }
      }
      mw.println("Run completed. Total "+total+", passed "+
                 (total-failed.size())+", failed "+
              failed.size());
      if(failed.size() > 0 )
        mw.println("Failed scripts: "+failed);
    } finally {
      if( logsFile != null )
        logsd.close();
    }
    return retcode;
  }
}

// arch-tag: e6872bbe-47c8-42c3-a3e6-d71f55013183
