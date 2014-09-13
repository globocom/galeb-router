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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.globo.galeb.core.HttpCode;
import com.globo.galeb.test.integration.util.Action;
import com.globo.galeb.test.integration.util.UtilTestVerticle;

import org.junit.Ignore;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpHeaders;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;


public class RouterTest extends UtilTestVerticle {

    private final String httpHeaderHost = HttpHeaders.HOST.toString();

    @Test
    public void testRouterWhenEmpty() {
        newGet().onPort(9000).addHeader(httpHeaderHost, "www.unknownhost1.com").expectCode(HttpCode.BadRequest).expectBodySize(0).run();
    }

    @Test
    public void testRouterWith1VHostAndNoBackend() {
        JsonObject vhostJson = new JsonObject().putString("id", "test.localdomain");
        JsonObject expectedJson = new JsonObject().putString("status_message", "OK");
        JsonArray routesJson = new JsonArray().add(vhostJson);
        JsonObject postJson = new JsonObject().putNumber("version", 1L).putArray("routes", routesJson);

        Action action1 = newPost().onPort(9090).setBodyJson(postJson).atUri("/virtualhost").expectBodyJson(expectedJson);

        newGet().onPort(9000).addHeader(httpHeaderHost, "test.localdomain").expectCode(HttpCode.BadRequest).expectBodySize(0).after(action1);
        action1.run();

    }

    @Test
    public void testRouterNoVHostAddBackend() {
        JsonObject backend = new JsonObject().putString("id", "1.2.3.4:80");
        JsonObject vhostJson = new JsonObject().putString("id", "test.localdomain")
                .putArray("backends", new JsonArray().addObject(backend));
        JsonObject expectedJson = new JsonObject().putString("status_message", "OK");
        JsonArray routesJson = new JsonArray().add(vhostJson);
        JsonObject postJson = new JsonObject().putNumber("version", 1L).putArray("routes", routesJson);

        Action action1 = newPost().onPort(9090).setBodyJson(postJson).atUri("/backend").expectBodyJson(expectedJson);

        newGet().onPort(9000).addHeader(httpHeaderHost, "test.localdomain").expectCode(HttpCode.BadRequest).expectBodySize(0).after(action1);
        action1.run();

    }

    @Test
    public void testRouterWith1VHostAnd1ClosedBackend() {
        JsonObject backend = new JsonObject().putString("id", "127.0.0.1:8888");
        JsonObject vhostJson = new JsonObject().putString("id", "test.localdomain")
                .putArray("backends", new JsonArray().addObject(backend));
        JsonObject expectedJson = new JsonObject().putString("status_message", "OK");
        JsonArray routesJson = new JsonArray().add(vhostJson);
        JsonObject postJson = new JsonObject().putNumber("version", 1L).putArray("routes", routesJson);

        Action action1 = newPost().onPort(9090).setBodyJson(postJson).atUri("/virtualhost").expectBodyJson(expectedJson);

        Action action2 = newPost().onPort(9090).setBodyJson(postJson).atUri("/backend").expectBodyJson(expectedJson).after(action1);

        newGet().onPort(9000).addHeader(httpHeaderHost, "test.localdomain").expectCode(HttpCode.BadGateway).expectBodySize(0).after(action2);

        action1.run();

    }

    @Test
    public void testRouterWith1VHostAnd1TimeoutBackend() {
        // The timeout is set to 1s at test initialization
        JsonObject backend = new JsonObject().putString("id", "1.2.3.4:8888");
        JsonObject vhostJson = new JsonObject().putString("id", "test.localdomain")
                .putArray("backends", new JsonArray().addObject(backend));
        JsonArray routesJson = new JsonArray().add(vhostJson);
        JsonObject postJson = new JsonObject().putNumber("version", 1L).putArray("routes", routesJson);
        JsonObject expectedJson = new JsonObject().putString("status_message", "OK");

        Action action1 = newPost().onPort(9090).setBodyJson(postJson).atUri("/virtualhost").expectBodyJson(expectedJson);

        Action action2 = newPost().onPort(9090).setBodyJson(postJson).atUri("/backend").expectBodyJson(expectedJson).after(action1);

        newGet().onPort(9000).addHeader(httpHeaderHost, "test.localdomain").expectCode(HttpCode.GatewayTimeout).expectBodySize(0).after(action2);

        action1.run();

    }

