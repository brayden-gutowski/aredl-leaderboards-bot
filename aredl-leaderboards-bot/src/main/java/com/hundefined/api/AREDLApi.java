package com.hundefined.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;


public class AREDLApi {

    private static final String BASE_URL = "https://api.aredl.net/v2/api/aredl/leaderboard";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AREDLApi() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public CompletableFuture<JsonNode> getGlobalLeaderboard(int amount) {
        
        String url = BASE_URL + "?page=1&per_page=" + amount;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {

                    if (response.statusCode() != 200) {
                        throw new RuntimeException(
                                "AREDL API returned status " + response.statusCode()
                        );
                    }

                    try {
                        return objectMapper.readTree(response.body());
                    } catch (Exception e) {
                        throw new RuntimeException("Could not parse AREDL response", e);
                    }
                });
    }
}