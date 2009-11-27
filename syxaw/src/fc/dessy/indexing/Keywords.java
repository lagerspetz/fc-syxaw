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

package fc.dessy.indexing;

import java.util.LinkedList;

import fc.util.StringUtil;
import fc.util.log.Log;

public class Keywords {

  protected String keywords;
  protected String uid;
  protected int wordLimit = -1;

  public Keywords(boolean isKeywords, String uid) {
    if (isKeywords) {
      this.keywords = uid;
    }
    else {
      this.uid = uid;
    }
  }

  public String toStrings() {
    if (this.keywords == null || this.keywords.equals("")) {
      if (this.uid == null || this.uid.equals("")) {
        //Log.log("UID AND KEYWORDS ARE EMPTY!!", Log.DEBUG);
        return "";
      }
      // use uid to get real keywords
      /*
        ArrayList<String> dirs = IndexList.getDirectories(uid);
        if (dirs == null) {
          return "";
        }
        Iterator<String> dIt = dirs.iterator();
        StringBuffer list = new StringBuffer();
        while(dIt.hasNext()) {
          String dir = dIt.next();
          list.append(dir + "\n");
          HashSet<String> words = IndexList.getWords(uid, dir);
          if (words == null) {
            continue;
          }
          Iterator<String> wIt = words.iterator();
          while (wIt.hasNext()) {
            list.append(wIt.next() + "\n");
          }
        }
        this.keywords = list.toString();

       */
      return "";
    }
    return this.keywords;
  }

  public LinkedList split() {
    if (keywords == null || keywords.equals("")) {
      return null;
    }
    return StringUtil.splitWordsL(keywords, false, new char[] {'\n'});
  }

  public void truncate(int lth){
    if (lth >= 0){
      Log.log("Word limit set to " + lth, Log.DEBUG);
      this.wordLimit = lth;
    }
  }
}
