package com.motadata.NMSLiteUsingVertex.database;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;


public class QueryHandler
{
  // Instance-level pool
  private static final Pool pool = DatabaseClient.getPool();

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

      tuple.addValue(entry.getValue() instanceof List ? new JsonArray((List<?>) entry.getValue()).encode() : entry.getValue());

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
          for (int i = 0; i < row.size(); i++)
          {
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
  public static Future<JsonObject> getByField(String tableName, String fieldName, String fieldvalue)
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

  // Generalized Find by ID
  public static Future<JsonObject> getById(String tableName, String id)
  {
    String tableId = switch (tableName)
    {
      case CREDENTIAL_TABLE -> CREDENTIAL_ID_KEY;
      case DISCOVERY_TABLE -> DISCOVERY_ID_KEY;
      case PROVISIONED_OBJECTS_TABLE -> OBJECT_ID_KEY;
      default -> ID_KEY;
    };
    return getByField(tableName, tableId, id);
  }

  // Generalized UPDATE : find by field & update
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

  // handle delete by using tableId
  public static Future<Boolean> deleteById(String tableName, String idValue)
  {
    if (!List.of(DISCOVERY_TABLE, CREDENTIAL_TABLE, POLLING_RESULTS_TABLE, PROVISIONED_OBJECTS_TABLE).contains(tableName))
    {
      return Future.failedFuture("Invalid table name: " + tableName);
    }

    String tableId = switch (tableName)
    {
      case CREDENTIAL_TABLE -> CREDENTIAL_ID_KEY;
      case DISCOVERY_TABLE -> DISCOVERY_ID_KEY;
      case PROVISIONED_OBJECTS_TABLE -> OBJECT_ID_KEY;
      default -> ID_KEY;
    };

    String query = "DELETE FROM " + tableName + " WHERE " + tableId + " = $1";

    Tuple params = Tuple.of(Integer.parseInt(idValue));

    return pool.preparedQuery(query)
      .execute(params)
      .map(rows -> rows.rowCount() > 0);
  }
}
