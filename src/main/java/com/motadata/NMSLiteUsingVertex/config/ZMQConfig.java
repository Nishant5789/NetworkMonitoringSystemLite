package com.motadata.NMSLiteUsingVertex.config;

import com.motadata.NMSLiteUsingVertex.services.ObjectManager;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import org.zeromq.ZMQ;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.ZMQ_ADDRESS;

public class ZMQConfig
{
//  private static final Logger LOGGER = AppLogger.getLogger();
  private static final Logger LOGGER =  Logger.getLogger(ZMQConfig.class.getName());


  private static final String ZMQ_ADDRESS = "tcp://localhost:5555";

  private static ZMQ.Context context = ZMQ.context(1);

  private static ZMQ.Socket dealerSocket;

  private static ZMQ.Socket reqSocket;

  // ✅ Initialize Dealer Socket
  public static ZMQ.Socket getDealerSocket()
  {
      dealerSocket = context.socket(ZMQ.DEALER);
      dealerSocket.setReceiveTimeOut(0);
      dealerSocket.setHWM(0);
      dealerSocket.connect(ZMQ_ADDRESS);
      return dealerSocket;
  }

  // Initialize Req Socket
  public static ZMQ.Socket getReqSocket()
  {
      reqSocket = context.socket(ZMQ.REQ);
      reqSocket.connect(ZMQ_ADDRESS);
      return reqSocket;
  }

  //Close & terminate Dealer & req Socket
  public static void closeSocket()
  {
    if(dealerSocket!=null)
    {
      dealerSocket.close();
      LOGGER.info("Dealer socket closed");
    }
    if(reqSocket!=null)
    {
      reqSocket.close();
      LOGGER.info("REQ socket closed.");
    }
    if (context != null)
    {
      context.term();
      LOGGER.info("ZMQ Context terminated.");
    }
  }
}
