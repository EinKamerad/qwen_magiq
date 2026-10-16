package rorlite.game;

import rorlite.engine.Rng;
import rorlite.engine.Vec2;

/** Вспомогательные визуальные сущности: частицы, цифры урона, лучи. */
public final class Fx {

    public static class Particle {
        public Vec2 pos, vel;
        public double life, maxLife;
        public double cr, cg, cb;
        public double size;
        public boolean gravity = false;
    }

    public static class FloatText {
        public String text;
        public double x, y, vy, life;
        public double cr, cg, cb;
        public double size;
    }

    public static class Beam {
        public double x1, y1, x2, y2;
        public double life;
        public double cr, cg, cb;
        public double width;
    }

    public static Particle particle(Vec2 pos, Vec2 vel, double life, double size, double cr, double cg, double cb, boolean grav) {
        Particle p = new Particle();
        p.pos = pos.copy(); p.vel = vel.copy();
        p.life = p.maxLife = life;
        p.size = size; p.cr = cr; p.cg = cg; p.cb = cb; p.gravity = grav;
        return p;
    }

    public static void burst(java.util.List<Particle> out, Vec2 at, int n, double speed, double life, double size, double cr, double cg, double cb, boolean grav) {
        for (int i = 0; i < n; i++) {
            double a = Rng.d(0, Math.PI * 2);
            double s = Rng.d(0.3, 1) * speed;
            out.add(particle(at, new Vec2(Math.cos(a) * s, Math.sin(a) * s),
                    life * Rng.d(0.6, 1.2), size * Rng.d(0.7, 1.4), cr, cg, cb, grav));
        }
    }

    public static FloatText floatText(String t, double x, double y, double cr, double cg, double cb, double size) {
        FloatText f = new FloatText();
        f.text = t; f.x = x; f.y = y; f.vy = -3.2; f.life = 0.9;
        f.cr = cr; f.cg = cg; f.cb = cb; f.size = size;
        return f;
    }

    public static Beam beam(double x1, double y1, double x2, double y2, double life, double w, double cr, double cg, double cb) {
        Beam b = new Beam();
        b.x1 = x1; b.y1 = y1; b.x2 = x2; b.y2 = y2; b.life = life; b.width = w;
        b.cr = cr; b.cg = cg; b.cb = cb;
        return b;
    }
}
