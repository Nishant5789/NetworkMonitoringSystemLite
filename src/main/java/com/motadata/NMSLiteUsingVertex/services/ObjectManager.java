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


import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class ObjectManager extends AbstractVerticle
{
//  private static final Logger LOGGER = AppLogger.getLogger();
  private static final Logger LOGGER =  Logger.getLogger(ObjectManager.class.getName());


  @Override
  public void start(Promise<Void> startPromise) throws Exception
  {
    LOGGER.info("Object services deployed: " + Thread.currentThread().getName());

    vertx.eventBus().localConsumer(PROVISION_EVENT, this::provision);

    // start object scheduling
    handleObjectScheduling();
    startPromise.complete();
  }

  // handle provisioning
  private void provision(Message<Object> message)
  {
    var payload = (JsonObject) message.body();

    var object_id = payload.getInteger(OBJECT_ID_KEY);

    var pollInterval = payload.getInteger(POLL_INTERVAL_KEY);

    QueryHandler.getById(PROVISIONED_OBJECTS_TABLE ,object_id.toString())
      .onSuccess(provisionRecord->
      {
        if (provisionRecord == null)
        {
          LOGGER.info("provision details  is not found for this object_id in database");
          message.reply(Utils.createResponse("failed", "provision details  is not found for this object_id in database"));
          return;
        }

        var objectId = provisionRecord.getInteger(OBJECT_ID_KEY);
        var object = provisionRecord.getJsonObject("object_data").put(OBJECT_ID_KEY,objectId).put(LAST_POLL_TIME_KEY, System.currentTimeMillis()).put(POLL_INTERVAL_KEY, pollInterval);

        Utils.addObjectInQueue(object);

        var updatePayload = new JsonObject().put(LAST_POLL_TIME_KEY, System.currentTimeMillis()).put(POLL_INTERVAL_KEY, pollInterval);

        QueryHandler.updateByField(PROVISIONED_OBJECTS_TABLE, updatePayload, OBJECT_ID_KEY, objectId)
          .onComplete(result ->
          {
            if (result.succeeded())
            {
              LOGGER.info("Update successful for Object ID: " + object.getInteger(OBJECT_ID_KEY));
            }
            else
            {
              LOGGER.warning("Update failed: " + result.cause().getMessage());
            }
          });

        LOGGER.info("Device's ip: " + object.getString(IP_KEY) + " added in objectQueue");

        var provisionUpdatePayload = new JsonObject().put(PROVISIONING_STATUS_KEY,"active");

        QueryHandler.updateByField(PROVISIONED_OBJECTS_TABLE, provisionUpdatePayload, OBJECT_ID_KEY, objectId)
            .onSuccess(res -> message.reply(Utils.createResponse("success", "Polling is started for provisioned device")))
            .onFailure(err -> message.reply(err.getMessage()));
      })
      .onFailure(err ->
      {
        LOGGER.warning("database query failed");
        message.reply(Utils.createResponse("error", err.getMessage()));
      });
  }

  // schedule object polling
  private void handleObjectScheduling()
  {
    Main.vertx().setPeriodic(2000,timeId ->
    {
      LOGGER.info("Polling is started, objectQueue: " + Utils.getObjectQueue());

      var currentTime = System.currentTimeMillis();

      var objectToPoll = new JsonArray();

      for (JsonObject object : Utils.getObjectQueue())
      {
        var lastPollTime = object.getLong(LAST_POLL_TIME_KEY);

        var timeSinceLastPoll = currentTime - lastPollTime;

        if (timeSinceLastPoll >= object.getInteger(POLL_INTERVAL_KEY))
        {
          objectToPoll.add(object);

          LOGGER.info("Object sent for polling: " + objectToPoll.encodePrettily());

          handleDevicePolling(objectToPoll);
        }
      }
    });
  }

  // handle device polling
  private void handleDevicePolling(JsonArray objectToPoll)
  {
    Main.vertx().eventBus().request(POLLING_EVENT, objectToPoll, result->
    {
      if(result.succeeded())
      {
        LOGGER.info("Polling is completed");
      }
      else
      {
        LOGGER.severe("Failed to run polling");
      }
    });
  }
}
