package rorlite;

import org.lwjgl.glfw.GLFW;
import rorlite.data.CharacterDef;
import rorlite.engine.GameWindow;
import rorlite.engine.Renderer;
import rorlite.engine.World;
import rorlite.game.Enemy;
import rorlite.game.Fx;
import rorlite.game.Game;
import rorlite.game.Interactable;
import rorlite.game.Pet;
import rorlite.game.Player;
import rorlite.game.Projectile;

/**
 * Точка входа ROR-Lite: выбор персонажа -> игровой цикл -> экран смерти/победы.
 */
public final class Main {

    public static void main(String[] args) {
        GameWindow win = new GameWindow("ROR-Lite (RoR2-like prototype)");
        Renderer.init();

        int selected = 0;      // индекс в ростере
        int heldFrames = 0;    // анти-дребезг стрелок меню
        Game game = null;      // запущенный ран

        while (!win.shouldClose()) {
            win.pollEvents();

            if (game == null) {
                // ---------- МЕНЮ ВЫБОРА ПЕРСОНАЖА ----------
                selected = menuInput(win, selected, heldFrames);
                heldFrames = (win.keyDown(GLFW.GLFW_KEY_LEFT) || win.keyDown(GLFW.GLFW_KEY_RIGHT)
                        || win.keyDown(GLFW.GLFW_KEY_A) || win.keyDown(GLFW.GLFW_KEY_D)) ? heldFrames + 1 : 0;

                if (win.keyPressed(GLFW.GLFW_KEY_ENTER) || win.keyPressed(GLFW.GLFW_KEY_SPACE)) {
                    Player p = new Player(CharacterDef.ROSTER.get(selected));
                    game = new Game(p);
                    game.win = win;
                }
                if (win.keyPressed(GLFW.GLFW_KEY_ESCAPE)) win.setShouldClose(true);

                renderMenu(win, selected);
            } else {
                // ---------- ИГРОВОЙ ЦИКЛ ----------
                updateAim(game, win);
                game.update(1f / 60f);
                renderGame(win, game);

                // рестарт после смерти / победы
                if (game.phase == Game.Phase.DEAD || game.phase == Game.Phase.VICTORY_WAIT) {
                    if (win.keyPressed(GLFW.GLFW_KEY_R)) {
                        game = null; // обратно в меню
                    }
                    if (win.keyPressed(GLFW.GLFW_KEY_ESCAPE)) win.setShouldClose(true);
                } else if (win.keyPressed(GLFW.GLFW_KEY_ESCAPE)) {
                    game = null; // брошить ран -> меню
                }
            }

            win.swapBuffers();
            win.endFrame();
        }
        win.close();
    }

    /** Перевод экранных координат мыши в мировые (для прицела). */
    private static void updateAim(Game g, GameWindow win) {
        double W = win.fbWidth(), H = win.fbHeight();
        g.aimWorld.set(g.camX + (win.mouseX() - W / 2.0) / g.zoom,
                        g.camY + (H / 2.0 - win.mouseY()) / g.zoom);
    }

    private static int menuInput(GameWindow win, int sel, int heldFrames) {
        int n = CharacterDef.ROSTER.size();
        boolean ok = heldFrames > 6; // задержка повтора
        if ((win.keyPressed(GLFW.GLFW_KEY_LEFT) || win.keyPressed(GLFW.GLFW_KEY_A) && ok) ) sel = (sel + n - 1) % n;
        if ((win.keyPressed(GLFW.GLFW_KEY_RIGHT) || win.keyPressed(GLFW.GLFW_KEY_D) && ok) ) sel = (sel + 1) % n;
        return sel;
    }

