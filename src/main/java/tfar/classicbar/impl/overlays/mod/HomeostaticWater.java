package tfar.classicbar.impl.overlays.mod;

import homeostatic.common.attachments.WaterData;
import homeostatic.network.IWater;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import tfar.classicbar.compat.ModCompat;
import tfar.classicbar.config.ClassicBarsConfig;
import tfar.classicbar.config.ConfigCache;
import tfar.classicbar.impl.BarOverlayImpl;
import tfar.classicbar.util.Color;
import tfar.classicbar.util.ModUtils;

import java.util.Optional;

// Restored (MC 26.1.2): Homeostatic now ships a 26.1-compatible build (file ID 8035546), so the
// previous disabled stub is replaced with the real water/hydration overlay. Ported from the
// original MC 1.21 implementation; the only API change required was GuiGraphics -> GuiGraphicsExtractor
// in the render method signatures (the 26.1 GUI render entry point used by every ClassicBar overlay).
//
// Homeostatic API (confirmed against the original 1.21 jar; revisit if the 26.1 build renamed it):
//   WaterData.getData(player) -> Optional<? extends IWater>
//   IWater.getWaterLevel()           -> int   (0-20, drives the primary "water" bar)
//   IWater.getWaterSaturationLevel() -> float (0-1, drives the "hydration" overlay bar)
public class HomeostaticWater extends BarOverlayImpl {

    public static final String NAME = "homeostatic_water";
    /// Max raw water level reported by Homeostatic; used to scale the water bar to BarOverlayImpl.WIDTH.
    public static final double MAX_WATER = 20.0;
    /// Saturation level is normalized 0-1 by Homeostatic; clamp/scale the hydration overlay against this.
    public static final double MAX_HYDRATION = 1.0;

    public HomeostaticWater() {
        super(NAME);
    }

    @Override
    public boolean shouldRender(Player player) {
        // Only render when the Homeostatic mod is actually present; EventHandler already gates
        // registration on this, but guard here too so the overlay is inert if state changes.
        return ModCompat.homeostatic.loaded;
    }

    @Override
    public void renderBar(Gui gui, GuiGraphicsExtractor graphics, Player player, int screenWidth, int screenHeight, int vOffset) {
        // Homeostatic stores water state in a data attachment; absent until the mod initializes it for the player.
        Optional<? extends IWater> waterOpt = WaterData.getData(player);
        if (waterOpt.isEmpty()) return;
        IWater waterData = waterOpt.get();

        int waterLevel = waterData.getWaterLevel();
        // Clamp saturation to MAX_HYDRATION so a transient over-cap value can't draw past the bar end.
        double hydration = Math.min(waterData.getWaterSaturationLevel(), MAX_HYDRATION);

        int xStart = screenWidth / 2 + getHOffset();
        int yStart = screenHeight - vOffset;

        Color.reset();
        renderFullBarBackground(graphics, xStart, yStart);

        drawWater(graphics, xStart, yStart, waterLevel);

        // Hydration overlay sits on top of the water bar; only shown when present and enabled in config.
        if (hydration > 0 && ClassicBarsConfig.showHydrationBar.get()) {
            drawHydration(graphics, xStart, yStart, hydration);
        }
    }

    /// Draws the primary water-level bar (scaled 0..MAX_WATER) in the configured water color.
    private void drawWater(GuiGraphicsExtractor graphics, int x, int y, int waterLevel) {
        getSecondaryBarColor(0, null).color2Gl();
        double barWidth = ModUtils.getWidth(waterLevel, MAX_WATER);
        // Right-side bars fill from the right edge, so offset the start by the unused width.
        double barXStart = x + (rightHandSide() ? BarOverlayImpl.WIDTH - barWidth : 0);
        renderPartialBar(graphics, barXStart + 2, y + 2, barWidth);
    }

    /// Draws the hydration overlay bar (scaled 0..MAX_HYDRATION) in the configured hydration color.
    private void drawHydration(GuiGraphicsExtractor graphics, int x, int y, double hydration) {
        getPrimaryBarColor(0, null).color2Gl();
        double barWidth = ModUtils.getWidth(hydration, MAX_HYDRATION);
        double barXStart = x + (rightHandSide() ? BarOverlayImpl.WIDTH - barWidth : 0);
        renderPartialBar(graphics, barXStart + 2, y + 2, barWidth);
    }

    @Override
    public double getBarWidth(Player player) {
        Optional<? extends IWater> waterOpt = WaterData.getData(player);
        if (waterOpt.isEmpty()) return 0;
        return Math.ceil(BarOverlayImpl.WIDTH * waterOpt.get().getWaterLevel() / MAX_WATER);
    }

    @Override
    public void renderText(GuiGraphicsExtractor graphics, Player player, int width, int height, int vOffset) {
        Optional<? extends IWater> waterOpt = WaterData.getData(player);
        if (waterOpt.isEmpty()) return;
        int xStart = width / 2 + getIconOffset();
        int yStart = height - vOffset;
        int c = getSecondaryBarColor(0, player).colorToText();
        textHelper(graphics, xStart, yStart, waterOpt.get().getWaterLevel(), c);
    }

    @Override
    public void renderIcon(GuiGraphicsExtractor graphics, Player player, int width, int height, int vOffset) {
        int xStart = width / 2 + getIconOffset();
        int yStart = height - vOffset;
        // Homeostatic icons.png is 256x256; the filled blue water droplet at (0,0) is the best
        // available icon (it includes a faint background slot, but no cleaner standalone sprite exists).
        ModUtils.drawTexturedModalRect(graphics, xStart, yStart, 0, 0, 9, 9);
    }

    /// Hydration overlay color (the saturation portion).
    @Override
    public Color getPrimaryBarColor(int index, Player player) {
        return ConfigCache.homeostaticHydration;
    }

    /// Water-level color (the main bar).
    @Override
    public Color getSecondaryBarColor(int index, Player player) {
        return ConfigCache.homeostaticWater;
    }
}
