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

public class DiscoveryVerticle extends AbstractVerticle
{
  private static final Logger logger = LoggerFactory.getLogger(DiscoveryVerticle.class);

  @Override
  public void start()
  {
    logger.info("Discovery Verticle deployed: {}", Thread.currentThread().getName());

    vertx.eventBus().consumer(DISCOVERY_EVENT, this::discovery);
  }

  // handle discovery event
  private void discovery(Message<Object> message)
  {
    JsonObject payload = (JsonObject) message.body();

    var credentialId = payload.getString(CREDENTIAL_ID_KEY);
    var ip = payload.getString(IP_KEY);
    var port = String.valueOf(payload.getInteger(PORT_KEY));
    var deviceType = payload.getString(DEVICE_TYPE_KEY);
    var discoveryId =  String.valueOf(payload.getInteger(ID_KEY));

    Utils.checkDeviceAvailability(ip, port)
      .onSuccess(flag ->
      {
        QueryHandler.findById(CREDENTIAL_TABLE, credentialId)
          .onSuccess(credential ->
          {
            if(credential == null)
            {
              message.reply(Utils.createResponse("failed", "Credential not found"));
            }
            else
            {
              vertx.executeBlocking(promise ->
              {
                JsonObject discoveryPayload =  new JsonObject();

                discoveryPayload
                  .put(USERNAME_KEY, credential.getString(USERNAME_KEY))
                  .put(PASSWORD_KEY, credential.getString(PASSWORD_KEY))
                  .put(IP_KEY,ip)
                  .put(PORT_KEY,port)
                  .put(EVENT_NAME_KEY, DISCOVERY_EVENT)
                  .put(PLUGIN_ENGINE_TYPE_KEY, deviceType);

                checkDiscovery(discoveryPayload).onSuccess(promise::complete).onFailure(promise::fail);
              }, result ->
              {
                if (result.succeeded())
                {
                  var updatePayload = new JsonObject().put(STATUS_KEY,TRUE_VALUE);
                  QueryHandler.updateByField(DISCOVERY_TABLE, updatePayload,"id", discoveryId)
                    .onSuccess(responce->
                    {
                      message.reply(new JsonObject(String.valueOf(result.result())));
                    })
                    .onFailure(err->
                    {
                      message.reply(Utils.createResponse("failed", "Failed to update object: " + err.getMessage()));
                    });
                }
                else
                {
                  logger.error("Error during discovery: ", result.cause());
                  message.reply(Utils.createResponse("failed", result.cause().getMessage()));
                }
              });
            }
          })
          .onFailure(err->
          {
            message.reply(Utils.createResponse("error", err.getMessage()));
          });
      })
      .onFailure(err->
      {
        logger.warn("Discovery failed for device IP: {} Port: {}", ip, port);
        message.reply(Utils.createResponse("error", err.getMessage()));
      }
      );
  }

  // handle discovery rechaility using plugin engine
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
        return Future.failedFuture(Utils.createResponse("failed", jsonResponse).encode());
      }

      return Future.succeededFuture(jsonResponse);
    }
    catch (Exception e)
    {
      return Future.failedFuture(Utils.createResponse("error", "ZMQ communication failed: " + e.getMessage()).encode());
    }
  }
}
