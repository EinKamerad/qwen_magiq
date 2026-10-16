package rorlite.game;

import rorlite.data.CharacterDef;
import rorlite.data.Team;
import rorlite.engine.Physics;
import rorlite.engine.Rng;
import rorlite.engine.Vec2;

/**
 * Базовый персонаж (игрок или враг) с физическим телом, статами и статус-эффектами.
 */
public abstract class Entity {
    public final Physics.Body body = new Physics.Body();
    public Team team = Team.NONE;
    public double hp, maxHp, armor;
    public boolean alive = true;
    public boolean onGround;
    public double facing = 1; // 1 вправо, -1 влево
    public double invulnTimer; // секунды неуязвимости
    public double deathTimer;  // для анимации смерти

    // Статусы
    public double poisonTime, poisonDps;   // яд
    public double slowTime;                // замедление
    public double stunTime;                // оглушение (не двигается)
    public double bleedTime, bleedDps;     // кровотечение (для боссов)

    // Идентификация для рендера
    public abstract String displayName();
    public abstract double radius();

    public void initStats(double maxHp, double armor) {
        this.maxHp = maxHp; this.hp = maxHp; this.armor = armor;
        body.mass = 1;
    }

    public Vec2 pos() { return body.pos; }
    public Vec2 vel() { return body.vel; }

    /** Урон с учётом брони в стиле RoR2: effectiveHealth = hp * (1 + armor/100). */
    public void takeDamage(double raw, Entity attacker, Game game) {
        if (!alive || invulnTimer > 0) return;
        // уклонение
        if (this instanceof Player p && Rng.chance(p.dodgeChance)) {
            game.spawnFloatingText(pos().x, pos().y - radius(), "УКЛОН", 0.6, 0.9, 0.9);
            return;
        }
        double mult = 100.0 / (100.0 + Math.max(0, armor));
        double dealt = raw * mult;
        hp -= dealt;
        game.spawnDamageNumber(pos(), dealt, team == Team.ENEMY);
        game.spawnHitSpark(pos().copy(), 0.95, 0.85, 0.4);
        if (hp <= 0) die(attacker, game);
    }

    public void heal(double amount) {
        hp = Math.min(maxHp, hp + amount);
    }

    public void applyPoison(double dps, double time) {
        poisonDps = Math.max(poisonDps, dps);
        poisonTime = Math.max(poisonTime, time);
    }

    public void applySlow(double time) { slowTime = Math.max(slowTime, time); }
    public void applyStun(double time) { stunTime = Math.max(stunTime, time); }

    protected abstract void die(Entity killer, Game game);

    /** Обновление статусов (яд и т.п.) — вызывается из Game. */
    public void updateStatuses(float dt, Game game) {
        if (invulnTimer > 0) invulnTimer -= dt;
        if (poisonTime > 0) {
            poisonTime -= dt;
            double dmg = poisonDps * dt;
            hp -= dmg;
            if (hp <= 0) die(null, game);
        }
        if (slowTime > 0) slowTime -= dt;
        if (stunTime > 0) stunTime -= dt;
    }

    public double speedMult() {
        double s = 1.0;
        if (slowTime > 0) s *= 0.5;
        if (stunTime > 0) s = 0;
        return s;
    }
}
