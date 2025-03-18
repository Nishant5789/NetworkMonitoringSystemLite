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

  import java.util.HashMap;
  import java.util.Map;
  import java.util.UUID;
  import java.util.logging.Logger;

  import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

  public class Poller extends AbstractVerticle
  {
    private static final Logger LOGGER = AppLogger.getLogger();

    private static final Map<String, Integer> pendingRequests = new HashMap<>();

    private static final ZMQ.Socket dealer = ZMQConfig.getDealerSocket();

    @Override
    public void start(Promise<Void> startPromise) throws Exception
    {
      LOGGER.info("Polling service deployed: " + Thread.currentThread().getName());

      vertx.eventBus().localConsumer(POLLING_EVENT, this::handlePolling);

      vertx.setPeriodic(2000, id ->
      {
        handlePollingResponce(dealer);
      });

      startPromise.complete();
    }

    // handle polling
    private void handlePolling(Message<Object> message)
    {
      var objectsList = (JsonArray) message.body();

      handlePollingResponce(dealer);

      for (Object ObjectObj : objectsList)
      {
        var object = (JsonObject) ObjectObj;
        var deviceType = object.getString(PLUGIN_ENGINE_TYPE_KEY);

        if (deviceType.contains("linux"))
        {
          handleLinuxPollingData(object, dealer);
        }
      }
      message.reply("Polling completed");
    }

    // handle polling for linux
    private static void handleLinuxPollingData(JsonObject payload, ZMQ.Socket dealer)
    {
      payload.put(EVENT_NAME_KEY, POLLING_EVENT);
      var objectId = payload.getInteger(OBJECT_ID_KEY);

      try
      {
        LOGGER.info("Sending request: " + payload.toString());

        String requestId =  UUID.randomUUID().toString();
        payload.put(REQUEST_ID, requestId);
        pendingRequests.put(requestId, objectId);

        dealer.send("", ZMQ.SNDMORE);
        dealer.send(payload.toString());
      }
      catch (Exception e)
      {
        LOGGER.severe("ZMQ communication failed for object " + objectId + ": " + e.getMessage());
      }
    }

    // handle waiting for responce
    private static void handlePollingResponce(ZMQ.Socket dealer)
    {
      String response;

      while ((response = dealer.recvStr(ZMQ.DONTWAIT)) != null)
      {
        if (response.trim().isEmpty())
          continue;

        try
        {
          var jsonResponse  = new JsonObject(response);

          var requestId = jsonResponse.getString(REQUEST_ID);

          if (pendingRequests.containsKey(requestId))
          {
            var objectId = pendingRequests.get(requestId);
            pendingRequests.remove(requestId);

            LOGGER.info(Thread.currentThread() + "is receive Responce");

            JsonObject pollResponsePayload = null;

            if(jsonResponse.getString("status").equals("failed"))
            {
              pollResponsePayload = new JsonObject().put(TIMESTAMP_KEY, System.currentTimeMillis()).put(COUNTERS_KEY, jsonResponse.getString("statusMsg"));
            }
            else
            {
              var counterObjects = new JsonObject();
              for (var counterObj : jsonResponse.getJsonArray("data"))
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
                return QueryHandler.updateByField (PROVISIONED_OBJECTS_TABLE, new JsonObject().put(LAST_POLL_TIME_KEY, updatePollTime), OBJECT_ID_KEY, objectId);
              })
              .onSuccess(updateRes ->
              {
                LOGGER.info("Polling data dumped to DB for objectId: " + objectId);
                LOGGER.info("Update successful for Object ID: " + objectId);
              })
              .onFailure(err ->
              {
                LOGGER.severe("Failed during polling flow for objectId: " + objectId + " Error: " + err.getMessage());
              });
            LOGGER.info("Received response for object: " + objectId);
          }
          else
          {
            LOGGER.warning("No pending request found for request_id: " + requestId);
          }
        }
        catch (Exception e)
        {
          LOGGER.severe("Failed to parse response as JSON: "+ e.getMessage() +" from plugin");
        }
      }
    }
  }
