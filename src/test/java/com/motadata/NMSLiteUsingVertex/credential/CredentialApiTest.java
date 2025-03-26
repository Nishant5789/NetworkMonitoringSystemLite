package com.motadata.NMSLiteUsingVertex.credential;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CredentialApiTest
{
    private static int credentialId;

    @BeforeAll
    public static void setup()
    {
        RestAssured.baseURI = "http://localhost:8080/api/credentials";
        RestAssured.defaultParser = Parser.JSON;
    }

    @Test
    @Order(1)
    public void testSaveCredential()
    {
        String requestBody = """
                {
                    "credential_name": "nish123456",
                    "credential_data": {
                        "username": "nishant",
                        "password": "password1234"
                    },
                    "system_type": "linux"
                }
                """;

         given()
                .contentType(ContentType.JSON)
                .body(requestBody)
                .when()
                .post("/")
                .then()
                .statusCode(201)
                .body("credential_name", equalTo("nish123"))
                .extract()
                .path("id");   // Assuming API returns created ID
    }

    @Test
    @Order(2)
    public void testGetAllCredentials()
    {
        given()
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .body("size()", greaterThan(0));
    }

    @Test
    @Order(3)
    public void testGetCredentialById()
    {
        given()
                .when()
                .get("/" + credentialId)
                .then()
                .statusCode(200)
                .body("credential_name", equalTo("nish123"));
    }

    @Test
    @Order(4)
    public void testUpdateCredentialById()
    {
        String updateBody = """
                {
                    "credential_data": {
                        "username": "nish_updated",
                        "password": "updatedPass123"
                    },
                    "system_type": "linux"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateBody)
                .when()
                .put("/" + credentialId)
                .then()
                .statusCode(200)
                .body("credential_data.username", equalTo("nish_updated"));
    }

    @Test
    @Order(5)
    public void testDeleteCredentialById()
    {
        given()
                .when()
                .delete("/" + credentialId)
                .then()
                .statusCode(200)
                .body("message", containsString("deleted"));
    }
}

