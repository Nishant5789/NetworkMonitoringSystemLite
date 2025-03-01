package com.motadata.NMSLiteUsingVertex.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;

public class DiscoveryService {
  private final Pool pool;

  public DiscoveryService(Vertx vertx) {
    this.pool = com.motadata.NMSLiteUsingVertex.database.DatabaseClient.getPool(vertx);
  }

  public Future<JsonObject> findById(Integer id) {
    return pool
      .preparedQuery("SELECT * FROM credential WHERE id = $1")
      .execute(Tuple.of(id))
      .map(rows -> {
        if (rows.size() == 0) {
          return null;
        }
        Row row = rows.iterator().next();
        return new JsonObject()
          .put("id", row.getInteger("id"))
          .put("name", row.getString("name"))
          .put("username", row.getString("username"))
          .put("password", row.getString("password"));
      });
  }

  public Future<Void> save(JsonObject payload){

    String ip = payload.getString("ip");
    Integer credential_id = payload.getInteger("credential_id");
    String port = payload.getString("port");

    return pool.preparedQuery("INSERT INTO object (ip, credential_id, port) VALUES ($1, $2, $3)")
      .execute(Tuple.of(ip, credential_id, port))
      .mapEmpty();
  }

}
