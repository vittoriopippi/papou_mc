package com.giulio.papouhud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Sopra la testa di ogni mob vicino, la sua vita in cuori, come la barra della vita del giocatore:
 * un cuore ogni 2 punti, mezzo cuore per il punto dispari. Oltre 10 cuori (es. Enderman, Golem di
 * ferro) la fila sarebbe troppo lunga: c'e' un cuore e i numeri, sempre in cuori ("12/20").
 *
 * <p>E' disegnata come i nomi sopra le entita' (nametag: stessa posizione, rivolta verso la telecamera,
 * stesso sfondo), un po' piu' in alto se il mob ha gia' un nome. A differenza dei nomi non si vede
 * attraverso i muri, per non scoprire i mob nascosti; il nametag "non attraverso i muri" del gioco e'
 * quello sbiadito di chi e' accovacciato, quindi il testo viene inviato direttamente, a colori pieni.
 * I cuori sono caratteri di un font della mod (assets/papouhud/font/hearts.json). Si attiva/disattiva dal menu.
 */
public final class MobHealthLabel {
	/** Oltre questa distanza (blocchi) la vita non si mostra. */
	static final double MAX_DISTANCE = 20.0;
	/** Fino a questi cuori si mostrano tutti; oltre, cuore e numeri. */
	static final int MAX_HEART_ICONS = 10;

	private static final Style HEARTS = Style.EMPTY.withFont(
			new FontDescription.Resource(Identifier.fromNamespaceAndPath("papouhud", "hearts")));
	private static final String FULL = "";
	private static final String HALF = "";
	private static final String EMPTY = "";
	/** Scala dei nametag del gioco: 1 px di testo = 0.025 blocchi. */
	private static final float SCALE = 0.025f;
	/** Una riga dei nomi del gioco: 9 px di testo, con l'interlinea 1.15. */
	private static final float LINE_HEIGHT = 9.0f * 1.15f * SCALE;
	/** I nametag del gioco stanno mezzo blocco sopra il loro punto di aggancio. */
	private static final double ABOVE_ATTACHMENT = 0.5;

	private record Label(Component text, Vec3 attachment) {
	}

	private static final RenderStateDataKey<Label> KEY = RenderStateDataKey.create(() -> "papouhud:mob_health");

	private MobHealthLabel() {
	}

	/** Mentre il gioco prepara il disegno dell'entita': la scritta, se serve, va nel suo stato. */
	public static void extract(Entity entity, EntityRenderState state, float partialTick) {
		FabricRenderState data = (FabricRenderState) state;
		data.setData(KEY, null);
		if (!(entity instanceof Mob mob) || !PapouConfig.get().mobHealthEnabled || mob.isDeadOrDying()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mob.isInvisibleTo(mc.player)
				|| mc.player.distanceToSqr(mob) > MAX_DISTANCE * MAX_DISTANCE) {
			return;
		}
		Vec3 attachment = mob.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, mob.getYRot(partialTick));
		if (attachment != null) {
			data.setData(KEY, new Label(text(mob.getHealth(), mob.getMaxHealth()), attachment));
		}
	}

	/** Dopo il disegno dell'entita': la scritta, sopra il nome se ce l'ha. */
	public static void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		Label label = ((FabricRenderState) state).getData(KEY);
		if (label == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		// Sopra il nome e, se c'e', il punteggio sotto il nome (come li dispone il gioco).
		int lines = state.nameTag == null ? 0 : state.scoreText == null ? 1 : 2;
		Vec3 at = label.attachment();
		poseStack.pushPose();
		poseStack.translate(at.x, at.y + ABOVE_ATTACHMENT + LINE_HEIGHT * lines, at.z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(SCALE, -SCALE, SCALE);
		FormattedCharSequence text = label.text().getVisualOrderText();
		float x = -mc.font.width(text) / 2.0f;
		int background = ARGB.color(mc.options.getBackgroundOpacity(0.25f), 0xFF000000);
		collector.submitText(poseStack, x, 0.0f, text, false, Font.DisplayMode.NORMAL,
				LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, 2), 0xFFFFFFFF, background, 0);
		poseStack.popPose();
	}

	/** La vita in cuori: tutti i cuori fino a 10, altrimenti un cuore e "12/20" (in cuori). */
	static Component text(float health, float maxHealth) {
		int points = Mth.ceil(Math.max(0.0f, health));
		int maxPoints = Math.max(points, Mth.ceil(maxHealth));
		int maxHearts = (maxPoints + 1) / 2;
		if (maxHearts <= MAX_HEART_ICONS) {
			StringBuilder hearts = new StringBuilder();
			for (int i = 0; i < maxHearts; i++) {
				int left = points - i * 2;
				hearts.append(left >= 2 ? FULL : left == 1 ? HALF : EMPTY);
			}
			return Component.literal(hearts.toString()).withStyle(HEARTS);
		}
		// Il cuore con il font della mod, i numeri con quello normale (come fratelli, non figli: lo stile si eredita).
		MutableComponent text = Component.empty();
		text.append(Component.literal(FULL).withStyle(HEARTS));
		return text.append(Component.literal(" " + hearts(points) + "/" + hearts(maxPoints)).withStyle(ChatFormatting.WHITE));
	}

	/** Punti vita in cuori: "12" o "12.5". */
	private static String hearts(int points) {
		return points % 2 == 0 ? Integer.toString(points / 2) : points / 2 + ".5";
	}
}
