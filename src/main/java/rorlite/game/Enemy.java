package rorlite.game;

import rorlite.data.Team;
import rorlite.engine.Rng;
import rorlite.engine.Vec2;

/**
 * Враг: простой ИИ (преследование, прыжки через пропасти, атака в упор или стрельба).
 * Параметры задаются из EnemyDefs.
 */
public class Enemy extends Entity {
    public enum AiMode { MELEE_CHARGER, SHOOTER, FLYER, BOSS }

    public final String defId;
    public final String name;
    public double moveSpeed;
    public double attackDamage;
    public double attackInterval = 1.0;
    public double attackRange;      // дистанция атаки
    public double sightRange = 30;
    public double expValue;         // опыт за убийство
    public double moneyDrop;        // деньги с врага
    public boolean elite = false;   // элитная версия (+40% хп/урона, другой цвет)
    public AiMode ai = AiMode.MELEE_CHARGER;
    public double projSpeed = 16;
    public boolean shoots = false;

    // Внутренний ИИ
    double attackTimer;
    double thinkTimer;
    int jumpCooldown;
    double wanderDir = 1;
    public double flightPhase = Rng.d(0, Math.PI * 2);
    // Для босса: фазы
    public int bossPhase = 0;
    public double bossSpecialTimer = 5;

    public Enemy(String defId, String name, double hp, double armor, double speed,
                 double dmg, double range, double radius, Team team) {
        this.defId = defId; this.name = name;
        initStats(hp, armor);
        this.moveSpeed = speed;
        this.attackDamage = dmg;
        this.attackRange = range;
        body.hw = radius * 0.6;
        body.hh = radius;
        this.expValue = hp * 0.12;
        this.moneyDrop = 1 + Rng.i(0, 2);
    }

    @Override public String displayName() { return name + (elite ? " (Элита)" : ""); }
    @Override public double radius() { return body.hh; }

    public void makeElite() {
        elite = true;
        maxHp *= 1.4; hp = maxHp;
        armor *= 1.4;
        attackDamage *= 1.4;
        moveSpeed *= 1.1;
        expValue *= 2;
        moneyDrop *= 3;
    }

    @Override
    protected void die(Entity killer, Game game) {
        if (!alive) return;
        alive = false;
        deathTimer = 0.4;
        game.onEnemyKilled(this, killer);
    }

    /** Обновление ИИ. target — игрок. */
    public void updateAI(float dt, Player target, Game game) {
        if (!alive) return;
        attackTimer -= dt;
        Vec2 tp = target.pos();
        double dx = tp.x - pos().x;
        double dy = tp.y - pos().y;
        double dist = Math.hypot(dx, dy);
        if (dist > 0.01) facing = dx > 0 ? 1 : -1;

        double sm = speedMult();
        if (sm == 0) return; // оглушён

        switch (ai) {
            case MELEE_CHARGER -> chaseGround(dt, dx, dy, dist, target, game);
            case SHOOTER -> shooter(dt, dx, dy, dist, target, game);
            case FLYER -> flyer(dt, dx, dy, dist, target, game);
            case BOSS -> boss(dt, dx, dy, dist, target, game);
        }
    }

    private void chaseGround(float dt, double dx, double dy, double dist, Player target, Game game) {
        if (dist < sightRange) {
            // идём к игроку
            double dir = Math.signum(dx);
            game.accelerate(body, dir * moveSpeed * speedMult() * 8, dt, moveSpeed * speedMult());
            // Прыжок, если впереди пропасть или нужно подняться
            if (onGround && jumpCooldown <= 0) {
                double aheadX = pos().x + dir * (body.hw + 0.9);
                if (!game.worldSolidAt(aheadX, pos().y + body.hh + 0.3)) {
                    jump(14);
                    jumpCooldown = 40;
                } else if (dy < -2.5 && Rng.chance(0.05)) {
                    jump(16); // догнать летящего/запрыгнувшего игрока
                }
            }
            if (jumpCooldown > 0) jumpCooldown--;
            // Атака в упор
            if (dist < attackRange + target.radius() && attackTimer <= 0) {
                attackTimer = attackInterval;
                game.spawnMeleeHit(pos(), target.pos(), attackDamage, this, 1.2);
            }
        } else {
            // патрулирование
            game.accelerate(body, wanderDir * moveSpeed * 0.4 * speedMult(), dt, moveSpeed * 0.4);
            if (Rng.chance(0.01)) wanderDir = -wanderDir;
        }
    }

