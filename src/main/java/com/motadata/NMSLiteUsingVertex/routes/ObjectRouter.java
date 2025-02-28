package com.motadata.NMSLiteUsingVertex.routes;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

public class ObjectRouter {
  public Router getRouter(Vertx vertx) {
    Router router = Router.router(vertx);

    router.get("/").handler(ctx -> {
      ctx.response()
        .putHeader("content-type", "text/plain")
        .end("Object Router: List all objects");
    });

    router.put("/update").handler(ctx -> {
      ctx.response()
        .putHeader("content-type", "text/plain")
        .end("Object Router: Update object");
    });

    return router;
  }
}