    // ==================================================================
    // Рендер меню
    // ==================================================================
    private static void renderMenu(GameWindow win, int selected) {
        int w = win.fbWidth(), h = win.fbHeight();
        Renderer.beginFrame(w, h);
        Renderer.clear(0.07, 0.07, 0.11);

        Renderer.textTr("ROR-LITE", w / 2 - 130, 40, 54, 1.0, 0.85, 0.3);
        Renderer.textTr("Roguelike-экшен в стиле Risk of Rain 2", w / 2 - 210, 104, 20, 0.7, 0.7, 0.8);
        Renderer.textTr("Выберите выжившего:", w / 2 - 90, 150, 22, 0.9, 0.9, 0.9);

        int cardW = 190, cardH = 260, gap = 18;
        int total = CharacterDef.ROSTER.size();
        int x0 = (w - total * (cardW + gap) + gap) / 2;
        int y0 = 190;
        for (int i = 0; i < total; i++) {
            CharacterDef c = CharacterDef.ROSTER.get(i);
            int cx = x0 + i * (cardW + gap);
            boolean sel = i == selected;
            Renderer.rect(cx, y0, cardW, cardH, sel ? 0.18 : 0.12, sel ? 0.18 : 0.12, sel ? 0.26 : 0.17);
            if (sel) Renderer.rectOutline(cx, y0, cardW, cardH, 3, 1.0, 0.85, 0.3);
            // «тело» персонажа
            Renderer.circle(cx + cardW / 2, y0 + 70, 34, 20, c.cr, c.cg, c.cb);
            Renderer.circle(cx + cardW / 2 + 12, y0 + 58, 8, 12, c.acr, c.acg, c.acb);
            Renderer.textTr(c.name, cx + 12, y0 + 118, 22, 1, 1, 1);
            // описание роли (перенос по словам)
            wrapText(c.roleDesc, cx + 12, y0 + 146, 15, 0.75, 0.78, 0.85, cardW - 24);
            String stats = "HP " + (int) c.maxHealth + "  DMG " + (int) c.attackDamage + "  SPD " + fmt(c.baseSpeed);
            Renderer.textTr(stats, cx + 12, y0 + cardH - 26, 14, 0.6, 0.9, 0.6);
        }

        CharacterDef c = CharacterDef.ROSTER.get(selected);
        int iy = y0 + cardH + 30;
        Renderer.textTr("Умения: [ЛКМ] " + c.primary.name + "   [Q] " + c.secondary.name
                + "   [E] " + c.utility.name + "   [R] " + c.special.name, 40, iy, 18, 0.95, 0.9, 0.6);
        Renderer.textTr("Управление: A/D или <-/-> движение, W/Space прыжок, ЛКМ атака,"
                + " F — взаимодействие с сундуками/лавками/порталом", 40, iy + 30, 16, 0.65, 0.7, 0.8);
        Renderer.textTr("Enter/Space — начать ран. ESC — выйти.", 40, iy + 56, 18, 1, 1, 1);

        Renderer.textTr("v0.2 build " + BUILD_TAG, w - 220, h - 24, 13, 0.45, 0.45, 0.55);
    }

    private static String fmt(double d) {
        return String.valueOf((int) (d * 10));
    }

    private static void wrapText(String s, double x, double y, double size, double r, double g, double b, double maxW) {
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        double yy = y;
        for (String wd : words) {
            String test = line.length() == 0 ? wd : line + " " + wd;
            if (test.length() * size * 0.55 > maxW) {
                Renderer.textTr(line.toString(), x, yy, size, r, g, b);
                yy += size + 4;
                line = new StringBuilder(wd);
            } else line = new StringBuilder(test);
        }
        if (line.length() > 0) Renderer.textTr(line.toString(), x, yy, size, r, g, b);
    }

    // ==================================================================
    // Рендер игры
    // ==================================================================
    private static final String BUILD_TAG = "2026-10-16";

