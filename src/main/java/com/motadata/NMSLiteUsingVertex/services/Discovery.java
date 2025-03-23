package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class Discovery extends AbstractVerticle
{
//  private static final Logger LOGGER = AppLogger.getLogger();
  private static final Logger LOGGER =  Logger.getLogger(Discovery.class.getName());

  @Override
  public void start()
  {
    LOGGER.info("Discovery service deployed: " + Thread.currentThread().getName());

    vertx.eventBus().<JsonObject>localConsumer(DISCOVERY_EVENT, this::discovery);
  }

  // handle discovery event
  private void discovery(Message<JsonObject> message)
  {
    var payload = message.body();

    var ip = payload.getString(IP_KEY);
    var credentialId = payload.getString(CREDENTIAL_ID_KEY);
    var port = String.valueOf(payload.getInteger(PORT_KEY));

    Utils.checkDeviceAvailability(ip, port)
      .compose(flag ->
      {
        if (!flag)
        {
          return Future.failedFuture("port is not available");
        }

        return QueryHandler.getById(CREDENTIAL_TABLE, credentialId);
      })
      .compose(credential ->
      {
        if (credential == null)
        {
          return Future.failedFuture("Credential not found");
        }

        var credentialDataPayload = new JsonObject(credential.getString(CREDENTIAL_DATA_KEY));
        var zmqReqPayload = new JsonObject().put(USERNAME_KEY, credentialDataPayload.getString(USERNAME_KEY)).put(PASSWORD_KEY, credentialDataPayload.getString(PASSWORD_KEY)).put(IP_KEY, ip).put(PORT_KEY, port).put(EVENT_NAME_KEY, DISCOVERY_EVENT).put(PLUGIN_ENGINE_TYPE_KEY, PLUGIN_ENGINE_LINUX);

         return Main.vertx().eventBus().<JsonObject>request(ZMQ_REQUEST_EVENT, zmqReqPayload);
      })
      .compose(jsonResponse ->
      {
        if(jsonResponse.body().getString(STATUS_KEY).equals(STATUS_RESPONSE_SUCCESS))
        {
          var updatePayload = new JsonObject().put(DISCOVERY_STATUS_KEY,"complete");

          return QueryHandler.updateByField(DISCOVERY_TABLE, updatePayload, DISCOVERY_ID_KEY, credentialId);
        }
        return Future.failedFuture("ssh connection is unsuccessful");
      })
      .onSuccess(response ->
      {
        message.reply("Discovery successfully completed");
      })
      .onFailure(err ->
      {
        LOGGER.warning("Error during discovery for IP: " + ip + " Port: "+ port +" "+ err.getMessage());

        message.reply("Discovery failed: "+ err.getMessage());
      });
  }
}
