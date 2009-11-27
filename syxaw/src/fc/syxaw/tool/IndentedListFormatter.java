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

// $Id: IndentedListFormatter.java,v 1.4 2003/10/15 11:06:21 ctl Exp $
package fc.syxaw.tool;

import java.io.PrintStream;
import java.util.Iterator;

import fc.syxaw.util.Util;

/** Formatter that ouputs indented lists. This formatter outputs its data
 * grid as an indented list. The first columns are used as level 0 list
 * items, all other columns are level 1 list items. For instance, the
 * formatting of a {@link fc.syxaw.fs.FullMetadata} object could
 * look like this:<pre>
name: afile.txt
         guid: alchAHWSANbhiY1t
      version: -1
         link:
  linkVersion: -1
     modFlags: 0
       format: -1
         hash:
       length: 2
  metaModTime: 1066206537040
      modTime: 1066206538000
     readOnly: false
         type:
</pre>
 *
 */


public class IndentedListFormatter extends Formatter {

  public IndentedListFormatter( Object o ) {
    super(o);
  }

  public IndentedListFormatter( String[] acols ) {
    super(acols);
  }

  public IndentedListFormatter( String[] acols, String[] anames ) {
    super(acols,anames);
  }

  public void print(PrintStream out) {
    int width=measureTitles(cols);
    for( Iterator i=lines.iterator();i.hasNext();) {
      String[] line =  (String []) i.next();
      // Print heading:
      out.println(cols[0]+": "+line[0]);
      for (int ic = 1; ic < line.length; ic++)
        out.println(Util.format(cols[ic]+": ",-(width+4))+line[ic]);
      out.println();
    }
  }

  private int measureTitles(String[] names) {
    int width = 0;
    for( int i=0;i<names.length;i++)
      width = names[i].length() > width ? names[i].length() : width;
    return width;
  }
}
// arch-tag: d35ceb843df3c54304e6acb1861e56bc *-
