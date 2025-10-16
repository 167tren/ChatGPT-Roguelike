import java.util.List;
import java.util.Random;

public final class RelicCatalog {
    public static final List<Relic> ALL = List.of(
            new Relic(
                    "starter_blade",
                    "Traveler's Blade",
                    "A balanced starter relic that keeps damage steady.",
                    1.10,
                    1.00,
                    0.0,
                    0.0,
                    1.00,
                    0,
                    0,
                    0,
                    false),
            new Relic(
                    "lucky_needle",
                    "Lucky Needle",
                    "A curious pin that nudges your crits just a bit higher.",
                    1.00,
                    1.25,
                    0.0,
                    0.05,
                    1.00,
                    0,
                    1,
                    0,
                    false),
            new Relic(
                    "dancers_wraps",
                    "Dancer's Wraps",
                    "Silk wraps that hasten your strikes and widen swings.",
                    1.00,
                    1.00,
                    0.15,
                    0.0,
                    1.15,
                    1,
                    0,
                    0,
                    false),
            new Relic(
                    "veterans_medal",
                    "Veteran's Medal",
                    "A medal that grants resilience after every victory.",
                    1.05,
                    1.00,
                    0.0,
                    0.0,
                    1.00,
                    0,
                    0,
                    10,
                    false),
            new Relic(
                    "sentinel_plate",
                    "Sentinel Plate",
                    "Heavy plating that lets a block shrug off a strike.",
                    0.95,
                    1.00,
                    0.0,
                    0.0,
                    0.95,
                    0,
                    0,
                    0,
                    true));

    private RelicCatalog() {
        throw new AssertionError("No instances");
    }

    public static Relic random(Random rng) {
        if (ALL.isEmpty()) {
            throw new IllegalStateException("No relics configured");
        }
        return ALL.get(rng.nextInt(ALL.size()));
    }
}
