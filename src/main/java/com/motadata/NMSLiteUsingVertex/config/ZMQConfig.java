package com.motadata.NMSLiteUsingVertex.config;

import org.zeromq.ZMQ;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.ZMQ_ADDRESS;

public class ZMQConfig
{
  private final ZMQ.Context context;

  private final ZMQ.Socket socket;

  // update zmq configuration
  public ZMQConfig()
  {
    this.context = ZMQ.context(1);

    this.socket = context.socket(ZMQ.REQ);

    socket.connect(ZMQ_ADDRESS);
  }

  // return socket instance
  public ZMQ.Socket getSocket()
  {
    return socket;
  }

  public void close()
  {
    socket.close();

    context.term();
  }
}
