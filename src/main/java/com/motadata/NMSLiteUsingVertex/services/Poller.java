package com.motadata.NMSLiteUsingVertex.services;

import io.vertx.core.AbstractVerticle;

public class Poller extends AbstractVerticle {
  @Override
  public void start() {
    System.out.println("Poller deploy on : " + Thread.currentThread().getName());
    vertx.setPeriodic(5000, id -> {
      System.out.println("Polling running...");
    });
  }
}
