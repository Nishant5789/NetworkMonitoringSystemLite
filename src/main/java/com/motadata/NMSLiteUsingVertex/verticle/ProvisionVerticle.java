package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class ProvisionVerticle extends AbstractVerticle
{
  private static final Logger logger = LoggerFactory.getLogger(ProvisionVerticle.class);

  public static final Queue<JsonObject> deviceQueue = new ConcurrentLinkedQueue<>();

  @Override
  public void start(Promise<Void> startPromise) throws Exception
  {
    logger.info("Device Verticle deployed: {}", Thread.currentThread().getName());

    vertx.eventBus().consumer(PROVISION_EVENT, this::provision);

    vertx.eventBus().consumer(GET_POLLING_DATA_EVENT,this::getPollerResult);

    vertx.eventBus().consumer(DELETE_DEVICE_EVENT,this::handledeleteDevice);

    startPromise.complete();
  }

  // handle provisioning
  private void provision(Message<Object> message)
  {
    JsonObject payload = (JsonObject) message.body();

    String discoveryId = String.valueOf(payload.getInteger(DISCOVERY_ID_KEY));

    String  pollInterval = String.valueOf(payload.getInteger(POLL_INTERVAL_KEY));

    QueryHandler.getDeviceByDiscoveryId(discoveryId)
      .onSuccess(discoveryRecord->
      {
        if (discoveryRecord == null)
        {
          logger.info("Discovery is not found in database");
          message.reply(Utils.createResponse("failed", "discovery is not found"));
          return;
        }

        String discoveryStatus = discoveryRecord.getString("status");

        if (discoveryStatus.equals("false"))
        {
          message.reply(Utils.createResponse("failed", "device is not discovered"));
          return;
        }

        JsonObject device = new JsonObject(String.valueOf(discoveryRecord));
        device.put(EVENT_NAME_KEY, LINUX_PLUGIN_ENGINE);
        device.put(PORT_KEY,String.valueOf(device.getInteger(PORT_KEY)));

        deviceQueue.add(device.put("lastPollTime", System.currentTimeMillis()));

        logger.info("Device's ip: {} added in deviceQueue", device.getString(IP_KEY));

        message.reply(Utils.createResponse("success", "Polling is started for provisioned device"));
        handleDeviceScheduling(Integer.parseInt(pollInterval));
    })
      .onFailure(err->
      {
        logger.warn("database query failed");
        message.reply(Utils.createResponse("error", err.getMessage()));
      });
  }

  // schedule device polling
  private void handleDeviceScheduling(int pollInterval)
  {
    Main.vertx().setTimer(5000,timeId->
    {
      logger.info("Polling is started, deviceQueue: {}", deviceQueue);

      var currentTime = System.currentTimeMillis();

      JsonArray devicesToPoll = new JsonArray();

      for (JsonObject device : deviceQueue)
      {
        long lastPollTime = device.getLong(LAST_POLL_TIME_KEY);

        long timeSinceLastPoll = currentTime - lastPollTime;

        if (timeSinceLastPoll >= pollInterval)
        {
          // Update last poll time
          device.put("lastPollTime", currentTime);

          devicesToPoll.add(device);

          logger.info("Devices sent for polling: {}", devicesToPoll.encodePrettily());

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
        logger.error("Failed to run polling");
      }
    });
  }

  // handle delete device
  private void handledeleteDevice(Message<Object> message)
  {
    String monitorDeviceId = (String) message.body();

    QueryHandler.deleteById(monitorDeviceId,DISCOVERY_TABLE)
      .onSuccess(deleted -> {
        if (deleted)
        {
          logger.info("Record deleted successfully");

          message.reply(new JsonObject().put("status", "success").put("statusMsg", "device deleted Sucessfully"));
        }
        else
        {
          logger.info("No matching record found");
          message.reply(new JsonObject().put("status", "success").put("statusMsg", "No matching record found"));
        }
      })
      .onFailure(err ->
      {
        message.reply(new JsonObject().put("status", "failed").put("statusMsg", "Database query failed" + err.getMessage()));
      });
  }

  // get polling data from device id
  private void getPollerResult(Message<Object> message)
  {
    String discoveryId = (String) message.body();

    QueryHandler.getPollerData(discoveryId)
      .onSuccess(metricsResult ->
      {
        message.reply(metricsResult);
      })
      .onFailure(err->
      {
        logger.error("Failed to fetch device data for discoveryId {}: {}", discoveryId, err.getMessage());

        message.reply(new JsonObject().put("status", "failed").put("statusMsg", "Database query failed" + err.getMessage()));
      });
  }
}
