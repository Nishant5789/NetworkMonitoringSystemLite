package com.motadata.NMSLiteUsingVertex.utils;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
//  private static final Logger LOGGER = AppLogger.getLogger();
  private static final Logger LOGGER =  Logger.getLogger(Utils.class.getName());

  private static final List<JsonObject> pollingDataCache = new ArrayList<>();

  private static final Queue<JsonObject> objectQueue = new LinkedList<>();

  // create send responseObject
  public static JsonObject createResponse(String status, String statusMsg)
  {
    return new JsonObject()
      .put("status", status)
      .put("statusMsg", statusMsg);
  }

  // check ping is successful or not
  public static Future<Boolean> ping(String ip)
  {
    return Main.vertx().executeBlocking(() ->
    {
      try
      {
        var command = "ping -c 3 " + ip + " | awk '/packets transmitted/ {if ($6 == \"0%\") print \"true\"; else print \"false\"}'";

        ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);

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

        return output != null && output.trim().equals("true");
      }
      catch (IOException | InterruptedException e)
      {
        LOGGER.severe("Exception occurred during ping execution: " + e.getMessage());
        return false;
      }
    });
  }

  // check port is reachable
  public static Future<Boolean> checkPort(String ip, String port)
  {
    Promise<Boolean> promise = Promise.promise();
    try
    {
      Main.vertx().createNetClient().connect(Integer.parseInt(port), ip, res ->
      {
        if (res.succeeded())
        {
          LOGGER.info("Successful TCP connection for IP: " + ip + " Port: " + port);
          promise.complete(true);
        }
        else
        {
          LOGGER.severe("Failed TCP connection for IP: " + ip + " Port: " + port + " - " + res.cause().getMessage());
          promise.complete(false);
        }
      });
    }
    catch (Exception exception)
    {
      LOGGER.severe("Failed to connect to IP " + ip + " Port: " + port + " - " + exception.getMessage());
      promise.fail(exception);
    }

    return promise.future();
  }

  // check device reachability
  public static Future<Boolean> checkDeviceAvailability(String ip, String port)
  {
    try
    {
      return ping(ip).compose(isPingReachable ->
      {
        if (isPingReachable)
        {
          return checkPort(ip, port);
        }
        else
        {
          return Future.failedFuture("Device is not reachable");
        }
      });
    }
    catch (Exception exception)
    {
      LOGGER.severe("Failed to check device availability: " + exception.getMessage());
      return Future.failedFuture("Failed to check device availability. " + exception.getMessage());
    }
  }

  // add objectwithdata in objectqueue
  public static void addObjectInQueue(JsonObject obj)
  {
    objectQueue.add(obj);
  }

  // return objectQueue
  public static Queue<JsonObject> getObjectQueue()
  {
    return objectQueue;
  }

  // update objectqueue from database
  public static Future<Object> updateObjectQueueFromDatabase()
  {
    return QueryHandler.getAll(PROVISIONED_OBJECTS_TABLE)
      .onSuccess(result ->
      {
        LOGGER.info("Received object  from DB: " + (result != null ? result.size() : 0));

        objectQueue.clear(); // Clear the existing queue before updating
        for (JsonObject obj : result)
        {
          if(obj.getString(PROVISIONING_STATUS_KEY).equals("pending"))
            continue;

          JsonObject filteredObject = new JsonObject()
            .put(IP_KEY, obj.getJsonObject("object_data").getString(IP_KEY))
            .put(PORT_KEY, obj.getJsonObject("object_data").getString(PORT_KEY))
            .put(PASSWORD_KEY, obj.getJsonObject("object_data").getString(PASSWORD_KEY))
            .put(USERNAME_KEY, obj.getJsonObject("object_data").getString(USERNAME_KEY))
            .put(PLUGIN_ENGINE_TYPE_KEY, obj.getJsonObject("object_data").getString(PLUGIN_ENGINE_TYPE_KEY))
            .put(OBJECT_ID_KEY, obj.getInteger(OBJECT_ID_KEY))
            .put(LAST_POLL_TIME_KEY, obj.getLong(LAST_POLL_TIME_KEY))
            .put(PROVISIONING_STATUS_KEY, obj.getString(PROVISIONING_STATUS_KEY))
            .put(POLL_INTERVAL_KEY, obj.getInteger(POLL_INTERVAL_KEY));

          objectQueue.add(filteredObject);
        }
        LOGGER.severe("Object queue updated successfully: " + objectQueue);
      })
      .mapEmpty()
      .onFailure(err ->
      {
        LOGGER.severe("Failed to update object queue: " + err.getMessage());
      });
  }

  // handle update lastpolltime in objectqueue
  public static void updateObjectLastPollTimeInObjectQueue(int objectId, Long lastPollTIME)
  {
    objectQueue.stream()
      .filter(obj -> obj.getInteger(OBJECT_ID_KEY) == objectId)
      .findFirst()
      .ifPresent(obj -> obj.put(LAST_POLL_TIME_KEY, lastPollTIME));
  }

  // get CounterObject from pollingdataCache
  public static JsonArray getPollDataFromCache(String objectId)
  {
    JsonArray resultArray = new JsonArray();

    pollingDataCache.stream()
      .filter(obj -> objectId.equals(obj.getString(OBJECT_ID_KEY)))
      .forEach(resultArray::add);

    return resultArray;
  }

  // add pollResponce in cache
  public static void addPollingResponse(JsonObject pollResponsePayload)
  {
    if (pollResponsePayload == null)
    {
      LOGGER.warning("Attempted to add a null JsonObject to pollingDataCache.");
      return;
    }

    // added polling responce in PollingdataCache
    pollingDataCache.add(pollResponsePayload);

    LOGGER.info("Added polling response to pollingDataCache: " + pollResponsePayload.encode());
  }

  // update pollingDataCache from database
  public static Future<Object> updatePollingDataCacheFromDatabase()
  {
    return QueryHandler.getAll(POLLING_RESULTS_TABLE)
      .map(jsonList ->
      {
        LOGGER.info("Received polling data from DB: " + (jsonList != null ? jsonList.size() : 0));

        if (jsonList != null && !jsonList.isEmpty())
        {
          pollingDataCache.clear();
          pollingDataCache.addAll(jsonList);
          LOGGER.info("Polling data cache updated successfully");
        }
        else
        {
          LOGGER.info("No polling data found in the database.");
        }
        return null; // ✅ Convert to `Void`
      }).
      mapEmpty()
      .onFailure(err ->
      {
        LOGGER.severe("Failed to update polling data cache: " + err.getMessage());
        err.printStackTrace();
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
        response.put(IS_VALID_KEY, "false");
        response.put(ERROR_KEY, "Invalid table name");
    }
    return response;
  }

  // validate discovery payload
  private static Map<String, String> validateDiscoveryPayload(JsonObject payload)
  {
    Map<String, String> response = new HashMap<>();
    response.put(IS_VALID_KEY, "true");

    if (payload == null)
    {
      response.put(IS_VALID_KEY, "false");
      response.put(ERROR_KEY, "Payload is null");
      return response;
    }

    if (!payload.containsKey(IPS_KEY) || !(payload.getValue(IPS_KEY) instanceof JsonArray) || payload.getJsonArray(IPS_KEY).isEmpty())
    {
      response.put(IS_VALID_KEY, "false");
      response.put(IPS_ERROR, "Invalid or missing 'IP' address");
    }
    else if (!isValidIPSAddress(payload.getJsonArray(IPS_KEY)))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(IPS_ERROR, "IP address format is invalid");
    }

    if (!payload.containsKey("port") || !(payload.getValue("port") instanceof Integer))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(PORT_ERROR, "Invalid or missing 'port'");
    }
    else if (payload.getInteger("port") < 0 || payload.getInteger("port") > 65535)
    {
      response.put(IS_VALID_KEY, "false");
      response.put(PORT_ERROR, "Port must be between 0 and 65535");
    }

    if (!payload.containsKey(OBJECT_TYPE_KEY) || !(payload.getValue(OBJECT_TYPE_KEY) instanceof String) || payload.getString(OBJECT_TYPE_KEY).trim().isEmpty())
    {
      response.put(IS_VALID_KEY, "false");
      response.put(TYPE_ERROR, "Invalid or missing 'type'");
    }
    else
    {
      String type = payload.getString(OBJECT_TYPE_KEY).trim().toLowerCase();
      if (!type.equals("linux") && !type.equals("windows") && !type.equals("snmp"))
      {
        response.put(IS_VALID_KEY, "false");
        response.put(TYPE_ERROR, "Type must be one of: 'linux', 'windows', or 'snmp'");
      }
    }

    if (!payload.containsKey(CREDENTIAL_ID_KEY) || !(payload.getValue(CREDENTIAL_ID_KEY) instanceof Integer))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(CREDENTIAL_ID_ERROR, "Invalid or missing 'credential_id'");
    }

    return response;
  }

  // validate credential payload
  private static Map<String, String> validateCredentialPayload(JsonObject payload)
  {
    Map<String, String> response = new HashMap<>();
    response.put(IS_VALID_KEY, "true");

    if (payload == null)
    {
      response.put(IS_VALID_KEY, "false");
      response.put(ERROR_KEY, "Payload is null");
      return response;
    }

    if (!(payload.containsKey(USERNAME_KEY) && payload.getValue(USERNAME_KEY) instanceof String && !payload.getString(USERNAME_KEY).trim().isEmpty()))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(USERNAME_ERROR, "Invalid or missing 'username'");
    }
    if (!(payload.containsKey(PASSWORD_KEY) && payload.getValue(PASSWORD_KEY) instanceof String && !payload.getString(PASSWORD_KEY).trim().isEmpty()))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(PASSWORD_ERROR, "Invalid or missing 'password'");
    }

    return response;
  }

  // validate provision payload
  private static Map<String, String> validateProvisionObjectPayload(JsonObject payload)
  {
    Map<String, String> response = new HashMap<>();
    response.put(IS_VALID_KEY, "true");

    if (payload == null)
    {
      response.put(IS_VALID_KEY, "false");
      response.put(ERROR_KEY, "Payload is null");
      return response;
    }

    if (!payload.containsKey(OBJECT_ID_KEY) || !(payload.getValue(OBJECT_ID_KEY) instanceof Integer))
    {
      response.put(IS_VALID_KEY, "false");
      response.put("objectIdError", "Invalid or missing 'discovery_id'");
    }

    if (!payload.containsKey(POLL_INTERVAL_KEY) || !(payload.getValue(POLL_INTERVAL_KEY) instanceof Integer))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(POLLINTERVAL_ERROR, "Invalid or missing 'pollInterval'");
    }
    else
    {
      try
      {
        int pollInterval = payload.getInteger(POLL_INTERVAL_KEY);
        if (pollInterval <= 0)
        {
          response.put(IS_VALID_KEY, "false");
          response.put(POLLINTERVAL_ERROR, "'pollInterval' must be a positive number");
        }
      }
      catch (NumberFormatException e)
      {
        response.put(IS_VALID_KEY, "false");
        response.put(POLLINTERVAL_ERROR, "'pollInterval' must be a numeric string");
      }
    }
    return response;
  }

  // Validate IP Address (Both IPv4 & IPv6)
  private static boolean isValidIPSAddress(JsonArray ips)
  {
    var ipRegex = "^((25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)$";

    for (int i = 0; i < ips.size(); i++)
    {
      String ip = ips.getString(i);
      if (!Pattern.compile(ipRegex).matcher(ip).matches())
      {
        return false;
      }
    }
    return true;
  }

  // format invalid response and return response
  public static String formatInvalidResponse(Map<String, String> response)
  {
    return response.entrySet().stream()
      .filter(entry -> !entry.getKey().equals(IS_VALID_KEY))
      .map(entry -> entry.getKey() + ": " + entry.getValue())
      .collect(Collectors.joining(", "));
  }
}
