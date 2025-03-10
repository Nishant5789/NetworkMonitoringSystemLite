package com.motadata.NMSLiteUsingVertex.utils;

import com.motadata.NMSLiteUsingVertex.Main;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class Utils
{

  private static final Logger log = AppLogger.getLogger();

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
          log.severe("Ping command execution failed with exit code: " + exitCode);
          return false;
        }

        return output != null && output.trim().equals("true");
      }
      catch (IOException | InterruptedException e)
      {
        log.severe("Exception occurred during ping execution: " + e.getMessage());
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
          log.info("Successful TCP connection for IP: " + ip + " Port: " + port);
          promise.complete(true);
        }
        else
        {
          log.severe("Failed TCP connection for IP: " + ip + " Port: " + port + " - " + res.cause().getMessage());
          promise.complete(false);
        }
      });
    }
    catch (Exception exception)
    {
      log.severe("Failed to connect to IP " + ip + " Port: " + port + " - " + exception.getMessage());
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
      log.severe("Failed to check device availability: " + exception.getMessage());
      return Future.failedFuture("Failed to check device availability. " + exception.getMessage());
    }
  }

  // valiaat payload
  public static Map<String, String> isValidPayload(String tableName, JsonObject payload)
  {
    Map<String, String> response;

    switch (tableName.toLowerCase())
    {
      case "credential":
        response = validateCredentialPayload(payload);
        break;
      case "discovery":
        response = validateDiscoveryPayload(payload);
        break;
      case "provision":
        response = validateProvisionPayload(payload);
        break;

      default:
        response = new HashMap<>();
        response.put("isValid", "false");
        response.put("error", "Invalid table name");
    }
    return response;
  }

  // validate discovery payload
  private static Map<String, String> validateDiscoveryPayload(JsonObject payload)
  {
    Map<String, String> response = new HashMap<>();
    response.put("isValid", "true");

    if (payload == null)
    {
      response.put("isValid", "false");
      response.put("error", "Payload is null");
      return response;
    }

    if (!payload.containsKey("ip") || !(payload.getValue("ip") instanceof String) || payload.getString("ip").trim().isEmpty())
    {
      response.put("isValid", "false");
      response.put("ipError", "Invalid or missing 'ip' address");
    }
    else if (!isValidIPAddress(payload.getString("ip")))
    {
      response.put("isValid", "false");
      response.put("ipError", "IP address format is invalid");
    }

    if (!payload.containsKey("port") || !(payload.getValue("port") instanceof Integer))
    {
      response.put("isValid", "false");
      response.put("portError", "Invalid or missing 'port'");
    }
    else if (payload.getInteger("port") < 0 || payload.getInteger("port") > 65535)
    {
      response.put("isValid", "false");
      response.put("portError", "Port must be between 0 and 65535");
    }

    if (!payload.containsKey("type") || !(payload.getValue("type") instanceof String) || payload.getString("type").trim().isEmpty())
    {
      response.put("isValid", "false");
      response.put("typeError", "Invalid or missing 'type'");
    }
    else
    {
      String type = payload.getString("type").trim().toLowerCase();
      if (!type.equals("linux") && !type.equals("windows") && !type.equals("snmp"))
      {
        response.put("isValid", "false");
        response.put("typeError", "Type must be one of: 'linux', 'windows', or 'snmp'");
      }
    }

    if (!payload.containsKey("credential_id") || !(payload.getValue("credential_id") instanceof Integer))
    {
      response.put("isValid", "false");
      response.put("credentialIdError", "Invalid or missing 'credential_id'");
    }

    return response;
  }

  // validate credential payload
  private static Map<String, String> validateCredentialPayload(JsonObject payload)
  {
    Map<String, String> response = new HashMap<>();
    response.put("isValid", "true");

    if (payload == null)
    {
      response.put("isValid", "false");
      response.put("error", "Payload is null");
      return response;
    }

    if (!(payload.containsKey("name") && payload.getValue("name") instanceof String && !payload.getString("name").trim().isEmpty()))
    {
      response.put("isValid", "false");
      response.put("nameError", "Invalid or missing 'name'");
    }
    if (!(payload.containsKey("username") && payload.getValue("username") instanceof String && !payload.getString("username").trim().isEmpty()))
    {
      response.put("isValid", "false");
      response.put("usernameError", "Invalid or missing 'username'");
    }
    if (!(payload.containsKey("password") && payload.getValue("password") instanceof String && !payload.getString("password").trim().isEmpty()))
    {
      response.put("isValid", "false");
      response.put("passwordError", "Invalid or missing 'password'");
    }

    return response;
  }

  // validate provision payload
  private static Map<String, String> validateProvisionPayload(JsonObject payload)
  {
    Map<String, String> response = new HashMap<>();
    response.put("isValid", "true");

    if (payload == null)
    {
      response.put("isValid", "false");
      response.put("error", "Payload is null");
      return response;
    }

    if (!payload.containsKey("discovery_id") || !(payload.getValue("discovery_id") instanceof Integer))
    {
      response.put("isValid", "false");
      response.put("discoveryIdError", "Invalid or missing 'discovery_id'");
    }

    if (!payload.containsKey("pollInterval") || !(payload.getValue("pollInterval") instanceof Integer))
    {
      response.put("isValid", "false");
      response.put("pollIntervalError", "Invalid or missing 'pollInterval'");
    }
    else
    {
      try
      {
        int pollInterval = payload.getInteger("pollInterval");
        if (pollInterval <= 0)
        {
          response.put("isValid", "false");
          response.put("pollIntervalError", "'pollInterval' must be a positive number");
        }
      }
      catch (NumberFormatException e)
      {
        response.put("isValid", "false");
        response.put("pollIntervalError", "'pollInterval' must be a numeric string");
      }
    }

    return response;
  }

  // Validate IP Address (Both IPv4 & IPv6)
  private static boolean isValidIPAddress(String ip)
  {
    String ipRegex = "^((25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)$";
    return Pattern.compile(ipRegex).matcher(ip).matches();
  }

  public static String formatInvalidResponse(Map<String, String> response)
  {
    return response.entrySet().stream()
      .filter(entry -> !entry.getKey().equals("isValid"))
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
