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

// $Id: CommandProcessor.java,v 1.4 2003/10/15 11:06:16 ctl Exp $
package fc.syxaw.tool;

import java.io.OutputStream;

/** Base class for CLI command processors. */

public abstract class CommandProcessor {

  /** Execute command.
   * @param cmd command string to execute
   * @param out stream receiving output of command
   */
  public abstract void exec( String cmd, OutputStream out );

  /** Execute command. Send output to <code>System.out</code>.
   * @param cmd command string to execute
   */

  public void exec( String cmd ) {
    exec( cmd, System.out );
  }


  public static final byte[]  EXCEPT_SIGNAL="[[ERROR]]".getBytes();

}// arch-tag: 052190775904d9107f1d49388a51dd0c *-
