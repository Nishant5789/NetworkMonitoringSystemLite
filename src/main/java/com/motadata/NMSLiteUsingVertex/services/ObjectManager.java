package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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
    private static final Logger LOGGER = AppLogger.getLogger();

    @Override
    public void start(Promise<Void> startPromise) throws Exception
    {
        LOGGER.info("Object services deployed: " + Thread.currentThread().getName());

        vertx.eventBus().<JsonObject>localConsumer(PROVISION_EVENT, this::provision);

        // start object scheduling
        handleObjectScheduling();
        startPromise.complete();
    }

    // handle provisioning
    private void provision(Message<JsonObject> message)
    {
        var payload = message.body();
        var ip = payload.getString(IP_KEY);
        var pollInterval = payload.getInteger(POLL_INTERVAL_KEY);

        QueryHandler.getOneByFieldWithJoin(DISCOVERY_TABLE, CREDENTIAL_TABLE, CREDENTIAL_ID_KEY, IP_KEY, ip)
        .compose(discoveryRecord ->
        {
            if (discoveryRecord==null)
            {
                LOGGER.info("Discovery details not found for IP: " + ip);

                return Future.failedFuture("No discovery record found for your provided IP");
            }
            else if (discoveryRecord.getString(DISCOVERY_STATUS_KEY).equals(STATUS_PENDING))
            {
                LOGGER.info("Discovery is incomplete for this IP: " + ip);

                return Future.failedFuture("Discovery is incomplete for your provided IP");
            }

            var objectPayload = createObject(discoveryRecord, pollInterval);

            return QueryHandler.save(PROVISIONED_OBJECTS_TABLE, createProvisionObjectPayload(discoveryRecord, pollInterval)).map(v -> objectPayload);
        })
        .compose(objectPayload ->
        {
            LOGGER.info("Provisioned object successfully created for IP: " + ip);

            return QueryHandler.getOneByField(PROVISIONED_OBJECTS_TABLE, IP_KEY, ip).map(provisionedObjectRecord -> objectPayload.put(OBJECT_ID_KEY, provisionedObjectRecord.getInteger(OBJECT_ID_KEY)));
        })
        .onSuccess(objectPayload ->
        {
            Utils.addObjectInList(objectPayload);

            LOGGER.info("Object with IP: " + ip + " and Object ID: " + objectPayload.getInteger(OBJECT_ID_KEY) + " added to objectList");

            message.reply(Utils.createResponse(STATUS_KEY, STATUS_RESPONSE_SUCCESS));
        })
        .onFailure(err ->
        {
            LOGGER.warning("Database query failed: " + err);

            message.reply(Utils.createResponse(STATUS_RESPONSE_FAIIED, err.getMessage()));
        });
    }

    // schedule object polling
    private void handleObjectScheduling()
    {
        Main.vertx().setPeriodic(3000, timeId ->
        {
            LOGGER.info("Polling is started, objectList: " + Utils.getObjectList());

            var objectToPoll = new JsonArray();

            for (JsonObject object : Utils.getObjectList())
            {
                var timeSinceLastPoll = System.currentTimeMillis() - object.getLong(LAST_POLL_TIME_KEY);

                if (timeSinceLastPoll >= object.getInteger(POLL_INTERVAL_KEY))
                {
                    objectToPoll.add(object);

                    Main.vertx().eventBus().send(POLLING_EVENT, objectToPoll);
                }
            }
            LOGGER.info("Object sent for polling: " + objectToPoll.encodePrettily());
        });
    }

    //create object with data to store on objectQueue
    private JsonObject createObject(JsonObject discoveryRecord, Integer pollInterval)
    {
        JsonObject credentialDataPayload = new JsonObject(discoveryRecord.getString(CREDENTIAL_DATA_KEY));

        return new JsonObject().put(USERNAME_KEY, credentialDataPayload.getString(USERNAME_KEY)).put(PASSWORD_KEY, credentialDataPayload.getString(PASSWORD_KEY)).put(IP_KEY, discoveryRecord.getString(IP_KEY)).put(PORT_KEY, discoveryRecord.getString(PORT_KEY)).put(PLUGIN_ENGINE_TYPE_KEY, PLUGIN_ENGINE_LINUX).put(LAST_POLL_TIME_KEY, System.currentTimeMillis()).put(POLL_INTERVAL_KEY, pollInterval).put(FAILURE_COUNT_KEY, DEAFAULT_FAILURE_VALUE).put(OBJECT_AVAILABILITY_KEY, OBJECT_AVAILABILITY_UP);
    }

    //create provision_object  store on provisioned_objectstable on database
    private JsonObject createProvisionObjectPayload(JsonObject discoveryRecord, Integer pollInterval)
    {
        return new JsonObject().put(IP_KEY, discoveryRecord.getString(IP_KEY)).put(CREDENTIAL_ID_KEY, Integer.parseInt(discoveryRecord.getString(CREDENTIAL_ID_KEY))).put(POLL_INTERVAL_KEY, pollInterval);
    }
}
