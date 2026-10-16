package rorlite.data;

/**
 * Предмет (item) в стиле RoR2: складывается стаками, имеет тир и текстовое описание.
 * Эффекты применяются через Game.applyItem(...) по id предмета.
 */
public class Item {
    public final String id;
    public final String name;       // Отображаемое имя (транслит при рендере)
    public final Tier tier;
    public final String desc;
    /** Цвет иконки в инвентаре. */
    public final double cr, cg, cb;

    public enum Tier { TIER1, TIER2, TIER3, LEGENDARY, BOSS }

    public Item(String id, String name, Tier tier, String desc, double cr, double cg, double cb) {
        this.id = id; this.name = name; this.tier = tier; this.desc = desc;
        this.cr = cr; this.cg = cg; this.cb = cb;
    }

    public static double tierValue(Tier t) {
        switch (t) {
            case TIER1: return 1.0;
            case TIER2: return 3.0;
            case TIER3: return 9.0;
            case LEGENDARY: return 27.0;
            default: return 100.0;
        }
    }

    /** Редкость дропа из сундуков — чем выше тир, тем реже. */
    public static double tierDropWeight(Tier t) {
        switch (t) {
            case TIER1: return 60;
            case TIER2: return 28;
            case TIER3: return 9;
            case LEGENDARY: return 3;
            default: return 0;
        }
    }
}
