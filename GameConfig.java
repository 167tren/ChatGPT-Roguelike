import java.awt.Color;

public final class GameConfig {
    private GameConfig() {
    }

    public static final int TILE_SIZE = 24;
    public static final int GRID_WIDTH = 40;
    public static final int GRID_HEIGHT = 24;
    public static final int HUD_HEIGHT = 64;
    public static final int FPS = 60;
    public static final int MOVE_DURATION_MS = 140;
    public static final int PARTICLES_MIN = 6;
    public static final int PARTICLES_MAX = 10;

    public static final int MAX_ROOMS = 14;
    public static final int ROOM_MIN = 4;
    public static final int ROOM_MAX = 9;
    public static final int ROOM_ATTEMPTS = 60;
    public static final long DEMO_SEED = 123456789L;

    public static final Color COLOR_BG = new Color(0x0D0F14);
    public static final Color COLOR_FLOOR = new Color(0x171A21);
    public static final Color COLOR_WALL = new Color(0x2B2F3A);
    public static final Color COLOR_PLAYER = new Color(0x52FFB8);
    public static final Color COLOR_PARTICLE = new Color(0xF9D66F);
    public static final Color COLOR_TEXT_PRIMARY = new Color(0xEDEFF3);
    public static final Color COLOR_TEXT_SECONDARY = new Color(0xAEB6C2);
    public static final Color COLOR_ACCENT = new Color(0x6BE675);
    public static final Color COLOR_ENEMY = new Color(0xFF546E);
    public static final Color COLOR_COMBAT_PANEL = new Color(0x12151D);
    public static final Color COLOR_TRACK = new Color(0x1F2330);
    public static final Color COLOR_TRACK_BORDER = new Color(0x2F3342);
    public static final Color COLOR_SEGMENT_HIT = new Color(0xFFC94A);
    public static final Color COLOR_SEGMENT_CRIT = new Color(0x4AF58A);
    public static final Color COLOR_SEGMENT_DANGER = new Color(0xFF546E);
    public static final Color COLOR_SEGMENT_BLOCK = new Color(0x6AA9FF);
    public static final Color COLOR_ELITE_OUTLINE = new Color(0xFFB347);
    public static final Color COLOR_STAIRS = new Color(0x4FE8D3);
    public static final Color COLOR_SANCTUARY = new Color(0x5C6CFF);

    public static final int SHARD_PER_WIN = 1;
    public static final int SHARD_PER_ELITE = 2;
    public static final float RELIC_DROP_BASE = 0.20f;
    public static final float RELIC_DROP_FLOOR_BONUS = 0.10f;
    public static final float RELIC_DROP_ELITE_BONUS = 0.10f;

    public static final float ENEMY_HP_SCALING_PER_FLOOR = 0.15f;
    public static final float ENEMY_DMG_SCALING_PER_FLOOR = 0.15f;
    public static final float CURSOR_SPEED_SCALING_PER_FLOOR = 0.05f;

    public static final int COST_RELIC = 3;
    public static final int COST_HEAL = 2;
    public static final int SHOP_HEAL_AMOUNT = 25;

    public static final int ENEMIES_MIN = 8;
    public static final int ENEMIES_MAX = 12;
    public static final float ELITE_CHANCE = 0.10f;
    public static final float ARCHER_WEIGHT = 0.30f;
    public static final float SLIME_WEIGHT = 0.20f;
    public static final int PLAYER_MIN_DAMAGE = 6;
    public static final int PLAYER_MAX_DAMAGE = 10;
    public static final float PLAYER_COMBO_STEP = 0.15f;
    public static final float BLOCK_DAMAGE_REDUCTION = 0.2f;
    public static final float COMBAT_PANEL_HEIGHT_RATIO = 0.34f;
    public static final float COMBAT_TRACK_WIDTH_RATIO = 0.75f;
    public static final float COMBAT_TRACK_HEIGHT = 80f;
    public static final float COMBAT_ROUND_TIMEOUT = 2.5f;
    public static final float COMBAT_POST_INPUT_DELAY = 0.45f;
    public static final float COMBAT_CURSOR_BASE_SPEED = 0.7f;
    public static final float COMBAT_CURSOR_SPEED_GROWTH = 1.1f;
    public static final float COMBAT_TRACK_FLASH_TIME = 0.4f;
    public static final float COMBAT_SHAKE_TIME = 0.3f;
    public static final float COMBAT_SHAKE_MAG = 6f;

    public static final int PANEL_WIDTH = GRID_WIDTH * TILE_SIZE;
    public static final int PANEL_HEIGHT = GRID_HEIGHT * TILE_SIZE + HUD_HEIGHT;

    public static final int DECAL_NONE = -1;
    public static final int DECAL_PLUS = 0;
    public static final int DECAL_LINE = 1;
    public static final int DECAL_DOT = 2;
}
