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

package fc.syxaw.transport;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import fc.syxaw.transport.www.SimpleGetPutChannel;
import fc.util.log.Log;

public class ChannelProvider {

  private static final Map<String, 
    Class<? extends SynchronousCallChannel>> CHANNEL_PROVIDERS;
  
  static {
    Map<String, Class<? extends SynchronousCallChannel>> cp = 
        new HashMap<String, Class<? extends  SynchronousCallChannel>>();
    cp.put(SimpleGetPutChannel.URI, SimpleGetPutChannel.class);
    CHANNEL_PROVIDERS = cp; 
  }
  
  public static SynchronousCallChannel getChannel(String destination)
      throws IOException {
    Log.debug("Resolving channel for destination", destination);
    Class<? extends SynchronousCallChannel> ch = 
      CHANNEL_PROVIDERS.get(destination);
    if (ch != null) {
      try {
        // When will this ever be pretty?
        return ch.getConstructor(new Class<?>[] 
            {String.class}).newInstance(new Object[] {destination});
      } catch (IllegalArgumentException e) {
        Log.fatal(e);
      } catch (SecurityException e) {
        Log.fatal(e);
      } catch (InstantiationException e) {
        Log.fatal(e);
      } catch (IllegalAccessException e) {
        Log.fatal(e);
      } catch (InvocationTargetException e) {
        Log.fatal(e);
      } catch (NoSuchMethodException e) {
        Log.fatal(e);
      }
      throw new IOException("Could not create channel of type " + ch +
          " to " + destination);
    }
    return new HTTPCallChannel(destination);
  }
  
}
// arch-tag: 794b8bb3-70b8-4762-8772-6d676c2524d0
//
