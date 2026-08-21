package de.terrestris.shogun.migrator.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import de.terrestris.shogun.migrator.model.HostDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

class ApiUtilTest {

    @Test
    void getTokenSetsToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/auth/realms/SHOGun/protocol/openid-connect/token", exchange -> {
            String response = "{\"access_token\":\"test-token\"}";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();

        try {
            HostDto host = new HostDto(baseUrl(server), "user", "password");
            host.setClientId("test-client");

            ApiUtil.getToken(host);

            Assertions.assertEquals("test-token", host.getToken());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchBootUsesBearerAndUnwrapsContent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/applications", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String response = "{\"content\":[{\"id\":1}]}";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();

        try {
            HostDto host = new HostDto(baseUrl(server), "user", "password");
            host.setToken("boot-token");

            JsonNode node = ApiUtil.fetch(host, "applications", true);

            Assertions.assertEquals("Bearer boot-token", authorization.get());
            Assertions.assertTrue(node.isArray());
            Assertions.assertEquals(1, node.get(0).get("id").asInt());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchLegacyUsesBasicAuth() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/rest/projectlayers", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String response = "[{\"id\":2}]";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();

        try {
            HostDto host = new HostDto(baseUrl(server), "legacy-user", "legacy-password");

            JsonNode node = ApiUtil.fetch(host, "rest/projectlayers", false);

            Assertions.assertNotNull(authorization.get());
            Assertions.assertTrue(authorization.get().startsWith("Basic "));
            Assertions.assertEquals(2, node.get(0).get("id").asInt());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deleteSendsBearerHeader() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/layers/5", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            HostDto host = new HostDto(baseUrl(server), "user", "password");
            host.setToken("delete-token");

            ApiUtil.delete(host, "layers/5");

            Assertions.assertEquals("Bearer delete-token", authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void saveLayerPostsJsonAndReturnsId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/layers", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(readBody(exchange.getRequestBody()));
            String response = "{\"id\":99}";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();

        try {
            HostDto host = new HostDto(baseUrl(server), "user", "password");
            host.setToken("save-token");

            int id = ApiUtil.saveLayer("{\"name\":\"layer-a\"}".getBytes(StandardCharsets.UTF_8), host);

            Assertions.assertEquals(99, id);
            Assertions.assertEquals("Bearer save-token", authorization.get());
            Assertions.assertTrue(requestBody.get().contains("\"name\":\"layer-a\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void saveApplicationPostsJson() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/applications", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(readBody(exchange.getRequestBody()));
            String response = "{\"id\":123}";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();

        try {
            HostDto host = new HostDto(baseUrl(server), "user", "password");
            host.setToken("app-token");

            ApiUtil.saveApplication("{\"name\":\"app-a\"}".getBytes(StandardCharsets.UTF_8), host);

            Assertions.assertEquals("Bearer app-token", authorization.get());
            Assertions.assertTrue(requestBody.get().contains("\"name\":\"app-a\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void makeLayerPublicPostsToExpectedEndpoint() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/layers/11/permissions/public", exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            HostDto host = new HostDto(baseUrl(server), "user", "password");
            host.setToken("public-token");

            ApiUtil.makeLayerPublic(host, 11);

            Assertions.assertEquals("POST", method.get());
            Assertions.assertEquals("Bearer public-token", authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void hasPrivateConstructor() throws Exception {
        Constructor<ApiUtil> constructor = ApiUtil.class.getDeclaredConstructor();
        Assertions.assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        constructor.setAccessible(true);
        ApiUtil instance = constructor.newInstance();
        Assertions.assertNotNull(instance);
    }

    private static String baseUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort() + "/";
    }

    private static String readBody(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

}