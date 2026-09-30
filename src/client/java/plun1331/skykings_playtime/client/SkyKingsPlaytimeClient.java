package plun1331.skykings_playtime.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.hypixel.data.type.GameType;
import net.hypixel.data.type.ServerType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SkyKingsPlaytimeClient implements ClientModInitializer {
	public static final String MOD_ID = "skykings-playtime";
	private static final int PUBLISH_INTERVAL_TICKS = 5 * 60 * 20; // 5 minutes
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private ServerType currentType;
	private String currentMap;
	private PlaytimeDatabase database;

	private boolean hasWarned = false;
	private int publishTicks = PUBLISH_INTERVAL_TICKS;
	private final AtomicBoolean publishInFlight = new AtomicBoolean(false);


	private final ServerType[] supportedServerTypes = new ServerType[] {
			GameType.SKYBLOCK,
	};

	@Override
	public void onInitializeClient() {

        try {
			database = new PlaytimeDatabase("jdbc:sqlite:playtime.sqlite");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

		ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
			onDisconnect();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> cleanup());
		ClientTickEvents.END_CLIENT_TICK.register(_ -> publishPlaytimeIfDue());
		Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));

		HypixelModAPI.getInstance().subscribeToEventPacket(ClientboundLocationPacket.class);
		HypixelModAPI.getInstance().createHandler(ClientboundLocationPacket.class, packet -> {
			if (packet.getServerType().isEmpty()) {
				LOGGER.info("Received ClientboundLocationPacket with empty server type, ending playtime tracking");
				onDisconnect();
				return;
			}
			ServerType serverType = packet.getServerType().get();
			if (Arrays.stream(supportedServerTypes).noneMatch(type -> type.equals(serverType))) {
				LOGGER.info("Received ClientboundLocationPacket with unsupported server type {}, ending playtime tracking", serverType);
				onDisconnect();
				return; // Unsupported
			}

			String map = packet.getMap().orElse("Unknown");
			LOGGER.info("Received ClientboundLocationPacket with server type {} and map {}", serverType, map);

			if (currentType != serverType || !currentMap.equals(map)) {
				PlaytimeRecord currentPlaytime;
                try {
                    currentPlaytime = database.getCurrentPlaytime().orElse(null);
                } catch (SQLException e) {
					currentPlaytime = null;
                }
				LOGGER.info("Current playtime: {}", currentPlaytime);
                if (currentType != null) {
					// End previous playtime
					try {
						database.endPlaytime();
					} catch (SQLException e) {
						LOGGER.error("Error ending playtime", e);
						throw new RuntimeException(e);
					}
				} else if (currentPlaytime != null) {
					// End previous playtime (volatile)
					try {
						database.deleteCurrentPlaytime();
					} catch (SQLException e) {
						LOGGER.error("Error deleting current playtime", e);
						throw new RuntimeException(e);
					}
				}

				currentType = serverType;
				currentMap = map;

				// Start new playtime
				try {
					database.startPlaytime(serverType, map);
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}

            try {
				if (!hasWarned) {
					if (database.getSetting("api_key").isEmpty()) {
						LOGGER.warn("API key not set! Alerting user in chat.");
						Minecraft client = Minecraft.getInstance();
						client.execute(() -> {
							assert client.player != null;
							client.player.sendSystemMessage(Component.empty());
							client.player.sendSystemMessage(
									Component.empty()
											.append(Component.literal("[SkyKings Playtime] ")
													.withStyle(ChatFormatting.AQUA))
											.append(Component.literal("You have not set your API key for playtime tracking! Use ")
													.withStyle(ChatFormatting.RED)
											)
											.append(Component.literal("/set-playtime-key <key>")
													.withStyle(style -> style
															.withColor(ChatFormatting.GOLD)
															.withBold(true)
															.withClickEvent(new ClickEvent.SuggestCommand(
																	"/set-playtime-key "
															))
															.withHoverEvent(new HoverEvent.ShowText(
																	Component.literal("Click to set your API key")
															))
													)
											)
											.append(Component.literal(" to set it.")
													.withStyle(ChatFormatting.RED)
											)

							);
							client.player.sendSystemMessage(Component.empty());
						});
					} else {
						String apiKey = database.getSetting("api_key").get();
						boolean isValid = PlaytimeAPI.validateAPIKey(apiKey);
						if (!isValid) {
							LOGGER.warn("API key is invalid! Alerting user in chat.");
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
												.append(Component.literal("/set-playtime-key <key>")
														.withStyle(style -> style
																.withColor(ChatFormatting.GOLD)
																.withBold(true)
																.withClickEvent(new ClickEvent.SuggestCommand(
																		"/set-playtime-key "
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
						} else {
							LOGGER.info("API key is valid.");
						}
					}
					hasWarned = true;
				}
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> {
			dispatcher.register(
					ClientCommands.literal("set-playtime-key")
							.then(ClientCommands.argument("key", StringArgumentType.string())
									.executes(context -> {
										String key = StringArgumentType.getString(context, "key");
										boolean isValid = PlaytimeAPI.validateAPIKey(key);
										if (!isValid) {
											context.getSource().sendFeedback(
													Component.empty()
															.append(Component.literal("[SkyKings Playtime] ")
																	.withStyle(ChatFormatting.AQUA))

															.append(Component.literal("The API key you provided is invalid!")
																	.withStyle(ChatFormatting.RED)
															)
											);
											return 0;
										}
                                        try {
                                            database.setSetting("api_key", key);
                                        } catch (SQLException e) {
                                            throw new RuntimeException(e);
                                        }
                                        context.getSource().sendFeedback(
												Component.empty()
														.append(Component.literal("[SkyKings Playtime] ")
																.withStyle(ChatFormatting.AQUA))

														.append(Component.literal("Your API key has been set!")
																.withStyle(ChatFormatting.GREEN)
														)
										);
										return 1;
									}))
			);
		});

		LOGGER.info("Client initialized!");
	}

	private void publishPlaytimeIfDue() {
		if (--publishTicks > 0 || !publishInFlight.compareAndSet(false, true)) {
			return;
		}
		publishTicks = PUBLISH_INTERVAL_TICKS;

		try {
			String apiKey = database.getSetting("api_key").orElse(null);
			LOGGER.info("Publishing playtime records");
			if (apiKey == null) {
				publishInFlight.set(false);
				return;
			}
			database.splitCurrentPlaytime(currentType, currentMap);
			LinkedList<PlaytimeRecord> records = database.getUnpublishedPlaytimeRecords();
			if (records.isEmpty()) {
				publishInFlight.set(false);
				return;
			}

			PlaytimeAPI.publishPlaytimeRecords(apiKey, records).whenComplete((published, error) ->
					Minecraft.getInstance().execute(() -> {
						try {
							if (error != null) {
								LOGGER.warn("Could not publish playtime records", error);
							} else if (published) {
								database.markPlaytimePublished(records);
								LOGGER.info("Published {} playtime record(s)", records.size());
							} else {
								LOGGER.warn("Playtime server rejected the records");
							}
						} catch (SQLException e) {
							LOGGER.error("Could not mark playtime records as published", e);
						} finally {
							publishInFlight.set(false);
						}
					}));
		} catch (SQLException e) {
			publishInFlight.set(false);
			LOGGER.error("Could not read playtime records for publishing", e);
		}
	}

	public void publishPlaytimeNow() {
		publishTicks = 0;
		publishPlaytimeIfDue();
	}

	public void onDisconnect() {
		if (currentType != null) {
			// End previous playtime
			try {
				database.endPlaytime();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
		currentType = null;
		currentMap = null;
		publishPlaytimeNow();
	}

	public void cleanup() {
		onDisconnect();
		try {
			database.close();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
}