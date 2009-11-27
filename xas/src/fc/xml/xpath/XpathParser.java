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
import java.io.Reader;
import java.io.StringReader;
import java.util.EnumSet;

import fc.util.Util;
import fc.xml.xas.Qname;

public class XpathParser {

    private Reader reader;
    private StringBuilder buffer;
    private int pos;

    public XpathParser () {
	this(null);
    }

    public XpathParser (Reader reader) {
	this.reader = reader;
	this.buffer = new StringBuilder();
	this.pos = 0;
    }

    public void reset (Reader reader) {
	this.reader = reader;
	buffer.setLength(0);
	pos = 0;
    }

    private boolean isNcnameStart (int i) {
	return Character.isLetter(i) || i == '_';
    }

    private boolean isNcnameChar (int i) {
	return Character.isLetterOrDigit(i) || i == '.' || i == '-'
	    || i == '_';
    }

    private int read () throws IOException {
	if (buffer.length() <= pos) {
	    try {
		int i = reader.read();
		if (i >= 0) {
		    buffer.append((char) i);
		    pos += 1;
		}
		return i;
	    } catch (IOException ex) {
		raise(ex.getMessage());
		return -1;
	    }
	} else {
	    return buffer.charAt(pos++);
	}
    }

    private void unread () {
	unread(1);
    }

    private void unread (int amount) {
	pos -= amount;
    }

    private void raise (String message) throws IOException {
	throw new IOException("XpathParser:" + pos + ":" + message);
    }

    public XpathLocation parseLocation (String line) throws IOException {
	reset(new StringReader(line));
	return parseLocation();
    }

    public XpathExpression parseExpression () throws IOException {
	XpathExpression left = parseAnd();
	if (left != null) {
	    String token = fullToken();
	    while (XpathOperator.OR.toString().equals(token)) {
		XpathExpression right = parseAnd();
		if (right != null) {
		    left = XpathExpression.createBinary(XpathOperator.OR, left,
							right);
		    token = fullToken();
		} else {
		    raise("No parsable expression after operator "
			  + XpathOperator.OR);
		}
	    }
	    if (token != null) {
		unread(token.length());
	    }
	}
	return left;
    }

    private XpathExpression parseAnd () throws IOException {
	XpathExpression left = parseEquality();
	if (left != null) {
	    String token = fullToken();
	    while (XpathOperator.AND.toString().equals(token)) {
		XpathExpression right = parseEquality();
		if (right != null) {
		    left = XpathExpression.createBinary(XpathOperator.AND,
							left, right);
		    token = fullToken();
		} else {
		    raise("No parsable expression after operator "
			  + XpathOperator.AND);
		}
	    }
	    if (token != null) {
		unread(token.length());
	    }
	}
	return left;
    }

    private XpathExpression parseEquality () throws IOException {
	XpathExpression left = parseRelational();
	if (left != null) {
	    boolean finished = false;
	    String token = null;
	    while (!finished) {
		finished = true;
		token = fullToken();
		for (XpathOperator op :
			 EnumSet.range(XpathOperator.EQUAL,
				       XpathOperator.NOT_EQUAL)) {
		    if (op.toString().equals(token)) {
			XpathExpression right = parseRelational();
			if (right != null) {
			    left = XpathExpression.createBinary(op, left,
								right);
			    finished = false;
			    break;
			} else {
			    raise("No parsable expression found after"
				  + " operator " + op);
			}
		    }
		}
	    }
	    if (token != null) {
		unread(token.length());
	    }
	}
	return left;
    }

    private XpathExpression parseRelational () throws IOException {
	XpathExpression left = parseAdditive();
	if (left != null) {
	    boolean finished = false;
	    String token = null;
	    while (!finished) {
		finished = true;
		token = fullToken();
		for (XpathOperator op :
			 EnumSet.range(XpathOperator.LESS_THAN,
				       XpathOperator.GREATER_EQUAL)) {
		    if (op.toString().equals(token)) {
			XpathExpression right = parseAdditive();
			if (right != null) {
			    left = XpathExpression.createBinary(op, left,
								right);
			    finished = false;
			    break;
			} else {
			    raise("No parsable expression found after"
				  + " operator " + op);
			}
		    }
		}
	    }
	    if (token != null) {
		unread(token.length());
	    }
	}
	return left;
    }

