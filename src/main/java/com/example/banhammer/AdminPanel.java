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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side kistmenu (werkt voor gewone clients zonder mod). Alleen operators kunnen het gebruiken. */
public final class AdminPanel {
	private static final int PLAYER_SLOTS = 45;

	private AdminPanel() {
	}

	/** Een knop in het menu. run() geeft true terug als het menu is gesloten of vervangen. */
	private interface Action {
		boolean run(ServerPlayer clicker);
	}

	private record Button(ItemStack stack, Action action) {
	}

	// ---------- Hulpfuncties ----------

	private static ItemStack named(Item item, String name, ChatFormatting color) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(name).withStyle(style -> style.withColor(color).withItalic(false)));
		return stack;
	}

	private static void run(ServerPlayer clicker, String command) {
		MinecraftServer server = clicker.level().getServer();
		// Draait namens de klikker: het vanilla-commando controleert zelf de rechten
		server.getCommands().performPrefixedCommand(clicker.createCommandSourceStack(), command);
	}

	/** Knop die een commando uitvoert en het menu open laat. */
	private static Button cmd(Item item, String name, ChatFormatting color, String command) {
		return new Button(named(item, name, color), clicker -> {
			run(clicker, command);
			return false;
		});
	}

	private static Button close() {
		return new Button(named(Items.BARRIER, "Sluiten", ChatFormatting.RED), clicker -> {
			clicker.closeContainer();
			return true;
		});
	}

	private static Button toMain() {
		return new Button(named(Items.ARROW, "Terug", ChatFormatting.GRAY), clicker -> {
			openMain(clicker);
			return true;
		});
	}

	private static Button toPlayers() {
		return new Button(named(Items.ARROW, "Terug", ChatFormatting.GRAY), clicker -> {
			openPlayers(clicker);
			return true;
		});
	}

	private static Button giveButton(boolean ban) {
		ItemStack stack = ban ? Hammers.banhammer() : Hammers.kickhammer();
		return new Button(stack, clicker -> {
			ItemStack hammer = ban ? Hammers.banhammer() : Hammers.kickhammer();
			if (!clicker.getInventory().add(hammer)) {
				clicker.sendSystemMessage(Component.literal("Admin Panel: je inventaris is vol."));
			}
			return false;
		});
	}

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
		run(clicker, ban
				? "ban " + name + " Gebanned via het Admin Panel"
				: "kick " + name + " Gekicked via het Admin Panel");
		clicker.sendSystemMessage(Component.literal("Admin Panel: " + name + (ban ? " is gebanned." : " is gekicked.")));
	}

	// ---------- Menu's ----------

	public static void openMain(ServerPlayer viewer) {
		Map<Integer, Button> b = new HashMap<>();
		b.put(10, new Button(named(Items.PLAYER_HEAD, "Spelers", ChatFormatting.AQUA), p -> {
			openPlayers(p);
			return true;
		}));
		b.put(12, new Button(named(Items.CLOCK, "Wereld", ChatFormatting.YELLOW), p -> {
			openWorld(p);
			return true;
		}));
		b.put(14, new Button(named(Items.GOLDEN_APPLE, "Jezelf", ChatFormatting.GREEN), p -> {
			openSelf(p);
			return true;
		}));
		b.put(16, new Button(named(Items.MACE, "Hamers", ChatFormatting.RED), p -> {
			openHammers(p);
			return true;
		}));
		b.put(22, close());
		open(viewer, "Admin Panel", 3, b);
	}

	public static void openPlayers(ServerPlayer viewer) {
		Map<Integer, Button> b = new HashMap<>();
		int slot = 0;
		for (ServerPlayer target : viewer.level().getServer().getPlayerList().getPlayers()) {
			if (slot >= PLAYER_SLOTS) {
				break;
			}
			if (Ops.isOperator(target)) {
				continue; // operators zijn onaantastbaar en staan niet in de lijst
			}
			UUID id = target.getUUID();
			b.put(slot, new Button(named(Items.NAME_TAG, target.getGameProfile().name(), ChatFormatting.WHITE), p -> {
				openTarget(p, id);
				return true;
			}));
			slot++;
		}
		b.put(49, toMain());
		open(viewer, "Spelers", 6, b);
	}

	public static void openTarget(ServerPlayer viewer, UUID targetId) {
		ServerPlayer target = viewer.level().getServer().getPlayerList().getPlayer(targetId);
		if (target == null) {
			openPlayers(viewer);
			return;
		}
		String name = target.getGameProfile().name();
		String me = viewer.getGameProfile().name();

		Map<Integer, Button> b = new HashMap<>();
		b.put(4, new Button(named(Items.NAME_TAG, name, ChatFormatting.WHITE), p -> false));

		b.put(10, new Button(named(Items.IRON_BOOTS, "Kick " + name, ChatFormatting.GOLD), p -> {
			punish(p, targetId, false);
			p.closeContainer();
			return true;
		}));
		b.put(11, new Button(named(Items.TNT, "Ban " + name, ChatFormatting.RED), p -> {
			punish(p, targetId, true);
			p.closeContainer();
			return true;
		}));
		b.put(12, cmd(Items.ENDER_PEARL, "Teleporteer naar " + name, ChatFormatting.LIGHT_PURPLE, "tp " + me + " " + name));
		b.put(13, cmd(Items.ENDER_EYE, "Haal " + name + " naar jou", ChatFormatting.DARK_PURPLE, "tp " + name + " " + me));
		b.put(14, cmd(Items.GLISTERING_MELON_SLICE, "Genees " + name, ChatFormatting.RED,
				"effect give " + name + " minecraft:instant_health 1 10 true"));
		b.put(15, cmd(Items.COOKED_BEEF, "Voed " + name, ChatFormatting.GOLD,
				"effect give " + name + " minecraft:saturation 1 20 true"));

		b.put(19, cmd(Items.IRON_SWORD, "Survival voor " + name, ChatFormatting.GRAY, "gamemode survival " + name));
		b.put(20, cmd(Items.GRASS_BLOCK, "Creative voor " + name, ChatFormatting.GREEN, "gamemode creative " + name));
		b.put(21, cmd(Items.GLASS, "Spectator voor " + name, ChatFormatting.AQUA, "gamemode spectator " + name));

		b.put(26, toPlayers());
		open(viewer, "Speler: " + name, 3, b);
	}

	public static void openWorld(ServerPlayer viewer) {
		Map<Integer, Button> b = new HashMap<>();
		b.put(10, cmd(Items.SUNFLOWER, "Dag", ChatFormatting.YELLOW, "time set day"));
		b.put(11, cmd(Items.COAL, "Nacht", ChatFormatting.DARK_GRAY, "time set night"));

		b.put(13, cmd(Items.FEATHER, "Helder weer", ChatFormatting.WHITE, "weather clear"));
		b.put(14, cmd(Items.WATER_BUCKET, "Regen", ChatFormatting.BLUE, "weather rain"));
		b.put(15, cmd(Items.LIGHTNING_ROD, "Onweer", ChatFormatting.DARK_PURPLE, "weather thunder"));

		b.put(19, cmd(Items.POPPY, "Moeilijkheid: peaceful", ChatFormatting.GREEN, "difficulty peaceful"));
		b.put(20, cmd(Items.IRON_SWORD, "Moeilijkheid: normal", ChatFormatting.YELLOW, "difficulty normal"));
		b.put(21, cmd(Items.ZOMBIE_HEAD, "Moeilijkheid: hard", ChatFormatting.RED, "difficulty hard"));

		b.put(23, cmd(Items.HOPPER, "Verwijder gedropte items", ChatFormatting.GOLD, "kill @e[type=item]"));

		b.put(26, toMain());
		open(viewer, "Wereld", 3, b);
	}

	public static void openSelf(ServerPlayer viewer) {
		Map<Integer, Button> b = new HashMap<>();
		b.put(10, cmd(Items.IRON_SWORD, "Survival", ChatFormatting.GRAY, "gamemode survival"));
		b.put(11, cmd(Items.GRASS_BLOCK, "Creative", ChatFormatting.GREEN, "gamemode creative"));
		b.put(12, cmd(Items.GLASS, "Spectator", ChatFormatting.AQUA, "gamemode spectator"));

		b.put(14, cmd(Items.GLISTERING_MELON_SLICE, "Genees jezelf", ChatFormatting.RED,
				"effect give @s minecraft:instant_health 1 10 true"));
		b.put(15, cmd(Items.COOKED_BEEF, "Voed jezelf", ChatFormatting.GOLD,
				"effect give @s minecraft:saturation 1 20 true"));

		b.put(19, cmd(Items.POTION, "Onzichtbaar", ChatFormatting.LIGHT_PURPLE,
				"effect give @s minecraft:invisibility infinite 0 true"));
		b.put(20, cmd(Items.GOLDEN_CARROT, "Nachtzicht", ChatFormatting.YELLOW,
				"effect give @s minecraft:night_vision infinite 0 true"));
		b.put(21, cmd(Items.MILK_BUCKET, "Alle effecten wissen", ChatFormatting.WHITE, "effect clear @s"));

		b.put(26, toMain());
		open(viewer, "Jezelf", 3, b);
	}

	public static void openHammers(ServerPlayer viewer) {
		Map<Integer, Button> b = new HashMap<>();
		b.put(11, giveButton(true));
		b.put(15, giveButton(false));
		b.put(22, toMain());
		open(viewer, "Hamers (Shift + rechtsklik = andere reden)", 3, b);
	}

	// ---------- Menu openen en afhandelen ----------

	private static void open(ServerPlayer viewer, String title, int rows, Map<Integer, Button> buttons) {
		SimpleContainer container = new SimpleContainer(rows * 9);
		for (Map.Entry<Integer, Button> entry : buttons.entrySet()) {
			container.setItem(entry.getKey(), entry.getValue().stack().copy());
		}
		viewer.openMenu(new SimpleMenuProvider(
				(syncId, inventory, player) -> new PanelMenu(syncId, inventory, container, rows, buttons),
				Component.literal(title)));
	}

	private static MenuType<?> menuType(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	private static final class PanelMenu extends ChestMenu {
		private final int rows;
		private final Map<Integer, Button> buttons;

		PanelMenu(int syncId, Inventory inventory, Container container, int rows, Map<Integer, Button> buttons) {
			super(menuType(rows), syncId, inventory, container, rows);
			this.rows = rows;
			this.buttons = buttons;
		}

		/** Niets mag uit het menu worden gehaald: we vangen elke klik zelf af. */
		@Override
		public void clicked(int slotId, int button, ContainerInput input, Player player) {
			if (player instanceof ServerPlayer clicker && slotId >= 0 && slotId < rows * 9) {
				if (!Ops.isOperator(clicker)) {
					clicker.closeContainer();
					return;
				}
				Button clickedButton = buttons.get(slotId);
				if (clickedButton != null && clickedButton.action().run(clicker)) {
					return; // menu is gesloten of vervangen
				}
			}
			sendAllDataToRemote(); // zet het scherm van de speler weer recht
		}
	}
}
