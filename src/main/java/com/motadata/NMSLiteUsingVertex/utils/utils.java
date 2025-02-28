package com.motadata.NMSLiteUsingVertex.utils;

import com.motadata.NMSLiteUsingVertex.Main;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class utils {
  public static Future<Boolean> ping(String ip)
  {
      return Main.vertx().executeBlocking(()->
      {
        try {
          String pingCommand = "ping -c 2 " + ip;

          ProcessBuilder processBuilder = new ProcessBuilder(pingCommand.split(" "));
          Process process = processBuilder.start();

          BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
          StringBuilder output = new StringBuilder();

          String line;
          while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
          }

          // Wait for the process to complete and get exit code
          int exitCode = process.waitFor();

          if(exitCode == 0){
            return  true;
          }
          else{
            return  false;
          }
        } catch (Exception e) {
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
          promise.complete(true);
        }
        else
        {
          System.out.println("Failed to connect to ip "+": " + port + "- " +res.cause().getMessage());
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
}
