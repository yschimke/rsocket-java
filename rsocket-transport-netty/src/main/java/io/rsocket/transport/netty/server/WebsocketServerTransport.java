/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.rsocket.transport.netty.server;

import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.netty.WebsocketDuplexConnection;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.server.HttpServer;

public class WebsocketServerTransport implements ServerTransport<NettyContextCloseable> {
  HttpServer server;

  private WebsocketServerTransport(HttpServer server) {
    this.server = server;
  }

  public static WebsocketServerTransport create(String bindAddress, int port) {
    HttpServer httpServer = HttpServer.create(bindAddress, port);
    return create(httpServer);
  }

  public static WebsocketServerTransport create(int port) {
    HttpServer httpServer = HttpServer.create(port);
    return create(httpServer);
  }

  public static WebsocketServerTransport create(HttpServer server) {
    return new WebsocketServerTransport(server);
  }

  @Override
  public Mono<NettyContextCloseable> start(ServerTransport.ConnectionAcceptor acceptor) {
    return server
        .newHandler(
            (request, response) ->
                response.sendWebsocket(
                    (in, out) -> {
                      WebsocketDuplexConnection connection =
                          new WebsocketDuplexConnection(in, out, in.context());
                      acceptor.apply(connection).subscribe();

                      return out.neverComplete();
                    }))
        .map(NettyContextCloseable::new);
  }
}
