package com.motadata.NMSLiteUsingVertex.routes;

import com.motadata.NMSLiteUsingVertex.Main;
import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;

public class CredentialsRouter
{
  private static final Logger LOGGER = LoggerFactory.getLogger(CredentialsRouter.class);

  private  static final Router router = Router.router(Main.vertx());

  // return subrouter for crednetial Routing
  public static Router getRouter()
  {
    // POST /api/credentials - Save new credential
    router.post("/").handler(CredentialsRouter::saveCredential);

    // GET /api/credentials - Get all credentials
    router.get("/").handler(CredentialsRouter::getAllCredentials);

    // GET /api/credentials/:name - Find credential by name
    router.get("/:name").handler(CredentialsRouter::findCredentialByName);

    // PUT /api/credentials/:name - Update credential by name
    router.put("/:name").handler(CredentialsRouter::updateCredential);

    return router;
  }


  // handle save credential
  private static void saveCredential(RoutingContext ctx)
  {
    JsonObject payload = ctx.body().asJsonObject();

    if (!Utils.isValidPayload(CREDENTIAL_TABLE, payload))
    {
      JsonObject response = Utils.createResponse("error", "Invalid credential payload: Missing required fields.");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
    }

    LOGGER.info("Saving credential: {}", payload);

    QueryHandler.save(CREDENTIAL_TABLE, payload)
      .onSuccess(v ->
      {
        LOGGER.info("Credential saved successfully");

        JsonObject response = Utils.createResponse("success", "Credential saved successfully.");

        ctx.response().setStatusCode(201).end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.error("Failed to save credential: {}", err.getMessage());

        JsonObject response = Utils.createResponse("error", "Failed to save credential: " + err.getMessage());

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }


  //  handle getalll credential
  private static void getAllCredentials(RoutingContext ctx)
  {
    LOGGER.info("Fetching all credentials");

    QueryHandler.getAll(CREDENTIAL_TABLE)
      .onSuccess(credentials ->
      {
        LOGGER.info("Fetched credentials successfully");

        JsonArray response = new JsonArray(credentials);

        ctx.response().end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.error("Failed to fetch credentials: {}", err.getMessage());

        JsonObject response = Utils.createResponse("error", "Failed to fetch credentials: " + err.getMessage());

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // handle find credential by name
  private static void findCredentialByName(RoutingContext ctx)
  {
    String name = ctx.pathParam(NAME_HEADER_PATH);

    if (name == null || name.trim().isEmpty())
    {
      LOGGER.warn("Invalid credential name received: {}", name);

      JsonObject response = Utils.createResponse("failed", "Invalid credential name: Name cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
    }

    LOGGER.info("Finding credential by name: {}", name);

    QueryHandler.getByfield(CREDENTIAL_TABLE, "name = $1", name)
      .onSuccess(credential ->
      {
        if (credential == null)
        {
          LOGGER.warn("Credential not found: {}", name);

          JsonObject response = Utils.createResponse("failed", "Credential not found");

          ctx.response().setStatusCode(404).end(response.encodePrettily());
        }
        else
        {
          LOGGER.info("Found credential: {}", credential);

          ctx.response()
            .end(credential.encodePrettily());
        }
      })
      .onFailure(err ->
      {
        LOGGER.error("Failed to find credential: {}", err.getMessage());

        JsonObject response = Utils.createResponse("failed", "Credential not found");

        ctx.response()
          .setStatusCode(500)
          .end("Failed to find credential: " + err.getMessage());
      });
  }

  // handle update credential
  private static void updateCredential(RoutingContext ctx)
  {
    String name = ctx.pathParam(NAME_HEADER_PATH);

    if (name == null || name.trim().isEmpty())
    {
      LOGGER.warn("Invalid credential name received: {}", name);

      JsonObject response = Utils.createResponse("failed", "Invalid credential name: Name cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
    }

    JsonObject payload = ctx.body().asJsonObject();

    LOGGER.info("Updating credential for name: {}", name);

    if (!Utils.isValidPayload(CREDENTIAL_TABLE,payload))
    {
      LOGGER.warn("Invalid payload: {}", payload);

      JsonObject response = Utils.createResponse("failed", "Invalid credential payload: Missing required fields");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
    }

    QueryHandler.updateByField(CREDENTIAL_TABLE, payload, "name = $4", name)
      .onSuccess(v ->
      {
        LOGGER.info("Credential updated successfully for name: {}", name);

        JsonObject response = Utils.createResponse("success", "Credential updated successfully");

        ctx.response().setStatusCode(200).end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.error("Failed to update credential: {}", err.getMessage());

        JsonObject response = Utils.createResponse("error", "Failed to update credential: " + err.getMessage());

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }
}
