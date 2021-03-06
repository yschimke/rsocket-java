package io.rsocket.transport.local;

import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import io.rsocket.uri.UriTransportRegistry;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class LocalUriTransportRegistryTest {
  @Test
  public void testLocalClient() {
    ClientTransport transport = UriTransportRegistry.clientForUri("local:test1");

    assertTrue(transport instanceof LocalClientTransport);
  }

  @Test
  public void testLocalServer() {
    ServerTransport transport = UriTransportRegistry.serverForUri("local:test1");

    assertTrue(transport instanceof LocalServerTransport);
  }
}
