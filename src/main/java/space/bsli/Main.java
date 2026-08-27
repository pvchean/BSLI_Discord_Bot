package space.bsli;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Main extends ListenerAdapter {
    public static JDA jda;

    public static void main(String[] args) {
        // 1. Load Configuration
        Config.loadConfig();

        // 2. Initialize JDA
        try {
            jda = JDABuilder.createDefault(Config.BOT_TOKEN)
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.watching("Launching Rockets!"))
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    // Register AutoModeration here so the Undo button listener works
                    .addEventListeners(new Main(), new OnboardingListener(), new AutoModeration())
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        // Register Guild-specific slash commands for instant updates
        Guild guild = event.getJDA().getGuildById(Config.GUILD_ID);
        if (guild != null) {
            guild.updateCommands().addCommands(
                    Commands.slash("setup-onboarding", "Start the interactive member onboarding process")
                            .setContexts(InteractionContextType.GUILD)
                            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                            .addOptions(new OptionData(OptionType.CHANNEL, "target-channel", "Channel to send onboarding to", false)
                                    .setChannelTypes(ChannelType.TEXT)),
                    Commands.slash("website", "Link to the BSLI Website")
            ).queue(
                    success -> System.out.println("Successfully registered slash commands to Guild: " + guild.getName()),
                    error -> System.err.println("Failed to register commands: " + error.getMessage())
            );
        } else {
            System.err.println("Could not find Guild ID: " + Config.GUILD_ID + " to register commands.");
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem() || !event.isFromGuild()) {
            return;
        }

        Member member = event.getMember();
        // Skip moderation if the user is an Administrator
        // (Comment this out temporarily if you are testing this yourself)
        if (member != null && member.hasPermission(Permission.ADMINISTRATOR)) {
            return;
        }

        String content = event.getMessage().getContentRaw().trim();
        if (content.isEmpty()) {
            return;
        }

        // Pass the event to AutoModeration to sort through the patterns and spam
        AutoModeration.processMessage(event, content);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("website")) {
            event.reply("https://bsli.space/").setEphemeral(true).queue();
        }
    }
}