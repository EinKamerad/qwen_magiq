package rorlite.game;

import rorlite.data.Team;
import rorlite.engine.Rng;
import rorlite.engine.Vec2;

/**
 * Исполнение умений персонажей. primary вызывается ЛКМ, остальные — Q/E/R.
 */
public final class Abilities {

    private Abilities() {}

    /** Основная атака (ЛКМ). */
    public static void usePrimary(Game g, Player p) {
        Vec2 muzzle = muzzle(p);
        double dmg = p.attackDamage();
        boolean bloodbath = "bloodbath".equals(p.specialActiveId);
        switch (p.def.id) {
            case "commando" -> {
                Projectile pr = g.spawnProjectile(muzzle, g.aimWorld, 34, dmg, p, Team.PLAYER, false, 0);
                if (pr != null) { pr.cr = 1; pr.cg = 0.9; pr.cb = 0.4; }
            }
            case "bandit" -> {
                // две пули с разбросом
                for (int i = -1; i <= 1; i += 2) {
                    Vec2 aim = new Vec2(g.aimWorld.x + i * Rng.d(0.2, 0.6), g.aimWorld.y + i * Rng.d(-0.2, 0.2));
                    Projectile pr = g.spawnProjectile(muzzle, aim, 38, dmg, p, Team.PLAYER, false, 0);
                    if (pr != null) { pr.cr = 0.85; pr.cg = 0.45; pr.cb = 1; }
                }
            }
            case "acrid" -> {
                Projectile pr = g.spawnProjectile(muzzle, g.aimWorld, 24, dmg, p, Team.PLAYER, false, 0);
                if (pr != null) { pr.poisonOnHit = true; pr.gravity = true; pr.vel.y += 3; pr.cr = 0.4; pr.cg = 1; pr.cb = 0.3; }
            }
            case "engineer" -> {
                // лазер: мгновенный луч по ближайшей цели на линии
                Enemy t = g.nearestEnemyAlongRay(muzzle, g.aimWorld, 18);
                g.beams.add(Fx.beam(muzzle.x, muzzle.y,
                        t != null ? t.pos().x : g.aimWorld.x,
                        t != null ? t.pos().y : g.aimWorld.y, 0.1, 2.5, 0.3, 1, 0.9));
                if (t != null) {
                    double d = dmg;
                    if (g.rollCrit(p)) { d *= 2; g.spawnFloatingText(t.pos().x, t.pos().y - 2, "КРИТ!", 1, 0.9, 0.2); }
                    t.takeDamage(d, p, g);
                    g.applyLightningChain(t, p);
                    g.applyGlaciate(t, p);
                }
            }
            case "medic" -> {
                Projectile pr = g.spawnProjectile(muzzle, g.aimWorld, 30, dmg, p, Team.PLAYER, false, 0);
                if (pr != null) { pr.healOnHit = true; pr.cr = 1; pr.cg = 0.4; pr.cb = 0.5; }
            }
            case "golem" -> {
                // ближний конус перед собой
                Vec2 to = new Vec2(p.pos().x + p.facing * p.def.attackRange, p.pos().y);
                g.spawnMeleeHit(p.pos(), to, dmg, p, 1.6);
                Fx.burst(g.particles, to, 8, 5, 0.4, 0.3, 0.9, 0.7, 0.4, false);
            }
        }
        afterAttack(g, p);
    }

