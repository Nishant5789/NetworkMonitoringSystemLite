package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.config.ZMQConfig;
import com.motadata.NMSLiteUsingVertex.services.PollerService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.zeromq.ZMQ;

import java.io.FileWriter;
import java.io.IOException;

public class PollerVerticle extends AbstractVerticle {

  private static PollerService pollerService;
  @Override
  public void start(Promise<Void> startPromise) throws Exception {
      pollerService = new PollerService(vertx);
      vertx.eventBus().consumer("poller.verticle",this::handlePolling);
  }

  // start polling
  private void handlePolling(Message<Object> message) {
    JsonArray devicesList = (JsonArray) message.body();
    ZMQ.Socket socket = new ZMQConfig("tcp://127.0.0.1:5555").getSocket();

    for(Object deviceObj : devicesList){

      JsonObject device = (JsonObject) deviceObj;
      device.put("event_name","poller").put("plugin_engine", "linux");
      String deviceId = String.valueOf(device.getInteger("id"));

      try {
        System.out.println("Sending request: " + device.toString());
        socket.send(device.toString().getBytes(ZMQ.CHARSET), 0);
        byte[] reply = socket.recv(0);
        String jsonResponse = new String(reply, ZMQ.CHARSET);
        JsonObject counterObject = new JsonObject();

        for(Object object: new JsonObject(jsonResponse).getJsonArray("metrics")){
          JsonObject jsonObject = (JsonObject) object;
          counterObject.put(jsonObject.getString("name"),jsonObject.getString("value"));
        }

        pollerService.saveCounter(counterObject);
//        writeJsonToFile("linux_"+deviceId+".json", jsonResponse);
        System.out.println("Received response for device: "+deviceId);
      } catch (Exception e) {
        System.out.println("ZMQ communication failed for device " + deviceId + " " + e.getMessage());
      }
    }
    message.reply("Polling completed");
  }

  private static void writeJsonToFile(String fileName, String jsonData) throws IOException {
    try (FileWriter file = new FileWriter(fileName)) {
      file.write(jsonData);
      System.out.println("Response written to file: " + fileName);
    }
  }

}
