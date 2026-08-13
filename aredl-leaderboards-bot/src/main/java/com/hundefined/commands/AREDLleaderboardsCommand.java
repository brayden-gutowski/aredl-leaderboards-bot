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
        String subcommand = event.getSubcommandName();

        if (subcommand == null) {
            return;
        }

        switch (subcommand) {
            case "global" -> showGlobalLeaderboard(event);
            case "server" -> event.reply("Server leaderboards WIP")
                    .setEphemeral(true)
                    .queue();
        }
    }

    private void showGlobalLeaderboard(SlashCommandInteractionEvent event) {

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