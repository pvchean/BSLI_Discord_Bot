package space.bsli;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

// Extending ListenerAdapter to handle button interactions
public class AutoModeration extends ListenerAdapter {
    private static final Duration SPAM_WINDOW = Duration.ofSeconds(15);
    private static final int DISTINCT_CHANNEL_THRESHOLD = 3;

    private static final Map<Long, List<MessageLog>> userHistory = new ConcurrentHashMap<>();

    public record MessageLog(String content, long channelId, long messageId, Instant timestamp) {}

    // High Severity: Phishing, typo-squatted domains, steam scams (24-hour timeout)
    public static final List<Pattern> HIGH_SEVERITY_PATTERNS = List.of(
            // FIX 1: Added negative lookahead to explicitly ignore official Discord domains
            Pattern.compile("\\b(?!(?:www\\.)?(?:discord|discordapp)\\.(?:com|gg|gift|net|media)\\b)d[li]scord[a-z0-9-]*\\.(?:com|gift|club|online|app|xyz|ru|info|net)\\b", Pattern.CASE_INSENSITIVE),

            // FIX 2: Added domain extensions and word boundaries so casual chat like "is steam-nitro a scam?" doesn't trigger a 24-hour ban
            Pattern.compile("\\b(?:1nitro|appnitro|steam-nitro)\\.(?:com|xyz|club|online|ru|net|info)\\b", Pattern.CASE_INSENSITIVE),

            Pattern.compile("accidentally reported.*steam", Pattern.CASE_INSENSITIVE),

            // FIX 3: Your original regex literally targeted the OFFICIAL steamcommunity.com.
            // This replacement targets typo-squats (e.g., steamcommmunity.com, steamcommunity-gift.com) while ignoring the real one.
            Pattern.compile("\\b(?!(?:www\\.)?steamcommunity\\.com\\b)steam[a-z]*community[a-z0-9-]*\\.[a-z]+\\b", Pattern.CASE_INSENSITIVE),

            Pattern.compile("(?:free crypto|giveaway).*?(?:seed phrase|private key)", Pattern.CASE_INSENSITIVE)
    );

    // Low Severity: Genuine official discord.gift links (5-minute timeout)
    public static final List<Pattern> LOW_SEVERITY_PATTERNS = List.of(
            Pattern.compile("https?://(?:www\\.)?discord\\.gift/[a-zA-Z0-9]+", Pattern.CASE_INSENSITIVE),

            // FIX 4: Added a word boundary (\b) so a malicious link like "fakediscord.gift/abc" doesn't accidentally trigger the low-severity filter
            Pattern.compile("\\bdiscord\\.gift/[a-zA-Z0-9]+", Pattern.CASE_INSENSITIVE)
    );

    // Single entry point to sort through the message
    public static void processMessage(MessageReceivedEvent event, String content) {
        // 1. Check High Severity Patterns first
        for (Pattern pattern : HIGH_SEVERITY_PATTERNS) {
            if (pattern.matcher(content).find()) {
                handleScamMessage(event, content, true);
                return;
            }
        }

        // 2. Check Low Severity Patterns second
        for (Pattern pattern : LOW_SEVERITY_PATTERNS) {
            if (pattern.matcher(content).find()) {
                handleScamMessage(event, content, false);
                return;
            }
        }

        // 3. If no patterns matched, check for spam
        handleAntiSpam(event, content);
    }

    public static void handleAntiSpam(MessageReceivedEvent event, String content) {
        long userId = event.getAuthor().getIdLong();
        long channelId = event.getChannel().getIdLong();
        long messageId = event.getMessageIdLong();
        Instant now = Instant.now();

        List<MessageLog> history = userHistory.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (history) {
            history.removeIf(log -> Duration.between(log.timestamp(), now).compareTo(SPAM_WINDOW) > 0);
            history.add(new MessageLog(content, channelId, messageId, now));

            // Extract logs matching the current repeated content
            List<MessageLog> matchingLogs = history.stream()
                    .filter(log -> log.content().equalsIgnoreCase(content))
                    .toList();

            Set<Long> uniqueChannels = new HashSet<>();
            for (MessageLog log : matchingLogs) {
                uniqueChannels.add(log.channelId());
            }

            if (uniqueChannels.size() >= DISTINCT_CHANNEL_THRESHOLD) {
                Member member = event.getMember();

                if (member != null && event.getGuild().getSelfMember().canInteract(member)) {
                    Duration timeoutDuration = Duration.ofMinutes(10);

                    // 1. Delete all tracked instances of the spammed message across channels
                    for (MessageLog log : matchingLogs) {
                        TextChannel targetChannel = event.getGuild().getTextChannelById(log.channelId());
                        if (targetChannel != null) {
                            targetChannel.deleteMessageById(log.messageId()).queue(
                                    null,
                                    error -> System.err.println("Failed to delete spammed message: " + error.getMessage())
                            );
                        }
                    }

                    // 2. Apply timeout to the spammer and alert moderators
                    member.timeoutFor(timeoutDuration)
                            .reason("Automated Anti-Spam: Cross-channel identical messages")
                            .queue(
                                    success -> {
                                        sendModAlertEmbed(
                                                event.getJDA().getTextChannelById(Config.MOD_CHANNEL_ID),
                                                "🛡️ Anti-Spam Action Taken",
                                                member,
                                                "Identical message posted in " + uniqueChannels.size() + " channels within " + SPAM_WINDOW.toSeconds() + "s (Messages deleted)",
                                                content,
                                                timeoutDuration
                                        );
                                        history.clear();
                                    },
                                    error -> System.err.println("Failed to timeout user: " + error.getMessage())
                            );
                }
            }
        }
    }

