package com.example.banhammer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-side kistmenu (werkt voor gewone clients zonder mod). Alleen operators kunnen het gebruiken. */
public final class AdminPanel {
	private static final int PLAYER_SLOTS = 45;

	private static final int SLOT_GIVE_BAN = 46;
	private static final int SLOT_CLOSE = 49;
	private static final int SLOT_GIVE_KICK = 52;

	private static final int SLOT_KICK = 11;
	private static final int SLOT_TARGET = 13;
	private static final int SLOT_BAN = 15;
	private static final int SLOT_BACK = 22;

	private AdminPanel() {
	}

	// ---------- Menu's openen ----------

	public static void openMain(ServerPlayer viewer) {
		SimpleContainer container = new SimpleContainer(54);
		List<UUID> targets = new ArrayList<>();
		for (int i = 0; i < PLAYER_SLOTS; i++) {
			targets.add(null);
		}

		int slot = 0;
		for (ServerPlayer p : viewer.level().getServer().getPlayerList().getPlayers()) {
			if (slot >= PLAYER_SLOTS) {
				break;
			}
			if (Ops.isOperator(p)) {
				continue; // operators zijn onaantastbaar en staan niet in de lijst
			}
			container.setItem(slot, named(Items.NAME_TAG, p.getGameProfile().name(), ChatFormatting.WHITE));
			targets.set(slot, p.getUUID());
			slot++;
		}

		container.setItem(SLOT_GIVE_BAN, Hammers.banhammer());
		container.setItem(SLOT_CLOSE, named(Items.BARRIER, "Sluiten", ChatFormatting.RED));
		container.setItem(SLOT_GIVE_KICK, Hammers.kickhammer());

		open(viewer, "Admin Panel", container, 6, false, targets, null);
	}

	public static void openActions(ServerPlayer viewer, ServerPlayer target) {
		SimpleContainer container = new SimpleContainer(27);
		String name = target.getGameProfile().name();

		container.setItem(SLOT_KICK, named(Items.IRON_BOOTS, "Kick " + name, ChatFormatting.GOLD));
		container.setItem(SLOT_TARGET, named(Items.NAME_TAG, name, ChatFormatting.WHITE));
		container.setItem(SLOT_BAN, named(Items.TNT, "Ban " + name, ChatFormatting.RED));
		container.setItem(SLOT_BACK, named(Items.ARROW, "Terug", ChatFormatting.GRAY));

		open(viewer, "Actie: " + name, container, 3, true, null, target.getUUID());
	}

	private static void open(ServerPlayer viewer, String title, Container container, int rows,
			boolean actionMenu, List<UUID> targets, UUID actionTarget) {
		viewer.openMenu(new SimpleMenuProvider(
				(syncId, inventory, player) -> new PanelMenu(syncId, inventory, container, rows,
						actionMenu, targets, actionTarget),
				Component.literal(title)));
	}

	private static ItemStack named(Item item, String name, ChatFormatting color) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(name).withStyle(style -> style.withColor(color).withItalic(false)));
		return stack;
	}

	// ---------- Acties ----------

	private static void punish(ServerPlayer clicker, UUID targetId, boolean ban) {
		MinecraftServer server = clicker.level().getServer();
		ServerPlayer target = server.getPlayerList().getPlayer(targetId);
		if (target == null) {
			clicker.sendSystemMessage(Component.literal("Admin Panel: die speler is niet meer online."));
			return;
		}
		String name = target.getGameProfile().name();
		if (Ops.isOperator(target)) {
			clicker.sendSystemMessage(Component.literal("Admin Panel: " + name + " is een operator en is beschermd."));
			return;
		}
		String command = ban
				? "ban " + name + " Gebanned via het Admin Panel"
				: "kick " + name + " Gekicked via het Admin Panel";
		// Draait namens de klikker: het vanilla-commando controleert zelf de rechten
		server.getCommands().performPrefixedCommand(clicker.createCommandSourceStack(), command);
		clicker.sendSystemMessage(Component.literal("Admin Panel: " + name + (ban ? " is gebanned." : " is gekicked.")));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	// ---------- Het menu zelf ----------

	private static final class PanelMenu extends ChestMenu {
		private final int rows;
		private final boolean actionMenu;
		private final List<UUID> targets;
		private final UUID actionTarget;

		PanelMenu(int syncId, Inventory inventory, Container container, int rows,
				boolean actionMenu, List<UUID> targets, UUID actionTarget) {
			super(rows == 6 ? MenuType.GENERIC_9x6 : MenuType.GENERIC_9x3, syncId, inventory, container, rows);
			this.rows = rows;
			this.actionMenu = actionMenu;
			this.targets = targets;
			this.actionTarget = actionTarget;
		}

		/** Niets mag uit het menu worden gehaald: we vangen elke klik zelf af. */
		@Override
		public void clicked(int slotId, int button, ClickType clickType, Player player) {
			if (player instanceof ServerPlayer clicker && slotId >= 0 && slotId < rows * 9) {
				if (!Ops.isOperator(clicker)) {
					clicker.closeContainer();
					return;
				}
				if (handle(clicker, slotId)) {
					return; // menu is gesloten of vervangen
				}
			}
			sendAllDataToRemote(); // zet het scherm van de speler weer recht
		}

		/** Geeft true terug als het menu is gesloten of vervangen. */
		private boolean handle(ServerPlayer clicker, int slot) {
			if (actionMenu) {
				switch (slot) {
					case SLOT_KICK -> {
						punish(clicker, actionTarget, false);
						clicker.closeContainer();
						return true;
					}
					case SLOT_BAN -> {
						punish(clicker, actionTarget, true);
						clicker.closeContainer();
						return true;
					}
					case SLOT_BACK -> {
						openMain(clicker);
						return true;
					}
					default -> {
						return false;
					}
				}
			}

			if (slot < PLAYER_SLOTS) {
				UUID id = targets.get(slot);
				if (id == null) {
					return false;
				}
				ServerPlayer target = clicker.level().getServer().getPlayerList().getPlayer(id);
				if (target == null) {
					openMain(clicker); // lijst verversen
					return true;
				}
				openActions(clicker, target);
				return true;
			}

			switch (slot) {
				case SLOT_GIVE_BAN -> {
					give(clicker, Hammers.banhammer());
					return false;
				}
				case SLOT_GIVE_KICK -> {
					give(clicker, Hammers.kickhammer());
					return false;
				}
				case SLOT_CLOSE -> {
					clicker.closeContainer();
					return true;
				}
				default -> {
					return false;
				}
			}
		}
	}
}
