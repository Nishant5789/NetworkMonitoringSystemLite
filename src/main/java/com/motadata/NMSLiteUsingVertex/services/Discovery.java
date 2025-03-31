package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class Discovery extends AbstractVerticle
{
    private static final Logger LOGGER = AppLogger.getLogger();

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
        var port = payload.getInteger(PORT_KEY);

        Utils.ping(ip).compose(PingReachability ->
        {
            if (PingReachability)
            {
                return Utils.checkPort(ip, port);
            }
            else
            {
                return Future.failedFuture("ping is unsuccessful for iP: " + ip);
            }
        })
        .compose(portAvailability ->
        {
            if (!portAvailability)
            {
                return Future.failedFuture("ssh service is not running or disable on provided port");
            }

            return Main.vertx().<JsonObject>executeBlocking(promise ->
            {
                 QueryHandler.getOneById(CREDENTIAL_TABLE, payload.getString(CREDENTIAL_ID_KEY))
                   .onSuccess(promise::complete)
                   .onFailure(promise::fail);
            });

        })
        .compose(credential ->
        {
            if (credential==null)
            {
                return Future.failedFuture("Credential not found");
            }

            var credentialDataPayload = new JsonObject(credential.getString(CREDENTIAL_DATA_KEY));

            var zmqReqPayload = new JsonObject().put(USERNAME_KEY, credentialDataPayload.getString(USERNAME_KEY)).put(PASSWORD_KEY, credentialDataPayload.getString(PASSWORD_KEY)).put(IP_KEY, ip).put(PORT_KEY, String.valueOf(port)).put(EVENT_NAME_KEY, DISCOVERY_EVENT).put(PLUGIN_ENGINE_TYPE_KEY, PLUGIN_ENGINE_LINUX);

            return Main.vertx().eventBus().<JsonObject>request(ZMQ_REQUEST_EVENT, zmqReqPayload);
        })
        .compose(jsonResponse ->
        {
            if (jsonResponse.body().getString(STATUS_KEY).equals(STATUS_RESPONSE_SUCCESS))
            {
                return Main.vertx().<Void>executeBlocking(promise ->
                {
                     QueryHandler.updateByField(DISCOVERY_TABLE, new JsonObject().put(DISCOVERY_STATUS_KEY, "complete"), DISCOVERY_ID_KEY, payload.getInteger(CREDENTIAL_ID_KEY))
                       .onSuccess(promise::complete)
                       .onFailure(promise::fail);
                });
            }
            else
            {
                return Future.failedFuture("ssh connection is unsuccessful");
            }
        })
        .onSuccess(response ->
        {
            message.reply("Discovery successfully completed");
        })
        .onFailure(err ->
        {
            LOGGER.warning("Error during discovery for IP: " + ip + " Port: " + port + " " + err.getMessage());

            message.reply("Discovery failed: " + err.getMessage());
        });
    }
}
