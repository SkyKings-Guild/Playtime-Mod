package plun1331.skykings_playtime.client;

import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ConfigManager {
    private static final long API_KEY_VALIDATION_DEBOUNCE_MS = 350;
    private static final ScheduledExecutorService API_KEY_VALIDATION_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "skykings-playtime-api-key-validation");
                thread.setDaemon(true);
                return thread;
            });
    private static final AtomicLong apiKeyValidationVersion = new AtomicLong();
    private static volatile boolean apiKeyInvalid;
    private static ScheduledFuture<?> pendingApiKeyValidation;

    public static ConfigClassHandler<ConfigManager> HANDLER = ConfigClassHandler.createBuilder(ConfigManager.class)
            .id(Identifier.fromNamespaceAndPath("skykings_playtime", "skykings_playtime_config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("playtime.json5"))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting)
                    .setJson5(true)
                    .build())
            .build();

    private static final String defaultBaseUrl = "https://playtime.plunthe.dev";

    @SerialEntry
    public static String apiKey = "";

    @SerialEntry
    public static String baseUrl = defaultBaseUrl;

    public static Screen showConfigScreen(Screen parentScreen) {
        if (apiKey == null) {
            apiKey = "";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = defaultBaseUrl;
        }

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("SkyKings Playtime"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("SkyKings Playtime Settings"))
                        .group(OptionGroup.createBuilder()
                                .name(Component.literal("API Settings"))
                                .option(Option.<String>createBuilder()
                                        .name(Component.literal("API Key"))
                                        .description(OptionDescription.of(Component.literal("Your API key for publishing playtime!")))
                                        .binding("", () -> apiKey == null ? "" : apiKey, newVal -> {
                                            apiKey = newVal;
                                            HANDLER.save();
                                        })
                                        .controller(StringControllerBuilder::create)
                                        .build()
                                )
                                .option(Option.<String>createBuilder()
                                        .name(Component.literal("Base API URL"))
                                        .description(OptionDescription.of(Component.literal("Don't change this unless you know what you're doing!")))
                                        .binding(defaultBaseUrl, () -> baseUrl == null ? defaultBaseUrl : baseUrl, newVal -> {
                                            baseUrl = newVal;
                                            HANDLER.save();
                                        })
                                        .controller(StringControllerBuilder::create)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .save(() -> {
                    // validate API key
                    boolean isKeyValid = PlaytimeAPI.validateAPIKey(apiKey);
                    if (!isKeyValid) {
                        apiKey = "";
                        Minecraft client = Minecraft.getInstance();
                        client.execute(() -> {
                            assert client.player != null;
                            client.player.sendSystemMessage(Component.empty());
                            client.player.sendSystemMessage(
                                    Component.empty()
                                            .append(Component.literal("[SkyKings Playtime] ")
                                                    .withStyle(ChatFormatting.AQUA))

                                            .append(Component.literal("Your API key for playtime tracking is invalid! Use ")
                                                    .withStyle(ChatFormatting.RED)
                                            )
                                            .append(Component.literal("/skykings-playtime")
                                                    .withStyle(style -> style
                                                            .withColor(ChatFormatting.GOLD)
                                                            .withBold(true)
                                                            .withClickEvent(new ClickEvent.RunCommand(
                                                                    "/skykings-playtime"
                                                            ))
                                                            .withHoverEvent(new HoverEvent.ShowText(
                                                                    Component.literal("Click to set your API key")
                                                            ))
                                                    )
                                            )
                                            .append(Component.literal(" to change it.")
                                                    .withStyle(ChatFormatting.RED)
                                            )
                            );
                            client.player.sendSystemMessage(Component.empty());
                        });
                    }
                    HANDLER.save();
                })
                .build()
                .generateScreen(parentScreen);
    }

    public static boolean setApiKey(String newKey) {
        boolean isValid = PlaytimeAPI.validateAPIKey(newKey);
        if (!isValid) {
            apiKeyInvalid = true;
            return false;
        }
        apiKey = newKey;
        apiKeyInvalid = false;
        HANDLER.save();
        return true;
    }
}
