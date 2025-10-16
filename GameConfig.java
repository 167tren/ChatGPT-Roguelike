import java.awt.Color;

public final class GameConfig {
    private GameConfig() {
    }

    public static final int SHARD_PER_WIN = 12;
    public static final int SHARD_PER_ELITE = 24;

    public static final float RELIC_DROP_BASE = 0.05f;
    public static final float RELIC_DROP_PER_FLOOR = 0.02f;
    public static final float RELIC_DROP_ELITE_BONUS = 0.15f;
    public static final float RELIC_DROP_PITY_INCREMENT = 0.05f;
    public static final float RELIC_DROP_PITY_MAX = 0.35f;

    public static final float ENEMY_HP_SCALING_PER_FLOOR = 0.10f;
    public static final float ENEMY_DMG_SCALING_PER_FLOOR = 0.08f;
    public static final float CURSOR_SPEED_SCALING_PER_FLOOR = 0.03f;

    public static final int COST_RELIC = 200;
    public static final int COST_HEAL = 80;
    public static final int SHOP_HEAL_AMOUNT = 40;

    public static final int ENEMIES_MIN = 4;
    public static final int ENEMIES_MAX = 8;
    public static final float ELITE_CHANCE = 0.2f;
    public static final float ARCHER_WEIGHT = 0.25f;
    public static final float SLIME_WEIGHT = 0.35f;

    public static final Color COLOR_ELITE_OUTLINE = new Color(0xFFD166);
    public static final Color COLOR_STAIRS = new Color(0x7D7AFF);
    public static final Color COLOR_SANCTUARY = new Color(0x56E39F);
}
