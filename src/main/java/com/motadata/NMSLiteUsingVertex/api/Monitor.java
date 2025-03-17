package com.motadata.NMSLiteUsingVertex.api;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class Monitor
{
    private static final Logger LOGGER = AppLogger.getLogger();
//  private static final Logger LOGGER =  Logger.getLogger(Monitor.class.getName());

  private static final Router router = Router.router(Main.vertx());

  // return subroutes for monitor
  public static Router getRouter()
  {
    // GET /api/monitor/ - get all monitors with data
    router.get("/").handler(com.motadata.NMSLiteUsingVertex.services.Monitor::getAllMonitors);

    // GET /api/monitor/ - get monitor with data
    router.get("/:monitor_id").handler(com.motadata.NMSLiteUsingVertex.services.Monitor::getMonitorById);

    // GET /api/monitor/status/:monitor_id - fetch monitor  status by monitorId
    router.get("/status/:monitor_id").handler(com.motadata.NMSLiteUsingVertex.services.Monitor::getMonitorStatus);

    // DELETE /api/monitor/:monitor_id - delete Monitor by Id
    router.delete("/:monitor_id").handler(com.motadata.NMSLiteUsingVertex.services.Monitor::deleteMonitor);

    return router;
  }
}
