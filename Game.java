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
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    private static final Color COLOR_SANCTUARY = new Color(0x54E0B0);
    private static final Color COLOR_STAIRS = new Color(0x8DA2FF);
    private static final Color COLOR_SANCTUARY = new Color(0x2F2A55);
    private static final Color COLOR_SANCTUARY_GLOW = new Color(0x6D5BFF);
    private static final Color COLOR_STAIRS = new Color(0x3A4B4F);
    private static final Color COLOR_STAIRS_HIGHLIGHT = new Color(0x8ED0E8);
    private static final Color COLOR_ENEMY = new Color(0xD9607C);
    private static final Color COLOR_ELITE = new Color(0xF28F45);
    private static final Color COLOR_OUTLINE = new Color(0xFFD166);
    private static final Color COLOR_OVERLAY_BACK = new Color(0x141820);
    private static final Color COLOR_OVERLAY_PANEL = new Color(0x1B2332);
    private static final Color COLOR_OVERLAY_ACCENT = new Color(0x5F8BFF);

    private static final int PANEL_WIDTH = GRID_WIDTH * TILE_SIZE;
    private static final int PANEL_HEIGHT = GRID_HEIGHT * TILE_SIZE + HUD_HEIGHT;

    private static final int DECAL_NONE = -1;
    private static final int DECAL_PLUS = 0;
    private static final int DECAL_LINE = 1;
    private static final int DECAL_DOT = 2;

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

    private static class Enemy extends Entity {
        String name;
        boolean elite;
        int shardReward;
        int attackPower;
    }

    private static class RelicDefinition {
        final String id;
        final String name;
        final String description;
        final int cost;

        RelicDefinition(String id, String name, String description, int cost) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.cost = cost;
        }
    }

    private static class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float life;
        float maxLife;
        float radius;
    }

    private final float[][] floorShade = new float[GRID_WIDTH][GRID_HEIGHT];
    private final int[][] floorDecals = new int[GRID_WIDTH][GRID_HEIGHT];
    private final Dungeon dungeon = new Dungeon(GRID_WIDTH, GRID_HEIGHT, MAX_ROOMS, ROOM_MIN, ROOM_MAX, ROOM_ATTEMPTS);
    private final Entity player = new Entity();
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();
    private final List<Enemy> enemies = new ArrayList<>();

    private static final RelicDefinition[] RELIC_LIBRARY = {
            new RelicDefinition("blood-chalice", "Blood Chalice", "Increase max HP by 25.", 35),
            new RelicDefinition("glass-blade", "Glass Blade", "Attacks deal +8 damage.", 40),
            new RelicDefinition("echo-prism", "Echo Prism", "Gain +20% shards from combat.", 30),
            new RelicDefinition("ward-sigil", "Ward Sigil", "Take 4 less damage from enemies.", 30),
            new RelicDefinition("sage-bloom", "Sage's Bloom", "Heal 8 HP after each victory.", 28)
    };

    private long baseSeed;
    private long currentSeed;
    private boolean running;
    private int currentFloor = 1;
    private int shardCount;
    private final List<RelicDefinition> ownedRelics = new ArrayList<>();
    private final List<RelicDefinition> relicPool = new ArrayList<>();

    private int sanctuaryX = -1;
    private int sanctuaryY = -1;
    private int stairsX = -1;
    private int stairsY = -1;

    private boolean showSanctuaryOverlay;
    private boolean showRelicOverlay;
    private int sanctuarySelection;
    private String sanctuaryStatusText = "";

    private static final int SANCTUARY_OPTION_RELIC = 0;
    private static final int SANCTUARY_OPTION_HEAL = 1;
    private static final int SANCTUARY_OPTION_LEAVE = 2;
    private static final int SANCTUARY_RELIC_COST = 35;
    private static final int SANCTUARY_HEAL_COST = 15;
    private static final int SANCTUARY_HEAL_AMOUNT = 45;

    private final CombatManager combatManager = new CombatManager();

    public Game() {
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setFocusable(true);
        setDoubleBuffered(true);
        setBackground(COLOR_BG);

        beginRun(System.nanoTime());
        initInput();
    }

    private void generateDungeon() {
        rng.setSeed(currentSeed);
        particles.clear();
        enemies.clear();
        player.moving = false;
        player.moveTime = 0f;
        sanctuaryX = sanctuaryY = -1;
        stairsX = stairsY = -1;

        dungeon.setFloor(currentFloor);
        dungeon.generate(currentSeed);
        currentFloor = dungeon.getFloor();

        for (int x = 0; x < GRID_WIDTH; x++) {
            for (int y = 0; y < GRID_HEIGHT; y++) {
                Dungeon.TileType tile = dungeon.getTile(x, y);
                if (tile == Dungeon.TileType.WALL) {
                    floorShade[x][y] = 1f;
                    floorDecals[x][y] = DECAL_NONE;
                } else {
                    floorShade[x][y] = 0.9f + rng.nextFloat() * 0.18f;
                    if (tile == Dungeon.TileType.FLOOR && rng.nextFloat() < 0.08f) {
                        floorDecals[x][y] = rng.nextInt(3);
                    } else {
                        floorDecals[x][y] = DECAL_NONE;
                    }
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

        placeSanctuaryAndStairs();
        populateEnemies();
    }

    private void carveRoom(Rect room) {
        for (int x = room.x; x < room.x + room.w; x++) {
            for (int y = room.y; y < room.y + room.h; y++) {
                carveFloor(x, y);
            }
        }
    }

    private void placeSanctuaryAndStairs() {
        if (rooms.isEmpty()) {
            return;
        }
        Rect sanctuaryRoom = rooms.size() > 1 ? rooms.get(rng.nextInt(rooms.size() - 1) + 1) : rooms.get(0);
        Rect stairsRoom = rooms.get(rooms.size() - 1);
        if (sanctuaryRoom == rooms.get(0)) {
            sanctuaryRoom = rooms.get(rooms.size() / 2);
        }
        if (sanctuaryRoom == stairsRoom && rooms.size() > 2) {
            sanctuaryRoom = rooms.get(rooms.size() / 2);
        }
        sanctuaryX = sanctuaryRoom.centerX();
        sanctuaryY = sanctuaryRoom.centerY();
        stairsX = stairsRoom.centerX();
        stairsY = stairsRoom.centerY();
        if (sanctuaryX == stairsX && sanctuaryY == stairsY) {
            sanctuaryX = Math.min(GRID_WIDTH - 2, sanctuaryRoom.x + 1);
            sanctuaryY = Math.min(GRID_HEIGHT - 2, sanctuaryRoom.y + 1);
        }
    }

    private void populateEnemies() {
        enemies.clear();
        if (rooms.isEmpty()) {
            return;
        }
        int desired = Math.min(rooms.size(), 4 + currentFloor * 2);
        for (int i = 1; i < rooms.size(); i++) {
            if (enemies.size() >= desired) {
                break;
            }
            Rect room = rooms.get(i);
            int attempts = 0;
            while (attempts++ < 6) {
                int spawnX = room.x + 1 + rng.nextInt(Math.max(1, room.w - 2));
                int spawnY = room.y + 1 + rng.nextInt(Math.max(1, room.h - 2));
                if ((spawnX == sanctuaryX && spawnY == sanctuaryY) || (spawnX == stairsX && spawnY == stairsY)
                        || (spawnX == player.tileX && spawnY == player.tileY)) {
                    continue;
                }
                if (getEnemyAt(spawnX, spawnY) != null) {
                    continue;
                }
                if (rng.nextFloat() > 0.6f) {
                    continue;
                }
                Enemy enemy = new Enemy();
                enemy.tileX = spawnX;
                enemy.tileY = spawnY;
                enemy.renderX = spawnX;
                enemy.renderY = spawnY;
                enemy.startX = spawnX;
                enemy.startY = spawnY;
                enemy.targetX = spawnX;
                enemy.targetY = spawnY;
                enemy.moving = false;
                enemy.moveTime = 0f;
                enemy.elite = rng.nextFloat() < 0.18f + currentFloor * 0.04f;
                enemy.name = enemy.elite ? "Ascended Warden" : "Shattered Husk";
                int baseHp = 60 + currentFloor * 18;
                if (enemy.elite) {
                    baseHp += 40 + currentFloor * 6;
                }
                enemy.maxHp = baseHp;
                enemy.hp = baseHp;
                enemy.attackPower = 10 + currentFloor * 2 + (enemy.elite ? 5 : 0);
                enemy.shardReward = 12 + currentFloor * 4 + (enemy.elite ? 8 : 0);
                enemies.add(enemy);
                break;
            }
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

        Point start = dungeon.getStartPosition();
        placePlayer(start.x, start.y);
    }

    private boolean inBounds(int x, int y) {
        return dungeon.inBounds(x, y);
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
        if (showSanctuaryOverlay) {
            return handleSanctuaryOverlayInput(keyCode);
        }
        if (showRelicOverlay) {
            if (keyCode == KeyEvent.VK_R || keyCode == KeyEvent.VK_ESCAPE) {
                showRelicOverlay = false;
                return true;
            }
            return false;
        }
        if (keyCode == KeyEvent.VK_Q) {
            System.exit(0);
            return true;
        }
        if (keyCode == KeyEvent.VK_N) {
            currentFloor = 1;
            setSeed(System.nanoTime());
            generateDungeon();
            beginRun(System.nanoTime());
            return true;
        }
        if (keyCode == KeyEvent.VK_F5) {
            regenerateCurrentFloor();
            return true;
        }
        if (keyCode == KeyEvent.VK_P) {
            beginRun(DEMO_SEED);
            return true;
        }
        if (keyCode == KeyEvent.VK_R) {
            currentFloor = 1;
            setSeed(DEMO_SEED);
            generateDungeon();
            showRelicOverlay = !showRelicOverlay;
            return true;
        }
        if (keyCode == KeyEvent.VK_E) {
            if (player.tileX == sanctuaryX && player.tileY == sanctuaryY) {
                openSanctuaryOverlay();
                return true;
            }
            if (player.tileX == stairsX && player.tileY == stairsY) {
                descendStairs();
                return true;
            }
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

    private boolean tryMove(int dx, int dy) {
        int newX = player.tileX + dx;
        int newY = player.tileY + dy;
        if (!isWalkable(newX, newY)) {
            return false;
        }
        Enemy enemy = getEnemyAt(newX, newY);
        if (enemy != null) {
            boolean victory = combatManager.engage(enemy);
            if (!victory) {
                return true;
            }
        }
        if (getEnemyAt(newX, newY) != null) {
            return true;
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
        return true;
    }

    private boolean isWalkable(int x, int y) {
        return dungeon.isWalkable(x, y);
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
                elapsed = 250_000_000.0; // clamp
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

            long sleepTime = (long) Math.max(0, (frameDuration - accumulator) / 1_000_000.0);
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
        updatePlayer(dt);
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
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life += dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += -200f * dt; // gravity
            if (p.life >= p.maxLife) {
                particles.remove(i);
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
        drawEnemies(g2);
        drawParticles(g2);
        drawPlayer(g2);
        g2.translate(0, -HUD_HEIGHT);

        drawVignette(g2);

        if (showSanctuaryOverlay) {
            drawSanctuaryOverlay(g2);
        } else if (showRelicOverlay) {
            drawRelicOverlay(g2);
        }

        g2.dispose();
    }

    private void drawTiles(Graphics2D g2) {
        for (int x = 0; x < GRID_WIDTH; x++) {
            for (int y = 0; y < GRID_HEIGHT; y++) {
                Dungeon.TileType tile = dungeon.getTile(x, y);
                if (tile == Dungeon.TileType.WALL) {
                    drawWallTile(g2, x, y);
                } else {
                    drawFloorTile(g2, x, y, tile);
                }
            }
        }
    }

    private void drawFloorTile(Graphics2D g2, int x, int y, Dungeon.TileType tile) {
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

        if (tile == Dungeon.TileType.SANCTUARY) {
            CompositeState state = new CompositeState(g2);
            g2.setComposite(AlphaComposite.SrcOver.derive(GameConfig.SANCTUARY_GLOW_ALPHA));
            g2.setColor(COLOR_SANCTUARY);
            g2.fillOval(px + 4, py + 4, TILE_SIZE - 8, TILE_SIZE - 8);
            state.restore();

            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            int cx = px + TILE_SIZE / 2;
            int cy = py + TILE_SIZE / 2;
            g2.drawLine(cx, py + 6, cx, py + TILE_SIZE - 6);
            g2.drawLine(px + 6, cy, px + TILE_SIZE - 6, cy);
        } else if (tile == Dungeon.TileType.STAIRS) {
            CompositeState state = new CompositeState(g2);
            g2.setComposite(AlphaComposite.SrcOver.derive(GameConfig.STAIRS_GLOW_ALPHA));
            g2.setColor(COLOR_STAIRS);
            g2.fillRoundRect(px + 3, py + 3, TILE_SIZE - 6, TILE_SIZE - 6, 6, 6);
            state.restore();

            g2.setColor(Color.WHITE);
            int stepBase = py + TILE_SIZE - 6;
            for (int i = 0; i < 3; i++) {
                int stepY = stepBase - i * 4;
                int stepX = px + 5 + i * 2;
                int stepWidth = TILE_SIZE - 10 - i * 4;
                g2.fillRect(stepX, stepY, stepWidth, 2);
            }
            int arrowY = py + 6;
            int arrowX = px + TILE_SIZE / 2;
            g2.drawLine(arrowX, arrowY, arrowX - 4, arrowY + 6);
            g2.drawLine(arrowX, arrowY, arrowX + 4, arrowY + 6);
        }
        if (x == sanctuaryX && y == sanctuaryY) {
            drawSanctuaryTile(g2, px, py);
        } else if (x == stairsX && y == stairsY) {
            drawStairsTile(g2, px, py);
        }
    }

    private void drawSanctuaryTile(Graphics2D g2, int px, int py) {
        int size = TILE_SIZE - 6;
        int offset = 3;
        CompositeState state = new CompositeState(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.85f));
        g2.setColor(COLOR_SANCTUARY);
        g2.fillRoundRect(px + offset, py + offset, size, size, 12, 12);
        state.restore();

        GradientPaint glow = new GradientPaint(px, py, COLOR_SANCTUARY_GLOW, px, py + TILE_SIZE,
                new Color(COLOR_SANCTUARY_GLOW.getRed(), COLOR_SANCTUARY_GLOW.getGreen(), COLOR_SANCTUARY_GLOW.getBlue(), 20));
        PaintState paintState = new PaintState(g2);
        g2.setPaint(glow);
        g2.fillOval(px + offset - 4, py + offset - 4, size + 8, size + 8);
        paintState.restore();

        g2.setColor(new Color(0x8E82FF));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(px + offset, py + offset, size, size, 12, 12);
    }

    private void drawStairsTile(Graphics2D g2, int px, int py) {
        g2.setColor(COLOR_STAIRS);
        g2.fillRoundRect(px + 3, py + 3, TILE_SIZE - 6, TILE_SIZE - 6, 10, 10);
        g2.setColor(COLOR_STAIRS_HIGHLIGHT);
        int stepHeight = 4;
        for (int i = 0; i < 3; i++) {
            int stepY = py + 5 + i * (stepHeight + 2);
            g2.fillRoundRect(px + 5 + i * 2, stepY, TILE_SIZE - 10 - i * 4, stepHeight, 6, 6);
        }
        g2.setColor(new Color(0x1E2C30));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(px + 3, py + 3, TILE_SIZE - 6, TILE_SIZE - 6, 10, 10);
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
            if (alpha <= 0f) continue;
            alpha = Math.max(0f, Math.min(1f, alpha));
            CompositeState state = new CompositeState(g2);
            g2.setComposite(AlphaComposite.SrcOver.derive(alpha * 0.8f));
            g2.setColor(COLOR_PARTICLE);
            float size = p.radius * (0.5f + alpha * 0.5f);
            g2.fillOval(Math.round(p.x - size / 2f), Math.round(p.y - size / 2f),
                        Math.round(size), Math.round(size));
            state.restore();
        }
    }

    private void drawEnemies(Graphics2D g2) {
        for (Enemy enemy : enemies) {
            float ex = enemy.tileX * TILE_SIZE;
            float ey = enemy.tileY * TILE_SIZE;
            float shadowW = TILE_SIZE * 0.6f;
            float shadowH = TILE_SIZE * 0.22f;
            float shadowX = ex + (TILE_SIZE - shadowW) / 2f;
            float shadowY = ey + TILE_SIZE - shadowH * 1.1f;

            CompositeState state = new CompositeState(g2);
            g2.setComposite(AlphaComposite.SrcOver.derive(0.4f));
            g2.setColor(Color.BLACK);
            g2.fillOval(Math.round(shadowX), Math.round(shadowY), Math.round(shadowW), Math.round(shadowH));
            state.restore();

            int bodyX = Math.round(ex + TILE_SIZE * 0.2f);
            int bodyY = Math.round(ey + TILE_SIZE * 0.2f);
            int bodySize = Math.round(TILE_SIZE * 0.6f);
            Color base = enemy.elite ? COLOR_ELITE : COLOR_ENEMY;
            g2.setColor(base);
            g2.fillRoundRect(bodyX, bodyY, bodySize, bodySize, bodySize / 2, bodySize / 2);

            if (enemy.elite) {
                g2.setColor(COLOR_OUTLINE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(bodyX, bodyY, bodySize, bodySize, bodySize / 2, bodySize / 2);
            }

            float healthRatio = Math.max(0f, Math.min(1f, enemy.hp / (float) enemy.maxHp));
            int barWidth = Math.round(bodySize * healthRatio);
            int barHeight = 4;
            int barX = bodyX;
            int barY = bodyY - 6;
            g2.setColor(new Color(26, 30, 39, 180));
            g2.fillRoundRect(barX, barY, bodySize, barHeight, 4, 4);
            g2.setColor(COLOR_PARTICLE);
            g2.fillRoundRect(barX, barY, barWidth, barHeight, 4, 4);
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

        String hpText = "HP 100/100";
        String stageText = "Floor " + currentFloor + " — Stage 4 Prototype";
        String seedText = "Seed: " + currentSeed;
        String hpText = "HP " + player.hp + "/" + player.maxHp;
        String stageText = "Stage 4 — Sanctuaries & Relics";
        String floorText = "Floor " + currentFloor;
        String shardText = shardCount + " Shards";
        String seedText = "Seed " + baseSeed;

        int hpWidth = pillWidth(pillMetrics, hpText);
        int stageWidth = pillWidth(pillMetrics, stageText);
        int floorWidth = pillWidth(pillMetrics, floorText);
        int shardWidth = pillWidth(pillMetrics, shardText);
        int seedWidth = pillWidth(pillMetrics, seedText);

        int hpX = padding;
        int stageX = Math.max(padding, (width - stageWidth) / 2);
        int seedX = width - padding - seedWidth;
        int shardX = seedX - 12 - shardWidth;
        int floorX = shardX - 12 - floorWidth;
        int rightBoundary = floorX;
        if (stageX + stageWidth + 12 > rightBoundary) {
            stageX = rightBoundary - stageWidth - 12;
        }
        if (stageX < hpX + hpWidth + 12) {
            stageX = hpX + hpWidth + 12;
        }
        if (floorX < stageX + stageWidth + 12) {
            floorX = stageX + stageWidth + 12;
            shardX = floorX + floorWidth + 12;
            seedX = shardX + shardWidth + 12;
        }

        drawHudPill(g2, hpX, 16, hpWidth, pillHeight, pillArc, hpText, COLOR_ACCENT, COLOR_TEXT_PRIMARY, pillMetrics);
        drawHudPill(g2, stageX, 16, stageWidth, pillHeight, pillArc, stageText, new Color(0x1F232D), COLOR_TEXT_SECONDARY, pillMetrics);
        drawHudPill(g2, floorX, 16, floorWidth, pillHeight, pillArc, floorText, new Color(0x1A1E27), COLOR_TEXT_SECONDARY, pillMetrics);
        drawHudPill(g2, shardX, 16, shardWidth, pillHeight, pillArc, shardText, new Color(0x212835), COLOR_TEXT_PRIMARY, pillMetrics);
        drawHudPill(g2, seedX, 16, seedWidth, pillHeight, pillArc, seedText, new Color(0x161B24), COLOR_TEXT_SECONDARY, pillMetrics);

        drawRelicChips(g2, padding, HUD_HEIGHT - 46, width - padding * 2);

        Font controlsFont = getFont().deriveFont(Font.PLAIN, 13f);
        g2.setFont(controlsFont);
        g2.setColor(COLOR_TEXT_SECONDARY);
        String controls = "Move: WASD/Arrows   •   Interact: E   •   Relics: R   •   New Seed: N   •   Demo Seed: P   •   Reload: F5   •   Quit: Q";
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

    private void drawRelicChips(Graphics2D g2, int x, int y, int availableWidth) {
        int chipSize = 26;
        int spacing = 8;
        int maxVisible = Math.max(1, (availableWidth + spacing) / (chipSize + spacing));
        int shown = Math.min(ownedRelics.size(), maxVisible);
        int chipY = y;

        if (shown == 0) {
            g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            g2.setColor(COLOR_TEXT_SECONDARY);
            g2.drawString("No relics — visit sanctuaries to acquire them.", x, y + chipSize - 6);
            return;
        }

        for (int i = 0; i < shown; i++) {
            RelicDefinition relic = ownedRelics.get(i);
            int chipX = x + i * (chipSize + spacing);
            g2.setColor(new Color(0x202B3A));
            g2.fillRoundRect(chipX, chipY, chipSize, chipSize, 10, 10);
            g2.setColor(COLOR_OVERLAY_ACCENT);
            g2.drawRoundRect(chipX, chipY, chipSize, chipSize, 10, 10);
            g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
            g2.setColor(COLOR_TEXT_PRIMARY);
            String label = relic.name.substring(0, 1);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, chipX + (chipSize - fm.stringWidth(label)) / 2,
                    chipY + (chipSize + fm.getAscent()) / 2 - 4);
        }

        if (ownedRelics.size() > shown) {
            int remaining = ownedRelics.size() - shown;
            int chipX = x + shown * (chipSize + spacing);
            g2.setColor(new Color(0x202B3A));
            g2.fillRoundRect(chipX, chipY, chipSize, chipSize, 10, 10);
            g2.setColor(COLOR_OVERLAY_ACCENT);
            g2.drawRoundRect(chipX, chipY, chipSize, chipSize, 10, 10);
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g2.setColor(COLOR_TEXT_PRIMARY);
            String label = "+" + remaining;
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, chipX + (chipSize - fm.stringWidth(label)) / 2,
                    chipY + (chipSize + fm.getAscent()) / 2 - 4);
        }
    }

    private void drawVignette(Graphics2D g2) {
        int width = getWidth();
        int height = getHeight();
        float radius = Math.max(width, height);
        float[] dist = { 0f, 1f };
        Color[] colors = { new Color(0, 0, 0, 0), new Color(0, 0, 0, 200) };
        RadialGradientPaint paint = new RadialGradientPaint(new Point2D.Float(width / 2f, height / 2f), radius, dist, colors);
        PaintState paintState = new PaintState(g2);
        CompositeState compositeState = new CompositeState(g2);
        g2.setPaint(paint);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.85f));
        g2.fillRect(0, 0, width, height);
        compositeState.restore();
        paintState.restore();
    }

    private void drawSanctuaryOverlay(Graphics2D g2) {
        CompositeState state = new CompositeState(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.65f));
        g2.setColor(COLOR_OVERLAY_BACK);
        g2.fillRect(0, 0, getWidth(), getHeight());
        state.restore();

        int panelWidth = 420;
        int panelHeight = 280;
        int panelX = (getWidth() - panelWidth) / 2;
        int panelY = (getHeight() - panelHeight) / 2;

        g2.setColor(COLOR_OVERLAY_PANEL);
        g2.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 24, 24);
        g2.setColor(COLOR_OVERLAY_ACCENT);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 24, 24);

        Font titleFont = getFont().deriveFont(Font.BOLD, 22f);
        g2.setFont(titleFont);
        g2.setColor(COLOR_TEXT_PRIMARY);
        g2.drawString("Sanctuary", panelX + 32, panelY + 48);

        Font infoFont = getFont().deriveFont(Font.PLAIN, 16f);
        g2.setFont(infoFont);
        g2.setColor(COLOR_TEXT_SECONDARY);
        g2.drawString("Shards: " + shardCount, panelX + panelWidth - 140, panelY + 48);

        String relicOption = relicPool.isEmpty() ? "No relics remaining" : "Acquire Relic — " + relicCostForFloor() + " shards";
        String healOption = "Restore Health — " + healCostForFloor() + " shards";
        String leaveOption = "Leave Sanctuary";
        List<String> options = Arrays.asList(relicOption, healOption, leaveOption);

        int optionY = panelY + 96;
        int optionHeight = 44;
        for (int i = 0; i < options.size(); i++) {
            boolean selected = sanctuarySelection == i;
            int y = optionY + i * (optionHeight + 12);
            if (selected) {
                g2.setColor(new Color(0x23304A));
                g2.fillRoundRect(panelX + 28, y - 22, panelWidth - 56, optionHeight, 16, 16);
                g2.setColor(COLOR_OVERLAY_ACCENT);
                g2.drawRoundRect(panelX + 28, y - 22, panelWidth - 56, optionHeight, 16, 16);
            }

            g2.setFont(getFont().deriveFont(Font.BOLD, 17f));
            if (i == SANCTUARY_OPTION_RELIC && (relicPool.isEmpty() || shardCount < relicCostForFloor())) {
                g2.setColor(COLOR_TEXT_SECONDARY);
            } else if (i == SANCTUARY_OPTION_HEAL && (player.hp >= player.maxHp || shardCount < healCostForFloor())) {
                g2.setColor(COLOR_TEXT_SECONDARY);
            } else {
                g2.setColor(COLOR_TEXT_PRIMARY);
            }
            g2.drawString(options.get(i), panelX + 40, y);
        }

        g2.setFont(infoFont);
        g2.setColor(COLOR_TEXT_SECONDARY);
        g2.drawString("Enter: confirm   •   Esc/E: close", panelX + 32, panelY + panelHeight - 48);

        if (!sanctuaryStatusText.isEmpty()) {
            g2.setColor(COLOR_TEXT_PRIMARY);
            g2.drawString(sanctuaryStatusText, panelX + 32, panelY + panelHeight - 72);
        }
    }

    private void drawRelicOverlay(Graphics2D g2) {
        CompositeState state = new CompositeState(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.65f));
        g2.setColor(COLOR_OVERLAY_BACK);
        g2.fillRect(0, 0, getWidth(), getHeight());
        state.restore();

        int panelWidth = 460;
        int panelHeight = 320;
        int panelX = (getWidth() - panelWidth) / 2;
        int panelY = (getHeight() - panelHeight) / 2;

        g2.setColor(COLOR_OVERLAY_PANEL);
        g2.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 24, 24);
        g2.setColor(COLOR_OVERLAY_ACCENT);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 24, 24);

        g2.setFont(getFont().deriveFont(Font.BOLD, 22f));
        g2.setColor(COLOR_TEXT_PRIMARY);
        g2.drawString("Relic Archive", panelX + 32, panelY + 48);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 15f));
        g2.setColor(COLOR_TEXT_SECONDARY);
        g2.drawString("R/Esc: close", panelX + panelWidth - 140, panelY + 48);

        int listY = panelY + 92;
        int lineSpacing = 48;
        if (ownedRelics.isEmpty()) {
            g2.setColor(COLOR_TEXT_SECONDARY);
            g2.drawString("No relics collected yet. Visit sanctuaries to acquire them.", panelX + 32, listY);
            return;
        }

        for (int i = 0; i < ownedRelics.size(); i++) {
            RelicDefinition relic = ownedRelics.get(i);
            int y = listY + i * lineSpacing;
            int chipSize = 36;
            int chipX = panelX + 32;
            int chipY = y - chipSize + 12;
            g2.setColor(new Color(0x223044));
            g2.fillRoundRect(chipX, chipY, chipSize, chipSize, 12, 12);
            g2.setColor(COLOR_OVERLAY_ACCENT);
            g2.drawRoundRect(chipX, chipY, chipSize, chipSize, 12, 12);
            g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
            g2.setColor(COLOR_TEXT_PRIMARY);
            String initial = relic.name.substring(0, 1);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initial, chipX + (chipSize - fm.stringWidth(initial)) / 2, chipY + chipSize / 2 + fm.getAscent() / 2 - 4);

            g2.setFont(getFont().deriveFont(Font.BOLD, 17f));
            g2.drawString(relic.name, chipX + chipSize + 16, y - 4);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 14f));
            g2.setColor(COLOR_TEXT_SECONDARY);
            g2.drawString(relic.description, chipX + chipSize + 16, y + 16);
        }
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

    private boolean hasRelic(String id) {
        for (RelicDefinition relic : ownedRelics) {
            if (relic.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void healPlayer(int amount) {
        player.hp = Math.min(player.maxHp, player.hp + amount);
    }

    private void handlePlayerDefeat() {
        shardCount = Math.max(0, shardCount / 2);
        player.hp = player.maxHp;
        regenerateCurrentFloor();
    }

    private void spawnRewardParticles(int tileX, int tileY) {
        float originX = (tileX + 0.5f) * TILE_SIZE;
        float originY = (tileY + 0.35f) * TILE_SIZE;
        for (int i = 0; i < 12; i++) {
            Particle p = new Particle();
            float angle = (float) (rng.nextFloat() * Math.PI * 2);
            float speed = 30f + rng.nextFloat() * 50f;
            p.x = originX;
            p.y = originY;
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = (float) Math.sin(angle) * speed;
            p.life = 0f;
            p.maxLife = 0.5f + rng.nextFloat() * 0.25f;
            p.radius = 2f + rng.nextFloat() * 1.5f;
            particles.add(p);
        }
    }

    private void beginRun(long seed) {
        baseSeed = seed;
        currentFloor = 1;
        shardCount = 0;
        ownedRelics.clear();
        relicPool.clear();
        Collections.addAll(relicPool, RELIC_LIBRARY);
        recalculateDerivedStats();
        player.hp = player.maxHp;
        sanctuaryStatusText = "";
        showSanctuaryOverlay = false;
        showRelicOverlay = false;
        updateSeedForCurrentFloor();
        generateDungeon();
    }

    private void regenerateCurrentFloor() {
        showSanctuaryOverlay = false;
        showRelicOverlay = false;
        sanctuaryStatusText = "";
        sanctuarySelection = 0;
        updateSeedForCurrentFloor();
        generateDungeon();
    }

    private void updateSeedForCurrentFloor() {
        currentSeed = computeFloorSeed(baseSeed, currentFloor);
    }

    private long computeFloorSeed(long seed, int floor) {
        return seed + (long) floor * 104729L;
    }

    private void recalculateDerivedStats() {
        int maxHp = 100;
        if (hasRelic("blood-chalice")) {
            maxHp += 25;
        }
        if (hasRelic("sage-bloom")) {
            maxHp += 10;
        }
        player.maxHp = maxHp;
        player.hp = Math.min(player.hp, player.maxHp);
    }

    private void acquireRelic(RelicDefinition definition) {
        ownedRelics.add(definition);
        relicPool.remove(definition);
        recalculateDerivedStats();
    }

    private void openSanctuaryOverlay() {
        showSanctuaryOverlay = true;
        showRelicOverlay = false;
        sanctuarySelection = 0;
        sanctuaryStatusText = "";
    }

    private boolean handleSanctuaryOverlayInput(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_ESCAPE:
            case KeyEvent.VK_E:
                showSanctuaryOverlay = false;
                return true;
            case KeyEvent.VK_UP:
            case KeyEvent.VK_W:
                sanctuarySelection = (sanctuarySelection + 3 - 1) % 3;
                return true;
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_S:
                sanctuarySelection = (sanctuarySelection + 1) % 3;
                return true;
            case KeyEvent.VK_ENTER:
            case KeyEvent.VK_SPACE:
                activateSanctuarySelection();
                return true;
            default:
                return false;
        }
    }

    private void activateSanctuarySelection() {
        switch (sanctuarySelection) {
            case SANCTUARY_OPTION_RELIC:
                purchaseRelic();
                break;
            case SANCTUARY_OPTION_HEAL:
                purchaseHeal();
                break;
            case SANCTUARY_OPTION_LEAVE:
                showSanctuaryOverlay = false;
                break;
            default:
                break;
        }
    }

    private int relicCostForFloor() {
        return SANCTUARY_RELIC_COST + Math.max(0, currentFloor - 1) * 5;
    }

    private int healCostForFloor() {
        return SANCTUARY_HEAL_COST + Math.max(0, currentFloor - 1) * 3;
    }

    private void purchaseRelic() {
        if (relicPool.isEmpty()) {
            sanctuaryStatusText = "The sanctuary is out of relics.";
            return;
        }
        int cost = relicCostForFloor();
        if (shardCount < cost) {
            sanctuaryStatusText = "Need " + (cost - shardCount) + " more shards.";
            return;
        }
        RelicDefinition definition = relicPool.get(rng.nextInt(relicPool.size()));
        shardCount -= cost;
        acquireRelic(definition);
        sanctuaryStatusText = "You received " + definition.name + "!";
    }

    private void purchaseHeal() {
        if (player.hp >= player.maxHp) {
            sanctuaryStatusText = "Already at full strength.";
            return;
        }
        int cost = healCostForFloor();
        if (shardCount < cost) {
            sanctuaryStatusText = "Need " + (cost - shardCount) + " more shards.";
            return;
        }
        shardCount -= cost;
        int missing = player.maxHp - player.hp;
        int healAmount = Math.min(missing, SANCTUARY_HEAL_AMOUNT + currentFloor * 5);
        healPlayer(healAmount);
        sanctuaryStatusText = "Recovered " + healAmount + " HP.";
    }

    private void descendStairs() {
        currentFloor++;
        showSanctuaryOverlay = false;
        showRelicOverlay = false;
        sanctuaryStatusText = "";
        sanctuarySelection = 0;
        updateSeedForCurrentFloor();
        generateDungeon();
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

    private class CombatManager {
        boolean engage(Enemy enemy) {
            if (enemy == null) {
                return false;
            }
            int enemyHp = enemy.hp;
            int playerHp = player.hp;

            while (enemyHp > 0 && playerHp > 0) {
                enemyHp -= Math.max(1, computePlayerDamage());
                if (enemyHp <= 0) {
                    break;
                }
                playerHp -= Math.max(0, computeEnemyDamage(enemy));
            }

            player.hp = Math.max(0, playerHp);
            enemy.hp = Math.max(0, enemyHp);

            if (player.hp <= 0) {
                handlePlayerDefeat();
                return false;
            }

            handleEnemyDefeat(enemy);
            return true;
        }

        private int computePlayerDamage() {
            int base = 18 + currentFloor * 2 + rng.nextInt(6);
            if (hasRelic("glass-blade")) {
                base += 8;
            }
            if (hasRelic("blood-chalice")) {
                base += 2;
            }
            return base;
        }

        private int computeEnemyDamage(Enemy enemy) {
            int base = enemy.attackPower + rng.nextInt(4);
            if (enemy.elite) {
                base += 4;
            }
            if (hasRelic("ward-sigil")) {
                base = Math.max(0, base - 4);
            }
            return base;
        }

        private void handleEnemyDefeat(Enemy enemy) {
            enemies.remove(enemy);
            int reward = enemy.shardReward;
            if (hasRelic("echo-prism")) {
                reward = Math.round(reward * 1.2f);
            }
            shardCount += reward;
            if (hasRelic("sage-bloom")) {
                healPlayer(8);
            }
            spawnRewardParticles(enemy.tileX, enemy.tileY);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Roguelike — Stage 4 (Sanctuaries & Relics)");
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
