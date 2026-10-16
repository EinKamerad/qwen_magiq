package rorlite.game;

import rorlite.data.CharacterDef;
import rorlite.data.Item;
import rorlite.data.Items;
import rorlite.data.Team;
import rorlite.engine.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Игровой цикл уровня (run) в стиле RoR2:
 * волны врагов -> сундуки/лавки -> алтарь босса -> портал на следующий тир -> ...
 * после 4-го тира — финальный босс и эвакуация.
 */
public class Game {

    public enum Phase { PLAYING, BOSS_ALIVE, PORTAL_CHARGING, VICTORY_WAIT, DEAD }

    // ---- мир ----
    public World world;
    public int tier = 1;
    public static final int MAX_TIER = 4;      // 4 уровня, затем финал
    public Player player;
    public final List<Enemy> enemies = new ArrayList<>();
    public final List<Pet> pets = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Interactable> interactables = new ArrayList<>();
    public final List<Fx.Particle> particles = new ArrayList<>();
    public final List<Fx.FloatText> texts = new ArrayList<>();
    public final List<Fx.Beam> beams = new ArrayList<>();

    public Phase phase = Phase.PLAYING;
    public double timeAlive = 0;
    public double killCount = 0;
    public double stormTimer = 0;   // таймер метеоритов «Каменного шторма»
    public String lastLog = "";
    public double logTimer = 0;

    // Волны: прирост врагов со временем (как pressure в RoR2)
    private double waveTimer = 6;
    private int aliveBudget() { return 8 + tier * 5; }

    // Портал
    public boolean portalActive = false;
    public double portalCharge = 0;   // прогресс зарядки портала
    public Vec2 portalPos;

    // Босс
    public Enemy boss;
    public Interactable bossAltar;

    // Камера
    public double camX, camY;
    public double zoom = 34;          // пикселей на мировую единицу

    // Ввод (устанавливается из Main каждый кадр)
    public GameWindow win;
    public Vec2 aimWorld = new Vec2(); // точка прицела в мире

    private final Physics.MoveResult moveRes = new Physics.MoveResult();

    // ------------------------------------------------------------------
    public Game(Player player) {
        this.player = player;
        startTier(1);
    }

    public void startTier(int t) {
        tier = t;
        world = World.generate(t);
        enemies.clear();
        pets.clear();
        projectiles.clear();
        interactables.clear();
        particles.clear();
        beams.clear();
        portalActive = false;
        portalCharge = 0;
        boss = null;
        phase = Phase.PLAYING;
        waveTimer = 4;

        // игрок на точку спавна
        player.body.pos.set(world.playerSpawn[0], world.playerSpawn[1]);
        player.body.vel.set(0, 0);
        player.jumpsLeft = player.hasJetpack ? 2 : 1;

        portalPos = new Vec2(world.portalPos[0], world.portalPos[1]);

        // Размещение объектов: сундуки, лавки, кусты, алтарь босса
        placeInteractables();

        // Стартовые враги
        for (int i = 0; i < 6 + t * 2; i++) spawnRandomEnemy();

        addLog("Уровень " + t + "/" + MAX_TIER + ". Очисти арену, убей босса, активируй портал!");
    }

