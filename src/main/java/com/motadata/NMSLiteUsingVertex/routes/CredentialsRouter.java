package com.motadata.NMSLiteUsingVertex.routes;

import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class CredentialsRouter {

  private static final Logger LOGGER = LoggerFactory.getLogger(CredentialsRouter.class);

  private final Router router;

  public CredentialsRouter(Vertx vertx){
    router = Router.router(vertx);
  }

  public Router getRouter() {

    // POST /api/credentials - Save new credential
    router.post("/").handler(this::saveCredential);

    // GET /api/credentials - Get all credentials
    router.get("/").handler(this::getAllCredentials);

    // GET /api/credentials/:name - Find credential by name
    router.get("/:name").handler(this::findCredentialByName);

    // PUT /api/credentials/:name - Update credential by name
    router.put("/:name").handler(this::updateCredential);

    return router;
  }

  private void saveCredential(RoutingContext ctx) {
    JsonObject payload = ctx.body().asJsonObject();
    LOGGER.info("Saving credential: {}", payload);

    QueryHandler.save(CREDENTIAL_TABLE, payload)
      .onSuccess(v -> {
        LOGGER.info("Credential saved successfully");
        ctx.response()
          .setStatusCode(201)
          .end("Credential saved successfully");
      })
      .onFailure(err -> {
        LOGGER.error("Failed to save credential: {}", err.getMessage());
        ctx.response()
          .setStatusCode(500)
          .end("Failed to save credential: " + err.getMessage());
      });
  }

  private void getAllCredentials(RoutingContext ctx){
    LOGGER.info("Fetching all credentials");

    QueryHandler.getAll(CREDENTIAL_TABLE)
      .onSuccess(credentials -> {
        LOGGER.info("Fetched credentials: {}", credentials);
        ctx.response()
          .putHeader("content-type", "application/json")
          .end(new JsonObject().put("credentials", credentials).encode());
      })
      .onFailure(err -> {
        LOGGER.error("Failed to fetch credentials: {}", err.getMessage());
        ctx.response()
          .setStatusCode(500)
          .end("Failed to fetch credentials: " + err.getMessage());
      });
  }

  private void findCredentialByName(RoutingContext ctx){
    String name = ctx.pathParam(NAME_HEADER_PATH);
    LOGGER.info("Finding credential by name: {}", name);

    QueryHandler.getByfield(CREDENTIAL_TABLE, "name = $1", name)
      .onSuccess(credential -> {
        if (credential == null) {
          LOGGER.warn("Credential not found: {}", name);
          ctx.response()
            .setStatusCode(404)
            .end("Credential not found");
        } else {
          LOGGER.info("Found credential: {}", credential);
          ctx.response()
            .putHeader("content-type", "application/json")
            .end(credential.encode());
        }
      })
      .onFailure(err -> {
        LOGGER.error("Failed to find credential: {}", err.getMessage());
        ctx.response()
          .setStatusCode(500)
          .end("Failed to find credential: " + err.getMessage());
      });
  }

  private void updateCredential(RoutingContext ctx) {
    String name = ctx.pathParam(NAME_HEADER_PATH);
    JsonObject payload = ctx.body().asJsonObject();

    LOGGER.info("Updating credential for name: {}", name);

    if (payload == null || !payload.containsKey(USERNAME_KEY) || !payload.containsKey(PASSWORD_KEY)) {
      LOGGER.warn("Invalid payload: {}", payload);
      ctx.response()
        .setStatusCode(400)
        .end("Invalid payload: username and password are required");
      return;
    }

    QueryHandler.updateByField(CREDENTIAL_TABLE, payload, "name = $4", name)
      .onSuccess(v -> {
        LOGGER.info("Credential updated successfully for name: {}", name);
        ctx.response()
          .setStatusCode(200)
          .end("Credential updated successfully");
      })
      .onFailure(err -> {
        LOGGER.error("Failed to update credential: {}", err.getMessage());
        ctx.response()
          .setStatusCode(500)
          .end("Failed to update credential: " + err.getMessage());
      });
  }
}
