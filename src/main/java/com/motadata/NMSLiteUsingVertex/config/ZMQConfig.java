package com.motadata.NMSLiteUsingVertex.config;

import org.zeromq.ZMQ;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.ZMQ_ADDRESS;

public class ZMQConfig
{
  // return socket instance
  public static ZMQ.Socket getDealerSocket()
  {
    var context = ZMQ.context(1);
    var socket = context.socket(ZMQ.DEALER);

    socket.setReceiveTimeOut(0);
    socket.setHWM(0);
    socket.connect(ZMQ_ADDRESS);

    return socket;
  }

  public static ZMQ.Socket getReqSocket()
  {
    var context = ZMQ.context(1);
    var socket = context.socket(ZMQ.REQ);

    socket.connect(ZMQ_ADDRESS);

    return socket;
  }
}