    private void placeInteractables() {
        List<double[]> pts = new ArrayList<>(world.spawnPoints);
        java.util.Collections.shuffle(pts);
        int idx = 0;
        int chests = 3 + Rng.i(0, 2);
        for (int i = 0; i < chests && idx < pts.size(); i++, idx++)
            interactables.add(new Interactable(Interactable.Kind.CHEST, new Vec2(pts.get(idx)[0], pts.get(idx)[1] - 0.4)));
        for (int i = 0; i < 2 && idx < pts.size(); i++, idx++)
            interactables.add(new Interactable(Interactable.Kind.SHOP_1, new Vec2(pts.get(idx)[0], pts.get(idx)[1] - 0.4)));
        if (idx < pts.size())
            interactables.add(new Interactable(Interactable.Kind.SHOP_2, new Vec2(pts.get(idx)[0], pts.get(idx)[1] - 0.4)));
        for (int i = 0; i < 4 && idx < pts.size(); i++, idx++)
            interactables.add(new Interactable(Interactable.Kind.BUSH, new Vec2(pts.get(idx)[0], pts.get(idx)[1] - 0.2)));
        if (idx < pts.size())
            interactables.add(new Interactable(Interactable.Kind.DOLL, new Vec2(pts.get(idx)[0], pts.get(idx)[1] - 0.3)));
        // Алтарь вызова босса — у правого края арены
        if (idx < pts.size()) {
            bossAltar = new Interactable(Interactable.Kind.BOSS_CHALLENGE, new Vec2(portalPos.x - 10, pts.get(idx)[1] - 0.5));
            interactables.add(bossAltar);
        } else {
            bossAltar = new Interactable(Interactable.Kind.BOSS_CHALLENGE, new Vec2(portalPos.x - 12, portalPos.y));
            interactables.add(bossAltar);
        }
    }

    // ------------------------------------------------------------------
    /** Главный шаг симуляции. */
    public void update(float dt) {
        if (player == null || phase == Phase.DEAD) {
            updateFxOnly(dt);
            return;
        }
        timeAlive += dt;
        if (logTimer > 0) logTimer -= dt;

        // --- ввод игрока ---
        handlePlayerInput(dt);

        // --- физика игрока ---
        Physics.applyGravity(player.body, dt);
        Physics.move(world, player.body, dt, moveRes);
        player.onGround = moveRes.onGround;
        if (moveRes.landed) {
            player.jumpsLeft = player.hasJetpack ? 2 : 1;
            checkLandingImpacts(player);
        }
        clampToWorld(player.body);
        player.update(dt, this);
        Abilities.tickSpecial(this, player, dt);
        player.updateStatuses(dt, this);
        if (player.hp <= 0 && player.alive) player.die(null, this);

        // аура Корня всевидящего
        if (player.stackCount("root") > 0) {
            for (Enemy e : enemies) if (e.alive && e.pos().dist(player.pos()) < 8) e.applySlow(0.3f);
        }

        // --- волны ---
        if (phase == Phase.PLAYING) {
            waveTimer -= dt;
            if (waveTimer <= 0) {
                waveTimer = Math.max(3.5, 9 - tier);
                int aliveEnemies = 0;
                for (Enemy e : enemies) if (e.alive) aliveEnemies++;
                int toSpawn = Math.min(aliveBudget() - aliveEnemies, 2 + tier);
                for (int i = 0; i < toSpawn; i++) spawnRandomEnemy();
            }
        }

        // --- враги ---
        for (Enemy e : enemies) {
            if (!e.alive) { e.deathTimer -= dt; continue; }
            Physics.applyGravity(e.body, dt);
            e.updateAI(dt, player, this);
            Physics.move(world, e.body, dt, moveRes);
            e.onGround = moveRes.onGround;
            clampToWorld(e.body);
            e.updateStatuses(dt, this);
        }
        enemies.removeIf(e -> !e.alive && e.deathTimer <= 0);

        // --- питомцы ---
        for (Pet p : pets) {
            if (!p.alive) continue;
            p.update(dt, this);
        }
        pets.removeIf(p -> !p.alive);

        // --- снаряды ---
        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile pr = it.next();
            if (!pr.update(dt, world, this)) it.remove();
        }

        // --- интерактивы ---
        for (Interactable in : interactables) in.update(dt, player, this);

        // --- фаза босса / портала ---
        if (phase == Phase.BOSS_ALIVE && (boss == null || !boss.alive)) {
            phase = Phase.PLAYING;
            boss = null;
            // босс дропает корону
            player.addItem(Items.CROWN, this);
            addLog("Босс повержен! Найден: Королевская корона. Активируй ПОРТАЛ (E)!");
            portalActive = true;
        }

