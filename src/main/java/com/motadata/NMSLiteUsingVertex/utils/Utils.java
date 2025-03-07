package com.motadata.NMSLiteUsingVertex.utils;

import com.motadata.NMSLiteUsingVertex.Main;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.CREDENTIAL_TABLE;
import static com.motadata.NMSLiteUsingVertex.utils.Constants.MONITOR_DEVICE_TABLE;

public class Utils {

  private static final Logger log = LoggerFactory.getLogger(Utils.class);

  public static JsonObject createResponse(String status, String statusMsg) {
    return new JsonObject()
      .put("status", status)
      .put("statusMsg", statusMsg);
  }

  public static Future<Boolean> ping(String ip)
  {
    return Main.vertx().executeBlocking(() ->
    {
      try
      {
        String command = "ping -c 3 " + ip + " | awk '/packets transmitted/ {if ($6 == \"0%\") print \"true\"; else print \"false\"}'";

        ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", command);

        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        
        String output = reader.readLine();

        int exitCode = process.waitFor();
        if (exitCode != 0)
        {
          log.error("Ping command execution failed with exit code: {}", exitCode);
          return false;
        }

        return output != null && output.trim().equals("true");
      }
      catch (IOException | InterruptedException e)
      {
        log.error("Exception occurred during ping execution: {}", e.getMessage(), e);
        return false;
      }
    });
  }

  public static Future<Boolean> checkPort(String ip, String port) {
    Promise<Boolean> promise = Promise.promise();
    try {
      Main.vertx().createNetClient().connect(Integer.parseInt(port), ip, res -> {
        if (res.succeeded()) {
          log.info("Successful TCP connection for IP: {} Port: {}", ip, port);
          promise.complete(true);
        } else {
          log.error("Failed TCP connection for IP: {} Port: {} - {}", ip, port, res.cause().getMessage());
          promise.complete(false);
        }
      });
    } catch (Exception exception) {
      log.error("Failed to connect to IP {} Port: {} - {}", ip, port, exception.getMessage(), exception);
      promise.fail(exception);
    }

    return promise.future();
  }

  public static Future<Boolean> checkDeviceAvailability(String ip, String port) {
    try {
      return ping(ip).compose(isPingReachable -> {
        if (isPingReachable) {
          return checkPort(ip, port);
        } else {
          return Future.failedFuture("Device is not reachable");
        }
      });
    } catch (Exception exception) {
      log.error("Failed to check device availability: {}", exception.getMessage(), exception);
      return Future.failedFuture("Failed to check device availability. " + exception.getMessage());
    }
  }

  public static boolean isValidPayload(String tableName, JsonObject payload) {
    if (payload == null) return false;

    switch (tableName.toLowerCase()) {
      case CREDENTIAL_TABLE:
        return payload.containsKey("name") && !payload.getString("name", "").trim().isEmpty() &&
          payload.containsKey("username") && !payload.getString("username", "").trim().isEmpty() &&
          payload.containsKey("password") && !payload.getString("password", "").trim().isEmpty();

      case MONITOR_DEVICE_TABLE:
        return payload.containsKey("ip") && !payload.getString("ip", "").trim().isEmpty() &&
          payload.containsKey("credential_id") && !payload.getString("credential_id", "").trim().isEmpty() &&
          payload.containsKey("port") && !payload.getString("port", "").trim().isEmpty() &&
          payload.containsKey("type") && !payload.getString("type", "").trim().isEmpty() &&
          payload.containsKey("is_discovered");

      default:
        return false;
    }
  }

  public static String windowsPollDataKeyFormatter(String key) {
    StringBuilder result = new StringBuilder();
    result.append(Character.toLowerCase(key.charAt(0)));

    for (int i = 1; i < key.length(); i++) {
      char ch = key.charAt(i);
      if (Character.isUpperCase(ch)) {
        result.append('_').append(Character.toLowerCase(ch));
      } else {
        result.append(ch);
      }
    }
    return result.toString();
  }

  public static JsonObject formatWindowsPlugineEnginePayload(String ip, String username, String password) {
    return new JsonObject()
      .put("ip", ip)
      .put("username", username)
      .put("password", password)
      .put("requestType", "provisioning")
      .put("systemType", "windows");
  }
}
