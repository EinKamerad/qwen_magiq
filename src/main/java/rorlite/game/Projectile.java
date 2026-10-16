package rorlite.game;

import rorlite.data.Team;
import rorlite.engine.Physics;
import rorlite.engine.Rng;
import rorlite.engine.Vec2;
import rorlite.engine.World;

/** Снаряд (пуля, плюс, кинжал, граната…). */
public class Projectile {
    public Vec2 pos, vel;
    public double damage;
    public Entity owner;
    public Team team;
    public boolean gravity = false;
    public boolean pierce = false;      // пробивает насквозь
    public boolean explosive = false;   // взрыв по площади
    public double explodeRadius = 4.0;
    public double life = 3.0;           // сек
    public double radius = 0.18;
    public double cr = 1, cg = 0.95, cb = 0.5;
    public boolean poisonOnHit = false;     // кислотные плювки Молотобойца
    public boolean healOnHit = false;       // дротик Медика
    public boolean homing = false;          // самонаводящиеся снаряды боссов
    public final Physics.Body body = new Physics.Body();

    public Projectile(Vec2 pos, Vec2 dir, double speed, double damage, Entity owner, Team team) {
        this.pos = pos.copy();
        this.vel = dir.copy().norm().scl(speed);
        this.damage = damage;
        this.owner = owner;
        this.team = team;
        body.hw = radius; body.hh = radius;
    }

    /** Двигает снаряд с коллизиями со стенами. Возвращает true, если жив. */
    public boolean update(float dt, World world, Game game) {
        life -= dt;
        if (life <= 0) return false;
        if (gravity) {
            Physics.applyGravity(body, dt);
            Physics.MoveResult res = new Physics.MoveResult();
            body.pos.set(pos);
            body.vel.set(vel);
            Physics.move(world, body, dt, res);
            pos.set(body.pos);
            vel.set(body.vel);
            if (res.hitWall || (res.onGround && res.landImpact > 6)) {
                explodeIfNeeded(game);
                return false;
            }
        } else {
            pos.x += vel.x * dt;
            pos.y += vel.y * dt;
            int tx = World.tileIndex(pos.x), ty = World.tileIndex(pos.y);
            if (world.solid(tx, ty)) {
                if (explosive) explodeIfNeeded(game);
                else game.spawnHitSpark(pos.copy(), cr, cg, cb);
                return false;
            }
        }
        // столкновение с персонажами
        for (Entity e : game.activeTargets()) {
            if (!team.hostileTo(e.team) || e == owner) continue;
            double rr = e.radius() + radius;
            double dx = e.pos().x - pos.x, dy = e.pos().y - pos.y;
            if (dx * dx + dy * dy < rr * rr) {
                hit(e, game);
                if (!pierce) {
                    explodeIfNeeded(game);
                    return false;
                }
            }
        }
        return true;
    }

    private void hit(Entity target, Game game) {
        double dmg = damage;
        if (game.rollCrit(game.ownerPlayerIfAny(owner))) {
            dmg *= 2.0;
            game.spawnFloatingText(target.pos().x, target.pos().y - target.radius() - 0.5, "КРИТ!", 1, 0.9, 0.2);
        }
        target.takeDamage(dmg, owner, game);
        if (poisonOnHit) target.applyPoison(6 + game.itemStacks(owner, "spore") * 2, 3);
        if (healOnHit && owner instanceof Player p) p.heal(1 + p.stackCount("leech"));
        game.applyLightningChain(target, owner);
        game.applyGlaciate(target, owner);
        game.spawnHitSpark(pos.copy(), cr, cg, cb);
    }

    private void explodeIfNeeded(Game game) {
        if (explosive) {
            game.explode(pos.copy(), explodeRadius, damage, owner, team);
        } else {
            game.spawnHitSpark(pos.copy(), cr, cg, cb);
        }
    }
}
