package com.motadata.NMSLiteUsingVertex.database;

import com.motadata.NMSLiteUsingVertex.utils.Utils;
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
      Object value = entry.getValue();

      columns.append(column);
      placeholders.append("$").append(index++);

      if (value instanceof Map)
      {
        tuple.addValue(new JsonObject((Map<String, Object>) value).encode()); // Manual wrap avoids JsonObject.mapFrom()
      }
      else if (value instanceof List)
      {
        tuple.addValue(new JsonArray((List<?>) value).encode());
      }
      else
      {
        tuple.addValue(value);
      }

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
    return getByField(tableName, Utils.getIdColumnByTable(tableName), id);
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
      var value = entry.getValue();

      setClause.append(column).append(" = $").append(index++);

      if (value instanceof Map)
      {
        tuple.addValue(new JsonObject((Map<String, Object>) value).encode());
      }
      else
      {
        tuple.addValue(value);
      }

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

  // Genralized find by ID Using join
  public static Future<JsonObject> getByFieldWithJoin(String tableName1, String tableName2, String joiningOnField, String fieldName, String fieldValue)
  {
    var query = "SELECT t1.*, t2.* " + "FROM " + tableName1 + " t1 " + "JOIN " + tableName2 + " t2 " + "ON t1." + joiningOnField + " = t2." + joiningOnField + " " + "WHERE t1." + fieldName + " = $1";

    return pool.preparedQuery(query)
      .execute(Tuple.of(fieldValue))
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

  public static Future<List<JsonObject>> getAllWithJoin(String tableName1, String tableName2, String joiningOnField)
  {
    var query = "SELECT t1.*, t2.* " + "FROM " + tableName1 + " t1 " + "JOIN " + tableName2 + " t2 " + "ON t1." + joiningOnField + " = t2." + joiningOnField;

    return pool.preparedQuery(query)
      .execute()
      .map(rows ->
      {
        List<JsonObject> resultList = new ArrayList<>();

        for (Row row : rows)
        {
          JsonObject obj = new JsonObject();

          for (int i = 0; i < row.size(); i++)
          {
            String column = row.getColumnName(i);
            Object val = row.getValue(i);
            obj.put(column, val);
          }
          resultList.add(obj);
        }
        return resultList;
      });
  }

  public static Future<List<JsonObject>> getAllByField(String tableName, String fieldName, Object fieldValue)
  {
    var query = String.format("SELECT * FROM %s WHERE %s = $1", tableName, fieldName);

    return pool.preparedQuery(query)
      .execute(Tuple.of(fieldValue))
      .map(rows ->
      {
        List<JsonObject> resultList = new ArrayList<>();

        if (rows.size() == 0) return resultList;

        for (Row row : rows)
        {
          JsonObject obj = new JsonObject();

          for (int i = 0; i < row.size(); i++)
          {
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
          resultList.add(obj);
        }
        return resultList;
      });
  }
}
