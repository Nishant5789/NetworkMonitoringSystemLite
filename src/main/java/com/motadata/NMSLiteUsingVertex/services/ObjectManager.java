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
//  private static final Logger LOGGER = AppLogger.getLogger();
  private static final Logger LOGGER =  Logger.getLogger(ObjectManager.class.getName());

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
    var objectPayload = new JsonObject[1];

    QueryHandler.getByFieldWithJoinTable(DISCOVERY_TABLE, CREDENTIAL_TABLE, CREDENTIAL_ID_KEY, IP_KEY, ip)
      .compose(discoveryRecord ->
      {
        if (discoveryRecord == null)
        {
          LOGGER.info("Discovery details not found for IP: " + ip);

          return Future.failedFuture("No discovery record found");
        }
        else if(discoveryRecord.getString(DISCOVERY_STATUS_KEY).equals(STATUS_PENDING))
        {
          LOGGER.info("Discovery is pending for this IP: " + ip);

          return Future.failedFuture("Discovery run is pending for your provided IP");
        }

        var credentialDataPayload = new JsonObject(discoveryRecord.getString(CREDENTIAL_DATA_KEY));
        objectPayload[0] = createObject(credentialDataPayload, discoveryRecord, pollInterval);
        var provisionObjectPayload = createProvisionObjectPayload(discoveryRecord, pollInterval);

        return QueryHandler.save(PROVISIONED_OBJECTS_TABLE, provisionObjectPayload);
      })
      .compose(saveResult ->
      {
        LOGGER.info("Provisioned object successfully created for IP: " + ip);

        return QueryHandler.getByField(PROVISIONED_OBJECTS_TABLE, IP_KEY, ip);
      })
      .onSuccess(provisionedObjectRecord ->
      {
        if (provisionedObjectRecord != null)
        {
          var objectId = provisionedObjectRecord.getInteger(OBJECT_ID_KEY);
          objectPayload[0].put(OBJECT_ID_KEY, objectId);  // ✅ Add objectId after fetching

          Utils.addObjectInQueue(objectPayload[0]);

          LOGGER.info("Object with IP: " + ip + " and Object ID: " + objectId + " added to objectQueue");

          message.reply(Utils.createResponse(STATUS_KEY, STATUS_RESPONSE_SUCCESS));
        }
        else
        {
          LOGGER.warning("Provisioned object record not found after saving for IP: " + ip);

          message.reply(Utils.createResponse(STATUS_RESPONSE_FAIIED, "Provisioned object record not found"));
        }
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
    Main.vertx().setTimer(60000, timeId ->
    {
      LOGGER.info("Polling is started, objectQueue: " + Utils.getObjectQueue());

      var currentTime = System.currentTimeMillis();

      var objectToPoll = new JsonArray();

      for (JsonObject object : Utils.getObjectQueue())
      {
        var lastPollTime = object.getLong(LAST_POLL_TIME_KEY);

        var timeSinceLastPoll = currentTime - lastPollTime;

        if (timeSinceLastPoll >= object.getInteger(POLL_INTERVAL_KEY))
        {
          objectToPoll.add(object);

          LOGGER.info("Object sent for polling: " + objectToPoll.encodePrettily());

          Main.vertx().eventBus().send(POLLING_EVENT, objectToPoll);
        }
      }
    });
  }

 //create object with data to store on objectQueue
  private JsonObject createObject(JsonObject credentialDataPayload, JsonObject discoveryRecord, Integer pollInterval)
  {
    return new JsonObject()
      .put(USERNAME_KEY, credentialDataPayload.getString(USERNAME_KEY))
      .put(PASSWORD_KEY, credentialDataPayload.getString(PASSWORD_KEY))
      .put(IP_KEY, discoveryRecord.getString(IP_KEY))
      .put(PORT_KEY, discoveryRecord.getString(PORT_KEY))
      .put(PLUGIN_ENGINE_TYPE_KEY, PLUGIN_ENGINE_LINUX)
      .put(LAST_POLL_TIME_KEY, System.currentTimeMillis())
      .put(POLL_INTERVAL_KEY, pollInterval);
  }

  //create provision_object  store on provisioned_objectstable on database
  private JsonObject createProvisionObjectPayload(JsonObject discoveryRecord, Integer pollInterval)
  {
    return new JsonObject()
      .put(IP_KEY, discoveryRecord.getString(IP_KEY))
      .put(CREDENTIAL_ID_KEY, Integer.parseInt(discoveryRecord.getString(CREDENTIAL_ID_KEY)))
      .put(POLL_INTERVAL_KEY, pollInterval);
  }
}