    private static void renderGame(final GameWindow win, Game g) {
        int W = win.fbWidth(), H = win.fbHeight();
        Renderer.beginFrame(W, H);

        // небо по теме биома
        double[][] themes = {
            {0.10, 0.12, 0.18}, {0.16, 0.10, 0.14}, {0.08, 0.15, 0.12}, {0.13, 0.13, 0.10}, {0.09, 0.10, 0.17}
        };
        double[] th = themes[Math.floorMod(g.world.theme, themes.length)];
        Renderer.clear(th[0], th[1], th[2]);

        double zoom = g.zoom;
        // мир: y вверх -> экран: y вниз
        java.util.function.BiFunction<Double, Double, double[]> sx = (wx, wy) ->
                new double[]{(wx - g.camX) * zoom + W / 2.0, H / 2.0 - (wy - g.camY) * zoom};

        // --- тайлы ---
        var world = g.world;
        int tx0 = Math.max(0, World_tileX(g.camX - W / 2.0 / zoom));
        int tx1 = Math.min(world.wTiles - 1, World_tileX(g.camX + W / 2.0 / zoom) + 1);
        int ty0 = Math.max(0, World_tileX(g.camY - H / 2.0 / zoom));
        int ty1 = Math.min(world.hTiles - 1, World_tileX(g.camY + H / 2.0 / zoom) + 1);
        for (int tx = tx0; tx <= tx1; tx++) {
            for (int ty = ty0; ty <= ty1; ty++) {
                int v = world.tiles[ty * world.wTiles + tx];
                if (v == 0) continue;
                double wx = tx * world.TILE, wy = ty * world.TILE;
                double[] p = sx.apply(wx + world.TILE, wy + world.TILE); // верхний-левый угол тайла
                double tw = world.TILE * zoom + 1;
                if (v == 1) Renderer.rect(p[0], p[1], tw, tw, th[0] + 0.22, th[1] + 0.2, th[2] + 0.18);
                else Renderer.rect(p[0], p[1], tw, tw, th[0] + 0.32, th[1] + 0.26, th[2] + 0.22);
            }
        }

        // --- портал ---
        if (g.portalActive) {
            double[] pp = sx.apply(g.portalPos.x, g.portalPos.y);
            double pulse = 1 + 0.15 * Math.sin(System.currentTimeMillis() / 150.0);
            Renderer.circle(pp[0], pp[1], 2.2 * zoom * pulse, 24, 0.25, 0.7, 1.0);
            Renderer.circle(pp[0], pp[1], 1.4 * zoom * pulse, 20, 0.6, 0.95, 1.0);
            Renderer.textTr("ПОРТАЛ [F]", pp[0] - 60, pp[1] - 3.2 * zoom, 18, 0.4, 0.9, 1.0);
            if (g.portalCharge > 0) {
                Renderer.rect(pp[0] - 50, pp[1] + 2.6 * zoom, 100 * (g.portalCharge / 3.0), 8, 0.3, 0.9, 1.0);
                Renderer.rectOutline(pp[0] - 50, pp[1] + 2.6 * zoom, 100, 8, 1, 0.7, 0.8, 0.9);
            }
        } else {
            double[] pp = sx.apply(g.portalPos.x, g.portalPos.y);
            Renderer.circleOutline(pp[0], pp[1], 1.6 * zoom, 20, 2, 0.3, 0.4, 0.5);
            Renderer.textTr("Портал спит", pp[0] - 55, pp[1] - 2.8 * zoom, 14, 0.4, 0.5, 0.6);
        }

        // --- интерактивы ---
        for (Interactable in : g.interactables) {
            double[] p = sx.apply(in.pos.x, in.pos.y);
            if (p[0] < -80 || p[0] > W + 80) continue;
            drawInteractable(in, p[0], p[1], zoom);
        }

        // --- питомцы (турели/мины) ---
        for (Pet pt : g.pets) {
            if (!pt.alive) continue;
            double[] p = sx.apply(pt.pos().x, pt.pos().y);
            switch (pt.kind) {
                case MINE -> {
                    Renderer.circle(p[0], p[1], 0.45 * zoom, 14, 0.9, 0.3, 0.2);
                    Renderer.circle(p[0], p[1], 0.18 * zoom, 10, 1.0, 0.9, 0.3);
                }
                case TURRET -> {
                    Renderer.rect(p[0] - 0.5 * zoom, p[1] - 0.1 * zoom, zoom, zoom * 0.5, 0.45, 0.5, 0.6);
                    Renderer.line(p[0], p[1], p[0] + pt.aimDir.x * zoom, p[1] - pt.aimDir.y * zoom, 4, 0.8, 0.85, 0.9);
                }
                case MEGA_TURRET -> {
                    Renderer.rect(p[0] - 0.8 * zoom, p[1] - 0.1 * zoom, zoom * 1.6, zoom * 0.7, 0.3, 0.55, 0.75);
                    Renderer.line(p[0], p[1], p[0] + pt.aimDir.x * zoom * 1.5, p[1] - pt.aimDir.y * zoom * 1.5, 6, 0.4, 0.9, 1.0);
                }
            }
        }

        // --- враги ---
        for (Enemy e : g.enemies) {
            double[] p = sx.apply(e.pos().x, e.pos().y);
            if (p[0] < -120 || p[0] > W + 120) continue;
            drawEnemy(e, p[0], p[1], zoom);
        }

        // --- игрок ---
        if (g.player.alive) {
            var c = g.player.def;
            double[] p = sx.apply(g.player.pos().x, g.player.pos().y);
            double rw = c.bodyRadius * zoom;
            if (g.player.invulnTimer > 0 && (System.currentTimeMillis() / 80) % 2 == 0) {
                // мигание при неуязвимости
            } else {
                Renderer.circle(p[0], p[1], rw, 20, c.cr, c.cg, c.cb);
            }
            Renderer.circle(p[0] + g.player.facing * rw * 0.45, p[1] - rw * 0.25, rw * 0.3, 12, c.acr, c.acg, c.acb);
            // полоска HP над игроком
            bar(p[0] - 30, p[1] - rw - 14, 60, 7, hpFrac(g.player), 0.25, 0.9, 0.35);
        }

        // --- снаряды ---
        for (Projectile pr : g.projectiles) {
            double[] p = sx.apply(pr.pos.x, pr.pos.y);
            Renderer.circle(p[0], p[1], Math.max(2, pr.radius * zoom), 8, pr.cr, pr.cg, pr.cb);
        }

        // --- лучи ---
        for (Fx.Beam b : g.beams) {
            double[] a = sx.apply(b.x1, b.y1), z = sx.apply(b.x2, b.y2);
            Renderer.line(a[0], a[1], z[0], z[1], b.width, b.cr, b.cg, b.cb);
        }

        // --- частицы ---
        for (Fx.Particle pa : g.particles) {
            double[] p = sx.apply(pa.pos.x, pa.pos.y);
            double a = pa.life / pa.maxLife;
            Renderer.rect(p[0] - pa.size * zoom / 2, p[1] - pa.size * zoom / 2,
                          pa.size * zoom, pa.size * zoom, pa.cr * a, pa.cg * a, pa.cb * a);
        }

        // --- всплывающий текст ---
        for (Fx.FloatText t : g.texts) {
            double[] p = sx.apply(t.x, t.y);
            double a = Math.min(1, t.life * 2);
            Renderer.textTr(t.text, p[0], p[1], t.size, t.cr * a, t.cg * a, t.cb * a);
        }

        drawHud(win, g, W, H);
    }

