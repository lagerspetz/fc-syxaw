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

import java.util.Date;

public class Progress {
  private static int size = 0;
  private static int progress = 0;
  private static long millisEstimated = 0;
  private static long startTime = 0;
  private static int progressInElapsed = 0;
  
  public static void reset(){
    size = progress = progressInElapsed = 0;
    millisEstimated = startTime = 0;
  }
  
  public static void moreStuff(int howmore){
    if (howmore <= 0)
      return;
    size += howmore;
    estimateAndEvaluate();
  }
  
  public static void lessStuff(int howLess){
    if (howLess <= 0)
      return;
    size -= howLess;
    estimateAndEvaluate();
  }
  
  public static void someDone(int howSome){
    if (howSome <= 0)
      return;
    progress += howSome;
    estimateAndEvaluate();
  }
  
  public static void someRolledBack(int howSome){
    if (howSome <= 0)
      return;
    progress -= howSome;
    estimateAndEvaluate();
  }
  
  public static void oneDone(){
    progress++;
    estimateAndEvaluate();
  }
  
  private static void estimateAndEvaluate(){
    if (startTime == 0 && progressInElapsed == 0){
      startTime = new Date().getTime();
      return;
    }
    long time = new Date().getTime();
    double rate = (time - startTime) / (progress * 1.0);
    millisEstimated = (long) (( size - progress ) / rate);
  }
  
  public static int getSize(){
    return size;
  }
  
  public static int getDone(){
    return progress;
  }
  
  public static long getMillisRemainingEstimate(){
    return millisEstimated;
  }
}
