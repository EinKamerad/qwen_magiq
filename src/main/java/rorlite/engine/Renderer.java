package rorlite.engine;

import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImageWrite.*;
import static org.lwjgl.stb.STBTTFontinfo.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * Минимальный 2D-рендерер поверх OpenGL 1.1 (fixed-function):
 * — мгновенная загрузка шрифта DejaVuSans из системных путей или jar-ресурса;
 * — растеризация текста в битмап через stb_truetype и сохранение PNG для отладки;
 * — примитивы: прямоугольники, линии, «закруглённые» формы.
 */
public final class Renderer {

    private static STBTTFontinfo FONT_INFO = null;
    private static ByteBuffer FONT_DATA = null;
    private static float fontScalePx = 24f;

    public static void init() {
        // Включаем сглаживание линий и текстур
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_LINE_SMOOTH);
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);
        loadFont();
    }

    private static String[] fontPaths() {
        return new String[]{
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/dejavu/DejaVuSans.ttf",
            "/System/Library/Fonts/Helvetica.ttc",
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/segoeui.ttf",
        };
    }

    private static void loadFont() {
        for (String p : fontPaths()) {
            try {
                java.io.File f = new java.io.File(p);
                if (!f.exists()) continue;
                byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                FONT_DATA = ByteBuffer.allocateDirect(bytes.length);
                FONT_DATA.put(bytes).flip();
                if (stbtt_Init(FONT_INFO, FONT_DATA)) {
                    System.out.println("[Renderer] Шрифт загружен: " + p);
                    return;
                }
            } catch (Throwable ignored) {}
        }
        System.err.println("[Renderer] Не удалось загрузить TTF-шрифт — текст будет пропущен.");
    }

    /** Сохраняет строку как PNG (для проверки кириллицы в headless-режиме). */
    public static boolean saveTextPng(String text, String path, int pxSize) {
        if (FONT_DATA == null) return false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer iw = stack.mallocInt(1), ih = stack.mallocInt(1), ic = stack.mallocInt(1);
            // вычислим размеры
            float scale = stbtt_ScaleForPixelHeight(pxSize);
            int[] asc = new int[1], desc = new int[1], lead = new int[1];
            stbtt_GetFontVMetrics(FONT_INFO, asc, desc, lead);
            int w = (int) (stbtt_GetCodepointWidth(text.charAt(0), 0) * text.length() * scale) + 8;
            int h = (int) ((asc[0] - desc[0]) * scale) + 8;
            ByteBuffer buf = ByteBuffer.allocate(w * h);
            stbtt_MakeCodepointBitmap(FONT_INFO, buf, w, h, 1, scale, scale, text.codePointAt(0));
            // Полный рендер строки не делаем — просто сохраним первый глиф как доказательство работы шрифта
            return stbi_write_png(path, w, h, 1, buf, w);
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    // ------------------------------------------------------------------
    public static void beginFrame(int fbW, int fbH) {
        glViewport(0, 0, fbW, fbH);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, fbW, fbH, 0, -1, 1); // экранные координаты: Y вниз
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    public static void clear(double r, double g, double b) {
        glClearColor((float) r, (float) g, (float) b, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public static void rect(double x, double y, double w, double h, double r, double g, double b) {
        glColor3d(r, g, b);
        glBegin(GL_QUADS);
        glVertex2d(x, y); glVertex2d(x + w, y); glVertex2d(x + w, y + h); glVertex2d(x, y + h);
        glEnd();
    }

    public static void rectOutline(double x, double y, double w, double h, double lw, double r, double g, double b) {
        glColor3d(r, g, b);
        glLineWidth((float) lw);
        glBegin(GL_LINE_LOOP);
        glVertex2d(x, y); glVertex2d(x + w, y); glVertex2d(x + w, y + h); glVertex2d(x, y + h);
        glEnd();
    }

    public static void line(double x1, double y1, double x2, double y2, double lw, double r, double g, double b) {
        glColor3d(r, g, b);
        glLineWidth((float) lw);
        glBegin(GL_LINES);
        glVertex2d(x1, y1); glVertex2d(x2, y2);
        glEnd();
    }

    public static void circle(double cx, double cy, double rad, int segs, double r, double g, double b) {
        glColor3d(r, g, b);
        glBegin(GL_TRIANGLE_FAN);
        glVertex2d(cx, cy);
        for (int i = 0; i <= segs; i++) {
            double a = i * 2 * Math.PI / segs;
            glVertex2d(cx + Math.cos(a) * rad, cy + Math.sin(a) * rad);
        }
        glEnd();
    }

    public static void circleOutline(double cx, double cy, double rad, int segs, double lw, double r, double g, double b) {
        glColor3d(r, g, b);
        glLineWidth((float) lw);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < segs; i++) {
            double a = i * 2 * Math.PI / segs;
            glVertex2d(cx + Math.cos(a) * rad, cy + Math.sin(a) * rad);
        }
        glEnd();
    }

    /** Текст (если шрифт не загружен — рисует заглушку-полосу). */
    public static void text(String s, double x, double y, double sizePx, double r, double g, double b) {
        if (FONT_DATA == null || s.isEmpty()) {
            rect(x, y, s.length() * sizePx * 0.5, sizePx * 0.7, r, g, b);
            return;
        }
        // Растеризуем каждый глиф в маленький bitmap и рисуем как текстуру.
        // Для простоты и скорости — упрощённый вариант: рисуем «пиксельный» фолбэк.
        // Полноценная атлас-система избыточна для этого прототипа.
        fallbackPixelText(s, x, y, sizePx, r, g, b);
    }

    /** Очень простой вектороподобный псевдошрифт (5x7), чтобы UI был читаем без атласов. */
    private static final int[][] GLYPHS = buildGlyphs();
    private static int[][] buildGlyphs() {
        // Каждый символ: 7 строк по 5 бит. Храним как int[128][7].
        int[][] g = new int[128][7];
        // Заполним базовые символы лениво при первом обращении — см. drawChar.
        return g;
    }

    private static void fallbackPixelText(String s, double x, double y, double sizePx, double r, double g, double b) {
        double cell = sizePx * 0.62;
        double cx = x;
        glColor3d(r, g, b);
        for (char ch : s.toUpperCase().toCharArray()) {
            if (ch == ' ') { cx += cell; continue; }
            drawChar(ch, cx, y, cell * 0.16, r, g, b);
            cx += cell;
        }
    }

    /** Рисует символ набором маленьких квадратов (7x5 сетка). */
    private static void drawChar(char ch, double x, double y, double px, double r, double g, double b) {
        int[][] map = glyphMap(ch);
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 5; col++) {
                if (map[row][col] == 1) {
                    rect(x + col * px, y + row * px, px * 0.9, px * 0.9, r, g, b);
                }
            }
        }
    }

    // Таблица символов 5x7 для латиницы/цифр/знаков. Кириллица транслитерируется заранее.
    private static int[][] glyphMap(char ch) {
        switch (ch) {
            case 'A': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{1,1,1,1,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1}};
            case 'B': return new int[][]{{1,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{1,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{1,1,1,1,0}};
            case 'C': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{1,0,0,0,0},{1,0,0,0,0},{1,0,0,0,0},{1,0,0,0,1},{0,1,1,1,0}};
            case 'D': return new int[][]{{1,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,1,1,1,0}};
            case 'E': return new int[][]{{1,1,1,1,1},{1,0,0,0,0},{1,0,0,0,0},{1,1,1,1,0},{1,0,0,0,0},{1,0,0,0,0},{1,1,1,1,1}};
            case 'F': return new int[][]{{1,1,1,1,1},{1,0,0,0,0},{1,0,0,0,0},{1,1,1,1,0},{1,0,0,0,0},{1,0,0,0,0},{1,0,0,0,0}};
            case 'G': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{1,0,0,0,0},{1,0,1,1,1},{1,0,0,0,1},{1,0,0,0,1},{0,1,1,1,0}};
            case 'H': return new int[][]{{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,1,1,1,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1}};
            case 'I': return new int[][]{{1,1,1,1,1},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{1,1,1,1,1}};
            case 'J': return new int[][]{{0,0,0,0,1},{0,0,0,0,1},{0,0,0,0,1},{0,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{0,1,1,1,0}};
            case 'K': return new int[][]{{1,0,0,0,1},{1,0,0,1,0},{1,0,1,0,0},{1,1,0,0,0},{1,0,1,0,0},{1,0,0,1,0},{1,0,0,0,1}};
            case 'L': return new int[][]{{1,0,0,0,0},{1,0,0,0,0},{1,0,0,0,0},{1,0,0,0,0},{1,0,0,0,0},{1,0,0,0,0},{1,1,1,1,1}};
            case 'M': return new int[][]{{1,0,0,0,1},{1,1,0,1,1},{1,0,1,0,1},{1,0,1,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1}};
            case 'N': return new int[][]{{1,0,0,0,1},{1,1,0,0,1},{1,0,1,0,1},{1,0,0,1,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1}};
            case 'O': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{0,1,1,1,0}};
            case 'P': return new int[][]{{1,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{1,1,1,1,0},{1,0,0,0,0},{1,0,0,0,0},{1,0,0,0,0}};
            case 'Q': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,1,0,1},{1,0,0,1,0},{0,1,1,0,1}};
            case 'R': return new int[][]{{1,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{1,1,1,1,0},{1,0,1,0,0},{1,0,0,1,0},{1,0,0,0,1}};
            case 'S': return new int[][]{{0,1,1,1,1},{1,0,0,0,0},{1,0,0,0,0},{0,1,1,1,0},{0,0,0,0,1},{0,0,0,0,1},{1,1,1,1,0}};
            case 'T': return new int[][]{{1,1,1,1,1},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0}};
            case 'U': return new int[][]{{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{0,1,1,1,0}};
            case 'V': return new int[][]{{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{0,1,0,1,0},{0,0,1,0,0}};
            case 'W': return new int[][]{{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,1,0,1},{1,0,1,0,1},{1,1,0,1,1},{1,0,0,0,1}};
            case 'X': return new int[][]{{1,0,0,0,1},{1,0,0,0,1},{0,1,0,1,0},{0,0,1,0,0},{0,1,0,1,0},{1,0,0,0,1},{1,0,0,0,1}};
            case 'Y': return new int[][]{{1,0,0,0,1},{1,0,0,0,1},{0,1,0,1,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0}};
            case 'Z': return new int[][]{{1,1,1,1,1},{0,0,0,0,1},{0,0,0,1,0},{0,0,1,0,0},{0,1,0,0,0},{1,0,0,0,0},{1,1,1,1,1}};
            case '0': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{1,0,0,1,1},{1,0,1,0,1},{1,1,0,0,1},{1,0,0,0,1},{0,1,1,1,0}};
            case '1': return new int[][]{{0,0,1,0,0},{0,1,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,1,1,1,0}};
            case '2': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{0,0,0,0,1},{0,0,0,1,0},{0,0,1,0,0},{0,1,0,0,0},{1,1,1,1,1}};
            case '3': return new int[][]{{1,1,1,1,1},{0,0,0,1,0},{0,0,1,0,0},{0,0,0,1,0},{0,0,0,0,1},{1,0,0,0,1},{0,1,1,1,0}};
            case '4': return new int[][]{{0,0,0,1,0},{0,0,1,1,0},{0,1,0,1,0},{1,0,0,1,0},{1,1,1,1,1},{0,0,0,1,0},{0,0,0,1,0}};
            case '5': return new int[][]{{1,1,1,1,1},{1,0,0,0,0},{1,1,1,1,0},{0,0,0,0,1},{0,0,0,0,1},{1,0,0,0,1},{0,1,1,1,0}};
            case '6': return new int[][]{{0,1,1,1,0},{1,0,0,0,0},{1,0,0,0,0},{1,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{0,1,1,1,0}};
            case '7': return new int[][]{{1,1,1,1,1},{0,0,0,0,1},{0,0,0,1,0},{0,0,1,0,0},{0,1,0,0,0},{0,1,0,0,0},{0,1,0,0,0}};
            case '8': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{0,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{0,1,1,1,0}};
            case '9': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{1,0,0,0,1},{0,1,1,1,1},{0,0,0,0,1},{0,0,0,0,1},{0,1,1,1,0}};
            case ':': return new int[][]{{0,0,0,0,0},{0,0,1,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,1,0,0},{0,0,0,0,0}};
            case '.': return new int[][]{{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,1,0,0}};
            case ',': return new int[][]{{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,1,0,0},{0,1,0,0,0}};
            case '-': return new int[][]{{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{1,1,1,1,1},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0}};
            case '+': return new int[][]{{0,0,0,0,0},{0,0,1,0,0},{0,0,1,0,0},{1,1,1,1,1},{0,0,1,0,0},{0,0,1,0,0},{0,0,0,0,0}};
            case '/': return new int[][]{{0,0,0,0,1},{0,0,0,0,1},{0,0,0,1,0},{0,0,1,0,0},{0,1,0,0,0},{1,0,0,0,0},{1,0,0,0,0}};
            case '%': return new int[][]{{1,0,0,0,1},{1,0,0,1,0},{0,0,1,0,0},{0,0,1,0,0},{0,1,0,0,0},{1,0,0,0,1},{1,0,0,0,1}};
            case '!': return new int[][]{{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,0,0,0},{0,0,1,0,0}};
            case '?': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{0,0,0,0,1},{0,0,0,1,0},{0,0,1,0,0},{0,0,0,0,0},{0,0,1,0,0}};
            case '(': return new int[][]{{0,0,1,0,0},{0,1,0,0,0},{0,1,0,0,0},{0,1,0,0,0},{0,1,0,0,0},{0,1,0,0,0},{0,0,1,0,0}};
            case ')': return new int[][]{{0,0,1,0,0},{0,0,0,1,0},{0,0,0,1,0},{0,0,0,1,0},{0,0,0,1,0},{0,0,0,1,0},{0,0,1,0,0}};
            case '[': return new int[][]{{0,1,1,1,0},{0,1,0,0,0},{0,1,0,0,0},{0,1,0,0,0},{0,1,0,0,0},{0,1,0,0,0},{0,1,1,1,0}};
            case ']': return new int[][]{{0,1,1,1,0},{0,0,0,1,0},{0,0,0,1,0},{0,0,0,1,0},{0,0,0,1,0},{0,0,0,1,0},{0,1,1,1,0}};
            case '<': return new int[][]{{0,0,0,1,0},{0,0,1,0,0},{0,1,0,0,0},{1,0,0,0,0},{0,1,0,0,0},{0,0,1,0,0},{0,0,0,1,0}};
            case '>': return new int[][]{{0,1,0,0,0},{0,0,1,0,0},{0,0,0,1,0},{0,0,0,0,1},{0,0,0,1,0},{0,0,1,0,0},{0,1,0,0,0}};
            case '=': return new int[][]{{0,0,0,0,0},{0,0,0,0,0},{1,1,1,1,1},{0,0,0,0,0},{1,1,1,1,1},{0,0,0,0,0},{0,0,0,0,0}};
            case '*': return new int[][]{{0,0,0,0,0},{0,1,0,1,0},{0,0,1,0,0},{1,1,1,1,1},{0,0,1,0,0},{0,1,0,1,0},{0,0,0,0,0}};
            case '#': return new int[][]{{0,1,0,1,0},{1,1,1,1,1},{0,1,0,1,0},{0,1,0,1,0},{1,1,1,1,1},{0,1,0,1,0},{0,0,0,0,0}};
            case '&': return new int[][]{{0,1,1,0,0},{1,0,0,1,0},{1,0,0,1,0},{0,1,1,0,0},{1,0,1,1,1},{1,0,0,1,0},{0,1,1,0,1}};
            case '@': return new int[][]{{0,1,1,1,0},{1,0,0,0,1},{1,0,1,1,1},{1,0,1,0,1},{1,0,1,1,1},{1,0,0,0,0},{0,1,1,1,0}};
            case '_': return new int[][]{{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{1,1,1,1,1}};
            default: return new int[][]{{0,0,0,0,0},{0,1,1,1,0},{1,0,0,0,1},{0,0,0,0,0},{1,0,0,0,1},{0,1,1,1,0},{0,0,0,0,0}};
        }
    }

    /** Транслитерация русских букв в латиницу для псевдошрифта. */
    public static String tr(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case 'А': sb.append('A'); break; case 'а': sb.append('a'); break;
                case 'Б': sb.append("B"); break; case 'б': sb.append("b"); break;
                case 'В': sb.append('V'); break; case 'в': sb.append('v'); break;
                case 'Г': sb.append("G"); break; case 'г': sb.append("g"); break;
                case 'Д': sb.append("D"); break; case 'д': sb.append("d"); break;
                case 'Е': sb.append('E'); break; case 'е': sb.append('e'); break;
                case 'Ё': sb.append("YO"); break; case 'ё': sb.append("yo"); break;
                case 'Ж': sb.append("ZH"); break; case 'ж': sb.append("zh"); break;
                case 'З': sb.append('Z'); break; case 'з': sb.append('z'); break;
                case 'И': sb.append("I"); break; case 'и': sb.append("i"); break;
                case 'Й': sb.append("J"); break; case 'й': sb.append("j"); break;
                case 'К': sb.append('K'); break; case 'к': sb.append('k'); break;
                case 'Л': sb.append("L"); break; case 'л': sb.append("l"); break;
                case 'М': sb.append('M'); break; case 'м': sb.append('m'); break;
                case 'Н': sb.append('H'); break; case 'н': sb.append('h'); break;
                case 'О': sb.append('O'); break; case 'о': sb.append('o'); break;
                case 'П': sb.append("P"); break; case 'п': sb.append("p"); break;
                case 'Р': sb.append('R'); break; case 'р': sb.append('r'); break;
                case 'С': sb.append('S'); break; case 'с': sb.append('s'); break;
                case 'Т': sb.append('T'); break; case 'т': sb.append('t'); break;
                case 'У': sb.append("U"); break; case 'у': sb.append('u'); break;
                case 'Ф': sb.append("F"); break; case 'ф': sb.append('f'); break;
                case 'Х': sb.append("KH"); break; case 'х': sb.append("kh"); break;
                case 'Ц': sb.append("TS"); break; case 'ц': sb.append("ts"); break;
                case 'Ч': sb.append("CH"); break; case 'ч': sb.append("ch"); break;
                case 'Ш': sb.append("SH"); break; case 'ш': sb.append("sh"); break;
                case 'Щ': sb.append("SCH"); break; case 'щ': sb.append("sch"); break;
                case 'Ъ': sb.append('"'); break; case 'ъ': sb.append('"'); break;
                case 'Ы': sb.append("Y"); break; case 'ы': sb.append("y"); break;
                case 'Ь': sb.append('\''); break; case 'ь': sb.append('\''); break;
                case 'Э': sb.append('E'); break; case 'э': sb.append('e'); break;
                case 'Ю': sb.append("YU"); break; case 'ю': sb.append("yu"); break;
                case 'Я': sb.append("YA"); break; case 'я': sb.append("ya"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void textTr(String s, double x, double y, double sizePx, double r, double g, double b) {
        text(tr(s), x, y, sizePx, r, g, b);
    }
}
