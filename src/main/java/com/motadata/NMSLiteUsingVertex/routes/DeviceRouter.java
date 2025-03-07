package com.motadata.NMSLiteUsingVertex.routes;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class DeviceRouter
{
  private  static final Router router = Router.router(Main.vertx());

  private static final Logger LOGGER = LoggerFactory.getLogger(DeviceRouter.class);

  // return subrouter for deviceRouting
  public static Router getRouter()
  {
    // POST /api/devices/provision - Handle device provisioning
    router.post("/provision").handler(ctx ->
    {
      JsonObject requestBody = ctx.body().asJsonObject();

      if (requestBody == null)
      {
        LOGGER.error("Provisioning request failed: No request body");

        ctx.response().setStatusCode(400).end(new JsonObject().put("error", "Invalid request body").encodePrettily());

        return;
      }

      LOGGER.info("Received provisioning request: {}", requestBody);

      ctx.vertx().eventBus().request(PROVISION_EVENT, requestBody, reply ->
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

//     GET /api/devices/:monitored_device_id - fetch devicePolling data
    router.get("/:monitored_device_id").handler(ctx ->
    {
      LOGGER.info("Fetching devicePolling data");

      String monitoredDeviceID = ctx.pathParam(MONITORED_DEVICE_ID_KEY);

      ctx.vertx().eventBus().request(GET_POLLING_DATA_EVENT, monitoredDeviceID, reply ->
      {
        if (reply.succeeded())
        {
          JsonObject response = (JsonObject) reply.result().body();

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

    // delete api/devices/:monitored_device_id
    router.delete("/:monitored_device_id").handler(ctx->
    {
      String monitoredDeviceID = ctx.pathParam(MONITORED_DEVICE_ID_KEY);

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
