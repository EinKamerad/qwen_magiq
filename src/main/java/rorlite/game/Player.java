package rorlite.game;

import rorlite.data.CharacterDef;
import rorlite.data.Item;
import rorlite.data.Team;
import rorlite.engine.Rng;

import java.util.LinkedHashMap;
import java.util.Map;

/** Игрок: персонаж из ростера, инвентарь предметов, способности с кулдаунами. */
public class Player extends Entity {
    public final CharacterDef def;
    /** Инвентарь: id предмета -> количество стаков. */
    public final Map<String, Integer> inventory = new LinkedHashMap<>();
    public double money = 0;          // «лунные монеты» (вместо Money в RoR2 — упрощённо)
    public double moveSpeedMult = 1.0;
    public double damageMult = 1.0;
    public double attackSpeedMult = 1.0;
    public double dodgeChance = 0.0;
    public double regenPerSec = 0.0;
    public boolean hasJetpack = false;
    public int jumpsLeft = 1;         // двойной прыжок
    public double warhammerTimer = 99; // готовность «Молота войны»
    public int hitsForSpore = 0;      // счётчик попаданий для спор
    public double specialActiveTimer = 0; // эффекты типа «Кровавая баня»
    public String specialActiveId = null;

    // Кулдауны умений: slot -> оставшееся время
    public final double[] cooldowns = new double[4];
    // Таймер атаки
    public double attackTimer = 0;

    // Статы, растущие от предметов/убийств
    public double bonusHealth = 0;

    public Player(CharacterDef def) {
        this.def = def;
        team = Team.PLAYER;
        initStats(def.maxHealth, def.armor);
        body.hw = def.bodyRadius * 0.6;
        body.hh = def.bodyRadius;
        body.mass = def.mass;
        body.dragOnGround = 14;
    }

    @Override public String displayName() { return def.name; }
    @Override public double radius() { return def.bodyRadius; }

    public double totalMaxHp() { return maxHp + bonusHealth; }

    /** Эффективная скорость передвижения с учётом предметов и статусов. */
    public double speed() {
        return def.baseSpeed * moveSpeedMult * speedMult();
    }

    /** Урон основной атаки с учётом предметов. */
    public double attackDamage() {
        double base = def.attackDamage * damageMult;
        if (warhammerTimer <= 0) { base *= 3; warhammerTimer = 3.0 / Math.max(attackSpeedMult, 0.5); }
        return base;
    }

    public double attackInterval() {
        return 1.0 / (def.attackRate * attackSpeedMult);
    }

    public void addItem(Item item, Game game) {
        inventory.merge(item.id, 1, Integer::sum);
        game.applyItem(this, item);
        game.addLog("Получен предмет: " + item.name + " x" + inventory.get(item.id));
    }

    public int stackCount(String id) { return inventory.getOrDefault(id, 0); }

    @Override
    protected void die(Entity killer, Game game) {
        if (!alive) return;
        alive = false;
        game.onPlayerDeath();
    }

    /** Обновление таймеров игрока. */
    public void update(float dt, Game game) {
        for (int i = 0; i < 4; i++) if (cooldowns[i] > 0) cooldowns[i] -= dt;
        if (attackTimer > 0) attackTimer -= dt;
        if (warhammerTimer > 0) warhammerTimer -= dt;
        if (regenPerSec > 0 && hp < totalMaxHp()) heal(regenPerSec * dt);
        if (specialActiveTimer > 0) {
            specialActiveTimer -= dt;
            if (specialActiveTimer <= 0) specialActiveId = null;
        }
    }

    public void triggerCooldown(int slot) {
        cooldowns[slot] = abilityDef(slot).cooldown / Math.max(0.5, cooldownReductionMult());
    }

    private double cooldownReductionMult() {
        // Кварц ускоряет перезарядку
        return attackSpeedMult;
    }

    public CharacterDef.AbilityDef abilityDef(int slot) {
        switch (slot) {
            case 0: return def.primary;
            case 1: return def.secondary;
            case 2: return def.utility;
            default: return def.special;
        }
    }

    public boolean canCast(int slot) { return cooldowns[slot] <= 0; }
}
