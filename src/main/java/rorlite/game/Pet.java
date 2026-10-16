package rorlite.game;

import rorlite.data.Team;
import rorlite.engine.Vec2;

/** Питомец игрока (турель, мина) — стреляет по врагам. */
public class Pet extends Entity {
    public enum Kind { TURRET, MEGA_TURRET, MINE }

    public final Kind kind;
    public double lifeTime = 12;      // турели живут ограниченное время
    public double fireTimer = 0;
    public double fireInterval = 0.5;
    public double damage;
    public double range = 14;
    public Vec2 aimDir = new Vec2(1, 0);

    public Pet(Kind kind, Vec2 pos, double damage) {
        this.kind = kind;
        this.damage = damage;
        body.pos.set(pos);
        team = Team.PLAYER;
        switch (kind) {
            case TURRET -> { maxHp = hp = 60; lifeTime = 12; fireInterval = 0.5; range = 14; }
            case MEGA_TURRET -> { maxHp = hp = 120; lifeTime = 15; fireInterval = 0.35; range = 18; }
            case MINE -> { maxHp = hp = 1; lifeTime = 999; }
        }
        body.hw = 0.4; body.hh = 0.5;
    }

    @Override public String displayName() {
        return switch (kind) { case TURRET -> "Турель"; case MEGA_TURRET -> "Мега-турель"; case MINE -> "Мина"; };
    }
    @Override public double radius() { return 0.5; }

    @Override protected void die(Entity killer, Game game) { alive = false; }

    public void update(float dt, Game game) {
        if (!alive) return;
        if (kind == Kind.MINE) {
            // взрыв при приближении врага
            for (Enemy e : game.enemies) {
                if (!e.alive) continue;
                if (pos().dist(e.pos()) < 2.2) {
                    alive = false;
                    game.explode(pos().copy(), 5.5, damage, null, Team.PLAYER);
                    return;
                }
            }
            return;
        }
        lifeTime -= dt;
        if (lifeTime <= 0) { alive = false; return; }
        fireTimer -= dt;
        Enemy target = game.nearestEnemy(pos(), range);
        if (target != null && fireTimer <= 0) {
            fireTimer = fireInterval;
            aimDir.set(target.pos()).sub(pos()).norm();
            Vec2 from = pos().copy();
            from.y += 0.3;
            Projectile p = game.spawnProjectile(from, target.pos(), 30, damage, this, Team.PLAYER, false, 0);
            if (p != null) { p.cr = 0.4; p.cg = 1.0; p.cb = 0.5; }
        }
    }
}
