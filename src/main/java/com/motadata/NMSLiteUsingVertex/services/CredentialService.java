package com.motadata.NMSLiteUsingVertex.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;

public class CredentialService {
  private final Pool pool;

  public CredentialService(Vertx vertx) {
    this.pool = com.motadata.NMSLiteUsingVertex.database.DatabaseClient.getPool(vertx);
  }

  // Save a new credential
  public Future<Void> save(JsonObject payload) {
    String name = payload.getString("name");
    String username = payload.getString("username");
    String password = payload.getString("password");

    return pool.preparedQuery("INSERT INTO credential (name, username, password) VALUES ($1, $2, $3)")
      .execute(Tuple.of(name, username, password))
      .mapEmpty();
  }

  // Get all credentials
  public Future<List<JsonObject>> getAll() {
    return pool
      .query("SELECT * FROM credential")
      .execute()
      .map(rows -> {
        List<JsonObject> credentials = new ArrayList<>();
        for (Row row : rows) {
          credentials.add(new JsonObject()
            .put("id", row.getInteger("id"))
            .put("name", row.getString("name"))
            .put("username", row.getString("username"))
            .put("password", row.getString("password")));
        }
        return credentials;
      });
  }

  // Find credential by name
  public Future<JsonObject> findByName(String name) {
    return pool
      .preparedQuery("SELECT * FROM credential WHERE name = $1")
      .execute(Tuple.of(name))
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

  // Update credential by name
  public Future<Void> update(String name, JsonObject payload) {
    String username = payload.getString("username");
    String password = payload.getString("password");

    return pool
      .preparedQuery("UPDATE credential SET username = $1, password = $2 WHERE name = $3")
      .execute(Tuple.of(username, password, name))
      .mapEmpty();
  }
}

