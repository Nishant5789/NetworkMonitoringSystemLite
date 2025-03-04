package com.motadata.NMSLiteUsingVertex.config;

import org.zeromq.ZMQ;

public class ZMQConfig
{
  private final ZMQ.Context context;

  private final ZMQ.Socket socket;

  public ZMQConfig(String address)
  {
    this.context = ZMQ.context(1);

    this.socket = context.socket(ZMQ.REQ);

    socket.connect(address);
  }

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
