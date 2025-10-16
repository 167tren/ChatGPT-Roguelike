public class Enemy {
    public EnemyType type;
    public boolean elite;
    public int tileX;
    public int tileY;
    public int maxHp;
    public int hp;
    public int minDmg;
    public int maxDmg;
    public String name;

    public static Enemy spawn(EnemyType type, boolean elite, int x, int y, int floor) {
        Enemy enemy = new Enemy();
        enemy.type = type;
        enemy.elite = elite;
        enemy.tileX = x;
        enemy.tileY = y;

        int baseHp;
        int minDmg;
        int maxDmg;
        switch (type) {
            case ARCHER:
                baseHp = 32;
                minDmg = 6;
                maxDmg = 10;
                enemy.name = elite ? "Elite Watcher" : "Watcher";
                break;
            case SLIME:
                baseHp = 48;
                minDmg = 7;
                maxDmg = 12;
                enemy.name = elite ? "Elder Slime" : "Slime";
                break;
            case GRUNT:
            default:
                baseHp = 38;
                minDmg = 5;
                maxDmg = 9;
                enemy.name = elite ? "Elite Shade" : "Shade";
                break;
        }

        int floorIndex = Math.max(0, floor - 1);
        float hpScalar = 1f + floorIndex * GameConfig.ENEMY_HP_SCALING_PER_FLOOR;
        float dmgScalar = 1f + floorIndex * GameConfig.ENEMY_DMG_SCALING_PER_FLOOR;

        int scaledHp = Math.round(baseHp * hpScalar);
        int scaledMin = Math.round(minDmg * dmgScalar);
        int scaledMax = Math.round(maxDmg * dmgScalar);

        if (elite) {
            scaledHp = Math.round(scaledHp * 1.5f);
            scaledMin = Math.round(scaledMin * 1.25f);
            scaledMax = Math.round(scaledMax * 1.25f);
        }

        enemy.maxHp = Math.max(1, scaledHp);
        enemy.hp = enemy.maxHp;
        enemy.minDmg = Math.max(1, scaledMin);
        enemy.maxDmg = Math.max(enemy.minDmg, scaledMax);
        return enemy;
    }
}
