package com.motadata.NMSLiteUsingVertex.routes;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;
import static com.motadata.NMSLiteUsingVertex.utils.Utils.formatInvalidResponse;

public class DiscoveryRouter
{
  private  static final Router router = Router.router(Main.vertx());

  private static final Logger LOGGER = AppLogger.getLogger();

  // return subrouter for discoveryRouter
  public static Router getRouter()
  {
    // POST /api/discovery
    router.post("/").handler(DiscoveryRouter::addedDiscovery);

    // GET /api/run/:discoveryId
    router.get("/run/:discovery_id").handler(DiscoveryRouter::handleRunDiscovery);

    // DELETE /api/:discoveryId
    router.delete("/:discovery_id").handler(DiscoveryRouter::deleteDiscovery);

    return router;
  }

  // added discovery
  private static void addedDiscovery(RoutingContext ctx)
  {
    var payload = ctx.body().asJsonObject();

    var payloadValidationResult = Utils.isValidPayload(DISCOVERY_TABLE, payload);

    if (payloadValidationResult.get("isValid").equals("false"))
    {
      var errorResponse = Utils.createResponse("error", formatInvalidResponse(payloadValidationResult));

      ctx.response().setStatusCode(400).end(errorResponse.encodePrettily());
      return;
    }

    LOGGER.info("Saving discovery: " + payload);

    QueryHandler.save(DISCOVERY_TABLE, payload)
      .onSuccess(v ->
      {
        LOGGER.info("discovery saved successfully");

        var response = Utils.createResponse("success", "discovery saved successfully.");

        ctx.response().setStatusCode(201).end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to save discovery: " + err.getMessage());

        var response = Utils.createResponse("error", "Failed to save discovery: " + err.getMessage());

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // run discovery
  private static void handleRunDiscovery(RoutingContext ctx)
  {
    var id = ctx.pathParam(DISCOVERY_ID_HEADER_PATH);

    if (id == null || id.trim().isEmpty())
    {
      LOGGER.warning("Invalid discovery name received: " + id);

      var response = Utils.createResponse("failed", "Invalid discovery id: id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
      return;
    }

    QueryHandler.findById(DISCOVERY_TABLE, id)
      .onSuccess(discoveryRecord->
      {
        ctx.vertx().eventBus().request(DISCOVERY_EVENT, discoveryRecord,
          reply->
          {
            if (reply.succeeded())
            {
              ctx.response().setStatusCode(200).end(((JsonObject) reply.result().body()).encodePrettily());
            }
            else
            {
              ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to start discovery").encodePrettily());
            }
          });
      })
      .onFailure((err)->
      {
        LOGGER.severe("Failed to find device: " + err.getMessage());

        var response = Utils.createResponse("failed", "device is not found");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // delete discovery
  private static void deleteDiscovery(RoutingContext ctx)
  {
    var discoveryId = ctx.pathParam(DISCOVERY_ID_HEADER_PATH);

    QueryHandler.deleteById(DISCOVERY_TABLE, discoveryId)
      .onSuccess(deleted ->
      {
        if (deleted)
        {
          LOGGER.info("discovery deleted successfully");

          var response = new JsonObject().put("status", "success").put("statusMsg", "discovery deleted Sucessfully");

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
        LOGGER.severe("Failed to find credential: " + err.getMessage());

        var response = Utils.createResponse("error", "Database query failed");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }
}
