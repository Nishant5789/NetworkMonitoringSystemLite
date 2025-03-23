package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.time.ZoneId;
import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class Poller extends AbstractVerticle
{
  //    private static final Logger LOGGER = AppLogger.getLogger();
  private static final Logger LOGGER = Logger.getLogger(ObjectManager.class.getName());

  @Override
  public void start(Promise<Void> startPromise) throws Exception
  {
    LOGGER.info("Polling service deployed: " + Thread.currentThread().getName());

    vertx.eventBus().<JsonArray>localConsumer(POLLING_EVENT, this::handlePolling);

    startPromise.complete();
  }

  // handle polling
  private void handlePolling(Message<JsonArray> message)
  {
    var objectsList = message.body();

    for (Object Object : objectsList)
    {
      var objectPayload = (JsonObject) Object;
      var pluginEngineType = objectPayload.getString(PLUGIN_ENGINE_TYPE_KEY);
      var objectId = objectPayload.getInteger(OBJECT_ID_KEY);

      objectPayload.put(EVENT_NAME_KEY, POLLING_EVENT);

      if (pluginEngineType.contains(PLUGIN_ENGINE_LINUX))
      {
        Main.vertx().eventBus().<JsonObject>request(ZMQ_REQUEST_EVENT, objectPayload)
          .onSuccess(result ->
          {
            var jsonResponse = result.body();
            JsonObject pollResponsePayload;

            if (jsonResponse.getString(STATUS_KEY).equals(STATUS_RESPONSE_FAIIED))
            {
              pollResponsePayload = new JsonObject().put(TIMESTAMP_KEY, System.currentTimeMillis()).put(COUNTERS_KEY, jsonResponse.getString(STATUS_MSG_KEY));
            }
            else
            {
              var currTimestamp = System.currentTimeMillis();

              // uddate lastpolltime in Object queue
              Utils.updateObjectLastPollTimeInObjectQueue(objectId, currTimestamp);

              var counterObjects = Utils.replaceUnderscoreWithDot(jsonResponse.getJsonObject(METRICS_DATA_KEY));

              pollResponsePayload = new JsonObject().put(OBJECT_ID_KEY, objectId).put(TIMESTAMP_KEY,currTimestamp).put(COUNTERS_KEY, counterObjects);

              QueryHandler.save(POLLING_RESULTS_TABLE, pollResponsePayload)
                .onSuccess(responce ->
                {
                  LOGGER.info("Polling completed for ObjectId: " + objectId +" at timestamp: " + Instant.ofEpochMilli(currTimestamp).atZone(ZoneId.systemDefault()).toLocalTime());
                })
                .onFailure(err ->
                {
                  LOGGER.severe("Failed during polling flow for objectId: " + objectId + " Error: " + err.getMessage());
                });
            }
          })
          .onFailure(err ->
          {
            LOGGER.severe("Polling failure for ObjectId: " + objectId + " due to: " + err.getMessage());
          });
      }
    }
  }
}
