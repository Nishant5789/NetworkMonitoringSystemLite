package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.config.ZMQConfig;
import com.motadata.NMSLiteUsingVertex.services.DiscoveryService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.zeromq.ZMQ;

public class DiscoveryVerticle extends AbstractVerticle {
  DiscoveryService discoveryService;

  @Override
  public void start() {
    System.out.println("DiscoveryVerticle deploy on : " + Thread.currentThread().getName());
    discoveryService = new DiscoveryService(vertx);
    vertx.eventBus().consumer("discovery.verticle", this::discovery);
  }

  private void discovery(Message<Object> message)  {
    JsonObject payload = (JsonObject) message.body();
    Integer id = payload.getInteger("credential_id");

    discoveryService.findById(id)
      .onSuccess(credential->{
        if(credential == null){
          message.reply(new JsonObject().put("status", "failed").put("statusMsg","Credential not found"));
        }
        else{
          vertx.executeBlocking(promise -> {
            payload.put("username",credential.getString("username"))
              .put("password",credential.getString("password"))
              .put("event_name","discovery")
              .put("plugin_engine","linux");
            checkDiscovery(payload)
              .onSuccess(promise::complete)
              .onFailure(promise::fail);
          }, result -> {
            if (result.succeeded()) {
              discoveryService.save(payload)
                  .onSuccess(responce->message.reply(result.result()))
                  .onFailure(err->message.reply("failed to added object "+err.getMessage()));
            } else {
              message.reply(result.result());
            }
          });
        }
      })
      .onFailure(err->{
        message.reply(new JsonObject().put("status", "error").put("statusMsg",err.getMessage()));
      });
  }

  private Future<String> checkDiscovery(JsonObject requestJson) {
    try {
      // Send request via ZMQ
      ZMQ.Socket socket = new ZMQConfig("tcp://127.0.0.1:5555").getSocket();
      System.out.println("Sending request: " + requestJson.toString());
      socket.send(requestJson.toString().getBytes(ZMQ.CHARSET), 0);

      // Receive response
      byte[] reply = socket.recv(0);
      String jsonResponse = new String(reply, ZMQ.CHARSET);
      System.out.println("Received response:\n" + jsonResponse);

      return Future.succeededFuture(jsonResponse);
    } catch (Exception e) {
      return Future.failedFuture("ZMQ communication failed: " + e.getMessage());
    }
  }
}


