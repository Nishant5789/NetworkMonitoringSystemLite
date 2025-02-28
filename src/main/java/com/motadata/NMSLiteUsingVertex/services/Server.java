package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.routes.CredentialsRouter;
import com.motadata.NMSLiteUsingVertex.routes.DiscoveryRouter;
import com.motadata.NMSLiteUsingVertex.routes.ObjectRouter;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class Server extends AbstractVerticle {
  @Override
  public void start() {
    System.out.println("Server deploy on : " + Thread.currentThread().getName());
    Router mainRouter = Router.router(vertx);
    mainRouter.route().handler(BodyHandler.create());

    // Mount sub-routers with specific paths
    mainRouter.mountSubRouter("/api/credentials", new CredentialsRouter(vertx).getRouter());
    mainRouter.mountSubRouter("/api/discovery", new DiscoveryRouter(vertx).getRouter());
    mainRouter.mountSubRouter("/api/objects", new ObjectRouter().getRouter(vertx));

    // Add a root handler
    mainRouter.route("/").handler(ctx -> {
      ctx.response()
        .putHeader("content-type", "text/plain")
        .end("Welcome to the API");
    });

    vertx.createHttpServer()
      .requestHandler(mainRouter)
      .listen(8080, result -> {
        if (result.succeeded()) {
          System.out.println("HTTP server started on port 8080");
        } else {
          System.out.println("Failed to start HTTP server: " + result.cause());
        }
      });

  }
}