    private XpathExpression parseAdditive () throws IOException {
	XpathExpression left = parseMultiplicative();
	if (left != null) {
	    boolean finished = false;
	    String token = null;
	    while (!finished) {
		finished = true;
		token = fullToken();
		for (XpathOperator op :
			 EnumSet.range(XpathOperator.PLUS,
				       XpathOperator.MINUS)) {
		    if (op.toString().equals(token)) {
			XpathExpression right = parseMultiplicative();
			if (right != null) {
			    left = XpathExpression.createBinary(op, left,
								right);
			    finished = false;
			    break;
			} else {
			    raise("No parsable expression found after"
				  + " operator " + op);
			}
		    }
		}
	    }
	    if (token != null) {
		unread(token.length());
	    }
	}
	return left;
    }

    private XpathExpression parseMultiplicative () throws IOException {
	XpathExpression left = parseUnary();
	if (left != null) {
	    boolean finished = false;
	    String token = null;
	    while (!finished) {
		finished = true;
		token = fullToken();
		for (XpathOperator op :
			 EnumSet.range(XpathOperator.TIMES,
				       XpathOperator.MOD)) {
		    if (op.toString().equals(token)) {
			XpathExpression right = parseUnary();
			if (right != null) {
			    left = XpathExpression.createBinary(op, left,
								right);
			    finished = false;
			    break;
			} else {
			    raise("No parsable expression found after"
				  + " operator " + op);
			}
		    }
		}
	    }
	    if (token != null) {
		unread(token.length());
	    }
	}
	return left;
    }

    private XpathExpression parseUnary () throws IOException {
	XpathExpression expr = null;
	int i = startToken();
	if (i == '-') {
	    expr = parseUnary();
	    if (expr != null) {
		expr = XpathExpression.createUnary(XpathOperator.NEGATION,
						   expr);
	    }
	} else {
	    unread();
	    expr = parseUnion();
	}
	return expr;
    }

    private XpathExpression parseUnion () throws IOException {
	XpathExpression left = null;
	XpathPath leftPath = parsePath();
	if (leftPath != null) {
	    left = XpathExpression.createPath(leftPath);
	    String token = fullToken();
	    while (XpathOperator.UNION.toString().equals(token)) {
		XpathPath rightPath = parsePath();
		if (rightPath != null) {
		    left = XpathExpression.createBinary
			(XpathOperator.UNION, left,
			 XpathExpression.createPath(rightPath));
		    token = fullToken();
		} else {
		    raise("No parsable expression after operator "
			  + XpathOperator.UNION);
		}
	    }
	    if (token != null) {
		unread(token.length());
	    }
	}
	return left;
    }

    public XpathLocation parseLocation () throws IOException {
	int savedPos = pos;
	int i = startToken();
	XpathLocation result = null;
	if (i == '/') {
	    result = new XpathLocation(true);
	    String token = readToken(i);
	    if (Util.equals(token, "//")) {
		result.addStep(XpathStep.createNodeTest
			       (XpathAxis.DESCENDANT_OR_SELF, XpathNode.NODE));
	    }
	    XpathStep step = parseStep();
	    if (step == null) {
		return result;
	    } else {
		result.addStep(step);
	    }
	} else if (i >= 0) {
	    unread();
	    XpathStep step = parseStep();
	    if (step != null) {
		result = new XpathLocation(false);
		result.addStep(step);
	    }
	}
	if (result != null) {
	    while ((i = startToken()) == '/') {
		if (Util.equals(readToken(i), "//")) {
		    result.addStep(XpathStep.createNodeTest
				   (XpathAxis.DESCENDANT_OR_SELF,
				    XpathNode.NODE));
		}
		XpathStep step = parseStep();
		if (step != null) {
		    result.addStep(step);
		} else {
		    raise("Axis found without a following step");
		}
	    }
	    if (i >= 0) {
		unread();
	    }
	} else {
	    pos = savedPos;
	}
	return result;
    }

