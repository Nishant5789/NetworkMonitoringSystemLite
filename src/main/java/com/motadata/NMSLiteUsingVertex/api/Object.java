package com.motadata.NMSLiteUsingVertex.api;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;
import static com.motadata.NMSLiteUsingVertex.utils.Utils.formatInvalidResponse;

public class Object
{
  private static final Router router = Router.router(Main.vertx());

  private static final Logger LOGGER = AppLogger.getLogger();

  // return subrouter for object
  public static Router getRouter()
  {
    // POST /api/object - Handle device provisioning
    router.post("/provision").handler(Object::handleProvisioning);

    // GET /api/object - fetch Polling data by objectId
    router.get("/pollingdata/:ip_address").handler(Object::handlePollingData);

    // GET /api/object - get all objects with data
    router.get("/").handler(Object::getAllObjects);

    // GET /api/object - get object with data
    router.get("/:object_id").handler(Object::getObjectById);

    // DELETE /api/object - delete Monitor by Id
    router.delete("/:object_id").handler(Object::deleteObject);

    return router;
  }

  // handle provisioning
  private static void handleProvisioning(RoutingContext ctx)
  {
    var payload = ctx.body().asJsonObject();

    var payloadValidationResult = Utils.isValidPayload(PROVISIONED_OBJECTS_TABLE, payload);

    if (payloadValidationResult.get(IS_VALID_KEY).equals("false"))
    {
      ctx.response().setStatusCode(400).end(Utils.createResponse(STATUS_RESPONSE_ERROR, formatInvalidResponse(payloadValidationResult)).encodePrettily());
      return;
    }

    QueryHandler.getOneByField(PROVISIONED_OBJECTS_TABLE, IP_KEY, payload.getString(IP_KEY))
        .onSuccess(provisionRecord ->
        {
          if(provisionRecord != null)
          {
            LOGGER.severe("Object is already Provisioned & perform");

            ctx.response().setStatusCode(200).end(Utils.createResponse(STATUS_RESPONSE_SUCCESS, "Object is already Provisioned & perform polling..").encodePrettily());
            return;
          }

          Main.vertx().eventBus().<JsonObject>request(PROVISION_EVENT, payload)
            .onSuccess(replybody ->
            {
              var response = replybody.body();

              LOGGER.info("Provisioning successful: " + response);

              ctx.response().setStatusCode(201).end(response.encodePrettily());
            })
            .onFailure(err ->
            {
              LOGGER.severe("Provisioning failed: " + err.getMessage());

              ctx.response().setStatusCode(400).end(Utils.createResponse(STATUS_RESPONSE_FAIIED, "Provisioning failed").encodePrettily());
            });
        })
        .onFailure(err ->
        {
        LOGGER.severe("database query failed: " + err.getMessage());

        ctx.response().setStatusCode(500).end(Utils.createResponse(STATUS_RESPONSE_ERROR, "database query failed").encodePrettily());
      });
  }

  // handle pollingData
  private static void handlePollingData(RoutingContext ctx)
  {
    var ipAddress = ctx.pathParam(IP_HEADER_PATH);

    if (ipAddress == null|| ipAddress.trim().isEmpty())
    {
      LOGGER.warning("Invalid Ip Address : empty IP is received: " + ipAddress);

      ctx.response().setStatusCode(400).end(Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid Ip Address : IP cannot be empty").encodePrettily());
      return;
    }

    if (!Utils.isValidIPAddress(ipAddress))
    {
      LOGGER.warning("Invalid IP address format received: " + ipAddress);

      ctx.response().setStatusCode(400).end(Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid Ip Address : IP cannot be empty").encodePrettily());
      return;
    }

    QueryHandler.getAllByFieldWithJoin(POLLING_RESULTS_TABLE, IP_KEY, ipAddress)
      .onSuccess(pollingRecords ->
      {
        LOGGER.info("Object Polling data fetched successfully");

        ctx.response().setStatusCode(200).end(new JsonArray(pollingRecords).encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to fetch provision data: " + err.getMessage());

        ctx.response().setStatusCode(500).end(Utils.createResponse(STATUS_RESPONSE_ERROR, "Failed to fetch Object Polling data").encodePrettily());
      });
}

  // handle send all objects data
  private static void getAllObjects(RoutingContext ctx)
  {
    LOGGER.info("Fetching all objects with details...");

    QueryHandler.getAllWithJoin(PROVISIONED_OBJECTS_TABLE, CREDENTIAL_TABLE, CREDENTIAL_ID_KEY)
      .onSuccess(objects ->
      {
        LOGGER.info("Fetched objects successfully");

        ctx.response().end(new JsonArray(objects).encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to fetch objects: " + err.getMessage());

        ctx.response().setStatusCode(500).end(Utils.createResponse(STATUS_RESPONSE_ERROR, "Failed to fetch objects: " + err.getMessage()).encodePrettily());
      });
  }

  // handle send object by id
  private static void getObjectById(RoutingContext ctx)
  {
    var objectId = ctx.pathParam(OBJECT_ID_HEADER_PATH);

    if (objectId == null || objectId.trim().isEmpty()) {
      LOGGER.warning("Invalid object id received: " + objectId);

      var response = Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid object id: Id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());

      return;
    }

    LOGGER.info("Finding object by id: " + objectId);

    QueryHandler.getOneByField(PROVISIONED_OBJECTS_TABLE, OBJECT_ID_KEY, objectId)
      .onSuccess(object ->
      {
        if (object == null)
        {
          LOGGER.warning("Object not found: " + objectId);

          ctx.response().setStatusCode(404).end(Utils.createResponse(STATUS_RESPONSE_FAIIED, "object not found").encodePrettily());
        }
        else
        {
          LOGGER.info("Found object: " + object);

          ctx.response().end(object.encodePrettily());
        }
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to find object: " + err.getMessage());

        ctx.response().setStatusCode(500).end(Utils.createResponse(STATUS_RESPONSE_FAIIED, "object not found").encodePrettily());
      });
  }

  // handle delete object
  private static void deleteObject(RoutingContext ctx)
  {
    var objectId = ctx.pathParam(OBJECT_ID_HEADER_PATH);

    if (objectId == null || objectId.trim().isEmpty())
    {
      LOGGER.warning("Invalid object id received: " + objectId);

      var response = Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid object id: Id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
      return;
    }

    LOGGER.info("Finding object status by id: " + objectId);

    QueryHandler.deleteById(PROVISIONED_OBJECTS_TABLE, objectId)
      .onSuccess(deleted ->
      {
        if (deleted)
        {
          LOGGER.info("Object deleted successfully");

          Utils.removeObjectFromQueue(Integer.parseInt(objectId));

          ctx.response().setStatusCode(200).end(new JsonObject().put(STATUS_KEY, STATUS_RESPONSE_SUCCESS).put(STATUS_MSG_KEY, "Object deleted successfully").encodePrettily());
        }
        else
        {
          LOGGER.info("No matching record found");

          var response = new JsonObject().put("status", "success").put("statusMsg", "No matching record found");

          ctx.response().setStatusCode(200).end(response.encodePrettily());
        }
      })
      .onFailure(err ->
      {
        LOGGER.severe("Database query failed: " + err.getMessage());

        var response = Utils.createResponse(STATUS_RESPONSE_ERROR, "Database query failed");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }
}
