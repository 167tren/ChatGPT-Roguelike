public class Rect {
    public final int x;
    public final int y;
    public final int w;
    public final int h;

    public Rect(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public Rect expanded(int padding) {
        return new Rect(x - padding, y - padding, w + padding * 2, h + padding * 2);
    }

    public boolean intersects(Rect other) {
        return x < other.x + other.w && x + w > other.x && y < other.y + other.h && y + h > other.y;
    }

    public int centerX() {
        return x + w / 2;
    }

    public int centerY() {
        return y + h / 2;
    }
}
