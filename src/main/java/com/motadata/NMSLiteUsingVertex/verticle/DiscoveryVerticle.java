package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.config.ZMQConfig;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.zeromq.ZMQ;

public class DiscoveryVerticle extends AbstractVerticle {

  @Override
  public void start() {
    System.out.println("DiscoveryVerticle deploy on : " + Thread.currentThread().getName());
    vertx.eventBus().localConsumer("discovery.verticle", this::discovery);
  }

  private void discovery(Message<Object> message)  {

    JsonObject payload = (JsonObject) message.body();
    String credentialId = payload.getString("credential_id");
    String ip = payload.getString("ip");
    String port = payload.getString("port");

//    String deviceId =
      QueryHandler.saveAndGetById("monitored_device",payload)
        .onSuccess(deviceId->{
          Utils.checkDeviceAvailability(ip,port)
            .onSuccess(flag -> {
              QueryHandler.findById("credential",credentialId)
                .onSuccess(credential -> {
                  if(credential == null){
                    message.reply(new JsonObject().put("status", "failed").put("statusMsg","Credential not found"));
                  }
                  else{
                    vertx.executeBlocking(promise -> {

                      JsonObject discoveryPayload =  new JsonObject(String.valueOf(payload));
                      discoveryPayload.put("username",credential.getString("username"))
                        .put("password",credential.getString("password"))
                        .put("event_name","discovery")
                        .put("plugin_engine","linux");

                      checkDiscovery(discoveryPayload)
                        .onSuccess(promise::complete)
                        .onFailure(promise::fail);

                    }, result -> {
                      if (result.succeeded()) {
                        QueryHandler.updateByField("monitored_device",payload.put("is_discovered","true"),"id = $6",deviceId)
                          .onSuccess(responce->{
                            message.reply(new JsonObject(String.valueOf(result.result())));
                          })
                          .onFailure(err->
                          {
                            message.reply(new JsonObject().put("status", "failed").put("statusMsg", "failed to added object" + err.getMessage()));
                          });
                      }
                      else {
                        System.out.println(result.cause());
                        message.reply(new JsonObject().put("status", "failed").put("statusMsg",result.cause()));
                      }
                    });
                  }
                })
                .onFailure(err->{
                  message.reply(new JsonObject().put("status", "error").put("statusMsg",err.getMessage()));
                });
            })
            .onFailure(err->{
              System.out.println("discovery failed for device ip: "+ip+" port: "+port);
              message.reply(new JsonObject().put("status", "error").put("statusMsg",err.getMessage()));
            });
          System.out.println("device is saved and starting discovery run");
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

      JsonObject responceObject = new JsonObject(jsonResponse);

      if(responceObject.getString("status").equals("failed")){
        return  Future.failedFuture(jsonResponse);
      }

      return Future.succeededFuture(jsonResponse);
    } catch (Exception e) {
      return Future.failedFuture("ZMQ communication failed: " + e.getMessage());
    }
  }
}


