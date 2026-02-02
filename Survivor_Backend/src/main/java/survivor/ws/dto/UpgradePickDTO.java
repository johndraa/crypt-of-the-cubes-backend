package survivor.ws.dto;

/**
 * Very simple upgrade selection message.
 *
 * Sent to: /app/upgrade.pick
 */
public record UpgradePickDTO(
        int playerId,
        String selectedUpgrade   // "DAMAGE_UP", "ATKSPEED_UP", "MAX_HP_UP"
) {}
