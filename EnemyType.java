public enum EnemyType {
    GRUNT(60, 10, 2, 12, 6, 40),
    SLINGER(48, 12, 1, 14, 8, 30),
    BRUTE(90, 16, 4, 9, 12, 20),
    GUARDIAN(110, 18, 6, 8, 16, 10);

    private final int baseMaxHealth;
    private final int baseAttack;
    private final int baseDefense;
    private final int baseSpeed;
    private final int baseExperience;
    private final int weight;

    EnemyType(int baseMaxHealth, int baseAttack, int baseDefense, int baseSpeed, int baseExperience, int weight) {
        this.baseMaxHealth = baseMaxHealth;
        this.baseAttack = baseAttack;
        this.baseDefense = baseDefense;
        this.baseSpeed = baseSpeed;
        this.baseExperience = baseExperience;
        this.weight = weight;
    }

    public int getBaseMaxHealth() {
        return baseMaxHealth;
    }

    public int getBaseAttack() {
        return baseAttack;
    }

    public int getBaseDefense() {
        return baseDefense;
    }

    public int getBaseSpeed() {
        return baseSpeed;
    }

    public int getBaseExperience() {
        return baseExperience;
    }

    public int getWeight() {
        return weight;
    }
}
