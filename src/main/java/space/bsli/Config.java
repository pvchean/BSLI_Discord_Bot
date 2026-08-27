package space.bsli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Config {
    public static String BOT_TOKEN;
    public static long GUILD_ID;

    public static long MOD_CHANNEL_ID;
    public static long RULES_CHANNEL_ID;
    public static long ONBOARDING_CHANNEL_ID;

    public static long ONBOARDING_ROLE_ID;
    //public static long NASA_ROLE_ID;
    //public static long IREC_ROLE_ID;
    //public static long LRS_ROLE_ID;




    private static final Map<String, String> dotEnvMap = new HashMap<>();

    public static void loadConfig() {
        // Automatically check for a local .env file first
        loadDotEnv();

        // Check environment variables (supports DISCORD_API_TOKEN or BOT_TOKEN)
        BOT_TOKEN = requireEnv("BOT_TOKEN", "DISCORD_API_TOKEN");
        GUILD_ID = parseLongEnv("GUILD_ID");

        MOD_CHANNEL_ID = parseLongEnv("MOD_CHANNEL_ID");
        RULES_CHANNEL_ID = parseLongEnv("RULES_CHANNEL_ID");
        ONBOARDING_CHANNEL_ID = parseLongEnv("ONBOARDING_CHANNEL_ID");

        ONBOARDING_ROLE_ID = parseLongEnv("ONBOARDING_ROLE_ID");
        //NASA_ROLE_ID = parseLongEnv("NASA_ROLE_ID");
        //IREC_ROLE_ID = parseLongEnv("IREC_ROLE_ID");
        //LRS_ROLE_ID = parseLongEnv("LRS_ROLE_ID");
    }

    /**
     * Reads a local .env file if it exists in the root working directory.
     */
    private static void loadDotEnv() {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    dotEnvMap.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to read .env file: " + e.getMessage());
        }
    }

    private static String getEnv(String key) {
        if (dotEnvMap.containsKey(key)) {
            return dotEnvMap.get(key);
        }
        return System.getenv(key);
    }

    private static String requireEnv(String... keys) {
        for (String key : keys) {
            String val = getEnv(key);
            if (val != null && !val.isBlank()) {
                return val;
            }
        }
        System.err.println("Missing required environment variable: " + keys[0]);
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.exit(1);
        return null;
    }

    private static long parseLongEnv(String key) {
        String val = requireEnv(key);
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            System.err.println("Environment variable '" + key + "' must be a valid long ID, got: " + val);
            System.exit(1);
            return 0L;
        }
    }
}