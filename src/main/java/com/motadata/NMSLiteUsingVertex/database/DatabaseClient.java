package com.motadata.NMSLiteUsingVertex.database;

import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

import java.util.logging.Logger;

public class DatabaseClient {
  private static final Logger LOGGER = AppLogger.getLogger();

  private static Pool pool;

  // initialize database(check database exists if not then crate according to schema)
  public static Future<Void> initializeDatabase(Vertx vertx) {
    Promise<Void> promise = Promise.promise();

    PgConnectOptions connectOptions = new PgConnectOptions()
      .setPort(5432)
      .setHost("localhost")
      .setDatabase("postgres") // Connect to default DB
      .setUser("postgres")
      .setPassword("1234");

    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
    pool = Pool.pool(vertx, connectOptions, poolOptions);

    // Check if database exists
    String checkDbQuery = "SELECT 1 FROM pg_database WHERE datname = 'nms_lite_16'";

    pool.query(checkDbQuery).execute(ar ->
    {
      if (ar.succeeded() && ar.result().size() > 0) {
        LOGGER.info("Database nms_lite_16 already exists.");
        switchToNewDatabase(vertx).onComplete(promise); // ✅ Ensure switching
      } else {
        LOGGER.info("Database nms_lite_16 does not exist, creating...");

        pool.query("CREATE DATABASE nms_lite_16").execute(createAr ->
        {
          if (createAr.succeeded()) {
            LOGGER.info("Database nms_lite_16 created successfully.");
            switchToNewDatabase(vertx).onComplete(promise); // ✅ Only switch if created
          } else {
            LOGGER.warning("Failed to create database nms_lite_16: " + createAr.cause().getMessage());
            promise.fail(createAr.cause()); // ✅ Fail if DB creation fails
          }
        });
      }
    });

    return promise.future();
  }

  // create pool using database user credential
  private static Future<Void> switchToNewDatabase(Vertx vertx) {
    if (pool != null) {
      pool.close();
      LOGGER.info("Closed existing database connection pool.");
    }

    PgConnectOptions newConnectOptions = new PgConnectOptions()
      .setPort(5432)
      .setHost("localhost")
      .setDatabase("nms_lite_16")
      .setUser("postgres")
      .setPassword("1234");

    PoolOptions newPoolOptions = new PoolOptions().setMaxSize(5);
    pool = Pool.pool(vertx, newConnectOptions, newPoolOptions);

    return createTables(); // ✅ Ensure table creation happens before resolving
  }

  // create table according to schema
  private static Future<Void> createTables() {
    Promise<Void> promise = Promise.promise();

    String createTablesQuery = """
          -- Create ENUM types
          DO $$ BEGIN
              CREATE TYPE os_type AS ENUM ('linux', 'windows', 'snmp');
          EXCEPTION WHEN duplicate_object THEN null;
          END $$;

          DO $$ BEGIN
              CREATE TYPE discovery_status_enum AS ENUM ('pending', 'completed', 'failed');
          EXCEPTION WHEN duplicate_object THEN null;
          END $$;

          DO $$ BEGIN
              CREATE TYPE provisioning_status_enum AS ENUM ('pending', 'active', 'failed');
          EXCEPTION WHEN duplicate_object THEN null;
          END $$;

          -- Credential Table
          CREATE TABLE IF NOT EXISTS credential (
              credential_id SERIAL PRIMARY KEY,
              username VARCHAR(255) NOT NULL,
              password VARCHAR(255) NOT NULL
          );

          -- Discovery Table
          CREATE TABLE IF NOT EXISTS discovery (
              discovery_id SERIAL PRIMARY KEY,
              credential_id INT NOT NULL,
              ips JSONB NOT NULL,
              port INTEGER NOT NULL CHECK (port BETWEEN 0 AND 65535),
              type os_type NOT NULL,
              discovery_status discovery_status_enum NOT NULL DEFAULT 'pending',

              CONSTRAINT fk_discovery_credential
                  FOREIGN KEY (credential_id) REFERENCES credential(credential_id)
                  ON DELETE RESTRICT
          );

          -- Provisioned Objects Table
          CREATE TABLE IF NOT EXISTS provisioned_objects (
              object_id SERIAL PRIMARY KEY,
              object_data JSONB NOT NULL,
              pollinterval INT,
              lastpolltime BIGINT,
              provisioning_status provisioning_status_enum NOT NULL DEFAULT 'pending'
          );

          -- Polling Table
          CREATE TABLE IF NOT EXISTS polling_results (
              object_id INT NOT NULL,
              timestamp BIGINT NOT NULL,
              counters JSONB NOT NULL,
              PRIMARY KEY (object_id, timestamp),

              CONSTRAINT fk_polling_monitor
                  FOREIGN KEY (object_id) REFERENCES provisioned_objects(object_id)
                  ON DELETE RESTRICT
          );
      """;

    pool.query(createTablesQuery).execute(ar ->
    {
      if (ar.succeeded()) {
        LOGGER.info("Tables created successfully.");
        promise.complete();
      } else {
        LOGGER.severe("Failed to create tables: " + ar.cause().getMessage());
        promise.fail(ar.cause());
      }
    });

    return promise.future();
  }

  // return postgres pool
  public static Pool getPool() {
    return pool;
  }

  public static void closePool() {
    if (pool != null) {
      pool.close();
      LOGGER.info("Database connection pool closed successfully.");
    } else {
      LOGGER.info("Database connection pool is already null or closed.");
    }
  }
}
