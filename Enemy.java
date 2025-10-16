public class Enemy {
    public final EnemyType type;
    public final boolean elite;
    public int tileX;
    public int tileY;

    public int maxHealth;
    public int health;
    public int attack;
    public int defense;
    public int speed;
    public int experience;

    private Enemy(EnemyType type, boolean elite, int tileX, int tileY) {
        this.type = type;
        this.elite = elite;
        this.tileX = tileX;
        this.tileY = tileY;
    }

    public static Enemy spawn(EnemyType type, boolean elite, int x, int y, int floor) {
        Enemy enemy = new Enemy(type, elite, x, y);
        int effectiveFloor = Math.max(1, floor);
        int floorOffset = effectiveFloor - 1;

        double hpMultiplier = Math.pow(GameConfig.ENEMY_FLOOR_HP_MULTIPLIER, floorOffset);
        double attackMultiplier = Math.pow(GameConfig.ENEMY_FLOOR_ATTACK_MULTIPLIER, floorOffset);
        double defenseMultiplier = Math.pow(GameConfig.ENEMY_FLOOR_DEFENSE_MULTIPLIER, floorOffset);

        enemy.maxHealth = Math.max(1, (int) Math.round(type.getBaseMaxHealth() * hpMultiplier));
        enemy.attack = Math.max(1, (int) Math.round(type.getBaseAttack() * attackMultiplier));
        enemy.defense = Math.max(0, (int) Math.round(type.getBaseDefense() * defenseMultiplier));
        enemy.speed = Math.max(1, (int) Math.round(type.getBaseSpeed() * attackMultiplier));
        enemy.experience = Math.max(1, type.getBaseExperience() + floorOffset * 2);

        if (elite) {
            enemy.maxHealth = Math.max(1, (int) Math.round(enemy.maxHealth * GameConfig.ELITE_HP_MULTIPLIER));
            enemy.attack = Math.max(1, (int) Math.round(enemy.attack * GameConfig.ELITE_ATTACK_MULTIPLIER));
            enemy.defense += GameConfig.ELITE_DEFENSE_BONUS;
            enemy.speed = Math.max(1, enemy.speed + 1);
            enemy.experience += Math.max(2, type.getBaseExperience());
        }

        enemy.health = enemy.maxHealth;
        return enemy;
    }
}
