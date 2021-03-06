package com.pablo67340.guishop.listenable;

import java.util.Arrays;
import java.util.HashMap;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;

import com.pablo67340.guishop.definition.Enchantments;
import com.pablo67340.guishop.handler.Item;
import com.pablo67340.guishop.main.Main;
import com.pablo67340.guishop.util.Config;
import com.pablo67340.guishop.util.Dependencies;
import com.pablo67340.guishop.util.XMaterial;
import com.songoda.epicspawners.api.EpicSpawners;
import com.songoda.epicspawners.api.EpicSpawnersAPI;

import de.dustplanet.util.SilkUtil;

public class Quantity implements Listener {

	/**
	 * The name of the player who is buying an item.
	 */
	private String playerName;

	/**
	 * The player who is buying an item.
	 */
	private Player player;

	/**
	 * The item currently being targetted.
	 */
	private Item item;

	/**
	 * The GUI that is projected onto the screen when a {@link Player} opens the
	 * {@link Menu}.
	 */
	private Inventory GUI;

	/**
	 * The map containing the sell increments.
	 */
	private Map<Integer, Integer> qty = new HashMap<>();

	private Shop currentShop;

	public Quantity(String player, Item item, Shop shop) {
		this.item = item;
		this.playerName = player;
		this.player = Bukkit.getPlayer(player);
		this.currentShop = shop;
	}

	/**
	 * Opens the GUI to sell the items in.
	 */
	public void open() {
		GUI = Bukkit.getServer().createInventory(null, 9 * 6, Config.getQtyTitle());
		packInventory();

		player.openInventory(GUI);

	}

