package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.config.ZMQConfig;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.zeromq.ZMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class PollerVerticle extends AbstractVerticle
{
  private static final Logger logger = LoggerFactory.getLogger(PollerVerticle.class);

  @Override
  public void start(Promise<Void> startPromise) throws Exception
  {
    logger.info("Polling Verticle deployed: {}", Thread.currentThread().getName());

    vertx.eventBus().consumer(POLLING_EVENT, this::handlePolling);

    startPromise.complete();
  }

  // start polling
  private void handlePolling(Message<Object> message)
  {
    JsonArray devicesList = (JsonArray) message.body();

    ZMQ.Socket socket = new ZMQConfig("tcp://127.0.0.1:5555").getSocket();

    for(Object deviceObj : devicesList)
    {
      JsonObject device = (JsonObject) deviceObj;

      device.put(EVENT_NAME_KEY,POLLING_EVENT).put(PLUGIN_ENGINE_TYPE_KEY, LINUX_PLUGIN_ENGINE);

      String deviceId = device.getString(ID_KEY);

      try
      {
        logger.info("Sending request: {}", device.toString());

        socket.send(device.toString().getBytes(ZMQ.CHARSET), 0);

        byte[] reply = socket.recv(0);

        String jsonResponse = new String(reply, ZMQ.CHARSET);

        JsonObject counterObject = new JsonObject();

        for(Object object: new JsonObject(jsonResponse).getJsonArray("metrics"))
        {
          JsonObject jsonObject = (JsonObject) object;
          counterObject.put(jsonObject.getString("name"),jsonObject.getString("value"));
        }

        QueryHandler.saveAndGetById(LINUX_COUNTER_RESULT_TABLE, counterObject)
          .onSuccess(counterId->
          {
            QueryHandler.save(POLLLER_RESULT_TABLE, new JsonObject().put(COUNTER_ID_KEY,counterId).put(MONITOR_DEVICE_ID_KEY,deviceId).put(COUNTER_TYPE_KEY,LINUX_PLUGIN_ENGINE))
              .onSuccess(res->
              {
                logger.info("Polling data dumped to DB for deviceId: {}", deviceId);
              })
              .onFailure(err->
              {
                logger.error("Failed to save polling data for device {}: {}", deviceId, err.getMessage());
              });
          });

        logger.info("Received response for device: {}", deviceId);
      }
      catch (Exception e)
      {
        logger.error("ZMQ communication failed for device {}: {}", deviceId, e.getMessage());
      }
    }
    message.reply("Polling completed");
  }

  // handle Polling data dump to file
  private static void writeJsonToFile(String fileName, String jsonData) throws IOException
  {
    try (FileWriter file = new FileWriter(fileName))
    {
      file.write(jsonData);

      logger.info("Response written to file: {}", fileName);
    }
  }
}
