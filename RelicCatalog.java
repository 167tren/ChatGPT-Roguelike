import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class RelicCatalog {
    public static final List<Relic> ALL;

    static {
        List<Relic> relics = new ArrayList<>();
        relics.add(new Relic("sharpened_edge", "Sharpened Edge", "+12% damage to all strikes", 1.12f, 0f, 0f, 0f, 1f, 0, 0, 0,
                false));
        relics.add(new Relic("perfect_timing", "Perfect Timing", "Criticals deal +60% damage, +5% crit window", 1f, 0.6f, 0f,
                0.05f, 1f, 0, 2, 0, false));
        relics.add(new Relic("focus_charm", "Focus Charm", "Cursor slows by 10%", 1f, 0f, 0.05f, 0.05f, 0.9f, 0, 0, 0, false));
        relics.add(new Relic("second_wind", "Second Wind", "Heal 12 HP on victory", 1f, 0f, 0f, 0f, 1f, 0, 0, 12, false));
        relics.add(new Relic("guard_sigil", "Guard Sigil", "Blocks fully negate damage", 1f, 0f, 0f, 0f, 1f, 0, 0, 0, true));
        ALL = Collections.unmodifiableList(relics);
    }

    private RelicCatalog() {
    }

    public static Relic random(Random rng) {
        if (ALL.isEmpty()) {
            throw new IllegalStateException("No relics defined");
        }
        return ALL.get(rng.nextInt(ALL.size()));
    }
}
