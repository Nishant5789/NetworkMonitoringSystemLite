package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.config.ZMQConfig;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.zeromq.ZMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class PollerVerticle extends AbstractVerticle
{
  private static final Logger logger = LoggerFactory.getLogger(PollerVerticle.class);

  @Override
  public void start(Promise<Void> startPromise) throws Exception
  {
    logger.info("Polling Verticle deployed: {}", Thread.currentThread().getName());

    vertx.eventBus().consumer(POLLING_EVENT, this::handlePolling);

    startPromise.complete();
  }

  // start polling
  private void handlePolling(Message<Object> message)
  {
    JsonArray devicesList = (JsonArray) message.body();

    ZMQ.Socket socket = new ZMQConfig("tcp://127.0.0.1:5555").getSocket();

    for(Object deviceObj : devicesList)
    {
      JsonObject device = (JsonObject) deviceObj;

      String deviceType = device.getString(PLUGIN_ENGINE_TYPE_KEY);

      if(deviceType.contains("linux"))
      {
        handleLinuxPollingData(device, socket);
      }
      else
      {
        handleWindowsPollingData(device, socket);
      }
    }
    message.reply("Polling completed");
  }

  // handle linux device polling data
  private static void handleLinuxPollingData(JsonObject device, ZMQ.Socket socket)
  {
    device.put(EVENT_NAME_KEY,POLLING_EVENT).put(PLUGIN_ENGINE_TYPE_KEY, LINUX_PLUGIN_ENGINE);

    Integer deviceId = device.getInteger(ID_KEY);
    String deviceIp = device.getString(IP_KEY);

    try
    {
      logger.info("Sending request: {}", device.toString());

      socket.send(device.toString().getBytes(ZMQ.CHARSET), 0);

      byte[] reply = socket.recv(0);

      String jsonResponse = new String(reply, ZMQ.CHARSET);

      JsonObject counterObject = new JsonObject();

      for(Object object: new JsonObject(jsonResponse).getJsonArray("metrics"))
      {
        JsonObject jsonObject = (JsonObject) object;
        counterObject.put(jsonObject.getString("name"),jsonObject.getString("value"));
      }

      JsonObject pollResponcePayload = new JsonObject()
        .put("discovery_id", deviceId)
        .put("ip", deviceIp)
        .put("counter_result", counterObject);

      QueryHandler.save(POLLER_RESULTS_TABLE, pollResponcePayload)
        .onSuccess(res->
        {
          logger.info("Polling data dumped to DB for deviceId: {}", deviceId);
        })
        .onFailure(err->
        {
          logger.error("Failed to save polling data for device {}: {}", deviceId, err.getMessage());
        });

      logger.info("Received response for device: {}", deviceId);
    }
    catch (Exception e)
    {
      logger.error("ZMQ communication failed for device {}: {}", deviceId, e.getMessage());
    }
  }

  // handle windows device polling data
  private void handleWindowsPollingData(JsonObject device, ZMQ.Socket socket)
  {

    Integer deviceId = device.getInteger(ID_KEY);
    String deviceIp = device.getString(IP_KEY);
    JsonObject formatWindowsDevicePayload = Utils.formatWindowsPlugineEnginePayload(String.valueOf(deviceId), device.getString(USERNAME_KEY), device.getString(PASSWORD_KEY));

    try
    {
      logger.info("Sending request: {}", formatWindowsDevicePayload.toString());

      socket.send(formatWindowsDevicePayload.toString().getBytes(ZMQ.CHARSET), 0);

      byte[] reply = socket.recv(0);

      String jsonResponse = new String(reply, ZMQ.CHARSET);

      JsonObject counterObject = new JsonObject(jsonResponse);
      JsonObject formattedCounterObject = new JsonObject();

      counterObject.forEach(entry -> {
        String formattedKey =  Utils.windowsPollDataKeyFormatter(entry.getKey());
        formattedCounterObject.put(formattedKey, entry.getValue());
      });

      JsonObject pollResponcePayload = new JsonObject()
        .put("discovery_id", deviceId)
        .put("ip", deviceIp)
        .put("counter_result", counterObject);

      QueryHandler.save(POLLER_RESULTS_TABLE, pollResponcePayload)
        .onSuccess(res->
        {
          logger.info("Polling data dumped to DB for deviceId: {}", deviceId);
        })
        .onFailure(err->
        {
          logger.error("Failed to save polling data for device {}: {}", deviceId, err.getMessage());
        });

      logger.info("Received response for device: {}", deviceId);
    }
    catch (Exception e)
    {
      logger.error("ZMQ communication failed for device {}: {}", deviceId, e.getMessage());
    }
  }
}
