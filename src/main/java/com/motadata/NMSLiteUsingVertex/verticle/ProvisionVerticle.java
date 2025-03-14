package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class ProvisionVerticle extends AbstractVerticle
{
  private static final Logger logger = AppLogger.getLogger();

  public static final Queue<JsonObject> deviceQueue = new ConcurrentLinkedQueue<>();

  @Override
  public void start(Promise<Void> startPromise) throws Exception
  {
    logger.info("Device Verticle deployed: " + Thread.currentThread().getName());

    vertx.eventBus().consumer(PROVISION_EVENT, this::provision);

    vertx.eventBus().consumer(GET_POLLING_DATA_EVENT,this::getPollerResult);

    startPromise.complete();
  }

  // handle provisioning
  private void provision(Message<Object> message)
  {
    var payload = (JsonObject) message.body();

    var discoveryId = String.valueOf(payload.getInteger(DISCOVERY_ID_KEY));

    var  pollInterval = String.valueOf(payload.getInteger(POLL_INTERVAL_KEY));

    QueryHandler.getDeviceByDiscoveryId(discoveryId)
      .onSuccess(discoveryRecord->
      {
        if (discoveryRecord == null)
        {
          logger.info("Discovery is not found in database");
          message.reply(Utils.createResponse("failed", "discovery is not found"));
          return;
        }

        var discoveryStatus = discoveryRecord.getString("status");

        if (discoveryStatus.equals("false"))
        {
          message.reply(Utils.createResponse("failed", "device is not discovered"));
          return;
        }

        var device = new JsonObject(String.valueOf(discoveryRecord));
        device.put(EVENT_NAME_KEY, LINUX_PLUGIN_ENGINE);
        device.put(PORT_KEY,String.valueOf(device.getInteger(PORT_KEY)));

        deviceQueue.add(device.put("lastPollTime", System.currentTimeMillis()));

        logger.info("Device's ip: " + device.getString(IP_KEY) + " added in deviceQueue");

        message.reply(Utils.createResponse("success", "Polling is started for provisioned device"));
        handleDeviceScheduling(Integer.parseInt(pollInterval));
      })
      .onFailure(err->
      {
        logger.warning("database query failed");
        message.reply(Utils.createResponse("error", err.getMessage()));
      });
  }

  // schedule device polling
  private void handleDeviceScheduling(int pollInterval)
  {
    Main.vertx().setTimer(5000,timeId->
    {
      logger.info("Polling is started, deviceQueue: " + deviceQueue);

      var currentTime = System.currentTimeMillis();

      var devicesToPoll = new JsonArray();

      for (JsonObject device : deviceQueue)
      {
        var lastPollTime = device.getLong(LAST_POLL_TIME_KEY);

        var timeSinceLastPoll = currentTime - lastPollTime;

        if (timeSinceLastPoll >= pollInterval)
        {
          // Update last poll time
          device.put("lastPollTime", currentTime);

          devicesToPoll.add(device);

          logger.info("Devices sent for polling: " + devicesToPoll.encodePrettily());

          handleDevicePolling(devicesToPoll);
        }
      }
    });
  }

  // handle device polling
  private void handleDevicePolling(JsonArray devicesToPoll)
  {
    Main.vertx().eventBus().request(POLLING_EVENT, devicesToPoll, result->
    {
      if(result.succeeded())
      {
        logger.info("Polling is completed");
      }
      else
      {
        logger.severe("Failed to run polling");
      }
    });
  }

  // get polling data from device id
  private void getPollerResult(Message<Object> message)
  {
    var discoveryId = (String) message.body();

    QueryHandler.getPollerData(discoveryId)
      .onSuccess(metricsResult ->
      {
        message.reply(metricsResult);
      })
      .onFailure(err->
      {
        logger.severe("Failed to fetch device data for discoveryId " + discoveryId + ": " + err.getMessage());

        message.reply(new JsonObject().put("status", "failed").put("statusMsg", "Database query failed" + err.getMessage()));
      });
  }
}