    private void shooter(float dt, double dx, double dy, double dist, Player target, Game game) {
        if (dist > sightRange) {
            game.accelerate(body, wanderDir * moveSpeed * 0.3, dt, moveSpeed * 0.3);
            if (Rng.chance(0.01)) wanderDir = -wanderDir;
            return;
        }
        // держим дистанцию ~attackRange*0.7
        double want = attackRange * 0.65;
        double dir = Math.abs(dx) > want ? Math.signum(dx) : -Math.signum(dx) * 0.6;
        game.accelerate(body, dir * moveSpeed * speedMult(), dt, moveSpeed * speedMult());
        if (attackTimer <= 0 && dist < attackRange * 1.4) {
            attackTimer = attackInterval;
            Vec2 from = pos().copy();
            Vec2 to = target.pos().copy();
            game.spawnProjectile(from, to, projSpeed, attackDamage, this, Team.ENEMY, false, 0);
        }
    }

    private void flyer(float dt, double dx, double dy, double dist, Player target, Game game) {
        flightPhase += dt * 3;
        if (dist < sightRange) {
            double dir = Math.signum(dx);
            game.accelerateNoCap(body, dir * moveSpeed * speedMult() * 0.8, dt);
            body.vel.y += (Math.sin(flightPhase) * 3.0 - dy * 0.15) * dt * 10;
            body.vel.y = Math.max(-8, Math.min(8, body.vel.y));
            if (attackTimer <= 0 && dist < attackRange) {
                attackTimer = attackInterval;
                game.spawnProjectile(pos().copy(), target.pos().copy(), projSpeed, attackDamage, this, Team.ENEMY, false, 0);
            }
        } else {
            body.vel.y += Math.sin(flightPhase) * 2 * dt * 6;
            game.accelerateNoCap(body, wanderDir * moveSpeed * 0.3, dt);
        }
    }

    private void boss(float dt, double dx, double dy, double dist, Player target, Game game) {
        bossSpecialTimer -= dt;
        // Фазы по здоровью
        double frac = hp / totalMaxHp();
        int phase = frac > 0.66 ? 0 : frac > 0.33 ? 1 : 2;
        if (phase != bossPhase) {
            bossPhase = phase;
            game.addLog(name + ": фаза " + (phase + 1) + "!");
            game.bossShockwave(pos(), 30 + phase * 15, this);
        }
        // базовое преследование
        double dir = Math.signum(dx);
        double spd = moveSpeed * speedMult() * (1 + bossPhase * 0.15);
        game.accelerate(body, dir * spd * 6, dt, spd);
        if (onGround && jumpCooldown <= 0 && dy < -3) { jump(16); jumpCooldown = 60; }
        if (jumpCooldown > 0) jumpCooldown--;
        // ближняя атака
        if (dist < attackRange + target.radius() && attackTimer <= 0) {
            attackTimer = attackInterval;
            game.spawnMeleeHit(pos(), target.pos(), attackDamage, this, 2.0);
            game.bossShockwave(pos(), 18, this);
        }
        // спец-атака: залп снарядов
        if (bossSpecialTimer <= 0) {
            bossSpecialTimer = 6 - bossPhase * 1.5;
            int n = 5 + bossPhase * 3;
            for (int i = 0; i < n; i++) {
                double a = Math.atan2(dy, dx) + (i - n / 2.0) * 0.22;
                Vec2 to = new Vec2(pos().x + Math.cos(a) * 10, pos().y + Math.sin(a) * 10);
                game.spawnProjectile(pos().copy(), to, projSpeed, attackDamage * 0.6, this, Team.ENEMY, false, 0);
            }
        }
    }

    public double totalMaxHp() { return maxHp; }

    public void jump(double force) { body.vel.y = force; onGround = false; }
}