	/**
	 * Preloads the inventory to display items.
	 */
	private void packInventory() {
		Integer multiplier = 1;
		for (int x = 19; x <= 25; x++) {
			ItemStack itemStack = new ItemStack(XMaterial.valueOf(item.getMaterial()).parseMaterial(), multiplier);
			ItemMeta itemMeta = itemStack.getItemMeta();
			if (item.getBuyPrice() != 0 && item.getSellPrice() != 0) {

				itemMeta.setLore(Arrays.asList(
						ChatColor.translateAlternateColorCodes('&',
								"&fBuy: &c" + Config.getCurrency() + item.getBuyPrice() * multiplier),
						ChatColor.translateAlternateColorCodes('&',
								"&fSell: &a" + Config.getCurrency() + item.getSellPrice() * multiplier)));
			} else if (item.getBuyPrice() == 0) {
				itemMeta.setLore(Arrays.asList(ChatColor.translateAlternateColorCodes('&', "&cCannot be purchased"),
						ChatColor.translateAlternateColorCodes('&',
								"&fSell: &a" + Config.getCurrency() + item.getSellPrice() * multiplier)));
			} else {
				itemMeta.setLore(Arrays.asList(
						ChatColor.translateAlternateColorCodes('&',
								"&fBuy: &c" + Config.getCurrency() + item.getBuyPrice() * multiplier),
						ChatColor.translateAlternateColorCodes('&', "&cCannot be sold")));
			}
			String type = itemStack.getType().toString();
			if ((type.contains("CHESTPLATE") || type.contains("LEGGINGS") || type.contains("BOOTS")
					|| type.contains("HELMET")) && x >= 20) {
				break;
			}
			itemStack.setItemMeta(itemMeta);
			GUI.setItem(x, itemStack);
			qty.put(x, multiplier);
			multiplier *= 2;
		}

		if (!Config.getEscapeOnly()) {
			String backButtonid = Main.INSTANCE.getConfig().getString("back-button-item");

			

			ItemStack backButtonItem = new ItemStack(Material.getMaterial(backButtonid));

			ItemMeta backButtonMeta = backButtonItem.getItemMeta();

			backButtonMeta.setDisplayName(
					ChatColor.translateAlternateColorCodes('&', Main.INSTANCE.getConfig().getString("back")));

			backButtonItem.setItemMeta(backButtonMeta);

			GUI.setItem(GUI.getSize() - 1, backButtonItem);

		}
	}

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.HIGH)
	public void onQuantityClick(InventoryClickEvent e) {
		if (e.getWhoClicked().getName().equalsIgnoreCase(this.playerName)) {
			if (Main.HAS_QTY_OPEN.contains(playerName)) {
				e.setCancelled(true);
				if (!Config.getEscapeOnly()) {
					if (e.getSlot() == (GUI.getSize() - 1)) {
						Main.HAS_QTY_OPEN.remove(playerName);
						HandlerList.unregisterAll(this);
						reOpen();
						return;
					}
				}

				if (e.getClickedInventory() == null) {
					return;
				}

				if (player.getInventory().firstEmpty() == -1) {
					player.sendMessage(Config.getFull());
					return;
				}
				/*
				 * If the player clicks on an empty slot, then cancel the event.
				 */
				if (e.getCurrentItem() != null) {
					if (e.getCurrentItem().getType() == Material.AIR) {
						return;
					}
				}

				/*
				 * If the player clicks in their own inventory, we want to cancel the event.
				 */
				if (e.getClickedInventory() == player.getInventory()) {
					return;
				}

				// Check if the item is disabled, or price is 0
				if (item.getBuyPrice() == 0) {
					player.sendMessage(Config.getPrefix() + " " + Config.getCantBuy());
					player.setItemOnCursor(new ItemStack(Material.AIR));
					return;
				}

				// Does the quantity work out?
				int quantity = qty.get(e.getSlot());

				// If the quantity is 0
				if (quantity == 0) {
					player.sendMessage(Config.getPrefix() + " " + Config.getNotEnoughPre() + item.getBuyPrice()
							+ Config.getNotEnoughPost());
					player.setItemOnCursor(new ItemStack(Material.AIR));
					return;
				}

				Map<Integer, ItemStack> returnedItems = new HashMap<>();

				ItemStack itemStack = null;

				// If the item is not a mob spawner
				if (!item.isMobSpawner()) {

					itemStack = new ItemStack(XMaterial.valueOf(item.getMaterial()).parseMaterial(), quantity);
					// If the item has enchantments
					if (item.getEnchantments() != null) {
						for (String enc : item.getEnchantments()) {
							String enchantment = StringUtils.substringBefore(enc, ":");
							String level = StringUtils.substringAfter(enc, ":");
							itemStack.addUnsafeEnchantment(Enchantments.getByName(enchantment), Integer.parseInt(level));
						}
					}
					itemStack.setAmount(e.getCurrentItem().getAmount());
					// If is shift clicking, buy 1.

				} else {
					itemStack = new ItemStack(XMaterial.valueOf(item.getMaterial()).parseMaterial(), quantity);
					if (Main.getInstance().usesSpawners()) {
						if (Dependencies.hasDependency("SilkSpawners")) {
							SilkUtil su = (SilkUtil) Main.getInstance().getSpawnerObject();
							String oldName = item.getMobType();
							String spawnerName = oldName.substring(0, 1).toUpperCase()
									+ oldName.substring(1).toLowerCase() + " Spawner";
							itemStack = su.setSpawnerType(itemStack, Short.parseShort(item.getMobType().toLowerCase()), spawnerName);
						} else if (Dependencies.hasDependency("EpicSpawners")) {
							EpicSpawners es = (EpicSpawners) Main.getInstance().getSpawnerObject();
							itemStack = es.newSpawnerItem(EpicSpawnersAPI.getSpawnerManager()
									.getSpawnerData(item.getMobType()), quantity);

						}

					} else {
						player.sendMessage("Spawners Disabled! Dependencies not installed!");
						return;
					}
				}

				double priceToPay = 0;

				/*
				 * If the map is empty, then the items purchased don't overflow the player's
				 * inventory. Otherwise, we need to reimburse the player (subtract it from
				 * priceToPay).
				 */

				double priceToReimburse = 0D;

				// if the item is not a shift click

				while (returnedItems.values().iterator().hasNext()) {
					priceToReimburse += (item.getBuyPrice() / e.getCurrentItem().getAmount());
				}
				priceToPay = (item.getBuyPrice() * e.getCurrentItem().getAmount()) - priceToReimburse;

				// Check if the transition was successful

				if (Main.getEconomy().withdrawPlayer(player, priceToPay).transactionSuccess()) {
					// If the player has the sound enabled, play
					// it!
					if (Config.isSoundEnabled()) {
						try {
							player.playSound(player.getLocation(), Sound.valueOf(Config.getSound()), 1, 1);

						} catch (Exception ex) {
							Main.getInstance().getLogger().warning(
									"Incorrect sound specified in config. Make sure you are using sounds from the right version of your server!");
						}
					}
					player.sendMessage(Config.getPrefix() + Config.getPurchased() + priceToPay + Config.getTaken()
							+ Config.getCurrencySuffix());
					returnedItems = player.getInventory().addItem(itemStack);
				} else {
					player.sendMessage(
							Config.getPrefix() + Config.getNotEnoughPre() + priceToPay + Config.getNotEnoughPost());
				}

			}
		}
	}

	/**
	 * Preloads the shops before opening them.
	 */
	public void reOpen() {
		player.closeInventory();
		BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
		scheduler.scheduleSyncDelayedTask(Main.getInstance(), new Runnable() {
			@Override
			public void run() {
				Bukkit.getServer().getPluginManager().registerEvents(currentShop, Main.getInstance());
				currentShop.open();
			}
		}, 1L);

	}

	/**
	 * The inventory close listener for the quantity inventory.
	 */
	@EventHandler
	public void onClose(InventoryCloseEvent e) {
		if (e.getPlayer().getName().equalsIgnoreCase(this.playerName)) {
			if (Main.HAS_QTY_OPEN.contains(playerName)) {
				if (Config.getEscapeOnly()) {
					Main.HAS_QTY_OPEN.remove(playerName);
					HandlerList.unregisterAll(this);
					reOpen();
				} else {
					Main.HAS_QTY_OPEN.remove(playerName);
					HandlerList.unregisterAll(this);
				}
			}
		}

	}

}
