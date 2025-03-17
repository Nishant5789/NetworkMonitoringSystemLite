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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class Utils
{
  private static final Logger LOGGER = AppLogger.getLogger();
//  private static final Logger LOGGER =  Logger.getLogger(Utils.class.getName());

  private static final List<JsonObject> pollingDataCache = new CopyOnWriteArrayList<>();

  // create send responceObject
  public static JsonObject createResponse(String status, String statusMsg)
  {
    return new JsonObject()
      .put("status", status)
      .put("statusMsg", statusMsg);
  }

  // check ping command is successful or not
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

  // update pollingDataCache
  public static Future<Object> updatePollingDataCache()
  {
    LOGGER.info("updatePollingDataCache() called");

    return QueryHandler.getAll(POLLING_RESULTS_TABLE)
      .map(jsonList ->
      {
        LOGGER.info("Received data from DB: " + (jsonList != null ? jsonList.size() : 0));

        if (jsonList != null && !jsonList.isEmpty())
        {
          pollingDataCache.clear();
          pollingDataCache.addAll(jsonList);
          LOGGER.info("Polling data cache updated successfully with " + jsonList.size() + " new records.");
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

  // get CounterObject from cache
  public static JsonArray getPollDataFromCache(String objectId)
  {
    JsonArray resultArray = new JsonArray();

    pollingDataCache.stream()
      .filter(obj -> objectId.equals(obj.getString(MONITOR_ID_KEY)))
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

    pollingDataCache.add(pollResponsePayload);

    LOGGER.info("Added polling response to pollingDataCache: " + pollResponsePayload.encode());
  }

  // valiaat payload
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
        response = validateProvisionPayload(payload);
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

    if (!payload.containsKey("ips") || !(payload.getValue("ips") instanceof JsonArray) || payload.getJsonArray("ips").isEmpty())
    {
      response.put(IS_VALID_KEY, "false");
      response.put("ipsError", "Invalid or missing 'IP' address");
    }
    else if (!isValidIPSAddress(payload.getJsonArray("ips")))
    {
      response.put(IS_VALID_KEY, "false");
      response.put("ipsError", "IP address format is invalid");
    }

    if (!payload.containsKey("port") || !(payload.getValue("port") instanceof Integer))
    {
      response.put(IS_VALID_KEY, "false");
      response.put("portError", "Invalid or missing 'port'");
    }
    else if (payload.getInteger("port") < 0 || payload.getInteger("port") > 65535)
    {
      response.put(IS_VALID_KEY, "false");
      response.put("portError", "Port must be between 0 and 65535");
    }

    if (!payload.containsKey("type") || !(payload.getValue("type") instanceof String) || payload.getString("type").trim().isEmpty())
    {
      response.put(IS_VALID_KEY, "false");
      response.put("typeError", "Invalid or missing 'type'");
    }
    else
    {
      String type = payload.getString("type").trim().toLowerCase();
      if (!type.equals("linux") && !type.equals("windows") && !type.equals("snmp"))
      {
        response.put(IS_VALID_KEY, "false");
        response.put("typeError", "Type must be one of: 'linux', 'windows', or 'snmp'");
      }
    }

    if (!payload.containsKey("credential_id") || !(payload.getValue("credential_id") instanceof Integer))
    {
      response.put(IS_VALID_KEY, "false");
      response.put("credentialIdError", "Invalid or missing 'credential_id'");
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

    if (!(payload.containsKey("username") && payload.getValue("username") instanceof String && !payload.getString("username").trim().isEmpty()))
    {
      response.put(IS_VALID_KEY, "false");
      response.put("usernameError", "Invalid or missing 'username'");
    }
    if (!(payload.containsKey("password") && payload.getValue("password") instanceof String && !payload.getString("password").trim().isEmpty()))
    {
      response.put(IS_VALID_KEY, "false");
      response.put("passwordError", "Invalid or missing 'password'");
    }

    return response;
  }

  // validate provision payload
  private static Map<String, String> validateProvisionPayload(JsonObject payload)
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

    if (!payload.containsKey("pollInterval") || !(payload.getValue("pollInterval") instanceof Integer))
    {
      response.put(IS_VALID_KEY, "false");
      response.put("pollIntervalError", "Invalid or missing 'pollInterval'");
    }
    else
    {
      try
      {
        int pollInterval = payload.getInteger("pollInterval");
        if (pollInterval <= 0)
        {
          response.put(IS_VALID_KEY, "false");
          response.put("pollIntervalError", "'pollInterval' must be a positive number");
        }
      }
      catch (NumberFormatException e)
      {
        response.put(IS_VALID_KEY, "false");
        response.put("pollIntervalError", "'pollInterval' must be a numeric string");
      }
    }

    return response;
  }

  // Validate IP Address (Both IPv4 & IPv6)
  private static boolean isValidIPSAddress(JsonArray ips)
  {
    var ipRegex = "^((25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)$";

    for (int i = 0; i < ips.size(); i++) {
      String ip = ips.getString(i);
      if (!Pattern.compile(ipRegex).matcher(ip).matches()) {
        return false;
      }
    }
    return true;
  }

  public static String formatInvalidResponse(Map<String, String> response)
  {
    return response.entrySet().stream()
      .filter(entry -> !entry.getKey().equals(IS_VALID_KEY))
      .map(entry -> entry.getKey() + ": " + entry.getValue())
      .collect(Collectors.joining(", "));
  }

  public static String windowsPollDataKeyFormatter(String key)
  {
    StringBuilder result = new StringBuilder();
    result.append(Character.toLowerCase(key.charAt(0)));

    for (int i = 1; i < key.length(); i++)
    {
      char ch = key.charAt(i);
      if (Character.isUpperCase(ch))
      {
        result.append('_').append(Character.toLowerCase(ch));
      }
      else
      {
        result.append(ch);
      }
    }
    return result.toString();
  }

  public static JsonObject formatWindowsPlugineEnginePayload(String ip, String username, String password)
  {
    return new JsonObject()
      .put("ip", ip)
      .put("username", username)
      .put("password", password)
      .put("requestType", "provisioning")
      .put("systemType", "windows");
  }
}
