package com.hundefined.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class AREDLleaderboardsCommand implements Command {

    public String getName() {
        return "aredlleaderboards";
    }

    public String getDescription() {
        return "Displays the AREDL leaderboards either server-wide or globally.";
    }

    public void executeSlash(SlashCommandInteractionEvent event) {
        OptionMapping textOption = event.getOption("text");
        String textToEcho = "";
        if (textOption != null) {
            textToEcho = textOption.getAsString();
        } else {
            textToEcho = "You didn't provide any text to echo!"; // Default message if no text provided
        }

        event.reply(textToEcho).setEphemeral(false).queue();
    }
}