    /** Активация умений 2-4 (Q/E/R). slot: 1..3 */
    public static void cast(Game g, Player p, int slot) {
        String id = p.def.id;
        Vec2 muzzle = muzzle(p);
        switch (id) {
            case "commando" -> {
                if (slot == 1) { // граната
                    Projectile pr = g.spawnProjectile(muzzle, g.aimWorld, 18, 60 * p.damageMult, p, Team.PLAYER, true, 5.0);
                    if (pr != null) { pr.gravity = true; pr.life = 4; pr.radius = 0.3; pr.cr = 0.5; pr.cg = 0.5; pr.cb = 0.5; }
                } else if (slot == 2) { // рывок
                    dash(g, p, 16, 0.35);
                } else if (slot == 3) { // турель
                    g.pets.add(new Pet(Pet.Kind.TURRET, new Vec2(p.pos().x + p.facing, p.pos().y), 8 * p.damageMult));
                    g.addLog("Турель установлена!");
                }
            }
            case "bandit" -> {
                if (slot == 1) { // кинжал
                    Projectile pr = g.spawnProjectile(muzzle, g.aimWorld, 26, 45 * p.damageMult, p, Team.PLAYER, false, 0);
                    if (pr != null) { pr.pierce = true; pr.radius = 0.3; pr.cr = 0.8; pr.cg = 0.3; pr.cb = 0.9; }
                } else if (slot == 2) { // смерч
                    p.body.vel.y = 15;
                    p.jumpsLeft = 1;
                    for (Enemy e : g.enemies) if (e.alive && e.pos().dist(p.pos()) < 6) {
                        e.body.vel.y = 10; e.takeDamage(20 * p.damageMult, p, g);
                    }
                    Fx.burst(g.particles, p.pos(), 16, 6, 0.6, 0.4, 0.7, 0.4, 1, false);
                } else if (slot == 3) { // теневой удар
                    Enemy best = null; double bh = 0;
                    for (Enemy e : g.enemies) if (e.alive && e.pos().dist(p.pos()) < 16 && e.hp > bh) { bh = e.hp; best = e; }
                    if (best != null) {
                        p.body.pos.set(best.pos().x - p.facing * 1.5, best.pos().y);
                        best.takeDamage(80 * p.damageMult * 4, p, g);
                        Fx.burst(g.particles, best.pos(), 20, 8, 0.5, 0.4, 0.5, 0.2, 0.8, false);
                    }
                }
            }
            case "acrid" -> {
                if (slot == 1) { // жало
                    Projectile pr = g.spawnProjectile(muzzle, g.aimWorld, 22, 40 * p.damageMult, p, Team.PLAYER, true, 4.5);
                    if (pr != null) { pr.gravity = true; pr.cr = 0.4; pr.cg = 1; pr.cb = 0.2; pr.life = 3; }
                } else if (slot == 2) { // прыжок
                    p.body.vel.y = 20;
                    p.body.vel.x += p.facing * 10;
                    Fx.burst(g.particles, p.pos(), 10, 4, 0.5, 0.3, 0.4, 1, 0.4, false);
                } else if (slot == 3) { // отравляющий рой
                    p.specialActiveId = "plague";
                    p.specialActiveTimer = 8;
                    g.addLog("Ядовитый рой!");
                }
            }
            case "engineer" -> {
                if (slot == 1) { // мина
                    Pet mine = new Pet(Pet.Kind.MINE, new Vec2(p.pos().x + p.facing * 1.2, p.pos().y), 70 * p.damageMult);
                    double groundY = g.world.groundBelow(mine.pos().x, rorlite.engine.World.tileIndex(mine.pos().y));
                    mine.body.pos.y = groundY - 0.5;
                    g.pets.add(mine);
                    g.addLog("Мина установлена.");
                } else if (slot == 2) { // платформа — телепорт вверх + ускорение (упрощённо: высокий прыжок)
                    p.body.vel.y = 18;
                    Fx.burst(g.particles, p.pos(), 12, 5, 0.5, 0.3, 0.4, 0.8, 1, false);
                } else if (slot == 3) { // мега-турель
                    g.pets.add(new Pet(Pet.Kind.MEGA_TURRET, new Vec2(p.pos().x + p.facing, p.pos().y), 14 * p.damageMult));
                    g.addLog("Мега-турель развернута!");
                }
            }
            case "medic" -> {
                if (slot == 1) { // исцеление
                    p.heal(40 + 10 * p.stackCount("vitalik"));
                    Fx.burst(g.particles, p.pos(), 16, 4, 0.8, 0.4, 0.4, 1, 0.5, false);
                    g.spawnFloatingText(p.pos().x, p.pos().y - 2, "+HP", 0.4, 1, 0.5);
                } else if (slot == 2) { // рывок душ
                    dash(g, p, 14, 0.2);
                } else if (slot == 3) { // кровавая баня
                    p.specialActiveId = "bloodbath";
                    p.specialActiveTimer = 6;
                    p.attackSpeedMult *= 1.0; // множитель применяется динамически ниже
                    g.addLog("Кровавая баня!");
                }
            }
            case "golem" -> {
                if (slot == 1) { // валун
                    Projectile pr = g.spawnProjectile(muzzle, new Vec2(p.pos().x + p.facing * 10, p.pos().y), 16, 55 * p.damageMult, p, Team.PLAYER, false, 0);
                    if (pr != null) { pr.pierce = true; pr.gravity = true; pr.radius = 0.7; pr.life = 4; pr.cr = 0.6; pr.cg = 0.55; pr.cb = 0.5; }
                } else if (slot == 2) { // прыжок гиганта
                    p.body.vel.y = 17;
                    p.body.vel.x += p.facing * 6;
                    p.specialActiveId = "slam"; // при приземлении нанесёт урон
                    p.specialActiveTimer = 4;
                } else if (slot == 3) { // каменный шторм
                    p.specialActiveId = "storm";
                    p.specialActiveTimer = 5;
                    g.addLog("Каменный шторм!");
                }
            }
        }
        afterAttack(g, p);
    }

