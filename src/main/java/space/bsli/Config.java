package space.bsli;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Config {
    public static String BOT_TOKEN;
    public static long GUILD_ID;
    public static long MOD_CHANNEL_ID;
    public static long RULES_CHANNEL_ID;
    public static long ONBOARDING_ROLE_ID;

    public static void loadConfig() {
        File configFile = new File("config.json");
        if (!configFile.exists()) {
            System.err.println("Config file 'config.json' not found!");
            System.exit(1);
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            JSONObject json = new JSONObject(new JSONTokener(fis));
            BOT_TOKEN = json.getString("discord-api-token");
            GUILD_ID = json.getLong("guild-id");
            MOD_CHANNEL_ID = json.getLong("mod-channel-id");
            RULES_CHANNEL_ID = json.getLong("rules-channel-id");
            ONBOARDING_ROLE_ID = json.getLong("onboarding-role-id");
        } catch (IOException e) {
            System.err.println("Failed to read config.json: " + e.getMessage());
            System.exit(1);
        }
    }
}