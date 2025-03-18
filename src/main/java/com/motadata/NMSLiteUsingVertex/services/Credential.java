package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;
import static com.motadata.NMSLiteUsingVertex.utils.Constants.CREDENTIAL_TABLE;
import static com.motadata.NMSLiteUsingVertex.utils.Utils.formatInvalidResponse;

public class Credential
{
  private static final Logger LOGGER = AppLogger.getLogger();

  // handle save credential
  public static void saveCredential(RoutingContext ctx)
  {
    var payload = ctx.body().asJsonObject();

    var payloadValidationResult = Utils.isValidPayload(CREDENTIAL_TABLE, payload);

    if (payloadValidationResult.get(IS_VALID_KEY).equals("false"))
    {
      var errorResponse = Utils.createResponse("error", formatInvalidResponse(payloadValidationResult));

      ctx.response().setStatusCode(400).end(errorResponse.encodePrettily());

      return;
    }

    LOGGER.info("Saving credential: " + payload);

    QueryHandler.save(CREDENTIAL_TABLE, payload)
      .onSuccess(v ->
      {
        LOGGER.info("Credential saved successfully");

        var response = Utils.createResponse("success", "Credential saved successfully.");

        ctx.response().setStatusCode(201).end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to save credential: " + err.getMessage());

        var response = Utils.createResponse("error", "Failed to save credential: " + err.getMessage());

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // handle send all credentials
  public static void getAllCredentials(RoutingContext ctx)
  {
    LOGGER.info("Fetching all credentials");

    QueryHandler.getAll(CREDENTIAL_TABLE)
      .onSuccess(credentials ->
      {
        LOGGER.info("Fetched credentials successfully");

        var response = new JsonArray(credentials);

        ctx.response().end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to fetch credentials: " + err.getMessage());

        var response = Utils.createResponse("error", "Failed to fetch credentials: " + err.getMessage());

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // handle find credential by Id
  public static void getCredentialById(RoutingContext ctx)
  {
    var id = ctx.pathParam(ID_HEADER_PATH);

    if (id == null || id.trim().isEmpty())
    {
      LOGGER.warning("Invalid credential id received: " + id);

      var response = Utils.createResponse("failed", "Invalid credential id: Id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());

      return;
    }

    LOGGER.info("Finding credential by id: " + id);

    QueryHandler.getByField(CREDENTIAL_TABLE, "credential_id", id)
      .onSuccess(credential ->
      {
        if (credential == null)
        {
          LOGGER.warning("Credential not found: " + id);

          var response = Utils.createResponse("failed", "Credential not found");

          ctx.response().setStatusCode(404).end(response.encodePrettily());
        }
        else
        {
          LOGGER.info("Found credential: " + credential);

          ctx.response().end(credential.encodePrettily());
        }
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to find credential: " + err.getMessage());

        var response = Utils.createResponse("failed", "Credential not found");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // handle update credential
  public static void updateCredential(RoutingContext ctx)
  {
    var id = ctx.pathParam(ID_HEADER_PATH);

    if (id == null || id.trim().isEmpty())
    {
      LOGGER.warning("Invalid credential id received: " + id);

      var response = Utils.createResponse("failed", "Invalid credential id: id cannot be empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());

      return;
    }

    var payload = ctx.body().asJsonObject();

    if (payload == null || payload.isEmpty())
    {
      LOGGER.warning("payload is empty");

      var response = Utils.createResponse("failed", "Invalid payload: payload is empty");

      ctx.response().setStatusCode(400).end(response.encodePrettily());
      return;
    }

    LOGGER.info("Updating credential for id: " + id);

    QueryHandler.updateByField(CREDENTIAL_TABLE, payload, "credential_id", id)
      .onSuccess(v ->
      {
        LOGGER.info("Credential updated successfully for id: " + id);

        var response = Utils.createResponse("success", "Credential updated successfully");

        ctx.response().setStatusCode(200).end(response.encodePrettily());
      })
      .onFailure(err ->
      {
        LOGGER.severe("Failed to update credential: " + err.getMessage());

        var response = Utils.createResponse("error", "Failed to update credential: " + err.getMessage());

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }

  // handle delete credential
  public static void deleteCredential(RoutingContext ctx)
  {
    var credentialId = ctx.pathParam(ID_KEY);
    QueryHandler.deleteById(CREDENTIAL_TABLE, credentialId)
      .onSuccess(deleted ->
      {
        if (deleted)
        {
          LOGGER.info("Credential deleted successfully");

          var response = new JsonObject().put("status", "success").put("statusMsg", "Credential deleted successfully");

          ctx.response().setStatusCode(200).end(response.encodePrettily());
        }
        else
        {
          LOGGER.info("No matching record found");

          var response = new JsonObject().put("status", "success").put("statusMsg", "No matching record found");

          ctx.response().setStatusCode(200).end(response.encodePrettily());
        }
      })
      .onFailure(err ->
      {
        LOGGER.severe("Database query failed: " + err.getMessage());

        var response = Utils.createResponse("error", "Database query failed");

        ctx.response().setStatusCode(500).end(response.encodePrettily());
      });
  }
}
