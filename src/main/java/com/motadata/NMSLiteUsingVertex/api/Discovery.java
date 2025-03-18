package com.motadata.NMSLiteUsingVertex.api;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;
import static com.motadata.NMSLiteUsingVertex.utils.Utils.formatInvalidResponse;

public class Discovery
{
  private static final Logger LOGGER = AppLogger.getLogger();

  private static final Router router = Router.router(Main.vertx());

  // return subroutes for discovery
  public static Router getRouter()
  {
    // POST /api/discovery - added discovery
    router.post("/").handler(Discovery::addedDiscovery);

    // GET /api/run/ - run discovery
    router.post("/run").handler(Discovery::handleRunDiscovery);

    // DELETE /api/:discoveryId - delete discovery
    router.delete("/:discovery_id").handler(Discovery::deleteDiscovery);

    return router;
  }

  // handle added discovery
  public static void addedDiscovery(RoutingContext ctx)
  {
    var payload = ctx.body().asJsonObject();

    var payloadValidationResult = Utils.isValidPayload(DISCOVERY_TABLE, payload);

    if (payloadValidationResult.get(IS_VALID_KEY).equals("false"))
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

        var errResponse = Utils.createResponse("error", "Failed to save discovery: " + err.getMessage());

        ctx.response().setStatusCode(500).end(errResponse.encodePrettily());
      });
  }

  // handle run discovery
  public static void handleRunDiscovery(RoutingContext ctx)
  {
    var payload = ctx.body().asJsonObject();

    var id = String.valueOf(payload.getInteger(DISCOVERY_ID_KEY));

    if(id == null || id.trim().isEmpty())
    {
      LOGGER.warning("Invalid discovery id received: " + id);

      var response = Utils.createResponse("failed", "Invalid discovery id: id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
      return;
    }

    QueryHandler.getById(DISCOVERY_TABLE, id)
      .compose( discoveryRecord ->
      {
        if(discoveryRecord.getString(DISCOVERY_STATUS_KEY).equals("pending"))
        {
          return ctx.vertx().eventBus().request(DISCOVERY_EVENT, discoveryRecord);
        }
        else
        {
          return Future.failedFuture("discovery is already completed");
        }
      })
      .compose(reply ->
      {
        var updatePayload = new JsonObject().put(DISCOVERY_STATUS_KEY, "completed");

        return QueryHandler.updateByField(DISCOVERY_TABLE, updatePayload, DISCOVERY_ID_KEY, id)
          .map(v -> reply.body());

      })
      .onSuccess(replyBody ->
      {
        var responce = Utils.createResponse("success", replyBody.toString());

        ctx.response().setStatusCode(200).end(responce.encodePrettily());
      })
      .onFailure(err ->
      {
        var failedResponse = Utils.createResponse("failed", err.getMessage());

        ctx.response().setStatusCode(400).end(failedResponse.encodePrettily());
      });
  }

  // handle delete discovery
  public static void deleteDiscovery(RoutingContext ctx)
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
