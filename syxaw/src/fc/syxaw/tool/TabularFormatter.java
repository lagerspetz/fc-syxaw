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

// $Id: TabularFormatter.java,v 1.3 2003/10/15 11:06:25 ctl Exp $
package fc.syxaw.tool;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.LinkedList;

import fc.syxaw.util.Util;

/** Formatter that ouputs tabulated data. This formatter outputs its data
 * grid as a table, with each column in the data grid corresponding to
 * a column in the table, and each row in the grid corresponding to
 * a table row.
 * <p>Below is an example of the ouput produced by a <code>TableFormatter</code>
 * initialized to show the <code>name</code>, <code>guid</code>,
 * <code>version</code>, <code>link</code>, <code>linkVersion</code> and
 * <code>readOnly</code>
 * fields of <code>FullMetadata</code> objects for the directory entries
 * <code>dia</code> and <code>afile.txt</code>.<pre>
name       guid             version link linkVersion readOnly
dia        alchAAAVBgAAGA0A -1           -1          false
afile.txt  alchAOCCcb3PVBxl -1           -1          false
</pre>
*/

public class TabularFormatter extends Formatter {

  public TabularFormatter( Object o ) {
    super(o);
  }

  public TabularFormatter( String[] acols ) {
    super(acols);
  }

  public TabularFormatter( String[] acols, String[] anames ) {
    super(acols,anames);
  }

  public void print(PrintStream out) {
    lines.addFirst(cols);
    int[] widths=measureCols(lines);
    for( Iterator i=lines.iterator();i.hasNext();) {
      String[] line =  (String []) i.next();
      for (int ic = 0; ic < line.length; ic++)
        out.print(Util.format(line[ic],widths[ic]+1));
      out.println();
    }
  }

  private int[] measureCols(LinkedList lines) {
    int[] widths = new int[cols.length];
    for( Iterator i=lines.iterator();i.hasNext();) {
      String[] vals = (String[]) i.next();
      for( int ic=0;ic<vals.length;ic++) {
        String theval = vals[ic] == null ? "(null)" : vals[ic];
        widths[ic] = widths[ic] >= theval.length() ? widths[ic] :
            theval.length();
      }
    }
    return widths;
  }
}// arch-tag: 3c714266a647a723ab3d9393de7523b9 *-