    private static int World_tileX(double worldCoord) {
        return (int) Math.floor(worldCoord / rorlite.engine.World.TILE);
    }

    private static double hpFrac(Player p) {
        double mx = p.totalMaxHp();
        return mx <= 0 ? 0 : Math.max(0, Math.min(1, p.hp / mx));
    }

    private static double hpFrac(Enemy e) {
        return e.maxHp <= 0 ? 0 : Math.max(0, Math.min(1, e.hp / e.maxHp));
    }

    private static void bar(double x, double y, double w, double h, double frac, double r, double g, double b) {
        Renderer.rect(x, y, w, h, 0.1, 0.1, 0.12);
        Renderer.rect(x, y, w * frac, h, r, g, b);
        Renderer.rectOutline(x, y, w, h, 1, 0.35, 0.35, 0.4);
    }

    private static void drawEnemy(Enemy e, double px, double py, double zoom) {
        double rad = e.radius() * zoom;
        double br = 0.85, bg = 0.25, bb = 0.2; // базовый красный
        if (e.elite) { br = 0.95; bg = 0.35; bb = 0.95; }
        if (!e.alive) { // анимация смерти — оседание
            Renderer.circle(px, py + rad * 0.4, rad * 0.6, 10, br * 0.4, bg * 0.4, bb * 0.4);
            return;
        }
        switch (e.defId) {
            case "bee" -> {
                Renderer.circle(px, py, rad, 14, 0.9, 0.8, 0.2);
                double fl = Math.sin(System.currentTimeMillis() / 60.0 + e.flightPhase) * rad * 0.5;
                Renderer.circle(px - rad, py + fl, rad * 0.4, 8, 0.95, 0.95, 0.95);
                Renderer.circle(px + rad, py - fl, rad * 0.4, 8, 0.95, 0.95, 0.95);
            }
            case "bird" -> {
                Renderer.circle(px, py, rad, 16, 0.5, 0.65, 0.85);
                Renderer.rect(px + rad * 0.5, py - rad * 0.2, rad * 0.7, rad * 0.3, 0.95, 0.8, 0.3);
            }
            case "beetle" -> {
                Renderer.rect(px - rad, py - rad * 0.7, rad * 2, rad * 1.4, 0.3, 0.28, 0.35);
                Renderer.rectOutline(px - rad, py - rad * 0.7, rad * 2, rad * 1.4, 2, br, bg, bb);
            }
            case "lizardshooter" -> {
                Renderer.circle(px, py, rad, 14, 0.35, 0.7, 0.4);
                Renderer.circle(px + e.facing * rad * 0.5, py, rad * 0.3, 8, 0.95, 0.9, 0.3);
            }
            default -> { // гриб и боссы
                boolean boss = e.ai == Enemy.AiMode.BOSS;
                if (boss) {
                    Renderer.circle(px, py, rad, 24, br * 0.9, bg * 0.5, bb * 1.2);
                    Renderer.circleOutline(px, py, rad + 6, 24, 3, 1, 0.4, 0.2);
                } else {
                    Renderer.rect(px - rad * 0.35, py - rad, rad * 0.7, rad * 1.4, 0.85, 0.8, 0.7);
                    Renderer.circle(px, py - rad * 0.9, rad * 0.9, 14, br, bg, bb);
                }
            }
        }
        // полоска HP
        double bw = Math.max(26, rad * 2);
        bar(px - bw / 2, py - rad - 12, bw, 5, hpFrac(e), e.ai == Enemy.AiMode.BOSS ? 1 : 0.9, 0.25, 0.25);
        if (e.ai == Enemy.AiMode.BOSS) {
            Renderer.textTr(tr(e.displayName()), px - bw / 2, py - rad - 28, 15, 1, 0.6, 0.6);
        }
    }

