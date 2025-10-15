import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class Game extends JPanel implements Runnable {
    private static final long serialVersionUID = 1L;

    private static final int TILE_SIZE = 24;
    private static final int GRID_WIDTH = 40;
    private static final int GRID_HEIGHT = 24;
    private static final int HUD_HEIGHT = 64;
    private static final int FPS = 60;
    private static final int MOVE_DURATION_MS = 140;
    private static final int PARTICLES_MIN = 6;
    private static final int PARTICLES_MAX = 10;

    private static final int MAX_ROOMS = 14;
    private static final int ROOM_MIN = 4;
    private static final int ROOM_MAX = 9;
    private static final int ROOM_ATTEMPTS = 60;
    private static final long DEMO_SEED = 123456789L;

    private static final Color COLOR_BG = new Color(0x0D0F14);
    private static final Color COLOR_FLOOR = new Color(0x171A21);
    private static final Color COLOR_WALL = new Color(0x2B2F3A);
    private static final Color COLOR_PLAYER = new Color(0x52FFB8);
    private static final Color COLOR_PARTICLE = new Color(0xF9D66F);
    private static final Color COLOR_TEXT_PRIMARY = new Color(0xEDEFF3);
    private static final Color COLOR_TEXT_SECONDARY = new Color(0xAEB6C2);
    private static final Color COLOR_ACCENT = new Color(0x6BE675);
    private static final Color COLOR_ENEMY = new Color(0xFF546E);
    private static final Color COLOR_COMBAT_PANEL = new Color(0x12151D);
    private static final Color COLOR_TRACK = new Color(0x1F2330);
    private static final Color COLOR_TRACK_BORDER = new Color(0x2F3342);
    private static final Color COLOR_SEGMENT_HIT = new Color(0xFFC94A);
    private static final Color COLOR_SEGMENT_CRIT = new Color(0x4AF58A);
    private static final Color COLOR_SEGMENT_DANGER = new Color(0xFF546E);
    private static final Color COLOR_SEGMENT_BLOCK = new Color(0x6AA9FF);

    private static final int ENEMY_MIN_COUNT = 6;
    private static final int ENEMY_MAX_COUNT = 10;
    private static final int PLAYER_MIN_DAMAGE = 6;
    private static final int PLAYER_MAX_DAMAGE = 10;
    private static final float PLAYER_COMBO_STEP = 0.15f;
    private static final int ENEMY_MIN_DAMAGE = 4;
    private static final int ENEMY_MAX_DAMAGE = 8;
    private static final float BLOCK_DAMAGE_REDUCTION = 0.2f;
    private static final float COMBAT_PANEL_HEIGHT_RATIO = 0.34f;
    private static final float COMBAT_TRACK_WIDTH_RATIO = 0.75f;
    private static final float COMBAT_TRACK_HEIGHT = 80f;
    private static final float COMBAT_ROUND_TIMEOUT = 2.5f;
    private static final float COMBAT_POST_INPUT_DELAY = 0.45f;
    private static final float COMBAT_CURSOR_BASE_SPEED = 0.7f; // normalized units per second
    private static final float COMBAT_CURSOR_SPEED_GROWTH = 1.1f;
    private static final float COMBAT_TRACK_FLASH_TIME = 0.4f;
    private static final float COMBAT_SHAKE_TIME = 0.3f;
    private static final float COMBAT_SHAKE_MAG = 6f;

    private static final int PANEL_WIDTH = GRID_WIDTH * TILE_SIZE;
    private static final int PANEL_HEIGHT = GRID_HEIGHT * TILE_SIZE + HUD_HEIGHT;

    private static final int DECAL_NONE = -1;
    private static final int DECAL_PLUS = 0;
    private static final int DECAL_LINE = 1;
    private static final int DECAL_DOT = 2;

    private enum Tile { WALL, FLOOR }

    private enum Mode { DUNGEON, COMBAT, GAME_OVER }

    private enum SegmentType { HIT, CRIT, DANGER, BLOCK }

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

    private static class Entity {
        int tileX;
        int tileY;
        float renderX;
        float renderY;
        float startX;
        float startY;
        float targetX;
        float targetY;
        float moveTime;
        boolean moving;
        int hp = 100;
        int maxHp = 100;
    }

    private static class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float life;
        float maxLife;
        float radius;
        boolean screenSpace;
        Color color;
    }

    private static class Enemy {
        int tileX;
        int tileY;
        int maxHp;
        int hp;
        int minDmg;
        int maxDmg;
        String name;
    }

    private static class Segment {
        float start;
        float end;
        SegmentType type;
    }

    private static class CombatState {
        Enemy enemy;
        float cursorPos;
        boolean cursorForward = true;
        float cursorSpeed = COMBAT_CURSOR_BASE_SPEED;
        final List<Segment> segments = new ArrayList<>();
        boolean blockedThisRound;
        int roundIndex;
        int comboCount;
        float comboVisual;
        boolean inputProcessed;
        float roundElapsed;
        boolean awaitingEnemyTurn;
        float postInputTimer;
        float flashTimer;
        float shakeTimer;
        int lastDamage;
        SegmentType lastStrikeType;
    }

    private final Tile[][] map = new Tile[GRID_WIDTH][GRID_HEIGHT];
    private final float[][] floorShade = new float[GRID_WIDTH][GRID_HEIGHT];
    private final int[][] floorDecals = new int[GRID_WIDTH][GRID_HEIGHT];
    private final List<Rect> rooms = new ArrayList<>();
    private final Entity player = new Entity();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Particle> combatParticles = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final Random rng = new Random();

    private long currentSeed;
    private boolean running;
    private Mode mode = Mode.DUNGEON;
    private Enemy pendingCombatEnemy;
    private CombatState combat;

    public Game() {
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setFocusable(true);
        setDoubleBuffered(true);
        setBackground(COLOR_BG);

        setSeed(System.nanoTime());
        generateDungeon();
        initInput();
    }

    private void setSeed(long seed) {
        currentSeed = seed;
    }

    private void restartGame() {
        player.hp = player.maxHp;
        setSeed(System.nanoTime());
        generateDungeon();
    }

    private void generateDungeon() {
        rng.setSeed(currentSeed);
        rooms.clear();
        particles.clear();
        combatParticles.clear();
        enemies.clear();
        combat = null;
        pendingCombatEnemy = null;
        mode = Mode.DUNGEON;
        player.moving = false;
        player.moveTime = 0f;
        player.hp = Math.min(player.hp, player.maxHp);

        for (int x = 0; x < GRID_WIDTH; x++) {
            for (int y = 0; y < GRID_HEIGHT; y++) {
                map[x][y] = Tile.WALL;
                floorShade[x][y] = 1f;
                floorDecals[x][y] = DECAL_NONE;
            }
        }

        int attempts = 0;
        while (rooms.size() < MAX_ROOMS && attempts < ROOM_ATTEMPTS) {
            attempts++;
            int w = ROOM_MIN + rng.nextInt(ROOM_MAX - ROOM_MIN + 1);
            int h = ROOM_MIN + rng.nextInt(ROOM_MAX - ROOM_MIN + 1);
            if (w >= GRID_WIDTH - 2 || h >= GRID_HEIGHT - 2) {
                continue;
            }
            int x = 1 + rng.nextInt(Math.max(1, GRID_WIDTH - w - 1));
            int y = 1 + rng.nextInt(Math.max(1, GRID_HEIGHT - h - 1));
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
            int w = ROOM_MIN + 2;
            int h = ROOM_MIN + 2;
            int x = GRID_WIDTH / 2 - w / 2;
            int y = GRID_HEIGHT / 2 - h / 2;
            Rect fallback = new Rect(x, y, w, h);
            carveRoom(fallback);
            rooms.add(fallback);
        }

        Rect start = rooms.get(0);
        placePlayer(start.centerX(), start.centerY());
        spawnEnemies();
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
            floorDecals[x][y] = DECAL_NONE;
        }
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < GRID_WIDTH && y < GRID_HEIGHT;
    }

    private void placePlayer(int tileX, int tileY) {
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

    private void spawnEnemies() {
        int target = ENEMY_MIN_COUNT + rng.nextInt(ENEMY_MAX_COUNT - ENEMY_MIN_COUNT + 1);
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
            enemy.minDmg = ENEMY_MIN_DAMAGE;
            enemy.maxDmg = ENEMY_MAX_DAMAGE;
            enemy.name = rng.nextBoolean() ? "Shade" : "Warden";
            enemies.add(enemy);
        }
    }

    private Enemy getEnemyAt(int x, int y) {
        for (Enemy enemy : enemies) {
            if (enemy.tileX == x && enemy.tileY == y) {
                return enemy;
            }
        }
        return null;
    }

    private void beginCombat(Enemy enemy) {
        combat = new CombatState();
        combat.enemy = enemy;
        combat.comboCount = 0;
        combat.comboVisual = 0f;
        combat.cursorPos = 0f;
        combat.cursorForward = true;
        combat.cursorSpeed = COMBAT_CURSOR_BASE_SPEED;
        combat.roundIndex = 0;
        combat.lastStrikeType = null;
        combat.lastDamage = 0;
        mode = Mode.COMBAT;
        setupCombatRound(combat, true);
    }

    private void setupCombatRound(CombatState state, boolean resetSpeed) {
        if (!resetSpeed) {
            state.cursorSpeed *= COMBAT_CURSOR_SPEED_GROWTH;
        } else {
            state.cursorSpeed = COMBAT_CURSOR_BASE_SPEED;
        }
        state.cursorPos = 0f;
        state.cursorForward = true;
        state.roundElapsed = 0f;
        state.inputProcessed = false;
        state.awaitingEnemyTurn = false;
        state.postInputTimer = 0f;
        state.blockedThisRound = false;
        state.flashTimer = 0f;
        state.shakeTimer = 0f;
        generateCombatSegments(state);
    }

    private void generateCombatSegments(CombatState state) {
        state.segments.clear();
        boolean needHit = true;
        boolean hasCrit = false;
        boolean requireBlock = ((state.roundIndex + 1) % 3) == 0;
        float desiredDanger = 0.18f + rng.nextFloat() * 0.07f;
        float dangerCoverage = 0f;
        int targetSegments = 3 + rng.nextInt(3);
        int attempts = 0;
        while ((state.segments.size() < targetSegments || needHit || requireBlock) && attempts < 200) {
            attempts++;
            SegmentType type;
            if (requireBlock) {
                type = SegmentType.BLOCK;
            } else if (needHit) {
                type = SegmentType.HIT;
            } else if (!hasCrit && rng.nextFloat() < 0.28f) {
                type = SegmentType.CRIT;
            } else if (dangerCoverage < desiredDanger && rng.nextFloat() < 0.6f) {
                type = SegmentType.DANGER;
            } else {
                type = rng.nextFloat() < 0.65f ? SegmentType.HIT : SegmentType.DANGER;
            }

            float width = 0.08f + rng.nextFloat() * 0.12f;
            if (type == SegmentType.BLOCK) {
                width = 0.06f + rng.nextFloat() * 0.08f;
            } else if (type == SegmentType.CRIT) {
                width = 0.07f + rng.nextFloat() * 0.12f;
            }
            width = Math.min(width, 0.32f);
            float start = rng.nextFloat() * (1f - width);
            float end = start + width;

            if (segmentsOverlap(state.segments, start, end)) {
                continue;
            }

            Segment segment = new Segment();
            segment.start = start;
            segment.end = end;
            segment.type = type;
            state.segments.add(segment);

            if (type == SegmentType.HIT) {
                needHit = false;
            } else if (type == SegmentType.CRIT) {
                hasCrit = true;
            } else if (type == SegmentType.DANGER) {
                dangerCoverage += (end - start);
            } else if (type == SegmentType.BLOCK) {
                requireBlock = false;
            }
        }

        if (needHit) {
            Segment fallback = new Segment();
            fallback.start = 0.45f;
            fallback.end = 0.55f;
            fallback.type = SegmentType.HIT;
            state.segments.add(fallback);
        }

        Collections.sort(state.segments, Comparator.comparingDouble(s -> s.start));
    }

    private boolean segmentsOverlap(List<Segment> segments, float start, float end) {
        for (Segment s : segments) {
            if (start < s.end && end > s.start) {
                return true;
            }
        }
        return false;
    }

    private void initInput() {
        KeyAdapter adapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (handleKey(e.getKeyCode())) {
                    e.consume();
                }
            }
        };
        addKeyListener(adapter);
    }

    private boolean handleKey(int keyCode) {
        if (keyCode == KeyEvent.VK_Q) {
            System.exit(0);
            return true;
        }
        switch (mode) {
            case GAME_OVER:
                return handleGameOverKey(keyCode);
            case COMBAT:
                return handleCombatKey(keyCode);
            case DUNGEON:
            default:
                return handleDungeonKey(keyCode);
        }
    }

    private boolean handleGameOverKey(int keyCode) {
        if (keyCode == KeyEvent.VK_R) {
            restartGame();
            return true;
        }
        return false;
    }

    private boolean handleDungeonKey(int keyCode) {
        if (keyCode == KeyEvent.VK_N) {
            setSeed(System.nanoTime());
            generateDungeon();
            return true;
        }
        if (keyCode == KeyEvent.VK_F5) {
            generateDungeon();
            return true;
        }
        if (keyCode == KeyEvent.VK_S) {
            setSeed(DEMO_SEED);
            generateDungeon();
            return true;
        }
        if (player.moving) {
            return false;
        }
        switch (keyCode) {
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                return tryMove(0, -1);
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                return tryMove(0, 1);
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_A:
                return tryMove(-1, 0);
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_D:
                return tryMove(1, 0);
            default:
                return false;
        }
    }

    private boolean handleCombatKey(int keyCode) {
        if (combat == null) {
            return false;
        }
        if (keyCode == KeyEvent.VK_SPACE || keyCode == KeyEvent.VK_ENTER) {
            processCombatInput();
            return true;
        }
        return false;
    }

    private boolean tryMove(int dx, int dy) {
        int newX = player.tileX + dx;
        int newY = player.tileY + dy;
        if (!isWalkable(newX, newY)) {
            return false;
        }
        int fromX = player.tileX;
        int fromY = player.tileY;

        player.startX = player.renderX;
        player.startY = player.renderY;
        player.targetX = newX;
        player.targetY = newY;
        player.tileX = newX;
        player.tileY = newY;
        player.moveTime = 0f;
        player.moving = true;

        spawnStepParticles(fromX, fromY);

        Enemy encountered = getEnemyAt(newX, newY);
        if (encountered != null) {
            pendingCombatEnemy = encountered;
        }
        return true;
    }

    private boolean isWalkable(int x, int y) {
        return inBounds(x, y) && map[x][y] == Tile.FLOOR;
    }

    private void spawnStepParticles(int tileX, int tileY) {
        int count = PARTICLES_MIN + rng.nextInt(PARTICLES_MAX - PARTICLES_MIN + 1);
        float originX = (tileX + 0.5f) * TILE_SIZE;
        float originY = (tileY + 0.75f) * TILE_SIZE;
        for (int i = 0; i < count; i++) {
            float angle = (float) (rng.nextFloat() * Math.PI * 2);
            float speed = 20f + rng.nextFloat() * 40f;
            Particle p = new Particle();
            float offsetRadius = rng.nextFloat() * 6f;
            p.x = originX + (float) Math.cos(angle) * offsetRadius;
            p.y = originY + (float) Math.sin(angle) * offsetRadius;
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = (float) Math.sin(angle) * speed - rng.nextFloat() * 10f;
            p.life = 0f;
            p.maxLife = 0.2f + rng.nextFloat() * 0.2f;
            p.radius = 2f + rng.nextFloat() * 2f;
            p.screenSpace = false;
            p.color = COLOR_PARTICLE;
            particles.add(p);
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        requestFocusInWindow();
        if (!running) {
            running = true;
            Thread thread = new Thread(this, "GameLoop");
            thread.setDaemon(true);
            thread.start();
        }
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        double frameDuration = 1_000_000_000.0 / FPS;
        double accumulator = 0.0;
        while (running) {
            long now = System.nanoTime();
            double elapsed = now - lastTime;
            if (elapsed > 250_000_000.0) {
                elapsed = 250_000_000.0;
            }
            lastTime = now;
            accumulator += elapsed;

            boolean updated = false;
            while (accumulator >= frameDuration) {
                tick(1f / FPS);
                accumulator -= frameDuration;
                updated = true;
            }

            if (updated) {
                repaint();
            }

            long sleepTime = (long) ((frameDuration - accumulator) / 1_000_000.0);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void tick(float dt) {
        switch (mode) {
            case DUNGEON:
                updatePlayer(dt);
                break;
            case COMBAT:
                updateCombat(dt);
                break;
            case GAME_OVER:
                break;
        }
        updateParticles(dt);
    }

    private void updatePlayer(float dt) {
        if (!player.moving) {
            player.renderX = player.tileX;
            player.renderY = player.tileY;
            return;
        }
        player.moveTime += dt * 1000f;
        float t = Math.min(1f, player.moveTime / MOVE_DURATION_MS);
        float eased = easeOut01(t);
        player.renderX = player.startX + (player.targetX - player.startX) * eased;
        player.renderY = player.startY + (player.targetY - player.startY) * eased;
        if (t >= 1f) {
            player.moving = false;
            player.renderX = player.targetX;
            player.renderY = player.targetY;
            if (pendingCombatEnemy != null) {
                beginCombat(pendingCombatEnemy);
                pendingCombatEnemy = null;
            }
        }
    }

    private void updateCombat(float dt) {
        if (combat == null) {
            mode = Mode.DUNGEON;
            return;
        }
        CombatState state = combat;
        state.roundElapsed += dt;
        float delta = state.cursorSpeed * dt;
        if (state.cursorForward) {
            state.cursorPos += delta;
            if (state.cursorPos >= 1f) {
                state.cursorPos = 1f;
                state.cursorForward = false;
            }
        } else {
            state.cursorPos -= delta;
            if (state.cursorPos <= 0f) {
                state.cursorPos = 0f;
                state.cursorForward = true;
            }
        }

        if (!state.inputProcessed && state.roundElapsed >= COMBAT_ROUND_TIMEOUT) {
            registerCombatMiss(false);
        }

        if (state.flashTimer > 0f) {
            state.flashTimer = Math.max(0f, state.flashTimer - dt);
        }
        if (state.shakeTimer > 0f) {
            state.shakeTimer = Math.max(0f, state.shakeTimer - dt);
        }

        if (state.awaitingEnemyTurn) {
            state.postInputTimer += dt;
            if (state.postInputTimer >= COMBAT_POST_INPUT_DELAY) {
                resolveEnemyTurn();
                if (mode != Mode.COMBAT || combat == null) {
                    return;
                }
            }
        }

        state.comboVisual += (state.comboCount - state.comboVisual) * Math.min(1f, dt * 6f);
    }

    private void processCombatInput() {
        if (combat == null) {
            return;
        }
        CombatState state = combat;
        if (state.inputProcessed) {
            return;
        }
        Segment segment = findSegmentAt(state, state.cursorPos);
        if (segment == null) {
            registerCombatMiss(true);
            return;
        }
        switch (segment.type) {
            case DANGER:
                registerCombatMiss(true);
                return;
            case BLOCK:
                state.blockedThisRound = true;
                state.lastStrikeType = SegmentType.BLOCK;
                state.lastDamage = 0;
                state.inputProcessed = true;
                state.awaitingEnemyTurn = true;
                state.postInputTimer = 0f;
                state.roundElapsed = 0f;
                spawnCombatSparks(segment, state, COLOR_SEGMENT_BLOCK);
                return;
            case HIT:
            case CRIT:
                resolvePlayerStrike(segment.type);
                return;
        }
    }

    private void resolvePlayerStrike(SegmentType type) {
        CombatState state = combat;
        if (state == null) {
            return;
        }
        int base = PLAYER_MIN_DAMAGE + rng.nextInt(PLAYER_MAX_DAMAGE - PLAYER_MIN_DAMAGE + 1);
        int comboTier = state.comboCount / 3;
        float multiplier = 1f + comboTier * PLAYER_COMBO_STEP;
        if (type == SegmentType.CRIT) {
            multiplier += 0.5f;
        }
        int damage = Math.max(1, Math.round(base * multiplier));
        state.enemy.hp = Math.max(0, state.enemy.hp - damage);
        state.lastDamage = damage;
        state.lastStrikeType = type;
        state.comboCount += type == SegmentType.CRIT ? 2 : 1;
        state.flashTimer = COMBAT_TRACK_FLASH_TIME;
        spawnCombatSparks(type);
        state.inputProcessed = true;
        state.roundElapsed = 0f;
        if (state.enemy.hp <= 0) {
            state.awaitingEnemyTurn = false;
            endCombat(true);
            return;
        }
        state.awaitingEnemyTurn = true;
        state.postInputTimer = 0f;
    }

    private void registerCombatMiss(boolean fromInput) {
        CombatState state = combat;
        if (state == null || state.inputProcessed) {
            return;
        }
        state.comboCount = 0;
        state.lastDamage = 0;
        state.lastStrikeType = SegmentType.DANGER;
        state.blockedThisRound = false;
        state.inputProcessed = true;
        state.awaitingEnemyTurn = true;
        state.postInputTimer = 0f;
        state.roundElapsed = 0f;
        state.flashTimer = 0f;
        if (fromInput) {
            state.shakeTimer = COMBAT_SHAKE_TIME;
        }
    }

    private void resolveEnemyTurn() {
        CombatState state = combat;
        if (state == null) {
            return;
        }
        state.awaitingEnemyTurn = false;
        state.postInputTimer = 0f;

        if (state.enemy.hp <= 0) {
            endCombat(true);
            return;
        }

        int damage = state.enemy.minDmg + rng.nextInt(state.enemy.maxDmg - state.enemy.minDmg + 1);
        if (state.blockedThisRound) {
            damage = Math.round(damage * BLOCK_DAMAGE_REDUCTION);
        }
        state.blockedThisRound = false;
        if (damage > 0) {
            player.hp = Math.max(0, player.hp - damage);
            state.shakeTimer = Math.max(state.shakeTimer, COMBAT_SHAKE_TIME * 0.8f);
        }

        if (player.hp <= 0) {
            endCombat(false);
            return;
        }

        state.roundIndex++;
        setupCombatRound(state, false);
    }

    private void endCombat(boolean victory) {
        if (combat == null) {
            return;
        }
        Enemy enemy = combat.enemy;
        combat.awaitingEnemyTurn = false;
        if (victory) {
            enemies.remove(enemy);
            player.hp = Math.min(player.maxHp, player.hp + 10);
        } else {
            mode = Mode.GAME_OVER;
            combatParticles.clear();
            combat = null;
            pendingCombatEnemy = null;
            return;
        }
        combatParticles.clear();
        combat = null;
        mode = Mode.DUNGEON;
        pendingCombatEnemy = null;
        if (enemies.isEmpty()) {
            // optional future logic placeholder
        }
    }

    private Segment findSegmentAt(CombatState state, float position) {
        for (Segment segment : state.segments) {
            if (position >= segment.start && position <= segment.end) {
                return segment;
            }
        }
        return null;
    }

    private void spawnCombatSparks(SegmentType type) {
        if (combat == null) {
            return;
        }
        float[] bounds = computeCombatTrackBounds();
        float cx = bounds[0] + combat.cursorPos * bounds[2];
        float cy = bounds[1] + bounds[3] / 2f;
        Color color = type == SegmentType.CRIT ? COLOR_SEGMENT_CRIT : COLOR_SEGMENT_HIT;
        spawnCombatSparksAt(cx, cy, color);
    }

    private void spawnCombatSparks(Segment segment, CombatState state, Color color) {
        if (segment == null) {
            return;
        }
        float[] bounds = computeCombatTrackBounds();
        float mid = (segment.start + segment.end) * 0.5f;
        float cx = bounds[0] + mid * bounds[2];
        float cy = bounds[1] + bounds[3] / 2f;
        spawnCombatSparksAt(cx, cy, color);
    }

    private void spawnCombatSparksAt(float cx, float cy, Color color) {
        for (int i = 0; i < 8; i++) {
            Particle p = new Particle();
            float angle = (float) (rng.nextFloat() * Math.PI * 2);
            float speed = 60f + rng.nextFloat() * 80f;
            p.x = cx;
            p.y = cy;
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = (float) Math.sin(angle) * speed * 0.6f;
            p.life = 0f;
            p.maxLife = 0.3f + rng.nextFloat() * 0.2f;
            p.radius = 3f + rng.nextFloat() * 2f;
            p.screenSpace = true;
            p.color = color;
            combatParticles.add(p);
        }
    }

    private float[] computeCombatTrackBounds() {
        int width = getWidth();
        int height = getHeight();
        float combatHeight = (height - HUD_HEIGHT) * COMBAT_PANEL_HEIGHT_RATIO;
        float trackWidth = width * COMBAT_TRACK_WIDTH_RATIO;
        float trackHeight = COMBAT_TRACK_HEIGHT;
        float trackX = (width - trackWidth) / 2f;
        float panelY = height - combatHeight - 24f;
        float trackY = panelY + (combatHeight - trackHeight) / 2f;
        return new float[] { trackX, trackY, trackWidth, trackHeight };
    }

    private void updateParticles(float dt) {
        updateParticleCollection(particles, dt, false);
        updateParticleCollection(combatParticles, dt, true);
    }

    private void updateParticleCollection(List<Particle> list, float dt, boolean screenSpace) {
        for (int i = list.size() - 1; i >= 0; i--) {
            Particle p = list.get(i);
            p.life += dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (screenSpace) {
                p.vx *= 0.94f;
                p.vy *= 0.94f;
            } else {
                p.vy += -8f * dt;
            }
            if (p.life >= p.maxLife) {
                list.remove(i);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setColor(COLOR_BG);
        g2.fillRect(0, 0, getWidth(), getHeight());

        drawHUD(g2);

        g2.translate(0, HUD_HEIGHT);
        drawTiles(g2);
        drawParticles(g2);
        drawEnemies(g2);
        drawPlayer(g2);
        g2.translate(0, -HUD_HEIGHT);

        if (mode == Mode.COMBAT) {
            drawCombatOverlay(g2);
        }
        if (mode == Mode.GAME_OVER) {
            drawGameOverOverlay(g2);
        }

        drawVignette(g2);

        g2.dispose();
    }

    private void drawTiles(Graphics2D g2) {
        for (int x = 0; x < GRID_WIDTH; x++) {
            for (int y = 0; y < GRID_HEIGHT; y++) {
                if (map[x][y] == Tile.WALL) {
                    drawWallTile(g2, x, y);
                } else {
                    drawFloorTile(g2, x, y);
                }
            }
        }
    }

    private void drawFloorTile(Graphics2D g2, int x, int y) {
        int px = x * TILE_SIZE;
        int py = y * TILE_SIZE;
        float shade = floorShade[x][y];
        Color base = scaleColor(COLOR_FLOOR, shade);
        g2.setColor(base);
        g2.fillRect(px, py, TILE_SIZE, TILE_SIZE);

        Random tileRandom = new Random(tileHash(currentSeed, x, y));
        for (int i = 0; i < 5; i++) {
            float dotX = px + tileRandom.nextFloat() * TILE_SIZE;
            float dotY = py + tileRandom.nextFloat() * TILE_SIZE;
            float alpha = 0.05f + tileRandom.nextFloat() * 0.05f;
            boolean light = tileRandom.nextBoolean();
            Color dotColor = light ? new Color(255, 255, 255, (int) (alpha * 255))
                    : new Color(0, 0, 0, (int) (alpha * 255));
            g2.setColor(dotColor);
            g2.fillRect(Math.round(dotX), Math.round(dotY), 1, 1);
        }

        int decal = floorDecals[x][y];
        if (decal != DECAL_NONE) {
            CompositeState state = new CompositeState(g2);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.12f));
            g2.setColor(new Color(0x6B7285));
            int cx = px + TILE_SIZE / 2;
            int cy = py + TILE_SIZE / 2;
            switch (decal) {
                case DECAL_PLUS:
                    g2.fillRect(cx - 1, py + TILE_SIZE / 4, 2, TILE_SIZE / 2);
                    g2.fillRect(px + TILE_SIZE / 4, cy - 1, TILE_SIZE / 2, 2);
                    break;
                case DECAL_LINE:
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawLine(px + TILE_SIZE / 4, py + TILE_SIZE / 4, px + TILE_SIZE * 3 / 4, py + TILE_SIZE * 3 / 4);
                    break;
                case DECAL_DOT:
                    g2.fillOval(cx - 2, cy - 2, 4, 4);
                    break;
                default:
                    break;
            }
            state.restore();
        }
    }

    private void drawWallTile(Graphics2D g2, int x, int y) {
        int px = x * TILE_SIZE;
        int py = y * TILE_SIZE;
        g2.setColor(COLOR_WALL);
        g2.fillRect(px, py, TILE_SIZE, TILE_SIZE);

        Color lightEdge = lightenColor(COLOR_WALL, 0.18f);
        Color darkEdge = darkenColor(COLOR_WALL, 0.3f);
        g2.setColor(lightEdge);
        g2.fillRect(px, py, TILE_SIZE, 1);
        g2.fillRect(px, py, 1, TILE_SIZE);
        g2.setColor(darkEdge);
        g2.fillRect(px, py + TILE_SIZE - 1, TILE_SIZE, 1);
        g2.fillRect(px + TILE_SIZE - 1, py, 1, TILE_SIZE);
    }

    private void drawParticles(Graphics2D g2) {
        for (Particle p : particles) {
            float alpha = 1f - (p.life / p.maxLife);
            if (alpha <= 0f) {
                continue;
            }
            alpha = Math.max(0f, Math.min(1f, alpha));
            CompositeState state = new CompositeState(g2);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha * 0.8f));
            g2.setColor(p.color != null ? p.color : COLOR_PARTICLE);
            float size = p.radius * (0.5f + alpha * 0.5f);
            g2.fillOval(Math.round(p.x - size / 2f), Math.round(p.y - size / 2f), Math.round(size), Math.round(size));
            state.restore();
        }
    }

    private void drawCombatParticles(Graphics2D g2) {
        for (Particle p : combatParticles) {
            float alpha = 1f - (p.life / p.maxLife);
            if (alpha <= 0f) {
                continue;
            }
            CompositeState state = new CompositeState(g2);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha * 0.85f));
            g2.setColor(p.color != null ? p.color : COLOR_PARTICLE);
            float size = p.radius * (0.6f + alpha * 0.6f);
            g2.fillOval(Math.round(p.x - size / 2f), Math.round(p.y - size / 2f), Math.round(size), Math.round(size));
            state.restore();
        }
    }

    private void drawEnemies(Graphics2D g2) {
        for (Enemy enemy : enemies) {
            float px = enemy.tileX * TILE_SIZE;
            float py = enemy.tileY * TILE_SIZE;
            float shadowW = TILE_SIZE * 0.7f;
            float shadowH = TILE_SIZE * 0.26f;
            float shadowX = px + (TILE_SIZE - shadowW) / 2f;
            float shadowY = py + TILE_SIZE - shadowH * 1.1f;

            CompositeState shadowState = new CompositeState(g2);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.4f));
            g2.setColor(Color.BLACK);
            g2.fillOval(Math.round(shadowX), Math.round(shadowY), Math.round(shadowW), Math.round(shadowH));
            shadowState.restore();

            int bodyX = Math.round(px + TILE_SIZE * 0.25f);
            int bodyY = Math.round(py + TILE_SIZE * 0.2f);
            int bodySize = Math.round(TILE_SIZE * 0.5f);
            g2.setColor(COLOR_ENEMY);
            g2.fillRoundRect(bodyX, bodyY, bodySize, bodySize, bodySize / 3, bodySize / 3);

            GradientPaint gradient = new GradientPaint(bodyX, bodyY, lightenColor(COLOR_ENEMY, 0.25f), bodyX,
                    bodyY + bodySize, COLOR_ENEMY);
            PaintState paintState = new PaintState(g2);
            g2.setPaint(gradient);
            g2.fillRoundRect(bodyX, bodyY, bodySize, bodySize, bodySize / 3, bodySize / 3);
            paintState.restore();

            if (combat != null && combat.enemy == enemy) {
                g2.setColor(new Color(255, 255, 255, 120));
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawRoundRect(bodyX - 2, bodyY - 2, bodySize + 4, bodySize + 4, bodySize / 3, bodySize / 3);
            }
        }
    }

    private void drawPlayer(Graphics2D g2) {
        float px = player.renderX * TILE_SIZE;
        float py = player.renderY * TILE_SIZE;
        float shadowW = TILE_SIZE * 0.65f;
        float shadowH = TILE_SIZE * 0.25f;
        float shadowX = px + (TILE_SIZE - shadowW) / 2f;
        float shadowY = py + TILE_SIZE - shadowH * 1.1f;

        CompositeState state = new CompositeState(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.45f));
        g2.setColor(Color.BLACK);
        g2.fillOval(Math.round(shadowX), Math.round(shadowY), Math.round(shadowW), Math.round(shadowH));
        state.restore();

        int bodyX = Math.round(px + TILE_SIZE * 0.2f);
        int bodyY = Math.round(py + TILE_SIZE * 0.15f);
        int bodySize = Math.round(TILE_SIZE * 0.6f);
        g2.setColor(COLOR_PLAYER);
        g2.fillRoundRect(bodyX, bodyY, bodySize, bodySize, bodySize / 2, bodySize / 2);

        Color highlight = lightenColor(COLOR_PLAYER, 0.35f);
        GradientPaint gradient = new GradientPaint(bodyX, bodyY, highlight, bodyX, bodyY + bodySize, COLOR_PLAYER);
        PaintState paintState = new PaintState(g2);
        g2.setPaint(gradient);
        g2.fillRoundRect(bodyX, bodyY, bodySize, bodySize, bodySize / 2, bodySize / 2);
        paintState.restore();

        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(0x2BFFD0));
        g2.drawRoundRect(bodyX, bodyY, bodySize, bodySize, bodySize / 2, bodySize / 2);
    }

    private void drawCombatOverlay(Graphics2D g2) {
        if (combat == null) {
            return;
        }
        int width = getWidth();
        int height = getHeight();
        int dungeonHeight = height - HUD_HEIGHT;

        CompositeState dimState = new CompositeState(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.55f));
        g2.setColor(new Color(8, 10, 16, 220));
        g2.fillRect(0, HUD_HEIGHT, width, dungeonHeight);
        dimState.restore();

        CombatState state = combat;
        float combatHeight = (height - HUD_HEIGHT) * COMBAT_PANEL_HEIGHT_RATIO;
        float panelHeight = combatHeight;
        float panelWidth = width * 0.84f;
        float panelX = (width - panelWidth) / 2f;
        float panelY = height - panelHeight - 24f;

        float shakeX = 0f;
        float shakeY = 0f;
        if (state.shakeTimer > 0f) {
            float progress = state.shakeTimer / COMBAT_SHAKE_TIME;
            float magnitude = COMBAT_SHAKE_MAG * progress;
            double time = System.nanoTime() * 0.0000009;
            shakeX = (float) (Math.sin(time) * magnitude);
            shakeY = (float) (Math.cos(time * 0.8) * magnitude * 0.6f);
        }

        Graphics2D panelGraphics = (Graphics2D) g2.create();
        panelGraphics.translate(shakeX, shakeY);

        CompositeState shadowState = new CompositeState(panelGraphics);
        panelGraphics.setComposite(AlphaComposite.SrcOver.derive(0.35f));
        panelGraphics.setColor(Color.BLACK);
        panelGraphics.fillRoundRect(Math.round(panelX), Math.round(panelY + 12f), Math.round(panelWidth),
                Math.round(panelHeight), 32, 32);
        shadowState.restore();

        panelGraphics.setColor(COLOR_COMBAT_PANEL);
        panelGraphics.fillRoundRect(Math.round(panelX), Math.round(panelY), Math.round(panelWidth),
                Math.round(panelHeight), 32, 32);

        drawCombatContents(panelGraphics, state, panelX, panelY, panelWidth, panelHeight);
        drawCombatParticles(panelGraphics);

        panelGraphics.dispose();
    }

    private void drawGameOverOverlay(Graphics2D g2) {
        int width = getWidth();
        int height = getHeight();
        CompositeState dimState = new CompositeState(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.75f));
        g2.setColor(new Color(5, 6, 10, 230));
        g2.fillRect(0, 0, width, height);
        dimState.restore();

        String title = "You have fallen";
        Font titleFont = g2.getFont().deriveFont(Font.BOLD, 36f);
        g2.setFont(titleFont);
        FontMetrics titleMetrics = g2.getFontMetrics();
        g2.setColor(COLOR_SEGMENT_DANGER);
        g2.drawString(title, (width - titleMetrics.stringWidth(title)) / 2,
                height / 2 - titleMetrics.getHeight() / 2);

        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 18f));
        String prompt = "Press R to restart • Q to quit";
        FontMetrics promptMetrics = g2.getFontMetrics();
        g2.setColor(COLOR_TEXT_PRIMARY);
        g2.drawString(prompt, (width - promptMetrics.stringWidth(prompt)) / 2,
                height / 2 + promptMetrics.getAscent() + 12);
    }

    private void drawCombatContents(Graphics2D g2, CombatState state, float panelX, float panelY, float panelWidth,
            float panelHeight) {
        float[] trackBounds = computeCombatTrackBounds();
        float trackX = trackBounds[0];
        float trackY = trackBounds[1];
        float trackWidth = trackBounds[2];
        float trackHeight = trackBounds[3];

        float segmentMargin = 14f;
        float segmentHeight = trackHeight - segmentMargin * 2f;
        float segmentY = trackY + segmentMargin;

        CompositeState trackComposite = new CompositeState(g2);
        g2.setColor(COLOR_TRACK);
        g2.fillRoundRect(Math.round(trackX), Math.round(trackY), Math.round(trackWidth), Math.round(trackHeight), 32,
                32);
        g2.setStroke(new BasicStroke(3f));
        g2.setColor(COLOR_TRACK_BORDER);
        g2.drawRoundRect(Math.round(trackX), Math.round(trackY), Math.round(trackWidth), Math.round(trackHeight), 32,
                32);
        trackComposite.restore();

        for (Segment segment : state.segments) {
            float segX = trackX + segment.start * trackWidth;
            float segW = (segment.end - segment.start) * trackWidth;
            Color color;
            switch (segment.type) {
                case CRIT:
                    color = COLOR_SEGMENT_CRIT;
                    break;
                case DANGER:
                    color = COLOR_SEGMENT_DANGER;
                    break;
                case BLOCK:
                    color = COLOR_SEGMENT_BLOCK;
                    break;
                case HIT:
                default:
                    color = COLOR_SEGMENT_HIT;
                    break;
            }
            CompositeState segState = new CompositeState(g2);
            float alpha = segment.type == SegmentType.DANGER ? 0.8f : 0.9f;
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
            g2.setColor(color);
            g2.fillRoundRect(Math.round(segX), Math.round(segmentY), Math.round(Math.max(segW, 6f)),
                    Math.round(segmentHeight), 20, 20);
            segState.restore();

            g2.setStroke(new BasicStroke(1.8f));
            g2.setColor(lightenColor(color, 0.3f));
            g2.drawRoundRect(Math.round(segX), Math.round(segmentY), Math.round(Math.max(segW, 6f)),
                    Math.round(segmentHeight), 20, 20);
        }

        if (state.flashTimer > 0f) {
            float alpha = state.flashTimer / COMBAT_TRACK_FLASH_TIME;
            CompositeState flashState = new CompositeState(g2);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha * 0.35f));
            g2.setColor(COLOR_PLAYER);
            g2.fillRoundRect(Math.round(trackX), Math.round(trackY), Math.round(trackWidth), Math.round(trackHeight),
                    32, 32);
            flashState.restore();
        }

        float cursorWidth = 10f;
        float cursorHeight = trackHeight - 12f;
        float cursorX = trackX + state.cursorPos * trackWidth - cursorWidth / 2f;
        float cursorY = trackY + 6f;
        g2.setColor(COLOR_PLAYER);
        g2.fillRoundRect(Math.round(cursorX), Math.round(cursorY), Math.round(cursorWidth), Math.round(cursorHeight),
                14, 14);
        g2.setColor(new Color(255, 255, 255, 110));
        g2.setStroke(new BasicStroke(1.6f));
        g2.drawRoundRect(Math.round(cursorX), Math.round(cursorY), Math.round(cursorWidth), Math.round(cursorHeight),
                14, 14);

        float comboBarWidth = trackWidth;
        float comboBarHeight = 8f;
        float comboBarX = trackX;
        float comboBarY = trackY - 26f;
        g2.setColor(new Color(24, 27, 36));
        g2.fillRoundRect(Math.round(comboBarX), Math.round(comboBarY), Math.round(comboBarWidth),
                Math.round(comboBarHeight), 12, 12);
        float comboProgress = Math.min(1f, state.comboVisual / 12f);
        if (comboProgress > 0f) {
            g2.setColor(COLOR_ACCENT);
            g2.fillRoundRect(Math.round(comboBarX), Math.round(comboBarY), Math.round(comboBarWidth * comboProgress),
                    Math.round(comboBarHeight), 12, 12);
        }

        Font baseFont = g2.getFont();
        float comboDifference = Math.max(0f, state.comboCount - state.comboVisual);
        float comboScale = 1f + Math.min(0.35f, comboDifference * 0.12f);
        Font comboFont = baseFont.deriveFont(Font.BOLD, 18f * comboScale);
        g2.setFont(comboFont);
        String comboText = "COMBO x" + state.comboCount;
        FontMetrics comboMetrics = g2.getFontMetrics();
        g2.setColor(COLOR_TEXT_PRIMARY);
        g2.drawString(comboText, Math.round(trackX + (trackWidth - comboMetrics.stringWidth(comboText)) / 2f),
                Math.round(comboBarY - 8f));

        g2.setFont(baseFont.deriveFont(Font.PLAIN, 14f));
        String statusText;
        Color statusColor = COLOR_TEXT_SECONDARY;
        if (state.lastStrikeType == null) {
            statusText = "Ready your strike";
        } else {
            switch (state.lastStrikeType) {
                case CRIT:
                    statusText = "Critical hit! " + state.lastDamage + " dmg";
                    statusColor = COLOR_SEGMENT_CRIT;
                    break;
                case HIT:
                    statusText = "Hit for " + state.lastDamage + " dmg";
                    statusColor = COLOR_SEGMENT_HIT;
                    break;
                case BLOCK:
                    statusText = "Guarded the blow";
                    statusColor = COLOR_SEGMENT_BLOCK;
                    break;
                case DANGER:
                default:
                    statusText = "Miss! Combo broken";
                    statusColor = COLOR_SEGMENT_DANGER;
                    break;
            }
        }
        g2.setColor(statusColor);
        g2.drawString(statusText, Math.round(trackX + (trackWidth - g2.getFontMetrics().stringWidth(statusText)) / 2f),
                Math.round(trackY + trackHeight + 28f));

        float infoPadding = 32f;
        float barWidth = panelWidth - infoPadding * 2f;
        float enemyBarY = panelY + infoPadding;
        float playerBarY = enemyBarY + 36f;
        drawLabeledBar(g2, panelX + infoPadding, enemyBarY, barWidth, 18f,
                state.enemy.name + " HP", state.enemy.hp, state.enemy.maxHp, COLOR_SEGMENT_DANGER);
        drawLabeledBar(g2, panelX + infoPadding, playerBarY, barWidth, 18f,
                "Player HP", player.hp, player.maxHp, COLOR_ACCENT);

        float portraitSize = Math.min(110f, panelHeight - 92f);
        float portraitX = panelX + 36f;
        float portraitY = panelY + panelHeight - portraitSize - 32f;
        CompositeState portraitState = new CompositeState(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.7f));
        g2.setColor(new Color(24, 28, 38));
        g2.fillRoundRect(Math.round(portraitX - 6f), Math.round(portraitY - 6f), Math.round(portraitSize + 12f),
                Math.round(portraitSize + 12f), 24, 24);
        portraitState.restore();
        g2.setColor(COLOR_ENEMY);
        g2.fillRoundRect(Math.round(portraitX), Math.round(portraitY), Math.round(portraitSize),
                Math.round(portraitSize), 24, 24);
        g2.setColor(new Color(255, 255, 255, 200));
        g2.setFont(baseFont.deriveFont(Font.BOLD, 16f));
        String portraitLabel = state.enemy.name.toUpperCase();
        FontMetrics portraitMetrics = g2.getFontMetrics();
        g2.drawString(portraitLabel,
                Math.round(portraitX + (portraitSize - portraitMetrics.stringWidth(portraitLabel)) / 2f),
                Math.round(portraitY + portraitSize / 2f + portraitMetrics.getAscent() / 2f));

        g2.setFont(baseFont.deriveFont(Font.PLAIN, 13f));
        String hint = "Press SPACE/ENTER on yellow/green • Avoid red • Hit blue to block";
        FontMetrics hintMetrics = g2.getFontMetrics();
        g2.setColor(COLOR_TEXT_SECONDARY);
        g2.drawString(hint, Math.round(panelX + (panelWidth - hintMetrics.stringWidth(hint)) / 2f),
                Math.round(panelY + panelHeight - 24f));
    }

    private void drawLabeledBar(Graphics2D g2, float x, float y, float width, float height, String label, int value,
            int max, Color fillColor) {
        Font baseFont = g2.getFont();
        g2.setFont(baseFont.deriveFont(Font.PLAIN, 13f));
        g2.setColor(COLOR_TEXT_SECONDARY);
        g2.drawString(label, Math.round(x), Math.round(y - 6f));

        CompositeState shadow = new CompositeState(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.4f));
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRoundRect(Math.round(x), Math.round(y), Math.round(width), Math.round(height), 16, 16);
        shadow.restore();

        float pct = max <= 0 ? 0f : Math.max(0f, Math.min(1f, value / (float) max));
        g2.setColor(fillColor);
        g2.fillRoundRect(Math.round(x), Math.round(y), Math.round(width * pct), Math.round(height), 16, 16);

        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(255, 255, 255, 50));
        g2.drawRoundRect(Math.round(x), Math.round(y), Math.round(width), Math.round(height), 16, 16);

        g2.setFont(baseFont.deriveFont(Font.BOLD, 13f));
        String text = Math.max(0, value) + "/" + max;
        FontMetrics metrics = g2.getFontMetrics();
        g2.setColor(COLOR_TEXT_PRIMARY);
        g2.drawString(text, Math.round(x + width - metrics.stringWidth(text) - 6f),
                Math.round(y + height - 4f));
        g2.setFont(baseFont);
    }

    private void drawHUD(Graphics2D g2) {
        int width = getWidth();

        CompositeState state = new CompositeState(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.35f));
        g2.setColor(Color.BLACK);
        g2.fillRoundRect(0, 6, width, HUD_HEIGHT, 16, 16);
        state.restore();

        g2.setColor(new Color(0x141820));
        g2.fillRoundRect(0, 0, width, HUD_HEIGHT, 16, 16);

        GradientPaint strip = new GradientPaint(0, 0, lightenColor(new Color(0x141820), 0.15f), 0, 16, new Color(0x141820));
        PaintState paintState = new PaintState(g2);
        g2.setPaint(strip);
        g2.fillRoundRect(0, 0, width, 18, 16, 16);
        paintState.restore();

        int padding = 20;
        int pillHeight = 32;
        int pillArc = 20;
        Font pillFont = getFont().deriveFont(Font.BOLD, 16f);
        g2.setFont(pillFont);
        FontMetrics pillMetrics = g2.getFontMetrics();

        String hpText = "HP " + player.hp + "/" + player.maxHp;
        String stageText = "Stage 3 — Timing Combat";
        String seedText = "Seed: " + currentSeed;

        int hpWidth = pillWidth(pillMetrics, hpText);
        int stageWidth = pillWidth(pillMetrics, stageText);
        int seedWidth = pillWidth(pillMetrics, seedText);

        int hpX = padding;
        int stageX = Math.max(padding, (width - stageWidth) / 2);
        int seedX = width - padding - seedWidth;
        if (stageX < hpX + hpWidth + 8) {
            stageX = hpX + hpWidth + 8;
        }
        if (seedX < stageX + stageWidth + 8) {
            seedX = stageX + stageWidth + 8;
        }

        drawHudPill(g2, hpX, 16, hpWidth, pillHeight, pillArc, hpText, COLOR_ACCENT, COLOR_TEXT_PRIMARY, pillMetrics);
        drawHudPill(g2, stageX, 16, stageWidth, pillHeight, pillArc, stageText, new Color(0x1F232D), COLOR_TEXT_SECONDARY,
                pillMetrics);
        drawHudPill(g2, seedX, 16, seedWidth, pillHeight, pillArc, seedText, new Color(0x1A1E27), COLOR_TEXT_SECONDARY,
                pillMetrics);

        Font controlsFont = getFont().deriveFont(Font.PLAIN, 13f);
        g2.setFont(controlsFont);
        g2.setColor(COLOR_TEXT_SECONDARY);
        String controls = "Move: WASD/Arrows   •   Combat: SPACE/ENTER   •   Reroll: N   •   Reseed: S   •   Reload: F5"
                + "   •   Restart: R   •   Quit: Q";
        FontMetrics controlsMetrics = g2.getFontMetrics();
        int controlsY = HUD_HEIGHT - 14;
        g2.drawString(controls, (width - controlsMetrics.stringWidth(controls)) / 2, controlsY);
    }

    private int pillWidth(FontMetrics fm, String text) {
        return fm.stringWidth(text) + 32;
    }

    private void drawHudPill(Graphics2D g2, int x, int y, int width, int height, int arc, String text, Color background,
            Color textColor, FontMetrics fm) {
        CompositeState state = new CompositeState(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.3f));
        g2.setColor(Color.BLACK);
        g2.fillRoundRect(x, y + 4, width, height, arc, arc);
        state.restore();

        g2.setColor(background);
        g2.fillRoundRect(x, y, width, height, arc, arc);
        g2.setColor(textColor);
        int textY = y + (height - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text, x + 16, textY);
    }

    private void drawVignette(Graphics2D g2) {
        int width = getWidth();
        int height = getHeight();
        float radius = Math.max(width, height);
        float[] dist = { 0f, 1f };
        Color[] colors = { new Color(0, 0, 0, 0), new Color(0, 0, 0, 200) };
        RadialGradientPaint paint = new RadialGradientPaint(new Point2D.Float(width / 2f, height / 2f), radius, dist,
                colors);
        PaintState paintState = new PaintState(g2);
        CompositeState compositeState = new CompositeState(g2);
        g2.setPaint(paint);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.85f));
        g2.fillRect(0, 0, width, height);
        compositeState.restore();
        paintState.restore();
    }

    private long tileHash(long seed, int x, int y) {
        long h = seed;
        h ^= (long) x * 341873128712L;
        h ^= (long) y * 132897987541L;
        h *= 1099511628211L;
        return h;
    }

    private static Color scaleColor(Color base, float factor) {
        int r = clamp((int) (base.getRed() * factor));
        int g = clamp((int) (base.getGreen() * factor));
        int b = clamp((int) (base.getBlue() * factor));
        return new Color(r, g, b);
    }

    private static Color lightenColor(Color color, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int r = clamp((int) (color.getRed() + (255 - color.getRed()) * amount));
        int g = clamp((int) (color.getGreen() + (255 - color.getGreen()) * amount));
        int b = clamp((int) (color.getBlue() + (255 - color.getBlue()) * amount));
        return new Color(r, g, b);
    }

    private static Color darkenColor(Color color, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int r = clamp((int) (color.getRed() * (1f - amount)));
        int g = clamp((int) (color.getGreen() * (1f - amount)));
        int b = clamp((int) (color.getBlue() * (1f - amount)));
        return new Color(r, g, b);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float easeOut01(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * (2f - t);
    }

    private static class CompositeState {
        private final Graphics2D g2;
        private final Composite composite;

        CompositeState(Graphics2D g2) {
            this.g2 = g2;
            this.composite = g2.getComposite();
        }

        void restore() {
            g2.setComposite(composite);
        }
    }

    private static class PaintState {
        private final Graphics2D g2;
        private final Paint paint;

        PaintState(Graphics2D g2) {
            this.g2 = g2;
            this.paint = g2.getPaint();
        }

        void restore() {
            g2.setPaint(paint);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Roguelike — Stage 3 (Timing Combat Encounters)");
            Game game = new Game();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
}
