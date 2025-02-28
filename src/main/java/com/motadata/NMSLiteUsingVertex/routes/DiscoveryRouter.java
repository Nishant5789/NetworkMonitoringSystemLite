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
          ctx.response().setStatusCode(200).end((String) reply.result().body());
//          ctx.response().setStatusCode(200).end(((JsonObject) reply.result().body()).encodePrettily());
        }
        else{
          ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to start discovery").encodePrettily());
        }
    }));

    return router;
  }

  private void saveDiscovery(RoutingContext ctx) {
    JsonObject payload = ctx.body().asJsonObject();
    String id = payload.getString("id");
    String port = payload.getString("port");
    String credential_id = payload.getString("credential_id");
    String status = payload.getString("status");


  }
}
