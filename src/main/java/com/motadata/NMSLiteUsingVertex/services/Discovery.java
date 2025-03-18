package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.config.ZMQConfig;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;
import static com.motadata.NMSLiteUsingVertex.utils.Utils.formatInvalidResponse;

public class Discovery extends AbstractVerticle
{
  private static final Logger LOGGER = AppLogger.getLogger();

  @Override
  public void start()
  {
    LOGGER.info("Discovery service deployed: " + Thread.currentThread().getName());

    vertx.eventBus().consumer(DISCOVERY_EVENT, this::discovery);
  }

  // handle discovery event
  private void discovery(Message<Object> message)
  {
    JsonObject payload = (JsonObject) message.body();

    var credentialId = payload.getString(CREDENTIAL_ID_KEY);
    var ips =  payload.getString(IPS_KEY);
    var port = String.valueOf(payload.getInteger(PORT_KEY));
    var deviceType = payload.getString(OBJECT_TYPE_KEY);

    List<Future> allFutures = new ArrayList<>();

    for (Object ipObj : new JsonArray(ips))
    {
      var ip = (String) ipObj;

      Future<Void> future = Utils.checkDeviceAvailability(ip, port)
        .compose(flag ->
        {
          if (!flag)
          {
            return Future.failedFuture("object is not available");
          }
          return QueryHandler.getById(CREDENTIAL_TABLE, credentialId);
        })
        .compose(credential ->
        {
          if (credential == null)
          {
            return Future.failedFuture("Credential not found");
          }

          var discoveryPayload = new JsonObject()
            .put(USERNAME_KEY, credential.getString(USERNAME_KEY))
            .put(PASSWORD_KEY, credential.getString(PASSWORD_KEY))
            .put(IP_KEY, ip)
            .put(PORT_KEY, port)
            .put(EVENT_NAME_KEY, DISCOVERY_EVENT)
            .put(PLUGIN_ENGINE_TYPE_KEY, deviceType);

          return vertx.executeBlocking(promise ->
            checkDiscovery(discoveryPayload)
              .onSuccess(responce ->
              {
                discoveryPayload.remove(EVENT_NAME_KEY);
                promise.complete(discoveryPayload);
              })
              .onFailure(promise::fail)
          );
        })
        .compose(result ->
        {
          var provisionPayload = new JsonObject().put("object_data", result);

          return QueryHandler.save(PROVISIONED_OBJECTS_TABLE, provisionPayload);
        })
        .onSuccess(response -> LOGGER.info("provisioning entry is created for object IP: " + ip))
        .onFailure(err -> LOGGER.warning("Error during discovery for IP: " + ip + " -> " + err.getMessage()));

      allFutures.add(future);
    }

    CompositeFuture.join(allFutures)
      .onComplete(result ->
      {
        if (result.succeeded())
        {
          message.reply("Discovery completed for all IPs");
        }
        else
        {
          message.reply("Some discoveries failed");
        }
      });
  }

  // handle discovery reachability using plugin engine
  private Future<String> checkDiscovery(JsonObject requestJson)
  {
    try
    {
      ZMQ.Socket socket = new ZMQConfig().getSocket();

      LOGGER.info("Sending request: " + requestJson.toString());

      socket.send(requestJson.toString().getBytes(ZMQ.CHARSET), 0);

      byte[] reply = socket.recv(0);

      var jsonResponse = new String(reply, ZMQ.CHARSET);

      LOGGER.info("Received response:\n" + jsonResponse);

      var responseObject = new JsonObject(jsonResponse);

      if(responseObject.getString("status").equals("failed"))
      {
        return Future.failedFuture(jsonResponse);
      }

      return Future.succeededFuture(jsonResponse);
    }
    catch (Exception e)
    {
      LOGGER.warning(e.getMessage());
      return Future.failedFuture("ZMQ communication failed");
    }
  }
}