    @Test
    public void testRouterWith1VHostAnd1RunningBackend() {
        // Create backend
        final HttpServer server = vertx.createHttpServer();
        server.requestHandler(new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest request) {
                request.response().setChunked(true).write("response from backend").end();
            }
        });
        server.listen(8888, "localhost");

        // Create Jsons
        JsonObject backend = new JsonObject().putString("id", "127.0.0.1:8888");
        JsonObject vhostJson = new JsonObject().putString("id", "test.localdomain")
                .putArray("backends", new JsonArray().addObject(backend));
        JsonObject expectedJson = new JsonObject().putString("status_message", "OK");
        JsonArray routesJson = new JsonArray().add(vhostJson);
        JsonObject postJson = new JsonObject().putNumber("version", 1L).putArray("routes", routesJson);

        // Create Actions
        Action action1 = newPost().onPort(9090).setBodyJson(postJson).atUri("/virtualhost").expectBodyJson(expectedJson);
        Action action2 = newPost().onPort(9090).setBodyJson(postJson).atUri("/backend").expectBodyJson(expectedJson).after(action1);
        final Action action3 = newGet().onPort(9000).addHeader(httpHeaderHost, "test.localdomain")
                .expectCode(HttpCode.Ok).expectBody("response from backend").after(action2).setDontStop();

        // Create handler to close server after the test
        getVertx().eventBus().registerHandler("ended.action", new Handler<Message<String>>() {
            @Override
            public void handle(Message<String> message) {
                if (message.body().equals(action3.id())) {
                    server.close();
                    testCompleteWrapper();
                }
            };
        });

        action1.run();
    }

    @Test
    public void testPost2RouterWith1VHostAnd1RunningBackend200() {
        // Create backend
        final HttpServer server = vertx.createHttpServer();
        server.requestHandler(new Handler<HttpServerRequest>() {
            @Override
            public void handle(final HttpServerRequest request) {
                request.bodyHandler(new Handler<Buffer>() {
                    @Override
                    public void handle(Buffer buffer) {
                        request.response().setChunked(true).write(buffer.toString()).end();
                    }
                });
            }
        });
        server.listen(8888, "localhost");

        // Create Jsons
        JsonObject backend = new JsonObject().putString("id", "127.0.0.1:8888");
        JsonObject vhostJson = new JsonObject().putString("id", "test.localdomain")
                .putArray("backends", new JsonArray().addObject(backend));
        JsonObject expectedJson = new JsonObject().putString("status_message", "OK");
        JsonArray routesJson = new JsonArray().add(vhostJson);
        JsonObject postJson = new JsonObject().putNumber("version", 1L).putArray("routes", routesJson);

        // Create Actions
        Action action1 = newPost().onPort(9090).setBodyJson(postJson).atUri("/virtualhost").expectBodyJson(expectedJson);
        Action action2 = newPost().onPort(9090).setBodyJson(postJson).atUri("/backend").expectBodyJson(expectedJson).after(action1);
        final Action action3 = newPost().onPort(9000).addHeader(httpHeaderHost, "test.localdomain").setBodyJson("{ \"some key\": \"some value\" }")
                .expectCode(HttpCode.Ok).expectBody("{\"some key\":\"some value\"}").after(action2).setDontStop();

        // Create handler to close server after the test
        getVertx().eventBus().registerHandler("ended.action", new Handler<Message<String>>() {
            @Override
            public void handle(Message<String> message) {
                if (message.body().equals(action3.id())) {
                    server.close();
                    testCompleteWrapper();
                }
            };
        });

        action1.run();
    }

    @Ignore
    @Test
    public void testRouterWith1VHostAnd1BackendAllHTTPCodes() {
        // Create backend
    	final Pattern p = Pattern.compile("^/([0-9]+)$");
        final HttpServer server = vertx.createHttpServer();
        server.requestHandler(new Handler<HttpServerRequest>() {
            @Override
            public void handle(final HttpServerRequest request) {
                request.endHandler(new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        Matcher m = p.matcher(request.uri());
                        int http_code = -1;
                        if (m.find()) {
                            http_code = Integer.parseInt(m.group(1));
                        }
                        request.response().setStatusCode(http_code).end();
                    }
                });
            }
        });
        server.listen(8888, "localhost");

        // Create Jsons
        JsonObject backend = new JsonObject().putString("id", "127.0.0.1:8888");
        JsonObject vhostJson = new JsonObject().putString("id", "test.localdomain")
                .putArray("backends", new JsonArray().addObject(backend));
        JsonObject expectedJson = new JsonObject().putString("status_message", "OK");
        JsonArray routesJson = new JsonArray().add(vhostJson);
        JsonObject postJson = new JsonObject().putNumber("version", 1L).putArray("routes", routesJson);

        // Create Actions
        Action action1 = newPost().onPort(9090).setBodyJson(postJson).atUri("/virtualhost").expectBodyJson(expectedJson);
        Action action2 = newPost().onPort(9090).setBodyJson(postJson).atUri("/backend").expectBodyJson(expectedJson).after(action1);
        Action actionn1 = action2; Action actionn2 = null;
        for (int http_code=HttpCode.Ok ; http_code < 600 ; http_code++) {
        	actionn2 = newGet().onPort(9000).addHeader(httpHeaderHost, "test.localdomain").atUri(String.format("/%d", http_code))
                	.expectCode(http_code).expectBodySize(0).setDontStop().after(actionn1);
        	actionn1 = actionn2;
        }
        final Action finalAction = actionn2;

        // Create handler to close server after the test
        getVertx().eventBus().registerHandler("ended.action", new Handler<Message<String>>() {
            @Override
            public void handle(Message<String> message) {
                if (message.body().equals(finalAction.id())) {
                    server.close();
                    testCompleteWrapper();
                }
            };
        });

        action1.run();
    }

    @Test
    public void testRouterWith1VHostAnd1Backend302() {
        // Create backend
        final HttpServer server = vertx.createHttpServer();
        server.requestHandler(new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest request) {
                request.response().setStatusCode(HttpCode.TemporaryRedirect).end();
            }
        });
        server.listen(8888, "localhost");

        // Create Jsons
        JsonObject backend = new JsonObject().putString("id", "127.0.0.1:8888");
        JsonObject vhostJson = new JsonObject().putString("id", "test.localdomain")
                .putArray("backends", new JsonArray().addObject(backend));
        JsonObject expectedJson = new JsonObject().putString("status_message", "OK");
        JsonArray routesJson = new JsonArray().add(vhostJson);
        JsonObject postJson = new JsonObject().putNumber("version", 1L).putArray("routes", routesJson);

        // Create Actions
        Action action1 = newPost().onPort(9090).setBodyJson(postJson).atUri("/virtualhost").expectBodyJson(expectedJson);
        Action action2 = newPost().onPort(9090).setBodyJson(postJson).atUri("/backend").expectBodyJson(expectedJson).after(action1);
        final Action action3 = newGet().onPort(9000).addHeader(httpHeaderHost, "test.localdomain")
                .expectCode(HttpCode.TemporaryRedirect).expectBodySize(0).after(action2).setDontStop();

        // Create handler to close server after the test
        getVertx().eventBus().registerHandler("ended.action", new Handler<Message<String>>() {
            @Override
            public void handle(Message<String> message) {
                if (message.body().equals(action3.id())) {
                    server.close();
                    testCompleteWrapper();
                }
            };
        });

        action1.run();
    }

}
