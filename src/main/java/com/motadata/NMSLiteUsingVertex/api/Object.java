package com.motadata.NMSLiteUsingVertex.api;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;
import static com.motadata.NMSLiteUsingVertex.utils.Utils.formatInvalidResponse;

public class Object
{
  private  static final Router router = Router.router(Main.vertx());

  private static final Logger LOGGER = AppLogger.getLogger();

  // return subrouter for object
  public static Router getRouter()
  {
    // POST /api/object/provision/ - Handle device provisioning
    router.post("/provision").handler(Object::handleProvisioning);

    // GET /api/object/pollingdata/:object_id - fetch Polling data by objectId
    router.get("/pollingdata/:object_id").handler(Object::handlePollingData);

    // GET /api/object/ - get all objects with data
    router.get("/").handler(Object::getAllObjects);

    // GET /api/object/ - get object with data
    router.get("/:object_id").handler(Object::getObjectById);

    // DELETE /api/object/:object_id - delete Monitor by Id
    router.delete("/:object_id").handler(Object::deleteObject);

    return router;
  }

  // handle provisioning
  private static void handleProvisioning(RoutingContext ctx)
  {
    var payload = ctx.body().asJsonObject();

    var payloadValidationResult = Utils.isValidPayload(PROVISIONED_OBJECTS_TABLE, payload);

    if (payloadValidationResult.get("isValid").equals("false"))
    {
      var errorResponse = Utils.createResponse("error", formatInvalidResponse(payloadValidationResult));
      ctx.response().setStatusCode(400).end(errorResponse.encodePrettily());
      return;
    }

    var objectId = payload.getInteger(OBJECT_ID_KEY);

    QueryHandler.getById(PROVISIONED_OBJECTS_TABLE, String.valueOf(objectId))
      .compose( objectRecord ->
      {
        if(objectRecord.getString(PROVISIONING_STATUS_KEY).equals("pending"))
        {
          return ctx.vertx().eventBus().request(PROVISION_EVENT, payload);
        }
        else
        {
          return Future.failedFuture("object provisioning is already completed");
        }
      })
        .onSuccess(replybody ->
        {
          var response = (JsonObject) replybody.body();

          LOGGER.info("Provisioning successful: " + response);

          ctx.response().setStatusCode(201).end(response.encodePrettily());
        })
        .onFailure(err->
        {
          LOGGER.severe("Provisioning failed: " + err.getMessage());

          var response = Utils.createResponse("failed", "Provisioning failed");

          ctx.response().setStatusCode(400).end(response.encodePrettily());
        });
  }

  // handle pollingData
  private static void handlePollingData(RoutingContext ctx)
  {
    var objectId = ctx.pathParam(OBJECT_ID_HEADER_PATH);

    if (objectId == null || objectId.trim().isEmpty())
    {
      LOGGER.warning("Invalid objectId id received: " + objectId);

      var response = Utils.createResponse("failed", "Invalid objectId id: Id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
      return;
    }

    var responseArray = Utils.getPollDataFromCache(objectId);

    if (responseArray != null)
    {
      LOGGER.info("fetch Polling data successfull");
    }
    else
    {
      LOGGER.severe("fetch Polling is successfull but Empty");
    }
    ctx.response().setStatusCode(200).end(responseArray.encodePrettily());
  }

  // handle send all objects data
  private static void getAllObjects(RoutingContext ctx)
  {
    LOGGER.info("Fetching all objects with details...");

    QueryHandler.getAll(PROVISIONED_OBJECTS_TABLE)
      .onSuccess(objects ->
      {
        LOGGER.info("Fetched objects successfully");

        var response = new JsonArray(objects);

        ctx.response().end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to fetch objects: " + err.getMessage());

        var response = Utils.createResponse("error", "Failed to fetch objects: " + err.getMessage());

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // handle send object by id
  private static void getObjectById(RoutingContext ctx)
  {
    var objectId = ctx.pathParam(OBJECT_ID_HEADER_PATH);

    if (objectId == null || objectId.trim().isEmpty())
    {
      LOGGER.warning("Invalid object id received: " + objectId);

      var response = Utils.createResponse("failed", "Invalid object id: Id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());

      return;
    }

    LOGGER.info("Finding object by id: " + objectId);

    QueryHandler.getByField(PROVISIONED_OBJECTS_TABLE, OBJECT_ID_KEY, objectId)
      .onSuccess(object ->
      {
        if (object == null)
        {
          LOGGER.warning("Object not found: " + objectId);

          var response = Utils.createResponse("failed", "object not found");

          ctx.response().setStatusCode(404).end(response.encodePrettily());
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

        var response = Utils.createResponse("failed", "object not found");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // handle delete object
  private static void deleteObject(RoutingContext ctx)
  {
    var objectId = ctx.pathParam(OBJECT_ID_HEADER_PATH);

    if (objectId == null || objectId.trim().isEmpty())
    {
      LOGGER.warning("Invalid object id received: " + objectId);

      var response = Utils.createResponse("failed", "Invalid object id: Id cannot be empty");

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

          var response = new JsonObject().put("status", "success").put("statusMsg", "Object deleted successfully");

          ctx.response().setStatusCode(200).end(response.encodePrettily());
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

        var response = Utils.createResponse("error", "Database query failed");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }
}
