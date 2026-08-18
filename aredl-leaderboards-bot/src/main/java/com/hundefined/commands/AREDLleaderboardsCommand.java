package com.hundefined.commands;
import java.awt.Color;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import com.fasterxml.jackson.databind.JsonNode;
import com.hundefined.api.AREDLApi;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
            case "server" -> {
                try {
                    showServerLeaderboard(event.getHook(), sortMethod, 1);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    event.getHook()
                            .editOriginal("Couldn't contact AREDL API")
                            .queue();
                }
            }
        }
    }

    private void showServerLeaderboard(
            InteractionHook hook,
            int sortType,
            int page
    ) throws IOException, InterruptedException {

        System.out.println("sortMethod: " + sortType);

        Guild guild = hook.getInteraction().getGuild();

        if (guild == null) {
            hook.editOriginal(
                    "This command can only be used in a server."
            ).queue();
            return;
        }

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


        Set<String> serverMemberIds =
                guild.getMembers()
                        .stream()
                        .map(Member::getId)
                        .collect(Collectors.toSet());


        List<JsonNode> playerList = new ArrayList<>();

        for (JsonNode player : cachedPlayerList) {

            String discordId =
                    player.path("user")
                            .path("discord_id")
                            .asText("");

            if (!discordId.isEmpty()
                    && serverMemberIds.contains(discordId)) {

                playerList.add(player);
            }
        }

        if (playerList.isEmpty()) {

            hook.editOriginal(
                    "No players from this server are currently on the AREDL leaderboard."
            ).setComponents().queue();

            return;
        }


        StringBuilder leaderboard = new StringBuilder();



        switch (sortType) {
            case 1:
                playerList.sort(
                        Comparator.comparingInt(
                                (JsonNode player) ->
                                        player.path("extremes").asInt()
                        ).reversed()
                );
                break;

            case 2:
                playerList.sort(
                        Comparator.comparingInt(player ->
                                levelPositions.getOrDefault(
                                        player.path("hardest")
                                                .path("name")
                                                .asText(),

                                        Integer.MAX_VALUE
                                )
                        )
                );
                break;


            case 0:
            default:
                playerList.sort(
                        Comparator.comparingDouble(
                                (JsonNode player) ->
                                        player.path("total_points").asDouble()
                        ).reversed()
                );
                break;
        }

        int totalPages = Math.max(
                1,
                (playerList.size() + PLAYERS_PER_PAGE - 1)
                        / PLAYERS_PER_PAGE
        );

        page = Math.max(
                1,
                Math.min(page, totalPages)
        );

        int startIndex =
                (page - 1) * PLAYERS_PER_PAGE;

        int endIndex =
                Math.min(
                        startIndex + PLAYERS_PER_PAGE,
                        playerList.size()
                );

        List<JsonNode> playerListDisplay =
                new ArrayList<>(
                        playerList.subList(
                                startIndex,
                                endIndex
                        )
                );

        int counter = startIndex + 1;



        for (JsonNode entry : playerListDisplay) {

            String playerName =
                    entry.path("user")
                            .path("global_name")
                            .asText("Unknown");

            double points =
                    entry.path("total_points")
                            .asDouble() / 10.0;

            int extremes =
                    entry.path("extremes")
                            .asInt();

            String hardest = "Unknown";

            if (!entry.path("hardest").isNull()
                    && !entry.path("hardest").isMissingNode()) {

                hardest =
                        entry.path("hardest")
                                .path("name")
                                .asText("Unknown");
            }

            String medal = "";

            if (counter == 1) {
                medal = "🥇 ";
            } else if (counter == 2) {
                medal = "🥈 ";
            } else if (counter == 3) {
                medal = "🥉 ";
            }

            if (sortType == 0) {

                leaderboard.append(
                        String.format(
                                "%s**#%d %s**\n%,.1f points • %d extreme demons • Hardest: %s\n\n",
                                medal,
                                counter,
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
                                counter,
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
                                counter,
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

        embed.setTitle("🏠  AREDL Leaderboard -- Server  🏠");
        embed.setDescription(leaderboard.toString());
        embed.setColor(new Color(0,33,165));
        embed.setFooter("Page " + page + " / " + totalPages + "  •  Data pulled from AREDL API and is updated at midnight EST");


        hook.editOriginalEmbeds(embed.build())
                .setComponents(
                        createPageButtons(
                                page,
                                totalPages,
                                sortType,
                                "server"
                        )
                )
                .queue();
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
                new ArrayList<>(playerList.subList(startIndex, endIndex));

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
                                sortType,
                                "global"
                        )
                )
                .queue();
    }

    private ActionRow createPageButtons(int page, int totalPages, int sortType, String leaderboardType) {

        Button backButton = Button.secondary(
                "aredl_" + leaderboardType
                        + ":back:"
                        + page
                        + ":"
                        + sortType,
                "⬅ Back"
        );

        Button nextButton = Button.secondary(
                "aredl_" + leaderboardType
                        + ":next:"
                        + page
                        + ":"
                        + sortType,
                "Next ➡"
        );

        if(leaderboardType.equals("global")){
            backButton = Button.primary(
                    "aredl_" + leaderboardType
                            + ":back:"
                            + page
                            + ":"
                            + sortType,
                    "⬅ Back"
            );

            nextButton = Button.primary(
                    "aredl_" + leaderboardType
                            + ":next:"
                            + page
                            + ":"
                            + sortType,
                    "Next ➡"
            );
        }

        if (page <= 1) {
            backButton = backButton.asDisabled();
        }

        if (page >= totalPages) {
            nextButton = nextButton.asDisabled();
        }

        return ActionRow.of(
                backButton,
                nextButton
        );
    }

    public void handleButton(ButtonInteractionEvent event) {

        String buttonId =
                event.getComponentId();

        if (!buttonId.startsWith("aredl_global:")
                && !buttonId.startsWith("aredl_server:")) {

            return;
        }

        String[] parts =
                buttonId.split(":");

        if (parts.length != 4) {
            return;
        }

        String leaderboardType =
                parts[0];

        String direction =
                parts[1];

        final int sortType;
        final int currentPage;

        try {

            currentPage = Integer.parseInt(parts[2]);

            sortType = Integer.parseInt(parts[3]);

        } catch (NumberFormatException e) {

            event.reply("Invalid leaderboard button.")
            .setEphemeral(true)
            .queue();

            return;
        }


        final int newPage;

        if (direction.equals("next")) {

            newPage = currentPage + 1;

        } else if (direction.equals("back")) {

            newPage = currentPage - 1;

        } else {

            return;
        }


        event.deferEdit().queue(hook -> {

            try {

                if (leaderboardType.equals("aredl_global")) {

                    showGlobalLeaderboard(hook, sortType, newPage);

                } else if (leaderboardType.equals("aredl_server")) {

                    showServerLeaderboard(hook, sortType, newPage);
                }

            } catch (
                    IOException |
                    InterruptedException e
            ) {

                e.printStackTrace();

                hook.sendMessage("Couldn't update the AREDL leaderboard.")
                .setEphemeral(true)
                .queue();
            }

        });
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        throw new UnsupportedOperationException("Unimplemented method 'onSlashCommandInteraction'");
    }

    
}