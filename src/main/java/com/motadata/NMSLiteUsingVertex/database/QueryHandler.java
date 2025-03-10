package com.motadata.NMSLiteUsingVertex.database;

import com.motadata.NMSLiteUsingVertex.Main;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.postgresql.util.PGobject;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.POLLER_RESULTS_TABLE;


public class QueryHandler
{
  // Instance-level pool
  private static final Pool pool = com.motadata.NMSLiteUsingVertex.database.DatabaseClient.getPool();

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
  public static Future<JsonObject> getByfield(String tableName, String fieldName, String fieldvalue)
  {
    var condition = String.format("%s = '%s'",fieldName, fieldvalue);

    var query = String.format("SELECT * FROM %s WHERE %s", tableName, condition);

    return pool.preparedQuery(query)
      .execute()
      .map(rows ->
      {
        if (rows.size() == 0) return null;

        Row row = rows.iterator().next();

        JsonObject obj = new JsonObject();

        for (int i = 0; i < row.size(); i++)
        {
          String column = row.getColumnName(i);
          Object val = row.getValue(i);

          obj.put(column, val);
        }
        return obj;
      });
  }

  // Find by ID
  public static Future<JsonObject> findById(String tableName, String id)
  {
    return getByfield(tableName, "id", id);
  }

  // Generalized UPDATE :find by  field & update
  public static Future<Void> updateByField(String tableName, JsonObject payload, String fieldName, Object fieldvalue)
  {
    var condition = String.format("%s = '%s'",fieldName, fieldvalue);

    var setClause = new StringBuilder();

    Tuple tuple = Tuple.tuple();

    int index = 1;

    for (Map.Entry<String, Object> entry : payload.getMap().entrySet())
    {
      var column = entry.getKey();

      setClause.append(column).append(" = $").append(index++);

      tuple.addValue(entry.getValue());

      if (index <= payload.size())
      {
        setClause.append(", ");
      }
    }

    String query = String.format("UPDATE %s SET %s WHERE %s", tableName, setClause, condition);

    return pool.preparedQuery(query)
      .execute(tuple)
      .mapEmpty();
  }

  // get device data using discoveryId
  public static Future<JsonObject> getDeviceByDiscoveryId(String discoveryId)
  {
    var condition = String.format("d.id = %s", discoveryId);
    var query = String.format("SELECT d.id, d.ip, d.port, c.username, c.password, d.type AS plugin_engine, d.status " +
      "FROM discovery d " +
      "JOIN credential c ON d.credential_id = c.id " +
      "WHERE %s", condition);

    return pool.preparedQuery(query)
      .execute()
      .map(rows ->
      {
        if (rows.size() == 0) return null;

        Row row = rows.iterator().next();
        JsonObject obj = new JsonObject();

        for (int i = 0; i < row.size(); i++)
        {
          obj.put(row.getColumnName(i), row.getValue(i));
        }
        return obj;
      });
  }

  // get polling data using discoveryId
  public static Future<JsonArray> getPollerData(String discoveryId)
  {
    var query = String.format("SELECT * FROM %s WHERE discovery_id = %S", POLLER_RESULTS_TABLE, discoveryId);

    return pool.preparedQuery(query)
      .execute()
      .map(rows ->
      {
        JsonArray jsonArray = new JsonArray();

        if (rows.size() == 0) return null;

        for (Row row : rows) {
          JsonObject obj = new JsonObject();

          for (int i = 0; i < row.size(); i++) {
            String column = row.getColumnName(i);
            Object val = row.getValue(i);

            if (val instanceof PGobject pgObject && "jsonb".equalsIgnoreCase(pgObject.getType()))
            {
              val = new JsonObject(pgObject.getValue());
            }
            else if (val instanceof OffsetDateTime offsetDateTime)
            {
              val = offsetDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
            obj.put(column, val);
          }
          jsonArray.add(obj);
        }
        return jsonArray;
      });
  }

  // handle delete by using tableId
  public static Future<Boolean> deleteById(String tableName, String id)
  {
    if (!List.of("discovery", "poller_results", "credential").contains(tableName))
    {
      return Future.failedFuture("Invalid table name: " + tableName);
    }

    String query = "DELETE FROM " + tableName + " WHERE id = $1";

    Tuple params = Tuple.of(Integer.parseInt(id));

    return pool.preparedQuery(query)
      .execute(params)
      .map(rows -> rows.rowCount() > 0);
  }
}
