package rorlite.data;

import java.util.ArrayList;
import java.util.List;

/** Каталог всех предметов игры (аналоги предметов RoR2). */
public final class Items {
    public static final List<Item> ALL = new ArrayList<>();

    private static Item add(Item i) { ALL.add(i); return i; }

    // ---- TIER 1 ----
    public static final Item SPORE = add(new Item("spore", "Грибные споры", Item.Tier.TIER1,
        "+15% скорости атаки. Каждое 3-е попадание отравляет.", 0.45, 0.8, 0.35));
    public static final Item POLY = add(new Item("poly", "Полиморф-ключ", Item.Tier.TIER1,
        "+100 здоровья, +10% урона.", 0.9, 0.7, 0.3));
    public static final Item JETPACK = add(new Item("jetpack", "Ранец", Item.Tier.TIER1,
        "Двойной прыжок с реактивным импульсом.", 0.6, 0.7, 0.9));
    public static final Item LEECH = add(new Item("leech", "Кровавый жезл", Item.Tier.TIER1,
        "Восстанавливает 2 HP за убийство.", 0.8, 0.2, 0.2));
    public static final Item ARMORPLATE = add(new Item("armorplate", "Броневая пластина", Item.Tier.TIER1,
        "+1 брони (снижает получаемый урон).", 0.7, 0.7, 0.8));
    public static final Item CRITHALF = add(new Item("crithalf", "Половина крит-глаза", Item.Tier.TIER1,
        "+7% шанс критического удара x2.", 0.9, 0.9, 0.4));

    // ---- TIER 2 ----
    public static final Item BERSERKER = add(new Item("berserker", "Берсеркер", Item.Tier.TIER2,
        "+30% скорости атаки, -15% скорости передвижения.", 0.85, 0.3, 0.25));
    public static final Item SATURD = add(new Item("saturd", "Насыщенный серный камень", Item.Tier.TIER2,
        "+100 здоровья, +10 регена в секунду.", 0.7, 0.6, 0.2));
    public static final Item WARHAMMER = add(new Item("warhammer", "Молот войны", Item.Tier.TIER2,
        "Каждые 3 сек следующая атака наносит тройной урон.", 0.5, 0.55, 0.85));
    public static final Item RABBITFOOT = add(new Item("rabbitfoot", "Задняя лапа кролика", Item.Tier.TIER2,
        "+10% уклонения.", 0.75, 0.65, 0.55));
    public static final Item REDEMPING = add(new Item("redemption", "Красная redemption-звезда", Item.Tier.TIER2,
        "При низком HP: взрыв, наносящий 200 урона вокруг.", 0.95, 0.35, 0.2));
    public static final Item LIGHTNING = add(new Item("lightning", "Перечное печенье", Item.Tier.TIER2,
        "Атаки бьют молнией по цепи (3 цели, 25 урона).", 0.5, 0.8, 0.95));

    // ---- TIER 3 ----
    public static final Item ATCEED = add(new Item("atceed", "A.T.C.E.", Item.Tier.TIER3,
        "+35% скорости передвижения.", 0.35, 0.85, 0.6));
    public static final Item ROOT = add(new Item("root", "Корень всевидящего", Item.Tier.TIER3,
        "+250 здоровья, аура: враги рядом замедляются.", 0.4, 0.7, 0.4));
    public static final Item GLACK = add(new Item("glack", "Glacier-монокольцо", Item.Tier.TIER3,
        "Атаки замедляют цель на 2 сек.", 0.5, 0.75, 1.0));
    public static final Item VITALIK = add(new Item("vitalik", "Живой сок", Item.Tier.TIER3,
        "+2 HP/сек регенерации, +100 здоровья.", 0.3, 0.8, 0.45));
    public static final Item SOULCOCOON = add(new Item("soulcocoon", "Кокон душ", Item.Tier.TIER3,
        "Убийства дают +2 макс. здоровья навсегда.", 0.75, 0.4, 0.85));

    // ---- LEGENDARY ----
    public static final Item RUBY = add(new Item("ruby", "Рубин власти", Item.Tier.LEGENDARY,
        "+20% урона всем атакам.", 0.95, 0.2, 0.25));
    public static final Item DIODE = add(new Item("diode", "Диод", Item.Tier.LEGENDARY,
        "+15% урона и +15% скорости передвижения.", 0.9, 0.85, 0.4));
    public static final Item QUARTZ = add(new Item("quartz", "Кварц", Item.Tier.LEGENDARY,
        "+20% скорости атаки и перезарядки умений.", 0.7, 0.9, 0.95));

    // ---- BOSS ----
    public static final Item CROWN = add(new Item("crown", "Королевская корона", Item.Tier.BOSS,
        "Легендарный трофей босса. +25% ко всему урону.", 1.0, 0.85, 0.2));

    public static Item byId(String id) {
        for (Item i : ALL) if (i.id.equals(id)) return i;
        return null;
    }

    /** Случайный предмет заданного тира (для дропа). */
    public static Item randomOfTier(Item.Tier t) {
        List<Item> l = new ArrayList<>();
        for (Item i : ALL) if (i.tier == t) l.add(i);
        return rorlite.engine.Rng.pick(l);
    }

    /** Взвешенный случайный предмет из пула дропа. */
    public static Item rollDrop() {
        double total = 0;
        for (Item.Tier t : new Item.Tier[]{Item.Tier.TIER1, Item.Tier.TIER2, Item.Tier.TIER3, Item.Tier.LEGENDARY})
            total += Item.tierDropWeight(t);
        double r = rorlite.engine.Rng.R.nextDouble() * total;
        Item.Tier chosen = Item.Tier.TIER1;
        double acc = 0;
        for (Item.Tier t : new Item.Tier[]{Item.Tier.TIER1, Item.Tier.TIER2, Item.Tier.TIER3, Item.Tier.LEGENDARY}) {
            acc += Item.tierDropWeight(t);
            if (r <= acc) { chosen = t; break; }
        }
        return randomOfTier(chosen);
    }
}
