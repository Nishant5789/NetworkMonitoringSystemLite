package com.motadata.NMSLiteUsingVertex.messaging;

import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import org.zeromq.ZMQ;

import java.util.logging.Logger;

public class ZmqConfig
{
  // Default configurations
  private static String pushAddress = "tcp://127.0.0.1:5555";
  private static String pullAddress = "tcp://*:5556";
  private static int ioThreads = 1;
  private static int linger = 1000; // 1 second
  private static int hwm = 1000;    // High Water Mark
  private static int timeout = 5000; // 5 seconds

  private static final ZMQ.Context context = ZMQ.context(ioThreads);

  private static final Logger LOGGER = AppLogger.getLogger();

  static
  {
    // Cleanup hook
    Runtime.getRuntime().addShutdownHook(new Thread(() ->
    {
      if (!context.isClosed())
      {
        context.close();
      }
    }));
  }

  //Creates and configures a PUSH socket for sending request
  public static ZMQ.Socket createPushSocket()
  {
    ZMQ.Socket push = context.socket(ZMQ.PUSH);
    try
    {
      push.setHWM(hwm);
      push.setSendTimeOut(timeout);
      push.connect(pushAddress);
    }
    catch (Exception err)
    {
      closeSocketSilently(push);
      LOGGER.info("Failed to create PUSH socket" + err.getMessage());
    }
    return push;
  }

   // Creates and configures a PULL socket for receiving responses
  public static ZMQ.Socket createPullSocket()
  {
    ZMQ.Socket pull = context.socket(ZMQ.PULL);
    try
    {
      pull.setLinger(linger);
      pull.setHWM(hwm);
      pull.setReceiveTimeOut(timeout);
      pull.bind(pullAddress);
    }
    catch (Exception e)
    {
      closeSocketSilently(pull);
      LOGGER.info("Failed to create PULL socket" + e.getMessage());
    }
    return pull;
  }

  // handle gracefully closing the socket
  public static void closeSocketSilently(ZMQ.Socket socket)
  {
    if (socket != null)
    {
      try
      {
        socket.setLinger(0); // Drop pending messages on close
        socket.close();
      }
      catch (Exception ignored)
      {
        // Log if needed
      }
    }
  }
}
