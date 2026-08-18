package com.hundefined.listeners;

import com.hundefined.commands.Command;
import com.hundefined.commands.EchoCommand;
import com.hundefined.commands.AREDLleaderboardsCommand;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CommandListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CommandListener.class);
    private final Map<String, Object> commands = new HashMap<>();

    public CommandListener() {
        commands.put("aredlleaderboards", new AREDLleaderboardsCommand());
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
        Object rawCommand = commands.get(commandName);

        if (rawCommand instanceof Command command) {
            logger.info("Executing command: {}", commandName, event.getUser().getName());
            command.executeSlash(event);
        } else {
            logger.warn("Received unknown command: {}", commandName);
            event.reply("Unknown command: " + commandName).setEphemeral(true).queue();
        }
    }
    @Override
    public void onButtonInteraction(
            @NotNull ButtonInteractionEvent event
    ) {
        if (!event.getComponentId()
                .startsWith("aredl_")) {

            return;
        }

        Object rawCommand =
                commands.get("aredlleaderboards");

        if (rawCommand
                instanceof AREDLleaderboardsCommand command) {

            command.handleButton(event);
        }
    }
}