package com.example.banhammer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** Controleert via ops.json of een speler operator is (de owner staat daar ook in). */
public final class Ops {
	private static final Path OPS_FILE = Path.of("ops.json");

	private Ops() {
	}

	/** Bij een leesfout geeft dit true terug: bij twijfel is een speler beschermd. */
	public static boolean isOperator(ServerPlayer player) {
		String uuid = player.getUUID().toString();
		String name = player.getGameProfile().name();
		try (Reader reader = Files.newBufferedReader(OPS_FILE)) {
			JsonArray ops = JsonParser.parseReader(reader).getAsJsonArray();
			for (JsonElement element : ops) {
				JsonObject op = element.getAsJsonObject();
				if (op.has("uuid") && op.get("uuid").getAsString().equalsIgnoreCase(uuid)) {
					return true;
				}
				if (op.has("name") && op.get("name").getAsString().equalsIgnoreCase(name)) {
					return true;
				}
			}
			return false;
		} catch (NoSuchFileException e) {
			return false;
		} catch (Exception e) {
			BanhammerMod.LOGGER.warn("Kon ops.json niet lezen, speler wordt voor de zekerheid beschermd", e);
			return true;
		}
	}
}