    private static void dash(Game g, Player p, double power, double invuln) {
        double dx = g.aimWorld.x - p.pos().x, dy = g.aimWorld.y - p.pos().y;
        double l = Math.hypot(dx, dy);
        if (l < 0.1) { dx = p.facing; dy = 0; l = 1; }
        p.body.vel.x = dx / l * power * 2.2;
        p.body.vel.y = Math.max(p.body.vel.y, dy / l * power);
        p.invulnTimer = Math.max(p.invulnTimer, invuln);
        Fx.burst(g.particles, p.pos(), 10, 6, 0.4, 0.3, 0.6, 0.8, 1, false);
    }

    private static Vec2 muzzle(Player p) {
        Vec2 v = p.pos().copy();
        v.y += 0.15;
        return v;
    }

    /** Общие пост-эффекты атаки: споры (каждое 3-е попадание яд), warhammer уже в damage. */
    private static void afterAttack(Game g, Player p) {
        // скорость атаки берсеркера/крови уже учтена в attackInterval
    }

    /** Вызывается из Game при попадании снаряда игрока (для «Грибных спор»). */
    public static void onPlayerHitConfirm(Game g, Player p, Entity target) {
        if (p.stackCount("spore") > 0) {
            p.hitsForSpore++;
            if (p.hitsForSpore >= 3) {
                p.hitsForSpore = 0;
                target.applyPoison(8 + 4 * p.stackCount("spore"), 4);
            }
        }
    }

    /** Динамические модификаторы (вызывать из Game каждый кадр): эффекты specialActive. */
    public static void tickSpecial(Game g, Player p, float dt) {
        switch (String.valueOf(p.specialActiveId)) {
            case "plague" -> {
                for (Enemy e : g.enemies) {
                    if (e.alive && e.pos().dist(p.pos()) < 10) {
                        e.applyPoison(12 * p.damageMult, 1.5);
                    }
                }
            }
            case "bloodbath" -> {
                // вампиризм: лечим пропорционально текущему урону во времени
                p.heal(6 * dt);
            }
            case "storm" -> {
                // метеориты: раз в ~0.3 сек падают на случайного врага
                g.stormTimer -= dt;
                if (g.stormTimer <= 0) {
                    g.stormTimer = 0.3;
                    Enemy t = g.randomAliveEnemy();
                    if (t != null) {
                        Vec2 at = t.pos().copy();
                        g.explode(at, 4.5, 35 * p.damageMult, p, Team.PLAYER);
                        g.beams.add(Fx.beam(at.x, at.y - 14, at.x, at.y, 0.2, 3, 0.8, 0.6, 0.4));
                    }
                }
            }
            default -> {}
        }
    }
}
