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

// $Id: CodecException.java,v 1.3 2004/10/06 16:05:09 ctl Exp $
package fc.syxaw.codec;

import java.io.IOException;

/** Generic encoder/decoder exception. */

public class CodecException extends IOException {

  public CodecException() {
    super();
  }

  public CodecException(String msg) {
    super(msg);
  }
}

// arch-tag: 3ec80144d75b4f45b6bb5b55eb2de04b *-
