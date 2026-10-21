package com.example.banhammer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
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
		BANHAMMER = Registry.register(
				BuiltInRegistries.ITEM,
				BANHAMMER_KEY,
				new Item(new Item.Properties().setId(BANHAMMER_KEY).stacksTo(1).rarity(Rarity.EPIC))
		);

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

			// /ban draait namens de aanvaller: alleen operators kunnen bannen
			server.getCommands().performPrefixedCommand(
					attacker.createCommandSourceStack(),
					"ban " + targetName + " " + BAN_REASON
			);

			attacker.sendSystemMessage(Component.literal("Banhammer: " + targetName + " is gebanned."));
			return InteractionResult.SUCCESS;
		});

		LOGGER.info("Banhammer geladen!");
	}
}
