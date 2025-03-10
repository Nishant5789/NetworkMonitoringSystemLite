package com.motadata.NMSLiteUsingVertex.database;

import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

import java.util.logging.Logger;

public class DatabaseClient
{
  private static final Logger logger = AppLogger.getLogger();
  private static Pool pool;

  public static Future<Void> initializeDatabase(Vertx vertx)
  {
    Promise<Void> promise = Promise.promise();

    PgConnectOptions connectOptions = new PgConnectOptions()
      .setPort(5432)
      .setHost("localhost")
      .setDatabase("postgres")
      .setUser("postgres")
      .setPassword("1234");

    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

    pool = Pool.pool(vertx, connectOptions, poolOptions);

    pool.query("CREATE DATABASE NMS_LITE_5").execute(ar ->
    {
      if (ar.succeeded())
      {
        logger.info("Database NMS_LITE_5 created successfully.");
      }
      else
      {
          logger.warning("Database already exists or creation failed: " + ar.cause().getMessage());
      }

      switchToNewDatabase(vertx).onComplete(promise);
    });

    return promise.future();
  }

  private static Future<Void> switchToNewDatabase(Vertx vertx)
  {
    Promise<Void> promise = Promise.promise();

    if (pool != null)
    {
      pool.close();
      logger.info("Closed existing database connection pool.");
    }

    PgConnectOptions newConnectOptions = new PgConnectOptions()
      .setPort(5432)
      .setHost("localhost")
      .setDatabase("nms_lite_5")
      .setUser("postgres")
      .setPassword("1234");

    PoolOptions newPoolOptions = new PoolOptions().setMaxSize(5);
    pool = Pool.pool(vertx, newConnectOptions, newPoolOptions);

    createTables().onComplete(promise);

    return promise.future();
  }

  private static Future<Void> createTables()
  {
    Promise<Void> promise = Promise.promise();

    String createTablesQuery = """
            CREATE TABLE IF NOT EXISTS credential (
                id SERIAL PRIMARY KEY,
                name VARCHAR UNIQUE NOT NULL,
                username VARCHAR NOT NULL,
                password VARCHAR NOT NULL
            );

            CREATE TABLE IF NOT EXISTS discovery (
                id SERIAL PRIMARY KEY,
                credential_id INTEGER NOT NULL REFERENCES credential(id) ON DELETE CASCADE,
                ip VARCHAR NOT NULL,
                port INTEGER NOT NULL CHECK (port BETWEEN 0 AND 65535),
                type VARCHAR NOT NULL CHECK (type IN ('linux', 'windows', 'snmp')),
                status BOOLEAN NOT NULL DEFAULT FALSE
            );

            CREATE INDEX IF NOT EXISTS idx_discovery_credential ON discovery(credential_id);

            CREATE TABLE IF NOT EXISTS poller_results (
                id SERIAL PRIMARY KEY,
                discovery_id INTEGER NOT NULL REFERENCES discovery(id) ON DELETE CASCADE,
                ip VARCHAR NOT NULL,
                counter_result JSON NOT NULL,
                timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            );

            CREATE INDEX IF NOT EXISTS idx_poller_results_discovery ON poller_results(discovery_id);
        """;

    pool.query(createTablesQuery).execute(ar ->
    {
      if (ar.succeeded())
      {
        logger.info("Tables created successfully.");
        promise.complete();
      }
      else
      {
        logger.severe("Failed to create tables: "+ ar.cause().getMessage());
        promise.fail(ar.cause());
      }
    });

    return promise.future();
  }

  public static Pool getPool()
  {
    return pool;
  }
}
