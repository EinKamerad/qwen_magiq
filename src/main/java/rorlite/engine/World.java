package rorlite.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Тайловая карта уровня: генерируется процедурно (платформы, стены),
 * как арены-«острова» в RoR2. Плитки: 0 = пусто, 1 = земля, 2 = камень/край.
 */
public class World {
    public static final double TILE = 2.0; // размер плитки в мировых единицах

    public final int wTiles, hTiles;
    public final int[] tiles;
    public final List<double[]> spawnPoints = new ArrayList<>(); // точки спавна врагов (центр плитки)
    public final double[] playerSpawn;
    public final double[] portalPos;   // позиция портала в конце уровня
    public final int theme;            // визуальная тема (биом)

    public World(int wTiles, int hTiles, int theme) {
        this.wTiles = wTiles;
        this.hTiles = hTiles;
        this.tiles = new int[wTiles * hTiles];
        this.theme = theme;
        this.playerSpawn = new double[2];
        this.portalPos = new double[2];
    }

    public boolean solid(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= wTiles || ty >= hTiles) return true; // за пределами — стена
        return tiles[ty * wTiles + tx] != 0;
    }

    public void set(int tx, int ty, int v) {
        if (tx >= 0 && ty >= 0 && tx < wTiles && ty < hTiles) tiles[ty * wTiles + tx] = v;
    }

    public static int tileIndex(double worldCoord) {
        return (int) Math.floor(worldCoord / TILE);
    }

    public double widthWorld() { return wTiles * TILE; }
    public double heightWorld() { return hTiles * TILE; }

    /** Находит твёрдую поверхность под точкой и возвращает Y поверхности. */
    public double groundBelow(double x, int startTy) {
        for (int ty = startTy; ty < hTiles; ty++) {
            if (solid(tileIndex(x), ty)) return ty * TILE;
        }
        return hTiles * TILE;
    }

    // ------------------------------------------------------------------
    /** Процедурная генерация «арены»: пол, платформы, уступы, стены по краям. */
    public static World generate(int tier) {
        int wt = 64 + tier * 6;          // шире с каждым уровнем
        int ht = 30;
        int theme = Rng.i(0, 4);
        World w = new World(wt, ht, theme);

        // внешний пол
        int floorTy = ht - 4;
        fillRect(w, 0, floorTy, wt, ht, 1);

        // стены по краям (арена замкнута, как в RoR2)
        fillRect(w, 0, 0, 1, ht, 2);
        fillRect(w, wt - 1, 0, 1, ht, 2);

        // платформы — несколько ярусов
        int platforms = 7 + tier;
        for (int i = 0; i < platforms; i++) {
            int pw = Rng.i(4, 9);
            int px = Rng.i(3, wt - 4 - pw);
            int py = Rng.i(6, floorTy - 3);
            int thickness = Rng.i(1, 2);
            fillRect(w, px, py, pw, py + thickness, 1);
            // иногда выступ-столбик вниз
            if (Rng.chance(0.35)) {
                fillRect(w, px + pw / 2, py + thickness, px + pw / 2 + 1, py + thickness + Rng.i(1, 3), 1);
            }
        }

        // пара уступов из пола (чтобы уровень не был плоским)
        for (int i = 0; i < 3 + tier / 2; i++) {
            int cw = Rng.i(3, 6);
            int cx = Rng.i(4, wt - 5 - cw);
            int ch = Rng.i(1, 3);
            fillRect(w, cx, floorTy - ch, cx + cw, floorTy, 1);
        }

        // точка игрока — примерно центр пола
        double pxm = (wt / 2.0) * TILE;
        w.playerSpawn[0] = pxm;
        w.playerSpawn[1] = w.groundBelow(pxm, 1) - 1.2;

        // портал — далеко от спавна, на полу
        double ppx = (wt - 8) * TILE;
        w.portalPos[0] = ppx;
        w.portalPos[1] = w.groundBelow(ppx, 1) - 1.5;

        // точки спавна врагов — на всех платформах и полу
        for (int tx = 2; tx < wt - 2; tx++) {
            for (int ty = 2; ty < ht - 2; ty++) {
                if (w.solid(tx, ty) && !w.solid(tx, ty - 1) && !w.solid(tx, ty - 2)) {
                    // есть свободное пространство над плиткой
                    if (Math.abs(tx * TILE - pxm) > 12) {
                        w.spawnPoints.add(new double[]{tx * TILE + TILE / 2, ty * TILE - 1.1});
                    }
                }
            }
        }
        return w;
    }

    private static void fillRect(World w, int x0, int y0, int x1, int y1, int v) {
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++)
                w.set(x, y, v);
    }
}
