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

import fc.xml.xas.Qname;

public class XpathPrimary {

    public enum Kind {
	VARIABLE,
	EXPRESSION,
	LITERAL,
	NUMBER,
	FUNCTION_CALL;
    }

    private Kind kind;
    private Object value;

    private XpathPrimary (Kind kind, Object value) {
	this.kind = kind;
	this.value = value;
    }

    public static XpathPrimary createVariable (Qname value) {
	return new XpathPrimary(Kind.VARIABLE, value);
    }

    public static XpathPrimary createExpression (XpathExpression value) {
	return new XpathPrimary(Kind.EXPRESSION, value);
    }

    public static XpathPrimary createLiteral (String value) {
	return new XpathPrimary(Kind.LITERAL, value);
    }

    public static XpathPrimary createNumber (Double value) {
	return new XpathPrimary(Kind.NUMBER, value);
    }

    public static XpathPrimary createFunctionCall (Qname value) {
	return new XpathPrimary(Kind.FUNCTION_CALL, value);
    }

    public Kind getKind () {
	return kind;
    }

    public Object getValue () {
	return value;
    }

    public String toString () {
	switch (kind) {
	case VARIABLE: {
	    return ((Qname) value).toString();
	}
	case EXPRESSION: {
	    XpathExpression e = (XpathExpression) value;
	    return "(" + e.toString() + ")";
	}
	case LITERAL: {
	    String s = (String) value;
	    if (s.indexOf('"') >= 0) {
		return "'" + s + "'";
	    } else {
		return "\"" + s + "\"";
	    }
	}
	case NUMBER:
	    return Double.toString((Double) value);
	case FUNCTION_CALL:
	    return ((Qname) value).toString() + "()";
	default:
	    throw new IllegalStateException("Do not know how to stringify "
					    + kind);
	}
    }

}

// arch-tag: 8152955f-f17c-4ec0-a7be-710e612505e2