    public XpathPath parsePath () throws IOException {
	int savedPos = pos;
	XpathPath path = null;
	XpathLocation location = parseLocation();
	if (location != null) {
	    path = XpathPath.createLocation(location);
	} else {
	    XpathPrimary primary = parsePrimary();
	    if (primary != null) {
		path = XpathPath.createFilter(primary);
		int i = startToken();
		while (i == '[') {
		    XpathExpression predicate = parseExpression();
		    if (predicate == null) {
			raise("Could not parse predicate");
		    } else {
			path.addPredicate(predicate);
			i = startToken();
			if (i != ']') {
			    raise("Predicate terminated by " + ((char) i)
				  + " instead of ]");
			} else {
			    i = startToken();
			}
		    }
		}
		if (i >= 0) {
		    unread();
		}
		if (i == '/') {
		    location = parseLocation();
		    if (location == null) {
			raise("Unable to parse location");
		    } else {
			path.addLocation(location);
		    }
		}
	    }
	}
	if (path == null) {
	    pos = savedPos;
	}
	return path;
    }

    public XpathPrimary parsePrimary () throws IOException {
	int savedPos = pos;
	XpathPrimary primary = null;
	int i = startToken();
	if (i == '(') {
	    XpathExpression expression = parseExpression();
	    if (expression != null) {
		i = startToken();
		if (i == ')') {
		    primary = XpathPrimary.createExpression(expression);
		} else {
		    raise("Expression closed by " + ((char) i)
			  + " instead of )");
		}
	    } else {
		raise("Could not parse expression");
	    }
	} else if (i == '$') {
	    primary = XpathPrimary.createVariable(parseQname());
	} else if (Character.isDigit(i)) {
	    primary = XpathPrimary.createNumber(parseNumber(i));
	} else if (i == '"' || i == '\'') {
	    primary = XpathPrimary.createLiteral(parseLiteral(i));
	} else if (isNcnameStart(i)) {
	    unread();
	    Qname name = parseQname();
	    if (name != null) {
		if (startToken() == '(') {
		    // XXX - placeholder for argument parsing
		    if (startToken() == ')') {
			primary = XpathPrimary.createFunctionCall(name);
		    } else {
			raise("Call of function " + name
			      + " opening parenthesis not terminated");
		    }
		} else {
		    raise("Call of function " + name
			  + " not followed by arguments");
		}
	    }
	} else if (i >= 0) {
	    unread();
	}
	if (primary == null) {
	    pos = savedPos;
	}
	return primary;
    }

    private Qname parseQname () throws IOException {
	Qname qname = null;
	String namespace = "";
	String name = null;
	int i = startToken();
	if (isNcnameStart(i)) {
	    StringBuilder result = new StringBuilder();
	    result.append((char) i);
	    int j = read();
	    while (j >= 0 && isNcnameChar(j)) {
		result.append((char) j);
		j = read();
	    }
	    unread();
	    name = result.toString();
	    if (j >= 0 && j == ':') {
		namespace = name;
		name = fullToken();
	    }
	    qname = new Qname(namespace, name);
	} else if (i >= 0) {
	    unread();
	}
	return qname;
    }

    private Double parseNumber (int i) throws IOException {
	return Double.parseDouble(readToken(i));
    }

    private String parseLiteral (int i) throws IOException {
	String token = readToken(i);
	return token.substring(1, token.length() - 1);
    }

