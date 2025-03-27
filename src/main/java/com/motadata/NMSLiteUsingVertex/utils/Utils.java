package com.motadata.NMSLiteUsingVertex.utils;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.services.Credential;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.impl.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class Utils
{
  private static final Logger LOGGER = AppLogger.getLogger();

  private static final Queue<JsonObject> objectQueue = new LinkedList<>();

  // check ping is successful or not
  public static Future<Boolean> ping(String ip)
  {
    return Main.vertx().executeBlocking(() ->
    {
      try
      {
        var command = "ping -c 3 " + ip + " | awk '/packets transmitted/ {if ($(NF-4)== \"100%\") print \"false\"; else print \"true\"}'";

        var processBuilder = new ProcessBuilder("sh", "-c", command);

        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        var output = reader.readLine();

        var exitCode = process.waitFor();

        if (exitCode != 0)
        {
          LOGGER.severe("Ping command execution failed with exit code: " + exitCode);

          return false;
        }

        return output != null && output.trim().equals(TRUE_VALUE);
      }
      catch (IOException | InterruptedException e)
      {
        LOGGER.severe("Exception occurred during ping execution: " + e.getMessage());

        return false;
      }
    });
  }

  // check port is reachable
  public static Future<Boolean> checkPort(String ip, Integer port)
  {
    Promise<Boolean> promise = Promise.promise();
    try
    {
      Main.vertx().createNetClient().connect(port, ip, res ->
      {
        if (res.succeeded())
        {
          LOGGER.info("Successful TCP connection for IP: " + ip + " Port: " + port);

          promise.complete(true);
        }
        else
        {
          LOGGER.severe("tcp connection is unSuccessful for IP: " + ip + " Port: " + port + " - " + res.cause().getMessage());

          promise.complete(false);
        }
      });
    }
    catch (Exception exception)
    {
      LOGGER.severe("Failed to connection during  tcp connection for IP " + ip + " Port: " + port + " - " + exception.getMessage());

      promise.fail("Failed to connection during tcp connection for IP " + ip + "& Port: " + port);
    }

    return promise.future();
  }

  // check device reachability
  public static Future<Boolean> checkDeviceAvailability(String ip, Integer port)
  {
    try
    {
      return ping(ip).compose(isPingReachable ->
      {
        if (isPingReachable)
        {
          LOGGER.info("Ping command is  successfully executed for ip: " + ip);

          return checkPort(ip, port);
        }
        else
        {
          return Future.failedFuture("ping is unsuccessful for iP: " + ip);
        }
      });
    }
    catch (Exception exception)
    {
      LOGGER.severe("Failed to check device availability: " + exception.getMessage());

      return Future.failedFuture("Failed to check device availability");
    }
  }

  // Start GoPlugin using ProcessBuilder
  public static Future<String> startGOPlugin()
  {
    Promise<String> promise = Promise.promise();

    Main.vertx().executeBlocking(nestedPromise ->
    {
      try
      {
        ProcessBuilder builder = new ProcessBuilder("bash", "-c", "cd /home/nishant/codeworkspace/GoLandWorkSpace/PluginEngine && /usr/local/go/bin/go run main.go");

        builder.redirectErrorStream(true);

        Process process = builder.start();

        new Thread(() ->
        {
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
          {
            String line;

            while ((line = reader.readLine()) != null)
            {
              if (line.contains("Starting ZMQ Server..."))
              {
                nestedPromise.complete("ZMQ Server Started Successfully");

                LOGGER.info("go plugin is started successsfully.....");
              }
            }
          }
          catch (IOException e)
          {
            nestedPromise.fail("Error reading Go plugin output: " + e.getMessage());
          }
        }).start();
      }
    catch (IOException e)
    {
      nestedPromise.fail("Failed to start GO Plugin: " + e.getMessage());
    }
  }, res ->
    {
      if (res.succeeded())
      {
        promise.complete(res.result().toString());
      }
      else
      {
        promise.fail(res.cause());
      }
    });
    return promise.future();
  }

  // add objectWithData in objectQueue
  public static void addObjectInQueue(JsonObject obj)
  {
    objectQueue.add(obj);
  }

  // return objectQueue
  public static Queue<JsonObject> getObjectQueue()
  {
    return objectQueue;
  }

  // remove Object from ObjectQueue
  public static void removeObjectFromQueue(int objectId)
  {
    objectQueue.removeIf(obj -> obj.getInteger(OBJECT_ID_KEY) == objectId);
  }

  // update ObjectQueue from database
  public static Future<Object> updateObjectQueueFromDatabase()
  {
    return QueryHandler.getAllWithJoinTable(PROVISIONED_OBJECTS_TABLE, CREDENTIAL_TABLE, CREDENTIAL_ID_KEY)
      .onSuccess(objectResult ->
      {
        LOGGER.info("Received object  from DB: " + (objectResult != null ? objectResult.size() : 0));

        objectQueue.clear(); // Clear the existing queue before updating

        for (JsonObject objectData : objectResult)
        {
          objectQueue.add(addToQueue(objectData));
        }

        LOGGER.severe("Object queue updated successfully: " + objectQueue);
      })
      .mapEmpty()
      .onFailure(err ->
      {
        LOGGER.severe("Failed to update object queue: " + err.getMessage());
      });
  }

  // handle update lastPollTime in objectQueue
  public static void updateObjectLastPollTimeInObjectQueue(int objectId, Long lastPollTIME)
  {
    objectQueue.stream().filter(obj -> obj.getInteger(OBJECT_ID_KEY) == objectId).findFirst().ifPresent(obj -> obj.put(LAST_POLL_TIME_KEY, lastPollTIME));
  }

  // check whether object is down based on threshold value
  public static boolean checkFailureThresholdExceeded(int objectId)
  {
    return objectQueue.stream().filter(obj -> obj.getInteger(OBJECT_ID_KEY) == objectId).anyMatch(obj -> obj.getInteger(FAILURE_COUNT_KEY) >= THRESHOLD_FAILURE_VALUE);
  }

  // check Status is up or not?
  public static boolean isObjectStatusDown(int objectId)
  {
    return objectQueue.stream().filter(obj -> obj.getInteger(OBJECT_ID_KEY) == objectId).map(obj -> obj.getString(OBJECT_AVAILABILITY_KEY)).anyMatch(status -> OBJECT_AVAILABILITY_DOWN.equalsIgnoreCase(status));
  }

  // check Status is down or not?
  public static boolean isObjectStatusUP(int objectId)
  {
    return objectQueue.stream().filter(obj -> obj.getInteger(OBJECT_ID_KEY) == objectId).map(obj -> obj.getString(OBJECT_AVAILABILITY_KEY)).anyMatch(status -> OBJECT_AVAILABILITY_UP.equalsIgnoreCase(status));
  }

  // update failure count based on objectId
  public static void incrementFailureCount(int objectId)
  {
    objectQueue.stream().filter(obj -> obj.getInteger(OBJECT_ID_KEY) == objectId).findFirst().ifPresent(obj -> {obj.put(FAILURE_COUNT_KEY, obj.getInteger(FAILURE_COUNT_KEY) + 1);});
  }

  // reset failureThreeSold value zero
  public static void resetFailureCount(int objectId)
  {
    objectQueue.stream().filter(obj -> obj.getInteger(OBJECT_ID_KEY) == objectId).findFirst().ifPresent(obj -> obj.put(FAILURE_COUNT_KEY, 0));
  }

  // update status in objectQueue & database
  public static void updateStatusInObjectQueueAndDatabase(int objectId, String status)
  {
    // Update status in objectQueue
    objectQueue.stream().filter(obj -> obj.getInteger(OBJECT_ID_KEY) == objectId).findFirst().ifPresent(obj -> obj.put(OBJECT_AVAILABILITY_KEY, status));

    JsonObject updateObjectPayload = new JsonObject().put(OBJECT_AVAILABILITY_KEY, status);

    // Update status in database
    QueryHandler.updateByField(PROVISIONED_OBJECTS_TABLE, updateObjectPayload, OBJECT_ID_KEY, objectId)
            .onSuccess(updated ->
            {
              LOGGER.info("objectID: " + objectId + " status updated to " + status + " at timestamp: " + System.currentTimeMillis());
            })
            .onFailure(err ->
            {
              LOGGER.severe("Failed to update object status to " + status + " in database for objectID: " + objectId);
            });
  }

  // validate payload
  public static Map<String, String> isValidPayload(String tableName, JsonObject payload)
  {
    Map<String, String> response;

    switch (tableName.toLowerCase())
    {
      case CREDENTIAL_TABLE:
        response = validateCredentialPayload(payload);
        break;
      case DISCOVERY_TABLE:
        response = validateDiscoveryPayload(payload);
        break;
      case PROVISIONED_OBJECTS_TABLE:
        response = validateProvisionObjectPayload(payload);
        break;

      default:
        response = new HashMap<>();
        response.put(IS_VALID_KEY, FALSE_VALUE);
        response.put(ERROR_KEY, "Invalid table name");
    }
    return response;
  }

  // validate discovery payload
  private static Map<String, String> validateDiscoveryPayload(JsonObject payload)
  {
    Map<String, String> response = new HashMap<>();

    response.put(IS_VALID_KEY, TRUE_VALUE); // Default to valid

    // Check if payload is null
    if (payload == null)
    {
      return invalidate(response, "Payload is null");
    }

    // Validate IP address
    if (!isValidString(payload, IP_KEY))
    {
      response.put(IS_VALID_KEY, FALSE_VALUE);
      response.put(IP_ERROR, "Invalid or missing 'IP' address");
    }
    else if (!isValidIPAddress(payload.getString(IP_KEY)))
    {
      response.put(IS_VALID_KEY, FALSE_VALUE);
      response.put(IP_ERROR, "IP address format is invalid");
    }

    // Validate port
    if (!isValidInteger(payload, PORT_KEY))
    {
      response.put(IS_VALID_KEY, FALSE_VALUE);
      response.put(PORT_ERROR, "Invalid or missing 'port'");
    }
    else
    {
      int port = payload.getInteger(PORT_KEY);

      if (port < 0 || port > 65535)
      {
        response.put(IS_VALID_KEY, FALSE_VALUE);
        response.put(PORT_ERROR, "Port must be between 0 and 65535");
      }
    }

    // Validate credential_id
    if (!isValidInteger(payload, CREDENTIAL_ID_KEY))
    {
      response.put(IS_VALID_KEY, FALSE_VALUE);
      response.put(CREDENTIAL_ID_ERROR, "Invalid or missing 'credential_id'");
    }

    return response;
  }

  // validate credential payload
  private static Map<String, String> validateCredentialPayload(JsonObject payload)
  {
    Map<String, String> response = new HashMap<>();
    response.put(IS_VALID_KEY, TRUE_VALUE); // Use original constant

    if (payload == null)
    {
      return invalidate(response, "Payload is null");
    }

    if (!isValidString(payload, CREDENTIAL_NAME_KEY))
    {
      response.put(CREDENTIAL_NAME_ERROR, "Invalid or missing 'CredentialName'");
      response.put(IS_VALID_KEY, FALSE_VALUE);
    }

    if (!isValidString(payload, SYSTEM_TYPE_KEY))
    {
      response.put(SYSTEM_TYPE_ERROR, "Invalid or missing 'SystemType'");
      response.put(IS_VALID_KEY, FALSE_VALUE);
    }

    if (!payload.containsKey(CREDENTIAL_DATA_KEY) || !(payload.getValue(CREDENTIAL_DATA_KEY) instanceof JsonObject))
    {
      return invalidate(response, "Invalid or missing 'credential_data'");
    }

    var credentialData = payload.getJsonObject(CREDENTIAL_DATA_KEY);

    if (!isValidString(credentialData, USERNAME_KEY))
    {
      response.put(USERNAME_ERROR, "Invalid or missing 'CredentialUsername'");
      response.put(IS_VALID_KEY, FALSE_VALUE);
    }

    if (!isValidString(credentialData, PASSWORD_KEY))
    {
      response.put(PASSWORD_ERROR, "Invalid or missing 'CredentialPassword'");
      response.put(IS_VALID_KEY, FALSE_VALUE);
    }

    return response;
  }

  // validate provision payload
  private static Map<String, String> validateProvisionObjectPayload(JsonObject payload)
  {
    Map<String, String> response = new HashMap<>();
    response.put(IS_VALID_KEY, TRUE_VALUE); // Default to valid

    // Check if payload is null
    if (payload == null) 
    {
      return invalidate(response, "Payload is null");
    }

    // Validate IP address
    if (!isValidString(payload, IP_KEY)) 
    {
      response.put(IS_VALID_KEY, FALSE_VALUE);
      response.put(IP_ERROR, "Invalid or missing 'ip'");
    }
    else if (!isValidIPAddress(payload.getString(IP_KEY))) 
    {
      response.put(IS_VALID_KEY, FALSE_VALUE);
      response.put(IP_ERROR, "IP address format is invalid");
    }

    // Validate pollInterval
    if (!isValidInteger(payload, POLL_INTERVAL_KEY)) 
    {
      response.put(IS_VALID_KEY, FALSE_VALUE);
      response.put(POLLINTERVAL_ERROR, "Invalid or missing 'pollInterval'");
    } 
    else 
    {
      if (payload.getInteger(POLL_INTERVAL_KEY) <= 0)
      {
        response.put(IS_VALID_KEY, FALSE_VALUE);
        response.put(POLLINTERVAL_ERROR, "'pollInterval' must be a positive number");
      }
    }

    return response;
  }
  
  // Validate IP Address (Both IPv4 & IPv6)
  public static boolean isValidIPAddress(String ip)
  {
    return Pattern.compile("^((25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)$").matcher(ip).matches();
  }

  // Helper method to check if a string field is valid
  private static boolean isValidString(JsonObject json, String key) 
  {
    return json.containsKey(key) && json.getValue(key) instanceof String && !json.getString(key).trim().isEmpty();
  }

  // Helper method to check if an integer field is valid
  private static boolean isValidInteger(JsonObject json, String key)
  {
    return json.containsKey(key) && json.getValue(key) instanceof Integer;
  }

  // Helper method to invalidate response with an error message
  private static Map<String, String> invalidate(Map<String, String> response, String errorMessage) 
  {
    response.put(IS_VALID_KEY, FALSE_VALUE);
    response.put(ERROR_KEY, errorMessage);
    return response;
  }

  // format invalid response and return response
  public static String formatInvalidResponse(Map<String, String> response)
  {
    return response.entrySet().stream().filter(entry -> !entry.getKey().equals(IS_VALID_KEY)).map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining(", "));
  }

  // create send responseObject
  public static JsonObject createResponse(String status, String statusMsg)
  {
    return new JsonObject().put(STATUS_KEY, status).put(STATUS_MSG_KEY, statusMsg);
  }

  // create object to add in ObjectQueue
  private static JsonObject addToQueue(JsonObject obj)
  {
    JsonObject credentialDataPayload = new JsonObject(obj.getString(CREDENTIAL_DATA_KEY));

    return new JsonObject().put(IP_KEY, obj.getString(IP_KEY)).put(PORT_KEY, PORT_VALUE).put(PASSWORD_KEY, credentialDataPayload.getString(PASSWORD_KEY)).put(USERNAME_KEY, credentialDataPayload.getString(USERNAME_KEY)).put(PLUGIN_ENGINE_TYPE_KEY, PLUGIN_ENGINE_LINUX).put(OBJECT_ID_KEY, obj.getInteger(OBJECT_ID_KEY)).put(LAST_POLL_TIME_KEY, System.currentTimeMillis()).put(POLL_INTERVAL_KEY, obj.getInteger(POLL_INTERVAL_KEY)).put(FAILURE_COUNT_KEY, DEAFAULT_FAILURE_VALUE).put(OBJECT_AVAILABILITY_KEY, obj.getString(OBJECT_AVAILABILITY_KEY));
  }

  // replace underscore to dot in counters keys
  public static JsonObject replaceUnderscoreWithDot(JsonObject input)
  {
    var output = new JsonObject();
    for (Map.Entry<String, Object> entry : input)
    {
      output.put(entry.getKey().replace("_", "."), entry.getValue());
    }
    return output;
  }

  // get the tableId from tableName
  public static String getIdColumnByTable(String tableName)
  {
    return switch (tableName)
    {
      case CREDENTIAL_TABLE -> CREDENTIAL_ID_KEY;
      case DISCOVERY_TABLE -> DISCOVERY_ID_KEY;
      case PROVISIONED_OBJECTS_TABLE -> OBJECT_ID_KEY;
      default -> ID_KEY; // fallback generic ID column
    };
  }
}
