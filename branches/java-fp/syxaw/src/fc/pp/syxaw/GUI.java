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
import java.awt.Font;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import fc.pp.syxaw.util.TabPanel;
import fc.util.Util;
import fc.util.log.Log;
import fc.util.log.Logger;

// No javadoc for no
public class GUI extends Frame implements Logger {
    MenuBar menuBar1 = new MenuBar();
    Menu mnuFile = new Menu();
    MenuItem miExit = new MenuItem();
    TabPanel tabs = new TabPanel();

    CLIPanel tabCLI = new CLIPanel();
    LogPanel tabLog = new LogPanel();
    Panel tab3 = new Panel();
    Panel tab4 = new Panel();


    public GUI () {
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
      tabLog.message(s);
    }

    private void jbInit() throws Exception {
      setTitle("Syxaw");
      setFont( new Font( "Dialog", Font.PLAIN, 12 ));
      this.setMenuBar(menuBar1);
      mnuFile.setLabel("Syxaw");
      miExit.setActionCommand("Exit");
      miExit.setLabel("Exit");
      miExit.addActionListener(new LogWindow_miExit_actionAdapter(this));
      menuBar1.add(mnuFile);
      mnuFile.add(miExit);
      add(tabs,BorderLayout.CENTER);
      tabs.addPanel(tabCLI,"CLI");
      tabs.addPanel(tabLog,"Log");
//      tabs.addPanel(tab4,"tab4");
      setBackground( SystemColor.control );
      tabs.setBackground(SystemColor.control );
      tabCLI.setBackground( SystemColor.control);
      tab3.setBackground( SystemColor.blue);

//      tabs.addPanel(tab2,"tab3");
    }

    void miExit_actionPerformed(ActionEvent e) {
      Log.log("GUI Exit",Log.FATALERROR);
    }

    public InputStream getCLIInputStream() throws IOException {
      return tabCLI.getCLIInputStream();
    }

    public OutputStream getCLIOutputStream() throws IOException {
      return tabCLI.getCLIOutputStream();
    }

    class LogWindow_miExit_actionAdapter implements java.awt.event.ActionListener {
      GUI adaptee;

      LogWindow_miExit_actionAdapter(GUI adaptee) {
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

// arch-tag: 39491899-7450-47a1-9f11-69b2cbb80884
