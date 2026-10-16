package rorlite.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Персонаж (survivor) в стиле RoR2: базовые статы + набор умений.
 * Умения описаны параметрически, логика исполнения — в rorlite.game.abilities.
 */
public class CharacterDef {
    public enum AbilitySlot { PRIMARY, SECONDARY, UTILITY, SPECIAL }

    public static class AbilityDef {
        public final String name;
        public final double cooldown;   // сек
        public final String desc;
        public final int damage;
        public AbilityDef(String name, double cooldown, String desc, int damage) {
            this.name = name; this.cooldown = cooldown; this.desc = desc; this.damage = damage;
        }
    }

    public final String id;
    public final String name;
    public final String roleDesc;
    // Базовые статы (как в RoR2: health, armor, moveSpeed, attackDamage...)
    public double maxHealth = 100;
    public double armor = 0;
    public double baseSpeed = 6.5;       // ед/с
    public double accel = 60;            // разгон
    public double jumpForce = 17;
    public double attackDamage = 12;
    public double attackRate = 2.0;      // атак/сек
    public double attackRange = 4.0;     // дальность (ближний/дальний)
    public boolean melee = false;
    public double projSpeed = 28;        // скорость снаряда
    public double bodyRadius = 0.9;
    public double mass = 1.0;
    // Цвета тела для рендера
    public double cr, cg, cb, acr, acg, acb; // акцент
    // Умения
    public final AbilityDef primary, secondary, utility, special;

    public CharacterDef(String id, String name, String roleDesc,
                        AbilityDef primary, AbilityDef secondary, AbilityDef utility, AbilityDef special) {
        this.id = id; this.name = name; this.roleDesc = roleDesc;
        this.primary = primary; this.secondary = secondary; this.utility = utility; this.special = special;
    }

    public static final List<CharacterDef> ROSTER = new ArrayList<>();

    private static CharacterDef add(CharacterDef c) { ROSTER.add(c); return c; }

    // ------------------------------------------------------------------
    /** Коммандо — аналог Commando: сбалансированный стрелок. */
    public static final CharacterDef COMMANDO = add(new CharacterDef(
        "commando", "Коммандо", "Сбалансированный боец. Стреляет очередями, имеет гранату и турель.",
        new AbilityDef("Очередь", 0.35, "Стрельба пулями по курсору (основная атака).", 12),
        new AbilityDef("Граната", 8, "Бросает гранату: урон по площади + отбрасывание.", 60),
        new AbilityDef("Рывок", 10, "Быстрый рывок в сторону движения с неуязвимостью.", 0),
        new AbilityDef("Турель", 30, "Устанавливает турель, стреляющую по врагам 12 сек.", 8)
    ) {{
        maxHealth = 125; baseSpeed = 6.8; attackDamage = 12; attackRate = 2.2; melee = false;
        cr = 0.85; cg = 0.72; cb = 0.35; acr = 0.95; acg = 0.55; acb = 0.2; // жёлто-песочный
    }});

    /** Бандит — аналог Bandit: высокий урон, низкое здоровье, дэш-удар. */
    public static final CharacterDef BANDIT = add(new CharacterDef(
        "bandit", "Бандит", "Хрупкий, но смертельный. Быстрые пистолеты и теневой удар.",
        new AbilityDef("Двойной выстрел", 0.28, "Две пули веером по курсору.", 10),
        new AbilityDef("Кинжал", 6, "Метает кинжал, пробивающий врагов насквозь.", 45),
        new AbilityDef("Смерч", 9, "Подбрасывает себя и ближайших врагов в воздух.", 20),
        new AbilityDef("Теневой удар", 20, "Телепорт к самому дорогому врагу рядом, x4 урона.", 80)
    ) {{
        maxHealth = 90; armor = 20; baseSpeed = 7.6; attackDamage = 10; attackRate = 3.2;
        cr = 0.45; cg = 0.4; cb = 0.6; acr = 0.8; acg = 0.3; acb = 0.8; // фиолетовый
    }});

