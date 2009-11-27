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

package fc.pp.syxaw;

import java.awt.BorderLayout;
import java.awt.List;
import java.awt.Panel;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import fc.util.Util;
import fc.util.log.Log;
import fc.util.log.Logger;

// No javadoc for now
public class LogPanel extends Panel implements Logger {

  public static final int BUF_SIZE=128;
  String[] logbuf = new String[BUF_SIZE];
  int pos=0,line=0;
  FileOutputStream lfile = null;
  boolean logToFile = false;
  
  /**
   * Default constructor. Does not write log messages to a file.
   *
   */
  public LogPanel() {
    this(false);
  }

  /**
   * Main constructor.
   * @param logToFile whether to write log messages to a file. 
   * If true, writes the log to a log file (syxaw.log).
   */
  public LogPanel(boolean logToFile){
	  this.logToFile = logToFile;
	  try {
		  jbInit();
	  } catch (Exception ex) {
		  ex.printStackTrace();
	  }
	  
	  if (this.logToFile){
		  try {
			  lfile = new FileOutputStream("syxaw.log");
		  } catch (FileNotFoundException ex) {
			  Log.log("Can't open log outfile",Log.ERROR,ex);
		  }	
	  }
  }
  
  private void jbInit() throws Exception {
    this.setLayout(borderLayout1);
    this.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        this_keyTyped(e);
      }
    }); list1.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        this_keyTyped(e);
      }
    });
    this.add(list1, java.awt.BorderLayout.CENTER);
  }

  List list1 = new List();
  BorderLayout borderLayout1 = new BorderLayout();

  public synchronized void message(String msg) {
    if(logToFile && lfile != null ) {
      try {
        lfile.write(msg.getBytes());
        lfile.flush();
        if( line % 5 == 0 ) {
          lfile.close();
          lfile = new FileOutputStream("syxaw"+pos+".log",true);
        }
      } catch (IOException ex) {
        msg += "[IOERR] "+msg;
      }
    }
    logbuf[pos] = msg;
    pos++;
    pos %= BUF_SIZE;
    line++;
  }

  public void this_keyTyped(KeyEvent e) {
    int items = -1;
    //Log.log("Ketyped!!!"+e.getKeyChar(),Log.INFO);
    if( e.getKeyChar()=='1' )
      items = BUF_SIZE >> 4;
    else if ( e.getKeyChar()=='2' )
      items = BUF_SIZE >> 2;
    else if ( e.getKeyChar()=='3' )
      items = BUF_SIZE;
    if( items != -1 ) {
      list1.removeAll();
      int start = pos;
      for( int i=start-items;i<start;i++) {
        String s = logbuf[(i+BUF_SIZE)%BUF_SIZE];
        if( s== null )
          continue;
        list1.add(s);
      }
    }
  }

  public boolean isEnabled(int arg0) {
      // TODO This is not too sophisticated.
      return true;
  }

  public void log(Object arg0, int arg1) {
      // TODO This is not too sophisticated.
      message(arg0.toString());
  }

  public void log(Object arg0, int arg1, Throwable arg2) {
      // TODO This is not too sophisticated.
      message(arg0.toString());
  }

  public void log(Object arg0, int arg1, Object arg2) {
      // TODO This is not too sophisticated.
      message(arg0.toString());        
  }
  
  
  public void debug(Object message) {
    log(message,Log.DEBUG);
  }
  
  public void debug(Object message, Throwable cause) {
    log(message,Log.DEBUG,cause);
  }
  
  public void debug(Object message, Object data) {
    log(message,Log.DEBUG,data);    
  }
  
  public void info(Object message) {
    log(message,Log.INFO);
  }
  
  public void info(Object message, Throwable cause) {
    log(message,Log.INFO,cause);
  }
  
  public void info(Object message, Object data) {
    log(message,Log.INFO,data); 
  }
  
  public void warning(Object message) {
    log(message,Log.WARNING);
  }
  
  public void warning(Object message, Throwable cause) {
    log(message,Log.WARNING,cause);
  }
  
  public void warning(Object message, Object data) {
    log(message,Log.WARNING,data);  
  }
  
  public void error(Object message) {
    log(message,Log.ERROR);
  }
  
  public void error(Object message, Throwable cause) {
    log(message,Log.ERROR,cause);
  }
  
  public void error(Object message, Object data) {
    log(message,Log.ERROR,data);    
  }
  
  public void fatal(Object message) {
    log(message,Log.FATALERROR);
  }
  
  public void fatal(Object message, Throwable cause) {
    log(message,Log.FATALERROR,cause);
  }
  
  public void fatal(Object message, Object data) {
    log(message,Log.FATALERROR,data);   
  }
  
  public OutputStream getLogStream(int level) {
    return Util.SINK;
  }
  
  
}

//arch-tag: 20050802110712ctl
