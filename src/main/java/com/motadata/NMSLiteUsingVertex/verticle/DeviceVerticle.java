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

import java.util.LinkedList;
import java.util.Queue;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class DeviceVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(DeviceVerticle.class);

  public static final Queue<JsonObject> deviceQueue = new LinkedList<>();

  @Override
  public void start(Promise<Void> startPromise) throws Exception
  {
    logger.info("Device Verticle deployed: {}", Thread.currentThread().getName());

    vertx.eventBus().consumer(PROVISION_EVENT, this::provision);

    vertx.eventBus().consumer(GET_POLLING_DATA_EVENT,this::getPollerResult);

    startPromise.complete();
  }

  private void getPollerResult(Message<Object> message)
  {

  }

  // handle provision
  private void provision(Message<Object> message)
  {
    JsonObject payload = (JsonObject) message.body();

    JsonArray deviceIds = payload.getJsonArray(DEVICE_ID_KEYS);

    String  pollInterval = payload.getString(POLL_INTERVAL_KEY);

    QueryHandler.getAllByIds(MONITOR_DEVICE_TABLE, deviceIds).onSuccess(devices->
    {
      for(JsonObject device : devices)
      {
        String isDiscovered = device.getString(IS_DISCOVERED_KEY);

        if(isDiscovered.equals("false"))
          continue;

        String ip = device.getString(IP_KEY);

        String port = device.getString(PORT_KEY);

        Utils.checkDeviceAvailability(ip,port)

          .onSuccess(flag->
          {
            if(flag){
              deviceQueue.add(device.put("lastPollTime",System.currentTimeMillis()));
              logger.info("Device's ip: {} added in deviceQueue", ip);
            }
            else {
              logger.warn("Device's ip: {} with Port: {} is not reachable", ip, port);
            }
          })

          .onFailure(err->
          {
            logger.error("Device {} not available: {}", ip, err.getMessage());
          });

      }

      handleDeviceScheduling(Integer.parseInt(pollInterval));

      message.reply(new JsonObject().put("message", "Polling is started for provisioned device"));

    }).onFailure(err -> {
      logger.error("Failed to fetch device data: {}", err.getMessage());
      message.fail(500, "Database query failed");
    });
  }

  // schedule device polling
  private void handleDeviceScheduling(int pollInterval)
  {
    Main.vertx().setPeriodic(4000,timeId->
    {

      logger.info("Polling is started, deviceQueue: {}", deviceQueue);

      long currentTime = System.currentTimeMillis();

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
      if(result.succeeded()){
        logger.info("Polling is completed");
      }
      else{
        logger.error("Failed to run polling");
      }
    });
  }
}
