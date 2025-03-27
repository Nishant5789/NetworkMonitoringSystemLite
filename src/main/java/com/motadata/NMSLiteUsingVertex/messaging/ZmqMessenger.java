package com.motadata.NMSLiteUsingVertex.messaging;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class ZmqMessenger extends AbstractVerticle
{
  private static final ZMQ.Context context = ZMQ.context(1);

  private static final ZMQ.Socket dealer = context.socket(SocketType.DEALER);

  private static final Logger LOGGER = AppLogger.getLogger();

  private static final int RESPONSE_CHECK_INTERVAL = 1000;

  private static final long REQUEST_EXPIRE_DURATION = 86400; // one day

  private static final long REQUEST_TIMEOUT_CHECK_INTERVAL = 2000;

  private final Map<String, RequestHolder> pendingRequests = new HashMap<>();

  private static class RequestHolder
  {
    Message<JsonObject> message;
    JsonObject store;
    long timestamp;

    RequestHolder(Message<JsonObject> message, long timestamp)
    {
      this.message = message;
      this.timestamp = timestamp;
    }

    RequestHolder(JsonObject objectPayload, long timestamp)
    {
      this.store = new JsonObject().put(OBJECT_ID_KEY, objectPayload.getInteger(OBJECT_ID_KEY)).put(IP_KEY, objectPayload.getString(IP_KEY));
      this.timestamp = timestamp;
    }
  }

  public void start(Promise<Void> startPromise)
  {
    dealer.setReceiveTimeOut(0);

    dealer.setHWM(0);

    dealer.connect(ZMQ_ADDRESS);

    vertx.eventBus().<JsonObject>localConsumer(ZMQ_REQUEST_EVENT, this::handleRequest);

    vertx.setPeriodic(RESPONSE_CHECK_INTERVAL, id ->
    {
      checkResponses();
    });
    vertx.setPeriodic(REQUEST_TIMEOUT_CHECK_INTERVAL, id ->
    {
      checkTimeouts();
    });

    startPromise.complete();
  }


  private void handleRequest(Message<JsonObject> message)
  {
    var requestId =  UUID.randomUUID().toString();
    var messagePayload =  message.body();

    messagePayload.put(REQUEST_ID, requestId);

    if(messagePayload.getString(EVENT_NAME_KEY).equalsIgnoreCase(DISCOVERY_EVENT))
    {
      pendingRequests.put(requestId, new RequestHolder(message, System.currentTimeMillis()));
    }
    else
    {
      pendingRequests.put(requestId, new RequestHolder(messagePayload, System.currentTimeMillis()));
    }
    
    LOGGER.info("zmq request send using: " + Thread.currentThread().getName() + " with data : " + message.body());

    dealer.send("", ZMQ.SNDMORE);

    dealer.send(messagePayload.toString());
  }

  private void checkResponses()
  {
    String response;

    while ((response = dealer.recvStr(ZMQ.DONTWAIT)) != null)
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
          LOGGER.info("zmq response received using: " + Thread.currentThread().getName() + " with statusMsg : " + replyJson.getString(STATUS_MSG_KEY));

          RequestHolder requestValue = pendingRequests.get(requestId);

          if(replyJson.getString(STATUS_MSG_KEY).equalsIgnoreCase("Metrics collected successfully"))
          {
            var objectDataWithPollResponce = replyJson.put(IP_KEY, requestValue.store.getString(IP_KEY)).put(OBJECT_ID_KEY, requestValue.store.getInteger(OBJECT_ID_KEY));

            Main.vertx().eventBus().send(POLLING_RESPONCE_EVENT, objectDataWithPollResponce);
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
        LOGGER.severe("Failed to parse response as JSON: " + response + " from plugin" + e.getMessage());
      }
    }
  }

  private void checkTimeouts()
  {
    var timedOutRequests = pendingRequests.entrySet().stream().filter(entry -> System.currentTimeMillis() - entry.getValue().timestamp >= REQUEST_EXPIRE_DURATION).toList();

    timedOutRequests.forEach(entry ->
    {
      LOGGER.warning("Request " + entry.getKey() +" timed out");

      pendingRequests.remove(entry.getKey());
    });
  }

  @Override
  public void stop(Promise<Void> stopPromise)
  {
    if (dealer != null)
    {
      dealer.close();
    }

    if (context != null)
    {
      context.close();
    }

    stopPromise.complete();
  }
}
