package com.hundefined.listeners;

import com.hundefined.commands.Command;
import com.hundefined.commands.EchoCommand;
import com.hundefined.commands.AREDLleaderboardsCommand;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
public class CommandListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CommandListener.class);
    private final Map<String, Command> commands = new HashMap<>();

    public CommandListener() {
        commands.put("AREDLleaderboards", new AREDLleaderboardsCommand());
        commands.put("echo", new EchoCommand());
        logger.info("Registered commands: {}", commands.size());
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        logger.info("Bot is ready and connected to Discord",
                event.getJDA().getSelfUser().getName(),
                event.getJDA().getSelfUser().getDiscriminator());
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        Command command = commands.get(commandName);

        if (command != null) {
            logger.info("Executing command: {}", commandName, event.getUser().getName());
            command.executeSlash(event);
        } else {
            logger.warn("Received unknown command: {}", commandName);
            event.reply("Unknown command: " + commandName).setEphemeral(true).queue();
        }
    }
}