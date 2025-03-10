package com.motadata.NMSLiteUsingVertex;

import com.motadata.NMSLiteUsingVertex.database.DatabaseClient;
import com.motadata.NMSLiteUsingVertex.services.Server;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.verticle.DiscoveryVerticle;
import com.motadata.NMSLiteUsingVertex.verticle.ProvisionVerticle;
import com.motadata.NMSLiteUsingVertex.verticle.PollerVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;
import java.util.logging.Logger;

public class Main
{
  private static final int DISCOVERY_VERTICLE_INSTANCES = 1;

  private static final int SERVER_VERTICLE_INSTANCES = 1;

  private static final int POLLER_VERTICLE_INSTANCES = 2;

  private static final int PROVISION_VERTICLE_INSTANCES = 1;

  private static final int VERTX_WORKER_POOL_SIZE = 20;

  private static final int EVENT_BUS_CONNECTION_TIMEOUT = 5000;

  private static final int EVENT_BUS_IDLE_TIMEOUT = 180000;

  private static final int EVENT_BUS_RECONNECT_ATTEMPTS = 2;

  private static final int EVENT_BUS_RECONNECT_INTERVAL = 10000;

  private static final Logger LOGGER =  AppLogger.getLogger();

  private static final Vertx vertx = Vertx.vertx( new VertxOptions()
    .setWorkerPoolSize(VERTX_WORKER_POOL_SIZE)
    .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors())
    .setEventBusOptions( new EventBusOptions()
        .setConnectTimeout(EVENT_BUS_CONNECTION_TIMEOUT)
        .setIdleTimeout(EVENT_BUS_IDLE_TIMEOUT)
        .setReconnectAttempts(EVENT_BUS_RECONNECT_ATTEMPTS)
        .setReconnectInterval(EVENT_BUS_RECONNECT_INTERVAL)
    )
  );

  // return vertex instance
  public static Vertx vertx()
  {
    return vertx;
  }

  public static void main(String[] args)
  {
    deployVerticles()
      .onComplete(ar ->
      {
        if (ar.succeeded())
        {
          LOGGER.info("All verticles deployed successfully.");
        }
        else
        {
          LOGGER.severe("Failed to deploy verticles: " + ar.cause().getMessage());
          vertx.close();
        }
      });

    DatabaseClient.initializeDatabase(vertx);

    // Add a shutdown hook for graceful termination
    Runtime.getRuntime().addShutdownHook(new Thread(() ->
    {
      LOGGER.info("Shutdown signal received. Cleaning up resources...");
      vertx.close(ar ->
      {
        if (ar.succeeded())
        {
          LOGGER.info("Vert.x closed successfully.");
        }
        else
        {
          LOGGER.severe("Failed to close Vert.x: " + ar.cause().getMessage());
        }
      });
    }));

  }

  // Hanlde deploy verticles sequencally
  private static Future<Void> deployVerticles()
  {
    return deployVerticle(Server.class.getName(), new DeploymentOptions().setInstances(SERVER_VERTICLE_INSTANCES))

      .compose(v -> deployVerticle(DiscoveryVerticle.class.getName(), new DeploymentOptions().setInstances(DISCOVERY_VERTICLE_INSTANCES)))

      .compose(v -> deployVerticle(ProvisionVerticle.class.getName(), new DeploymentOptions().setInstances(PROVISION_VERTICLE_INSTANCES)))

      .compose(v -> deployVerticle(PollerVerticle.class.getName(), new DeploymentOptions().setInstances(POLLER_VERTICLE_INSTANCES).setThreadingModel(ThreadingModel.WORKER)
      ));
  }

  // deploy verticle by classname & deploymentoptions
  private static Future<Void> deployVerticle(String className, DeploymentOptions options)
  {
    return Future.future(promise ->
      vertx.deployVerticle(className, options, ar ->
      {
        if (ar.succeeded())
        {
          promise.complete();
        }
        else
        {
          promise.fail(ar.cause());
        }
      })
    );
  }
}
