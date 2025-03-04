package com.motadata.NMSLiteUsingVertex.database;

import com.motadata.NMSLiteUsingVertex.Main;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class QueryHandler {

  // Instance-level pool
  private static final Pool pool = com.motadata.NMSLiteUsingVertex.database.DatabaseClient.getPool(Main.vertx());

  // Generalized save
  public static Future<Void> save(String tableName, JsonObject payload)
  {
    StringBuilder columns = new StringBuilder();

    StringBuilder placeholders = new StringBuilder();

    Tuple tuple = Tuple.tuple();

    int index = 1;

    for (Map.Entry<String, Object> entry : payload.getMap().entrySet())
    {
      String column = entry.getKey();

      columns.append(column);

      placeholders.append("$").append(index++);

      tuple.addValue(entry.getValue());

      if (index <= payload.size())
      {
        columns.append(", ");

        placeholders.append(", ");
      }
    }

    String query = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders);

    return pool.preparedQuery(query)
      .execute(tuple)
      .mapEmpty();
  }

  // save and return the ID of the newly inserted row
  public static Future<String> saveAndGetById(String tableName, JsonObject payload)
  {
    StringBuilder columns = new StringBuilder();

    StringBuilder placeholders = new StringBuilder();

    Tuple tuple = Tuple.tuple();

    int index = 1;

    for (Map.Entry<String, Object> entry : payload.getMap().entrySet())
    {
      String column = entry.getKey();

      columns.append(column);

      placeholders.append("$").append(index++);

      tuple.addValue(entry.getValue());

      if (index <= payload.size())
      {
        columns.append(", ");

        placeholders.append(", ");
      }
    }

    String query = String.format("INSERT INTO %s (%s) VALUES (%s) RETURNING id", tableName, columns, placeholders);

    return pool.preparedQuery(query)
      .execute(tuple)
      .map(rows ->
      {
        if (rows.size() == 0)
        {
          throw new RuntimeException("Insert failed: no ID returned");
        }
        Row row = rows.iterator().next();

        return row.getString("id");
      });
  }

  // Generalized SELECT ALL
  public static Future<List<JsonObject>> getAll(String tableName)
  {
    String query = String.format("SELECT * FROM %s", tableName);

    return pool.query(query)
      .execute()
      .map(rows ->
      {
        List<JsonObject> results = new ArrayList<>();

        for (Row row : rows)
        {
          JsonObject obj = new JsonObject();
          for (int i = 0; i < row.size(); i++) {
            String column = row.getColumnName(i);
            Object value = row.getValue(i);
            obj.put(column, value);
          }
          results.add(obj);
        }
        return results;
      });
  }

  // Generalized SELECT BY CONDITION
  public static Future<JsonObject> getByfield(String tableName, String condition, String values)
  {
    if (condition == null || condition.isEmpty())
    {
      return Future.failedFuture("Condition required for select_by");
    }

    String query = String.format("SELECT * FROM %s WHERE %s", tableName, condition);

    Tuple tuple = Tuple.of(values);

    return pool.preparedQuery(query)
      .execute(tuple)
      .map(rows ->
      {
        if (rows.size() == 0)
        {
          return null;
        }

        Row row = rows.iterator().next();

        JsonObject obj = new JsonObject();

        for (int i = 0; i < row.size(); i++)
        {
          String column = row.getColumnName(i);

          Object value = row.getValue(i);

          obj.put(column, value);
        }

        return obj;
      });
  }

  // Find by ID
  public static Future<JsonObject> findById(String tableName, String id) {
    return getByfield(tableName, "id = $1", id);
  }

  // Generalized UPDATE :find by  field & update
  public static Future<Void> updateByField(String tableName, JsonObject payload, String condition, Object... conditionValues)
  {
    if (condition == null || condition.isEmpty())
    {
      return Future.failedFuture("Condition required for update");
    }

    StringBuilder setClause = new StringBuilder();

    Tuple tuple = Tuple.tuple();

    int index = 1;

    for (Map.Entry<String, Object> entry : payload.getMap().entrySet())
    {
      String column = entry.getKey();

      setClause.append(column).append(" = $").append(index++);

      tuple.addValue(entry.getValue());

      if (index <= payload.size())
      {
        setClause.append(", ");
      }
    }

    for (Object value : conditionValues)
    {
      tuple.addValue(value);
    }

    String query = String.format("UPDATE %s SET %s WHERE %s", tableName, setClause, condition);

    return pool.preparedQuery(query)
      .execute(tuple)
      .mapEmpty();
  }

  // Genralized get all object based on mutiple id
  public static Future<List<JsonObject>> getAllByIds(String tableName, JsonArray object_ids) {
    Promise<List<JsonObject>> promise = Promise.promise();

    List<String> ids = IntStream.range(0, object_ids.size())
      .mapToObj(object_ids::getString)
      .collect(Collectors.toList());

    String placeholders = IntStream.range(0, ids.size())
      .mapToObj(i -> "$" + (i + 1))
      .collect(Collectors.joining(","));

    String sql = "SELECT o.id, o.ip, o.is_discovered , o.port, c.username, c.password " + "FROM "+ tableName +" o " +
      "INNER JOIN credential c ON o.credential_id = c.id " +
      "WHERE o.id IN (" + placeholders + ")";

    Tuple params = Tuple.tuple();
    ids.forEach(params::addString);

    pool
      .preparedQuery(sql)
      .execute(params)
      .onSuccess(rows -> {
        List<JsonObject> result = new ArrayList<>();
        for (Row row : rows) {
          JsonObject json = new JsonObject()
            .put("id", row.getString("id"))
            .put("ip", row.getString("ip"))
            .put("is_discovered", row.getString("is_discovered"))
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
