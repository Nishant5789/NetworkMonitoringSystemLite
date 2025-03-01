package com.motadata.NMSLiteUsingVertex.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class DiscoveryRouter {
  Router router;

  public DiscoveryRouter(Vertx vertx){
    router = Router.router(vertx);
  }

  public Router getRouter() {
    // POST /api/discovery -
    router.post("/").handler(ctx->ctx.vertx().eventBus()
      .request("discovery.verticle",ctx.body().asJsonObject(),
        reply-> {
        if(reply.succeeded()){
          ctx.response().setStatusCode(201).end((String) reply.result().body());
//          ctx.response().setStatusCode(200).end(((JsonObject) reply.result().body()).encodePrettily());
        }
        else{
          ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to start discovery").encodePrettily());
        }
    }));

    router.get("/").handler(ctx->ctx.response().setStatusCode(200).end("work"));

    return router;
  }
}
