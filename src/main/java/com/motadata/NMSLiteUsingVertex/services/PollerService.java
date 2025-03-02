package com.motadata.NMSLiteUsingVertex.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

public class PollerService {
  private final Pool pool;

  public PollerService(Vertx vertx) {
    this.pool = com.motadata.NMSLiteUsingVertex.database.DatabaseClient.getPool(vertx);
  }

  // Save a new counter
  public Future<Void> saveCounter(JsonObject payload) {
    // Build the SQL query dynamically
    StringBuilder query = new StringBuilder("INSERT INTO counter_result (");
    StringBuilder placeholders = new StringBuilder("VALUES (");
    Tuple tuple = Tuple.tuple();

    for (int i = 0; i < COLUMNS.length; i++) {
      // Append column name
      query.append(COLUMNS[i]);
      // Append placeholder ($1, $2, etc.)
      placeholders.append("$").append(i + 1);
      // Add value to tuple
      tuple.addString(payload.getString(COLUMNS[i]));

      // Add commas except for the last element
      if (i < COLUMNS.length - 1) {
        query.append(", ");
        placeholders.append(", ");
      }
    }
    query.append(") ").append(placeholders).append(")");

    // Execute the query
    return pool.preparedQuery(query.toString())
      .execute(tuple)
      .mapEmpty();
  }

  private static final String[] COLUMNS = {
    "system_overall_memory_free_bytes", "system_memory_free_bytes",
    "system_overall_memory_used_bytes", "system_memory_used_bytes",
    "system_memory_installed_bytes", "system_memory_available_bytes",
    "system_cache_memory_bytes", "system_buffer_memory_bytes",
    "system_overall_memory_used_percent", "system_memory_used_percent",
    "system_overall_memory_free_percent", "system_memory_free_percent",
    "system_swap_memory_free_bytes", "system_swap_memory_used_bytes",
    "system_swap_memory_used_percent", "system_swap_memory_free_percent",
    "system_load_avg1_min", "system_load_avg5_min", "system_load_avg15_min",
    "system_cpu_cores", "system_cpu_percent", "system_cpu_kernel_percent",
    "system_cpu_idle_percent", "system_cpu_interrupt_percent", "system_cpu_io_percent",
    "system_disk_capacity_bytes", "system_disk_free_bytes", "system_disk_used_bytes",
    "system_disk_free_percent", "system_disk_used_percent",
    "system_network_tcp_connections", "system_network_udp_connections",
    "system_network_error_packets", "system_running_processes", "system_blocked_processes",
    "system_threads", "system_os_name", "system_os_version", "system_name",
    "started_time", "started_time_seconds", "system_context_switches_per_sec"
  };

}
