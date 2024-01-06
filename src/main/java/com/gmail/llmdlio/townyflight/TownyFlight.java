package com.gmail.llmdlio.townyflight;

import com.gmail.llmdlio.townyflight.listeners.*;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.scheduling.TaskScheduler;
import com.palmergames.bukkit.towny.scheduling.impl.BukkitTaskScheduler;
import com.palmergames.bukkit.towny.scheduling.impl.FoliaTaskScheduler;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.gmail.llmdlio.townyflight.config.Settings;
import com.gmail.llmdlio.townyflight.config.TownyFlightConfig;
import com.gmail.llmdlio.townyflight.integrations.TownyFlightPlaceholderExpansion;
import com.palmergames.bukkit.util.Version;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class TownyFlight extends JavaPlugin {
	private static final Version requiredTownyVersion = Version.fromString("0.99.0.6");
	private TownyFlightConfig config = new TownyFlightConfig(this);
	private static TownyFlight plugin;
	private static TownyFlightAPI api;
	private TownyFlightPlaceholderExpansion papiExpansion = null;
	private final TaskScheduler scheduler;
	private Map<Town, Integer> enemiesInTown = null;

	public TownyFlight() {
		plugin = this;
		this.scheduler = isFoliaClassPresent() ? new FoliaTaskScheduler(this) : new BukkitTaskScheduler(this);
	}

	public void onEnable() {
		api = new TownyFlightAPI(this);
		String townyVersion = getServer().getPluginManager().getPlugin("Towny").getDescription().getVersion();

		if (!loadSettings()) {
			getLogger().severe("Config failed to load!");
			disable();
			return;
		}

		if (!townyVersionCheck(townyVersion)) {
			getLogger().severe("Towny version does not meet required version: " + requiredTownyVersion.toString());
			disable();
			return;
		}

		checkWarPlugins();
		checkIntegrations();
		registerEvents();
		registerCommands();
		getLogger().info("Towny version " + townyVersion + " found.");
		getLogger().info(this.getDescription().getFullName() + " by LlmDl Enabled.");
	}

	public static TownyFlight getPlugin() {
		return plugin;
	}

	/**
	 * @return the API.
	 */
	public static TownyFlightAPI getAPI() {
		return api;
	}

	private void disable() {
		unregisterEvents();
		getLogger().severe("TownyFlight Disabled.");
	}

	protected boolean loadSettings() {
		return loadConfig() && Settings.loadSettings(config);
	}

	private boolean loadConfig() {
		if (!getDataFolder().exists())
			getDataFolder().mkdirs();
		return config.reload();
	}

	private boolean townyVersionCheck(String version) {
		return Version.fromString(version).compareTo(requiredTownyVersion) >= 0;
	}

	private void checkWarPlugins() {
		Settings.siegeWarFound = getServer().getPluginManager().getPlugin("SiegeWar") != null;
	}


	private void checkIntegrations() {
		Plugin test;
		test = getServer().getPluginManager().getPlugin("PlaceholderAPI");
		if (test != null) {
			papiExpansion = new TownyFlightPlaceholderExpansion(this);
			papiExpansion.register();
		}
	}

	protected void registerEvents() {
		final PluginManager pm = getServer().getPluginManager();

		pm.registerEvents(new PlayerJoinListener(this), this);
		pm.registerEvents(new PlayerLogOutListener(), this);
		pm.registerEvents(new PlayerLeaveTownListener(this), this);
		pm.registerEvents(new TownUnclaimListener(this), this);
		pm.registerEvents(new PlayerFallListener(), this);
		pm.registerEvents(new PlayerTeleportListener(), this);
		pm.registerEvents(new TownStatusScreenListener(), this);
		pm.registerEvents(new PlayerEnterTownListener(this), this);

		// TODO: Cleanup code.
		// TODO: Add all the events into one file to consolidate and make it simpler.
		if (Settings.flightDisableBy != "NONE") {
			pm.registerEvents(new EnemyEnterTownListener(this), this);
			getLogger().info("EnemyEnterTownListener registered.");
			pm.registerEvents(new EnemyLeaveTownListener(this), this);
			getLogger().info("EnemyLeaveTownListener registered.");
			pm.registerEvents(new EnemyLogOutListener(this), this);
			getLogger().info("EnemyLogOutListener registered.");
			pm.registerEvents(new EnemyTeleportListener(this), this);
			getLogger().info("EnemyTeleportListener registered.");
			enemiesInTown = new HashMap<Town, Integer>();
			getLogger().info("Enemy HashMap created.");
		}

		if (Settings.disableCombatPrevention)
			pm.registerEvents(new PlayerPVPListener(), this);
	}

	protected void unregisterEvents() {
		HandlerList.unregisterAll(this);
	}

	private void registerCommands() {
		getCommand("tfly").setExecutor(new TownyFlightCommand(this));
	}

	public TaskScheduler getScheduler() {
		return this.scheduler;
	}

	private static boolean isFoliaClassPresent() {
		try {
			Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	public void incrementEnemiesInTown(Town town) {
		if(enemiesInTown.containsKey(town)){
			enemiesInTown.put(town, enemiesInTown.get(town) + 1);
			getLogger().info("Enemies in town incremented to " + enemiesInTown.get(town) + " for town " + town.getName() + ".");
		} else {
			enemiesInTown.put(town, 1);
			getLogger().info("Enemies in town incremented to 1 for town " + town.getName() + ".");
		}
	}

	public void decrementEnemiesInTown(Town town) {
		if(enemiesInTown.containsKey(town)){
			enemiesInTown.put(town, enemiesInTown.get(town) - 1);
			getLogger().info("Enemies in town decremented to " + enemiesInTown.get(town) + " for town " + town.getName() + ".");

			// Re-add flight if there are no more enemies in town.
			if(enemiesInTown.get(town) <= 0){
				TownyFlightAPI.getInstance().addFlightToPlayersInTown(town);
				getLogger().info("Flight re-added to players in town " + town.getName() + ".");
			}
		}
		else{
			getLogger().severe("Tried to decrement enemies in town for a town that shouldn't have any enemies.");
		}
	}
	public boolean containsTown(Town town) {
		if(enemiesInTown.containsKey(town) && enemiesInTown.get(town) > 0){
			getLogger().info("Town " + town.getName() + " contains enemies.");
			return true;
		}
		else{
			return false;
		}
	}
}
