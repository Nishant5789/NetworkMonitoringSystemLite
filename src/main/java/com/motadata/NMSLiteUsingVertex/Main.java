package com.motadata.NMSLiteUsingVertex;

import com.motadata.NMSLiteUsingVertex.services.Server;
import com.motadata.NMSLiteUsingVertex.services.Poller;
import com.motadata.NMSLiteUsingVertex.verticle.DiscoveryVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;;

public class Main {

  private static final Vertx vertx = Vertx.vertx();

  public static Vertx vertx()
  {
    return vertx;
  }

  public static void main(String[] args) {
    vertx.deployVerticle(Server.class.getName(),new DeploymentOptions().setInstances(1));
    vertx.deployVerticle(DiscoveryVerticle.class.getName(),new DeploymentOptions().setInstances(1));
    vertx.deployVerticle(Poller.class.getName(),new DeploymentOptions().setInstances(1).setThreadingModel(ThreadingModel.WORKER));
  }
}
