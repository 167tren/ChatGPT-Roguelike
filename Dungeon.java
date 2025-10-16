import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Dungeon {
    public enum TileType {
        WALL,
        FLOOR,
        SANCTUARY,
        STAIRS
    }

    private final int width;
    private final int height;
    private final int maxRooms;
    private final int roomMin;
    private final int roomMax;
    private final int roomAttempts;

    private final TileType[][] tiles;
    private final List<Rect> rooms = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final Random rng = new Random();

    private Point startPosition = new Point(0, 0);
    private Point sanctuaryPosition;
    private Point stairsPosition;
    private int floor = 1;

    public Dungeon(int width, int height, int maxRooms, int roomMin, int roomMax, int roomAttempts) {
        this.width = width;
        this.height = height;
        this.maxRooms = maxRooms;
        this.roomMin = roomMin;
        this.roomMax = roomMax;
        this.roomAttempts = roomAttempts;
        this.tiles = new TileType[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles[x][y] = TileType.WALL;
            }
        }
    }

    public void setFloor(int floor) {
        this.floor = Math.max(1, floor);
    }

    public int getFloor() {
        return floor;
    }

    public void generate(long seed) {
        rng.setSeed(seed);
        rooms.clear();
        enemies.clear();
        sanctuaryPosition = null;
        stairsPosition = null;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles[x][y] = TileType.WALL;
            }
        }

        int attempts = 0;
        while (rooms.size() < maxRooms && attempts < roomAttempts) {
            attempts++;
            int w = roomMin + rng.nextInt(roomMax - roomMin + 1);
            int h = roomMin + rng.nextInt(roomMax - roomMin + 1);
            if (w >= width - 2 || h >= height - 2) {
                continue;
            }
            int x = 1 + rng.nextInt(Math.max(1, width - w - 1));
            int y = 1 + rng.nextInt(Math.max(1, height - h - 1));
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
            int w = roomMin + 2;
            int h = roomMin + 2;
            int x = width / 2 - w / 2;
            int y = height / 2 - h / 2;
            Rect fallback = new Rect(x, y, w, h);
            carveRoom(fallback);
            rooms.add(fallback);
        }

        placeKeyTiles();
        spawnEnemies();
    }

    private void placeKeyTiles() {
        Rect startRoom = rooms.get(0);
        startPosition = new Point(startRoom.centerX(), startRoom.centerY());

        Rect stairsRoom = findFarthestRoom(startRoom, null);
        if (stairsRoom == null) {
            stairsRoom = startRoom;
        }
        stairsPosition = placeWithinRoom(stairsRoom, startPosition);
        if (stairsPosition != null) {
            tiles[stairsPosition.x][stairsPosition.y] = TileType.STAIRS;
        }

        Rect sanctuaryRoom = findFarthestRoom(startRoom, stairsRoom);
        if (sanctuaryRoom == null) {
            sanctuaryRoom = rooms.size() > 1 ? rooms.get(rooms.size() - 1) : startRoom;
        }
        sanctuaryPosition = placeWithinRoom(sanctuaryRoom, stairsPosition);
        if (sanctuaryPosition != null) {
            tiles[sanctuaryPosition.x][sanctuaryPosition.y] = TileType.SANCTUARY;
        }
    }

    private Rect findFarthestRoom(Rect origin, Rect exclude) {
        Rect result = null;
        double bestDistance = -1;
        for (Rect room : rooms) {
            if (room == origin || room == exclude) {
                continue;
            }
            double distance = squaredDistance(origin.centerX(), origin.centerY(), room.centerX(), room.centerY());
            if (distance > bestDistance) {
                bestDistance = distance;
                result = room;
            }
        }
        return result;
    }

    private Point placeWithinRoom(Rect room, Point avoid) {
        if (room == null) {
            return null;
        }
        List<Point> preferred = new ArrayList<>();
        List<Point> fallback = new ArrayList<>();
        for (int x = room.x; x < room.x + room.w; x++) {
            for (int y = room.y; y < room.y + room.h; y++) {
                if (tiles[x][y] != TileType.FLOOR) {
                    continue;
                }
                Point candidate = new Point(x, y);
                if (avoid != null && avoid.equals(candidate)) {
                    continue;
                }
                if (candidate.equals(startPosition)) {
                    fallback.add(candidate);
                } else {
                    preferred.add(candidate);
                }
            }
        }
        List<Point> pool = preferred.isEmpty() ? fallback : preferred;
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    private double squaredDistance(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2;
        int dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    private void spawnEnemies() {
        List<Point> spawnable = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                TileType tile = tiles[x][y];
                if (tile != TileType.FLOOR) {
                    continue;
                }
                Point tilePoint = new Point(x, y);
                if (tilePoint.equals(startPosition)) {
                    continue;
                }
                spawnable.add(tilePoint);
            }
        }

        if (spawnable.isEmpty()) {
            return;
        }

        int minCount = Math.min(GameConfig.ENEMIES_MIN, spawnable.size());
        int maxCount = Math.min(GameConfig.ENEMIES_MAX, spawnable.size());
        int countRange = Math.max(0, maxCount - minCount);
        int enemyCount = minCount + (countRange > 0 ? rng.nextInt(countRange + 1) : 0);

        Collections.shuffle(spawnable, rng);
        for (int i = 0; i < enemyCount && !spawnable.isEmpty(); i++) {
            Point spawn = spawnable.remove(spawnable.size() - 1);
            EnemyType type = chooseWeightedEnemy();
            boolean elite = rng.nextDouble() < GameConfig.ENEMY_ELITE_CHANCE;
            Enemy enemy = Enemy.spawn(type, elite, spawn.x, spawn.y, floor);
            enemies.add(enemy);
        }
    }

    private EnemyType chooseWeightedEnemy() {
        int totalWeight = 0;
        for (EnemyType type : EnemyType.values()) {
            totalWeight += type.getWeight();
        }
        if (totalWeight <= 0) {
            return EnemyType.GRUNT;
        }
        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (EnemyType type : EnemyType.values()) {
            cumulative += type.getWeight();
            if (roll < cumulative) {
                return type;
            }
        }
        return EnemyType.GRUNT;
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
        if (!inBounds(x, y)) return;
        tiles[x][y] = TileType.FLOOR;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public TileType getTile(int x, int y) {
        if (!inBounds(x, y)) {
            return TileType.WALL;
        }
        return tiles[x][y];
    }

    public boolean isWalkable(int x, int y) {
        if (!inBounds(x, y)) {
            return false;
        }
        TileType tile = tiles[x][y];
        return tile == TileType.FLOOR || tile == TileType.SANCTUARY || tile == TileType.STAIRS;
    }

    public Point getStartPosition() {
        return startPosition;
    }

    public Point getSanctuaryPosition() {
        return sanctuaryPosition;
    }

    public Point getStairsPosition() {
        return stairsPosition;
    }

    public List<Enemy> getEnemies() {
        return Collections.unmodifiableList(enemies);
    }

    private static class Rect {
        final int x;
        final int y;
        final int w;
        final int h;

        Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        Rect expanded(int padding) {
            return new Rect(x - padding, y - padding, w + padding * 2, h + padding * 2);
        }

        boolean intersects(Rect other) {
            return x < other.x + other.w && x + w > other.x && y < other.y + other.h && y + h > other.y;
        }

        int centerX() {
            return x + w / 2;
        }

        int centerY() {
            return y + h / 2;
        }
    }
}
