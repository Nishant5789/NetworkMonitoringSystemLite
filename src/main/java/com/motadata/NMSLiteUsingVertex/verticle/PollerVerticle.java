package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.config.ZMQConfig;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.zeromq.ZMQ;

import java.util.logging.Logger;
import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class PollerVerticle extends AbstractVerticle
{
  private static final Logger logger = AppLogger.getLogger();

  @Override
  public void start(Promise<Void> startPromise) throws Exception
  {
    logger.info("Polling Verticle deployed: " + Thread.currentThread().getName());
    vertx.eventBus().consumer(POLLING_EVENT, this::handlePolling);
    startPromise.complete();
  }

  private void handlePolling(Message<Object> message)
  {
    var devicesList = (JsonArray) message.body();
    ZMQ.Socket socket = new ZMQConfig("tcp://127.0.0.1:5555").getSocket();

    for (Object deviceObj : devicesList)
    {
      var device = (JsonObject) deviceObj;
      var deviceType = device.getString(PLUGIN_ENGINE_TYPE_KEY);

      if (deviceType.contains("linux"))
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

  private static void handleLinuxPollingData(JsonObject device, ZMQ.Socket socket)
  {
    device.put(EVENT_NAME_KEY, POLLING_EVENT).put(PLUGIN_ENGINE_TYPE_KEY, LINUX_PLUGIN_ENGINE);
    var deviceId = device.getInteger(ID_KEY);
    var deviceIp = device.getString(IP_KEY);

    try
    {
      logger.info("Sending request: " + device.toString());
      socket.send(device.toString().getBytes(ZMQ.CHARSET), 0);
      byte[] reply = socket.recv(0);
      var jsonResponse = new String(reply, ZMQ.CHARSET);
      var counterObject = new JsonObject();

      for (var object : new JsonObject(jsonResponse).getJsonArray("metrics"))
      {
        var jsonObject = (JsonObject) object;
        counterObject.put(jsonObject.getString("name"), jsonObject.getString("value"));
      }

      var pollResponsePayload = new JsonObject()
        .put("discovery_id", deviceId)
        .put("ip", deviceIp)
        .put("counter_result", counterObject);

      QueryHandler.save(POLLER_RESULTS_TABLE, pollResponsePayload)
        .onSuccess(res -> logger.info("Polling data dumped to DB for deviceId: " + deviceId))
        .onFailure(err -> logger.severe("Failed to save polling data for device " + deviceId + ": " + err.getMessage()));

      logger.info("Received response for device: " + deviceId);
    }
    catch (Exception e)
    {
      logger.severe("ZMQ communication failed for device " + deviceId + ": " + e.getMessage());
    }
  }

  private void handleWindowsPollingData(JsonObject device, ZMQ.Socket socket)
  {
    var deviceId = device.getInteger(ID_KEY);
    var deviceIp = device.getString(IP_KEY);

    JsonObject formatWindowsDevicePayload = Utils.formatWindowsPlugineEnginePayload(
      String.valueOf(deviceId),
      device.getString(USERNAME_KEY),
      device.getString(PASSWORD_KEY)
    );

    try
    {
      logger.info("Sending request: " + formatWindowsDevicePayload.toString());
      socket.send(formatWindowsDevicePayload.toString().getBytes(ZMQ.CHARSET), 0);
      byte[] reply = socket.recv(0);
      var jsonResponse = new String(reply, ZMQ.CHARSET);
      var counterObject = new JsonObject(jsonResponse);
      var formattedCounterObject = new JsonObject();

      counterObject.forEach(entry ->
      {
        String formattedKey = Utils.windowsPollDataKeyFormatter(entry.getKey());
        formattedCounterObject.put(formattedKey, entry.getValue());
      });

      var pollResponsePayload = new JsonObject()
        .put("discovery_id", deviceId)
        .put("ip", deviceIp)
        .put("counter_result", counterObject);

      QueryHandler.save(POLLER_RESULTS_TABLE, pollResponsePayload)
        .onSuccess(res -> logger.info("Polling data dumped to DB for deviceId: " + deviceId))
        .onFailure(err -> logger.severe("Failed to save polling data for device " + deviceId + ": " + err.getMessage()));

      logger.info("Received response for device: " + deviceId);
    }
    catch (Exception e)
    {
      logger.severe("ZMQ communication failed for device " + deviceId + ": " + e.getMessage());
    }
  }
}
