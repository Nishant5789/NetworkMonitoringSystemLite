package com.motadata.NMSLiteUsingVertex.services;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ObjectService {

  private final Pool pool;

  public ObjectService(Vertx vertx) {
    this.pool = com.motadata.NMSLiteUsingVertex.database.DatabaseClient.getPool(vertx);
  }

  public Future<List<JsonObject>> getAllObjects(JsonArray object_ids) {
    Promise<List<JsonObject>> promise = Promise.promise();

    List<Integer> ids = object_ids.stream()
      .map(id -> ((Number) id).intValue()).toList();

    String placeholders = IntStream.range(0, ids.size())
      .mapToObj(i -> "$" + (i + 1))
      .collect(Collectors.joining(","));

    String sql = "SELECT o.id, o.ip, o.port, c.username, c.password " + "FROM object o " +
      "INNER JOIN credential c ON o.credentiali_d = c.id " +
      "WHERE o.id IN (" + placeholders + ")";

    Tuple params = Tuple.tuple();
    ids.forEach(params::addInteger);

    pool
      .preparedQuery(sql)
      .execute(params)
      .onSuccess(rows -> {
        List<JsonObject> result = new ArrayList<>();
        for (Row row : rows) {
          JsonObject json = new JsonObject()
            .put("id", row.getInteger("id"))
            .put("ip", row.getString("ip"))
            .put("port", row.getString("port").trim())
            .put("username", row.getString("username"))
            .put("password", row.getString("password"));
          result.add(json);
        }
        System.out.println("Query result: " + result);
        promise.complete(result);
      })
      .onFailure(err -> {
        System.err.println("Query failed: " + err.getMessage());
        promise.fail(err);
      });
    return promise.future();
  }
}
