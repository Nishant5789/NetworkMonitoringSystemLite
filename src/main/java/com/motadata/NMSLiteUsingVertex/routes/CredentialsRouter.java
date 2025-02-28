package com.motadata.NMSLiteUsingVertex.routes;

import com.motadata.NMSLiteUsingVertex.services.CredentialService;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CredentialsRouter {

  Router router;
  CredentialService credentialService;

  public CredentialsRouter(Vertx vertx){
    router = Router.router(vertx);
    credentialService = new CredentialService(vertx);
  }

  public Router getRouter() {
    // POST /api/credentials - Save new credential
    router.post("/").handler(this::saveCredential);

//    // GET /api/credentials - Get all credentials
    router.get("/").handler(this::getAllCredentials);

    // GET /api/credentials/:name - Find credential by name
    router.get("/:name").handler(this::findCredentialByName);

    // PUT /api/credentials/:name - Update credential by name
    router.put("/:name").handler(this::updateCredential);

    return router;
  }

  private void saveCredential(RoutingContext ctx) {
    JsonObject payload = ctx.body().asJsonObject();
    String name = payload.getString("name");
    String username = payload.getString("username");
    String password = payload.getString("password");

    credentialService.save(payload)
      .onSuccess(v -> ctx.response()
        .setStatusCode(201)
        .end("Credential saved successfully"))
      .onFailure(err -> ctx.response()
        .setStatusCode(500)
        .end("Failed to save credential: " + err.getMessage()));
  }

  private  void  getAllCredentials(RoutingContext ctx){
    credentialService.getAll()
      .onSuccess(credentials ->
        ctx.response()
        .putHeader("content-type", "application/json")
        .end(new JsonObject().put("credentials", credentials).encode()))
      .onFailure(err -> ctx.response()
        .setStatusCode(500)
        .end("Failed to fetch credentials: " + err.getMessage()));
  }

  private void  findCredentialByName(RoutingContext ctx){
    String name = ctx.pathParam("name");

    credentialService.findByName(name)
      .onSuccess(credential -> {
        if (credential == null) {
          ctx.response()
            .setStatusCode(404)
            .end("Credential not found");
        } else {
          ctx.response()
            .putHeader("content-type", "application/json")
            .end(credential.encode());
        }
      })
      .onFailure(err -> ctx.response()
        .setStatusCode(500)
        .end("Failed to find credential: " + err.getMessage()));
  }

  private void updateCredential(RoutingContext ctx) {
    String name = ctx.pathParam("name");
    JsonObject payload = ctx.body().asJsonObject();
    if (payload == null || !payload.containsKey("username") || !payload.containsKey("password")) {
      ctx.response()
        .setStatusCode(400)
        .end("Invalid payload: username and password are required");
      return;
    }

    credentialService.update(name, payload)
      .onSuccess(v -> ctx.response()
        .setStatusCode(200)
        .end("Credential updated successfully"))
      .onFailure(err -> ctx.response()
        .setStatusCode(500)
        .end("Failed to update credential: " + err.getMessage()));
  }

}
