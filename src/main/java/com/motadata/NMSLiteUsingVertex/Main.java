package com.motadata.NMSLiteUsingVertex;

import com.motadata.NMSLiteUsingVertex.database.DatabaseClient;
import com.motadata.NMSLiteUsingVertex.api.Server;
import com.motadata.NMSLiteUsingVertex.services.Discovery;
import com.motadata.NMSLiteUsingVertex.services.ObjectManager;
import com.motadata.NMSLiteUsingVertex.services.Poller;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
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

  private static final int POLLER_VERTICLE_INSTANCES = 1;

  private static final int PROVISION_VERTICLE_INSTANCES = 1;

  private static final int VERTX_WORKER_POOL_SIZE = 20;

  private static final int EVENT_BUS_CONNECTION_TIMEOUT = 5000;

  private static final int EVENT_BUS_IDLE_TIMEOUT = 180000;

  private static final int EVENT_BUS_RECONNECT_ATTEMPTS = 2;

  private static final int EVENT_BUS_RECONNECT_INTERVAL = 10000;

//  private static final Logger LOGGER =  AppLogger.getLogger();

  private static final Logger LOGGER =  Logger.getLogger(Main.class.getName());

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

    initializeDatabseAndUpdateCache(vertx)
      .onComplete(ar ->
      {
          if (ar.succeeded())
          {
            LOGGER.info("Database initialized and polling data cache updated successfully.");
          }
          else
          {
            LOGGER.severe("Failed to initialize database or update polling data cache: " + ar.cause().getMessage());
            ar.cause().printStackTrace();
          }
      });

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

      .compose(v -> deployVerticle(Discovery.class.getName(), new DeploymentOptions().setInstances(DISCOVERY_VERTICLE_INSTANCES)))

      .compose(v -> deployVerticle(ObjectManager.class.getName(), new DeploymentOptions().setInstances(PROVISION_VERTICLE_INSTANCES)))

      .compose(v -> deployVerticle(Poller.class.getName(), new DeploymentOptions().setInstances(POLLER_VERTICLE_INSTANCES).setThreadingModel(ThreadingModel.WORKER)
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

  // intializedatabase and Update Cache
  public static Future<Object> initializeDatabseAndUpdateCache(Vertx vertx)
  {
    return DatabaseClient.initializeDatabase(vertx)
      .compose(v -> Utils.updatePollingDataCache());
  }
}
