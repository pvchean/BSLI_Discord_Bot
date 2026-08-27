package space.bsli;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class OnboardingListener extends ListenerAdapter {

    private final Color ACCENT_COLOR = Color.decode("#BB0000");
    private final ArrayList<Role> preJoinRoles = new ArrayList<>();
    public static final Map<Long, List<Role>> savedPreJoinRoles = new ConcurrentHashMap<>();


    @Override
    public void onReady(ReadyEvent event) {
        Guild guild = event.getJDA().getGuildById(Config.GUILD_ID);
        if (guild == null) {
            System.err.println("Unable to locate the BSLI Guild!");
            return;
        }
        // Add roles that will temporarily be removed during onboarding process
        var role = guild.getRoleById(Config.NASA_ROLE_ID);
        if (role != null) preJoinRoles.add(role);
        role = guild.getRoleById(Config.IREC_ROLE_ID);
        if (role != null) preJoinRoles.add(role);
        role = guild.getRoleById(Config.LRS_ROLE_ID);
        if (role != null) preJoinRoles.add(role);
    }
    // 1. Initial trigger using the registered /onboard slash command
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("setup-onboarding")) {
            if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.reply("You must have Administrator permissions to use this command.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Welcome to the Server!")
                    .setDescription("""
                            Before you join this server, we need you to complete a simple onboarding process.
                            The onboarding process it done to make sure each member has agreed to the server rules, and their name complies with the Name Lastname.# standard.
                            
                            Click the button below to start the onboarding process.
                            """)
                    .setFooter("Note: ONLY your server-specific nickname will be changed.")
                    .setColor(ACCENT_COLOR);

            MessageChannel channel;

            OptionMapping channelOption = event.getOption("target-channel");
            if (channelOption != null) {
                channel = channelOption.getAsChannel().asGuildMessageChannel();
            } else {
                channel = event.getJDA().getTextChannelById(Config.ONBOARDING_CHANNEL_ID);

                if (channel == null) {
                    System.err.println("Unable to find onboarding channel via long id.");
                    event.reply("Unable to find onboarding channel via long id.").queue();
                    return;
                }
            }

            event.deferReply(true).queue();
            channel.sendMessageEmbeds(embed.build())
                    .addActionRow(Button.primary("onboard:rule1", "Start Onboarding Process"))
                    .queue(s -> event.getHook().editOriginal("Message successfully sent in " + channel.getAsMention()).queue(),
                            f -> event.getHook().editOriginal("Message failed to be sent in " + channel.getAsMention()).queue());
        }
    }

    // 2. Handling Button Clicks
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        switch (componentId) {
            case "onboard:rule1" -> {
                EmbedBuilder rulesEmbed = new EmbedBuilder()
                        .setTitle("Server Rules")
                        .setDescription("""
                                **Please agree to the server rules to continue.**
                                
                                🚀 OSU BSLI  — Server Rules
                                By joining this server, you agree to the following rules. Violations may result in a mute, kick, or permanent ban. Serious violations may be escalated to the Ohio State University. Welcome aboard @everyone !
                                
                                📋 Conduct & Respect
                                1. Be respectful. Treat all members with kindness and professionalism. No harassment, personal attacks, slurs, hateful language, or dogpiling — toward other members, faculty, or club advisors. This applies in the server and in DMs.
                                2. No trolling or raiding. Members who join to cause disruption will be removed without warning.
                                3. Keep DMs respectful. Do not use DMs to harass members or continue arguments after someone asks you to stop. If someone tells you to leave them alone, that is your only warning.
                                4. Use your real name and dot number. Set your server nickname to your first and last name.number . This is a university organization — do not say anything here you would not say in person.\s
                                   Ex. Brutus Buckeye.1 or Wilson Dhalwani.1
                                
                                🎓 Academic Integrity
                                5. No academic misconduct. Sharing homework answers, exam content, or any form of cheating is strictly prohibited and will be reported to Ohio State's Committee on Academic Misconduct (COAM). This can result in a server ban and serious academic consequences up to and including expulsion.
                                6. Advising questions go to your advisor. This server is not a substitute for official academic advising. For time-sensitive matters (fees, enrollment, holds), contact your OSU advisor directly.
                                """)
                        .setColor(ACCENT_COLOR);

                // Send ephemeral reply so only the user sees the rest of the flow
                event.replyEmbeds(rulesEmbed.build())
                        .addActionRow(Button.success("onboard:rule2", "I Agree"))
                        .setEphemeral(true)
                        .queue();
            }
            case "onboard:rule2" -> {
                EmbedBuilder rulesEmbed = new EmbedBuilder()
                        .setTitle("Server Rules")
                        .setDescription("""
                                **Please agree to the server rules to continue.**
                                
                                💬 Content & Channels
                                7. Stay on topic and use the right channels. Follow the guidelines in each channel's description and pins — there is no excuse for not reading them.
                                8. Keep controversial content in the designated channel. Political, socially sensitive, or controversial discussions belong in the designated channel. Mods may redirect conversations at their discretion. No political or controversial memes anywhere on the server.
                                9. No spam, walls of text, or unsolicited promotions. Keep messages concise. For lengthy technical content, use threads or a paste service (e.g. Pastebin). To promote an event or outside club, DM a mod first.
                                10. SFW only. No NSFW, adult, or explicitly offensive content anywhere on the server.
                                
                                🔒 Privacy & Safety
                                11. Protect @everyone's privacy. Do not share personal information (phone numbers, addresses, class schedules, etc.) for yourself or others. This server is not private — assume anyone can read it.
                                12. No unauthorized bots. Adding bots or integrations without admin approval will result in immediate removal and potential disciplinary action.
                                13. Ping mods only when necessary. If a situation needs moderator attention, @ the mod role. Do not ping mods as a joke. For non-urgent issues, DM a mod directly.
                                """)
                        .setColor(ACCENT_COLOR);

                // Send ephemeral reply so only the user sees the rest of the flow
                event.editMessageEmbeds(rulesEmbed.build())
                        .setComponents(ActionRow.of(Button.success("onboard:rule3", "I Agree")))
                        .queue();
            }
            case "onboard:rule3" -> {
                EmbedBuilder rulesEmbed = new EmbedBuilder()
                        .setTitle("Server Rules")
                        .setDescription("""
                                **Please agree to the server rules to continue.**
                                
                                ⚠️ Escalation Policy
                                Depending on the severity of a situation, the following steps may be taken:
                                
                                Verbal reminder — for minor or first-time infractions
                                Logged warning — for repeated or more serious violations
                                Temporary mute or kick — for continued poor behavior
                                Permanent ban — for severe violations or failure to correct behavior after warnings
                                Report to Ohio State — violations involving harassment, threats, discrimination, or academic misconduct may be reported to the OSU Office of Student Conduct and/or COAM, and could result in academic disciplinary action
                                
                                If you are experiencing an issue with another member, DM a moderator — we are here to help and will handle matters confidentially where possible. We will work to ensure @everyone can feel comfortable and safe.\s
                                
                                *These rules are not exhaustive. Moderators handle situations on a case-by-case basis and have final discretion. By participating in this server, you agree to be held accountable to these rules.*
                                """)
                        .setColor(ACCENT_COLOR);

                // Send ephemeral reply so only the user sees the rest of the flow
                event.editMessageEmbeds(rulesEmbed.build())
                        .setComponents(ActionRow.of(Button.success("onboard:name", "I Agree")))
                        .queue();
            }
            case "onboard:name" -> {
                EmbedBuilder regexEmbed = new EmbedBuilder()
                        .setTitle("Set Your Name")
                        .setDescription("You need to change your name to match our format. \n\nWe need your **Preferred First Name** and your **Lastname.Number** (e.g., smith.123).")
                        .setColor(ACCENT_COLOR);

                // Edit the ephemeral message to show the next step
                event.editMessageEmbeds(regexEmbed.build())
                        .setComponents(ActionRow.of(Button.primary("onboard:modal_btn", "Let's do that")))
                        .queue();
            }
            case "onboard:modal_btn" -> {
                // Build the Text Inputs for the Modal
                TextInput firstName = TextInput.create("firstname", "Preferred First Name", TextInputStyle.SHORT)
                        .setPlaceholder("John")
                        .setRequired(true)
                        .build();

                TextInput lastNameNum = TextInput.create("lastnamenum", "Lastname.Number", TextInputStyle.SHORT)
                        .setPlaceholder("Smith.123")
                        .setRequired(true)
                        .build();

                Modal modal = Modal.create("onboard:modal", "Onboarding Form")
                        .addComponents(ActionRow.of(firstName), ActionRow.of(lastNameNum))
                        .build();

                // Open the form
                event.replyModal(modal).queue();
            }
            case "onboard:pfp_done" -> {
                // In JDA 5, getAvatarUrl() is null if the user has a default Discord avatar.
                // We check both their global user profile and their server-specific member profile.
                boolean hasCustomAvatar = event.getUser().getAvatarUrl() != null ||
                        (event.getMember() != null && event.getMember().getAvatarUrl() != null);

                if (!hasCustomAvatar) {
                    // Reject the click and send a new ephemeral warning if they still have a default avatar
                    event.reply("I just checked, and it looks like you're still using a default profile picture! Please upload a professional-ish picture and try clicking the button again.")
                            .setEphemeral(true)
                            .queue();
                    return;
                }

                EmbedBuilder finalEmbed = new EmbedBuilder()
                        .setTitle("Onboarding Complete!")
                        .setDescription("Awesome, you're all set! Welcome aboard!")
                        .setColor(ACCENT_COLOR);

                event.editMessageEmbeds(finalEmbed.build())
                        .setComponents() // Clear buttons
                        .queue();

                event.getHook().deleteOriginal().queueAfter(15, TimeUnit.SECONDS);

                // Remove the onboarding role
                if (event.getGuild() != null && event.getMember() != null) {
                    Role onboardingRole = event.getGuild().getRoleById(Config.ONBOARDING_ROLE_ID);
                    if (onboardingRole != null) {
                        var rolesToAdd = savedPreJoinRoles.remove(event.getMember().getIdLong());
                        if (rolesToAdd == null) rolesToAdd = Collections.emptyList();
                        var rolesToRemove = Collections.singletonList(onboardingRole);

                        event.getGuild().modifyMemberRoles(event.getMember(), rolesToAdd, rolesToRemove).queueAfter(3, TimeUnit.SECONDS,
                                success -> System.out.println("Removed onboarding role from " + event.getUser().getName()),
                                error -> System.err.println("Failed to remove role: Onboarding"));
                    }
                }
            }
        }

        // 3. Handling the Form Submission
    }

    // 3. Handling the Form Submission
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().equals("onboard:modal")) {
            String firstName = Objects.requireNonNull(event.getValue("firstname")).getAsString();
            String lastNameNum = Objects.requireNonNull(event.getValue("lastnamenum")).getAsString();

            // Regex: Letters only, no spaces
            Pattern firstNamePattern = Pattern.compile("^[a-zA-Z]+$");
            // Regex: Letters followed by a dot, followed by numbers ([a-z].[0-9])
            Pattern lastNameNumPattern = Pattern.compile("^[a-zA-Z]+\\.[0-9]+$");

            if (!firstNamePattern.matcher(firstName).matches()) {
                event.reply("Validation failed: **Preferred First Name** must contain letters only with no spaces.")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            if (!lastNameNumPattern.matcher(lastNameNum).matches()) {
                event.reply("Validation failed: **Lastname.Number** must follow the exact format of letters, a dot, and numbers (e.g., doe.456).")
                        .setEphemeral(true)
                        .queue();
                return;
            }

            // 1) Change the user's server nickname
            if (event.getMember() != null && Objects.requireNonNull(event.getGuild()).getSelfMember().canInteract(event.getMember())) {
                String newNickname = firstName + " (" + lastNameNum + ")";
                event.getMember().modifyNickname(newNickname).queue(
                        success -> System.out.println("Nickname updated for " + event.getUser().getName()),
                        error -> System.err.println("Failed to update nickname: " + error.getMessage())
                );
            } else {
                System.out.println("Cant interact?");
            }

            // 2) Prompt for a profile picture
            EmbedBuilder pfpEmbed = new EmbedBuilder()
                    .setTitle("Looking good, " + firstName + "!")
                    .setDescription("Your name is all set! Next up: let's get a profile picture sorted out.\n\nIt absolutely doesn't have to be a selfie, just something professional-ish that you like or a photo of the ground. \n\nClick the button below once you've updated it!")
                    .setColor(ACCENT_COLOR);

            // Edit the existing ephemeral message to show the PFP prompt and a new button
            event.editMessageEmbeds(pfpEmbed.build())
                    .setComponents(ActionRow.of(Button.success("onboard:pfp_done", "I've added a picture!")))
                    .queue();
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Role onboardingRole = event.getGuild().getRoleById(Config.ONBOARDING_ROLE_ID);

        if (onboardingRole != null) {
            event.getGuild().addRoleToMember(event.getMember(), onboardingRole)
                    .reason("Auto-assigned onboarding role upon joining.")
                    .queue(
                            success -> System.out.println("Assigned onboarding role to " + event.getUser().getName()),
                            error -> System.err.println("Failed to assign onboarding role: " + error.getMessage())
                    );
        }
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        Role onboardingRole = event.getGuild().getRoleById(Config.ONBOARDING_ROLE_ID);
        if (onboardingRole == null) return;

        var member = event.getMember();

        // Only filter roles if the user currently has the Onboarding Role
        if (member.getRoles().contains(onboardingRole)) {

            // Find which of the newly added roles match your preJoinRoles list
            List<Role> interceptedRoles = event.getRoles().stream()
                    .filter(preJoinRoles::contains)
                    .toList();

            if (!interceptedRoles.isEmpty()) {
                // Save intercepted roles to the map (appends if user already has saved roles)
                savedPreJoinRoles.compute(member.getIdLong(), (id, existingList) -> {
                    if (existingList == null) {
                        return new ArrayList<>(interceptedRoles);
                    } else {
                        List<Role> updated = new ArrayList<>(existingList);
                        for (Role role : interceptedRoles) {
                            if (!updated.contains(role)) {
                                updated.add(role);
                            }
                        }
                        return updated;
                    }
                });

                // Remove the intercepted roles so the onboarding view lock stays active
                event.getGuild().modifyMemberRoles(member, Collections.emptyList(), interceptedRoles)
                        .reason("Temporarily removing pre-join roles until onboarding completion.")
                        .queue(
                                success -> System.out.println("Intercepted and saved pre-join roles for " + event.getUser().getName()),
                                error -> System.err.println("Failed to remove pre-join roles: " + error.getMessage())
                        );
            }
        }
    }
}