package com.motadata.NMSLiteUsingVertex.database;

import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class DatabaseClient
{
    private static final Logger LOGGER = AppLogger.getLogger();

    private static Pool pool;

    // initialize database(check database exists if not then crate according to schema)
    public static Future<Void> initializeDatabase(Vertx vertx)
    {
        Promise<Void> promise = Promise.promise();

        PgConnectOptions connectOptions = new PgConnectOptions()
        .setPort(DATABASE_PORT)
        .setHost(DATABASE_HOST)
        .setDatabase(DEFAULT_DATABASE_NAME) // Connect to default DB
        .setUser(DATABASE_USERNAME)
        .setPassword(DATABASE_PASSWORD);

        var poolOptions = new PoolOptions().setMaxSize(MAX_POOL_SIZE);
        pool = Pool.pool(vertx, connectOptions, poolOptions);

        // Check if database exists
        var checkDbQuery = "SELECT 1 FROM pg_database WHERE datname = 'nms_lite_20'";

        pool.query(checkDbQuery).execute(checkDbQueryResult ->
        {
            if (checkDbQueryResult.succeeded() && checkDbQueryResult.result().size() > 0)
            {
                LOGGER.info("Database nms_lite_20 already exists.");

                switchToNewDatabase(vertx)
                .onSuccess(result -> promise.complete())
                .onFailure(err -> promise.fail(err.getMessage()));
            }
            else
            {
                LOGGER.info("Database nms_lite_20 does not exist, creating...");

                pool.query("CREATE DATABASE nms_lite_20").execute(createQueryResult ->
                {
                    if (createQueryResult.succeeded())
                    {
                        LOGGER.info("Database nms_lite_20 created successfully.");
                        switchToNewDatabase(vertx).onComplete(promise);
                    }
                    else
                    {
                        LOGGER.warning("Failed to create database nms_lite_20: " + createQueryResult.cause().getMessage());
                        promise.fail("Failed to create database nms_lite_20"); // ✅ Fail if DB creation fails
                    }
                });
            }
        });
        return promise.future();
    }

    // create pool using database user credential
    private static Future<Void> switchToNewDatabase(Vertx vertx)
    {
        if (pool!=null)
        {
            pool.close();
            LOGGER.info("Closed existing database connection pool.");
        }

        PgConnectOptions newConnectOptions = new PgConnectOptions()
            .setPort(DATABASE_PORT)
            .setHost(DATABASE_HOST)
            .setDatabase(DATABASE_NAME)
            .setUser(DATABASE_USERNAME)
            .setPassword(DATABASE_PASSWORD);

        var newPoolOptions = new PoolOptions().setMaxSize(MAX_POOL_SIZE);
        pool = Pool.pool(vertx, newConnectOptions, newPoolOptions);

        return createTables(); // ✅ Ensure table creation happens before resolving
    }

    // create table according to schema
    private static Future<Void> createTables()
    {
        Promise<Void> promise = Promise.promise();

        var createTablesQuery = """
        -- Credential Table
        CREATE TABLE IF NOT EXISTS credential (
            credential_id SERIAL PRIMARY KEY,
            credential_name VARCHAR(255) UNIQUE NOT NULL,
            credential_data JSONB NOT NULL,
            system_type VARCHAR(255) NULL
        );
        
        -- Discovery Table
        CREATE TABLE IF NOT EXISTS discovery (
            discovery_id SERIAL PRIMARY KEY,
            credential_id INT NOT NULL,
            ip VARCHAR(255) NOT NULL,
            port INTEGER NOT NULL CHECK (port BETWEEN 0 AND 65535),
            discovery_status VARCHAR(50) NOT NULL DEFAULT 'pending',
        
            CONSTRAINT fk_discovery_credential
                FOREIGN KEY (credential_id) REFERENCES credential(credential_id)
                ON DELETE RESTRICT
        );
        
        -- Provisioned Objects Table
        CREATE TABLE IF NOT EXISTS provisioned_objects (
            object_id SERIAL PRIMARY KEY,
            ip VARCHAR(255) UNIQUE NOT NULL,
            credential_id INT NOT NULL,
            availability_status VARCHAR(255) NOT NULL DEFAULT 'UP',
            pollinterval INT,
            CONSTRAINT fk_provisioned_credential
                FOREIGN KEY (credential_id) REFERENCES credential(credential_id)
                ON DELETE RESTRICT
        );
        
        -- Polling Table
        CREATE TABLE IF NOT EXISTS polling_results (
            ip VARCHAR(255) NOT NULL,
            timestamp BIGINT NOT NULL,
            counters JSONB NOT NULL,
            PRIMARY KEY (ip, timestamp)
        );
        """;

        pool.query(createTablesQuery).execute(createTableQueryResult ->
        {
            if (createTableQueryResult.succeeded())
            {
                LOGGER.info("Tables created successfully.");
                promise.complete();
            }
            else
            {
                LOGGER.severe("Failed to create tables: " + createTableQueryResult.cause().getMessage());
                promise.fail(createTableQueryResult.cause());
            }
        });
        return promise.future();
    }

    // return postgres pool
    public static Pool getPool()
    {
        return pool;
    }

    public static void closePool()
    {
        if (pool!=null)
        {
            pool.close();
            LOGGER.info("Database connection pool closed successfully.");
        }
        else
        {
            LOGGER.info("Database connection pool is already null or closed.");
        }
    }
}
