package com.motadata.NMSLiteUsingVertex.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class DeviceRouter
{

  private static final Logger LOGGER = LoggerFactory.getLogger(DeviceRouter.class);

  private final Router router;

  public DeviceRouter(Vertx vertx)
  {
    this.router = Router.router(vertx);
  }

  public Router getRouter()
  {

    // POST /api/provision - Handle device provisioning
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
        } else
        {
          LOGGER.error("Provisioning failed: {}", reply.cause().getMessage());

          ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Provisioning failed").encodePrettily());
        }
      });
    });

    // GET /api/get - Fetch provision data
//    router.get("/get").handler(ctx -> {
//      LOGGER.info("Fetching provision data");
//
//      ctx.vertx().eventBus().request(GET_POLLING_DATA_EVENT, null, reply -> {
//        if (reply.succeeded()) {
//          String response = (String) reply.result().body();
//          LOGGER.info("Provision data fetched successfully");
//          ctx.response().setStatusCode(200).end(response);
//        } else {
//          LOGGER.error("Failed to fetch provision data: {}", reply.cause().getMessage());
//          ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to fetch provision data").encodePrettily());
//        }
//      });
//    });

    return router;
  }
}
