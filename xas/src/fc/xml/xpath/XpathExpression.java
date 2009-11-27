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

public class XpathExpression {

    private XpathOperator operator;
    private XpathExpression left;
    private XpathExpression right;
    private XpathPath path;

    private XpathExpression (XpathOperator operator, XpathExpression left,
			     XpathExpression right, XpathPath path) {
	this.operator = operator;
	this.left = left;
	this.right = right;
	this.path = path;
    }

    private static int numberArgs (XpathOperator operator) {
	switch (operator) {
	case NEGATION:
	    return 1;
	default:
	    return 2;
	}
    }

    public static XpathExpression createBinary (XpathOperator operator,
						XpathExpression left,
						XpathExpression right) {
	assert operator != null && left != null && right != null;
	if (numberArgs(operator) != 2) {
	    throw new IllegalArgumentException("Operator " + operator
					       + " not binary");
	}
	if (operator == XpathOperator.UNION
	    && (left.path == null || right.path == null)) {
	    throw new IllegalArgumentException("Arguments to UNION must be"
					       + " path expressions");
	}
	return new XpathExpression(operator, left, right, null);
    }

    public static XpathExpression createUnary (XpathOperator operator,
					       XpathExpression expression) {
	assert operator != null && expression != null;
	if (numberArgs(operator) != 1) {
	    throw new IllegalArgumentException("Operator " + operator
					       + " not unary");
	}
	return new XpathExpression(operator, expression, null, null);
    }

    public static XpathExpression createPath (XpathPath path) {
	assert path != null;
	return new XpathExpression(null, null, null, path);
    }

    public boolean isPath () {
	return path != null;
    }

    public XpathPath getPath () {
	return path;
    }

    public boolean isBinary () {
	return right != null;
    }

    public XpathOperator getOperator () {
	return operator;
    }

    public XpathExpression getLeft () {
	return left;
    }

    public XpathExpression getRight () {
	return right;
    }

    public String toString () {
	if (path != null) {
	    return path.toString();
	} else if (numberArgs(operator) == 1) {
	    return String.valueOf(operator) + " " + left;
	} else {
	    return "(" + String.valueOf(left) + ") " + operator + " (" + right
		+ ")";
	}
    }

}

// arch-tag: c88a5a9a-0eef-45a2-aae8-adb7fe2530aa
