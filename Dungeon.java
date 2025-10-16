import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Dungeon {
    private final Tile[][] map = new Tile[GameConfig.GRID_WIDTH][GameConfig.GRID_HEIGHT];
    private final float[][] floorShade = new float[GameConfig.GRID_WIDTH][GameConfig.GRID_HEIGHT];
    private final int[][] floorDecals = new int[GameConfig.GRID_WIDTH][GameConfig.GRID_HEIGHT];
    private final List<Rect> rooms = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final Random rng;

    public Dungeon(Random rng) {
        this.rng = rng;
        resetTiles();
    }

    public void generate(long seed, Entity player) {
        rng.setSeed(seed);
        rooms.clear();
        enemies.clear();

        resetTiles();
        resetPlayer(player);

        int attempts = 0;
        while (rooms.size() < GameConfig.MAX_ROOMS && attempts < GameConfig.ROOM_ATTEMPTS) {
            attempts++;
            int w = GameConfig.ROOM_MIN + rng.nextInt(GameConfig.ROOM_MAX - GameConfig.ROOM_MIN + 1);
            int h = GameConfig.ROOM_MIN + rng.nextInt(GameConfig.ROOM_MAX - GameConfig.ROOM_MIN + 1);
            if (w >= GameConfig.GRID_WIDTH - 2 || h >= GameConfig.GRID_HEIGHT - 2) {
                continue;
            }
            int x = 1 + rng.nextInt(Math.max(1, GameConfig.GRID_WIDTH - w - 1));
            int y = 1 + rng.nextInt(Math.max(1, GameConfig.GRID_HEIGHT - h - 1));
            Rect room = new Rect(x, y, w, h);

            boolean overlaps = false;
            for (Rect other : rooms) {
                if (room.expanded(1).intersects(other)) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) {
                continue;
            }

            carveRoom(room);
            if (!rooms.isEmpty()) {
                Rect previous = rooms.get(rooms.size() - 1);
                carveCorridor(previous, room);
            }
            rooms.add(room);
        }

        if (rooms.isEmpty()) {
            int w = GameConfig.ROOM_MIN + 2;
            int h = GameConfig.ROOM_MIN + 2;
            int x = GameConfig.GRID_WIDTH / 2 - w / 2;
            int y = GameConfig.GRID_HEIGHT / 2 - h / 2;
            Rect fallback = new Rect(x, y, w, h);
            carveRoom(fallback);
            rooms.add(fallback);
        }

        Rect start = rooms.get(0);
        placePlayer(player, start.centerX(), start.centerY());
        spawnEnemies(player);
    }

    public Tile[][] getMap() {
        return map;
    }

    public float[][] getFloorShade() {
        return floorShade;
    }

    public int[][] getFloorDecals() {
        return floorDecals;
    }

    public List<Rect> getRooms() {
        return rooms;
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    public boolean isWalkable(int x, int y) {
        return inBounds(x, y) && map[x][y] == Tile.FLOOR;
    }

    public Enemy getEnemyAt(int x, int y) {
        for (Enemy enemy : enemies) {
            if (enemy.tileX == x && enemy.tileY == y) {
                return enemy;
            }
        }
        return null;
    }

    public void removeEnemy(Enemy enemy) {
        enemies.remove(enemy);
    }

    private void resetPlayer(Entity player) {
        player.moving = false;
        player.moveTime = 0f;
    }

    private void resetTiles() {
        for (int x = 0; x < GameConfig.GRID_WIDTH; x++) {
            for (int y = 0; y < GameConfig.GRID_HEIGHT; y++) {
                map[x][y] = Tile.WALL;
                floorShade[x][y] = 1f;
                floorDecals[x][y] = GameConfig.DECAL_NONE;
            }
        }
    }

    private void carveRoom(Rect room) {
        for (int x = room.x; x < room.x + room.w; x++) {
            for (int y = room.y; y < room.y + room.h; y++) {
                carveFloor(x, y);
            }
        }
    }

    private void carveCorridor(Rect from, Rect to) {
        int x1 = from.centerX();
        int y1 = from.centerY();
        int x2 = to.centerX();
        int y2 = to.centerY();
        boolean horizontalFirst = rng.nextBoolean();
        if (horizontalFirst) {
            carveHorizontalCorridor(x1, x2, y1);
            carveVerticalCorridor(y1, y2, x2);
        } else {
            carveVerticalCorridor(y1, y2, x1);
            carveHorizontalCorridor(x1, x2, y2);
        }
    }

    private void carveHorizontalCorridor(int x1, int x2, int y) {
        int start = Math.min(x1, x2);
        int end = Math.max(x1, x2);
        for (int x = start; x <= end; x++) {
            carveFloor(x, y);
        }
    }

    private void carveVerticalCorridor(int y1, int y2, int x) {
        int start = Math.min(y1, y2);
        int end = Math.max(y1, y2);
        for (int y = start; y <= end; y++) {
            carveFloor(x, y);
        }
    }

    private void carveFloor(int x, int y) {
        if (!inBounds(x, y)) {
            return;
        }
        map[x][y] = Tile.FLOOR;
        floorShade[x][y] = 0.9f + rng.nextFloat() * 0.18f;
        if (rng.nextFloat() < 0.08f) {
            floorDecals[x][y] = rng.nextInt(3);
        } else {
            floorDecals[x][y] = GameConfig.DECAL_NONE;
        }
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < GameConfig.GRID_WIDTH && y < GameConfig.GRID_HEIGHT;
    }

    private void placePlayer(Entity player, int tileX, int tileY) {
        player.tileX = tileX;
        player.tileY = tileY;
        player.renderX = tileX;
        player.renderY = tileY;
        player.startX = tileX;
        player.startY = tileY;
        player.targetX = tileX;
        player.targetY = tileY;
        player.moving = false;
        player.moveTime = 0f;
    }

    private void spawnEnemies(Entity player) {
        int target = GameConfig.ENEMY_MIN_COUNT
                + rng.nextInt(GameConfig.ENEMY_MAX_COUNT - GameConfig.ENEMY_MIN_COUNT + 1);
        int attempts = 0;
        while (enemies.size() < target && attempts < target * 40) {
            attempts++;
            Rect room = rooms.get(rng.nextInt(rooms.size()));
            int x = room.x + 1 + rng.nextInt(Math.max(1, Math.max(1, room.w - 2)));
            int y = room.y + 1 + rng.nextInt(Math.max(1, Math.max(1, room.h - 2)));
            if (!isWalkable(x, y)) {
                continue;
            }
            if ((x == player.tileX && y == player.tileY) || getEnemyAt(x, y) != null) {
                continue;
            }
            Enemy enemy = new Enemy();
            enemy.tileX = x;
            enemy.tileY = y;
            enemy.maxHp = 28 + rng.nextInt(18);
            enemy.hp = enemy.maxHp;
            enemy.minDmg = GameConfig.ENEMY_MIN_DAMAGE;
            enemy.maxDmg = GameConfig.ENEMY_MAX_DAMAGE;
            enemy.name = rng.nextBoolean() ? "Shade" : "Warden";
            enemies.add(enemy);
        }
    }
}
