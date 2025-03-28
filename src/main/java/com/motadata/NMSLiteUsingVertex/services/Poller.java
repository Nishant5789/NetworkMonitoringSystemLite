package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
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
    private static final Logger LOGGER = AppLogger.getLogger();

    @Override
    public void start(Promise<Void> startPromise) throws Exception
    {
        LOGGER.info("Polling service deployed: " + Thread.currentThread().getName());

        vertx.eventBus().<JsonArray>localConsumer(POLLING_EVENT, this::handlePolling);

        vertx.eventBus().<JsonObject>localConsumer(POLLING_RESPONCE_EVENT, this::handleRecievePollingData);

        startPromise.complete();
    }

    // handle polling
    private void handlePolling(Message<JsonArray> message)
    {
        for (Object Object : message.body())
        {
            var objectPayload = (JsonObject) Object;

            var pluginEngineType = objectPayload.getString(PLUGIN_ENGINE_TYPE_KEY);

            objectPayload.put(EVENT_NAME_KEY, POLLING_EVENT);

            if (pluginEngineType.contains(PLUGIN_ENGINE_LINUX))
            {
                Main.vertx().eventBus().send(ZMQ_REQUEST_EVENT, objectPayload);
            }
        }
    }

    // handle receive Polling Data from ZMQ
    private void handleRecievePollingData(Message<JsonObject> Response)
    {
        var jsonResponse = Response.body();
        var objectId = jsonResponse.getInteger(OBJECT_ID_KEY);
        var ip = jsonResponse.getString(IP_KEY);

        var currTimestamp = System.currentTimeMillis();

        JsonObject pollResponsePayload;

        if (jsonResponse.getString(STATUS_KEY).equals(STATUS_RESPONSE_FAIIED))
        {
            Utils.incrementFailureCount(objectId);

            // if object is down then update status down
            if (Utils.isObjectStatusUP(objectId) && Utils.checkFailureThresholdExceeded(objectId))
            {
                Utils.updateStatusInObjectQueueAndDatabase(objectId, OBJECT_AVAILABILITY_DOWN);
            }
            pollResponsePayload = new JsonObject().put(IP_KEY, ip).put(TIMESTAMP_KEY, currTimestamp).put(COUNTERS_KEY, jsonResponse.getJsonObject(METRICS_DATA_KEY));
        }
        else
        {
            if (Utils.isObjectStatusDown(objectId))
            {
                Utils.resetFailureCount(objectId);

                Utils.updateStatusInObjectQueueAndDatabase(objectId, OBJECT_AVAILABILITY_UP);
            }
            var counterObjects = Utils.replaceUnderscoreWithDot(jsonResponse.getJsonObject(METRICS_DATA_KEY));

            pollResponsePayload = new JsonObject().put(IP_KEY, ip).put(TIMESTAMP_KEY, currTimestamp).put(COUNTERS_KEY, counterObjects);
        }

        // update lastPollTime in Object queue
        Utils.updateObjectLastPollTimeInObjectQueue(objectId, currTimestamp);

        // dumb polling data in database
        QueryHandler.save(POLLING_RESULTS_TABLE, pollResponsePayload)
        .onSuccess(responce ->
        {
            LOGGER.info("Polling completed for ObjectId: " + objectId + " at timestamp: " + Instant.ofEpochMilli(currTimestamp).atZone(ZoneId.systemDefault()).toLocalTime());
        })
        .onFailure(err ->
        {
            LOGGER.severe("Failed during polling flow for objectId: " + objectId + " Error: " + err.getMessage());
        });
    }
}
