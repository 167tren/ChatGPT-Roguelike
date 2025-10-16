import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CombatManager {
    public interface Listener {
        void onVictory(Enemy enemy, VictoryReward reward);

        void onDefeat();
    }

    public interface Effects {
        void onStrike(SegmentType type, float cursorPosition);

        void onSegmentEffect(CombatSegment segment, SegmentType type);
    }

    public static final class VictoryReward {
        public final int shardsEarned;
        public final List<Relic> newRelics;

        public VictoryReward(int shardsEarned, List<Relic> newRelics) {
            this.shardsEarned = shardsEarned;
            this.newRelics = newRelics;
        }
    }

    private final Random rng;
    private final Entity player;
    private final Listener listener;
    private final Effects effects;
    private CombatState state;

    public CombatManager(Random rng, Entity player, Listener listener, Effects effects) {
        this.rng = rng;
        this.player = player;
        this.listener = listener;
        this.effects = effects;
    }

    public void beginCombat(Enemy enemy, List<Relic> relics, int shards, int floor) {
        state = new CombatState();
        state.enemy = enemy;
        state.comboCount = 0;
        state.comboVisual = 0f;
        state.cursorPos = 0f;
        state.cursorForward = true;
        state.relics = relics;
        state.shards = shards;
        state.floor = Math.max(1, floor);
        Profile profile = profileFor(enemy);
        float floorScalar = 1f + Math.max(0, state.floor - 1) * GameConfig.CURSOR_SPEED_SCALING_PER_FLOOR;
        float relicSpeed = relicCursorSpeedMultiplier();
        float eliteScalar = enemy.elite ? 1.08f : 1f;
        state.cursorBaseSpeed = GameConfig.COMBAT_CURSOR_BASE_SPEED * profile.cursorScalar * floorScalar * relicSpeed
                * eliteScalar;
        state.cursorSpeed = state.cursorBaseSpeed;
        state.roundIndex = 0;
        state.lastStrikeType = null;
        state.lastDamage = 0;
        state.flashTimer = 0f;
        state.shakeTimer = 0f;
        state.blockedThisRound = false;
        setupCombatRound(true);
    }

    public void clear() {
        state = null;
    }

    public boolean isActive() {
        return state != null;
    }

    public CombatState getState() {
        return state;
    }

    public void update(float dt) {
        if (state == null) {
            return;
        }
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

        if (!state.inputProcessed && state.roundElapsed >= GameConfig.COMBAT_ROUND_TIMEOUT) {
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
            if (state.postInputTimer >= GameConfig.COMBAT_POST_INPUT_DELAY) {
                resolveEnemyTurn();
                if (state == null) {
                    return;
                }
            }
        }

        state.comboVisual += (state.comboCount - state.comboVisual) * Math.min(1f, dt * 6f);
    }

    public void processInput() {
        if (state == null || state.inputProcessed) {
            return;
        }
        CombatSegment segment = findSegmentAt(state.cursorPos);
        if (segment == null) {
            registerCombatMiss(true);
            return;
        }
        switch (segment.type) {
            case DANGER:
                registerCombatMiss(true);
                return;
            case BLOCK:
                handleBlock(segment);
                return;
            case HIT:
            case CRIT:
                resolvePlayerStrike(segment.type);
                return;
            default:
                break;
        }
    }

    private void handleBlock(CombatSegment segment) {
        state.blockedThisRound = true;
        state.lastStrikeType = SegmentType.BLOCK;
        state.lastDamage = 0;
        state.inputProcessed = true;
        state.awaitingEnemyTurn = true;
        state.postInputTimer = 0f;
        state.roundElapsed = 0f;
        if (effects != null) {
            effects.onSegmentEffect(segment, SegmentType.BLOCK);
        }
    }

    private void resolvePlayerStrike(SegmentType type) {
        int base = GameConfig.PLAYER_MIN_DAMAGE
                + rng.nextInt(GameConfig.PLAYER_MAX_DAMAGE - GameConfig.PLAYER_MIN_DAMAGE + 1);
        int comboTier = state.comboCount / 3;
        float multiplier = 1f + comboTier * GameConfig.PLAYER_COMBO_STEP;
        multiplier *= relicDamageMultiplier();
        if (type == SegmentType.CRIT) {
            multiplier += 0.5f;
            multiplier += relicCritBonus();
        }
        int damage = Math.max(1, Math.round(base * multiplier));
        state.enemy.hp = Math.max(0, state.enemy.hp - damage);
        state.lastDamage = damage;
        state.lastStrikeType = type;
        int comboGain = type == SegmentType.CRIT ? 2 + relicComboBonusOnCrit() : 1 + relicComboBonusOnHit();
        state.comboCount += Math.max(0, comboGain);
        state.flashTimer = GameConfig.COMBAT_TRACK_FLASH_TIME;
        if (effects != null) {
            effects.onStrike(type, state.cursorPos);
        }
        state.inputProcessed = true;
        state.roundElapsed = 0f;
        if (state.enemy.hp <= 0) {
            state.awaitingEnemyTurn = false;
            grantVictoryRewards();
            return;
        }
        state.awaitingEnemyTurn = true;
        state.postInputTimer = 0f;
    }

    private void registerCombatMiss(boolean fromInput) {
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
            state.shakeTimer = GameConfig.COMBAT_SHAKE_TIME;
        }
    }

    private void resolveEnemyTurn() {
        if (state == null) {
            return;
        }
        state.awaitingEnemyTurn = false;
        state.postInputTimer = 0f;

        if (state.enemy.hp <= 0) {
            grantVictoryRewards();
            return;
        }

        int damage = state.enemy.minDmg + rng.nextInt(state.enemy.maxDmg - state.enemy.minDmg + 1);
        if (state.blockedThisRound) {
            damage = Math.round(damage * GameConfig.BLOCK_DAMAGE_REDUCTION);
            if (hasBlockNegateRelic()) {
                damage = 0;
            }
        }
        state.blockedThisRound = false;
        if (damage > 0) {
            player.hp = Math.max(0, player.hp - damage);
            state.shakeTimer = Math.max(state.shakeTimer, GameConfig.COMBAT_SHAKE_TIME * 0.8f);
        }

        if (player.hp <= 0) {
            notifyDefeat();
            return;
        }

        state.roundIndex++;
        setupCombatRound(false);
    }

    private void notifyVictory(VictoryReward reward) {
        if (listener != null && state != null) {
            listener.onVictory(state.enemy, reward);
        }
        state = null;
    }

    private void notifyDefeat() {
        if (listener != null && state != null) {
            listener.onDefeat();
        }
        state = null;
    }

    private void setupCombatRound(boolean resetSpeed) {
        if (state == null) {
            return;
        }
        if (resetSpeed) {
            state.cursorSpeed = Math.max(0.2f, state.cursorBaseSpeed);
        } else {
            state.cursorSpeed = Math.max(0.2f, state.cursorSpeed * GameConfig.COMBAT_CURSOR_SPEED_GROWTH);
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
        generateCombatSegments();
    }

    private void generateCombatSegments() {
        if (state == null) {
            return;
        }
        state.segments.clear();
        Profile profile = profileFor(state.enemy);
        boolean needHit = true;
        boolean hasCrit = false;
        boolean requireBlock = profile.blockFrequency > 0 && ((state.roundIndex + 1) % profile.blockFrequency) == 0;
        float desiredDanger = profile.dangerMin + rng.nextFloat() * (profile.dangerMax - profile.dangerMin);
        float dangerCoverage = 0f;
        int targetSegments = profile.minSegments
                + rng.nextInt(Math.max(1, profile.maxSegments - profile.minSegments + 1));
        float hitWidthScale = relicHitWidthMultiplier();
        float critWidthScale = relicCritWidthMultiplier();

        int attempts = 0;
        while ((state.segments.size() < targetSegments || needHit || requireBlock) && attempts < 240) {
            attempts++;
            SegmentType type;
            if (requireBlock) {
                type = SegmentType.BLOCK;
                requireBlock = false;
            } else if (needHit) {
                type = SegmentType.HIT;
                needHit = false;
            } else {
                float roll = rng.nextFloat();
                if (!hasCrit && roll < profile.critChance) {
                    type = SegmentType.CRIT;
                    hasCrit = true;
                } else if (dangerCoverage < desiredDanger && roll < 0.6f) {
                    type = SegmentType.DANGER;
                } else {
                    type = SegmentType.HIT;
                }
            }

            float length = randomSegmentLength(type, profile, hitWidthScale, critWidthScale);
            float start = rng.nextFloat() * Math.max(0.01f, 1f - length);
            float end = Math.min(1f, start + length);

            if (type == SegmentType.DANGER) {
                dangerCoverage += end - start;
            }
            if (!isSegmentPlacementValid(start, end)) {
                continue;
            }
            CombatSegment segment = new CombatSegment();
            segment.start = start;
            segment.end = end;
            segment.type = type;
            state.segments.add(segment);
        }

        if (state.segments.isEmpty()) {
            CombatSegment defaultSegment = new CombatSegment();
            defaultSegment.start = 0.4f;
            defaultSegment.end = 0.6f;
            defaultSegment.type = SegmentType.HIT;
            state.segments.add(defaultSegment);
        }

        state.segments.sort((a, b) -> Float.compare(a.start, b.start));

        if (requireBlock) {
            CombatSegment block = new CombatSegment();
            block.type = SegmentType.BLOCK;
            block.start = 0.45f;
            block.end = 0.55f;
            if (isSegmentPlacementValid(block.start, block.end)) {
                state.segments.add(block);
                state.segments.sort((a, b) -> Float.compare(a.start, b.start));
            }
        }
    }

    private boolean isSegmentPlacementValid(float start, float end) {
        for (CombatSegment existing : state.segments) {
            if (!(end <= existing.start || start >= existing.end)) {
                return false;
            }
        }
        return true;
    }

    private float randomSegmentLength(SegmentType type, Profile profile, float hitWidthScale, float critWidthScale) {
        switch (type) {
            case CRIT:
                return clamp((profile.critMinWidth + rng.nextFloat() * (profile.critMaxWidth - profile.critMinWidth))
                        * critWidthScale, 0.04f, 0.5f);
            case HIT:
                return clamp((profile.hitMinWidth + rng.nextFloat() * (profile.hitMaxWidth - profile.hitMinWidth))
                        * hitWidthScale, 0.04f, 0.5f);
            case DANGER:
                return clamp(profile.dangerMinWidth
                        + rng.nextFloat() * (profile.dangerMaxWidth - profile.dangerMinWidth), 0.04f, 0.4f);
            case BLOCK:
            default:
                return clamp(0.08f + rng.nextFloat() * 0.05f, 0.06f, 0.18f);
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private Profile profileFor(Enemy enemy) {
        Profile profile = new Profile();
        switch (enemy.type != null ? enemy.type : EnemyType.GRUNT) {
            case ARCHER:
                profile.minSegments = 4;
                profile.maxSegments = 6;
                profile.dangerMin = 0.22f;
                profile.dangerMax = 0.30f;
                profile.critChance = 0.2f;
                profile.cursorScalar = 1.1f;
                profile.blockFrequency = 2;
                profile.hitMinWidth = 0.06f;
                profile.hitMaxWidth = 0.12f;
                profile.critMinWidth = 0.06f;
                profile.critMaxWidth = 0.10f;
                profile.dangerMinWidth = 0.06f;
                profile.dangerMaxWidth = 0.14f;
                break;
            case SLIME:
                profile.minSegments = 3;
                profile.maxSegments = 4;
                profile.dangerMin = 0.12f;
                profile.dangerMax = 0.18f;
                profile.critChance = 0.3f;
                profile.cursorScalar = 0.9f;
                profile.blockFrequency = 4;
                profile.hitMinWidth = 0.12f;
                profile.hitMaxWidth = 0.22f;
                profile.critMinWidth = 0.14f;
                profile.critMaxWidth = 0.24f;
                profile.dangerMinWidth = 0.05f;
                profile.dangerMaxWidth = 0.10f;
                break;
            case GRUNT:
            default:
                profile.minSegments = 3;
                profile.maxSegments = 5;
                profile.dangerMin = 0.16f;
                profile.dangerMax = 0.22f;
                profile.critChance = 0.25f;
                profile.cursorScalar = 1f;
                profile.blockFrequency = 3;
                profile.hitMinWidth = 0.08f;
                profile.hitMaxWidth = 0.18f;
                profile.critMinWidth = 0.08f;
                profile.critMaxWidth = 0.16f;
                profile.dangerMinWidth = 0.05f;
                profile.dangerMaxWidth = 0.12f;
                break;
        }
        return profile;
    }

    private float relicHitWidthMultiplier() {
        float scale = 1f;
        for (Relic relic : activeRelics()) {
            scale *= 1f + relic.hitWiden;
        }
        return scale;
    }

    private float relicCritWidthMultiplier() {
        float scale = 1f;
        for (Relic relic : activeRelics()) {
            scale *= 1f + relic.critWiden;
        }
        return scale;
    }

    private float relicCursorSpeedMultiplier() {
        float scale = 1f;
        for (Relic relic : activeRelics()) {
            scale *= relic.cursorSpeedMult;
        }
        return scale;
    }

    private float relicDamageMultiplier() {
        float scale = 1f;
        for (Relic relic : activeRelics()) {
            scale *= relic.dmgMult;
        }
        return scale;
    }

    private float relicCritBonus() {
        float bonus = 0f;
        for (Relic relic : activeRelics()) {
            bonus += relic.critBonusMult;
        }
        return bonus;
    }

    private int relicComboBonusOnHit() {
        int bonus = 0;
        for (Relic relic : activeRelics()) {
            bonus += relic.comboOnHit;
        }
        return bonus;
    }

    private int relicComboBonusOnCrit() {
        int bonus = 0;
        for (Relic relic : activeRelics()) {
            bonus += relic.comboOnCrit;
        }
        return bonus;
    }

    private int relicHealOnWin() {
        int heal = 0;
        for (Relic relic : activeRelics()) {
            heal += relic.healOnWin;
        }
        return heal;
    }

    private boolean hasBlockNegateRelic() {
        for (Relic relic : activeRelics()) {
            if (relic.blockNegates) {
                return true;
            }
        }
        return false;
    }

    private List<Relic> activeRelics() {
        if (state == null) {
            return Collections.emptyList();
        }
        if (state.relics == null) {
            throw new IllegalStateException("Combat state missing relic inventory");
        }
        return state.relics;
    }

    private void grantVictoryRewards() {
        if (state == null) {
            return;
        }
        int shardsBefore = state.shards;
        int gain = state.enemy.elite ? GameConfig.SHARD_PER_ELITE : GameConfig.SHARD_PER_WIN;
        state.shards += gain;

        List<Relic> newRelics = new ArrayList<>();
        float baseChance = GameConfig.RELIC_DROP_BASE
                + (state.floor - 1) * GameConfig.RELIC_DROP_FLOOR_BONUS
                + (state.enemy.elite ? GameConfig.RELIC_DROP_ELITE_BONUS : 0f);
        float dropChance = Math.max(0f, Math.min(0.95f, baseChance));
        if (rng.nextFloat() < dropChance) {
            Relic relic = RelicCatalog.random(rng);
            activeRelics().add(relic);
            newRelics.add(relic);
        }

        int heal = relicHealOnWin();
        if (heal > 0) {
            player.hp = Math.min(player.maxHp, player.hp + heal);
        }

        VictoryReward reward = new VictoryReward(state.shards - shardsBefore, newRelics);
        notifyVictory(reward);
    }

    private static final class Profile {
        int minSegments;
        int maxSegments;
        float dangerMin;
        float dangerMax;
        float critChance;
        float cursorScalar;
        int blockFrequency;
        float hitMinWidth;
        float hitMaxWidth;
        float critMinWidth;
        float critMaxWidth;
        float dangerMinWidth;
        float dangerMaxWidth;
    }

    private CombatSegment findSegmentAt(float position) {
        if (state == null) {
            return null;
        }
        for (CombatSegment segment : state.segments) {
            if (position >= segment.start && position <= segment.end) {
                return segment;
            }
        }
        return null;
    }
}
