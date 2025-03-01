package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.services.DiscoveryService;
import com.motadata.NMSLiteUsingVertex.services.ObjectService;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.LinkedList;
import java.util.Queue;

public class ObjectVerticle extends AbstractVerticle {
  public static final Queue<JsonObject> deviceQueue = new LinkedList<>();
  ObjectService objectService;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    System.out.println("ObjectVerticle deploy on : " + Thread.currentThread().getName());
    objectService = new ObjectService(vertx);
    vertx.eventBus().consumer("provision", this::provision);
  }

  private void provision(Message<Object> message) {
    JsonObject payload = (JsonObject) message.body();
    JsonArray object_ids = payload.getJsonArray("object_ids");

    objectService.getAllObjects(object_ids).onSuccess(objects->{

      for(JsonObject object : objects){
        String ip = object.getString("ip");
        String port = object.getString("port");

        checkDeviceAvailability(ip,port)
          .onSuccess(flag->{
            if(flag){
              deviceQueue.add(object);
            }
            else{
              System.out.println("Device's ip: " +ip+ " with Port: " +port+  "is not reachable");
            }
          })
          .onFailure(err->{
            System.out.println("Device " +ip+ "not available: " + err.getMessage());
          });
      }

      message.reply(new JsonObject().put("message", "Polling is started for provisioned device").toString());
    });
  }
//  scheduler()
  // Checks if the device IP is reachable and if the port is open
  private Future<Boolean> checkDeviceAvailability(String ip, String port) {
    try {
      return Utils.ping(ip).compose(isReachable -> {
        if (isReachable) {
          return Utils.checkPort(ip, port);
        }
        else {
          return Future.failedFuture("Device is not reachable");
        }
      });
    }
    catch (Exception exception) {
      return Future.failedFuture("Failed to check device availability. " + exception.getMessage());
    }
  }

  // schedule device polling
  private void scheduler(String event, int pollInterval){

  }



}
