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

package fc.xml.xpath;

import java.io.IOException;
import java.io.StringReader;

public class Canonize {

    public static void main (String[] args) {
	XpathParser parser = null;
	for (String arg : args) {
	    System.out.println("Input expression:  " + arg);
	    StringReader reader = new StringReader(arg);
	    if (parser == null) {
		parser = new XpathParser(reader);
	    } else {
		parser.reset(reader);
	    }
	    try {
		XpathLocation loc = parser.parseLocation();
		System.out.println("Output expression: " + loc);
	    } catch (IOException ex) {
		System.err.println("Error parsing expression " + arg);
		ex.printStackTrace();
	    }
	}
    }

}

// arch-tag: 5993cd92-1528-4ffa-b1fe-598cbb93ba83
