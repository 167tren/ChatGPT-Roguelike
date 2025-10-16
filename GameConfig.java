public final class GameConfig {
    private GameConfig() {}

    // Enemy population
    public static final int ENEMIES_MIN = 6;
    public static final int ENEMIES_MAX = 10;

    // Weighted spawn odds per floor
    public static final double ENEMY_ELITE_CHANCE = 0.12;

    // Floor scaling multipliers (applied per floor past the first)
    public static final double ENEMY_FLOOR_HP_MULTIPLIER = 1.20;
    public static final double ENEMY_FLOOR_ATTACK_MULTIPLIER = 1.15;
    public static final double ENEMY_FLOOR_DEFENSE_MULTIPLIER = 1.10;

    // Elite bonuses
    public static final double ELITE_HP_MULTIPLIER = 1.6;
    public static final double ELITE_ATTACK_MULTIPLIER = 1.35;
    public static final int ELITE_DEFENSE_BONUS = 4;

    // Sanctuary / Stairs visuals
    public static final float SANCTUARY_GLOW_ALPHA = 0.45f;
    public static final float STAIRS_GLOW_ALPHA = 0.35f;
}
