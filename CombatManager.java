import java.util.Random;

public class CombatManager {
    public interface Listener {
        void onVictory(Enemy enemy);

        void onDefeat();
    }

    public interface Effects {
        void onStrike(SegmentType type, float cursorPosition);

        void onSegmentEffect(CombatSegment segment, SegmentType type);
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

    public void beginCombat(Enemy enemy) {
        state = new CombatState();
        state.enemy = enemy;
        state.comboCount = 0;
        state.comboVisual = 0f;
        state.cursorPos = 0f;
        state.cursorForward = true;
        state.cursorSpeed = GameConfig.COMBAT_CURSOR_BASE_SPEED;
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
        if (type == SegmentType.CRIT) {
            multiplier += 0.5f;
        }
        int damage = Math.max(1, Math.round(base * multiplier));
        state.enemy.hp = Math.max(0, state.enemy.hp - damage);
        state.lastDamage = damage;
        state.lastStrikeType = type;
        state.comboCount += type == SegmentType.CRIT ? 2 : 1;
        state.flashTimer = GameConfig.COMBAT_TRACK_FLASH_TIME;
        if (effects != null) {
            effects.onStrike(type, state.cursorPos);
        }
        state.inputProcessed = true;
        state.roundElapsed = 0f;
        if (state.enemy.hp <= 0) {
            state.awaitingEnemyTurn = false;
            notifyVictory();
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
            notifyVictory();
            return;
        }

        int damage = state.enemy.minDmg + rng.nextInt(state.enemy.maxDmg - state.enemy.minDmg + 1);
        if (state.blockedThisRound) {
            damage = Math.round(damage * GameConfig.BLOCK_DAMAGE_REDUCTION);
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

    private void notifyVictory() {
        if (listener != null && state != null) {
            Enemy enemy = state.enemy;
            listener.onVictory(enemy);
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
        if (!resetSpeed) {
            state.cursorSpeed *= GameConfig.COMBAT_CURSOR_SPEED_GROWTH;
        } else {
            state.cursorSpeed = GameConfig.COMBAT_CURSOR_BASE_SPEED;
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
                requireBlock = false;
            } else if (needHit) {
                type = SegmentType.HIT;
                needHit = false;
            } else {
                float roll = rng.nextFloat();
                if (!hasCrit && roll < 0.25f) {
                    type = SegmentType.CRIT;
                    hasCrit = true;
                } else if (dangerCoverage < desiredDanger && roll < 0.55f) {
                    type = SegmentType.DANGER;
                } else {
                    type = SegmentType.HIT;
                }
            }
            float length = 0.08f + rng.nextFloat() * 0.25f;
            float start = rng.nextFloat() * (1f - length);
            float end = start + length;
            if (type == SegmentType.DANGER) {
                dangerCoverage += length;
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
        state.segments.sort((a, b) -> Float.compare(a.start, b.start));
        if (needHit && !state.segments.isEmpty()) {
            state.segments.get(0).type = SegmentType.HIT;
        }
        if (requireBlock) {
            CombatSegment segment = new CombatSegment();
            segment.start = 0.45f;
            segment.end = 0.55f;
            segment.type = SegmentType.BLOCK;
            state.segments.add(segment);
            state.segments.sort((a, b) -> Float.compare(a.start, b.start));
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
