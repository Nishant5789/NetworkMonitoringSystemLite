package com.motadata.NMSLiteUsingVertex.database;

import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class DatabaseClient
{
  private static Pool pool;

// return pooloption configuration
  public static Pool getPool(Vertx vertx)
  {
    if (pool == null)
    {
      PgConnectOptions connectOptions = new PgConnectOptions().setPort(5432).setHost("localhost").setDatabase("NMS_LITE_3.0").setUser("postgres").setPassword("1234");

      PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

      pool = Pool.pool(vertx, connectOptions, poolOptions);
    }
    return pool;
  }
}

