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

public enum XpathOperator {

    OR ("or"),
    AND ("and"),
    EQUAL ("="),
    NOT_EQUAL ("!="),
    LESS_THAN ("<"),
    GREATER_THAN (">"),
    LESS_EQUAL ("<="),
    GREATER_EQUAL (">="),
    PLUS ("+"),
    MINUS ("-"),
    TIMES ("*"),
    DIV ("div"),
    MOD ("mod"),
    NEGATION ("-"),
    UNION ("|");

    String name;

    XpathOperator (String name) {
	this.name = name;
    }

    public String toString () {
	return name;
    }

}

// arch-tag: a2323181-9779-4def-a323-1750ee0ee0e7
