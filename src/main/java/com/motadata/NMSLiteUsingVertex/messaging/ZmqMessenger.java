package com.motadata.NMSLiteUsingVertex.messaging;

import com.motadata.NMSLiteUsingVertex.services.Credential;
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
  private ZMQ.Context context;

  private ZMQ.Socket dealer;

  private static final Logger LOGGER =  Logger.getLogger(Credential.class.getName());

  private static final int RESPONSE_CHECK_INTERVAL = 1000;

  private static final long REQUEST_EXPIRE_DURATION = 86400; // one day

  private static final long REQUEST_TIMEOUT_CHECK_INTERVAL = 2000;

  private final Map<String, PendingRequest> pendingRequests = new HashMap<>();

  private static class PendingRequest
  {
    Message<JsonObject> message;

    long timestamp;

    PendingRequest(Message<JsonObject> message, long timestamp)
    {
      this.message = message;
      this.timestamp = timestamp;
    }
  }

  public void start(Promise<Void> startPromise)
  {
    context = ZMQ.context(1);

    dealer = context.socket(SocketType.DEALER);

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
    LOGGER.info("zmq request send using: " + Thread.currentThread().getName() + "with data : " + message.body());

    var requestId =  UUID.randomUUID().toString();
    var messagePayload =  message.body();

    messagePayload.put(REQUEST_ID, requestId);

    pendingRequests.put(requestId, new PendingRequest(message, System.currentTimeMillis()));

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
          PendingRequest requestValue = pendingRequests.get(requestId);

          requestValue.message.reply(replyJson);

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
    var now = System.currentTimeMillis();

    var timedOutRequests = pendingRequests.entrySet().stream().filter(entry -> now - entry.getValue().timestamp >= REQUEST_EXPIRE_DURATION).toList();

    timedOutRequests.forEach(entry ->
    {
//      logger.warn("Request {} timed out", entry.getKey());

      entry.getValue().message.fail(408, "Request timed out");

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
