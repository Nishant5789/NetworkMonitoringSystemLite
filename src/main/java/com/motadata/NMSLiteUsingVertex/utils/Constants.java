package com.motadata.NMSLiteUsingVertex.utils;

public class Constants
{
  public static final String ZMQ_ADDRESS = "tcp://127.0.0.1:5555";

  public static final String CREDENTIAL_TABLE = "credential";

  public static final String DISCOVERY_TABLE = "discovery";

  public static final String PROVISIONED_OBJECTS_TABLE = "provisioned_objects";

  public static final String POLLING_RESULTS_TABLE = "polling_results";

  public static final String DISCOVERY_EVENT = "discovery";

  public static final String PROVISION_EVENT = "provision";

  public static final String POLLING_EVENT = "polling";

  public static final String DISCOVERY_REQUEST = "discovery_request";

  public static final String POLLING_REQUEST = "polling_request";

  public static final String ZMQ_REQUEST_EVENT = "zmq_request";

  public static final String POLLING_RESPONCE_EVENT = "polling_resonce";

  public static final String ID_HEADER_PATH = "id";

  public static final String OBJECT_ID_HEADER_PATH = "object_id";

  public static final String IP_HEADER_PATH = "ip_address";

  public static final String CREDENTIAL_NAME_KEY = "credential_name";

  public static final String SYSTEM_TYPE_KEY = "system_type";

  public static final String CREDENTIAL_DATA_KEY = "credential_data";

  public static final String USERNAME_KEY = "username";

  public static final String PASSWORD_KEY = "password";

  public static final String ID_KEY = "id";

  public static final String METRICS_DATA_KEY = "data";

  public static final String REQUEST_ID = "request_id";

  public static final String CREDENTIAL_ID_KEY = "credential_id";

  public static final String DISCOVERY_ID_KEY = "discovery_id";

  public static final String OBJECT_ID_KEY = "object_id";

  public static final String TIMESTAMP_KEY = "timestamp";

  public static final String COUNTERS_KEY = "counters";

  public static final String IS_VALID_KEY = "isValid";

  public static final String ERROR_KEY = "error";

  public static final String POLL_INTERVAL_KEY = "pollinterval";

  public static final String LAST_POLL_TIME_KEY = "lastpolltime";

  public static final String DISCOVERY_STATUS_KEY = "discovery_status";

  public static final String STATUS_KEY = "status";

  public static final String STATUS_COMPLETED = "completed";

  public static final String STATUS_PENDING = "pending";

  public static final String STATUS_MSG_KEY = "statusMsg";

  public static final String PORT_KEY = "port";

  public static final String PORT_VALUE = "22";

  public static final String TRUE_VALUE = "true";

  public static final String FALSE_VALUE = "false";

  public static final String IP_KEY = "ip";

  public static final String EVENT_NAME_KEY = "event_name";

  public static final String PLUGIN_ENGINE_TYPE_KEY = "plugin_engine";

  public static final String PLUGIN_ENGINE_LINUX = "linux";

  public static final String CREDENTIAL_NAME_ERROR = "credential_name_error";

  public static final String SYSTEM_TYPE_ERROR = "system_type_error";

  public static final String USERNAME_ERROR = "username_error";

  public static final String PASSWORD_ERROR = "password_error";

  public static final String IP_ERROR = "ip_error";

  public static final String PORT_ERROR = "port_error";

  public static final String CREDENTIAL_ID_ERROR = "credential_id_error";

  public static final String POLLINTERVAL_ERROR = "pollInterval_error";

  public static final String STATUS_RESPONSE_ERROR = "error";

  public static final String STATUS_RESPONSE_FAIIED = "failed";

  public static final String STATUS_RESPONSE_SUCCESS = "success";

  public static final Integer DATABASE_PORT = 5432;

  public static final String DATABASE_HOST = "localhost";

  public static final String DEFAULT_DATABASE_NAME = "postgres";

  public static final String DATABASE_USERNAME = "postgres";

  public static final String DATABASE_PASSWORD = "1234";

  public static final Integer MAX_POOL_SIZE = 5;

  public static final String FAILURE_COUNT_KEY = "failure_count";

  public static final Integer DEAFAULT_FAILURE_VALUE = 0;

  public static final Integer THRESHOLD_FAILURE_VALUE = 5;

  public static final String DATABASE_NAME = "nms_lite_20";

  public static final String OBJECT_AVAILABILITY_KEY = "availability_status";

  public static final String OBJECT_AVAILABILITY_UP = "UP";

  public static final String OBJECT_AVAILABILITY_DOWN = "DOWN";
}
