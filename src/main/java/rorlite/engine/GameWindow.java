package rorlite.engine;

import org.lwjgl.glfw.*;
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** Обёртка GLFW: окно, ввод (клавиши + мышь). */
public final class GameWindow {
    public long handle;
    public int width = 1280, height = 720;

    private final boolean[] keys = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private final boolean[] pressedThisFrame = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private final boolean[] releasedThisFrame = new boolean[GLFW.GLFW_KEY_LAST + 1];

    private double mouseX, mouseY;
    private boolean mousePressed, mouseReleased, mouseHeld;

    public GameWindow(String title) {
        if (!glfwInit()) throw new RuntimeException("Не удалось инициализировать GLFW");
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        handle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (handle == NULL) throw new RuntimeException("Не удалось создать окно");

        GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vm != null) {
            glfwSetWindowPos(handle, (vm.width() - width) / 2, (vm.height() - height) / 2);
        }

        glfwSetKeyCallback(handle, (h, key, sc, action, mods) -> {
            if (key < 0 || key >= keys.length) return;
            if (action == GLFW_PRESS) { keys[key] = true; pressedThisFrame[key] = true; }
            else if (action == GLFW_RELEASE) { keys[key] = false; releasedThisFrame[key] = true; }
        });
        glfwSetCursorPosCallback(handle, (h, x, y) -> { mouseX = x; mouseY = y; });
        glfwSetMouseButtonCallback(handle, (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) { mouseHeld = true; mousePressed = true; }
                else { mouseHeld = false; mouseReleased = true; }
            }
        });
        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1); // vsync
    }

    public void pollEvents() { glfwPollEvents(); }
    public void swapBuffers() { glfwSwapBuffers(handle); }
    public boolean shouldClose() { return glfwWindowShouldClose(handle); }
    public void setShouldClose(boolean v) { glfwSetWindowShouldClose(handle, v); }

    /** Вызывать в конце кадра — сбрасывает одноразовые флаги. */
    public void endFrame() {
        java.util.Arrays.fill(pressedThisFrame, false);
        java.util.Arrays.fill(releasedThisFrame, false);
        mousePressed = false;
        mouseReleased = false;
    }

    public boolean keyDown(int key) { return key >= 0 && key < keys.length && keys[key]; }
    public boolean keyPressed(int key) { return key >= 0 && key < pressedThisFrame.length && pressedThisFrame[key]; }
    public boolean keyReleased(int key) { return key >= 0 && key < releasedThisFrame.length && releasedThisFrame[key]; }

    public double mouseX() { return mouseX; }
    public double mouseY() { return mouseY; }
    public boolean mousePressed() { return mousePressed; }
    public boolean mouseReleased() { return mouseReleased; }
    public boolean mouseHeld() { return mouseHeld; }

    public int fbWidth() {
        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(handle, w, h);
        return w[0];
    }
    public int fbHeight() {
        int[] w = new int[1], h = new int[1];
        glfwGetFramebufferSize(handle, w, h);
        return h[0];
    }

    public void close() {
        glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
