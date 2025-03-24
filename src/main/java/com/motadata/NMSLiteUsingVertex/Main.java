package com.motadata.NMSLiteUsingVertex;

import com.motadata.NMSLiteUsingVertex.database.DatabaseClient;
import com.motadata.NMSLiteUsingVertex.api.Server;
import com.motadata.NMSLiteUsingVertex.messaging.ZmqMessenger;
import com.motadata.NMSLiteUsingVertex.services.Discovery;
import com.motadata.NMSLiteUsingVertex.services.ObjectManager;
import com.motadata.NMSLiteUsingVertex.services.Poller;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;

import java.util.logging.Logger;

public class Main
{
  private static final int DISCOVERY_VERTICLE_INSTANCES = 1;

  private static final int SERVER_VERTICLE_INSTANCES = 1;

  private static final int ZMQMESSENGER_VERTICLE_INSTANCES = 1;

  private static final int POLLER_VERTICLE_INSTANCES = 1;

  private static final int OBJECT_VERTICLE_INSTANCES = 1;

  private static final int VERTX_WORKER_POOL_SIZE = 20;

  private static final int EVENT_BUS_CONNECTION_TIMEOUT = 5000;

  private static final int EVENT_BUS_IDLE_TIMEOUT = 180000;

  private static final int EVENT_BUS_RECONNECT_ATTEMPTS = 2;

  private static final int EVENT_BUS_RECONNECT_INTERVAL = 10000;

//  private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final Logger LOGGER =  AppLogger.getLogger();

  private static final Vertx vertx = Vertx.vertx(new VertxOptions()
    .setWorkerPoolSize(VERTX_WORKER_POOL_SIZE)
    .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors())
    .setEventBusOptions(new EventBusOptions()
      .setConnectTimeout(EVENT_BUS_CONNECTION_TIMEOUT)
      .setIdleTimeout(EVENT_BUS_IDLE_TIMEOUT)
      .setReconnectAttempts(EVENT_BUS_RECONNECT_ATTEMPTS)
      .setReconnectInterval(EVENT_BUS_RECONNECT_INTERVAL))
  );

  // return vertex instance
  public static Vertx vertx()
  {
    return vertx;
  }

  public static void main(String[] args)
  {
    Utils.startGOPlugin()
      .compose(goResult ->
      {
        LOGGER.info(goResult);

        return initializeDatabaseAndUpdateCache(vertx);
      })
      .compose(dbResult ->
      {
        LOGGER.info("Database initialized and polling data cache updated successfully.");

        return deployVerticles();
      })
      .onSuccess(result ->
      {
        LOGGER.info("All verticles deployed successfully.");

        System.out.println("NMS Lite server is starting.......");
      })
      .onFailure(err ->
      {
        LOGGER.severe("Intialization process failed due to: " + err.getMessage());
      });

    // Add a shutdown hook for graceful termination
    Runtime.getRuntime().addShutdownHook(new Thread(() ->
    {
      LOGGER.info("Shutdown signal received. Cleaning up resources...");

      // close Postgrel pool
      DatabaseClient.closePool();

      vertx.close(ar ->
      {
        if (ar.succeeded()) {
          LOGGER.info("Vert.x closed successfully.");
        } else {
          LOGGER.severe("Failed to close Vert.x: " + ar.cause().getMessage());
        }
      });
    }));
  }

  // handle initializeDatabase And UpdateCache
  public static Future<Object> initializeDatabaseAndUpdateCache(Vertx vertx)
  {
    return DatabaseClient.initializeDatabase(vertx)
      .compose(v -> Utils.updateObjectQueueFromDatabase());
  }

  // deploy verticals sequentially
  private static Future<Void> deployVerticles()
  {
    return deployVerticle(Server.class.getName(), new DeploymentOptions().setInstances(SERVER_VERTICLE_INSTANCES))

      .compose(v -> deployVerticle(ZmqMessenger.class.getName(), new DeploymentOptions().setInstances(ZMQMESSENGER_VERTICLE_INSTANCES)))

      .compose(v -> deployVerticle(Discovery.class.getName(), new DeploymentOptions().setInstances(DISCOVERY_VERTICLE_INSTANCES)))

      .compose(v -> deployVerticle(ObjectManager.class.getName(), new DeploymentOptions().setInstances(OBJECT_VERTICLE_INSTANCES)))

      .compose(v -> deployVerticle(Poller.class.getName(), new DeploymentOptions().setInstances(POLLER_VERTICLE_INSTANCES)));
  }

  // handle deploy vertical by class name & deployment options
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
