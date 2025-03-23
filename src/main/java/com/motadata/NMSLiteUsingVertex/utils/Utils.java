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
  public static Future<Boolean> checkDeviceAvailability(String ip, String port)
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

  // Start GoPlugin
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
    return QueryHandler.getAllWithJoinTable(PROVISIONED_OBJECTS_TABLE, CREDENTIAL_TABLE, CREDENTIAL_ID_KEY)
      .onSuccess(result ->
      {
        LOGGER.info("Received object  from DB: " + (result != null ? result.size() : 0));

        objectQueue.clear(); // Clear the existing queue before updating
        for (JsonObject obj : result)
        {
          var credentialDataPayload = new JsonObject(obj.getString(CREDENTIAL_DATA_KEY));

          JsonObject filteredObject = new JsonObject().put(IP_KEY, obj.getString(IP_KEY))
            .put(PORT_KEY, PORT_VALUE)
            .put(PASSWORD_KEY, credentialDataPayload.getString(PASSWORD_KEY))
            .put(USERNAME_KEY, credentialDataPayload.getString(USERNAME_KEY))
            .put(PLUGIN_ENGINE_TYPE_KEY, PLUGIN_ENGINE_LINUX)
            .put(OBJECT_ID_KEY, obj.getInteger(OBJECT_ID_KEY))
            .put(LAST_POLL_TIME_KEY, System.currentTimeMillis())
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

    if (!payload.containsKey(IP_KEY) || !(payload.getValue(IP_KEY) instanceof String) || payload.getString(IP_KEY).trim().isEmpty())
    {
      response.put(IS_VALID_KEY, "false");
      response.put(IP_ERROR, "Invalid or missing 'IP' address");
    }
    else if (!isValidIPSAddress(payload.getString(IP_KEY)))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(IP_ERROR, "IP address format is invalid");
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

    if (!(payload.containsKey(CREDENTIAL_NAME_KEY) && payload.getValue(CREDENTIAL_NAME_KEY) instanceof String && !payload.getString(CREDENTIAL_NAME_KEY).trim().isEmpty()))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(CREDENTIAL_NAME_ERROR, "Invalid or missing 'CredentialName'");
    }

    if (!(payload.containsKey(SYSTEM_TYPE_KEY) && payload.getValue(SYSTEM_TYPE_KEY) instanceof String && !payload.getString(SYSTEM_TYPE_KEY).trim().isEmpty()))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(SYSTEM_TYPE_ERROR, "Invalid or missing 'SystemType'");
    }

    if(payload.containsKey(CREDENTIAL_DATA_KEY))
    {
        var credentialDataPayload = payload.getJsonObject(CREDENTIAL_DATA_KEY);

        if (!(credentialDataPayload.containsKey(USERNAME_KEY) && credentialDataPayload.getValue(USERNAME_KEY) instanceof String && !credentialDataPayload.getString(USERNAME_KEY).trim().isEmpty()))
        {
          response.put(IS_VALID_KEY, "false");
          response.put(USERNAME_ERROR, "Invalid or missing 'CredentialUsername'");
        }

        if (!(credentialDataPayload.containsKey(PASSWORD_KEY) && credentialDataPayload.getValue(PASSWORD_KEY) instanceof String && !credentialDataPayload.getString(PASSWORD_KEY).trim().isEmpty()))
        {
          response.put(IS_VALID_KEY, "false");
          response.put(PASSWORD_ERROR, "Invalid or missing 'CredentialPassword'");
        }
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

    if (!payload.containsKey(IP_KEY) || !(payload.getValue(IP_KEY) instanceof String))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(IP_ERROR, "Invalid or missing 'ip'");
    }
    else if (!isValidIPSAddress(payload.getString(IP_KEY)))
    {
      response.put(IS_VALID_KEY, "false");
      response.put(IP_ERROR, "IP address format is invalid");
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
  private static boolean isValidIPSAddress(String ip)
  {
    var ipRegex = "^((25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)$";

    return Pattern.compile(ipRegex).matcher(ip).matches();
  }

  // format invalid response and return response
  public static String formatInvalidResponse(Map<String, String> response)
  {
    return response.entrySet().stream()
      .filter(entry -> !entry.getKey().equals(IS_VALID_KEY))
      .map(entry -> entry.getKey() + ": " + entry.getValue())
      .collect(Collectors.joining(", "));
  }

  public static JsonObject replaceUnderscoreWithDot(JsonObject input)
  {
    JsonObject output = new JsonObject();
    for (Map.Entry<String, Object> entry : input)
    {
      String newKey = entry.getKey().replace("_", ".");
      output.put(newKey, entry.getValue());
    }
    return output;
  }


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
