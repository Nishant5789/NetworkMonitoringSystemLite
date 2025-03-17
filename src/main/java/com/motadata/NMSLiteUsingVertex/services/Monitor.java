package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class Monitor
{
    private static final Logger LOGGER = AppLogger.getLogger();
//  private static final Logger LOGGER =  Logger.getLogger(Monitor.class.getName());

  public static void getAllMonitors(RoutingContext ctx)
  {
    LOGGER.info("Fetching all monitors with details...");

    QueryHandler.getAll(PROVISIONED_OBJECTS_TABLE)
      .onSuccess(monitors ->
      {
        LOGGER.info("Fetched monitors successfully");

        var response = new JsonArray(monitors);

        ctx.response().end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to fetch monitors: " + err.getMessage());

        var response = Utils.createResponse("error", "Failed to fetch monitors: " + err.getMessage());

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  public static void getMonitorById(RoutingContext ctx)
  {
    var monitorId = ctx.pathParam(MONITOR_ID_HEADER_PATH);

    if (monitorId == null || monitorId.trim().isEmpty())
    {
      LOGGER.warning("Invalid monitor id received: " + monitorId);

      var response = Utils.createResponse("failed", "Invalid monitor id: Id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());

      return;
    }

    LOGGER.info("Finding monitor by id: " + monitorId);

    QueryHandler.getByfield(PROVISIONED_OBJECTS_TABLE, MONITOR_ID_KEY, monitorId)
      .onSuccess(monitor ->
      {
        if (monitor == null)
        {
          LOGGER.warning("Monitor not found: " + monitorId);

          var response = Utils.createResponse("failed", "monitor not found");

          ctx.response().setStatusCode(404).end(response.encodePrettily());
        }
        else
        {
          LOGGER.info("Found monitor: " + monitor);

          ctx.response().end(monitor.encodePrettily());
        }
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to find monitor: " + err.getMessage());

        var response = Utils.createResponse("failed", "monitor not found");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  public static void getMonitorStatus(RoutingContext ctx)
  {
    var monitorId = ctx.pathParam(MONITOR_ID_HEADER_PATH);

    if (monitorId == null || monitorId.trim().isEmpty())
    {
      LOGGER.warning("Invalid monitor id received: " + monitorId);

      var response = Utils.createResponse("failed", "Invalid monitor id: Id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
      return;
    }

    LOGGER.info("Finding monitor status by id: " + monitorId);

    QueryHandler.getByfield(PROVISIONED_OBJECTS_TABLE, MONITOR_ID_KEY, monitorId)
      .onSuccess(monitor ->
      {
        if (monitor == null)
        {
          LOGGER.warning("Monitor not found: " + monitorId);

          var response = Utils.createResponse("failed", "monitor not found");

          ctx.response().setStatusCode(404).end(response.encodePrettily());
        }
        else
        {
          LOGGER.info("Found monitor: " + monitor);

          var statusResponce = new JsonObject().put("provisioning_status", monitor.getString("provisioning_status"));

          ctx.response().end(statusResponce.encodePrettily());
        }
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to find monitor: " + err.getMessage());

        var response = Utils.createResponse("failed", "monitor not found");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  public static void deleteMonitor(RoutingContext ctx)
  {
    var monitorId = ctx.pathParam(MONITOR_ID_HEADER_PATH);

    if (monitorId == null || monitorId.trim().isEmpty())
    {
      LOGGER.warning("Invalid monitor id received: " + monitorId);

      var response = Utils.createResponse("failed", "Invalid monitor id: Id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
      return;
    }

    LOGGER.info("Finding monitor status by id: " + monitorId);

    QueryHandler.deleteById(PROVISIONED_OBJECTS_TABLE, monitorId)
      .onSuccess(deleted ->
      {
        if (deleted)
        {
          LOGGER.info("Monitor deleted successfully");

          var response = new JsonObject().put("status", "success").put("statusMsg", "Monitor deleted successfully");

          ctx.response().setStatusCode(200).end(response.encodePrettily());
        }
        else
        {
          LOGGER.info("No matching record found");

          var response = new JsonObject().put("status", "success").put("statusMsg", "No matching record found");

          ctx.response().setStatusCode(200).end(response.encodePrettily());
        }
      })
      .onFailure(err ->
      {
        LOGGER.severe("Database query failed: " + err.getMessage());

        var response = Utils.createResponse("error", "Database query failed");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

}
