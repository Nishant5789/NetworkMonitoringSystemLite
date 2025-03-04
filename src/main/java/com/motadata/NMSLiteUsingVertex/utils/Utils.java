package com.motadata.NMSLiteUsingVertex.utils;

import com.motadata.NMSLiteUsingVertex.Main;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Utils {

  public static Future<Boolean> ping(String ip)
  {
      return Main.vertx().executeBlocking(()->
      {
        try
        {
          String pingCommand = "ping -c 2 " + ip;

          System.out.println("execute command: " + pingCommand);

          ProcessBuilder processBuilder = new ProcessBuilder(pingCommand.split(" "));

          Process process = processBuilder.start();

          BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

          StringBuilder output = new StringBuilder();

          String line;

          while ((line = reader.readLine()) != null)
          {
            output.append(line).append("\n");
          }

          // Wait for the process to complete and get exit code
          int exitCode = process.waitFor();

          if(exitCode == 0)
          {
            System.out.println("ping command is sucessful for ip: " + ip);
            return  true;
          }
          else
          {
            System.out.println("ping command is unsucessful for ip: " + ip);
            return  false;
          }
        }
        catch (Exception e)
        {
          System.err.println("Ping failed for " + ip + ": " + e.getMessage());
          return false;
        }
      });
  }

  public static Future<Boolean> checkPort( String ip, String port)
  {
    Promise<Boolean> promise = Promise.promise();
   try {
      Main.vertx().createNetClient().connect(Integer.parseInt(port), ip, res ->
      {
        if (res.succeeded())
        {
          System.out.println("sucessful tcp connection for ip: "+ ip +"port: " + port);
          promise.complete(true);
        }
        else
        {
          System.out.println("failed tcp connection for ip: "+ ip +"port: " + port +" "+res.cause().getMessage());
          promise.complete(false);
        }
      });
    }
    catch (Exception exception)
    {
      System.out.println("Failed to connect to ip "+": " + port + "- " +exception.getMessage());
      promise.fail(exception);
    }

    return promise.future();
  }

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
      return Future.failedFuture("Failed to check device availability. " + exception.getMessage());
    }
  }
}
