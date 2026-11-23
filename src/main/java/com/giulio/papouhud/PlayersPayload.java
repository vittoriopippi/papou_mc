package com.giulio.papouhud;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Server -> client: tutti gli altri giocatori online (nome, dove sono, vita, armatura), cosi' i
 * pannelli funzionano a qualsiasi distanza e in qualsiasi dimensione. Inviato due volte al secondo,
 * solo a chi ha Papou HUD.
 *
 * <p>Il plugin Paper ({@code paper/}, server Minecraft Cross) scrive esattamente gli stessi byte:
 * se cambi il formato, cambia anche {@code PlayersPacket} li' e aumenta {@link #VERSION}.
 *
 * <pre>
 * VarInt versione (1), VarInt numero di giocatori, poi per ognuno:
 *   UUID (2 long), String nome, String dimensione ("minecraft:the_nether"),
 *   double x, y, z, float vita, float vita massima, VarInt punti armatura,
 *   4 pezzi (testa, petto, gambe, piedi): String id oggetto ("" = niente), VarInt danno,
 *   VarInt danno massimo (0 = non si consuma), boolean luccichio, int colore tinta (-1 = nessuno)
 * </pre>
 * Stringhe e VarInt come nel resto del protocollo di Minecraft; numeri big-endian.
 */
record PlayersPayload(int version, List<Entry> players) implements CustomPacketPayload {
	static final Type<PlayersPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("papouhud", "players"));
	static final int VERSION = 1;
	static final StreamCodec<FriendlyByteBuf, PlayersPayload> CODEC = StreamCodec.of(
			(buf, payload) -> payload.write(buf), PlayersPayload::read);

	private static final int MAX_PLAYERS = 500;
	private static final int MAX_STRING = 256;

	PlayersPayload(List<Entry> players) {
		this(VERSION, players);
	}

	/** Un pezzo di armatura: solo quello che serve per disegnarne l'icona e la barra di durabilita'. */
	record Armor(String itemId, int damage, int maxDamage, boolean glint, int dyeColor) {
		static final Armor EMPTY = new Armor("", 0, 0, false, -1);

		static Armor of(ItemStack stack) {
			if (stack.isEmpty()) {
				return EMPTY;
			}
			boolean damageable = stack.isDamageableItem();
			DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
			return new Armor(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
					damageable ? stack.getDamageValue() : 0, damageable ? stack.getMaxDamage() : 0,
					stack.hasFoil(), dye != null ? dye.rgb() : -1);
		}

		ItemStack toStack() {
			Identifier id = itemId.isEmpty() ? null : Identifier.tryParse(itemId);
			Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : Items.AIR;
			if (item == Items.AIR) {
				return ItemStack.EMPTY;
			}
			ItemStack stack = new ItemStack(item);
			if (maxDamage > 0) {
				stack.set(DataComponents.MAX_DAMAGE, maxDamage);
				stack.setDamageValue(Math.clamp(damage, 0, maxDamage));
			}
			if (glint) {
				stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			if (dyeColor != -1) {
				stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeColor));
			}
			return stack;
		}
	}

	record Entry(UUID uuid, String name, String dimension, double x, double y, double z,
			float health, float maxHealth, int armorValue, List<Armor> armor) {
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(version);
		buf.writeVarInt(players.size());
		for (Entry entry : players) {
			buf.writeUUID(entry.uuid());
			buf.writeUtf(entry.name(), MAX_STRING);
			buf.writeUtf(entry.dimension(), MAX_STRING);
			buf.writeDouble(entry.x());
			buf.writeDouble(entry.y());
			buf.writeDouble(entry.z());
			buf.writeFloat(entry.health());
			buf.writeFloat(entry.maxHealth());
			buf.writeVarInt(entry.armorValue());
			for (int i = 0; i < 4; i++) {
				Armor piece = i < entry.armor().size() ? entry.armor().get(i) : Armor.EMPTY;
				buf.writeUtf(piece.itemId(), MAX_STRING);
				buf.writeVarInt(piece.damage());
				buf.writeVarInt(piece.maxDamage());
				buf.writeBoolean(piece.glint());
				buf.writeInt(piece.dyeColor());
			}
		}
	}

	private static PlayersPayload read(FriendlyByteBuf buf) {
		int version = buf.readVarInt();
		if (version != VERSION) {
			// Formato di una versione futura della mod: lo saltiamo, i pannelli tornano a quelli vicini.
			buf.skipBytes(buf.readableBytes());
			return new PlayersPayload(version, List.of());
		}
		int count = buf.readVarInt();
		if (count < 0 || count > MAX_PLAYERS) {
			throw new IllegalArgumentException("Too many players in papouhud:players: " + count);
		}
		List<Entry> players = new ArrayList<>(count);
		for (int n = 0; n < count; n++) {
			UUID uuid = buf.readUUID();
			String name = buf.readUtf(MAX_STRING);
			String dimension = buf.readUtf(MAX_STRING);
			double x = buf.readDouble();
			double y = buf.readDouble();
			double z = buf.readDouble();
			float health = buf.readFloat();
			float maxHealth = buf.readFloat();
			int armorValue = buf.readVarInt();
			List<Armor> armor = new ArrayList<>(4);
			for (int i = 0; i < 4; i++) {
				armor.add(new Armor(buf.readUtf(MAX_STRING), buf.readVarInt(), buf.readVarInt(),
						buf.readBoolean(), buf.readInt()));
			}
			players.add(new Entry(uuid, name, dimension, x, y, z, health, maxHealth, armorValue, armor));
		}
		return new PlayersPayload(version, players);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