    private static void drawInteractable(Interactable in, double px, double py, double zoom) {
        double rs = in.radius * zoom;
        switch (in.kind) {
            case CHEST -> {
                if (in.used) { Renderer.rect(px - rs * 0.7, py - rs * 0.3, rs * 1.4, rs * 0.6, 0.3, 0.25, 0.2); }
                else {
                    Renderer.rect(px - rs * 0.7, py - rs * 0.5, rs * 1.4, rs, 0.65, 0.45, 0.2);
                    Renderer.rect(px - rs * 0.7, py - rs * 0.5, rs * 1.4, rs * 0.35, 0.8, 0.6, 0.25);
                    Renderer.rect(px - rs * 0.1, py - rs * 0.25, rs * 0.2, rs * 0.25, 1, 0.9, 0.3);
                    progressRing(in, px, py, rs);
                }
            }
            case SHOP_1, SHOP_2 -> {
                Renderer.rect(px - rs * 0.8, py - rs * 0.6, rs * 1.6, rs * 1.2, 0.25, 0.35, 0.5);
                Renderer.circle(px, py - rs * 0.9, rs * 0.35, 12, 0.4, 0.9, 0.5);
                String label = (in.shopItem != null ? in.shopItem.name + "  " + (int) in.price : "Лавка")
                        + (in.used ? " [пусто]" : "");
                Renderer.textTr(label, px - rs * 1.4, py - rs * 1.7, 14, 0.7, 0.9, 0.7);
                progressRing(in, px, py, rs);
            }
            case BUSH -> {
                if (!in.used) {
                    Renderer.circle(px, py, rs * 0.8, 12, 0.2, 0.55, 0.25);
                    Renderer.circle(px - rs * 0.4, py + rs * 0.2, rs * 0.5, 10, 0.25, 0.6, 0.3);
                }
            }
            case DOLL -> {
                if (!in.used) {
                    Renderer.rect(px - rs * 0.3, py - rs, rs * 0.6, rs * 2, 0.85, 0.75, 0.6);
                    Renderer.circle(px, py - rs * 1.1, rs * 0.35, 10, 0.9, 0.8, 0.65);
                    Renderer.textTr("Разбей!", px - 26, py + rs * 1.3, 12, 0.8, 0.7, 0.5);
                } else {
                    Renderer.rect(px - rs * 0.3, py + rs * 0.4, rs * 0.6, rs * 0.5, 0.5, 0.45, 0.4);
                }
            }
            case BOSS_CHALLENGE -> {
                boolean ready = !in.used;
                Renderer.rect(px - rs * 0.9, py - rs * 0.2, rs * 1.8, rs * 0.8, ready ? 0.5 : 0.25, ready ? 0.2 : 0.2, ready ? 0.55 : 0.28);
                if (ready) {
                    double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 200.0);
                    Renderer.circle(px, py - rs * 0.6, rs * 0.4 * pulse + rs * 0.2, 16, 0.9, 0.3, 0.6);
                    Renderer.textTr("Алтарь босса [F]", px - 60, py - rs * 1.6, 15, 1, 0.5, 0.7);
                } else {
                    Renderer.textTr("Босс пробуждён", px - 55, py - rs * 1.4, 13, 0.6, 0.4, 0.5);
                }
                progressRing(in, px, py, rs);
            }
        }
    }

    private static void progressRing(Interactable in, double px, double py, double rs) {
        if (in.openTime > 0 && !in.used) {
            double need = in.kind == Interactable.Kind.CHEST ? 1.2 : 0.8;
            double f = Math.min(1, in.openTime / need);
            Renderer.rect(px - rs * 0.7, py + rs * 0.9, rs * 1.4 * f, 5, 1, 0.9, 0.3);
            Renderer.rectOutline(px - rs * 0.7, py + rs * 0.9, rs * 1.4, 5, 1, 0.6, 0.6, 0.4);
        }
    }

    // ==================================================================
    // HUD
    // ==================================================================
    private static void drawHud(GameWindow win, Game g, int W, int H) {
        Player p = g.player;
        // Здоровье
        bar(20, 20, 260, 22, hpFrac(p), 0.25, 0.9, 0.35);
        Renderer.textTr((int) Math.ceil(Math.max(0, p.hp)) + " / " + (int) p.totalMaxHp(), 26, 24, 16, 1, 1, 1);
        // Деньги и убийства
        Renderer.textTr("Луны: " + (int) p.money, 20, 50, 16, 0.6, 0.95, 0.95);
        Renderer.textTr("Убийств: " + (int) g.killCount, 130, 50, 16, 0.9, 0.8, 0.6);
        Renderer.textTr("Уровень " + g.tier + "/" + Game.MAX_TIER + "   Время: " + (int) g.timeAlive + "с",
                250, 50, 16, 0.85, 0.85, 0.9);

        // Кулдауны умений
        String[] keys = {"ЛКМ", "Q", "E", "R"};
        for (int slot = 0; slot < 4; slot++) {
            var ad = p.abilityDef(slot);
            double x = 20 + slot * 90, y = 78;
            boolean ready = p.cooldowns[slot] <= 0;
            Renderer.rect(x, y, 80, 46, ready ? 0.2 : 0.11, ready ? 0.22 : 0.11, ready ? 0.3 : 0.15);
            Renderer.rectOutline(x, y, 80, 46, 1, 0.4, 0.4, 0.5);
            Renderer.textTr(keys[slot] + " " + tr(ad.name), x + 5, y + 6, 13, ready ? 1 : 0.5, ready ? 1 : 0.5, ready ? 1 : 0.6);
            if (!ready) Renderer.textTr((int) Math.ceil(p.cooldowns[slot]) + "с", x + 5, y + 26, 14, 0.9, 0.6, 0.3);
        }

        // Инвентарь (последние предметы)
        int ix = 20;
        for (var en : p.inventory.entrySet()) {
            var it = rorlite.data.Items.byId(en.getKey());
            if (it == null) continue;
            Renderer.rect(ix, H - 40, 26, 26, it.cr * 0.6, it.cg * 0.6, it.cb * 0.6);
            Renderer.rectOutline(ix, H - 40, 26, 26, 1, it.cr, it.cg, it.cb);
            if (en.getValue() > 1) Renderer.textTr("x" + en.getValue(), ix + 28, H - 34, 12, 1, 1, 1);
            ix += 46;
            if (ix > W - 60) break;
        }

        // Лог-сообщение
        if (g.logTimer > 0 && !g.lastLog.isEmpty()) {
            Renderer.textTr(tr(g.lastLog), W / 2 - g.lastLog.length() * 5, H - 90, 18, 1, 0.95, 0.6);
        }

        // Босс-бар сверху
        if (g.boss != null && g.boss.alive) {
            bar(W / 2 - 250, 16, 500, 16, hpFrac(g.boss), 0.95, 0.25, 0.3);
            Renderer.textTr(tr(g.boss.displayName()), W / 2 - 60, 36, 15, 1, 0.7, 0.7);
        }

        // Прицел — прямо по позиции курсора мыши
        Renderer.circleOutline(win.mouseX(), win.mouseY(), 8, 12, 2, 1, 0.4, 0.4);

        // ---- Оверлеи состояний ----
        if (g.phase == Game.Phase.DEAD) {
            Renderer.rect(0, 0, W, H, 0.05, 0.02, 0.02);
            Renderer.textTr("ВЫ ПОГИБЛИ", W / 2 - 130, H / 2 - 60, 48, 0.95, 0.3, 0.3);
            Renderer.textTr("Убийств: " + (int) g.killCount + "   Уровень: " + g.tier + "   Время: " + (int) g.timeAlive + "с",
                    W / 2 - 180, H / 2 + 10, 20, 0.8, 0.8, 0.85);
            Renderer.textTr("[R] — в меню   [ESC] — выход", W / 2 - 140, H / 2 + 50, 18, 1, 0.9, 0.5);
        } else if (g.phase == Game.Phase.VICTORY_WAIT) {
            Renderer.rect(0, 0, W, H, 0.03, 0.05, 0.08);
            Renderer.textTr("ЭВАКУАЦИЯ! ПОБЕДА!", W / 2 - 220, H / 2 - 60, 44, 0.4, 0.95, 0.5);
            Renderer.textTr("Все 4 уровня пройдены, финальный босс повержен.", W / 2 - 230, H / 2 + 10, 20, 0.85, 0.9, 0.9);
            Renderer.textTr("Убийств: " + (int) g.killCount + "   Время рана: " + (int) g.timeAlive + "с",
                    W / 2 - 140, H / 2 + 45, 20, 0.85, 0.9, 0.9);
            Renderer.textTr("[R] — новый ран   [ESC] — выход", W / 2 - 150, H / 2 + 90, 18, 1, 0.9, 0.5);
        }
    }

    private static String tr(String s) { return Renderer.tr(s); }
}
