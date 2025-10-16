import java.util.ArrayList;
import java.util.List;

public class CombatState {
    public Enemy enemy;
    public float cursorPos;
    public boolean cursorForward = true;
    public float cursorSpeed = GameConfig.COMBAT_CURSOR_BASE_SPEED;
    public float cursorBaseSpeed = GameConfig.COMBAT_CURSOR_BASE_SPEED;
    public final List<CombatSegment> segments = new ArrayList<>();
    public boolean blockedThisRound;
    public int roundIndex;
    public int comboCount;
    public float comboVisual;
    public boolean inputProcessed;
    public float roundElapsed;
    public boolean awaitingEnemyTurn;
    public float postInputTimer;
    public float flashTimer;
    public float shakeTimer;
    public int lastDamage;
    public SegmentType lastStrikeType;
    public List<Relic> relics;
    public int shards;
    public int floor = 1;
}
