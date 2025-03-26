package com.motadata.NMSLiteUsingVertex.api;

import com.motadata.NMSLiteUsingVertex.Main;
import io.vertx.ext.web.Router;

public class Credential
{
  private static final Router router = Router.router(Main.vertx());

  // return subroutes for credential
  public static Router getRouter()
  {
    // POST /api/credentials - save credential
    router.post("/").handler(com.motadata.NMSLiteUsingVertex.services.Credential::saveCredential);

    // GET /api/credentials - get all credentials
    router.get("/").handler(com.motadata.NMSLiteUsingVertex.services.Credential::getAllCredentials);

    // GET /api/credentials - get  credential by id
    router.get("/:id").handler(com.motadata.NMSLiteUsingVertex.services.Credential::getCredentialById);

    // PUT /api/credentials - update credential
    router.put("/:id").handler(com.motadata.NMSLiteUsingVertex.services.Credential::updateCredential);

    // DELETE /api/credentials - delete credential
    router.delete("/:id").handler(com.motadata.NMSLiteUsingVertex.services.Credential::deleteCredential);

    return router;
  }
}
