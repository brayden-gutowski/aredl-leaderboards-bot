package com.hundefined;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hundefined.config.BotConfig;
import com.hundefined.listeners.CommandListener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;


public class AREDLbot {
    private static final Logger logger = LoggerFactory.getLogger(AREDLbot.class);
    private static JDA jda;

    public static void main(String[] args){
        String botToken = BotConfig.getBotToken();

        if(botToken == null || botToken.isEmpty()){
            logger.error("Bot token is not set in the configuration file.");
            return;
        }

        try{
            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(EnumSet.allOf(GatewayIntent.class))
                    .addEventListeners(new CommandListener())
                    .build();

                jda.awaitReady();
                logger.info("Bot is ready and connected to Discord");

                registerSlashCommands();
        } catch (Exception e){
            logger.error("Error initializing bot: ", e);
        }

    }

    private static void registerSlashCommands() {
        if (jda == null) {
            logger.error("JDA instance is not initialized. Cannot register slash commands.");
            return;
        }
        
        logger.info("Registering slash commands...");
        jda.updateCommands()
                .addCommands(
                    Commands.slash("aredlleaderboards", "Displays the AREDL leaderboards either server-wide or globally")
                        .addOptions(
                            new OptionData(OptionType.STRING, "scope", "Choose the scope of the leaderboard").setRequired(true)
                                .addChoice("global", "global")
                                .addChoice("country", "country")
                                .addChoice("server", "server")
                        )
                        .addOptions(
                            new OptionData(OptionType.STRING, "sort", "Choose how to sort the leaderboard").setRequired(true)
                                .addChoice("points", "points")
                                .addChoice("extremes", "extremes")
                                .addChoice("hardest", "hardest")
                        ),
                        
                    Commands.slash("echo", "Test command to echo back a message")
                        .addOption(OptionType.STRING, "text", "The message to echo back", true)
                )
                .queue(success -> logger.info("Slash commands are registered"),
                          failure -> logger.error("Failed to register slash commands: ", failure));
    }
}
