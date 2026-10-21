package com.example.banhammer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BanhammerMod implements ModInitializer {
	public static final String MOD_ID = "banhammer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final String BAN_REASON = "Gebanned met de Banhammer";

	public static final ResourceKey<Item> BANHAMMER_KEY =
			ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "banhammer"));

	public static Item BANHAMMER;

	@Override
	public void onInitialize() {
		BANHAMMER = Items.registerItem(
				BANHAMMER_KEY,
				Item::new,
				new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
		);

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(entries -> entries.accept(BANHAMMER));

		// Server-side: slaan met de hamer = ban uitvoeren
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer attacker)) {
				return InteractionResult.PASS;
			}
			if (!attacker.getItemInHand(hand).is(BANHAMMER)) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof ServerPlayer target)) {
				return InteractionResult.PASS;
			}

			MinecraftServer server = attacker.level().getServer();
			String targetName = target.getGameProfile().name();

			// De /ban-opdracht wordt namens de aanvaller uitgevoerd, dus alleen
			// spelers met ban-rechten (operators) kunnen de hamer echt gebruiken.
			server.getCommands().performPrefixedCommand(
					attacker.createCommandSourceStack(),
					"ban " + targetName + " " + BAN_REASON
			);

			if (!target.isAlive() || target.hasDisconnected()) {
				attacker.sendSystemMessage(Component.literal("Banhammer: " + targetName + " is gebanned."));
			}

			return InteractionResult.SUCCESS; // geen schade, alleen de ban
		});

		LOGGER.info("Banhammer geladen!");
	}
}
