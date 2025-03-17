  package com.motadata.NMSLiteUsingVertex.services;

  import com.motadata.NMSLiteUsingVertex.config.ZMQConfig;
  import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
  import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
  import com.motadata.NMSLiteUsingVertex.utils.Utils;
  import io.vertx.core.AbstractVerticle;
  import io.vertx.core.Future;
  import io.vertx.core.Promise;
  import io.vertx.core.eventbus.Message;
  import io.vertx.core.json.JsonArray;
  import io.vertx.core.json.JsonObject;
  import org.zeromq.ZMQ;

  import java.util.List;
  import java.util.concurrent.CopyOnWriteArrayList;
  import java.util.logging.Logger;

  import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

  public class Poller extends AbstractVerticle
  {
    private static final Logger LOGGER = AppLogger.getLogger();
//    private static final Logger LOGGER =  Logger.getLogger(Poller.class.getName());

    @Override
    public void start(Promise<Void> startPromise) throws Exception
    {
      LOGGER.info("Polling service deployed: " + Thread.currentThread().getName());

      vertx.eventBus().localConsumer(POLLING_EVENT, this::handlePolling);

      vertx.eventBus().localConsumer(GET_POLLING_DATA_EVENT,this::getPollingData);

      startPromise.complete();
    }

    // handle polling
    private void handlePolling(Message<Object> message)
    {
      var objectsList = (JsonArray) message.body();
      ZMQ.Socket socket = new ZMQConfig().getSocket();

      for (Object ObjectObj : objectsList)
      {
        var object = (JsonObject) ObjectObj;
        var deviceType = object.getString(PLUGIN_ENGINE_TYPE_KEY);

        if (deviceType.contains("linux"))
        {
          handleLinuxPollingData(object, socket);
        }
        else
        {
          handleWindowsPollingData(object, socket);
        }
      }
      message.reply("Polling completed");
    }

    // handle polling for linux
    private static void handleLinuxPollingData(JsonObject object, ZMQ.Socket socket)
    {
      object.put(EVENT_NAME_KEY, POLLING_EVENT);
      var objectId = object.getInteger(OBJECT_ID_KEY);
      var monitorId = object.getInteger(MONITOR_ID_KEY);

      try
      {
        LOGGER.info("Sending request: " + object.toString());
        socket.send(object.toString().getBytes(ZMQ.CHARSET), 0);
        byte[] reply = socket.recv(0);
        var jsonResponse = new JsonObject(new String(reply, ZMQ.CHARSET));
        JsonObject pollResponsePayload = null;

        if(jsonResponse.getString("status").equals("failed"))
        {
          pollResponsePayload = new JsonObject().put(MONITOR_ID_KEY, monitorId).put(TIMESTAMP_KEY, System.currentTimeMillis()).put(COUNTERS_KEY, jsonResponse.getString("statusMsg"));
        }
        else
        {
            var counterObjects = new JsonObject();
            for (var counterObj : jsonResponse.getJsonArray("metrics"))
            {
               var jsonObject = (JsonObject) counterObj;
               counterObjects.put(jsonObject.getString("name"), jsonObject.getString("value"));
            }
            pollResponsePayload = new JsonObject().put(MONITOR_ID_KEY, monitorId).put(TIMESTAMP_KEY, System.currentTimeMillis()).put(COUNTERS_KEY, counterObjects);
        }

        // added into cache
        Utils.addPollingResponse(pollResponsePayload);

        QueryHandler.save(POLLING_RESULTS_TABLE, pollResponsePayload)
          .onSuccess(res -> LOGGER.info("Polling data dumped to DB for objectId: " + objectId))
          .onFailure(err -> LOGGER.severe("Failed to save polling data for object: " + objectId + ": " + err.getMessage()));

        LOGGER.info("Received response for object: " + objectId);
      }
      catch (Exception e)
      {
        LOGGER.severe("ZMQ communication failed for object " + objectId + ": " + e.getMessage());
      }
    }

    // get polling data from Object id
    private void getPollingData(Message<Object> message)
    {
      var objectId =  (String) message.body();

      var responseArray = Utils.getPollDataFromCache(objectId);

      if (responseArray != null)
      {
        message.reply(responseArray);
      }
      else
      {
        message.fail(404, "Polling data not found for objectId: " + objectId);
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
        LOGGER.info("Sending request: " + formatWindowsDevicePayload.toString());
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

        QueryHandler.save(POLLING_RESULTS_TABLE, pollResponsePayload)
          .onSuccess(res -> LOGGER.info("Polling data dumped to DB for deviceId: " + deviceId))
          .onFailure(err -> LOGGER.severe("Failed to save polling data for device " + deviceId + ": " + err.getMessage()));

        LOGGER.info("Received response for device: " + deviceId);
      }
      catch (Exception e)
      {
        LOGGER.severe("ZMQ communication failed for device " + deviceId + ": " + e.getMessage());
      }
    }
  }
