package rorlite;

import rorlite.data.CharacterDef;
import rorlite.game.Enemy;
import rorlite.game.Game;
import rorlite.game.Player;

/**
 * Headless-смоуктест: без окна и рендера прогоняет игровую логику (физика, ИИ,
 * урон, смерть врагов) на нескольких персонажах. Используется скриптом build.sh
 * для проверки, что сборка не только компилируется, но и логика работает.
 * Запуск: java -cp "build:libs/*" rorlite.HeadlessSmokeTest
 */
public final class HeadlessSmokeTest {

    public static void main(String[] args) {
        int frames = 0;
        for (CharacterDef def : CharacterDef.ROSTER) {
            Player p = new Player(def);
            Game g = new Game(p);
            for (int i = 0; i < 1200; i++) { // ~20 секунд игрового времени на персонажа
                g.update(1f / 60f);
                frames++;
            }
            System.out.printf("[SMOKE] %s: tier=%d alive=%b kills=%.0f phase=%s%n",
                    def.name, g.tier, p.alive, g.killCount, g.phase);
        }

        // Боевой тест: сразим босса «в лоб» — проверяем путь урона по врагам.
        Player p = new Player(CharacterDef.ROSTER.get(0));
        Game g = new Game(p);
        int killed = 0;
        for (int i = 0; i < 300 && !g.enemies.isEmpty(); i++) {
            for (Enemy e : new java.util.ArrayList<>(g.enemies)) {
                if (e.alive) { e.takeDamage(99999, p, g); killed++; }
            }
            g.update(1f / 60f);
        }
        System.out.printf("[SMOKE] Combat: killed=%d killCounter=%.0f enemiesLeft=%d%n",
                killed, g.killCount, g.enemies.size());

        // Тест предметов: выдадим по одному предмету каждого тира в инвентарь.
        for (rorlite.data.Item it : rorlite.data.Items.ALL) p.addItem(it, g);
        g.update(1f / 60f);
        System.out.printf("[SMOKE] Items: inventory=%d dmgMult=%.2f spdMult=%.2f%n",
                p.inventory.size(), p.damageMult, p.moveSpeedMult);

        System.out.println("[SMOKE] OK — " + frames + " кадров симуляции без исключений.");
    }
}
