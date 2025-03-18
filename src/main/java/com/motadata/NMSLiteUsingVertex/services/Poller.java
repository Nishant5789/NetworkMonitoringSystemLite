  package com.motadata.NMSLiteUsingVertex.services;

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

  public class Poller extends AbstractVerticle
  {
    private static final Logger LOGGER = AppLogger.getLogger();

    @Override
    public void start(Promise<Void> startPromise) throws Exception
    {
      LOGGER.info("Polling service deployed: " + Thread.currentThread().getName());

      vertx.eventBus().localConsumer(POLLING_EVENT, this::handlePolling);

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
      }
      message.reply("Polling completed");
    }

    // handle polling for linux
    private static void handleLinuxPollingData(JsonObject object, ZMQ.Socket socket)
    {
      object.put(EVENT_NAME_KEY, POLLING_EVENT);
      var objectId = object.getInteger(OBJECT_ID_KEY);

      try
      {
        LOGGER.info("Sending request: " + object.toString());

        socket.send(object.toString().getBytes(ZMQ.CHARSET), 0);

        byte[] reply = socket.recv(0);

        var jsonResponse = new JsonObject(new String(reply, ZMQ.CHARSET));

        JsonObject pollResponsePayload = null;

        if(jsonResponse.getString("status").equals("failed"))
        {
          pollResponsePayload = new JsonObject().put(TIMESTAMP_KEY, System.currentTimeMillis()).put(COUNTERS_KEY, jsonResponse.getString("statusMsg"));
        }
        else
        {
            var counterObjects = new JsonObject();
            for (var counterObj : jsonResponse.getJsonArray("metrics"))
            {
               var jsonObject = (JsonObject) counterObj;
               counterObjects.put(jsonObject.getString("name"), jsonObject.getString("value"));
            }
            pollResponsePayload = new JsonObject().put(OBJECT_ID_KEY,objectId).put(TIMESTAMP_KEY, System.currentTimeMillis()).put(COUNTERS_KEY, counterObjects);
        }
        var updatePollTime = System.currentTimeMillis();

        // added into polling cache
        Utils.addPollingResponse(pollResponsePayload);

        // update in objectqueue
        Utils.updateObjectLastPollTimeInObjectQueue(objectId, updatePollTime);

        // save polldata & update lastpolltime
        QueryHandler.save(POLLING_RESULTS_TABLE, pollResponsePayload)
          .compose(res ->
          {
            // Poll data saved successfully, now update LAST_POLL_TIME
            return QueryHandler.updateByField (PROVISIONED_OBJECTS_TABLE, new JsonObject().put(LAST_POLL_TIME_KEY, updatePollTime), OBJECT_ID_KEY, object.getInteger(OBJECT_ID_KEY));})

          .onSuccess(updateRes ->
          {
            LOGGER.info("Polling data dumped to DB for objectId: " + objectId);
            LOGGER.info("Update successful for Object ID: " + object.getInteger(OBJECT_ID_KEY));
          })
          .onFailure(err ->
          {
            LOGGER.severe("Failed during polling flow for objectId: " + objectId + " Error: " + err.getMessage());
          });

        LOGGER.info("Received response for object: " + objectId);
      }
      catch (Exception e)
      {
        LOGGER.severe("ZMQ communication failed for object " + objectId + ": " + e.getMessage());
      }
    }
  }
