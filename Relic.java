import java.util.Objects;

public final class Relic {
    public final String id;
    public final String name;
    public final String desc;
    public final float dmgMult;
    public final float critBonusMult;
    public final float hitWiden;
    public final float critWiden;
    public final float cursorSpeedMult;
    public final int comboOnHit;
    public final int comboOnCrit;
    public final int healOnWin;
    public final boolean blockNegates;

    public Relic(String id, String name, String desc, float dmgMult, float critBonusMult, float hitWiden, float critWiden,
            float cursorSpeedMult, int comboOnHit, int comboOnCrit, int healOnWin, boolean blockNegates) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.desc = Objects.requireNonNull(desc, "desc");
        this.dmgMult = dmgMult;
        this.critBonusMult = critBonusMult;
        this.hitWiden = hitWiden;
        this.critWiden = critWiden;
        this.cursorSpeedMult = cursorSpeedMult;
        this.comboOnHit = comboOnHit;
        this.comboOnCrit = comboOnCrit;
        this.healOnWin = healOnWin;
        this.blockNegates = blockNegates;
    }
}
