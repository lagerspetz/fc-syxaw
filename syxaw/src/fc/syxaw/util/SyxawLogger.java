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

package fc.syxaw.util;

import java.io.PrintStream;

import fc.util.log.StreamLogger;

public class SyxawLogger extends StreamLogger {

  public SyxawLogger(PrintStream out) {
    super(out);
  }
  
  public void log (Object message, int level, Object data) {
    if( data != null && !(data instanceof String) ) {
      data = Util.toString(data, 1);
    }
    super.log(message, level,data);
  }
}
// arch-tag: 5e8cc4ae-7c10-4df7-82b1-371728b768a9
