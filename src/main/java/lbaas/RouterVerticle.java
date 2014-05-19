package lbaas;

import java.util.HashMap;
import java.util.HashSet;

import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.HttpVersion;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.streams.Pump;
import org.vertx.java.platform.Verticle;

public class RouterVerticle extends Verticle {

  public void start() {

      final Logger log = container.logger();
      final JsonObject conf = container.config();
      final Long keepAliveTimeOut = conf.getLong("keepAliveTimeOut", 2000L);
      final Long keepAliveMaxRequest = conf.getLong("maxKeepAliveRequests", 100L);
      final Integer clientRequestTimeOut = conf.getInteger("clientRequestTimeOut", 60000);
      final Integer clientConnectionTimeOut = conf.getInteger("clientConnectionTimeOut", 60000);
      final Boolean clientForceKeepAlive = conf.getBoolean("clientForceKeepAlive", false);
      final Integer clientMaxPoolSize = conf.getInteger("clientMaxPoolSize",1);

      final EventBus eventBus = vertx.eventBus();
      final HashMap<String, HashSet<Client>> vhosts = new HashMap<>();

      eventBus.registerHandler("route.add", new Handler<Message<String>>() {

        @Override
        public void handle(Message<String> buffer) {
            String[] message = buffer.body().split(":");
            HashSet<Client> clients = null;
            if (!vhosts.containsKey(message[0])) {
                clients = new HashSet<Client>();
                vhosts.put(message[0], clients);
            } else {
                clients = vhosts.get(message[0]);
            }
            clients.add(new Client(String.format("%s:%s", message[1],message[2]), vertx));
        }

      });

      eventBus.registerHandler("route.del", new Handler<Message<String>>() {

          @Override
          public void handle(Message<String> buffer) {
              String[] message = buffer.body().split(":");
              HashSet<Client> clients = null;
              if (!vhosts.containsKey(message[0])) {
                  return;
              } else {
                  clients = vhosts.get(message[0]);
              }
              clients.remove(new Client(String.format("%s:%s", message[1],message[2]), vertx));
          }

        });

      final Handler<HttpServerRequest> handlerHttpServerRequest = new Handler<HttpServerRequest>() {
         @Override
         public void handle(final HttpServerRequest sRequest) {

             sRequest.response().setChunked(true);

             final Long requestTimeoutTimer = vertx.setTimer(clientRequestTimeOut, new Handler<Long>() {
                 @Override
                 public void handle(Long event) {
                     serverShowErrorAndClose(sRequest.response(), new java.util.concurrent.TimeoutException());
                 }
             });

             String headerHost;
             if (sRequest.headers().contains("Host")) {
                 headerHost = sRequest.headers().get("Host").split(":")[0];
                 if (!vhosts.containsKey(headerHost)) {
                     serverShowErrorAndClose(sRequest.response(), new BadRequestException());
                     return;
                 }
             } else {
                 serverShowErrorAndClose(sRequest.response(), new BadRequestException());
                 return;
             }

             final boolean connectionKeepalive = sRequest.headers().contains("Connection") ?
                     (!sRequest.headers().get("Connection").equals("close")) : 
                     sRequest.version().equals(HttpVersion.HTTP_1_1);

             final HashSet<Client> clients = vhosts.get(headerHost);
             final Client client = ((Client)clients.toArray()[getChoice(clients.size())])
                     .setKeepAlive(connectionKeepalive||clientForceKeepAlive)
                     .setKeepAliveTimeOut(keepAliveTimeOut)
                     .setKeepAliveMaxRequest(keepAliveMaxRequest)
                     .setConnectionTimeout(clientConnectionTimeOut)
                     .setMaxPoolSize(clientMaxPoolSize);

             final Handler<HttpClientResponse> handlerHttpClientResponse = new Handler<HttpClientResponse>() {

                     @Override
                     public void handle(HttpClientResponse cResponse) {

                         vertx.cancelTimer(requestTimeoutTimer);

                         // Pump cResponse => sResponse
                         sRequest.response().headers().set(cResponse.headers());
                         if (!connectionKeepalive) {
                             sRequest.response().headers().set("Connection", "close");
                         }
                         Pump.createPump(cResponse, sRequest.response()).start();

                         cResponse.endHandler(new VoidHandler() {
                             @Override
                             public void handle() {
                                 sRequest.response().end();
                                 if (connectionKeepalive) {
                                     if (client.isKeepAliveLimit()) {
                                         client.close();
                                         serverNormalClose(sRequest.response());
                                     }
                                 } else {
                                     if (!clientForceKeepAlive) {
                                         client.close();
                                     }
                                     serverNormalClose(sRequest.response());
                                 }
                             }
                         });

                         cResponse.exceptionHandler(new Handler<Throwable>() {
                             @Override
                             public void handle(Throwable event) {
//                                 System.err.println(event.getMessage());
                                 serverShowErrorAndClose(sRequest.response(), event);
                                 client.close();
                             }
                         });
                 }
             };

             final HttpClientRequest cRequest = client.connect()
                     .request(sRequest.method(), sRequest.uri(),handlerHttpClientResponse)
                     .setChunked(true);

             cRequest.headers().set(sRequest.headers());
             if (clientForceKeepAlive) {
                 cRequest.headers().set("Connection", "keep-alive");
             }
             // Pump sRequest => cRequest
             Pump.createPump(sRequest, cRequest).start();

             cRequest.exceptionHandler(new Handler<Throwable>() {
                 @Override
                 public void handle(Throwable event) {
                     System.err.println(event.getMessage());
                     serverShowErrorAndClose(sRequest.response(), event);
                     client.close();
                 }
              });

             sRequest.endHandler(new VoidHandler() {
                 @Override
                 public void handle() {
                     cRequest.end();
                 }
              });
         }
     };

     vertx.createHttpServer().requestHandler(handlerHttpServerRequest)
         .setTCPKeepAlive(conf.getBoolean("serverTCPKeepAlive",true))
         .listen(conf.getInteger("port",9000));

     log.info(String.format("Instance %s started", this.toString()));

   }

   private int getChoice(int size) {
       int choice = (int) (Math.random() * (size - Float.MIN_VALUE));
       return choice;
   }

   private void serverShowErrorAndClose(final HttpServerResponse serverResponse, final Throwable event) {

       if (event instanceof java.util.concurrent.TimeoutException) {
           serverResponse.setStatusCode(504);
           serverResponse.setStatusMessage("Gateway Time-Out");
       } else if (event instanceof BadRequestException) {
           serverResponse.setStatusCode(400);
           serverResponse.setStatusMessage("Bad Request");
       } else {
           serverResponse.setStatusCode(502);
           serverResponse.setStatusMessage("Bad Gateway");
       }

       try {
           serverResponse.end();
       } catch (java.lang.IllegalStateException e) {
           // Response has already been written ?
//           System.err.println(e.getMessage());
       }

       try {
           serverResponse.close();
       } catch (RuntimeException e) {
           // Socket null or already closed
//           System.err.println(e.getMessage());
       }
   }

   private void serverNormalClose(final HttpServerResponse serverResponse) {
       try {
           serverResponse.close();
       } catch (RuntimeException e) {} // Ignore already closed
   }

}
