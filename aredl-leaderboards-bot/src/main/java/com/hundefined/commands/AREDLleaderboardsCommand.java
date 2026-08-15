package com.hundefined.commands;
import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hundefined.api.AREDLApi;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class AREDLleaderboardsCommand implements Command {

    private final AREDLApi aredlApi = new AREDLApi();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String getName() {
        return "aredlleaderboards";
    }

    @Override
    public String getDescription() {
        return "Displays the AREDL leaderboards either server-wide or globally";
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String subcommand = event.getOption("scope").getAsString();
        int sortMethod = 0;
        
        if (event.getOption("sort") != null) {
            String sortChoice = event.getOption("sort").getAsString();
            sortMethod = switch (sortChoice) {
                case "points" -> 0;
                case "extremes" -> 1;
                case "hardest" -> 2;
                default -> 0;
            };
        }

        if (subcommand == null) {
            return;
        }

        switch (subcommand) {
            case "global" -> {
                try {
                    showGlobalLeaderboard(event, sortMethod);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    event.reply("Couldn't contact AREDL API")
                            .setEphemeral(true)
                            .queue();
                }
            }
            case "server" -> event.reply("Server leaderboards WIP")
                    .setEphemeral(true)
                    .queue();
            case "country" -> event.reply("Country leaderboards WIP")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void showGlobalLeaderboard(SlashCommandInteractionEvent event, int sortType) throws IOException, InterruptedException {

        System.out.println("sortMethod: " + sortType);

        Map<String, Integer> levelPositions = new HashMap<>();

        HttpRequest levelRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.aredl.net/v2/api/aredl/levels"))
                .GET()
                .build();

        HttpResponse<String> levelResponse = httpClient.send(
                levelRequest,
                HttpResponse.BodyHandlers.ofString()
        );

        JsonNode levels = objectMapper.readTree(levelResponse.body());

        for (JsonNode level : levels) {
            String name = level.path("name")
                .asText();
            int position = level.path("position").asInt();

            levelPositions.put(name, position);
        }

        event.deferReply().queue();



        aredlApi.getAllGlobalLeaderboard()
                .thenAccept(playerList -> {

                    StringBuilder leaderboard = new StringBuilder();


                    switch (sortType) {
                        case 1:
                            playerList.sort(
                                Comparator.comparingInt(
                                    (JsonNode player) -> player.path("extremes").asInt()
                                ).reversed()
                            );
                            break;

                        case 2:
                            playerList.sort(
                                Comparator.comparingInt(player ->
                                    levelPositions.getOrDefault(
                                        player.path("hardest").path("name").asText(),
                                        Integer.MAX_VALUE
                                    )
                                )
                            );
                            break;

                        case 0:
                        default:
                            playerList.sort(
                                Comparator.comparingDouble(
                                    (JsonNode player) -> player.path("total_points").asDouble()
                                ).reversed()
                            );
                            break;
                    }

                    List<JsonNode> playerListDisplay  = new java.util.ArrayList<>(playerList.subList(0, 10));

                    int counter = 1;



                    for (JsonNode entry : playerListDisplay) {

                        int rank = entry.get("rank").asInt();

                        String playerName =
                                entry.get("user")
                                     .get("global_name")
                                     .asText();

                        double points =
                                entry.get("total_points").asDouble() / 10.0;

                        int extremes =
                                entry.get("extremes").asInt();

                        String hardest = "Unknown";

                        if (!entry.get("hardest").isNull()) {
                            hardest = entry.get("hardest")
                                    .get("name")
                                    .asText();
                        }

                        String medal = "";

                        if (counter == 1) {
                            medal = "🥇 ";
                        } else if (counter == 2) {
                            medal = "🥈 ";
                        } else if (counter == 3) {
                            medal = "🥉 ";
                        } else {
                            medal = "";
                        }

                        if (sortType == 0) {
                            leaderboard.append(
                                    String.format(
                                            "%s**#%d %s**\n%,.1f points • %d extreme demons • Hardest: %s\n\n",
                                            medal,
                                            rank,
                                            playerName,
                                            points,
                                            extremes,
                                            hardest
                                    )
                            );
                        } else if (sortType == 1) {
                            leaderboard.append(
                                    String.format(
                                            "%s**#%d %s**\n%,d extreme demons • Hardest: %s • %.1f points\n\n",
                                            medal,
                                            rank,
                                            playerName,
                                            extremes,
                                            hardest,
                                            points
                                    )
                            );
                        } else if (sortType == 2) {
                            leaderboard.append(
                                    String.format(
                                            "%s**#%d %s**\nHardest: %s • %.1f points • %,d extreme demons\n\n",
                                            medal,
                                            rank,
                                            playerName,
                                            hardest,
                                            points,
                                            extremes
                                    )
                            );

                        }
                        counter++;
                    }

                    EmbedBuilder embed = new EmbedBuilder();

                    embed.setTitle("🌎  AREDL Leaderboard -- Global  🌏");
                    embed.setDescription(leaderboard.toString());
                    embed.setColor(new Color(0,33,165));
                    embed.setFooter("*Data pulled from AREDL API");

                    event.getHook()
                            .editOriginalEmbeds(embed.build())
                            .queue();
                })

                .exceptionally(error -> {

                    error.printStackTrace();

                    event.getHook()
                            .editOriginal(
                                    "Couldn't contact AREDL API"
                            )
                            .queue();

                    return null;
                });
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onSlashCommandInteraction'");
    }

    
}