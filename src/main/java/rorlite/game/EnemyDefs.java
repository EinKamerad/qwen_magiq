package rorlite.game;

import rorlite.data.Team;
import rorlite.engine.Rng;
import rorlite.engine.Vec2;

/** Каталог врагов: фабрика, масштабирующая статы по тиру уровня. */
public final class EnemyDefs {

    public static String[] BASIC_IDS = {"mushroom", "bird", "bee", "lizardshooter", "beetle"};

    /** Создаёт врага указанного типа с масштабированием под тир уровня. */
    public static Enemy create(String id, int tier, Vec2 pos) {
        double hpScale = Math.pow(1.55, tier - 1);   // RoR2-подобный рост
        double dmgScale = Math.pow(1.45, tier - 1);
        Enemy e;
        switch (id) {
            case "mushroom" -> { // аналог Lemurians/Mushers — медленный милишник
                e = new Enemy("mushroom", "Гриб", 60 * hpScale, 0, 3.2, 12 * dmgScale, 1.8, 0.9, Team.ENEMY);
                e.ai = Enemy.AiMode.MELEE_CHARGER;
                e.attackInterval = 1.4;
            }
            case "bird" -> { // аналог Gulp/Gannick-птиц — стрелок наземный
                e = new Enemy("bird", "Птицелюд", 45 * hpScale, 5, 4.5, 10 * dmgScale, 12, 0.8, Team.ENEMY);
                e.ai = Enemy.AiMode.SHOOTER;
                e.projSpeed = 14;
                e.attackInterval = 1.6;
            }
            case "bee" -> { // аналог Bees/Drone — летающий мешающий стрелок
                e = new Enemy("bee", "Дрон", 35 * hpScale, 0, 6.0, 8 * dmgScale, 9, 0.55, Team.ENEMY);
                e.ai = Enemy.AiMode.FLYER;
                e.projSpeed = 12;
                e.attackInterval = 1.2;
            }
            case "lizardshooter" -> { // аналог Lizard Pugs — быстрый милишник-прыгун
                e = new Enemy("lizardshooter", "Ящер", 80 * hpScale, 10, 5.5, 15 * dmgScale, 2.0, 0.95, Team.ENEMY);
                e.ai = Enemy.AiMode.MELEE_CHARGER;
                e.attackInterval = 1.1;
            }
            case "beetle" -> { // аналог Beetles — танк
                e = new Enemy("beetle", "Жук", 140 * hpScale, 25, 2.6, 20 * dmgScale, 2.2, 1.15, Team.ENEMY);
                e.ai = Enemy.AiMode.MELEE_CHARGER;
                e.attackInterval = 1.8;
            }
            case "munchkin" -> { // мишка-охранитель из кустов/кукол
                e = new Enemy("munchkin", "Медведь", 100 * hpScale, 15, 5.0, 18 * dmgScale, 2.0, 1.0, Team.ENEMY);
                e.ai = Enemy.AiMode.MELEE_CHARGER;
            }
            default -> {
                e = new Enemy("mushroom", "Гриб", 60 * hpScale, 0, 3.2, 12 * dmgScale, 1.8, 0.9, Team.ENEMY);
                e.ai = Enemy.AiMode.MELEE_CHARGER;
            }
        }
        e.body.pos.set(pos);
        return e;
    }

    /** Боссы уровней: каждый сильнее предыдущего. */
    public static Enemy createBoss(int tier, Vec2 pos) {
        double hpScale = Math.pow(1.7, tier - 1);
        double dmgScale = Math.pow(1.5, tier - 1);
        Enemy b;
        switch (tier) {
            case 1 -> { // аналог Stone Golem
                b = new Enemy("boss_golem", "Каменный голем", 900 * hpScale, 40, 3.2, 30 * dmgScale, 3.2, 2.0, Team.ENEMY);
                b.ai = Enemy.AiMode.BOSS;
                b.attackInterval = 1.6;
            }
            case 2 -> { // аналог Wandering Vagrant / Voidling
                b = new Enemy("boss_void", "Пустотный скиталец", 1400 * hpScale, 60, 4.2, 38 * dmgScale, 3.5, 1.8, Team.ENEMY);
                b.ai = Enemy.AiMode.BOSS;
                b.shoots = true;
                b.projSpeed = 18;
                b.attackInterval = 1.4;
            }
            case 3 -> { // аналог Solus Twin / Titan
                b = new Enemy("boss_titan", "Титан", 2200 * hpScale, 80, 3.6, 48 * dmgScale, 4.0, 2.4, Team.ENEMY);
                b.ai = Enemy.AiMode.BOSS;
                b.attackInterval = 1.2;
            }
            default -> { // финальный босс — аналог Void Fiend
                b = new Enemy("boss_voidfiend", "Пустотный гад", 4000 * hpScale, 100, 4.6, 60 * dmgScale, 4.2, 2.6, Team.ENEMY);
                b.ai = Enemy.AiMode.BOSS;
                b.shoots = true;
                b.projSpeed = 20;
                b.attackInterval = 1.0;
            }
        }
        b.body.pos.set(pos);
        b.invulnTimer = 1.5; // «пробуждение»
        return b;
    }

    public static String randomBasic() { return Rng.pick(BASIC_IDS); }
}
