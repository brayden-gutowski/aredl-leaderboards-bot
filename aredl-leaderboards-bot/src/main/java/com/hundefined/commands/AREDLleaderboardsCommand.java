package com.hundefined.commands;
import java.awt.Color;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import com.fasterxml.jackson.databind.JsonNode;
import com.hundefined.api.AREDLApi;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class AREDLleaderboardsCommand implements Command {

    private static final int PLAYERS_PER_PAGE = 10;

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
        event.deferReply().queue();

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
                    showGlobalLeaderboard(event.getHook(), sortMethod, 1);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    event.getHook()
                            .editOriginal("Couldn't contact AREDL API")
                            .queue();
                }
            }
            case "server" -> event.getHook()
                    .editOriginal("Server leaderboards WIP")
                    .queue();
            case "country" -> event.getHook()
                    .editOriginal("Country leaderboards WIP")
                    .queue();
        }
    }

    private void showGlobalLeaderboard(InteractionHook hook, int sortType, int page) throws IOException, InterruptedException {

        System.out.println("sortMethod: " + sortType);

        Map<String, Integer> levelPositions =
            aredlApi.getCachedLevelPositions();

        List<JsonNode> cachedPlayerList =
                aredlApi.getCachedLeaderboard();

        if (cachedPlayerList == null) {
            hook.editOriginal(
                    "AREDL leaderboard cache is still loading. Try again shortly."
            ).queue();

            return;
        }

        List<JsonNode> playerList =
                new ArrayList<>(cachedPlayerList);

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

        int totalPages = Math.max(
                1,
                (playerList.size() + PLAYERS_PER_PAGE - 1)
                        / PLAYERS_PER_PAGE
        );

        // Safety: don't allow an invalid page
        page = Math.max(1, Math.min(page, totalPages));

        int startIndex =
                (page - 1) * PLAYERS_PER_PAGE;

        int endIndex =
                Math.min(
                        startIndex + PLAYERS_PER_PAGE,
                        playerList.size()
                );

        List<JsonNode> playerListDisplay =
                new ArrayList<>(
                        playerList.subList(startIndex, endIndex)
                );

        int counter = startIndex + 1;



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
        embed.setColor(new Color(250, 70, 22));
        embed.setFooter("Page " + page + " / " + totalPages + "  •  Data pulled from AREDL API and is updated at midnight EST");

        hook.editOriginalEmbeds(embed.build())
                .setComponents(
                        createPageButtons(
                                page,
                                totalPages,
                                sortType
                        )
                )
                .queue();
    }

    private ActionRow createPageButtons(
            int page,
            int totalPages,
            int sortType
    ) {

        int backPage = Math.max(1, page - 1);

        int nextPage = Math.min(totalPages, page + 1);

        Button backButton =
                Button.primary(
                        "aredl_global:" + sortType + ":" + backPage,
                        "← Back"
                )
                .withDisabled(page <= 1);

        Button nextButton =
                Button.primary(
                        "aredl_global:" + sortType + ":" + nextPage,
                        "Next →"
                )
                .withDisabled(page >= totalPages);

        return ActionRow.of(
                backButton,
                nextButton
        );
    }

    public void handleButton(ButtonInteractionEvent event) {

        String buttonId =
                event.getComponentId();

        if (!buttonId.startsWith("aredl_global:")) {
            return;
        }

        String[] parts =
                buttonId.split(":");

        if (parts.length != 3) {
            return;
        }

        final int sortType;
        final int page;

        try {

            sortType =
                    Integer.parseInt(parts[1]);

            page =
                    Integer.parseInt(parts[2]);

        } catch (NumberFormatException e) {

            event.reply(
                    "Invalid leaderboard button."
            )
            .setEphemeral(true)
            .queue();

            return;
        }

        event.deferEdit().queue(hook -> {

            try {

                showGlobalLeaderboard(
                        hook,
                        sortType,
                        page
                );

            } catch (
                    IOException |
                    InterruptedException e
            ) {

                e.printStackTrace();

                hook.sendMessage(
                        "Couldn't update the AREDL leaderboard."
                )
                .setEphemeral(true)
                .queue();
            }

        });
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onSlashCommandInteraction'");
    }

    
}