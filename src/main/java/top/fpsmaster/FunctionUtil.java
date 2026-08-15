package top.fpsmaster;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

// Approved from MIN, time-based functions.
public class FunctionUtil<T extends Number> {
    private final long startTime;
    private final Function<Long, T> function;
    private final long duration;
    private final T target;

    private FunctionUtil(long startTime, long duration, @NotNull T target, @NotNull Function<Long, T> function) {
        this.startTime = startTime;
        this.function = function;
        this.duration = duration;
        this.target = target;
    }

    public static @NotNull FunctionUtil<Integer> linear(int start, int target, long duration) {
        long currentTime = System.currentTimeMillis();
        int step = target - start;
        return new FunctionUtil<>(currentTime, duration, target, elapsedTime -> Math.round(((float) elapsedTime / duration) * step + start));
    }

    public static @NotNull FunctionUtil<Float> linear(float start, float target, long duration) {
        long currentTime = System.currentTimeMillis();
        float step = target - start;
        return new FunctionUtil<>(currentTime, duration, target, elapsedTime -> ((float) elapsedTime / duration) * step + start);
    }

    public static @NotNull FunctionUtil<Float> cubicBezier(float start, float target, int duration, float x1, float y1, float x2, float y2) {
        long currentTime = System.currentTimeMillis();

        return new FunctionUtil<>(currentTime, duration, target, elapsedTime -> {
            float x = (float) elapsedTime / duration;
            if (x >= 1.0f) return target;
            if (x <= 0.0f) return start;
            float t = getBezierTForX(x, x1, x2);
            float progress = calcBezier(t, y1, y2);
            return start + progress * (target - start);
        });
    }

    public static @NotNull FunctionUtil<Integer> easeOutQuad(int start, int target, long duration) {
        long currentTime = System.currentTimeMillis();
        return new FunctionUtil<>(currentTime, duration, target, elapsedTime -> Math.round((1 - (1 - (float) elapsedTime / duration) * (1 - (float) elapsedTime / duration)) * (target - start) + start));
    }

    public static @NotNull FunctionUtil<Float> easeOutQuad(float start, float target, long duration) {
        long currentTime = System.currentTimeMillis();
        return new FunctionUtil<>(currentTime, duration, target, elapsedTime -> (1 - (1 - (float) elapsedTime / duration) * (1 - (float) elapsedTime / duration)) * (target - start) + start);
    }

    public static @NotNull FunctionUtil<Integer> cubicBezier(int start, int target, long duration, float x1, float y1, float x2, float y2) {
        long currentTime = System.currentTimeMillis();

        return new FunctionUtil<>(currentTime, duration, target, elapsedTime -> {
            float x = (float) elapsedTime / duration;
            if (x >= 1.0f) return target;
            if (x <= 0.0f) return start;
            float t = getBezierTForX(x, x1, x2);
            float progress = calcBezier(t, y1, y2);
            return Math.round(start + progress * (target - start));
        });
    }

    public static @NotNull FunctionUtil<Float> cubicBezier(float start, float target, long duration, float x1, float y1, float x2, float y2) {
        long currentTime = System.currentTimeMillis();

        return new FunctionUtil<>(currentTime, duration, target, elapsedTime -> {
            float x = (float) elapsedTime / duration;
            if (x >= 1.0f) return target;
            if (x <= 0.0f) return start;
            float t = getBezierTForX(x, x1, x2);
            float progress = calcBezier(t, y1, y2);
            return start + progress * (target - start);
        });
    }

    public static @NotNull FunctionUtil<Integer> done(int value) {
        long currentTime = System.currentTimeMillis();
        return new FunctionUtil<>(currentTime, 0, value, elapsedTime -> value);
    }

    public static @NotNull FunctionUtil<Float> done(float value) {
        long currentTime = System.currentTimeMillis();
        return new FunctionUtil<>(currentTime, 0, value, elapsedTime -> value);
    }

    public T calculate() {
        if (System.currentTimeMillis() - startTime > duration) {
            return target;
        }
        return function.apply(System.currentTimeMillis() - startTime);
    }

    private static float calcBezier(float t, float p1, float p2) {
        return 3.0f * (1.0f - t) * (1.0f - t) * t * p1
                + 3.0f * (1.0f - t) * t * t * p2
                + t * t * t;
    }

    private static float getSlope(float t, float p1, float p2) {
        return 3.0f * (1.0f - t) * (1.0f - t) * p1
                + 6.0f * (1.0f - t) * t * (p2 - p1)
                + 3.0f * t * t;
    }

    @SuppressWarnings("all")
    private static float getBezierTForX(float percentageX, float x1, float x2) {
        float t = percentageX;
        for (int i = 0; i < 4; i++) {
            float currentX = calcBezier(t, x1, x2) - percentageX;
            float slope = getSlope(t, x1, x2);
            if (slope == 0.0f) break;
            t -= currentX / slope;
        }
        return Math.min(1.0f, Math.max(0.0f, t));
    }
}
