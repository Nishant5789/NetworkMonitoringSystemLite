package com.motadata.NMSLiteUsingVertex.routes;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class DiscoveryRouter
{
  private  static final Router router = Router.router(Main.vertx());

  public static Router getRouter()
  {
    // POST /api/discovery
    router.post("/").handler(ctx->
    {
      JsonObject payload = ctx.body().asJsonObject();

      if (!Utils.isValidPayload(MONITOR_DEVICE_TABLE, payload))
      {
        JsonObject response = Utils.createResponse("error", "Invalid  payload: Missing required fields.");

        ctx.response().setStatusCode(400).end(response.encodePrettily());
        return;
      }

      ctx.vertx().eventBus().request(DISCOVERY_EVENT,payload,
        reply->
        {
          if (reply.succeeded())
          {
            //          System.out.println(reply.result().body());
            //          ctx.response().setStatusCode(201).end( reply.result().body());
            ctx.response().setStatusCode(200).end(((JsonObject) reply.result().body()).encodePrettily());
          }
          else
          {
            ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to start discovery").encodePrettily());
          }
        });
    });

    return router;
  }
}