    public static void handleScamMessage(MessageReceivedEvent event, String rawContent, boolean isHighSeverity) {
        Member member = event.getMember();
        event.getMessage().delete().queue();

        if (member != null && event.getGuild().getSelfMember().canInteract(member)) {
            Duration timeoutDuration = isHighSeverity ? Duration.ofDays(1) : Duration.ofMinutes(5);
            String title = isHighSeverity ? "🚨 Scam Link Detected" : "⚠️ Risky Link Detected";
            String reason = isHighSeverity ? "Posted high-severity scam link/phrase." : "Posted low-severity link (gift link).";

            member.timeoutFor(timeoutDuration)
                    .reason("Automated Security: " + reason)
                    .queue(
                            success -> sendModAlertEmbed(
                                    event.getJDA().getTextChannelById(Config.MOD_CHANNEL_ID),
                                    title,
                                    member,
                                    reason,
                                    rawContent,
                                    timeoutDuration
                            ),
                            error -> System.err.println("Failed to timeout user: " + error.getMessage())
                    );
        }
    }

    private static void sendModAlertEmbed(TextChannel modChannel, String title, Member member, String reason, String originalMessage, Duration duration) {
        if (modChannel == null) {
            System.err.println("Mod channel ID is invalid or channel cannot be found.");
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(member.getAsMention() + " (`" + member.getUser().getId() + "`) was actioned.")
                .addField("Reason", reason, false)
                .addField("Original Message", "```\n" + originalMessage.replace("`", "`\u200B") + "\n```", false)
                .addField("Timeout Duration", (duration.toHours() >= 1 ? duration.toHours() + " hours" : duration.toMinutes() + " minutes"), true)
                .setColor(Color.RED)
                .setThumbnail(member.getEffectiveAvatarUrl())
                .setTimestamp(Instant.now());

        // Button format: automod:undo:<UserId>
        Button undoBtn = Button.danger("automod:undo:" + member.getId(), "Undo Timeout");

        modChannel.sendMessageEmbeds(embed.build())
                .addActionRow(undoBtn)
                .queue();
    }

    // Listens for the Undo button click
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().startsWith("automod:undo:")) {
            handleUndoButton(event);
        }
    }

    private void handleUndoButton(ButtonInteractionEvent event) {
        // Permission check: Must be an Administrator
        if (event.getMember() == null || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("You must be an **Administrator** to undo moderation actions.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String targetUserId = event.getComponentId().replace("automod:undo:", "");

        if (event.getGuild() != null) {
            event.getGuild().retrieveMemberById(targetUserId).queue(
                    target -> {
                        // Remove timeout
                        target.removeTimeout().queue(
                                success -> {
                                    EmbedBuilder replyEmbed = new EmbedBuilder()
                                            .setTitle("Penalty Undone")
                                            .setDescription("The timeout for " + target.getAsMention() + " was removed by " + event.getUser().getAsMention() + ".")
                                            .setColor(Color.GREEN)
                                            .setTimestamp(Instant.now());

                                    // Disable the button so it can't be clicked again
                                    event.editButton(event.getButton().asDisabled()).queue();
                                    // Send confirmation
                                    event.getChannel().sendMessageEmbeds(replyEmbed.build()).queue();
                                },
                                error -> event.reply("Failed to remove timeout: " + error.getMessage()).setEphemeral(true).queue()
                        );
                    },
                    error -> event.reply("Failed to find that user in the server.").setEphemeral(true).queue()
            );
        }
    }
}