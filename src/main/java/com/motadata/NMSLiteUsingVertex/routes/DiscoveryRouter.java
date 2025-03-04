package com.motadata.NMSLiteUsingVertex.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.DISCOVERY_EVENT;

public class DiscoveryRouter
{
  Router router;

  public DiscoveryRouter(Vertx vertx)
  {
    router = Router.router(vertx);
  }

  public Router getRouter()
  {

    // POST /api/discovery -
    router.post("/").handler(ctx->
      ctx.vertx().eventBus().request(DISCOVERY_EVENT,ctx.body().asJsonObject(),
        reply->
        {
          if(reply.succeeded())
          {
  //          System.out.println(reply.result().body());
  //          ctx.response().setStatusCode(201).end( reply.result().body());
            ctx.response().setStatusCode(200).end(((JsonObject) reply.result().body()).encodePrettily());
          }
          else
          {
            ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to start discovery").encodePrettily());
          }

    }));

    return router;
  }
}
