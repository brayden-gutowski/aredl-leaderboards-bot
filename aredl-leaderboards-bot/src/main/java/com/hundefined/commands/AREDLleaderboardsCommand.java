package com.hundefined.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.hundefined.api.AREDLApi;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.Color;

public class AREDLleaderboardsCommand implements Command {

    private final AREDLApi aredlApi = new AREDLApi();

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
            case "global" -> showGlobalLeaderboard(event, sortMethod);
            case "server" -> event.reply("Server leaderboards WIP")
                    .setEphemeral(true)
                    .queue();
            case "country" -> event.reply("Country leaderboards WIP")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void showGlobalLeaderboard(SlashCommandInteractionEvent event, int sortType) {

        System.out.println("sortMethod: " + sortType);

        event.deferReply().queue();

        aredlApi.getGlobalLeaderboard(10)
                .thenAccept(json -> {

                    JsonNode players = json.get("data");

                    StringBuilder leaderboard = new StringBuilder();

                    for (JsonNode entry : players) {

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

                        if (rank == 1) {
                            medal = "🥇 ";
                        } else if (rank == 2) {
                            medal = "🥈 ";
                        } else if (rank == 3) {
                            medal = "🥉 ";
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

    private void print(String string) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'print'");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onSlashCommandInteraction'");
    }

    
}