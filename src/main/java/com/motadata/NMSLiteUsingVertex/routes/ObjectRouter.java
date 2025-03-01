package com.motadata.NMSLiteUsingVertex.routes;

import com.motadata.NMSLiteUsingVertex.services.CredentialService;
import io.vertx.core.Vertx;
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

        }
        else{

        }
      }));


    return router;
  }

}

