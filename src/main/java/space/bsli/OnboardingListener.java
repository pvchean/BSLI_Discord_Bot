package space.bsli;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.awt.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class OnboardingListener extends ListenerAdapter {

    private final Color ACCENT_COLOR = Color.decode("#BB0000");

    // 1. Initial trigger using the registered /onboard slash command
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("onboard")) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Welcome to the Server!")
                    .setDescription("Click the button below to start the onboarding process.")
                    .setColor(ACCENT_COLOR);

            event.replyEmbeds(embed.build())
                    .addActionRow(Button.primary("onboard:start", "Start Onboarding Process"))
                    .queue();
        }
    }

    // 2. Handling Button Clicks
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        switch (componentId) {
            case "onboard:start" -> {
                EmbedBuilder rulesEmbed = new EmbedBuilder()
                        .setTitle("Server Rules")
                        .setDescription("Please agree to the server rules to continue.")
                        .setColor(ACCENT_COLOR);

                // Send ephemeral reply so only the user sees the rest of the flow
                event.replyEmbeds(rulesEmbed.build())
                        .addActionRow(Button.success("onboard:agree", "Yes"))
                        .setEphemeral(true)
                        .queue();
            }
            case "onboard:agree" -> {
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
                        .setDescription("Awesome, you're all set! Welcome aboard. This message will self-destruct in 15 seconds.")
                        .setColor(ACCENT_COLOR);

                event.editMessageEmbeds(finalEmbed.build())
                        .setComponents() // Clear buttons
                        .queue();

                event.getHook().deleteOriginal().queueAfter(15, TimeUnit.SECONDS);
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

            // Remove the onboarding role
            if (event.getGuild() != null && event.getMember() != null) {
                Role onboardingRole = event.getGuild().getRoleById(Config.ONBOARDING_ROLE_ID);
                if (onboardingRole != null) {
                    event.getGuild().removeRoleFromMember(event.getMember(), onboardingRole).queue(
                            success -> System.out.println("Removed onboarding role from " + event.getUser().getName()),
                            error -> System.err.println("Failed to remove role: " + error.getMessage())
                    );
                }
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
                    .setDescription("Your name is all set! Next up: let's get a profile picture sorted out.\n\nIt absolutely doesn't have to be a selfie—just toss up something professional-ish that you like. \n\nClick the button below once you've updated it!")
                    .setColor(ACCENT_COLOR);

            // Edit the existing ephemeral message to show the PFP prompt and a new button
            event.editMessageEmbeds(pfpEmbed.build())
                    .setComponents(ActionRow.of(Button.success("onboard:pfp_done", "I've added a picture!")))
                    .queue();
        }
    }
}