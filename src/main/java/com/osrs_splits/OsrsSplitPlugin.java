package com.osrs_splits;

import com.Utils.PartySocketIOClient;
import com.Utils.PlayerVerificationStatus;
import com.google.gson.Gson;
import com.google.inject.Provides;
import com.osrs_splits.LootTracker.NexSplitTrackerPanel;
import com.osrs_splits.PartyManager.PartyManager;
import com.osrs_splits.PartyManager.PlayerInfo;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WorldChanged;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.discord.DiscordService;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import okhttp3.OkHttpClient;
import javax.inject.Inject;

@PluginDescriptor(name = "OSRS Splits - The Kodai", configName = "NexSplitTracker")
public class OsrsSplitPlugin extends Plugin
{
	@Getter
	@Inject
	private Client client;

	@Getter
	@Inject
	private DiscordService discordService;

	@Inject
	private ClientToolbar clientToolbar;

	@Getter
	@Inject
	private OsrsSplitsConfig config;

	@Inject
	private EventBus eventBus;

	@Getter
	@Inject
	private ChatMessageManager chatMessageManager;
	@Getter
	@Setter
	@Inject
	private OkHttpClient okHttpClient;


	@Getter
	private OsrsSplitPluginPanel partyManagerPanel;

	private OsrsSplitMasterPanel masterPanel;
	private NavigationButton navButton;

	@Getter
	private PartyManager partyManager;

	@Getter
	private PartySocketIOClient socketIoClient;
	@Inject
	private ItemManager itemManager;
	@Inject
	private Gson gson;
	@Getter
	private NexSplitTrackerPanel lootTrackerPanel;


	@Override
	protected void startUp() throws Exception
	{
		partyManager = new PartyManager(config, this);

		partyManagerPanel = new OsrsSplitPluginPanel(this);

		lootTrackerPanel = new NexSplitTrackerPanel(itemManager, gson);

		// Init Socket.IO
		try
		{
			String socketIoUri = "https://osrssplits.xyz";
			socketIoClient = new PartySocketIOClient(socketIoUri, this);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		eventBus.register(this);
		eventBus.register(partyManagerPanel);

		saveApiKeyToFile(config.apiKey());

		if (discordService != null)
		{
			discordService.init();
		}

		masterPanel = new OsrsSplitMasterPanel(this, partyManagerPanel, lootTrackerPanel);

		// Create panel button
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/tempIcon.png");
		navButton = NavigationButton.builder()
				.tooltip("OSRS Splits - The Kodai")
				.icon(icon)
				.priority(1)
				.panel(masterPanel)
				.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		eventBus.unregister(this);
		eventBus.unregister(partyManagerPanel);

		if (socketIoClient != null)
		{
			socketIoClient.disconnect();

		}
	}

	@Provides
	OsrsSplitsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsSplitsConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("nexsplittracker") && event.getKey().equals("saveApiKey"))
		{
			if (config.saveApiKey())
			{
				saveApiKeyToFile(config.apiKey());

			}
		}
	}


	@Subscribe
	public void onWorldChanged(WorldChanged event)
	{
		int newWorld = client.getWorld();


		if (newWorld < 1)
		{

			return;
		}


		String localPlayer = client.getLocalPlayer().getName();
		if (!partyManager.isInParty(localPlayer))
		{

			return;
		}

		// If we’re in a party => push new world to PartyManager
		partyManager.updatePlayerData(localPlayer, newWorld);

	}


	public static void saveApiKeyToFile(String apiKey)
	{
		if (apiKey == null || apiKey.isEmpty())
		{

			return;
		}

		Path runeliteDir = Paths.get(System.getProperty("user.home"), ".runelite", "osrs_splits");
		File configDir = runeliteDir.toFile();
		if (!configDir.exists())
		{
			configDir.mkdirs();
		}

		File apiKeyFile = new File(configDir, "api_key.txt");
		try (FileWriter writer = new FileWriter(apiKeyFile))
		{
			writer.write(apiKey);

		}
		catch (IOException e)
		{

		}
	}

}
