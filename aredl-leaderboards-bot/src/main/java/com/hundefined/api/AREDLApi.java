package com.hundefined.api;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


public class AREDLApi {

    private volatile List<JsonNode> cachedLeaderboard = null;
    private volatile CompletableFuture<List<JsonNode>> refreshInProgress = null;
    private volatile Map<String, Integer> cachedLevelPositions = new HashMap<>();

    private static final Object AREDL_REQUEST_LOCK = new Object();
    private static final int PER_PAGE = 100;
    private static final String CACHE_FILE_PATH_LEADERBOARD = "cached_leaderboard.json";
    private static final String CACHE_FILE_PATH_LEVEL_POSITIONS = "cached_level_positions.json";

    private static final long REQUEST_DELAY_MS = 1500;

    private static final String BASE_URL = "https://api.aredl.net/v2/api/aredl/leaderboard";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AREDLApi() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();

        loadCacheFromDisk();
        loadLevelPositionsFromDisk();

        if (cachedLeaderboard == null) {
            refreshCache();
        }

        if (cachedLevelPositions.isEmpty()) {
            try {
                refreshLevelCache();
            } catch (Exception e) {
                System.err.println("Failed to refresh level cache on startup.");
                e.printStackTrace();
            }
        }

        scheduleRefresh();
    }

    private void scheduleRefresh() {

        ZoneId eastern = ZoneId.of("America/New_York");
        ZonedDateTime now = ZonedDateTime.now(eastern);
        ZonedDateTime nextMidnight = now
                .plusDays(1)
                .toLocalDate()
                .atStartOfDay(eastern);

        long initialDelay =
                Duration.between(now, nextMidnight).toMillis();

        CompletableFuture.delayedExecutor(
                initialDelay,
                TimeUnit.MILLISECONDS
        ).execute(() -> {

            refreshCache();

            scheduleRefresh();
        });
    }

    public synchronized CompletableFuture<List<JsonNode>> refreshCache() {

        if (refreshInProgress != null) {
            return refreshInProgress;
        }

        System.out.println("Refreshing AREDL Leaderboard");

        refreshInProgress = CompletableFuture.supplyAsync(() -> {

            try {
                refreshLevelCache();

                List<JsonNode> newCache = new ArrayList<>();

                JsonNode firstPage = getLeaderboardRateLimit(1);

                int totalPages = firstPage.path("pages").asInt();

                firstPage.path("data").forEach(newCache::add);

                for (int page = 2; page <= totalPages; page++) {

                    Thread.sleep(REQUEST_DELAY_MS);

                    JsonNode pageData =
                            getLeaderboardRateLimit(page);

                    pageData.path("data").forEach(newCache::add);

                    System.out.println(
                            "AREDL cache: page "
                            + page
                            + "/"
                            + totalPages
                    );
                }

                cachedLeaderboard = new ArrayList<>(newCache);

                System.out.println(
                        "AREDL cache refresh complete! "
                        + cachedLeaderboard.size()
                        + " players cached."
                );

                try {
                    objectMapper.writeValue(
                        new File(CACHE_FILE_PATH_LEADERBOARD),
                        cachedLeaderboard
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return new ArrayList<>(cachedLeaderboard);

            } catch (Exception e) {

                System.err.println(
                        "AREDL cache refresh failed:"
                );

                e.printStackTrace();

                throw new RuntimeException(e);
            }

        });

        refreshInProgress.whenComplete((result, error) -> {
            synchronized (AREDLApi.this) {
                refreshInProgress = null;
            }
        });

        return refreshInProgress;
    }

    private void loadLevelPositionsFromDisk() {

        File fileName = new File(CACHE_FILE_PATH_LEVEL_POSITIONS);

        if (!fileName.exists()) {
            System.out.println("No saved AREDL level positions cache found.");
            return;
        }

        try {
            JsonNode savedData = objectMapper.readTree(fileName);

            Map<String, Integer> loadedLevelPositions = new HashMap<>();

            savedData.fields().forEachRemaining(entry -> {
                loadedLevelPositions.put(
                    entry.getKey(),
                    entry.getValue().asInt()
                );
            });

            cachedLevelPositions = loadedLevelPositions;

            System.out.println(
                "Loaded " + cachedLevelPositions.size()
                + " AREDL levels from saved cache."
            );

        } catch (IOException e) {
            System.err.println("Could not load saved AREDL level positions cache.");
            e.printStackTrace();
        }
    }

    private void loadCacheFromDisk() {

        File fileName = new File(CACHE_FILE_PATH_LEADERBOARD);

        if (!fileName.exists()) {
            System.out.println("No saved AREDL cache found.");
            return;
        }

        try {
            JsonNode savedData = objectMapper.readTree(fileName);

            List<JsonNode> loadedPlayers = new ArrayList<>();

            for (JsonNode player : savedData) {
                loadedPlayers.add(player);
            }

            cachedLeaderboard = loadedPlayers;

            System.out.println(
                "Loaded " + cachedLeaderboard.size()
                + " players from saved AREDL cache."
            );

        } catch (IOException e) {
            System.err.println("Could not load saved AREDL cache.");
            e.printStackTrace();
        }
    }

    private JsonNode getLeaderboardRateLimit(int page)
            throws Exception {

        synchronized (AREDL_REQUEST_LOCK) {

            String url = BASE_URL
                    + "?page="
                    + page
                    + "&per_page="
                    + PER_PAGE;

            while (true) {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                if (response.statusCode() == 200) {
                    return objectMapper.readTree(response.body());
                }

                if (response.statusCode() == 429) {

                    long retrySeconds = response.headers()
                            .firstValue("Retry-After")
                            .map(value -> {
                                try {
                                    return Long.parseLong(value);
                                } catch (NumberFormatException e) {
                                    return 60L;
                                }
                            })
                            .orElse(60L);

                    System.out.println(
                            "AREDL rate limit hit. Waiting "
                            + retrySeconds
                            + " seconds..."
                    );

                    Thread.sleep(retrySeconds * 1000);

                    continue;
                }

                throw new RuntimeException(
                        "AREDL API returned status "
                        + response.statusCode()
                        + " on page "
                        + page
                );
            }
        }
    }

    private void refreshLevelCache() throws Exception {

        String url = "https://api.aredl.net/v2/api/aredl/levels";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        while (true) {

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {

                JsonNode levels = objectMapper.readTree(response.body());

                Map<String, Integer> newLevelPositions = new HashMap<>();

                for (JsonNode level : levels) {
                    newLevelPositions.put(
                            level.path("name").asText(),
                            level.path("position").asInt()
                    );
                }

                cachedLevelPositions = newLevelPositions;

                try {
                    objectMapper.writeValue(
                        new File(CACHE_FILE_PATH_LEVEL_POSITIONS),
                        cachedLevelPositions
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                }

                System.out.println(
                        "Cached " + cachedLevelPositions.size() + " AREDL levels."
                );

                return;
            }

            if (response.statusCode() == 429) {
                System.out.println("Rate limit hit.  Trying again in a minute.");
                Thread.sleep(60_000);
                continue;
            }

            throw new RuntimeException(
                    "AREDL levels API returned status " + response.statusCode()
            );
        }
    }

    public Map<String, Integer> getCachedLevelPositions() {
        return new HashMap<>(cachedLevelPositions);
    }

    public CompletableFuture<JsonNode> getGlobalLeaderboard(int page, int amount) {

        String url = BASE_URL + "?page=" + page + "&per_page=" + amount;

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

    public List<JsonNode> getCachedLeaderboard() {
        if (cachedLeaderboard == null) {
            return null;
        }

        return new ArrayList<>(cachedLeaderboard);
    }
}