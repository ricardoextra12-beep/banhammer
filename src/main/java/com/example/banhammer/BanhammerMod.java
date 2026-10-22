package com.example.banhammer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BanhammerMod implements ModInitializer {
	public static final String MOD_ID = "banhammer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// /adminpanel: alleen zichtbaar en bruikbaar voor operators (en dus de owner)
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("adminpanel")
						.requires(source -> source.getPlayer() != null && Ops.isOperator(source.getPlayer()))
						.executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							if (!Ops.isOperator(player)) {
								return 0;
							}
							AdminPanel.openMain(player);
							return 1;
						})));

		// Shift + rechtsklik met een hamer: volgende reden / andere stand
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getItemInHand(hand);
			boolean ban = Hammers.isHammer(held, true);
			boolean kick = Hammers.isHammer(held, false);
			boolean op = Hammers.isOpHammer(held);
			if (!ban && !kick && !op) {
				return InteractionResult.PASS;
			}
			if (!Ops.isOperator(sp)) {
				return InteractionResult.PASS;
			}

			if (op) {
				Hammers.cycleOp(held);
				sp.sendSystemMessage(Component.literal("Ophammer: " + Hammers.opModeText(held)), true);
			} else {
				Hammers.cycle(held, ban);
				sp.sendSystemMessage(Component.literal("Reden: " + Hammers.reason(held, ban)), true);
			}
			return InteractionResult.SUCCESS;
		});

		// Slaan met een hamer
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer attacker)) {
				return InteractionResult.PASS;
			}

			ItemStack held = attacker.getItemInHand(hand);
			boolean ban = Hammers.isHammer(held, true);
			boolean kick = Hammers.isHammer(held, false);
			boolean op = Hammers.isOpHammer(held);
			if (!ban && !kick && !op) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof ServerPlayer target)) {
				return InteractionResult.PASS;
			}

			if (op) {
				return useOpHammer(attacker, target, held);
			}

			String targetName = target.getGameProfile().name();

			if (Ops.isOperator(target)) {
				attacker.sendSystemMessage(Component.literal(
						"Banhammer: " + targetName + " is een operator en kan niet worden " + (ban ? "gebanned." : "gekicked.")));
				return InteractionResult.SUCCESS;
			}

			String reason = Hammers.reason(held, ban);
			MinecraftServer server = attacker.level().getServer();
			String command = ban
					? "ban " + targetName + " " + reason
					: "kick " + targetName + " " + reason;
			server.getCommands().performPrefixedCommand(attacker.createCommandSourceStack(), command);

			attacker.sendSystemMessage(Component.literal(
					"Banhammer: " + targetName + (ban ? " is gebanned" : " is gekicked") + " (" + reason + ")."));
			return InteractionResult.SUCCESS;
		});

		LOGGER.info("Banhammer, Kickhammer, Ophammer en Admin Panel (server-only) geladen!");
	}

	private static InteractionResult useOpHammer(ServerPlayer attacker, ServerPlayer target, ItemStack held) {
		String attackerName = attacker.getGameProfile().name();
		String targetName = target.getGameProfile().name();

		if (!Ops.isOperator(attacker)) {
			return InteractionResult.PASS;
		}
		// Staat er een owners-lijst, dan mogen alleen owners de Ophammer gebruiken
		if (Owners.restricted() && !Owners.isOwner(attackerName)) {
			attacker.sendSystemMessage(Component.literal("Ophammer: alleen owners mogen deze hamer gebruiken."));
			return InteractionResult.SUCCESS;
		}

		boolean targetIsOp = Ops.isOperator(target);
		String command;
		String done;

		if (Hammers.givesOp(held)) {
			if (targetIsOp) {
				attacker.sendSystemMessage(Component.literal("Ophammer: " + targetName + " is al operator."));
				return InteractionResult.SUCCESS;
			}
			command = "op " + targetName;
			done = targetName + " is nu operator.";
		} else {
			if (!targetIsOp) {
				attacker.sendSystemMessage(Component.literal("Ophammer: " + targetName + " is geen operator."));
				return InteractionResult.SUCCESS;
			}
			if (Owners.isOwner(targetName)) {
				attacker.sendSystemMessage(Component.literal("Ophammer: " + targetName + " is owner en kan niet worden gedeopt."));
				return InteractionResult.SUCCESS;
			}
			command = "deop " + targetName;
			done = targetName + " is geen operator meer.";
		}

		attacker.level().getServer().getCommands()
				.performPrefixedCommand(attacker.createCommandSourceStack(), command);
		attacker.sendSystemMessage(Component.literal("Ophammer: " + done));
		return InteractionResult.SUCCESS;
	}
}
