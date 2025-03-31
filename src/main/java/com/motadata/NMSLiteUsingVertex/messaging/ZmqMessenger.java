package com.motadata.NMSLiteUsingVertex.messaging;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.zeromq.ZMQ;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class ZmqMessenger extends AbstractVerticle
{
  private static final Logger LOGGER = AppLogger.getLogger();

  private static final int RESPONSE_CHECK_INTERVAL = 1000; // prod: 500 ms

  private static final long REQUEST_EXPIRE_DURATION = 300000; // prod: 300000 ms (5-min)

  private static final long REQUEST_TIMEOUT_CHECK_INTERVAL = 2000; // prod: 2000 ms(4-sec)

  private ZMQ.Socket push;
  private ZMQ.Socket pull;

  private final Map<String, RequestHolder> pendingRequests = new HashMap<>();

  private static class RequestHolder
  {
    Message<JsonObject> message;
    JsonObject store;
    String type;
    long timestamp;

    RequestHolder(String type, Message<JsonObject> message, long timestamp)
    {
      this.type = type;
      this.message = message;
      this.timestamp = timestamp;
    }

    RequestHolder(String type, JsonObject objectPayload, long timestamp)
    {
      this.type = type;
      this.store = new JsonObject().put(OBJECT_ID_KEY, objectPayload.getInteger(OBJECT_ID_KEY)).put(IP_KEY, objectPayload.getString(IP_KEY));
      this.timestamp = timestamp;
    }
  }

  public void start(Promise<Void> startPromise)
  {
    push = ZmqConfig.createPushSocket();
    pull = ZmqConfig.createPullSocket();

    Main.vertx().eventBus().<JsonObject>localConsumer(ZMQ_REQUEST_EVENT, this::handleRequest);

    Main.vertx().setPeriodic(RESPONSE_CHECK_INTERVAL, id ->
    {
      checkResponses();
    });
    Main.vertx().setPeriodic(REQUEST_TIMEOUT_CHECK_INTERVAL, id ->
    {
      checkTimeouts();
    });

    startPromise.complete();
  }

  private void handleRequest(Message<JsonObject> message)
  {
    var requestId = UUID.randomUUID().toString();
    var messagePayload = message.body();

    messagePayload.put(REQUEST_ID, requestId);

    if (messagePayload.getString(EVENT_NAME_KEY).equalsIgnoreCase(DISCOVERY_EVENT))
    {
      pendingRequests.put(requestId, new RequestHolder(DISCOVERY_REQUEST, message, System.currentTimeMillis()));
    }
    else
    {
      pendingRequests.put(requestId, new RequestHolder(POLLING_REQUEST, messagePayload, System.currentTimeMillis()));
    }

    LOGGER.info("zmq request send using: " + Thread.currentThread().getName() + " with data : " + message.body());

    push.send(messagePayload.toString());
  }

  private void checkResponses()
  {
    String response;
    try
    {
      while ((response = pull.recvStr(ZMQ.DONTWAIT)) != null)
      {
        if (response.trim().isEmpty())
        {
          continue;
        }
        try
        {
          var replyJson = new JsonObject(response);
          var requestId = replyJson.getString(REQUEST_ID);
          replyJson.remove(REQUEST_ID);

          if (pendingRequests.containsKey(requestId))
          {
            RequestHolder requestValue = pendingRequests.get(requestId);

            LOGGER.info("ZMQ response received using: " + Thread.currentThread().getName() + " with type: " + requestValue.type);

            if (requestValue.type.equalsIgnoreCase(POLLING_REQUEST))
            {
              var objectDataWithPollResponse = replyJson.put(IP_KEY, requestValue.store.getString(IP_KEY))
                .put(OBJECT_ID_KEY, requestValue.store.getInteger(OBJECT_ID_KEY));

              Main.vertx().eventBus().send(POLLING_RESPONCE_EVENT, objectDataWithPollResponse);
            }
            else
            {
              requestValue.message.reply(replyJson);
            }
            pendingRequests.remove(requestId);
          }
          else
          {
            LOGGER.severe("No pending request found for request_id: " + requestId);
          }
        }
        catch (Exception e)
        {
          LOGGER.severe("Failed to parse response as JSON: " + response + " from plugin. Error: " + e.getMessage());
        }
      }
    }
    catch (Exception e)
    {
      LOGGER.severe("Error while receiving response from ZMQ: " + e.getMessage());
    }
  }

  private void checkTimeouts()
  {
    var timedOutRequests = pendingRequests.entrySet().stream().filter(entry -> System.currentTimeMillis() - entry.getValue().timestamp >= REQUEST_EXPIRE_DURATION).toList();

    timedOutRequests.forEach(entry ->
    {
      LOGGER.warning("Request " + entry.getKey() + " timed out");

      pendingRequests.remove(entry.getKey());
    });
  }

  @Override
  public void stop(Promise<Void> stopPromise)
  {
    stopPromise.complete();
  }
}
