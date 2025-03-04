package com.motadata.NMSLiteUsingVertex.verticle;

import com.motadata.NMSLiteUsingVertex.config.ZMQConfig;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.zeromq.ZMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class DiscoveryVerticle extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(DiscoveryVerticle.class);

  @Override
  public void start()
  {
    logger.info("Discovery Verticle deployed: {}", Thread.currentThread().getName());

    vertx.eventBus().localConsumer(DISCOVERY_EVENT, this::discovery);
  }

  private void discovery(Message<Object> message)
  {
    JsonObject payload = (JsonObject) message.body();

    String credentialId = payload.getString(CREDENTIAL_ID_KEY);

    String ip = payload.getString(IP_KEY);

    String port = payload.getString(PORT_KEY);

    QueryHandler.saveAndGetById(MONITOR_DEVICE_TABLE,payload)
      .onSuccess(deviceId->
      {
        Utils.checkDeviceAvailability(ip,port)
          .onSuccess(flag ->
          {
            QueryHandler.findById(CREDENTIAL_TABLE, credentialId)
              .onSuccess(credential ->
              {
                if(credential == null)
                {
                  message.reply(new JsonObject().put("status", "failed").put("statusMsg","Credential not found"));
                }
                else
                {
                  vertx.executeBlocking(promise ->
                  {
                    JsonObject discoveryPayload =  new JsonObject(String.valueOf(payload));

                    discoveryPayload
                      .put(USERNAME_KEY, credential.getString(USERNAME_KEY))
                      .put(PASSWORD_KEY, credential.getString(PASSWORD_KEY))
                      .put(EVENT_NAME_KEY, DISCOVERY_EVENT)
                      .put(PLUGIN_ENGINE_TYPE_KEY, LINUX_PLUGIN_ENGINE);

                    checkDiscovery(discoveryPayload).onSuccess(promise::complete).onFailure(promise::fail);

                  }, result ->
                  {
                    if (result.succeeded())
                    {
                      QueryHandler.updateByField(MONITOR_DEVICE_TABLE,payload.put(IS_DISCOVERED_KEY,TRUE_VALUE),"id = $6", deviceId)
                        .onSuccess(responce->
                        {
                          message.reply(new JsonObject(String.valueOf(result.result())));
                        })
                        .onFailure(err->
                        {
                          message.reply(new JsonObject().put("status", "failed").put("statusMsg", "failed to added object" + err.getMessage()));
                        });
                    }
                    else
                    {
                      logger.error("Error during discovery: ", result.cause());

                      message.reply(new JsonObject().put("status", "failed").put("statusMsg",result.cause()));
                    }
                  });
                }
              })
              .onFailure(err->
              {
                message.reply(new JsonObject().put("status", "error").put("statusMsg",err.getMessage()));
              });
          })
          .onFailure(err->
          {
            logger.warn("Discovery failed for device IP: {} Port: {}", ip, port);

            message.reply(new JsonObject().put("status", "error").put("statusMsg",err.getMessage()));
          });

        logger.info("Device is saved and starting discovery run");
      });
  }

  private Future<String> checkDiscovery(JsonObject requestJson)
  {
    try
    {
      ZMQ.Socket socket = new ZMQConfig("tcp://127.0.0.1:5555").getSocket();

      logger.info("Sending request: {}", requestJson.toString());

      socket.send(requestJson.toString().getBytes(ZMQ.CHARSET), 0);

      byte[] reply = socket.recv(0);

      String jsonResponse = new String(reply, ZMQ.CHARSET);

      logger.info("Received response:\n{}", jsonResponse);

      JsonObject responceObject = new JsonObject(jsonResponse);

      if(responceObject.getString("status").equals("failed"))
      {
        return Future.failedFuture(jsonResponse);
      }

      return Future.succeededFuture(jsonResponse);
    }
    catch (Exception e)
    {
      return Future.failedFuture("ZMQ communication failed: " + e.getMessage());
    }
  }
}
