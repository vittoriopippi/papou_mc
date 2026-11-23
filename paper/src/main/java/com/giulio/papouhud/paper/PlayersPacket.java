package com.giulio.papouhud.paper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Il pacchetto {@code papouhud:players}, scritto byte per byte come lo legge la mod
 * ({@code PlayersPayload} in src/main): se cambi il formato, cambialo in tutti e due e aumenta
 * {@link #VERSION}.
 */
final class PlayersPacket {
	static final String CHANNEL = "papouhud:players";
	static final int VERSION = 1;

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	record Armor(String itemId, int damage, int maxDamage, boolean glint, int dyeColor) {
		static final Armor EMPTY = new Armor("", 0, 0, false, -1);

		static Armor of(ItemStack stack) {
			if (stack == null || stack.isEmpty()) {
				return EMPTY;
			}
			// Come ItemStack#isDamageableItem del gioco: ha durabilita' massima, danno, e non e' indistruttibile.
			Integer maxDamage = stack.getData(DataComponentTypes.MAX_DAMAGE);
			Integer damage = stack.getData(DataComponentTypes.DAMAGE);
			boolean damageable = maxDamage != null && damage != null && !stack.hasData(DataComponentTypes.UNBREAKABLE);
			// Come ItemStack#hasFoil: l'override se c'e', altrimenti "e' incantato".
			Boolean glintOverride = stack.getData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
			boolean glint = glintOverride != null ? glintOverride : !stack.getEnchantments().isEmpty();
			DyedItemColor dye = stack.getData(DataComponentTypes.DYED_COLOR);
			return new Armor(stack.getType().getKey().toString(),
					damageable ? damage : 0, damageable ? maxDamage : 0, glint,
					dye != null ? dye.color().asRGB() : -1);
		}
	}

	record Entry(UUID uuid, String name, String dimension, double x, double y, double z,
			float health, float maxHealth, int armorValue, List<Armor> armor) {
		static Entry of(Player player) {
			List<Armor> armor = new ArrayList<>(ARMOR_SLOTS.length);
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				armor.add(Armor.of(player.getInventory().getItem(slot)));
			}
			Location location = player.getLocation();
			return new Entry(player.getUniqueId(), player.getName(), player.getWorld().getKey().toString(),
					location.getX(), location.getY(), location.getZ(),
					(float) player.getHealth(), (float) attribute(player, Attribute.MAX_HEALTH, 20.0),
					(int) Math.floor(attribute(player, Attribute.ARMOR, 0.0)), armor);
		}

		private static double attribute(Player player, Attribute attribute, double fallback) {
			AttributeInstance instance = player.getAttribute(attribute);
			return instance != null ? instance.getValue() : fallback;
		}
	}

	private PlayersPacket() {
	}

	static byte[] write(List<Entry> players) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 + players.size() * 160);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			writeVarInt(out, VERSION);
			writeVarInt(out, players.size());
			for (Entry entry : players) {
				out.writeLong(entry.uuid().getMostSignificantBits());
				out.writeLong(entry.uuid().getLeastSignificantBits());
				writeString(out, entry.name());
				writeString(out, entry.dimension());
				out.writeDouble(entry.x());
				out.writeDouble(entry.y());
				out.writeDouble(entry.z());
				out.writeFloat(entry.health());
				out.writeFloat(entry.maxHealth());
				writeVarInt(out, entry.armorValue());
				for (int i = 0; i < 4; i++) {
					Armor piece = i < entry.armor().size() ? entry.armor().get(i) : Armor.EMPTY;
					writeString(out, piece.itemId());
					writeVarInt(out, piece.damage());
					writeVarInt(out, piece.maxDamage());
					out.writeBoolean(piece.glint());
					out.writeInt(piece.dyeColor());
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	/** VarInt di Minecraft: 7 bit per byte, il bit alto dice se ne segue un altro. */
	static void writeVarInt(DataOutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	/** Stringa di Minecraft: lunghezza in byte (VarInt) e poi UTF-8. */
	static void writeString(DataOutputStream out, String value) throws IOException {
		byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
		writeVarInt(out, utf8.length);
		out.write(utf8);
	}
}
