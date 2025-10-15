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

  
    private static final Color COLOR_BG = new Color(0x0D0F14);
    private static final Color COLOR_FLOOR = new Color(0x171A21);
    private static final Color COLOR_WALL = new Color(0x2B2F3A);
    private static final Color COLOR_PLAYER = new Color(0x52FFB8);
    private static final Color COLOR_PARTICLE = new Color(0xF9D66F);
    private static final Color COLOR_TEXT_PRIMARY = new Color(0xEDEFF3);
    private static final Color COLOR_TEXT_SECONDARY = new Color(0xAEB6C2);
    private static final Color COLOR_ACCENT = new Color(0x6BE675);

    private static final int PANEL_WIDTH = GRID_WIDTH * TILE_SIZE;
    private static final int PANEL_HEIGHT = GRID_HEIGHT * TILE_SIZE + HUD_HEIGHT;

    private enum Tile { WALL, FLOOR }

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
    }

    private final Tile[][] map = new Tile[GRID_WIDTH][GRID_HEIGHT];
    private final float[][] floorShade = new float[GRID_WIDTH][GRID_HEIGHT];
    private final Entity player = new Entity();
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random(1337);

    private boolean running;

    public Game() {
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setFocusable(true);
        setDoubleBuffered(true);
        setBackground(COLOR_BG);

        initMap();
        initPlayer();
        initInput();
    }

    private void initMap() {
        for (int x = 0; x < GRID_WIDTH; x++) {
            for (int y = 0; y < GRID_HEIGHT; y++) {
                if (x == 0 || y == 0 || x == GRID_WIDTH - 1 || y == GRID_HEIGHT - 1) {
                    map[x][y] = Tile.WALL;
                } else {
                    map[x][y] = Tile.FLOOR;
                }
                floorShade[x][y] = 0.92f + rng.nextFloat() * 0.16f;
            }
        }
    }

    private void initPlayer() {
        player.tileX = GRID_WIDTH / 2;
        player.tileY = GRID_HEIGHT / 2;
        player.renderX = player.tileX;
        player.renderY = player.tileY;
        player.startX = player.tileX;
        player.startY = player.tileY;
        player.targetX = player.tileX;
        player.targetY = player.tileY;
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

            @Override
            public void keyReleased(KeyEvent e) {
                // No-op, but helps consume key events on release if needed.
            }
        };
        addKeyListener(adapter);
    }

    private boolean handleKey(int keyCode) {
        if (keyCode == KeyEvent.VK_Q) {
            System.exit(0);
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
        if (x < 0 || y < 0 || x >= GRID_WIDTH || y >= GRID_HEIGHT) {
            return false;
        }
        return map[x][y] == Tile.FLOOR;
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
                elapsed = 250_000_000.0; // Clamp to avoid spiral of death.
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
            p.vy += -8f * dt;
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
        drawParticles(g2);
        drawPlayer(g2);
        g2.translate(0, -HUD_HEIGHT);

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

        Random tileRandom = new Random(x * 341873128712L ^ y * 132897987541L);
        for (int i = 0; i < 4; i++) {
            float dotX = px + tileRandom.nextFloat() * TILE_SIZE;
            float dotY = py + tileRandom.nextFloat() * TILE_SIZE;
            float alpha = 0.08f + tileRandom.nextFloat() * 0.05f;
            Color dotColor = tileRandom.nextBoolean()
                    ? new Color(255, 255, 255, (int) (alpha * 255))
                    : new Color(0, 0, 0, (int) (alpha * 255));
            g2.setColor(dotColor);
            g2.fillRect((int) dotX, (int) dotY, 1, 1);
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
            g2.setColor(COLOR_PARTICLE);
            float size = p.radius * (0.5f + alpha * 0.5f);
            g2.fillOval(Math.round(p.x - size / 2f), Math.round(p.y - size / 2f), Math.round(size), Math.round(size));
            state.restore();
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

        GradientPaint strip = new GradientPaint(0, 0, lightenColor(new Color(0x141820), 0.15f), 0, 16,
                new Color(0x141820));
        PaintState paintState = new PaintState(g2);
        g2.setPaint(strip);
        g2.fillRoundRect(0, 0, width, 18, 16, 16);
        paintState.restore();

        int padding = 20;
        int pillHeight = 32;
        int pillArc = 20;
        Font font = getFont().deriveFont(Font.BOLD, 16f);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        int x = padding;
        x = drawHudPill(g2, x, 16, pillHeight, pillArc, "HP 100/100", COLOR_ACCENT, COLOR_TEXT_PRIMARY, fm);
        x += 12;
        x = drawHudPill(g2, x, 16, pillHeight, pillArc, "Stage 1", new Color(0x1F232D), COLOR_TEXT_SECONDARY, fm);
        x += 12;
        drawHudPill(g2, x, 16, pillHeight, pillArc, "Move: WASD / Arrows   •   Quit: Q", new Color(0x1A1E27),
                COLOR_TEXT_SECONDARY, fm);
    }

    private int drawHudPill(Graphics2D g2, int x, int y, int height, int arc, String text, Color background,
            Color textColor, FontMetrics fm) {
        int textWidth = fm.stringWidth(text);
        int width = textWidth + 32;
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
        return x + width;
    }

    private void drawVignette(Graphics2D g2) {
        int width = getWidth();
        int height = getHeight();
        float radius = Math.max(width, height);
        float[] dist = { 0f, 1f };
        Color[] colors = { new Color(0, 0, 0, 0), new Color(0, 0, 0, 200) };
        RadialGradientPaint paint = new RadialGradientPaint(new Point2D.Float(width / 2f, height / 2f), radius,
                dist, colors);
        PaintState paintState = new PaintState(g2);
        CompositeState compositeState = new CompositeState(g2);
        g2.setPaint(paint);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.85f));
        g2.fillRect(0, 0, width, height);
        compositeState.restore();
        paintState.restore();
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
            JFrame frame = new JFrame("Roguelike — Stage 1 (Java 2D, Visual)");
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
