package com.motadata.NMSLiteUsingVertex.messaging;

import com.motadata.NMSLiteUsingVertex.utils.Constants;
import org.zeromq.ZMQ;

public class ZmqConfig
{
    private static final ZMQ.Context context = ZMQ.context(1);

    private static final String ZMQ_ADDRESS = Constants.ZMQ_ADDRESS;

    public static ZMQ.Socket createDealerSocket()
    {
        ZMQ.Socket dealer = context.socket(ZMQ.DEALER);
        dealer.setReceiveTimeOut(0);
        dealer.setHWM(0);
        dealer.connect(ZMQ_ADDRESS);
        return dealer;
    }

}