    /** Молотобоец — аналог Acrid: ядовитый ближний бой. */
    public static final CharacterDef ACRID = add(new CharacterDef(
        "acrid", "Молотобоец", "Ядовитый воин ближнего боя. Плюётся кислотой, прыгает как саранча.",
        new AbilityDef("Плевки", 0.4, "Кислотные плювки, накладывающие яд.", 14),
        new AbilityDef("Жало", 7, "Выплевывает шип, взрывающийся облаком яда.", 40),
        new AbilityDef("Прыжок", 5, "Мощный прыжок вверх-вперёд (без кулдауна в воздухе).", 0),
        new AbilityDef("Отравляющий рой", 25, "Аура яда: все враги вокруг получают урон 8 сек.", 12)
    ) {{
        maxHealth = 140; baseSpeed = 6.2; attackDamage = 14; attackRate = 1.8; melee = false;
        jumpForce = 20; mass = 1.2;
        cr = 0.35; cg = 0.75; cb = 0.4; acr = 0.9; acg = 0.85; acb = 0.2; // зелёный яд
    }});

    /** Инженер — аналог Engineer: оборонительный, ставит турели и мины. */
    public static final CharacterDef ENGINEER = add(new CharacterDef(
        "engineer", "Инженер", "Мастер обороны. Лазерная винтовка, турели и липучие мины.",
        new AbilityDef("Лазер", 0.5, "Лазерный луч, пронзающий одну цель.", 18),
        new AbilityDef("Мина", 8, "Ставит мину, взрывающуюся при подходе врага.", 70),
        new AbilityDef("Платформа", 14, "Создаёт парящую платформу для позиционирования.", 0),
        new AbilityDef("Мега-турель", 35, "Мощная турель с дробовиком, держит позицию 15 сек.", 14)
    ) {{
        maxHealth = 110; armor = 10; baseSpeed = 6.0; attackDamage = 18; attackRate = 1.6;
        cr = 0.5; cg = 0.6; cb = 0.8; acr = 1.0; acg = 0.8; acb = 0.3; // синий метал
    }});

    /** Медик — аналог Medic: поддержка, лечение ауры, быстрые клинки. */
    public static final CharacterDef MEDIC = add(new CharacterDef(
        "medic", "Медик", "Юркий целитель. Кидает дротики, лечится сам, крушит всё «вампирским» стилем.",
        new AbilityDef("Дротик", 0.3, "Быстрый дротик, лечащий 1 HP при попадании.", 9),
        new AbilityDef("Исцеление", 12, "Взрыв лечения: +40 HP себе.", 0),
        new AbilityDef("Рывок душ", 8, "Мгновенное перемещение в направлении взгляда.", 0),
        new AbilityDef("Кровавая баня", 22, "6 сек: скорость атаки x2, вампиризм 20% урона.", 0)
    ) {{
        maxHealth = 100; armor = 15; baseSpeed = 8.2; attackDamage = 9; attackRate = 3.5;
        cr = 0.9; cg = 0.9; cb = 0.95; acr = 0.95; acg = 0.3; acb = 0.4; // белый/красный
    }});

    /** Гигант — аналог Goliath/Titan: медленный танк с сокрушительными ударами. */
    public static final CharacterDef GOLEM = add(new CharacterDef(
        "golem", "Голем", "Танк-сокрушитель. Медлителен, но каждый удар трясёт землю.",
        new AbilityDef("Землетряс", 0.8, "Удар по земле: урон конусом перед собой.", 30),
        new AbilityDef("Валун", 10, "Катит камень, таранящий всех на пути.", 55),
        new AbilityDef("Прыжок гиганта", 12, "Прыжок с мощным приземлением: урон вокруг.", 45),
        new AbilityDef("Каменный шторм", 30, "Метеориты падают на врагов 5 сек.", 35)
    ) {{
        maxHealth = 220; armor = 30; baseSpeed = 4.6; accel = 40; attackDamage = 30; attackRate = 0.9;
        melee = true; attackRange = 3.2; bodyRadius = 1.3; mass = 2.5; jumpForce = 14;
        cr = 0.55; cg = 0.5; cb = 0.45; acr = 0.85; acg = 0.6; acb = 0.3; // каменный
    }});
}
