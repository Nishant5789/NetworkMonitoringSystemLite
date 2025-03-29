package com.motadata.NMSLiteUsingVertex.services;

import com.motadata.NMSLiteUsingVertex.database.QueryHandler;
import com.motadata.NMSLiteUsingVertex.utils.AppLogger;
import com.motadata.NMSLiteUsingVertex.utils.Utils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.logging.Logger;

import static com.motadata.NMSLiteUsingVertex.utils.Constants.*;
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
            ctx.response().setStatusCode(400).end(Utils.createResponse(STATUS_RESPONSE_ERROR, formatInvalidResponse(payloadValidationResult)).encodePrettily());
            return;
        }

        ctx.vertx().executeBlocking(promise ->
        {
            QueryHandler.save(CREDENTIAL_TABLE, payload)
            .onSuccess(promise::complete)
            .onFailure(promise::fail);
        })
        .onSuccess(v ->
        {
            ctx.response().setStatusCode(201).end(Utils.createResponse(STATUS_RESPONSE_SUCCESS, "Credential saved successfully.").encodePrettily());
        })
        .onFailure(err ->
        {
            LOGGER.severe("Failed to save credential: " + err.getMessage());

            var errorMessage = (err.getMessage()!=null && err.getMessage().contains("duplicate key value")) ? "Try with a different name, this name is already used by another credential":"Failed to save credential";

            ctx.response().setStatusCode(500).end(Utils.createResponse(STATUS_RESPONSE_ERROR, errorMessage).encodePrettily());
        });
    }

    // handle send all credentials
    public static void getAllCredentials(RoutingContext ctx)
    {
        LOGGER.info("Fetching all credentials");

        ctx.vertx().<List<JsonObject>>executeBlocking(promise ->
        {
             QueryHandler.getAll(CREDENTIAL_TABLE)
               .onSuccess(promise::complete)
               .onFailure(promise::fail);
        })
        .onSuccess(credentials ->
        {
            ctx.response().putHeader("Content-Type", "application/json").end(new JsonArray(credentials).encodePrettily());
        })
        .onFailure(err ->
        {
            LOGGER.severe("Failed to fetch credentials: " + err.getMessage());

            ctx.response().setStatusCode(500).end(Utils.createResponse(STATUS_RESPONSE_ERROR, "Failed to fetch credentials: " + err.getMessage()).encodePrettily());
        });
    }

    // handle find credential by Id
    public static void getCredentialById(RoutingContext ctx)
    {
        var id = ctx.pathParam(ID_HEADER_PATH);

        if (id==null || id.trim().isEmpty())
        {
            ctx.response().setStatusCode(400).end(Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid credential id: Id cannot be empty").encodePrettily());
            return;
        }

        ctx.vertx().<JsonObject>executeBlocking(promise ->
        {
             QueryHandler.getOneByField(CREDENTIAL_TABLE, CREDENTIAL_ID_KEY, id)
               .onSuccess(promise::complete)
               .onFailure(promise::fail);
        })
        .onSuccess(credential ->
        {
            if (credential==null)
            {
                ctx.response().setStatusCode(404).end(Utils.createResponse(STATUS_RESPONSE_FAIIED, "Credential not found").encodePrettily());
            }
            else
            {
                ctx.response().end(credential.encodePrettily());
            }
        })
        .onFailure(err ->
        {
            ctx.response().setStatusCode(500).end(Utils.createResponse(STATUS_RESPONSE_FAIIED, "Credential not found").encodePrettily());
        });
    }

    // handle update credential
    public static void updateCredential(RoutingContext ctx)
    {
        var credentialId = ctx.pathParam(ID_HEADER_PATH);

        if (credentialId==null || credentialId.trim().isEmpty())
        {
            ctx.response().setStatusCode(400).end(Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid credential id: id cannot be empty").encodePrettily());
            return;
        }

        var payload = ctx.body().asJsonObject();

        if (payload==null || payload.isEmpty())
        {
            ctx.response().setStatusCode(400).end(Utils.createResponse(STATUS_RESPONSE_FAIIED, "Invalid payload: payload is empty").encodePrettily());
            return;
        }

        ctx.vertx().<Void>executeBlocking(promise ->
        {
             QueryHandler.updateByField(CREDENTIAL_TABLE, payload, CREDENTIAL_ID_KEY, credentialId)
               .onSuccess(promise::complete)
               .onFailure(promise::fail);
        })
        .onSuccess(v ->
        {
            ctx.response().setStatusCode(200).end(Utils.createResponse(STATUS_RESPONSE_SUCCESS, "Credential updated successfully").encodePrettily());
        })
        .onFailure(err ->
        {
            var errorMessage = err.getMessage()!=null && err.getMessage().contains("duplicate key value") ? "Try with a different name, this name is already used by another credential":"Failed to save credential";

            ctx.response().setStatusCode(500).end(Utils.createResponse(STATUS_RESPONSE_ERROR, errorMessage).encodePrettily());
        });
    }

    // handle delete credential
    public static void deleteCredential(RoutingContext ctx)
    {
        var credentialId = ctx.pathParam(ID_KEY);

        ctx.vertx().<Boolean>executeBlocking(promise ->
        {
             QueryHandler.deleteById(CREDENTIAL_TABLE, credentialId)
               .onSuccess(promise::complete)
               .onFailure(promise::fail);
        })
        .onSuccess(deletedStatus ->
        {
          var statusMsg = deletedStatus ? "Credential deleted successfully":"No matching record found";

          ctx.response().setStatusCode(200).end(new JsonObject().put(STATUS_KEY, STATUS_RESPONSE_SUCCESS).put(STATUS_MSG_KEY, statusMsg).encodePrettily());
        })
        .onFailure(err ->
        {
            LOGGER.severe("Database query failed: " + err.getMessage());

            var errorMessage = err.getMessage().contains("violates foreign key constraint") ? "Database query failed because the credential is used by a provisioned object":"Database query failed";

            ctx.response().setStatusCode(500).end(Utils.createResponse(STATUS_RESPONSE_ERROR, errorMessage).encodePrettily());
        });
    }
}