        if (portalActive && player.pos().dist(portalPos) < 3.0) {
            if (keyInteractHeld()) {
                portalCharge += dt;
                if (portalCharge >= 3.0) nextTier();
            } else {
                portalCharge = Math.max(0, portalCharge - dt * 2);
            }
        }

        // --- FX ---
        updateFxOnly(dt);

        // камера следует за игроком с лёгким упреждением к курсору
        double targetX = player.pos().x + (aimWorld.x - player.pos().x) * 0.15;
        double targetY = player.pos().y + (aimWorld.y - player.pos().y) * 0.15;
        camX += (targetX - camX) * Math.min(1, dt * 8);
        camY += (targetY - camY) * Math.min(1, dt * 8);
    }

    private void updateFxOnly(float dt) {
        Iterator<Fx.Particle> pit = particles.iterator();
        while (pit.hasNext()) {
            Fx.Particle p = pit.next();
            p.life -= dt;
            if (p.life <= 0) { pit.remove(); continue; }
            if (p.gravity) p.vel.y -= 30 * dt;
            p.pos.x += p.vel.x * dt;
            p.pos.y += p.vel.y * dt;
        }
        Iterator<Fx.FloatText> tit = texts.iterator();
        while (tit.hasNext()) {
            Fx.FloatText t = tit.next();
            t.life -= dt;
            t.y += t.vy * dt;
            if (t.life <= 0) tit.remove();
        }
        Iterator<Fx.Beam> bit = beams.iterator();
        while (bit.hasNext()) {
            Fx.Beam b = bit.next();
            b.life -= dt;
            if (b.life <= 0) bit.remove();
        }
    }

    // ------------------------------------------------------------------
    private void handlePlayerInput(float dt) {
        if (!player.alive || win == null) return; // headless-режим (смоуктест): без ввода
        CharacterDef def = player.def;
        double left = win.keyDown(GLFW_KEY_A) || win.keyDown(GLFW_KEY_LEFT) ? -1 : 0;
        double right = win.keyDown(GLFW_KEY_D) || win.keyDown(GLFW_KEY_RIGHT) ? 1 : 0;
        double dir = left + right;
        if (dir != 0) player.facing = dir;

        double spd = player.speed();
        accelerate(player.body, dir * spd * 10, dt, spd);

        // Прыжок (W / Space), двойной прыжок/ранец
        boolean jumpPressed = win.keyPressed(GLFW_KEY_SPACE) || win.keyPressed(GLFW_KEY_W) || win.keyPressed(GLFW_KEY_UP);
        if (jumpPressed && player.stunTime <= 0) {
            if (player.onGround) {
                player.body.vel.y = def.jumpForce;
                player.jumpsLeft--;
                Fx.burst(particles, player.pos(), 5, 3, 0.3, 0.25, 0.8, 0.8, 0.8, false);
            } else if (player.jumpsLeft > 0) {
                player.body.vel.y = def.jumpForce * 0.85;
                player.jumpsLeft--;
                Fx.burst(particles, player.pos(), 8, 4, 0.4, 0.3, 0.5, 0.8, 1.0, false); // реактивный джетпак
            }
        }

        // Основная атака — ЛКМ (удерживать для автоогня)
        if (win.mouseHeld() && player.attackTimer <= 0) {
            player.attackTimer = player.attackInterval();
            Abilities.usePrimary(this, player);
        }
        // Умения: Q E? — используем R/T/Y/G/F как в прототипе:
        // 1/Q — secondary, 2/E... удобнее: Q, E, R — secondary/utility/special
        if ((win.keyPressed(GLFW_KEY_Q)) && player.canCast(1)) castAbility(1);
        if ((win.keyPressed(GLFW_KEY_E)) && player.canCast(2)) castAbility(2);
        if ((win.keyPressed(GLFW_KEY_R)) && player.canCast(3)) castAbility(3);
    }

    private void castAbility(int slot) {
        player.triggerCooldown(slot);
        Abilities.cast(this, player, slot);
    }

    private static final int GLFW_KEY_A = org.lwjgl.glfw.GLFW.GLFW_KEY_A;
    private static final int GLFW_KEY_D = org.lwjgl.glfw.GLFW.GLFW_KEY_D;
    private static final int GLFW_KEY_W = org.lwjgl.glfw.GLFW.GLFW_KEY_W;
    private static final int GLFW_KEY_S = org.lwjgl.glfw.GLFW.GLFW_KEY_S;
    private static final int GLFW_KEY_LEFT = org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
    private static final int GLFW_KEY_RIGHT = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
    private static final int GLFW_KEY_UP = org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
    private static final int GLFW_KEY_SPACE = org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
    private static final int GLFW_KEY_Q = org.lwjgl.glfw.GLFW.GLFW_KEY_Q;
    private static final int GLFW_KEY_E = org.lwjgl.glfw.GLFW.GLFW_KEY_E;
    private static final int GLFW_KEY_R = org.lwjgl.glfw.GLFW.GLFW_KEY_R;
    private static final int GLFW_KEY_F = org.lwjgl.glfw.GLFW.GLFW_KEY_F;

    public boolean keyInteractHeld() {
        return win != null && (win.keyDown(GLFW_KEY_F) || win.keyDown(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER));
    }

    /** Двойной прыжок засчитывается только если игрок НЕ на земле — см. handlePlayerInput. */

    // ------------------------------------------------------------------
    public Enemy nearestEnemyAlongRay(Vec2 from, Vec2 to, double range) {
        Vec2 dir = new Vec2(to.x - from.x, to.y - from.y);
        double l = dir.len();
        if (l < 0.01) return null;
        dir.scl(1 / l);
        Enemy best = null; double bestT = range;
        for (Enemy e : enemies) {
            if (!e.alive) continue;
            double ex = e.pos().x - from.x, ey = e.pos().y - from.y;
            double t = ex * dir.x + ey * dir.y;          // проекция
            if (t < 0 || t > range) continue;
            double px = from.x + dir.x * t, py = from.y + dir.y * t;
            double d = Math.hypot(e.pos().x - px, e.pos().y - py);
            if (d < e.radius() + 0.6 && t < bestT) { bestT = t; best = e; }
        }
        return best;
    }

    public Enemy randomAliveEnemy() {
        List<Enemy> alive = new ArrayList<>();
        for (Enemy e : enemies) if (e.alive) alive.add(e);
        if (alive.isEmpty()) return null;
        return Rng.pick(alive);
    }

    /** Урон атаки игрока с учётом «Кровавой бани» и т.п. */
    public double playerDamageMultDynamic() {
        double m = player.damageMult;
        if ("bloodbath".equals(player.specialActiveId)) m *= 1.3;
        return m;
    }

    public double playerAttackSpeedMultDynamic() {
        double m = player.attackSpeedMult;
        if ("bloodbath".equals(player.specialActiveId)) m *= 2.0;
        return m;
    }

    /** Обработка приземления игрока: «Прыжок гиганта» (slam). */
    private void checkLandingImpacts(Player p) {
        if ("slam".equals(p.specialActiveId)) {
            p.specialActiveId = null;
            p.specialActiveTimer = 0;
            explode(p.pos(), 7, 45 * p.damageMult, p, Team.PLAYER);
            Fx.burst(particles, p.pos(), 24, 10, 0.8, 0.4, 0.8, 0.6, 0.4, false);
            addLog("Сокрушительное приземление!");
        }
    }

    /** Ускорение тела к целевой скорости (аккуратно, как RoR2 accel). */
    public void accelerate(Physics.Body b, double targetVx, float dt, double maxSpeed) {
        double diff = targetVx - b.vel.x;
        double a = Math.signum(diff) * Math.min(Math.abs(diff), 60 * dt * 10);
        b.vel.x += a;
        if (maxSpeed > 0 && Math.abs(b.vel.x) > Math.abs(targetVx) && Math.signum(b.vel.x) == Math.signum(targetVx)) {
            b.vel.x = targetVx;
        }
    }

    public void accelerateNoCap(Physics.Body b, double ax, float dt) {
        b.vel.x += ax * dt * 4;
    }

    private void clampToWorld(Physics.Body b) {
        double maxX = world.widthWorld() - 2, minX = 2;
        b.pos.x = Math.max(minX, Math.min(maxX, b.pos.x));
        if (b.pos.y > world.heightWorld() + 20) b.pos.y = 10; // выпал — вернуть сверху
    }

    // ------------------------------------------------------------------
    public void spawnRandomEnemy() {
        if (world.spawnPoints.isEmpty()) return;
        double[] sp = Rng.pick(world.spawnPoints);
        // спавним подальше от игрока
        int tries = 0;
        while (Math.hypot(sp[0] - player.pos().x, sp[1] - player.pos().y) < 14 && tries++ < 10) {
            sp = Rng.pick(world.spawnPoints);
        }
        Enemy e = EnemyDefs.create(EnemyDefs.randomBasic(), tier, new Vec2(sp[0], sp[1]));
        if (tier >= 2 && Rng.chance(0.12)) e.makeElite();
        enemies.add(e);
        Fx.burst(particles, e.pos(), 6, 3, 0.4, 0.3, 0.9, 0.3, 0.9, false);
    }

    public Enemy spawnEnemyNear(String id, double x, double y) {
        Enemy e = EnemyDefs.create(id, tier, new Vec2(x, y));
        enemies.add(e);
        return e;
    }

    public void spawnBossAt(Vec2 pos) {
        boss = EnemyDefs.createBoss(tier, pos);
        enemies.add(boss);
        phase = Phase.BOSS_ALIVE;
    }

    private void nextTier() {
        if (tier >= MAX_TIER) {
            // Финальный босс уже должен быть убит до портала — но на всякий:
            addLog("Эвакуация! Победа!");
            phase = Phase.VICTORY_WAIT;
            return;
        }
        player.heal(player.totalMaxHp() * 0.35);
        startTier(tier + 1);
    }

    public void onPlayerDeath() {
        phase = Phase.DEAD;
        addLog("Вы погибли. Run завершён.");
    }

    public void onEnemyKilled(Enemy e, Entity killer) {
        killCount++;
        player.money += e.moneyDrop;
        Fx.burst(particles, e.pos(), 14, 6, 0.6, 0.35, 0.9, 0.4, 0.3, true);
        // эффекты предметов на убийство
        if (killer instanceof Player || e.team == Team.ENEMY) {
            if (player.stackCount("leech") > 0) player.heal(2 * player.stackCount("leech"));
            if (player.stackCount("soulcocoon") > 0) player.bonusHealth += 2 * player.stackCount("soulcocoon");
        }
        // Redemption: взрыв при низком HP игрока не связан с убийством — пропускаем здесь
        if (e == boss) {
            // обрабатывается в update()
        }
        // Редко враг роняет монеты-частицы
        if (Rng.chance(0.5)) Fx.burst(particles, e.pos(), 4, 4, 0.7, 0.2, 1.0, 0.9, 0.2, true);
    }

    // ------------------------------------------------------------------
    // API для Projectile / Abilities
    public List<Entity> activeTargets() {
        List<Entity> l = new ArrayList<>();
        if (player.alive) l.add(player);
        l.addAll(enemies.stream().filter(e -> e.alive).map(e -> (Entity) e).toList());
        for (Pet p : pets) if (p.alive && p.kind != Pet.Kind.MINE) l.add(p);
        return l;
    }

    public Projectile spawnProjectile(Vec2 from, Vec2 to, double speed, double damage,
                                      Entity owner, Team team, boolean explosive, double explodeRadius) {
        Vec2 dir = new Vec2(to.x - from.x, to.y - from.y).norm();
        Projectile p = new Projectile(from, dir, speed, damage, owner, team);
        if (explosive) { p.explosive = true; p.explodeRadius = explodeRadius; }
        projectiles.add(p);
        return p;
    }

    /** Ближний удар: мгновенная проверка попадания по конусу. */
    public void spawnMeleeHit(Vec2 from, Vec2 to, double damage, Entity owner, double widthMult) {
        beams.add(Fx.beam(from.x, from.y, to.x, to.y, 0.12, 3, 1, 0.9, 0.5));
        // попадание по всем врагам между точками (упрощённо — сфера вокруг точки цели)
        Team ot = owner != null ? owner.team : Team.NONE;
        for (Entity e : activeTargets()) {
            if (!ot.hostileTo(e.team)) continue;
            double d = e.pos().dist(to);
            if (d < 1.6 * widthMult + e.radius()) {
                double dmg = damage;
                if (rollCrit(ownerPlayerIfAny(owner))) { dmg *= 2; spawnFloatingText(e.pos().x, e.pos().y - 2, "КРИТ!", 1, 0.9, 0.2); }
                e.takeDamage(dmg, owner, this);
                applyLightningChain(e, owner);
                applyGlaciate(e, owner);
            }
        }
    }

    public void explode(Vec2 at, double radius, double damage, Entity owner, Team team) {
        Fx.burst(particles, at, 22, 9, 0.7, 0.5, 1.0, 0.6, 0.2, false);
        beams.add(Fx.beam(at.x, at.y, at.x, at.y - radius, 0.15, 2, 1, 0.7, 0.3));
        for (Entity e : activeTargets()) {
            if (!team.hostileTo(e.team)) continue;
            double d = e.pos().dist(at);
            if (d < radius + e.radius()) {
                double falloff = 1 - Math.max(0, (d - radius * 0.5)) / (radius * 0.7);
                e.takeDamage(damage * Math.max(0.35, falloff), owner, this);
                // отбрасывание
                Vec2 push = new Vec2(e.pos().x - at.x, e.pos().y - at.y).norm().scl(9);
                e.body.vel.x += push.x; e.body.vel.y += Math.abs(push.y) + 4;
            }
        }
    }

    public void bossShockwave(Vec2 at, double radius, Enemy boss) {
        Fx.burst(particles, at, 30, 12, 0.9, 0.4, 0.9, 0.4, 0.9, false);
        for (Entity e : activeTargets()) {
            if (e.team != Team.PLAYER) continue;
            double d = e.pos().dist(at);
            if (d < radius) {
                e.takeDamage(boss.attackDamage * 0.5, boss, this);
                Vec2 push = new Vec2(e.pos().x - at.x, e.pos().y - at.y).norm().scl(12);
                e.body.vel.x += push.x; e.body.vel.y += 8;
            }
        }
    }

    public Enemy nearestEnemy(Vec2 at, double range) {
        Enemy best = null; double bd = range;
        for (Enemy e : enemies) {
            if (!e.alive) continue;
            double d = e.pos().dist(at);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    public boolean worldSolidAt(double x, double y) {
        return world.solid(World.tileIndex(x), World.tileIndex(y));
    }

    public Player ownerPlayerIfAny(Entity owner) {
        return owner instanceof Player p ? p : (owner != null && owner.team == Team.PLAYER ? player : null);
    }

    public boolean rollCrit(Player p) {
        if (p == null) return false;
        double c = p.stackCount("crithalf") * 0.07;
        return Rng.chance(c);
    }

    public int itemStacks(Entity owner, String id) {
        if (owner instanceof Player p) return p.stackCount(id);
        return owner != null && owner.team == Team.PLAYER ? player.stackCount(id) : 0;
    }

    public void applyLightningChain(Entity first, Entity owner) {
        int stacks = itemStacks(owner, "lightning");
        if (stacks == 0) return;
        if (!Rng.chance(Math.min(0.9, 0.25 * stacks))) return;
        Enemy src = first instanceof Enemy fe ? fe : nearestEnemy(first.pos(), 10);
        List<Enemy> hitList = new ArrayList<>();
        hitList.add(src);
        Vec2 cur = src != null ? src.pos() : first.pos();
        for (int j = 0; j < 2; j++) {
            Enemy nx = null; double bd = 9;
            for (Enemy e : enemies) {
                if (!e.alive || hitList.contains(e)) continue;
                double d = e.pos().dist(cur);
                if (d < bd) { bd = d; nx = e; }
            }
            if (nx == null) break;
            hitList.add(nx);
            Vec2 np = nx.pos();
            beams.add(Fx.beam(cur.x, cur.y, np.x, np.y, 0.18, 2, 0.5, 0.85, 1.0));
            nx.takeDamage(25 * stacks, owner, this);
            cur = np;
        }
    }

    public void applyGlaciate(Entity target, Entity owner) {
        if (itemStacks(owner, "glack") > 0) target.applySlow(2.0);
    }

    public void spawnChestBurst(Vec2 at) {
        Fx.burst(particles, at, 20, 6, 0.8, 0.35, 1.0, 0.85, 0.3, true);
    }

    public void spawnHitSpark(Vec2 at, double cr, double cg, double cb) {
        Fx.burst(particles, at, 4, 5, 0.25, 0.18, cr, cg, cb, false);
    }

    public void spawnDamageNumber(Vec2 at, double amount, boolean good) {
        String s = String.valueOf((int) Math.round(amount));
        texts.add(Fx.floatText(s, at.x + Rng.d(-0.4, 0.4), at.y,
                good ? 1 : 1, good ? 0.95 : 1, good ? 0.9 : 0.3, 14));
    }

    public void spawnFloatingText(double x, double y, String text, double cr, double cg, double cb) {
        texts.add(Fx.floatText(text, x, y, cr, cg, cb, 14));
    }

    public void addLog(String msg) {
        lastLog = msg;
        logTimer = 5;
    }

    // ------------------------------------------------------------------
    /** Применение эффекта предмета при получении. */
    public void applyItem(Player p, Item it) {
        switch (it.id) {
            case "spore" -> p.attackSpeedMult += 0.15;
            case "poly" -> { p.bonusHealth += 100; p.damageMult += 0.10; }
            case "jetpack" -> { p.hasJetpack = true; p.jumpsLeft = 2; }
            case "leech" -> {} // эффект на убийстве
            case "armorplate" -> p.armor += 12;
            case "crithalf" -> {} // crit считается в rollCrit
            case "berserker" -> { p.attackSpeedMult += 0.30; p.moveSpeedMult -= 0.15; }
            case "saturd" -> { p.bonusHealth += 100; p.regenPerSec += 10; }
            case "warhammer" -> p.warhammerTimer = 0;
            case "rabbitfoot" -> p.dodgeChance += 0.10;
            case "redemption" -> { /* взрыв при смерти обрабатывается в onPlayerDeath-логике */ }
            case "lightning" -> {} // в applyLightningChain
            case "atceed" -> p.moveSpeedMult += 0.35;
            case "root" -> p.bonusHealth += 250;
            case "glack" -> {}
            case "vitalik" -> { p.regenPerSec += 2; p.bonusHealth += 100; }
            case "soulcocoon" -> {}
            case "ruby" -> p.damageMult += 0.20;
            case "diode" -> { p.damageMult += 0.15; p.moveSpeedMult += 0.15; }
            case "quartz" -> { p.attackSpeedMult += 0.20; }
            case "crown" -> p.damageMult += 0.25;
            default -> {}
        }
    }
}
