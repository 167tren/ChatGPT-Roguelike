import java.util.Objects;

public final class Relic {
    private final String id;
    private final String name;
    private final String desc;
    private final double dmgMult;
    private final double critBonusMult;
    private final double hitWiden;
    private final double critWiden;
    private final double cursorSpeedMult;
    private final int comboOnHit;
    private final int comboOnCrit;
    private final int healOnWin;
    private final boolean blockNegates;

    public Relic(
            String id,
            String name,
            String desc,
            double dmgMult,
            double critBonusMult,
            double hitWiden,
            double critWiden,
            double cursorSpeedMult,
            int comboOnHit,
            int comboOnCrit,
            int healOnWin,
            boolean blockNegates) {
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

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public double getDmgMult() {
        return dmgMult;
    }

    public double getCritBonusMult() {
        return critBonusMult;
    }

    public double getHitWiden() {
        return hitWiden;
    }

    public double getCritWiden() {
        return critWiden;
    }

    public double getCursorSpeedMult() {
        return cursorSpeedMult;
    }

    public int getComboOnHit() {
        return comboOnHit;
    }

    public int getComboOnCrit() {
        return comboOnCrit;
    }

    public int getHealOnWin() {
        return healOnWin;
    }

    public boolean isBlockNegates() {
        return blockNegates;
    }
}
