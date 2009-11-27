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

package fc.pp.syxaw.util;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.OutputStream;

import fc.util.Util;
import fc.util.log.Log;
import fc.util.log.Logger;

// No javadoc for now
public class LogWindow extends Frame implements Logger {
  List messageList = new List();
  MenuBar menuBar1 = new MenuBar();
  Menu mnuFile = new Menu();
  MenuItem miExit = new MenuItem();

  public LogWindow() {
    try {
      jbInit();
    }
    catch(Exception e) {
      Log.log("GUI Init error",Log.FATALERROR,e);
    }
    setSize(Toolkit.getDefaultToolkit().getScreenSize());
    setVisible(true);
    Log.setLogger(this);
  }

  public void message(String s) {
    messageList.add(s);
  }

  private void jbInit() throws Exception {
    this.setMenuBar(menuBar1);
    mnuFile.setLabel("Syxaw");
    miExit.setActionCommand("Exit");
    miExit.setLabel("Exit");
    miExit.addActionListener(new LogWindow_miExit_actionAdapter(this));
    this.add(messageList, BorderLayout.CENTER);
    menuBar1.add(mnuFile);
    mnuFile.add(miExit);
  }

  void miExit_actionPerformed(ActionEvent e) {
    Log.log("GUI Exit",Log.FATALERROR);
  }

  class LogWindow_miExit_actionAdapter implements java.awt.event.ActionListener {
    LogWindow adaptee;

    LogWindow_miExit_actionAdapter(LogWindow adaptee) {
      this.adaptee = adaptee;
    }
    public void actionPerformed(ActionEvent e) {
      adaptee.miExit_actionPerformed(e);
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


// arch-tag: 6221d903-cdd8-43b5-b07e-4c600ccf155f
