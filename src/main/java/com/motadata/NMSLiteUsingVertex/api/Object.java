package com.motadata.NMSLiteUsingVertex.api;

import com.motadata.NMSLiteUsingVertex.Main;
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
  private  static final Router router = Router.router(Main.vertx());

  private static final Logger LOGGER = AppLogger.getLogger();
//  private static final Logger LOGGER =  Logger.getLogger(Object.class.getName());

  // return subrouter for object
  public static Router getRouter()
  {
    // POST /api/object/provision/ - Handle device provisioning
    router.post("/provision").handler(Object::handleProvisioning);

    // GET /api/object/pollingdata/:monitor_id - fetch Polling data by monitorId
    router.get("/pollingdata/:object_id").handler(Object::handlePollingData);

    return router;
  }

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

    LOGGER.info("Received provisioning request: " + payload);

    ctx.vertx().eventBus().request(PROVISION_EVENT, payload, reply ->
    {
      if (reply.succeeded())
      {
        var response = (JsonObject) reply.result().body();

        LOGGER.info("Provisioning successful: " + response);

        ctx.response().setStatusCode(201).end(response.encodePrettily());
      }
      else
      {
        LOGGER.severe("Provisioning failed: " + reply.cause().getMessage());

        var response = Utils.createResponse("error", "Provisioning failed: " + reply.cause().getMessage());

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      }
    });
  }

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

    ctx.vertx().eventBus().request(GET_POLLING_DATA_EVENT, objectId, reply ->
    {
      if (reply.succeeded())
      {
        LOGGER.info("fetch Polling data successfull");

        var response = (JsonArray) reply.result().body();

        ctx.response().setStatusCode(200).end(response.encodePrettily());
      }
      else
      {
        LOGGER.severe("fetch Polling failed: " + reply.cause().getMessage());

        var response = Utils.createResponse("error", "fetch Polling failed");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      }
    });
  }
}
