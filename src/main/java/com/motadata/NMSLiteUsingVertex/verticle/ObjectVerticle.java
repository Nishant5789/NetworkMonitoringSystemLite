package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.LinkedList;
import java.util.Queue;

public class ObjectVerticle extends AbstractVerticle {
  public static final Queue<JsonObject> deviceQueue = new LinkedList<>();

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    System.out.println("ObjectVerticle deploy on : " + Thread.currentThread().getName());
    vertx.eventBus().consumer("provision", this::provision);
    vertx.eventBus().consumer("objectPollingData",this::getPollerResult);
  }

  private void getPollerResult(Message<Object> message) {
         }

  // handle provision
  private void provision(Message<Object> message) {
    JsonObject payload = (JsonObject) message.body();
    JsonArray object_ids = payload.getJsonArray("object_ids");
    String  pollInterval = payload.getString("pollInterval");

     QueryHandler.getAllByIds("monitored_device",object_ids).onSuccess(objects->{

      for(JsonObject object : objects){
        String ip = object.getString("ip");
        String port = object.getString("port");
        Utils.checkDeviceAvailability(ip,port)
          .onSuccess(flag->{
            if(flag){
              deviceQueue.add(object.put("lastPollTime",System.currentTimeMillis()));
              System.out.println("Device's ip: " +ip+ "added in deviceQueue");
            }
            else{
              System.out.println("Device's ip: " +ip+ " with Port: " +port+  "is not reachable");
            }
          })
          .onFailure(err->{
            System.out.println("Device " + ip + " not available: " + err.getMessage());
          });
      }
      handleDeviceScheduling(Integer.parseInt(pollInterval));
      message.reply(new JsonObject().put("message", "Polling is started for provisioned device"));
    });
  }

  // schedule device polling
  private void handleDeviceScheduling(int pollInterval) {
    Main.vertx().setTimer(4000,timeId->{
      System.out.println("polling is start devicequeue : "+deviceQueue);
      long currentTime = System.currentTimeMillis();
      JsonArray devicesToPoll = new JsonArray();

      for (JsonObject device : deviceQueue)
      {
        long lastPollTime = device.getLong("lastPollTime");
        long timeSinceLastPoll = currentTime - lastPollTime;

        if (timeSinceLastPoll >= pollInterval)
        {
          // Update last poll time
          device.put("lastPollTime", currentTime);
          devicesToPoll.add(device);
          System.out.println("devices send for  polling: " + devicesToPoll.encodePrettily());
          handleDevicePolling(devicesToPoll);
         }
        }
      });
  }

  // handle device polling
  private void handleDevicePolling(JsonArray devicesToPoll){
      Main.vertx().eventBus().request("poller.verticle", devicesToPoll,
        result-> {
          if(result.succeeded()){
            System.out.println("Polling is completed");
          }
          else{
            System.out.println("Failed to run polling");
          }
        });
  }

}
