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

package fc.xml.xas;

public class StartDocument extends Item {

    private static final StartDocument instance = new StartDocument();

    public static StartDocument instance () {
	return instance;
    }

    private StartDocument () {
	super(START_DOCUMENT);
    }

    public String toString () {
	return "SD()";
    }

    public boolean equals (Object o) {
	return o == instance;
    }

    public int hashCode () {
	return START_DOCUMENT;
    }

}

// arch-tag: 57d4dcde-026f-4692-a5fc-82b5e51572c5
