package com.motadata.NMSLiteUsingVertex.routes;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;
import static com.motadata.NMSLiteUsingVertex.utils.Utils.formatInvalidResponse;

public class ProvisionRouter
{
  private  static final Router router = Router.router(Main.vertx());

  private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionRouter.class);

  // return subrouter for deviceRouting
  public static Router getRouter()
  {
    // POST /api/provision/ - Handle device provisioning
    router.post("/").handler(ctx ->
    {
      JsonObject payload = ctx.body().asJsonObject();

      var payloadValidationResult = Utils.isValidPayload(PROVISION_TABLE, payload);

      if (payloadValidationResult.get("isValid").equals("false"))
      {
        var errorResponse = Utils.createResponse("error", formatInvalidResponse(payloadValidationResult));

        ctx.response().setStatusCode(400).end(errorResponse.encodePrettily());
        return;
      }

      LOGGER.info("Received provisioning request: {}", payload);

      ctx.vertx().eventBus().request(PROVISION_EVENT, payload, reply ->
      {
        if (reply.succeeded())
        {
          JsonObject response = (JsonObject) reply.result().body();

          LOGGER.info("Provisioning successful: {}", response);

          ctx.response().setStatusCode(201).end(response.encodePrettily());
        }
        else
        {
          LOGGER.error("Provisioning failed: {}", reply.cause().getMessage());

          JsonObject response = Utils.createResponse("error", "Provisioning failed: " + reply.cause().getMessage());

          ctx.response().setStatusCode(500).end(response.encodePrettily());
        }
      });
    });

    // GET /api/provision/:discovery_id - fetch devicePolling data
    router.get("/:discovery_id").handler(ctx ->
    {
      LOGGER.info("Fetching devicePolling data");

      String discoveryId = ctx.pathParam(DISCOVERY_ID_KEY);

      ctx.vertx().eventBus().request(GET_POLLING_DATA_EVENT, discoveryId, reply ->
      {
        if (reply.succeeded())
        {
          JsonArray response = (JsonArray) reply.result().body();

          LOGGER.info("Device Polling data fetched successfully");

          ctx.response().setStatusCode(200).end(response.encodePrettily());
        }
        else
        {
          LOGGER.error("Failed to fetch provision data: {}", reply.cause().getMessage());

          JsonObject response = Utils.createResponse("error", "Failed to fetch device Polling data");

          ctx.response().setStatusCode(500).end(response.encodePrettily());
        }
      });
    });

    // delete api/devices/:discovery_id
    router.delete("/:discovery_id").handler(ctx->
    {
      String monitoredDeviceID = ctx.pathParam(DISCOVERY_ID_KEY);

      ctx.vertx().eventBus().request(DELETE_DEVICE_EVENT, monitoredDeviceID, reply ->
        {
          if (reply.succeeded())
          {
            JsonObject response = (JsonObject) reply.result().body();

            LOGGER.info("Device Deleted successfully");

            ctx.response().setStatusCode(200).end(response.encodePrettily());
          }
          else
          {
            LOGGER.error("Failed to delete device ID: {}", reply.cause().getMessage());

            JsonObject response = Utils.createResponse("error", "Failed to delete device");

            ctx.response().setStatusCode(500).end(response.encodePrettily());
          }
        });
    });
    return router;
  }
}
