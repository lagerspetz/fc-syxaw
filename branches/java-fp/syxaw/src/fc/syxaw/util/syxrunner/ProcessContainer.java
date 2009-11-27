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

import java.io.PrintStream;
import java.util.Collection;

public interface ProcessContainer {

  public void rexec( PrintStream pw, String device, String cmd );
  public void addInstance( SyxawProcess sp );
  public void killInstance( String name );
  public void breakCommandWait(PrintStream pw, String device );
  public void setTitle(String title);
  public Collection getInstanceNames();
}

// arch-tag: 97dfbcbb-e7fc-45a8-8b24-538cc3a31f12
