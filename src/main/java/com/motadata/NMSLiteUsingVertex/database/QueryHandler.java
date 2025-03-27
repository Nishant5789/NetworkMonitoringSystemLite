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

    var tuple = Tuple.tuple();

    int index = 1;

    for (Map.Entry<String, Object> entry : payload.getMap().entrySet())
    {
      Object value = entry.getValue();

      columns.append(entry.getKey());
      placeholders.append("$").append(index++);

      if (value instanceof Map || value instanceof List)
      {
        tuple.addValue(value instanceof Map ? new JsonObject((Map<String, Object>) value).encode() : new JsonArray((List<?>) value).encode());
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

    return pool.preparedQuery(String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, columns, placeholders))
      .execute(tuple)
      .mapEmpty();
  }

  // Generalized SELECT ALL
  public static Future<List<JsonObject>> getAll(String tableName)
  {
    return pool.query(String.format("SELECT * FROM %s", tableName))
      .execute()
      .map(rows ->
      {
        List<JsonObject> results = new ArrayList<>();

        for (Row row : rows)
        {
          var obj = new JsonObject();
          for (int i = 0; i < row.size(); i++)
          {
            obj.put(row.getColumnName(i), row.getValue(i));
          }
          results.add(obj);
        }
        return results;
      });
  }

  // Generalized SELECT BY CONDITION
  public static Future<JsonObject> getByField(String tableName, String fieldName, String fieldvalue)
  {
    return pool.preparedQuery(String.format("SELECT * FROM %s WHERE %s", tableName, String.format("%s = '%s'",fieldName, fieldvalue)))
      .execute()
      .map(rows ->
      {
        if (rows.size() == 0) return null;

        Row row = rows.iterator().next();

        var obj = new JsonObject();

        for (int i = 0; i < row.size(); i++)
        {
          obj.put(row.getColumnName(i), row.getValue(i));
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
    var setClause = new StringBuilder();

    var tuple = Tuple.tuple();

    var index = 1;

    for (Map.Entry<String, Object> entry : payload.getMap().entrySet())
    {
      Object value = entry.getValue();

      setClause.append(entry.getKey()).append(" = $").append(index++);

      tuple.addValue(value instanceof Map ? new JsonObject((Map<String, Object>) value).encode() : value);

      if (index <= payload.size())
      {
        setClause.append(", ");
      }
    }

    return pool.preparedQuery(String.format("UPDATE %s SET %s WHERE %s", tableName, setClause, String.format("%s = '%s'",fieldName, fieldvalue)))
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

    var tableId = switch (tableName)
    {
      case CREDENTIAL_TABLE -> CREDENTIAL_ID_KEY;
      case DISCOVERY_TABLE -> DISCOVERY_ID_KEY;
      case PROVISIONED_OBJECTS_TABLE -> OBJECT_ID_KEY;
      default -> ID_KEY;
    };

    return pool.preparedQuery("DELETE FROM " + tableName + " WHERE " + tableId + " = $1")
      .execute(Tuple.of(Integer.parseInt(idValue)))
      .map(rows -> rows.rowCount() > 0);
  }

  // Genralized find by ID Using join
  public static Future<JsonObject> getByFieldWithJoinTable(String tableName1, String tableName2, String joiningOnField, String fieldName, String fieldValue)
  {
    return pool.preparedQuery("SELECT t1.*, t2.* " + "FROM " + tableName1 + " t1 " + "JOIN " + tableName2 + " t2 " + "ON t1." + joiningOnField + " = t2." + joiningOnField + " " + "WHERE t1." + fieldName + " = $1")
      .execute(Tuple.of(fieldValue))
      .map(rows ->
      {
        if (rows.size() == 0) return null;

        var row = rows.iterator().next();

        JsonObject obj = new JsonObject();

        for (int i = 0; i < row.size(); i++)
        {
          obj.put(row.getColumnName(i), row.getValue(i));
        }
        return obj;
      });
  }

  // Genralized find all with join two table
  public static Future<List<JsonObject>> getAllWithJoinTable(String tableName1, String tableName2, String joiningOnField)
  {
    return pool.preparedQuery("SELECT t1.*, t2.* " + "FROM " + tableName1 + " t1 " + "JOIN " + tableName2 + " t2 " + "ON t1." + joiningOnField + " = t2." + joiningOnField)
      .execute()
      .map(rows ->
      {
        List<JsonObject> resultList = new ArrayList<>();

        for (Row row : rows)
        {
          var obj = new JsonObject();

          for (int i = 0; i < row.size(); i++)
          {
            obj.put(row.getColumnName(i), row.getValue(i));
          }
          resultList.add(obj);
        }
        return resultList;
      });
  }

  // Genralized find all by field with join two table
  public static Future<List<JsonObject>> getAllByField(String tableName, String fieldName, Object fieldValue)
  {
    return pool.preparedQuery(String.format("SELECT * FROM %s WHERE %s = $1", tableName, fieldName))
      .execute(Tuple.of(fieldValue))
      .map(rows ->
      {
        List<JsonObject> resultList = new ArrayList<>();

        if (rows.size() == 0) return resultList;

        for (Row row : rows)
        {
          var obj = new JsonObject();

          for (int i = 0; i < row.size(); i++)
          {
            Object val = row.getValue(i);

            if (val instanceof PGobject pgObject && "jsonb".equalsIgnoreCase(pgObject.getType()))
            {
              val = new JsonObject(pgObject.getValue());
            }
            else if (val instanceof OffsetDateTime offsetDateTime)
            {
              val = offsetDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
            obj.put(row.getColumnName(i), val);
          }
          resultList.add(obj);
        }
        return resultList;
      });
  }
}
