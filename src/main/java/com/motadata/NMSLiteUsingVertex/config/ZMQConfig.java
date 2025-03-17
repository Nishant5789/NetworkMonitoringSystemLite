package com.motadata.NMSLiteUsingVertex.config;

import org.zeromq.ZMQ;

public class ZMQConfig
{
  private final ZMQ.Context context;

  private final ZMQ.Socket socket;

  public ZMQConfig()
  {
    this.context = ZMQ.context(1);

    this.socket = context.socket(ZMQ.REQ);

    socket.connect("tcp://127.0.0.1:5555");
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
