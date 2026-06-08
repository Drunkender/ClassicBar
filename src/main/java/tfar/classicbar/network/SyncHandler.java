package tfar.classicbar.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import tfar.classicbar.compat.ModCompat;
// Changed (MC 26.1 upgrade): Tough As Nails compat temporarily disabled (no 26.1 build),
// so toughasnails.* imports are removed and syncToughAsNailsData() is now a no-op.

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sync saturation (vanilla MC only syncs when it hits 0).
 * Sync exhaustion (vanilla MC does not sync it at all).
 * Also sync counterparts of thirst data since copied from the vanilla hunger system.
 */
public final class SyncHandler {

  private static SyncHandler INSTANCE;

  public static SyncHandler instance() {
    if (INSTANCE == null) {
      INSTANCE = new SyncHandler();
    }
    return INSTANCE;
  }

  private SyncHandler() {}

  // Vanilla MC
  private final Map<UUID, Float> lastSaturationLevels = new HashMap<>();
  private final Map<UUID, Float> lastExhaustionLevels = new HashMap<>();

  // Changed: removed lastHydrationLevels / lastThirstExhaustionLevels maps. They were never
  // written (syncToughAsNailsData() is a no-op while TAN compat is disabled for MC 26.1) and
  // only ever cleared, so they were dead state. When TAN compat is restored, re-add the per-player
  // last-value tracking maps alongside the restored syncToughAsNailsData() implementation
  // (see that method's javadoc).

  // Changed: was TickEvent.PlayerTickEvent with phase == Phase.END guard.
  // PlayerTickEvent.Post fires only at end-of-tick so no phase check is needed.
  @SubscribeEvent
  public void onPlayerTick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return; // Changed: getEntity() replaces event.player

    syncVanillaData(player);

    if (ModCompat.toughasnails.loaded) {
      syncToughAsNailsData(player);
    }
  }

  private void syncVanillaData(ServerPlayer player) {
    UUID uuid = player.getUUID();
    Float lastSaturationLevel = lastSaturationLevels.get(uuid);
    Float lastExhaustionLevel = lastExhaustionLevels.get(uuid);

    float saturationLevel = player.getFoodData().getSaturationLevel();
    if (lastSaturationLevel == null || lastSaturationLevel != saturationLevel) {
      PacketDistributor.sendToPlayer(player, new MessageSaturationSync(saturationLevel)); // Changed: was Message.channel().sendTo(msg, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT)
      lastSaturationLevels.put(uuid, saturationLevel);
    }

    float exhaustionLevel = player.getFoodData().exhaustionLevel; // Changed: getExhaustionLevel() removed in MC 26.1; field exposed via access transformer
    if (lastExhaustionLevel == null || Math.abs(lastExhaustionLevel - exhaustionLevel) >= 0.01f) {
      PacketDistributor.sendToPlayer(player, new MessageExhaustionSync(exhaustionLevel)); // Changed: was Message.channel().sendTo(msg, connection, NetworkDirection.PLAY_TO_CLIENT)
      lastExhaustionLevels.put(uuid, exhaustionLevel);
    }
  }

  /**
   * No-op while Tough As Nails compat is disabled for MC 26.1.
   * <p>
   * Restore the original implementation (read hydration/exhaustion via
   * {@code ThirstHelper.getThirst(player)} and send {@link MessageHydrationSync} /
   * {@link MessageThirstExhaustionSync}) once a 26.1-compatible Tough As Nails build exists.
   * When restoring, also re-add the per-player last-value tracking maps (removed as dead state)
   * and their cleanup in {@link #onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent)}.
   */
  private void syncToughAsNailsData(ServerPlayer player) {
    // intentionally empty — see method javadoc
  }

  @OnlyIn(Dist.CLIENT)
  @SubscribeEvent
  public void onClientPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
    Message.presentOnServer = true; // Changed: was Message.channel().isRemotePresent(conn); NeoForge payload system guarantees the server has the mod if the connection was established, so always true
  }

  @SubscribeEvent
  public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return; // §1: pattern matching instanceof
    UUID uuid = player.getUUID();

    lastSaturationLevels.remove(uuid);
    lastExhaustionLevels.remove(uuid);
    // Changed: removed the TAN hydration/thirst-exhaustion map cleanup — those maps no longer
    // exist (see field declarations above). Restore alongside syncToughAsNailsData().
  }
}
