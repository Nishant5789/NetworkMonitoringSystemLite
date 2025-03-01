package com.motadata.NMSLiteUsingVertex.routes;

import com.motadata.NMSLiteUsingVertex.services.CredentialService;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;


public class ObjectRouter {

  Router router;

  public ObjectRouter(Vertx vertx) {
    router = Router.router(vertx);
  }

  public Router getRouter() {
    // POST /api/provision - handle provision
    router.post("/provision").handler(ctx-> ctx.vertx()
      .eventBus().request("provision",ctx.body().asJsonObject(),reply->{
        if(reply.succeeded()){
            ctx.response().setStatusCode(201).end((String) reply.result().body());
        }
        else{
          ctx.response().setStatusCode(500).end(new JsonObject().put("error", "Failed to start provision").encodePrettily());
        }
      }));


    return router;
  }

}

