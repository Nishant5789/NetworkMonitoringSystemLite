package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.routes.CredentialsRouter;
import com.motadata.NMSLiteUsingVertex.routes.DiscoveryRouter;
import com.motadata.NMSLiteUsingVertex.routes.ProvisionRouter;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.logging.Logger;

public class Server extends AbstractVerticle
{
  private static final Logger LOGGER = AppLogger.getLogger();

  @Override
  public void start()
  {
    LOGGER.info("Server Verticle deployed: " + Thread.currentThread().getName());

    Router mainRouter = Router.router(vertx);

    mainRouter.route().handler(BodyHandler.create());

    mainRouter.route("/api/credentials/*").subRouter(CredentialsRouter.getRouter());

    mainRouter.route("/api/discovery/*").subRouter(DiscoveryRouter.getRouter());

    mainRouter.route("/api/provision/*").subRouter(ProvisionRouter.getRouter());

    mainRouter.route("/").handler(ctx ->
    {
      ctx.response()
        .putHeader("content-type", "text/plain")
        .end("Welcome to the API");
    });

    vertx.createHttpServer()
      .requestHandler(mainRouter)
      .listen(8080, result ->
      {
        if (result.succeeded())
        {
          LOGGER.info("HTTP server started on port 8080");
        }
        else
        {
          LOGGER.severe("Failed to start HTTP server: " + result.cause());
        }
      });
  }
}
