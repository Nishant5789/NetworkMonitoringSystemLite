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

public class Discovery
{
  private static final Logger LOGGER = AppLogger.getLogger();

  private static final Router router = Router.router(Main.vertx());

  // return subroutes for discovery
  public static Router getRouter()
  {
    // GET /api/discovery - get All discovery
    router.get("/").handler(Discovery::getAllDiscovery);

    // GET /api/discovery - get All discovery
    router.get("/:id").handler(Discovery::getDiscoveryByID);

    // POST /api/discovery - added discovery
    router.post("/").handler(Discovery::addedDiscovery);

    // PUT  /api/discovery - update discovery
    router.put("/:id").handler(Discovery::updateDiscovery);

    // GET /api/run/ - run discovery
    router.post("/run").handler(Discovery::handleRunDiscovery);

    // DELETE /api/:discoveryId - delete discovery
    router.delete("/:id").handler(Discovery::deleteDiscovery);

    return router;
  }

  // handle get discovery By Id
  private static void getDiscoveryByID(RoutingContext ctx)
  {
    var credentialId = ctx.pathParam(ID_KEY);

    if (credentialId == null || credentialId.trim().isEmpty())
    {
      LOGGER.warning("Invalid discovery id received: " + credentialId);

      var response = Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid discovery id: Id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());

      return;
    }

    LOGGER.info("Finding discovery by id: " + credentialId);

    QueryHandler.getByField(DISCOVERY_TABLE, DISCOVERY_ID_KEY, credentialId)
      .onSuccess(discoveryRecord ->
      {
        if (discoveryRecord == null)
        {
          LOGGER.warning("Credential not found: " + credentialId);

          var response = Utils.createResponse(STATUS_RESPONSE_FAIIED, "Credential not found");

          ctx.response().setStatusCode(404).end(response.encodePrettily());
        }
        else
        {
          LOGGER.info("Found discovery: " + discoveryRecord);

          ctx.response().end(discoveryRecord.encodePrettily());
        }
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to find discovery: " + err.getMessage());

        var response = Utils.createResponse(STATUS_RESPONSE_FAIIED, "Credential not found");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // handle get all discovery
  private static void getAllDiscovery(RoutingContext ctx)
  {
    LOGGER.info("Fetching all discovery");

    QueryHandler.getAllWithJoinTable(DISCOVERY_TABLE, CREDENTIAL_TABLE, CREDENTIAL_ID_KEY)
      .onSuccess(discoveryRecords ->
      {
        LOGGER.info("Fetched discovery successfully");

        var response = new JsonArray(discoveryRecords);

        ctx.response().end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to fetch discovery: " + err.getMessage());

        var response = Utils.createResponse(STATUS_RESPONSE_ERROR, "Failed to fetch discovery");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // handle added discovery
  public static void addedDiscovery(RoutingContext ctx)
  {
    var payload = ctx.body().asJsonObject();

    var payloadValidationResult = Utils.isValidPayload(DISCOVERY_TABLE, payload);

    if (payloadValidationResult.get(IS_VALID_KEY).equals("false"))
    {
      var errorResponse = Utils.createResponse(STATUS_RESPONSE_ERROR, formatInvalidResponse(payloadValidationResult));

      ctx.response().setStatusCode(400).end(errorResponse.encodePrettily());
      return;
    }

    LOGGER.info("Saving discovery: " + payload);

    QueryHandler.save(DISCOVERY_TABLE, payload)
      .onSuccess(v ->
      {
        LOGGER.info("discovery saved successfully");

        var response = Utils.createResponse(STATUS_RESPONSE_SUCCESS, "discovery saved successfully.");

        ctx.response().setStatusCode(201).end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to save discovery: " + err.getMessage());

        JsonObject errResponse;

        if(err.getMessage().contains("violates foreign key constraint"))
        {
           errResponse = Utils.createResponse(STATUS_RESPONSE_ERROR, "Failed to save discovery: provided credentialId not exists");
        }
        else
        {
          errResponse = Utils.createResponse(STATUS_RESPONSE_ERROR, "Failed to save discovery");
        }

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

      var response = Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid discovery id: id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
      return;
    }

    QueryHandler.getById(DISCOVERY_TABLE, id)
      .compose( discoveryRecord ->
      {
        if(discoveryRecord == null)
        {
          LOGGER.warning("Discovery not found: " + id);

          return Future.failedFuture("discovery not found for provided Id");
        }
        return  Main.vertx().eventBus().request(DISCOVERY_EVENT, discoveryRecord);
        })
      .compose(reply ->
      {
        var updatePayload = new JsonObject().put(DISCOVERY_STATUS_KEY, "completed");

        return QueryHandler.updateByField(DISCOVERY_TABLE, updatePayload, DISCOVERY_ID_KEY, id)
          .map(v -> reply.body());
      })
      .onSuccess(replyBody ->
      {
        var responce = Utils.createResponse(STATUS_RESPONSE_SUCCESS, replyBody.toString());

        ctx.response().setStatusCode(200).end(responce.encodePrettily());
      })
      .onFailure(err ->
      {
        var failedResponse = Utils.createResponse(STATUS_RESPONSE_FAIIED, err.getMessage());

        ctx.response().setStatusCode(400).end(failedResponse.encodePrettily());
      });
  }

  // handle Update discovery
  public static void updateDiscovery(RoutingContext ctx)
  {
    var discoveryId = ctx.pathParam(ID_HEADER_PATH);

    if (discoveryId == null || discoveryId.trim().isEmpty())
    {
      LOGGER.warning("Invalid discovery id received: " + discoveryId);

      var response = Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid discovery id: id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());

      return;
    }

    var payload = ctx.body().asJsonObject();

    if (payload == null || payload.isEmpty())
    {
      LOGGER.warning("payload is empty");

      var response = Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid payload: payload is empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
      return;
    }

    LOGGER.info("Updating discovery for id: " + discoveryId);

    QueryHandler.updateByField(DISCOVERY_TABLE, payload, DISCOVERY_ID_KEY, discoveryId)
      .onSuccess(v ->
      {
        LOGGER.info("Discovery updated successfully for id: " + discoveryId);

        var response = Utils.createResponse(STATUS_RESPONSE_SUCCESS, "Discovery updated successfully");

        ctx.response().setStatusCode(200).end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to save discovery: " + err.getMessage());

        var response = Utils.createResponse(STATUS_RESPONSE_ERROR, "Failed to save discovery");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // handle delete discovery
  public static void deleteDiscovery(RoutingContext ctx)
  {
    var discoveryId = ctx.pathParam(ID_KEY);

    QueryHandler.deleteById(DISCOVERY_TABLE, discoveryId)
      .onSuccess(deletedStatus ->
      {
        if (deletedStatus)
        {
          LOGGER.info("discovery deleted successfully");

          var response = new JsonObject().put(STATUS_KEY, STATUS_RESPONSE_SUCCESS).put("statusMsg", "discovery deleted Sucessfully");

          ctx.response().setStatusCode(200).end(response.encodePrettily());
        }
        else
        {
          LOGGER.info("No matching record found");

          var response = new JsonObject().put(STATUS_KEY, STATUS_RESPONSE_SUCCESS).put("statusMsg", "No matching record found");

          ctx.response().setStatusCode(200).end(response.encodePrettily());
        }
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to find discovery: " + err.getMessage());

        var response = Utils.createResponse(STATUS_RESPONSE_ERROR, "Database query failed");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }
}
