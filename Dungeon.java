import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Dungeon {
    private final Tile[][] map = new Tile[GameConfig.GRID_WIDTH][GameConfig.GRID_HEIGHT];
    private final float[][] floorShade = new float[GameConfig.GRID_WIDTH][GameConfig.GRID_HEIGHT];
    private final int[][] floorDecals = new int[GameConfig.GRID_WIDTH][GameConfig.GRID_HEIGHT];
    private final TileVisibility[][] visibility = new TileVisibility[GameConfig.GRID_WIDTH][GameConfig.GRID_HEIGHT];
    private final List<Rect> rooms = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final Random rng;
    private Sanctuary sanctuary;
    private Stairs stairs;
    private int floor = 1;

    public Dungeon(Random rng) {
        this.rng = rng;
        resetTiles();
    }

    public void generate(long seed, Entity player, int floor) {
        rng.setSeed(seed);
        rooms.clear();
        enemies.clear();
        sanctuary = null;
        stairs = null;
        this.floor = Math.max(1, floor);

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
        placeSanctuary(start);
        placeStairs(start);
        spawnEnemies(player, start);
        computeVisibility(player.tileX, player.tileY);
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

    public TileVisibility[][] getVisibility() {
        return visibility;
    }

    public List<Rect> getRooms() {
        return rooms;
    }

    public List<Enemy> getEnemies() {
        return enemies;
    }

    public Sanctuary getSanctuary() {
        return sanctuary;
    }

    public Stairs getStairs() {
        return stairs;
    }

    public int getFloor() {
        return floor;
    }

    public boolean isWalkable(int x, int y) {
        return inBounds(x, y) && map[x][y] == Tile.FLOOR;
    }

    public boolean isSanctuaryTile(int x, int y) {
        return sanctuary != null && sanctuary.tileX == x && sanctuary.tileY == y;
    }

    public boolean isStairsTile(int x, int y) {
        return stairs != null && stairs.tileX == x && stairs.tileY == y;
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

    public void computeVisibility(int originX, int originY) {
        for (int x = 0; x < GameConfig.GRID_WIDTH; x++) {
            for (int y = 0; y < GameConfig.GRID_HEIGHT; y++) {
                if (visibility[x][y] == TileVisibility.VISIBLE) {
                    visibility[x][y] = TileVisibility.SEEN;
                }
            }
        }

        int radius = GameConfig.FOV_RADIUS;
        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int tx = originX + dx;
                int ty = originY + dy;
                if (!inBounds(tx, ty)) {
                    continue;
                }
                if (dx * dx + dy * dy > radiusSq) {
                    continue;
                }
                if (hasLineOfSight(originX, originY, tx, ty)) {
                    visibility[tx][ty] = TileVisibility.VISIBLE;
                }
            }
        }

        if (inBounds(originX, originY)) {
            visibility[originX][originY] = TileVisibility.VISIBLE;
        }
    }

    public boolean isInBounds(int x, int y) {
        return inBounds(x, y);
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
                visibility[x][y] = TileVisibility.UNSEEN;
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

    private boolean hasLineOfSight(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = Integer.compare(x1, x0);
        int sy = Integer.compare(y1, y0);
        int err = dx - dy;
        int x = x0;
        int y = y0;

        while (x != x1 || y != y1) {
            if (!(x == x0 && y == y0)) {
                if (!inBounds(x, y)) {
                    return false;
                }
                if (map[x][y] == Tile.WALL && !(x == x1 && y == y1)) {
                    return false;
                }
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
        return true;
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

    private void spawnEnemies(Entity player, Rect startRoom) {
        int target = GameConfig.ENEMIES_MIN
                + rng.nextInt(GameConfig.ENEMIES_MAX - GameConfig.ENEMIES_MIN + 1);
        int attempts = 0;
        while (enemies.size() < target && attempts < target * 60) {
            attempts++;
            Rect room = rooms.get(rng.nextInt(rooms.size()));
            if (room == startRoom) {
                continue;
            }
            int rx = room.x + 1 + rng.nextInt(Math.max(1, room.w - 2));
            int ry = room.y + 1 + rng.nextInt(Math.max(1, room.h - 2));
            if (!isWalkable(rx, ry)) {
                continue;
            }
            if ((rx == player.tileX && ry == player.tileY) || getEnemyAt(rx, ry) != null) {
                continue;
            }
            if (isSanctuaryTile(rx, ry) || isStairsTile(rx, ry)) {
                continue;
            }
            EnemyType type = rollEnemyType();
            boolean elite = rng.nextFloat() < GameConfig.ELITE_CHANCE;
            enemies.add(Enemy.spawn(type, elite, rx, ry, floor));
        }
    }

    private EnemyType rollEnemyType() {
        float roll = rng.nextFloat();
        if (roll < GameConfig.ARCHER_WEIGHT) {
            return EnemyType.ARCHER;
        }
        if (roll < GameConfig.ARCHER_WEIGHT + GameConfig.SLIME_WEIGHT) {
            return EnemyType.SLIME;
        }
        return EnemyType.GRUNT;
    }

    private void placeSanctuary(Rect start) {
        if (rooms.isEmpty()) {
            sanctuary = null;
            return;
        }
        List<Rect> candidates = new ArrayList<>(rooms);
        candidates.remove(start);
        Rect target = candidates.isEmpty() ? start : candidates.get(rng.nextInt(candidates.size()));
        sanctuary = new Sanctuary(target.centerX(), target.centerY());
    }

    private void placeStairs(Rect start) {
        if (rooms.isEmpty()) {
            stairs = null;
            return;
        }
        int sx = start.centerX();
        int sy = start.centerY();
        Rect best = null;
        int bestDist = -1;
        for (Rect room : rooms) {
            int cx = room.centerX();
            int cy = room.centerY();
            if (sanctuary != null && sanctuary.tileX == cx && sanctuary.tileY == cy) {
                continue;
            }
            int dist = Math.abs(cx - sx) + Math.abs(cy - sy);
            if (room != start && dist > bestDist) {
                bestDist = dist;
                best = room;
            }
        }
        if (best == null) {
            best = rooms.get(rooms.size() - 1);
        }
        int tx = Math.max(best.x, Math.min(best.x + best.w - 1, best.centerX()));
        int ty = Math.max(best.y, Math.min(best.y + best.h - 1, best.centerY()));
        stairs = new Stairs(tx, ty);
        if (sanctuary != null && sanctuary.tileX == stairs.tileX && sanctuary.tileY == stairs.tileY) {
            int nx = Math.min(best.x + best.w - 1, stairs.tileX + 1);
            if (!isWalkable(nx, stairs.tileY)) {
                nx = Math.max(best.x, stairs.tileX - 1);
            }
            int ny = stairs.tileY;
            if (!isWalkable(nx, ny)) {
                ny = Math.min(best.y + best.h - 1, stairs.tileY + 1);
            }
            stairs = new Stairs(nx, ny);
        }
    }
}
