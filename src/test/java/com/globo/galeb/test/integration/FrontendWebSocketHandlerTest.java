/*
 * Copyright (c) 2014 Globo.com - ATeam
 * All rights reserved.
 *
 * This source is subject to the Apache License, Version 2.0.
 * Please see the LICENSE file for more information.
 *
 * Authors: See AUTHORS file
 *
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF ANY
 * KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A
 * PARTICULAR PURPOSE.
 */
package com.globo.galeb.test.integration;

import com.globo.galeb.core.IJsonable;
import com.globo.galeb.test.integration.util.Action;
import com.globo.galeb.test.integration.util.UtilTestVerticle;

import org.junit.Ignore;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.CaseInsensitiveMultiMap;
import org.vertx.java.core.http.HttpHeaders;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketVersion;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.streams.Pump;

public class FrontendWebSocketHandlerTest extends UtilTestVerticle {

    private final String httpHeaderHost = HttpHeaders.HOST.toString();

    private void connectWS() {
        MultiMap headers = new CaseInsensitiveMultiMap();
        headers.set(httpHeaderHost, "test.localdomain");

      vertx.createHttpClient().setHost("127.0.0.1").setPort(9000)
              .connectWebsocket("/", WebSocketVersion.RFC6455, headers, new Handler<WebSocket>() {

          @Override
          public void handle(final WebSocket ws) {

              vertx.setTimer(100, new Handler<Long>() {

                @Override
                public void handle(Long event) {
                    ws.close();
                }
            });

              ws.dataHandler(new Handler<Buffer>() {

                  @Override
                  public void handle(Buffer buffer) {
                      System.out.println(buffer.toString());
                  }
              });

              ws.closeHandler(new Handler<Void>() {

                @Override
                public void handle(Void event) {
                    vertx.setTimer(200, new Handler<Long>() {

                        @Override
                        public void handle(Long event) {
                            testCompleteWrapper();
                        }
                    });
                }
            });

              ws.writeTextFrame("1_ohlalalala");
              ws.writeTextFrame("2_ohlalalala");
              ws.writeTextFrame("3_ohlalalala");
          }
      });
    }

    @Ignore
    @Test
    public void testRouterWS() {
        // Create backend
        final HttpServer server = vertx.createHttpServer();
        server.websocketHandler(new Handler<ServerWebSocket>() {
            @Override
            public void handle(final ServerWebSocket serverWebSocket) {
                Pump.createPump(serverWebSocket, serverWebSocket).start();

//                vertx.setTimer(100, new Handler<Long>() {
//
//                    @Override
//                    public void handle(Long event) {
//                        serverWebSocket.close();
//                    }
//                });

            }
        });
        server.requestHandler(new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest request) {
                request.response().setChunked(true).write("response from backend").end();
            }
        });
        server.listen(8888, "localhost");

        // Create Jsons
        JsonObject backend = new JsonObject().putString(IJsonable.jsonIdFieldName, "127.0.0.1:8888");
        JsonObject vhostJson = new JsonObject().putString(IJsonable.jsonIdFieldName, "test.localdomain")
                .putArray("backends", new JsonArray().addObject(backend));
        JsonObject expectedJson = new JsonObject().putString("status_message", "OK");

        // Create Actions
        Action action1 = newPost().onPort(9090).setBodyJson(vhostJson).atUri("/virtualhost").expectBodyJson(expectedJson);
        final Action action2 = newPost().onPort(9090).setBodyJson(vhostJson).atUri("/backend").expectBodyJson(expectedJson).after(action1).setDontStop();

        // Create handler to close server after the test
        getVertx().eventBus().registerHandler("ended.action", new Handler<Message<String>>() {
            @Override
            public void handle(Message<String> message) {
                if (message.body().equals(action2.id())) {
                    connectWS();
                }
            };
        });

        action1.run();
    }

}