    public XpathStep parseStep () throws IOException {
	int savedPos = pos;
	XpathStep step = null;
	XpathAxis axis = null;
	XpathNode node = null;
	Qname name = null;
	int i = startToken();
	if (i == -1) {
	    return null;
	} else if (i == '.') {
	    String token = readToken(i);
	    if (Util.equals(token, "..")) {
		axis = XpathAxis.PARENT;
	    } else {
		axis = XpathAxis.SELF;
	    }
	    node = XpathNode.NODE;
	} else if (i == '@') {
	    axis = XpathAxis.ATTRIBUTE;
	} else {
	    int axisPos = pos - 1;
	    String token = readToken(i);
	    for (XpathAxis a : XpathAxis.values()) {
		if (Util.equals(a.toString(), token)) {
		    axis = a;
		    break;
		}
	    }
	    if (axis != null) {
		token = fullToken();
		if (!Util.equals(token, "::")) {
		    raise("No :: found after axis specifier");
		}
	    } else {
		if (isNcnameStart(i) || i == '*') {
		    axis = XpathAxis.CHILD;
		    pos = axisPos;
		}
	    }
	}
	if (axis == null) {
	    pos = savedPos;
	    return null;
	}
	if (node == null) {
	    String token = fullToken();
	    i = startToken();
	    if (i == '(') {
		i = startToken();
		if (i != ')') {
		    raise("PIs with arguments not yet recognized");
		}
		for (XpathNode n : XpathNode.values()) {
		    if (Util.equals(n.getName(), token)) {
			node = n;
			break;
		    }
		}
	    } else {
		unread();
		if (Util.equals(token, "*")) {
		    name = new Qname("*", "*");
		} else {
		    i = startToken();
		    if (i != ':') {
			unread();
			name = new Qname("", token);
		    } else {
			name = new Qname(token, fullToken());
		    }
		}
	    }
	}
	if (name != null) {
	    step = XpathStep.createNameTest(axis, name);
	} else if (node != null) {
	    step = XpathStep.createNodeTest(axis, node);
	}
	if (step != null) {
	    i = startToken();
	    while (i == '[') {
		XpathExpression predicate = parseExpression();
		if (predicate == null) {
		    raise("Could not parse predicate");
		} else {
		    step.addPredicate(predicate);
		    i = startToken();
		    if (i != ']') {
			raise("Predicate terminated by " + ((char) i)
			      + " instead of ]");
		    } else {
			i = startToken();
		    }
		}
	    }
	    if (i >= 0) {
		unread();
	    }
	} else {
	    pos = savedPos;
	}
	return step;
    }

    private String fullToken () throws IOException {
	int i = startToken();
	if (i >= 0) {
	    return readToken(i);
	} else {
	    return null;
	}
    }

    private int startToken () throws IOException {
	int i;
	do {
	    i = read();
	} while (i >= 0 && Character.isWhitespace(i));
	//System.out.println("Started token with " + ((char) i));
	//(new Throwable()).printStackTrace(System.out);
	return i;
    }

    private String readToken (int i) throws IOException {
	StringBuilder result = new StringBuilder();
	result.append((char) i);
	if (i == '.') {
	    int j = read();
	    if (j == '.') {
		result.append('.');
	    } else if (Character.isDigit(j)) {
		do {
		    result.append((char) j);
		    j = read();
		} while (j >= 0 && Character.isDigit(j));
		if (j >= 0) {
		    unread();
		}
	    } else {
		unread();
	    }
	} else if (i == ':' || i == '/') {
	    int j = read();
	    if (j == i) {
		result.append((char) j);
	    } else if (j >= 0) {
		unread();
	    }
	} else if (i == '"' || i == '\'') {
	    int j = read();
	    while (j >= 0 && j != i) {
		result.append((char) j);
		j = read();
	    }
	    if (j >= 0) {
		result.append((char) j);
	    } else {
		raise("Unterminated literal " + result);
	    }
	} else if (i == '!' || i == '<' || i == '>') {
	    int j = read();
	    if (j == '=') {
		result.append((char) j);
	    } else if (j >= 0) {
		unread();
	    }
	} else if (Character.isDigit(i)) {
	    int j = read();
	    while (Character.isDigit(j)) {
		result.append((char) j);
		j = read();
	    }
	    if (j == '.') {
		result.append((char) j);
		while (Character.isDigit(j = read())) {
		    result.append((char) j);
		}
	    }
	    if (j >= 0) {
		unread();
	    }
	} else if (isNcnameStart(i)) {
	    int j = read();
	    while (j >= 0 && isNcnameChar(j)) {
		result.append((char) j);
		j = read();
	    }
	    if (j >= 0) {
		unread();
	    }
	}
	return result.toString();
    }

}

// arch-tag: 488411b6-f8dc-47ff-9b05-74836bb729f0
