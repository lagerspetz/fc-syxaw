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

// $Id: Formatter.java,v 1.4 2003/10/15 11:06:18 ctl Exp $
package fc.syxaw.tool;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.LinkedList;

import fc.syxaw.util.Util;

/** Base class for pretty-printing a list of objects.
 * The data to format is thought of as a grid, where each row corresponds to an
 * object, and each column to a field of that object. To use the object:<ol>
 * <li>Construct the object with the desired list of columns to show in the output</li>
 * <li>Add rows (i.e. objects) with the {@link #addLine(Object) addLine} methods.</li>
 * <li>Generate formatted output by calling the {@link #print(OutputStream) print}
 * method</li>
 * </ol>
 */

public abstract class Formatter {
  protected String[] cols;
  protected String[] names;
  protected LinkedList lines = new LinkedList(); // list of String[]  corrsponding to cols
  protected FieldEvaluator evaluator = null;

  /** Create formatter for a Java Bean. The columns shown are the display
   * names of <code>o</code> obtained by Java beans introspection.
   * @param o object to create formatter for
   */

  public Formatter( Object o ) {
    names = new String[] {"object"};
  }

  /** Create a formatter using given columns.
   * @param acols List of columns to show. The column names are passed
   * to the {@link Formatter.FieldEvaluator FieldEvaluator}. Use the
   * Java beans display names with the default FieldEvaluator.
   */

  public Formatter( String[] acols ) {
    cols = acols;
    names = new String[cols.length];
  }

  /** Create a formatter using given columns and field names.
   * @param acols List of columns headings to show. Must be of the same
   * length as <code>anames</code>.
   * @param anames List of columns names show. The column names are passed
   * to the {@link Formatter.FieldEvaluator FieldEvaluator}. Use the
   * Java beans display names with the default FieldEvaluator.
   */

  public Formatter( String[] acols, String[] anames ) {
    if( acols.length != anames.length )
      throw new IllegalArgumentException();
    cols = acols;
    names = anames;
  }

  /** Set field evaluator for formatting. The field evaluator is called
   * to obtain the string representation for each column of each row.
   * <p>If no field evaluator is
   * set, Java beans introspection of the row objects is used to
   * obtain the field values. The default field evaluator expects
   * the column names to conatin Java beans display names.
   */

  public void setFieldEvaluator(FieldEvaluator af) {
    evaluator = af;
  }

  /** Add a row of pre-calculated column values. Adds a row of
   * pre-calculated string values. The first string in the array
   * is displayed in column 1, the second in column 2 etc.
   * @param vals array of String values.
   */

  public void addLine(String[] vals) {
    lines.add( vals);
  }

  /** Add a row. The column values are obtained by applying the
   * current field evaluator to the object.
   */

  public void addLine(Object o ) {
    lines.add(getValues(o,null));
  }

  /** Add a row.  The column values are obtained by applying the
   * current field evaluator to <code>key</code>, and by performing
   * default field evaluation on <code>object</code>, for those fields
   * that the field evaluator does not evaluate.
   *
   * @param key object to pass to field evaluator
   * @param o object to pass to default field evaluator
   */
  public void addLine(Object key,Object o ) {
    lines.add(getValues(o,key));
  }

  /** Interface for obtaining string representations of objects. */
  public interface FieldEvaluator {
    // Returning null means that default formatting is used
    /** Obtain the string representation of a filed.
     * @param field field to evaluate (a column name, as initialized
     * when creating the {@link Formatter} object.)
     * @param o object to format
     * @return a string representation of the field, or <code>null</code>
     * if the default string representation (as determined by the
     * default field evaluator) should be used.
     */
    public String getValue( String field, Object o );
  }

  private String[] getValues( Object o, Object key ) {
    String[] vals = new String[cols.length];
    for( int i=0;evaluator != null && i<cols.length;i++) {
      vals[i] = evaluator.getValue(cols[i], key);
      if( vals[i] != null )
        continue;
      String method = "get"+cols[i].substring(0,1).toUpperCase()+
          cols[i].substring(1);
      try {
        Method m = o.getClass().getMethod(method, new Class[] {});
        vals[i] = Util.toString( m.invoke(o,new Object[] {}));
      } catch (Exception ex) {
        vals[i]="<...>";
      }
    }
    return vals;
  }

  /*
  protected class Line {
    public String[] vals;
    public Object key;
    public Line( Object aKey, String[] aVals ) {
      key = aKey;
      vals = aVals;
    }
  }*/

  private String[] toStringArray( Object[] o) {
    String[] vals = new String[o.length];
    for( int i=0;i<o.length;i++)
      vals[i] = Util.toString(o[i]);
    return vals;
  }

  private int getPos( String name ) {
    for( int i=0;i<cols.length;i++)
      if( cols[i].equals(name) )
        return i;
    return -1;
  }

  /** Produce formatted output.
   * @param out stream to write output to
   */

  public void print(OutputStream out ) {
    PrintStream s = new PrintStream(out);
    print(s);
    s.flush();
  }

  /** Produce formatted output.
   * @param out stream to write output to
   */
  public abstract void print(PrintStream out);
}// arch-tag: b4d031b593d5290e8f5b14c492e7fd34 *-
