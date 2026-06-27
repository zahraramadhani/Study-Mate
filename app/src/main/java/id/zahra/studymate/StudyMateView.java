package id.zahra.studymate;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * StudyMate's compact UI prototype.  It intentionally keeps the whole demo in a
 * single, dependency-free custom view so it is easy to run in a fresh Android
 * Studio project while still providing real navigation and local persistence.
 */
public class StudyMateView extends View {
    private static final int SPLASH = 0, LOGIN = 1, HOME = 2, TASKS = 3, ADD_TASK = 4,
            DETAIL = 5, FOCUS = 6, PROGRESS = 7, PROFILE = 8, COURSES = 9,
            CALENDAR = 10, SHOP = 11;

    private static final int NAVY = Color.rgb(12, 24, 84);
    private static final int BLUE = Color.rgb(64, 82, 244);
    private static final int BLUE_2 = Color.rgb(73, 110, 245);
    private static final int MUTED = Color.rgb(100, 113, 151);
    private static final int LINE = Color.rgb(225, 229, 242);
    private static final int PALE = Color.rgb(247, 249, 255);
    private static final int GREEN = Color.rgb(29, 173, 104);
    private static final int ORANGE = Color.rgb(245, 132, 28);
    private static final int RED = Color.rgb(232, 53, 78);
    private static final int PURPLE = Color.rgb(119, 70, 235);
    private static final Typeface TEXT_NORMAL = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface TEXT_BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    private final MainActivity activity;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF drawingRect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private SharedPreferences dataPrefs;
    private final List<Task> tasks = new ArrayList<>();
    private final List<String> courses = new ArrayList<>();
    private final List<FocusSession> focusHistory = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID"));
    private float scale = 1f;
    private float offsetX;
    private float logicalHeight = 720;
    private int screen = SPLASH;
    private int filter = 0;
    private String search = "";
    private String loginEmail = "";
    private String loginPassword = "";
    private String profileName = "Zahra Sanjaya";
    private String profileUniversity = "";
    private String profileMajor = "";
    private String profileSemester = "";
    private Task selectedTask;
    private Task draft;
    private boolean editing;
    private boolean fontLarge;
    private boolean highContrast;
    private boolean darkMode;
    private boolean voiceEnabled = true;
    private boolean companionActive;
    private String characterClass = "Ksatria";
    public String getCharacterClass() { return characterClass != null ? characterClass : "Ksatria"; }
    private boolean accessibilityExpanded;
    private int xp;
    private int streak;
    private int coins;
    private int focusSessions;
    private int totalFocusMinutes;
    private int timerPreset = 25;
    private int timerSeconds = 25 * 60;
    private boolean timerRunning;
    private long lastTimerTick;
    private long splashStartedAt;
    private boolean splashExitScheduled;
    private boolean shortcutAddPending;
    private Shader splashBackgroundShader;
    private float splashShaderHeight;
    private Shader logoShader;
    private float logoShaderX = Float.NaN, logoShaderY = Float.NaN, logoShaderSize = Float.NaN;
    private String activeTheme = "classic";
    private String profileImageUri = "";
    private Bitmap profileImageBitmap;
    private float taskScroll;
    private float profileScroll;
    private float touchDownX;
    private float touchDownY;
    private float lastTouchY;
    private boolean draggingScroll;
    private final ScreenAccessibilityHelper accessibilityHelper;

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!timerRunning) return;
            long now = System.currentTimeMillis();
            int elapsed = Math.max(1, (int) ((now - lastTimerTick) / 1000));
            lastTimerTick = now;
            timerSeconds = Math.max(0, timerSeconds - elapsed);
            if (timerSeconds == 0) finishFocusSession();
            invalidate();
            if (timerRunning) handler.postDelayed(this, 1000);
        }
    };

    public StudyMateView(MainActivity context) {
        super(context);
        activity = context;
        prefs = context.getSharedPreferences("studymate", Context.MODE_PRIVATE);
        // Use the window's hardware-accelerated canvas without allocating a dedicated
        // full-screen layer, because this view intentionally redraws every splash frame.
        setLayerType(View.LAYER_TYPE_NONE, null);
        setFocusable(true);
        setContentDescription("Aplikasi StudyMate");
        accessibilityHelper = new ScreenAccessibilityHelper(this);
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper);
        ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        loadData();
        context.configureSystemBars(true);
    }

    public StudyMateView(Context context){this((MainActivity)context);}

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(screen == SPLASH ? BLUE : Color.WHITE);
        scale = Math.min(getWidth() / 390f, getHeight() / 720f);
        offsetX = (getWidth() - 390f * scale) / 2f;
        logicalHeight = getHeight() / scale;
        canvas.save();
        canvas.translate(offsetX, 0);
        canvas.scale(scale, scale);
        switch (screen) {
            case SPLASH: drawSplash(canvas); break;
            case LOGIN: drawLogin(canvas); break;
            case HOME: drawHome(canvas); break;
            case TASKS: drawTasks(canvas); break;
            case ADD_TASK: drawAddTask(canvas); break;
            case DETAIL: drawDetail(canvas); break;
            case FOCUS: drawFocus(canvas); break;
            case PROGRESS: drawProgress(canvas); break;
            case PROFILE: drawProfile(canvas); break;
            case COURSES: drawCourses(canvas); break;
            case CALENDAR: drawCalendar(canvas); break;
            case SHOP: drawShop(canvas); break;
        }
        canvas.restore();
    }

    @Override public boolean dispatchHoverEvent(MotionEvent event) {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override public void invalidate() {
        super.invalidate();
        if (accessibilityHelper != null) accessibilityHelper.invalidateRoot();
    }

    @Override public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) {
            if (screen == TASKS) { updateTaskScroll(220f); announce("Daftar quest digulir ke bawah"); return true; }
            if (screen == PROFILE) { updateProfileScroll(220f); announce("Halaman profil digulir ke bawah"); return true; }
        } else if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) {
            if (screen == TASKS) { updateTaskScroll(-220f); announce("Daftar quest digulir ke atas"); return true; }
            if (screen == PROFILE) { updateProfileScroll(-220f); announce("Halaman profil digulir ke atas"); return true; }
        }
        return super.performAccessibilityAction(action, args);
    }

    // region Drawing primitives
    private void background(Canvas c, int color) {
        c.drawColor(resolveColor(color));
    }

    private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(resolveTextColor(color));
        paint.setTextSize(size * (fontLarge ? 1.12f : 1f));
        paint.setTypeface(bold ? TEXT_BOLD : TEXT_NORMAL);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(value, x, y, paint);
    }

    private void centered(Canvas c, String value, float x, float y, float size, int color, boolean bold) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(bold ? TEXT_BOLD : TEXT_NORMAL);
        paint.setTextSize(size * (fontLarge ? 1.12f : 1f));
        paint.setColor(resolveTextColor(color));
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        c.drawText(value, x, y, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void round(Canvas c, float l, float t, float r, float b, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(resolveColor(color));
        drawingRect.set(l, t, r, b);
        c.drawRoundRect(drawingRect, radius, radius, paint);
    }

    private void outline(Canvas c, float l, float t, float r, float b, float radius, int color, float stroke) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(resolveColor(color));
        drawingRect.set(l, t, r, b);
        c.drawRoundRect(drawingRect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void card(Canvas c, float l, float t, float r, float b, float radius) {
        round(c, l + 1, t + 3, r + 1, b + 3, radius, darkMode ? 0x30000000 : 0x0D0C1854);
        round(c, l, t, r, b, radius, darkMode ? Color.rgb(24, 32, 48) : Color.WHITE);
        outline(c, l, t, r, b, radius, highContrast ? Color.rgb(125, 134, 160) : (darkMode ? Color.rgb(52, 67, 93) : LINE), 1);
    }

    private void gradientButton(Canvas c, float l, float t, float r, float b, String label) {
        paint.setShader(new LinearGradient(l, t, r, b, themeAccentSecondary(), themeAccentPrimary(), Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        drawingRect.set(l, t, r, b);
        c.drawRoundRect(drawingRect, 13, 13, paint);
        paint.setShader(null);
        centered(c, label, (l + r) / 2, (t + b) / 2 + 6, 16, Color.WHITE, true);
    }

    private void chip(Canvas c, String label, float l, float t, float w, int fg, int bg) {
        round(c, l, t, l + w, t + 28, 14, bg);
        centered(c, label, l + w / 2, t + 19, 10.5f, fg, false);
    }

    private void drawLogo(Canvas c, float x, float y, float size) {
        if (logoShader == null || logoShaderX != x || logoShaderY != y || logoShaderSize != size) {
            logoShaderX = x; logoShaderY = y; logoShaderSize = size;
            logoShader = new LinearGradient(x, y, x + size, y + size,
                    Color.rgb(80, 93, 249), Color.rgb(43, 87, 222), Shader.TileMode.CLAMP);
        }
        paint.setShader(logoShader);
        drawingRect.set(x, y, x + size, y + size);
        c.drawRoundRect(drawingRect, size * .25f, size * .25f, paint);
        paint.setShader(null);
        round(c, x + size * .20f, y + size * .29f, x + size * .80f, y + size * .75f, size * .19f, Color.WHITE);
        round(c, x + size * .27f, y + size * .37f, x + size * .73f, y + size * .67f, size * .13f, NAVY);
        paint.setColor(Color.rgb(91, 229, 208));
        paint.setStrokeWidth(Math.max(2, size * .045f));
        paint.setStyle(Paint.Style.STROKE);
        drawingRect.set(x + size * .34f, y + size * .44f, x + size * .45f, y + size * .57f);
        c.drawArc(drawingRect, 190, 155, false, paint);
        drawingRect.set(x + size * .55f, y + size * .44f, x + size * .66f, y + size * .57f);
        c.drawArc(drawingRect, 195, 150, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 217, 29));
        path.reset();
        path.moveTo(x + size * .82f, y + size * .08f);
        path.lineTo(x + size * .86f, y + size * .18f);
        path.lineTo(x + size * .96f, y + size * .22f);
        path.lineTo(x + size * .86f, y + size * .26f);
        path.lineTo(x + size * .82f, y + size * .36f);
        path.lineTo(x + size * .78f, y + size * .26f);
        path.lineTo(x + size * .68f, y + size * .22f);
        path.lineTo(x + size * .78f, y + size * .18f);
        path.close();
        c.drawPath(path, paint);
    }

    private void drawBot(Canvas c, float cx, float cy, float size) {
        int cream = Color.rgb(246, 241, 229);
        round(c, cx - size * .34f, cy - size * .15f, cx + size * .34f, cy + size * .42f, size * .18f, Color.rgb(84, 147, 145));
        paint.setColor(cream);
        c.drawCircle(cx, cy - size * .22f, size * .35f, paint);
        c.drawCircle(cx - size * .27f, cy - size * .45f, size * .13f, paint);
        c.drawCircle(cx + size * .27f, cy - size * .45f, size * .13f, paint);
        round(c, cx - size * .27f, cy - size * .39f, cx + size * .27f, cy - size * .08f, size * .13f, NAVY);
        paint.setColor(Color.rgb(91, 229, 208));
        c.drawCircle(cx - size * .10f, cy - size * .24f, size * .035f, paint);
        c.drawCircle(cx + size * .10f, cy - size * .24f, size * .035f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * .025f);
        drawingRect.set(cx - size * .09f, cy - size * .22f, cx + size * .09f, cy - size * .10f);
        c.drawArc(drawingRect, 15, 150, false, paint);
        paint.setStyle(Paint.Style.FILL);
        centered(c, "M", cx, cy + size * .23f, size * .20f, cream, true);
        paint.setColor(cream);
        c.drawCircle(cx - size * .23f, cy + size * .45f, size * .10f, paint);
        c.drawCircle(cx + size * .23f, cy + size * .45f, size * .10f, paint);
    }

    private void drawCharacter(Canvas c, float cx, float cy, float size) {
        int lv = level();
        String cls = characterClass != null ? characterClass : "Ksatria";
        
        // 1. Draw wings / back equipment (Level >= 7)
        if (lv >= 7) {
            paint.setStyle(Paint.Style.FILL);
            if ("Ksatria".equals(cls)) {
                paint.setColor(Color.rgb(220, 230, 245));
            } else if ("Penyihir".equals(cls)) {
                paint.setColor(Color.rgb(190, 180, 255));
            } else {
                paint.setColor(Color.rgb(210, 245, 220));
            }
            // Draw left wing
            path.reset();
            path.moveTo(cx - size * .20f, cy - size * .10f);
            path.lineTo(cx - size * .65f, cy - size * .35f);
            path.lineTo(cx - size * .50f, cy + size * .15f);
            path.lineTo(cx - size * .20f, cy + size * .10f);
            path.close();
            c.drawPath(path, paint);
            
            // Draw right wing
            path.reset();
            path.moveTo(cx + size * .20f, cy - size * .10f);
            path.lineTo(cx + size * .65f, cy - size * .35f);
            path.lineTo(cx + size * .50f, cy + size * .15f);
            path.lineTo(cx + size * .20f, cy + size * .10f);
            path.close();
            c.drawPath(path, paint);
        }

        // 2. Draw cape / back clothing (Level >= 3)
        if (lv >= 3) {
            if ("Ksatria".equals(cls)) {
                paint.setColor(Color.rgb(200, 35, 55));
            } else if ("Penyihir".equals(cls)) {
                paint.setColor(Color.rgb(80, 35, 180));
            } else {
                paint.setColor(Color.rgb(45, 110, 65));
            }
            path.reset();
            path.moveTo(cx - size * .30f, cy - size * .05f);
            path.lineTo(cx - size * .50f, cy + size * .35f);
            path.lineTo(cx + size * .50f, cy + size * .35f);
            path.lineTo(cx + size * .30f, cy - size * .05f);
            path.close();
            c.drawPath(path, paint);
        }

        // 3. Draw Body (Base Bot)
        int bodyColor;
        if ("Ksatria".equals(cls)) {
            bodyColor = Color.rgb(130, 145, 160);
        } else if ("Penyihir".equals(cls)) {
            bodyColor = Color.rgb(90, 70, 150);
        } else {
            bodyColor = Color.rgb(65, 120, 80);
        }
        
        round(c, cx - size * .32f, cy - size * .12f, cx + size * .32f, cy + size * .40f, size * .15f, bodyColor);
        
        // 4. Draw head and face
        int cream = Color.rgb(246, 241, 229);
        paint.setColor(cream);
        c.drawCircle(cx, cy - size * .22f, size * .34f, paint);
        
        round(c, cx - size * .26f, cy - size * .38f, cx + size * .26f, cy - size * .08f, size * .12f, NAVY);
        
        paint.setColor(Color.rgb(91, 229, 208));
        c.drawCircle(cx - size * .09f, cy - size * .23f, size * .035f, paint);
        c.drawCircle(cx + size * .09f, cy - size * .23f, size * .035f, paint);
        
        paint.setColor(Color.WHITE);
        c.drawCircle(cx - size * .08f, cy - size * .24f, size * .012f, paint);
        c.drawCircle(cx + size * .10f, cy - size * .24f, size * .012f, paint);
        
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * .022f);
        paint.setColor(Color.rgb(91, 229, 208));
        drawingRect.set(cx - size * .08f, cy - size * .22f, cx + size * .08f, cy - size * .11f);
        c.drawArc(drawingRect, 15, 150, false, paint);
        paint.setStyle(Paint.Style.FILL);
        
        paint.setColor(cream);
        centered(c, "M", cx, cy + size * .22f, size * .18f, cream, true);
        
        paint.setColor(cream);
        c.drawCircle(cx - size * .22f, cy + size * .42f, size * .09f, paint);
        c.drawCircle(cx + size * .22f, cy + size * .42f, size * .09f, paint);

        // 6. Draw Class Specific Helmets/Hats (Level >= 1)
        if ("Ksatria".equals(cls)) {
            round(c, cx - size * .22f, cy - size * .37f, cx + size * .22f, cy - size * .28f, size * .05f, Color.rgb(100, 115, 130));
            paint.setColor(Color.rgb(255, 205, 38));
            c.drawRect(cx - size * .16f, cy - size * .34f, cx + size * .16f, cy - size * .31f, paint);
        } else if ("Penyihir".equals(cls)) {
            paint.setColor(Color.rgb(105, 75, 200));
            path.reset();
            path.moveTo(cx - size * .38f, cy - size * .32f);
            path.lineTo(cx - size * .08f, cy - size * .68f);
            path.lineTo(cx + size * .08f, cy - size * .68f);
            path.lineTo(cx + size * .38f, cy - size * .32f);
            path.close();
            c.drawPath(path, paint);
            round(c, cx - size * .40f, cy - size * .33f, cx + size * .40f, cy - size * .28f, size * .02f, Color.rgb(255, 205, 38));
            if (lv >= 2) {
                paint.setColor(Color.rgb(255, 220, 50));
                star(c, cx, cy - size * .75f, size * .08f);
            }
        } else {
            paint.setColor(Color.rgb(65, 120, 80));
            path.reset();
            path.moveTo(cx - size * .38f, cy - size * .20f);
            path.lineTo(cx - size * .32f, cy - size * .48f);
            path.lineTo(cx, cy - size * .56f);
            path.lineTo(cx + size * .32f, cy - size * .48f);
            path.lineTo(cx + size * .38f, cy - size * .20f);
            path.lineTo(cx + size * .24f, cy - size * .06f);
            path.lineTo(cx - size * .24f, cy - size * .06f);
            path.close();
            c.drawPath(path, paint);
            paint.setColor(Color.rgb(40, 80, 50));
            drawingRect.set(cx - size * .28f, cy - size * .50f, cx + size * .28f, cy - size * .30f);
            c.drawArc(drawingRect, 180, 180, true, paint);
        }

        // 7. Draw Class Specific Weapons / Offhands (Level >= 5)
        if (lv >= 5) {
            paint.setStyle(Paint.Style.FILL);
            if ("Ksatria".equals(cls)) {
                paint.setColor(Color.rgb(180, 110, 50));
                path.reset();
                path.moveTo(cx - size * .25f, cy + size * .10f);
                path.lineTo(cx - size * .45f, cy + size * .10f);
                path.lineTo(cx - size * .42f, cy + size * .35f);
                path.lineTo(cx - size * .35f, cy + size * .42f);
                path.lineTo(cx - size * .28f, cy + size * .35f);
                path.close();
                c.drawPath(path, paint);
                paint.setColor(Color.rgb(255, 205, 38));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f);
                c.drawPath(path, paint);
                c.drawLine(cx - size * .35f, cy + size * .15f, cx - size * .35f, cy + size * .37f, paint);
                c.drawLine(cx - size * .41f, cy + size * .25f, cx - size * .29f, cy + size * .25f, paint);
                paint.setStyle(Paint.Style.FILL);
            } else if ("Penyihir".equals(cls)) {
                round(c, cx - size * .48f, cy + size * .15f, cx - size * .26f, cy + size * .38f, size * .03f, Color.rgb(160, 50, 60));
                round(c, cx - size * .45f, cy + size * .18f, cx - size * .29f, cy + size * .35f, size * .02f, Color.rgb(245, 240, 220));
                paint.setColor(Color.rgb(255, 205, 38));
                c.drawCircle(cx - size * .37f, cy + size * .26f, size * .04f, paint);
            } else {
                paint.setColor(Color.rgb(130, 85, 40));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3.5f);
                drawingRect.set(cx - size * .46f, cy + size * .02f, cx - size * .22f, cy + size * .46f);
                c.drawArc(drawingRect, 100, 160, false, paint);
                paint.setStrokeWidth(1.2f);
                paint.setColor(Color.rgb(230, 235, 245));
                c.drawLine(cx - size * .38f, cy + size * .04f, cx - size * .38f, cy + size * .44f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }

        // 8. Draw Class Specific Weapons / Details (Level >= 3)
        if (lv >= 3) {
            paint.setStyle(Paint.Style.FILL);
            if ("Ksatria".equals(cls)) {
                paint.setColor(Color.rgb(180, 140, 30));
                c.drawRect(cx + size * .20f, cy + size * .20f, cx + size * .36f, cy + size * .24f, paint);
                c.drawRect(cx + size * .26f, cy + size * .24f, cx + size * .30f, cy + size * .34f, paint);
                paint.setColor(Color.rgb(220, 225, 235));
                path.reset();
                path.moveTo(cx + size * .26f, cy + size * .20f);
                path.lineTo(cx + size * .26f, cy - size * .20f);
                path.lineTo(cx + size * .28f, cy - size * .26f);
                path.lineTo(cx + size * .30f, cy - size * .20f);
                path.lineTo(cx + size * .30f, cy + size * .20f);
                path.close();
                c.drawPath(path, paint);
            } else if ("Penyihir".equals(cls)) {
                paint.setColor(Color.rgb(110, 75, 45));
                c.drawRect(cx + size * .25f, cy - size * .18f, cx + size * .29f, cy + size * .36f, paint);
                if (lv >= 5) {
                    paint.setColor(Color.rgb(0, 230, 245));
                    path.reset();
                    path.moveTo(cx + size * .27f, cy - size * .36f);
                    path.lineTo(cx + size * .33f, cy - size * .27f);
                    path.lineTo(cx + size * .27f, cy - size * .18f);
                    path.lineTo(cx + size * .21f, cy - size * .27f);
                    path.close();
                    c.drawPath(path, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2f);
                    paint.setColor(0x8800E6F5);
                    c.drawCircle(cx + size * .27f, cy - size * .27f, size * .10f, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            } else {
                paint.setColor(Color.rgb(130, 85, 40));
                c.drawRect(cx + size * .18f, cy + size * .05f, cx + size * .30f, cy + size * .32f, paint);
                paint.setColor(Color.rgb(200, 50, 60));
                c.drawRect(cx + size * .20f, cy - size * .05f, cx + size * .23f, cy + size * .05f, paint);
                c.drawRect(cx + size * .25f, cy - size * .08f, cx + size * .28f, cy + size * .05f, paint);
            }
        }

        if (lv >= 5) {
            paint.setColor(Color.rgb(255, 197, 31));
            path.reset();
            path.moveTo(cx - size * .16f, cy - size * .44f);
            path.lineTo(cx - size * .10f, cy - size * .56f);
            path.lineTo(cx, cy - size * .46f);
            path.lineTo(cx + size * .10f, cy - size * .56f);
            path.lineTo(cx + size * .16f, cy - size * .44f);
            path.close();
            c.drawPath(path, paint);
        }

        if (lv >= 2 && !"Penyihir".equals(cls)) {
            paint.setColor(Color.rgb(255, 205, 38));
            star(c, cx - size * .36f, cy - size * .38f, size * .09f);
        }

        if (lv >= 10) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(0x99FFD23D);
            c.drawCircle(cx, cy - size * .05f, size * .61f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            drawSpark(c, cx + size * .50f, cy - size * .46f, size * .16f);
        }
    }

    private void star(Canvas canvas, float x, float y, float r) {
        if (r <= 0) return;
        path.reset();
        path.moveTo(x, y - r);
        path.lineTo(x + r * .22f, y - r * .22f);
        path.lineTo(x + r, y);
        path.lineTo(x + r * .22f, y + r * .22f);
        path.lineTo(x, y + r);
        path.lineTo(x - r * .22f, y + r * .22f);
        path.lineTo(x - r, y);
        path.lineTo(x - r * .22f, y - r * .22f);
        path.close();
        canvas.drawPath(path, paint);
    }


    private void drawSpark(Canvas c,float x,float y,float size){
        path.reset();path.moveTo(x,y-size);path.lineTo(x+size*.25f,y-size*.25f);path.lineTo(x+size,y);
        path.lineTo(x+size*.25f,y+size*.25f);path.lineTo(x,y+size);path.lineTo(x-size*.25f,y+size*.25f);
        path.lineTo(x-size,y);path.lineTo(x-size*.25f,y-size*.25f);path.close();c.drawPath(path,paint);
    }

    private String characterRank(){
        return GameRules.rankForLevel(level());
    }

    private void topBrand(Canvas c) {
        drawLogo(c, 18, 11, 38);
        text(c, "StudyMate Quest", 65, 36, 17, NAVY, true);
        round(c, 258, 13, 307, 43, 11, themeAccentSurface());
        centered(c, "LV " + level(), 282, 33, 10, themeAccentPrimary(), true);
        round(c, 312, 13, 372, 43, 11, Color.rgb(255,248,229));
        drawCoin(c, 325, 28, 7);
        text(c, String.valueOf(coins), 338, 33, 10, NAVY, true);
    }

    private void topBack(Canvas c, String title) {
        card(c, 20, 15, 52, 48, 10);
        centered(c, "‹", 36, 40, 30, NAVY, false);
        centered(c, title, 195, 40, 20, NAVY, true);
    }

    private void bottomNav(Canvas c, int selected) {
        float top = logicalHeight - 66;
        round(c, 0, top - 2, 390, logicalHeight, 24, 0x100C1854);
        round(c, 0, top, 390, logicalHeight, 24, Color.WHITE);
        String[] labels = {"Beranda", "Quest", "Fokus", "Prestasi", "Profil"};
        for (int i = 0; i < 5; i++) {
            float x = 39 + i * 78;
            int color = i == selected ? BLUE : MUTED;
            if (i == selected) round(c, x - 20, top + 5, x + 20, top + 8, 2, BLUE);
            drawNavIcon(c, i, x, top + 27, color);
            centered(c, labels[i], x, top + 53, 10, color, false);
        }
    }

    private void drawCoin(Canvas c,float x,float y,float radius){
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(255,190,30));c.drawCircle(x,y,radius,paint);
        paint.setColor(Color.rgb(255,225,112));c.drawCircle(x,y,radius*.58f,paint);
        centered(c,"C",x,y+3,radius*.95f,Color.rgb(173,105,0),true);
    }

    private void drawNavIcon(Canvas c,int type,float x,float y,int color){
        paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.1f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(color);
        path.reset();
        if(type==0){
            path.moveTo(x-10,y);path.lineTo(x,y-9);path.lineTo(x+10,y);path.moveTo(x-7,y-2);path.lineTo(x-7,y+9);path.lineTo(x+7,y+9);path.lineTo(x+7,y-2);c.drawPath(path,paint);
        }else if(type==1){
            c.drawRoundRect(new RectF(x-8,y-9,x+8,y+10),3,3,paint);c.drawLine(x-4,y-4,x+4,y-4,paint);c.drawLine(x-4,y+1,x+4,y+1,paint);c.drawLine(x-4,y+6,x+2,y+6,paint);c.drawRoundRect(new RectF(x-4,y-12,x+4,y-7),2,2,paint);
        }else if(type==2){
            c.drawCircle(x,y,9,paint);c.drawCircle(x,y,3,paint);c.drawLine(x,y-13,x,y-8,paint);c.drawLine(x+13,y,x+8,y,paint);c.drawLine(x,y+13,x,y+8,paint);c.drawLine(x-13,y,x-8,y,paint);
        }else if(type==3){
            c.drawRoundRect(new RectF(x-10,y+2,x-5,y+10),1,1,paint);c.drawRoundRect(new RectF(x-2.5f,y-4,x+2.5f,y+10),1,1,paint);c.drawRoundRect(new RectF(x+5,y-10,x+10,y+10),1,1,paint);
        }else{
            c.drawCircle(x,y-5,5,paint);c.drawArc(new RectF(x-10,y+1,x+10,y+14),190,160,false,paint);
        }
        paint.setStyle(Paint.Style.FILL);paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private float paragraph(Canvas c, String value, float x, float y, float maxWidth, float size, int color, float lineHeight) {
        String[] words = value.split(" ");
        StringBuilder line = new StringBuilder();
        paint.setTextSize(size * (fontLarge ? 1.12f : 1f));
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (paint.measureText(candidate) > maxWidth && line.length() > 0) {
                text(c, line.toString(), x, y, size, color, false);
                y += lineHeight;
                line = new StringBuilder(word);
            } else line = new StringBuilder(candidate);
        }
        if (line.length() > 0) text(c, line.toString(), x, y, size, color, false);
        return y;
    }

    private float paragraphLimited(Canvas c,String value,float x,float y,float maxWidth,float size,
                                   int color,float lineHeight,int maxLines,boolean bold){
        String content=value==null||value.trim().isEmpty()?"Belum ada deskripsi.":value.trim();
        paint.setTextSize(size*(fontLarge?1.12f:1f));
        paint.setTypeface(bold ? TEXT_BOLD : TEXT_NORMAL);
        String[] words=content.split("\\s+");List<String> lines=new ArrayList<>();StringBuilder line=new StringBuilder();
        for(String word:words){String candidate=line.length()==0?word:line+" "+word;
            if(paint.measureText(candidate)>maxWidth&&line.length()>0){lines.add(line.toString());line=new StringBuilder(word);}
            else line=new StringBuilder(candidate);}
        if(line.length()>0)lines.add(line.toString());
        int count=Math.min(maxLines,lines.size());
        for(int i=0;i<count;i++){String current=lines.get(i);
            if(i==count-1&&lines.size()>maxLines)current=fitTextWidth(current+"…",maxWidth,size,bold);
            text(c,current,x,y+i*lineHeight,size,color,bold);}
        return y+Math.max(0,count-1)*lineHeight;
    }

    private String fitTextWidth(String value,float maxWidth,float size,boolean bold){
        if(value==null)return "";paint.setTextSize(size*(fontLarge?1.12f:1f));
        paint.setTypeface(bold ? TEXT_BOLD : TEXT_NORMAL);
        if(paint.measureText(value)<=maxWidth)return value;String suffix="…";int end=value.length();
        while(end>0&&paint.measureText(value.substring(0,end).trim()+suffix)>maxWidth)end--;
        return value.substring(0,Math.max(0,end)).trim()+suffix;
    }
    // endregion

    private void drawSplash(Canvas c) {
        if (splashStartedAt == 0L) splashStartedAt = SystemClock.uptimeMillis();
        float progress = Math.min(1f, (SystemClock.uptimeMillis() - splashStartedAt) / 2200f);

        if (splashBackgroundShader == null || splashShaderHeight != logicalHeight) {
            splashShaderHeight = logicalHeight;
            splashBackgroundShader = new LinearGradient(0, 0, 390, logicalHeight,
                    Color.rgb(76, 78, 241), Color.rgb(22, 194, 194), Shader.TileMode.CLAMP);
        }
        paint.setShader(splashBackgroundShader);
        c.drawRect(0, 0, 390, logicalHeight, paint);
        paint.setShader(null);

        // Soft background shapes drift in opposite directions to create depth.
        paint.setColor(0x18FFFFFF);
        c.drawCircle(-20 + 28 * progress, 15 + 18 * progress, 128, paint);
        c.drawCircle(415 - 24 * progress, logicalHeight + 22 - 16 * progress, 115, paint);

        float logoProgress = interval(progress, 0f, .28f);
        float logoScale = .28f + .72f * easeOutBack(logoProgress);
        float logoY = logicalHeight * .20f + (1f - logoProgress) * 52f;
        c.save();
        c.translate(0, logoY);
        c.translate(195, 48);
        c.scale(logoScale, logoScale);
        c.translate(-195, -48);
        drawLogo(c, 147, 0, 96);
        c.restore();

        // Twinkling particles orbit the logo and mascot.
        float twinkle = .55f + .45f * (float) Math.sin(progress * Math.PI * 8);
        splashStar(c, 116, logoY + 19, 7, interval(progress, .10f, .25f) * twinkle);
        splashStar(c, 273, logoY + 56, 5, interval(progress, .16f, .31f) * (1.1f - twinkle * .4f));
        splashStar(c, 292, logicalHeight * .61f, 8, interval(progress, .45f, .62f) * twinkle);
        splashStar(c, 99, logicalHeight * .67f, 5, interval(progress, .48f, .66f) * (1.1f - twinkle * .4f));

        float titleProgress = interval(progress, .20f, .48f);
        float titleY = logicalHeight * .38f + (1f - easeOut(titleProgress)) * 24f;
        centered(c, "StudyMate", 195, titleY, 34,
                Color.argb((int) (255 * titleProgress), 255, 255, 255), true);

        float tagProgress = interval(progress, .36f, .61f);
        int tagColor = Color.argb((int) (235 * tagProgress), 255, 255, 255);
        centered(c, "Teman belajar pintar untuk", 195, logicalHeight * .43f + (1f - tagProgress) * 15f, 15, tagColor, false);
        centered(c, "mahasiswa produktif", 195, logicalHeight * .43f + 23 + (1f - tagProgress) * 15f, 15, tagColor, false);

        float botProgress = interval(progress, .42f, .78f);
        float botY = lerp(logicalHeight + 100, logicalHeight * .66f, easeOutBack(botProgress));
        if (botProgress >= 1f) botY += (float) Math.sin(progress * Math.PI * 7) * 4f;
        // A small waving arm is drawn behind the mascot body.
        c.save();
        c.rotate(-24 + (float) Math.sin(progress * Math.PI * 10) * 10, 225, botY - 8);
        round(c, 220, botY - 13, 258, botY + 1, 7, Color.rgb(84, 147, 145));
        paint.setColor(Color.rgb(246, 241, 229));
        c.drawCircle(261, botY - 7, 9, paint);
        c.restore();
        drawBot(c, 195, botY, 112);

        float loadingProgress = interval(progress, .67f, .88f);
        int loadingColor = Color.argb((int) (239 * loadingProgress), 255, 255, 255);
        centered(c, "Menyiapkan ruang belajarmu...", 195, logicalHeight - 66, 11, loadingColor, false);
        int activeDot = Math.min(3, (int) ((SystemClock.uptimeMillis() - splashStartedAt) / 260L) % 4);
        for (int i = 0; i < 4; i++) {
            float pulse = i == activeDot ? 1.35f : .82f;
            int dotAlpha = (int) ((i == activeDot ? 255 : 119) * loadingProgress);
            paint.setColor(Color.argb(dotAlpha, 255, 255, 255));
            c.drawCircle(174 + i * 14, logicalHeight - 38, 4 * pulse, paint);
        }

        if (progress < 1f) {
            postInvalidateOnAnimation();
        } else if (!splashExitScheduled) {
            splashExitScheduled = true;
            handler.post(() -> {
                if(screen!=SPLASH)return;
                if(prefs.getBoolean("logged_in",false)&&shortcutAddPending){shortcutAddPending=false;newDraft(false);showScreen(ADD_TASK);}
                else showScreen(prefs.getBoolean("logged_in", false) ? HOME : LOGIN);
            });
        }
    }

    private void splashStar(Canvas c, float x, float y, float size, float alpha) {
        paint.setColor(Color.argb(Math.max(0, Math.min(255, (int) (255 * alpha))),
                255, 255, 255));
        path.reset();
        path.moveTo(x, y - size);
        path.lineTo(x + size * .35f, y - size * .35f);
        path.lineTo(x + size, y);
        path.lineTo(x + size * .35f, y + size * .35f);
        path.lineTo(x, y + size);
        path.lineTo(x - size * .35f, y + size * .35f);
        path.lineTo(x - size, y);
        path.lineTo(x - size * .35f, y - size * .35f);
        path.close();
        c.drawPath(path, paint);
    }

    private float interval(float value, float start, float end) {
        if (end <= start) return value >= end ? 1f : 0f;
        return Math.max(0f, Math.min(1f, (value - start) / (end - start)));
    }

    private float easeOut(float value) {
        float inv = 1f - value;
        return 1f - inv * inv * inv;
    }

    private float easeOutBack(float value) {
        float c1 = 1.70158f, c3 = c1 + 1f, x = value - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    private float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private void drawLogin(Canvas c) {
        background(c, Color.WHITE);
        drawLogo(c, 105, 25, 43);
        text(c, "StudyMate", 160, 57, 23, NAVY, true);
        drawBot(c, 195, 142, 92);
        centered(c, "Selamat Datang Kembali", 195, 232, 25, NAVY, true);
        centered(c, "Masuk untuk lanjut mengatur tugasmu.", 195, 258, 13, MUTED, false);
        formField(c, 31, 292, 359, 347, "✉", loginEmail.isEmpty() ? "Email" : loginEmail, false);
        formField(c, 31, 360, 359, 415, "▣", loginPassword.isEmpty() ? "Password" : "••••••••", false);
        gradientButton(c, 31, 432, 359, 484, "Masuk");
        text(c, "Lupa password?", 256, 509, 12, BLUE, false);
        paint.setColor(LINE); c.drawRect(31, 536, 158, 537, paint); c.drawRect(232, 536, 359, 537, paint);
        centered(c, "atau", 195, 541, 13, MUTED, false);
        card(c, 31, 557, 359, 609, 12);
        centered(c, "G   Masuk dengan Google", 195, 588, 14, NAVY, true);
        centered(c, "Belum punya akun?  Daftar", 195, 642, 12, MUTED, false);
    }

    private void formField(Canvas c, float l, float t, float r, float b, String icon, String value, boolean compact) {
        outline(c, l, t, r, b, 12, LINE, 1.2f);
        round(c, l + 9, t + 9, l + 43, b - 9, 10, Color.rgb(242, 244, 255));
        centered(c, icon, l + 26, (t + b) / 2 + 6, compact ? 13 : 18, BLUE, true);
        text(c, value, l + 54, (t + b) / 2 + 6, compact ? 11 : 14, value.startsWith("Pilih") || value.equals("Email") || value.equals("Password") ? MUTED : NAVY, false);
    }

    private void drawHome(Canvas c) {
        background(c, Color.WHITE);
        topBrand(c);
        text(c, "Halo, " + firstName() + "!", 22, 86, 24, NAVY, true);
        text(c, "Streak " + streak + " hari  •  Saatnya menaklukkan quest!", 22, 109, 12, MUTED, false);
        paint.setShader(new LinearGradient(20, 128, 370, 278, Color.rgb(87, 72, 230), Color.rgb(48, 137, 225), Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(20, 128, 370, 278), 20, 20, paint);
        paint.setShader(null);
        text(c, characterRank(), 38, 158, 12, 0xDDFFFFFF, false);
        text(c, "Level " + level(), 38, 187, 25, Color.WHITE, true);
        text(c, pendingCount() + " quest aktif", 38, 209, 11, 0xE8FFFFFF, false);
        paint.setColor(0x44FFFFFF);c.drawRoundRect(new RectF(38,225,218,233),4,4,paint);
        paint.setColor(Color.WHITE);c.drawRoundRect(new RectF(38,225,38+180*(xp%300)/300f,233),4,4,paint);
        text(c,(xp%300)+" / 300 XP",38,251,10,Color.WHITE,true);
        drawCharacter(c,306,197,82);
        text(c, "Status Petualangan", 22, 314, 18, NAVY, true);
        statMini(c, 22, 330, "⚔", String.valueOf(doneCount()), "Quest selesai", GREEN, Color.rgb(239, 251, 246));
        statMini(c, 139, 330, "🔥", String.valueOf(streak), "Streak harian", ORANGE, Color.rgb(255, 247, 235));
        statMini(c, 256, 330, "🏆", String.valueOf(unlockedBadges()), "Badge diraih", PURPLE, Color.rgb(247, 242, 255));
        text(c, "Quest Terdekat", 22, 432, 18, NAVY, true);
        text(c, "Buka Quest Board", 283, 432, 9.5f, BLUE, false);
        List<Task> pending = visibleTasks(0, "");
        for (int i = 0; i < Math.min(2, pending.size()); i++) drawHomeTask(c, pending.get(i), 22, 448 + i * 72);
        bottomNav(c, 0);
    }

    private void statMini(Canvas c, float x, float y, String icon, String value, String label, int accent, int bg) {
        round(c, x, y, x + 102, y + 77, 15, bg);
        centered(c, icon, x + 24, y + 29, 18, accent, true);
        text(c, value, x + 45, y + 32, 20, NAVY, true);
        centered(c, label, x + 51, y + 61, 10, MUTED, false);
    }

    private void drawHomeTask(Canvas c, Task task, float x, float y) {
        card(c, x, y, 368, y + 60, 14);
        round(c, x + 10, y + 9, x + 49, y + 49, 11, priorityBg(task.priority));
        centered(c, "▤", x + 29, y + 35, 18, priorityColor(task.priority), true);
        text(c, ellipsize(task.title, 27), x + 59, y + 23, 13, NAVY, true);
        text(c, task.course + "  •  +" + task.xpReward + " XP", x + 59, y + 43, 10, MUTED, false);
        centered(c, "›", 349, y + 39, 25, MUTED, false);
    }

    private void drawTasks(Canvas c) {
        background(c, Color.WHITE);
        topBrand(c);
        text(c, "Quest Board", 22, 91, 25, NAVY, true);
        text(c, "Selesaikan quest untuk mendapatkan XP dan koin.", 22, 114, 12, MUTED, false);
        outline(c, 22, 132, 368, 174, 12, LINE, 1);
        text(c, "⌕", 34, 160, 24, MUTED, false);
        text(c, search.isEmpty() ? "Cari quest atau mata kuliah..." : search, 67, 158, 12, search.isEmpty() ? Color.rgb(170, 178, 202) : NAVY, false);
        String[] filters = {"Semua", "Hari ini", "Prioritas", "Selesai", "Telat"};
        float[] widths = {62, 65, 72, 61, 55};
        float fx = 22;
        for (int i = 0; i < filters.length; i++) {
            chip(c, filters[i], fx, 188, widths[i], i == filter ? BLUE : MUTED, i == filter ? Color.rgb(239, 242, 255) : Color.WHITE);
            outline(c, fx, 188, fx + widths[i], 216, 14, i == filter ? Color.rgb(199, 207, 255) : LINE, 1);
            fx += widths[i] + 7;
        }
        List<Task> list = visibleTasks(filter, search);
        if (list.isEmpty()) {
            drawCharacter(c, 195, 345, 85);
            centered(c, "Belum ada quest di sini", 195, 414, 17, NAVY, true);
            centered(c, "Tekan + untuk menerima quest baru", 195, 438, 11, MUTED, false);
        } else {
            c.save();
            c.clipRect(0, 230, 390, logicalHeight - 70);
            c.translate(0, -taskScroll);
            for (int i = 0; i < list.size(); i++) drawTaskCard(c, list.get(i), 22, 230 + i * 91);
            c.restore();
        }
        paint.setColor(0x220C1854);c.drawCircle(348, logicalHeight - 99, 31, paint);
        paint.setColor(BLUE); c.drawCircle(348, logicalHeight - 103, 29, paint);
        centered(c, "+", 348, logicalHeight - 94, 34, Color.WHITE, false);
        bottomNav(c, 1);
    }

    private void drawTaskCard(Canvas c, Task task, float x, float y) {
        card(c, x, y, 368, y + 79, 14);
        int accent = task.status.equals("Selesai") ? GREEN : priorityColor(task.priority);
        round(c, x + 10, y + 12, x + 53, y + 55, 12, task.status.equals("Selesai") ? Color.rgb(232, 249, 240) : priorityBg(task.priority));
        centered(c, task.status.equals("Selesai") ? "✓" : "▤", x + 31, y + 41, 19, accent, true);
        text(c, ellipsize(task.title, 23), x + 63, y + 22, 13, NAVY, true);
        text(c, ellipsize(task.course, 26), x + 63, y + 41, 10, MUTED, false);
        text(c, "▣  " + relativeDeadline(task), x + 63, y + 61, 10, accent, false);
        chip(c, difficulty(task), 279, y + 9, 60, priorityColor(task.priority), priorityBg(task.priority));
        text(c, "+"+task.xpReward+" XP", 288, y + 51, 9.5f, PURPLE, true);
        float progressLeft = x + 63;
        float progressRight = x + 250;
        float progressWidth = progressRight - progressLeft;
        float progress = Math.max(0f, Math.min(100f, task.progress)) / 100f;
        paint.setColor(Color.rgb(232, 235, 244)); c.drawRoundRect(new RectF(progressLeft, y + 68, progressRight, y + 72), 3, 3, paint);
        paint.setColor(accent); c.drawRoundRect(new RectF(progressLeft, y + 68, progressLeft + progressWidth * progress, y + 72), 3, 3, paint);
        text(c, task.progress + "%", 292, y + 73, 10, NAVY, false);
        centered(c, "›", 350, y + 52, 24, MUTED, false);
    }

    private void drawAddTask(Canvas c) {
        background(c, Color.WHITE);
        topBack(c, editing ? "Edit Quest" : "Quest Baru");
        text(c, "Judul Quest", 22, 82, 13, NAVY, true);
        formField(c, 22, 92, 368, 137, "Q", draft.title.isEmpty() ? "Masukkan judul quest" : ellipsize(draft.title, 33), true);
        text(c, "Mata Kuliah", 22, 160, 13, NAVY, true);
        formField(c, 22, 170, 368, 215, "◆", draft.course.isEmpty() ? "Pilih mata kuliah" : draft.course, true);
        text(c, "Deskripsi", 22, 238, 13, NAVY, true);
        outline(c, 22, 248, 368, 303, 11, LINE, 1);
        text(c, draft.description.isEmpty() ? "Jelaskan tugas ini secara singkat..." : ellipsize(draft.description, 48), 36, 278, 11, draft.description.isEmpty() ? MUTED : NAVY, false);
        round(c, 330, 258, 358, 292, 9, Color.rgb(241, 244, 255)); centered(c, "♬", 344, 281, 16, BLUE, true);
        text(c, "Tanggal Deadline", 22, 329, 12, NAVY, true);
        text(c, "Jam Deadline", 205, 329, 12, NAVY, true);
        miniField(c, 22, 339, 187, 382, "▣", dateFormat.format(new Date(draft.deadline)));
        miniField(c, 202, 339, 368, 382, "◷", timeText(draft.deadline));
        text(c, "Prioritas", 22, 408, 12, NAVY, true);
        miniField(c, 22, 418, 368, 461, "⚑", draft.priority);
        text(c, "Status", 22, 487, 12, NAVY, true);
        miniField(c, 22, 497, 368, 540, "✓", draft.status);
        text(c, "Catatan Tambahan (Opsional)", 22, 566, 12, NAVY, true);
        outline(c, 22, 576, 368, 617, 11, LINE, 1);
        text(c, draft.notes.isEmpty() ? "Tambahkan catatan penting lainnya..." : ellipsize(draft.notes, 45), 36, 602, 11, draft.notes.isEmpty() ? MUTED : NAVY, false);
        gradientButton(c, 22, logicalHeight - 58, 368, logicalHeight - 13, editing ? "Simpan Perubahan" : "Terima Quest");
    }

    private void miniField(Canvas c, float l, float t, float r, float b, String icon, String value) {
        outline(c, l, t, r, b, 10, LINE, 1);
        round(c, l + 7, t + 7, l + 38, b - 7, 8, Color.rgb(242, 245, 255));
        centered(c, icon, l + 22, t + 28, 14, BLUE, true);
        text(c, ellipsize(value, r - l > 200 ? 28 : 16), l + 46, t + 27, 10.5f, NAVY, false);
        centered(c, "⌄", r - 15, t + 27, 14, MUTED, false);
    }

    private void drawDetail(Canvas c) {
        background(c, Color.WHITE);
        topBack(c, "Detail Quest");
        if (selectedTask == null) return;
        card(c, 22, 66, 368, 473, 16);
        round(c, 37, 82, 82, 127, 13, priorityBg(selectedTask.priority));
        centered(c, "▤", 59, 111, 21, priorityColor(selectedTask.priority), true);
        float titleBottom=paragraphLimited(c,selectedTask.title,95,91,220,16,NAVY,18,2,true);
        text(c, fitTextWidth(selectedTask.course,220,11.5f,false), 95, titleBottom+20, 11.5f, MUTED, false);
        chip(c, selectedTask.priority, 37, 142, 112, priorityColor(selectedTask.priority), priorityBg(selectedTask.priority));
        chip(c, selectedTask.status, 156, 142, 129, statusColor(selectedTask.status), statusBg(selectedTask.status));
        round(c,292,142,353,170,14,Color.rgb(245,240,255));centered(c,"+"+selectedTask.xpReward+" XP",322.5f,161,9,PURPLE,true);
        paint.setColor(LINE); c.drawRect(37, 179, 353, 180, paint);

        round(c,37,192,65,240,9,Color.rgb(240,244,255));centered(c,"▣",51,222,17,BLUE,true);
        text(c, "Deadline", 73, 200, 10.5f, MUTED, false);
        text(c, fitTextWidth(deadlineShort(selectedTask),116,14,true), 73, 221, 14,
                selectedTask.deadline < System.currentTimeMillis()&&!selectedTask.status.equals("Selesai") ? RED : NAVY, true);
        text(c, dateFormat.format(new Date(selectedTask.deadline)), 73, 241, 10.5f, MUTED, false);
        paint.setColor(LINE);c.drawRect(195,192,196,243,paint);
        round(c,205,192,233,240,9,Color.rgb(255,247,235));centered(c,"◷",219,222,17,ORANGE,true);
        text(c, "Sisa Waktu", 241, 200, 10.5f, MUTED, false);
        text(c, fitTextWidth(remainingDays(selectedTask),108,14,true), 241, 221, 14, ORANGE, true);
        text(c, timeText(selectedTask.deadline), 241, 241, 10.5f, MUTED, false);
        paint.setColor(LINE); c.drawRect(37, 257, 353, 258, paint);
        text(c, "Deskripsi", 37, 282, 13, NAVY, true);
        paragraphLimited(c,selectedTask.description,37,303,310,11,NAVY,16,3,false);
        paint.setColor(LINE); c.drawRect(37, 354, 353, 355, paint);
        text(c, "Progres Tugas", 37, 378, 13, NAVY, true);
        text(c, selectedTask.progress + "%", 321, 378, 13, BLUE, true);
        paint.setColor(Color.rgb(231, 234, 244)); c.drawRoundRect(new RectF(37, 389, 353, 396), 4, 4, paint);
        paint.setColor(BLUE); c.drawRoundRect(new RectF(37, 389, 37 + 316 * selectedTask.progress / 100f, 396), 4, 4, paint);
        round(c, 37, 414, 353, 459, 12, Color.rgb(239, 251, 251));
        text(c, "✦  Saran StudyMate", 50, 434, 12, Color.rgb(0, 140, 148), true);
        text(c, fitTextWidth(suggestion(selectedTask),290,10,false), 50, 451, 10, NAVY, false);
        gradientButton(c, 22, 483, 368, 522, "Mulai Fokus");
        outline(c, 22, 528, 368, 563, 11, BLUE, 1); centered(c, "Edit Quest", 195, 551, 13, BLUE, true);
        round(c, 22, 569, 368, 604, 11, Color.rgb(241, 251, 246)); centered(c, "Quest Selesai", 195, 592, 13, GREEN, true);
        round(c, 22, 610, 368, 646, 11, Color.rgb(255, 245, 246)); centered(c, "Hapus Quest", 195, 634, 13, RED, true);
        bottomNav(c, 0);
    }

    private void drawFocus(Canvas c) {
        background(c, Color.WHITE);
        topBrand(c);
        text(c, "Focus Mode", 22, 91, 27, NAVY, true);
        text(c, "Fokus dulu, scroll nanti.", 22, 116, 14, MUTED, false);
        drawCharacter(c, 315, 122, 63);
        float cx = 195, cy = 269, radius = 101;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(10); paint.setColor(Color.rgb(235, 238, 249)); c.drawCircle(cx, cy, radius, paint);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(themeAccentPrimary());
        float sweep = timerPreset > 0 ? 360f * timerSeconds / (timerPreset * 60f) : 360f;
        c.drawArc(new RectF(cx-radius, cy-radius, cx+radius, cy+radius), -90, sweep, false, paint);
        paint.setStyle(Paint.Style.FILL); paint.setStrokeCap(Paint.Cap.BUTT);
        centered(c, "◷", cx, cy - 38, 24, BLUE, true);
        centered(c, timerDisplay(), cx, cy + 13, 48, NAVY, true);
        chip(c, timerRunning ? "Sedang fokus" : "Focus Time", 153, cy + 39, 84, themeAccentPrimary(), themeAccentSurface());
        timerChoice(c, 22, 391, 105, 25, "fokus");
        timerChoice(c, 142, 391, 105, 15, "ringan");
        timerChoice(c, 262, 391, 106, 50, "deep work");
        card(c, 22, 465, 368, 525, 14);
        text(c, "Sedang fokus untuk:", 76, 488, 11, MUTED, false);
        text(c, selectedTask == null ? "Pilih tugas belajar" : ellipsize(selectedTask.title, 31), 76, 510, 13, NAVY, true);
        centered(c, "▤", 50, 502, 22, BLUE, true);
        outline(c, 302, 480, 352, 511, 9, LINE, 1); centered(c, "Ubah", 327, 500, 10, BLUE, true);
        gradientButton(c, 22, 544, 128, 590, timerRunning ? "Jeda" : "▶ Mulai");
        round(c, 142, 544, 248, 590, 12, Color.rgb(241, 244, 255)); centered(c, "↻ Reset", 195, 573, 13, BLUE, true);
        round(c, 262, 544, 368, 590, 12, Color.rgb(237, 250, 244)); centered(c, "✓ Selesai", 315, 573, 13, GREEN, true);
        card(c, 22, 606, 22, 606, 0);
        centered(c, "★", 50, 636, 24, Color.rgb(255, 187, 14), true);
        text(c, "", 75, 633, 10.5f, NAVY, false);
        card(c, 22, 606, 192, 688, 13);
        text(c, "Stat fokus", 37, 628, 12, NAVY, true);
        text(c, totalFocusMinutes + " menit", 37, 650, 17, themeAccentPrimary(), true);
        text(c, focusSessions + " sesi selesai", 37, 671, 10, MUTED, false);
        card(c, 202, 606, 368, 688, 13);
        text(c, "Riwayat terakhir", 217, 628, 12, NAVY, true);
        if (focusHistory.isEmpty()) {
            text(c, "Belum ada sesi fokus tercatat.", 217, 652, 10, MUTED, false);
        } else {
            for (int i = 0; i < Math.min(2, focusHistory.size()); i++) {
                FocusSession session = focusHistory.get(i);
                float rowY = 651 + i * 19;
                text(c, ellipsize(session.taskTitle, 15), 217, rowY, 10.5f, NAVY, true);
                text(c, session.minutes + " mnt", 326, rowY, 10, themeAccentPrimary(), true);
                text(c, shortDateTime(session.finishedAt), 217, rowY + 12, 8.5f, MUTED, false);
            }
        }
        bottomNav(c, 2);
    }

    private void timerChoice(Canvas c, float x, float y, float w, int minutes, String label) {
        if (timerPreset == minutes) outline(c, x, y, x + w, y + 58, 13, themeAccentPrimary(), 1.5f); else outline(c, x, y, x + w, y + 58, 13, LINE, 1);
        centered(c, "◷", x + 23, y + 32, 17, minutes == 25 ? BLUE : minutes == 15 ? ORANGE : PURPLE, true);
        text(c, minutes + " menit", x + 43, y + 27, 11.5f, NAVY, true);
        text(c, label, x + 43, y + 45, 10, MUTED, false);
    }

    private void drawProgress(Canvas c) {
        background(c, Color.WHITE);
        topBrand(c);
        text(c, "Hall of Achievement", 22, 91, 25, NAVY, true);
        text(c, "Statistik petualangan dan badge prestasimu.", 22, 114, 12, MUTED, false);
        card(c, 22, 132, 368, 226, 15);
        drawCharacter(c, 75, 177, 64);
        text(c, "Minggu ini kamu", 135, 160, 15, NAVY, true);
        text(c, "menyelesaikan " + doneCount() + " dari " + tasks.size() + " quest.", 135, 182, 14, NAVY, true);
        paint.setColor(Color.rgb(230, 233, 244)); c.drawRoundRect(new RectF(135, 197, 345, 204), 4, 4, paint);
        float completion = tasks.isEmpty() ? 0 : doneCount() / (float) tasks.size();
        paint.setColor(themeAccentPrimary()); c.drawRoundRect(new RectF(135, 197, 135 + 210 * completion, 204), 4, 4, paint);
        text(c, Math.round(completion * 100) + "% selesai", 135, 220, 10, themeAccentPrimary(), true);
        progressStat(c, 22, 241, 80, "⚔", "Quest Selesai", String.valueOf(doneCount()), GREEN);
        progressStat(c, 111, 241, 80, "⚠️", "Terlambat", String.valueOf(overdueCount()), ORANGE);
        progressStat(c, 200, 241, 80, "⚡", "Total XP", String.valueOf(xp), PURPLE);
        progressStat(c, 289, 241, 79, "🔥", "Streak", streak + " hari", ORANGE);
        card(c, 22, 339, 368, 481, 15);
        text(c, "▥  Produktivitas Mingguan", 37, 363, 13, NAVY, true);
        text(c, "Jumlah tugas yang selesai setiap hari", 37, 380, 9.5f, MUTED, false);
        String[] days = {"Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"};
        int[] values = weeklyProductivity();
        int chartMax=4;for(int value:values)chartMax=Math.max(chartMax,value);
        for (int i = 0; i < 7; i++) {
            float x = 54 + i * 43;
            float h = values[i] * 56f / chartMax;
            if(values[i]>0)round(c, x, 451 - h, x + 18, 451, 5, Color.rgb(157, 176, 247));
            centered(c, String.valueOf(values[i]), x + 9, 444 - h, 9, NAVY, false);
            centered(c, days[i], x + 9, 468, 9, MUTED, false);
        }
        text(c, "Badge Prestasi", 22, 510, 16, NAVY, true);
        badge(c, 22, 526, "🚀", "Quest Starter", GREEN, doneCount()>=1);
        badge(c, 112, 526, "🎯", "Focus Hero", PURPLE, focusSessions>=1);
        badge(c, 202, 526, "🔥", "Streak Master", ORANGE, streak >= 7);
        badge(c, 292, 526, "🛡️", "Anti Telat", MUTED, overdueCount() == 0&&doneCount()>0);
        featureButton(c, 22, 623, 188, 669, "▣", "Kalender Deadline", "Lihat jadwal", BLUE, Color.rgb(239, 243, 255));
        featureButton(c, 202, 623, 368, 669, "◆", "Reward Shop", "Tukar koin", ORANGE, Color.rgb(255, 247, 232));
        bottomNav(c, 3);
    }

    private void featureButton(Canvas c, float l, float t, float r, float b, String icon, String title, String subtitle, int accent, int bg) {
        paint.setShadowLayer(10, 0, 5, 0x160C1854);
        round(c, l, t, r, b, 13, bg);
        paint.clearShadowLayer();
        outline(c, l, t, r, b, 13, accent, 1.2f);
        round(c, l + 10, t + 9, l + 36, t + 35, 9, Color.WHITE);
        centered(c, icon, l + 23, t + 28, 13, accent, true);
        text(c, title, l + 43, t + 20, 10.2f, NAVY, true);
        text(c, subtitle, l + 43, t + 34, 8.2f, accent, false);
        centered(c, "›", r - 15, t + 30, 18, accent, true);
    }

    private void progressStat(Canvas c, float x, float y, float w, String icon, String label, String value, int accent) {
        round(c, x, y, x + w, y + 82, 14, Color.WHITE); outline(c, x, y, x + w, y + 82, 14, LINE, 1);
        centered(c, icon, x + w/2, y + 25, 17, accent, true);
        centered(c, label, x + w/2, y + 45, 8.5f, MUTED, false);
        centered(c, value, x + w/2, y + 69, 17, NAVY, true);
    }

    private void badge(Canvas c, float x, float y, String icon, String label, int accent, boolean earned) {
        card(c, x, y, x + 78, y + 92, 13);
        paint.setColor(earned ? accent : Color.rgb(188, 195, 210)); c.drawCircle(x + 39, y + 32, 23, paint);
        centered(c, icon, x + 39, y + 40, 23, Color.WHITE, true);
        centered(c, label, x + 39, y + 68, 8.5f, NAVY, true);
        centered(c, earned ? "Diperoleh" : "Terkunci", x + 39, y + 84, 7.5f, earned ? accent : MUTED, false);
    }

    private void drawCalendar(Canvas c) {
        background(c, Color.WHITE);
        topBack(c, "Kalender Deadline");
        Calendar month = Calendar.getInstance();
        text(c, "Kalender Quest", 22, 82, 20, NAVY, true);
        text(c, new SimpleDateFormat("MMMM yyyy", new Locale("id", "ID")).format(month.getTime()), 22, 104, 12, themeAccentPrimary(), true);
        List<Task> upcoming = upcomingTasks();
        card(c, 22, 122, 368, 407, 16);
        drawMonthCalendar(c, month, 37, 150);
        drawCalendarLegend(c, 37, 370);
        card(c, 22, 423, 368, 512, 15);
        text(c, "Ringkasan deadline", 37, 447, 13, NAVY, true);
        text(c, upcoming.isEmpty() ? "Belum ada quest aktif." : upcoming.size() + " quest aktif menunggu diselesaikan.", 37, 468, 10.5f, MUTED, false);
        text(c, overdueCount() + " telat", 37, 489, 12.5f, overdueCount() > 0 ? RED : GREEN, true);
        text(c, pendingCount() + " belum selesai", 126, 489, 12.5f, themeAccentPrimary(), true);
        text(c, "Reminder otomatis aktif untuk deadline kurang dari 3 hari.", 37, 505, 8.8f, MUTED, false);
        text(c, "Deadline terdekat", 22, 545, 16, NAVY, true);
        if (upcoming.isEmpty()) {
            card(c, 22, 561, 368, 621, 13);
            text(c, "Belum ada deadline yang akan datang.", 37, 597, 11, MUTED, false);
        } else {
            for (int i = 0; i < Math.min(3, upcoming.size()); i++) {
                Task task = upcoming.get(i);
                float y = 561 + i * 64;
                card(c, 22, y, 368, y + 60, 13);
                text(c, ellipsize(task.title, 28), 37, y + 23, 12, NAVY, true);
                text(c, task.course, 37, y + 42, 9.5f, MUTED, false);
                int status = calendarStatusForDay(task.deadline);
                text(c, deadlineLabel(task), 285, y + 23, 10.5f, calendarStatusColor(status), true);
                text(c, dateTimeLabel(task.deadline), 241, y + 42, 9.2f, MUTED, false);
            }
        }
    }

    private void drawMonthCalendar(Canvas c, Calendar month, float left, float top) {
        String[] days = {"Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"};
        float cellW = 45f, cellH = 31f;
        for (int i = 0; i < 7; i++) centered(c, days[i], left + i * cellW + cellW / 2, top, 8.5f, MUTED, true);
        Calendar first = (Calendar) month.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int firstDay = first.get(Calendar.DAY_OF_WEEK) - 1;
        int maxDay = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar today = Calendar.getInstance();
        for (int day = 1; day <= maxDay; day++) {
            int cell = firstDay + day - 1;
            int row = cell / 7, col = cell % 7;
            float x = left + col * cellW, y = top + 17 + row * cellH;
            boolean isToday = day == today.get(Calendar.DAY_OF_MONTH)
                    && month.get(Calendar.MONTH) == today.get(Calendar.MONTH)
                    && month.get(Calendar.YEAR) == today.get(Calendar.YEAR);
            int status = calendarStatusForDate(month.get(Calendar.YEAR), month.get(Calendar.MONTH), day);
            if (isToday) round(c, x + 5, y - 14, x + cellW - 5, y + 12, 9, themeAccentSurface());
            centered(c, String.valueOf(day), x + cellW / 2, y + 4, 10, isToday ? themeAccentPrimary() : NAVY, isToday);
            if (status > 0) {
                paint.setColor(calendarStatusColor(status));
                c.drawCircle(x + cellW / 2, y + 12, 3.2f, paint);
            }
        }
    }

    private void drawCalendarLegend(Canvas c, float x, float y) {
        legendItem(c, x, y, RED, "Telat");
        legendItem(c, x + 75, y, ORANGE, "< 3 hari");
        legendItem(c, x + 164, y, BLUE, "Mendatang");
        legendItem(c, x + 265, y, GREEN, "Selesai");
    }

    private void legendItem(Canvas c, float x, float y, int color, String label) {
        paint.setColor(resolveColor(color));
        c.drawCircle(x, y - 3, 4, paint);
        text(c, label, x + 9, y, 8.3f, MUTED, false);
    }

    private int calendarStatusForDate(int year, int month, int day) {
        int result = 0;
        for (Task task : tasks) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(task.deadline);
            if (cal.get(Calendar.YEAR) != year || cal.get(Calendar.MONTH) != month || cal.get(Calendar.DAY_OF_MONTH) != day) continue;
            int status = "Selesai".equals(task.status) ? 4 : calendarStatusForDay(task.deadline);
            if (status == 1) return 1;
            if (status == 2) result = Math.max(result, 2);
            else if (status == 3 && result < 2) result = 3;
            else if (status == 4 && result == 0) result = 4;
        }
        return result;
    }

    private int calendarStatusForDay(long deadline) {
        long diff = deadline - System.currentTimeMillis();
        if (diff < 0) return 1;
        if (diff <= TimeUnit.DAYS.toMillis(3)) return 2;
        return 3;
    }

    private int calendarStatusColor(int status) {
        if (status == 1) return RED;
        if (status == 2) return ORANGE;
        if (status == 4) return GREEN;
        return BLUE;
    }

    private void drawShop(Canvas c) {
        background(c, Color.WHITE);
        topBack(c, "Reward Shop");
        text(c, "Belanjakan Koinmu", 22, 82, 20, NAVY, true);
        text(c, "Tema yang dibeli bisa langsung dipakai ke tampilan aplikasi.", 22, 104, 11, MUTED, false);
        shopCard(c, 22, 126, "Classic", "Gratis", 0, "classic");
        shopCard(c, 22, 234, "Ocean", "120 koin", 120, "ocean");
        shopCard(c, 22, 342, "Sunset", "180 koin", 180, "sunset");
        card(c, 22, 470, 368, 544, 14);
        text(c, "Tema aktif", 37, 495, 12, NAVY, true);
        text(c, activeThemeLabel(), 37, 520, 18, themeAccentPrimary(), true);
        text(c, "Coins tersedia: " + coins, 230, 520, 12, NAVY, true);
    }

    private void shopCard(Canvas c, float x, float y, String title, String price, int cost, String key) {
        boolean owned = cost == 0 || isThemeOwned(key);
        boolean active = activeTheme.equals(key);
        card(c, x, y, 368, y + 88, 14);
        paint.setStyle(Paint.Style.FILL);
        Shader shader = null;
        if ("ocean".equals(key)) {
            shader = new LinearGradient(x + 15, y + 16, x + 61, y + 62,
                Color.rgb(0, 119, 182), Color.rgb(72, 202, 228), Shader.TileMode.CLAMP);
        } else if ("sunset".equals(key)) {
            shader = new LinearGradient(x + 15, y + 16, x + 61, y + 62,
                Color.rgb(242, 100, 25), Color.rgb(247, 184, 1), Shader.TileMode.CLAMP);
        } else {
            // classic
            shader = new LinearGradient(x + 15, y + 16, x + 61, y + 62,
                Color.rgb(13, 27, 42), Color.rgb(112, 141, 180), Shader.TileMode.CLAMP);
        }
        paint.setShader(shader);
        drawingRect.set(x + 15, y + 16, x + 61, y + 62);
        c.drawRoundRect(drawingRect, 12, 12, paint);
        paint.setShader(null);
        text(c, title, x + 76, y + 31, 14, NAVY, true);
        text(c, owned ? (active ? "Sedang dipakai" : "Sudah dimiliki") : price, x + 76, y + 51, 10.5f, owned ? themeAccentPrimary() : MUTED, false);
        round(c, 262, y + 24, 352, y + 58, 10, active ? themeAccentSurface() : Color.rgb(245, 247, 255));
        centered(c, active ? "Aktif" : owned ? "Pakai" : "Beli", 307, y + 46, 11, themeAccentPrimary(), true);
        text(c, cost == 0 ? "Tema bawaan" : "Ubah suasana belajar", x + 76, y + 71, 9, MUTED, false);
    }

    private void drawProfile(Canvas c) {
        background(c, Color.WHITE);
        topBrand(c);
        text(c, "Profil", 22, 91, 25, NAVY, true);
        round(c, 306, 66, 358, 106, 16, resolveColor(Color.rgb(242, 245, 255)));
        centered(c, darkMode ? "☀" : "☾", 332, 92, 18, themeAccentPrimary(), true);
        c.save();
        c.clipRect(0, 108, 390, logicalHeight - 70);
        c.translate(0, -profileScroll);
        card(c, 22, 108, 368, 225, 15);
        paint.setColor(resolveColor(Color.rgb(240, 243, 255))); c.drawCircle(77, 163, 44, paint); drawProfileAvatar(c, 77, 163, 44);
        text(c, profileName, 135, 140, 20, NAVY, true);
        text(c, loginEmail.isEmpty() ? "petualang@studymate.app" : loginEmail, 135, 160, 11, MUTED, false);
        text(c, "Level " + level() + "  •  " + characterRank(), 135, 190, 11, NAVY, true);
        paint.setColor(resolveColor(Color.rgb(230, 233, 244))); c.drawRoundRect(new RectF(135, 201, 349, 207), 3, 3, paint);
        paint.setColor(themeAccentPrimary()); c.drawRoundRect(new RectF(135, 201, 135 + 214 * (xp % 300) / 300f, 207), 3, 3, paint);
        text(c, "Ketuk avatar untuk ganti foto", 135, 216, 9.5f, MUTED, false);
        profileRow(c, 22, 240, 5, "Foto Profil", profileImageUri.isEmpty() ? "Gunakan foto dari galeri" : "Foto pribadi sudah dipasang", themeAccentPrimary());
        profileRow(c, 22, 293, 0, "Karakter Virtual",
                companionActive ? "Aktif • geser karakter untuk memindahkan" : characterRank() + " • ketuk untuk mengaktifkan", PURPLE);
        profileRow(c, 22, 346, 1, "Edit Profil", "Edit identitas dan data akademik", BLUE);
        profileRow(c, 22, 399, 2, "Kelola Mata Kuliah", courses.size() + " mata kuliah tersimpan", ORANGE);
        float accessBottom=accessibilityExpanded?722:497;
        card(c, 22, 452, 368, accessBottom, 14);
        drawAccessibilityIcon(c, 46, 475, GREEN);
        text(c, "Mode Aksesibilitas", 64, 476, 13, NAVY, true);
        drawChevron(c,350,474,accessibilityExpanded?3:1,MUTED);
        if(accessibilityExpanded){
            toggleRow(c, 37, 497, 0, "Font Besar", fontLarge, GREEN);
            toggleRow(c, 37, 542, 1, "Kontras Tinggi", highContrast, ORANGE);
            accessibilityLinkRow(c,37,587,2,"TalkBack","Buka pengaturan pembaca layar",BLUE);
            toggleRow(c, 37, 632, 3, "Voice Input", voiceEnabled, PURPLE);
        }
        float aboutY=accessibilityExpanded?685:505,logoutY=accessibilityExpanded?738:558;
        profileRow(c, 22, aboutY, 3, "Tentang Aplikasi", "StudyMate Quest versi 1.0", BLUE);
        profileRow(c, 22, logoutY, 4, "Logout", "Keluar dari akun StudyMate", RED);
        c.restore();
        bottomNav(c, 4);
    }

    private void profileRow(Canvas c, float l, float t, int icon, String title, String subtitle, int accent) {
        card(c, l, t, 368, t + 45, 12);
        round(c, l + 9, t + 7, l + 42, t + 38, 9, accent == RED ? resolveColor(Color.rgb(255, 240, 242)) : resolveColor(Color.rgb(242, 245, 255)));
        drawProfileIcon(c,icon,l+25,t+22.5f,accent);
        text(c, title, l + 53, t + 20, 12, NAVY, true);
        text(c, subtitle, l + 53, t + 36, 9.5f, MUTED, false);
        drawChevron(c,350,t+23,1,MUTED);
    }

    private void toggleRow(Canvas c, float x, float y, int icon, String title, boolean enabled, int accent) {
        drawAccessibilityOptionIcon(c,icon,x+14,y+22,accent);
        text(c, title, x + 37, y + 26, 10.5f, NAVY, true);
        round(c, 322, y + 12, 352, y + 32, 10, enabled ? themeAccentSecondary() : resolveColor(Color.rgb(216, 221, 235)));
        paint.setColor(Color.WHITE); c.drawCircle(enabled ? 342 : 332, y + 22, 8, paint);
        if (y < 632) { paint.setColor(resolveColor(LINE)); c.drawRect(x + 35, y + 44, 352, y + 45, paint); }
    }

    private void accessibilityLinkRow(Canvas c,float x,float y,int icon,String title,String subtitle,int accent){
        drawAccessibilityOptionIcon(c,icon,x+14,y+22,accent);
        text(c,title,x+37,y+18,10.5f,NAVY,true);text(c,subtitle,x+37,y+34,8.5f,MUTED,false);
        drawChevron(c,342,y+22,1,MUTED);paint.setColor(LINE);c.drawRect(x+35,y+44,352,y+45,paint);
    }

    private void drawProfileIcon(Canvas c,int type,float x,float y,int color){
        paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(color);path.reset();
        if(type==0){
            c.drawRoundRect(new RectF(x-8,y-7,x+8,y+6),5,5,paint);c.drawCircle(x-3,y-1,1,paint);c.drawCircle(x+3,y-1,1,paint);
            c.drawArc(new RectF(x-4,y,x+4,y+5),10,160,false,paint);c.drawLine(x-5,y-9,x-7,y-12,paint);c.drawLine(x+5,y-9,x+7,y-12,paint);
        }else if(type==5){
            c.drawCircle(x,y-3,5,paint);c.drawArc(new RectF(x-8,y+1,x+8,y+15),200,140,false,paint);
            c.drawRect(x+5,y-10,x+11,y-4,paint);
        }else if(type==1){
            path.moveTo(x-8,y+8);path.lineTo(x-5,y);path.lineTo(x+5,y-10);path.lineTo(x+10,y-5);path.lineTo(x,y+5);path.close();c.drawPath(path,paint);
            c.drawLine(x+3,y-8,x+8,y-3,paint);
        }else if(type==2){
            path.moveTo(x-10,y-4);path.lineTo(x,y-9);path.lineTo(x+10,y-4);path.lineTo(x,y+1);path.close();c.drawPath(path,paint);
            c.drawLine(x-6,y-1,x-6,y+6,paint);c.drawArc(new RectF(x-6,y+1,x+6,y+9),0,180,false,paint);c.drawLine(x+10,y-4,x+10,y+5,paint);
        }else if(type==3){
            c.drawCircle(x,y,10,paint);c.drawLine(x,y-1,x,y+6,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y-5,1.4f,paint);
        }else{
            c.drawRoundRect(new RectF(x-9,y-10,x+2,y+10),2,2,paint);c.drawLine(x-3,y,x+10,y,paint);
            path.moveTo(x+6,y-4);path.lineTo(x+10,y);path.lineTo(x+6,y+4);c.drawPath(path,paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawChevron(Canvas c,float x,float y,int direction,int color){
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(color);path.reset();
        if(direction==1){path.moveTo(x-3,y-5);path.lineTo(x+2,y);path.lineTo(x-3,y+5);}
        else{path.moveTo(x-5,y+3);path.lineTo(x,y-2);path.lineTo(x+5,y+3);}c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
    }

    private void drawAccessibilityIcon(Canvas c,float x,float y,int color){
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);c.drawCircle(x,y,10,paint);c.drawCircle(x,y-5,1.5f,paint);
        c.drawLine(x,y-3,x,y+4,paint);c.drawLine(x-6,y-1,x+6,y-1,paint);c.drawLine(x,y+3,x-4,y+8,paint);c.drawLine(x,y+3,x+4,y+8,paint);paint.setStyle(Paint.Style.FILL);
    }

    private void drawAccessibilityOptionIcon(Canvas c,int type,float x,float y,int color){
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);
        if(type==0){centered(c,"Aa",x,y+4,11,color,true);return;}
        if(type==1){c.drawCircle(x,y,7,paint);paint.setStyle(Paint.Style.FILL);c.drawArc(new RectF(x-7,y-7,x+7,y+7),90,180,true,paint);}
        else if(type==4){c.drawCircle(x,y,7,paint);c.drawArc(new RectF(x-3,y-7,x+7,y+3),110,250,false,paint);}
        else if(type==2){path.reset();path.moveTo(x-8,y-3);path.lineTo(x-4,y-3);path.lineTo(x+1,y-7);path.lineTo(x+1,y+7);path.lineTo(x-4,y+3);path.lineTo(x-8,y+3);path.close();c.drawPath(path,paint);c.drawArc(new RectF(x,y-5,x+8,y+5),-55,110,false,paint);}
        else{c.drawRoundRect(new RectF(x-4,y-8,x+4,y+3),4,4,paint);c.drawArc(new RectF(x-8,y-2,x+8,y+8),0,180,false,paint);c.drawLine(x,y+8,x,y+11,paint);}
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCourses(Canvas c) {
        background(c, Color.WHITE);
        topBack(c, "Kelola Mata Kuliah");
        text(c, "Mata Kuliah Semester Ini", 22, 82, 18, NAVY, true);
        text(c, "Daftar ini tersedia saat kamu menambah tugas.", 22, 104, 11, MUTED, false);
        gradientButton(c, 22, 122, 368, 164, "+  Tambah Mata Kuliah");
        for (int i = 0; i < Math.min(courses.size(), 6); i++) {
            float y = 183 + i * 65;
            card(c, 22, y, 368, y + 53, 13);
            round(c, 34, y + 10, 76, y + 43, 10, Color.rgb(241, 243, 255));
            centered(c, "◆", 55, y + 32, 17, BLUE, true);
            text(c, courses.get(i), 88, y + 25, 13, NAVY, true);
            text(c, taskCountForCourse(courses.get(i)) + " tugas", 88, y + 42, 9.5f, MUTED, false);
            centered(c, "✎", 317, y + 33, 15, BLUE, true);
            centered(c, "×", 346, y + 33, 20, RED, false);
        }
        text(c, "Tip: tekan nama mata kuliah untuk mengubahnya.", 22, logicalHeight - 22, 10, MUTED, false);
    }

    // region Interaction
    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = (event.getX() - offsetX) / scale, y = event.getY() / scale;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchDownX = x;
            touchDownY = y;
            lastTouchY = y;
            draggingScroll = false;
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float dy = y - lastTouchY;
            lastTouchY = y;
            if (Math.abs(y - touchDownY) > 8) draggingScroll = true;
            if (screen == TASKS) {
                updateTaskScroll(-dy);
            } else if (screen == PROFILE) {
                updateProfileScroll(-dy);
            }
            return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        if (draggingScroll) return true;
        performClick();
        if (screen >= HOME && screen != ADD_TASK && screen != COURSES && screen != CALENDAR && screen != SHOP && y >= logicalHeight - 70) {
            int nav = Math.max(0, Math.min(4, (int) (x / 78)));
            int[] screens = {HOME, TASKS, FOCUS, PROGRESS, PROFILE};
            showScreen(screens[nav]); return true;
        }
        switch (screen) {
            case LOGIN: handleLogin(x, y); break;
            case HOME: handleHome(x, y); break;
            case TASKS: handleTasks(x, y); break;
            case ADD_TASK: handleAdd(x, y); break;
            case DETAIL: handleDetail(x, y); break;
            case FOCUS: handleFocus(x, y); break;
            case PROGRESS: handleProgress(x, y); break;
            case PROFILE: handleProfile(x, y); break;
            case COURSES: handleCourses(x, y); break;
            case CALENDAR: handleCalendar(x, y); break;
            case SHOP: handleShop(x, y); break;
        }
        return true;
    }

    @Override public boolean performClick(){super.performClick();return true;}

    private void handleLogin(float x, float y) {
        if (between(y, 292, 347)) inputDialog("Email", loginEmail, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, s -> { loginEmail = s; invalidate(); });
        else if (between(y, 360, 415)) inputDialog("Password", loginPassword, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, s -> { loginPassword = s; invalidate(); });
        else if (between(y, 432, 484) || between(y, 557, 609)) login();
        else if (between(y, 490, 526)) showMessage("Tautan pemulihan password akan dikirim ke email Anda");
        else if (between(y, 625, 660)) inputDialog("Daftar dengan Email", loginEmail, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, s -> { loginEmail = s; login(); });
    }

    private void handleHome(float x, float y) {
        if (between(y, 136, 270) || between(y, 414, 445)) showScreen(TASKS);
        else if (between(y, 446, 590)) {
            List<Task> p = visibleTasks(0, ""); int idx = (int) ((y - 446) / 72);
            if (idx < p.size()) { selectedTask = p.get(idx); showScreen(DETAIL); }
        }
    }

    private void handleTasks(float x, float y) {
        if (between(y, 132, 174)) inputDialog("Cari Tugas", search, InputType.TYPE_CLASS_TEXT, s -> { search = s; invalidate(); });
        else if (between(y, 188, 219)) {
            float[] starts = {22,91,163,242,310}; float[] ends = {84,156,235,303,365};
            for (int i=0;i<5;i++) if (x>=starts[i]&&x<=ends[i]) {filter=i;taskScroll=0f;invalidate();return;}
        } else if (between(x, 316, 380) && between(y, logicalHeight - 132, logicalHeight - 70)) {
            newDraft(false); showScreen(ADD_TASK);
        } else if (between(y, 230, logicalHeight - 80)) {
            int idx = (int) (((y + taskScroll) - 230) / 91); List<Task> list = visibleTasks(filter, search);
            if (idx >= 0 && idx < list.size()) { selectedTask = list.get(idx); showScreen(DETAIL); }
        }
    }

    private void handleAdd(float x, float y) {
        if (y < 60) { showScreen(editing ? DETAIL : TASKS); return; }
        if (between(y, 92, 137)) {
            if (voiceEnabled && x > 320) activity.requestVoiceInput();
            else inputDialog("Judul Tugas", draft.title, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, s->{draft.title=s;invalidate();});
        } else if (between(y, 170, 215)) choiceDialog("Pilih Mata Kuliah", courses.toArray(new String[0]), s->{draft.course=s;invalidate();});
        else if (between(y, 248, 303)) {
            if (voiceEnabled && x > 320) activity.requestVoiceInput();
            else inputDialog("Deskripsi Tugas", draft.description, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, s->{draft.description=s;invalidate();});
        } else if (between(y, 339, 382) && x < 195) pickDate();
        else if (between(y, 339, 382)) pickTime();
        else if (between(y, 418, 461)) choiceDialog("Pilih Prioritas", new String[]{"Prioritas Tinggi","Prioritas Sedang","Prioritas Rendah"}, s->{draft.priority=s;invalidate();});
        else if (between(y, 497, 540)) choiceDialog("Status Tugas", new String[]{"Belum Dikerjakan","Sedang Dikerjakan","Selesai"}, s->{draft.status=s;draft.progress=s.equals("Selesai")?100:s.equals("Sedang Dikerjakan")?40:0;invalidate();});
        else if (between(y, 576, 617)) inputDialog("Catatan Tambahan", draft.notes, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, s->{draft.notes=s;invalidate();});
        else if (y > logicalHeight - 72) saveDraft();
    }

    private void handleDetail(float x, float y) {
        if (y < 60) { showScreen(TASKS); return; }
        if (between(y, 483, 522)) showScreen(FOCUS);
        else if (between(y, 528, 563)) { draft=selectedTask.copy(); editing=true; showScreen(ADD_TASK); }
        else if (between(y, 569, 604)) completeSelectedTask();
        else if (between(y, 610, 649)) new AlertDialog.Builder(activity).setTitle("Hapus quest?").setMessage(selectedTask.title).setNegativeButton("Batal", null).setPositiveButton("Hapus", (d,w)->{tasks.remove(selectedTask);selectedTask=null;saveData();showScreen(TASKS);}).show();
    }

    private void handleFocus(float x, float y) {
        if (between(y, 391, 449)) {
            int preset = x<130?25:x<250?15:50; timerPreset=preset;timerSeconds=preset*60;timerRunning=false;handler.removeCallbacks(timerTick);invalidate();
        } else if (between(y, 465, 525)) showTaskPicker();
        else if (between(y, 544, 595) && x < 135) toggleTimer();
        else if (between(y, 544, 595) && x < 255) resetTimer();
        else if (between(y, 544, 595)) finishFocusSession();
    }

    private void handleProgress(float x, float y) {
        if (between(y, 626, 664) && x < 195) showScreen(CALENDAR);
        else if (between(y, 626, 664)) showScreen(SHOP);
    }

    private void handleProfile(float x, float y) {
        float aboutY=accessibilityExpanded?685:505,logoutY=accessibilityExpanded?738:558;
        float sy = y + profileScroll;
        if (between(y, 66, 106) && x >= 306 && x <= 358) {
            darkMode=!darkMode;saveData();activity.configureSystemBars(false);announce("Mode malam " + (darkMode?"aktif":"nonaktif"));invalidate();
        }
        else if (between(sy, 119, 207) && x >= 33 && x <= 121) {
            activity.requestProfileImage();
        }
        else if (between(sy, 240, 285)) {
            activity.requestProfileImage();
        }
        else if (between(sy, 293, 338)) {
            companionActive=!companionActive;saveData();activity.updateCompanion(companionActive,level());
            announce("Karakter virtual " + (companionActive?"aktif dan dapat dipindahkan":"dinonaktifkan"));invalidate();
        }
        else if (between(sy, 346, 391)) activity.showProfileEditor(profileName,loginEmail,profileUniversity,profileMajor,profileSemester,characterClass);
        else if (between(sy, 399, 444)) showScreen(COURSES);
        else if (between(sy,452,496)){accessibilityExpanded=!accessibilityExpanded;invalidate();}
        else if (accessibilityExpanded&&between(sy, 497, 542)) {fontLarge=!fontLarge;saveData();announce("Font besar " + (fontLarge?"aktif":"nonaktif"));invalidate();}
        else if (accessibilityExpanded&&between(sy, 542, 587)) {highContrast=!highContrast;saveData();announce("Kontras tinggi " + (highContrast?"aktif":"nonaktif"));invalidate();}
        else if (accessibilityExpanded&&between(sy, 587, 632)) activity.openTalkBackSettings();
        else if (accessibilityExpanded&&between(sy, 632, 677)) {voiceEnabled=!voiceEnabled;saveData();announce("Voice input " + (voiceEnabled?"aktif":"nonaktif"));invalidate();}
        else if (between(sy,aboutY,aboutY+45)) new AlertDialog.Builder(activity).setTitle("Tentang StudyMate Quest").setMessage("Aplikasi manajemen tugas bergaya RPG. Selesaikan quest, kumpulkan XP, naik level, dan kembangkan karaktermu.\n\nData disimpan secara lokal di perangkat.").setPositiveButton("Tutup",null).show();
        else if (between(sy,logoutY,logoutY+45)) new AlertDialog.Builder(activity).setTitle("Logout?").setNegativeButton("Batal",null).setPositiveButton("Logout",(d,w)->{prefs.edit().putBoolean("logged_in",false).apply();showScreen(LOGIN);}).show();
    }

    private void handleCourses(float x, float y) {
        if (y < 60) { showScreen(PROFILE); return; }
        if (between(y, 122, 164)) inputDialog("Tambah Mata Kuliah", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, s->{if(!s.trim().isEmpty()){courses.add(s.trim());saveData();invalidate();}});
        else if (y >=183) {
            int i=(int)((y-183)/65); if(i<0||i>=courses.size())return;
            String old=courses.get(i);
            if(x>333) new AlertDialog.Builder(activity).setTitle("Hapus mata kuliah?").setMessage(old).setNegativeButton("Batal",null).setPositiveButton("Hapus",(d,w)->{courses.remove(old);saveData();invalidate();}).show();
            else inputDialog("Ubah Mata Kuliah",old,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,s->{int idx=courses.indexOf(old);if(idx>=0){courses.set(idx,s);for(Task t:tasks)if(t.course.equals(old))t.course=s;saveData();invalidate();}});
        }
    }

    private void handleCalendar(float x, float y) {
        if (y < 60) showScreen(PROGRESS);
    }

    private void handleShop(float x, float y) {
        if (y < 60) {
            showScreen(PROGRESS);
            return;
        }
        if (between(y, 126, 214)) applyOrBuyTheme("classic", 0);
        else if (between(y, 234, 322)) applyOrBuyTheme("ocean", 120);
        else if (between(y, 342, 430)) applyOrBuyTheme("sunset", 180);
    }
    // endregion

    // region Dialogs and actions
    private void inputDialog(String title, String current, int inputType, Consumer<String> done) {
        EditText input = new EditText(activity); input.setText(current); input.setInputType(inputType); input.setSelectAllOnFocus(true); input.setPadding(36,20,36,20);
        new AlertDialog.Builder(activity).setTitle(title).setView(input).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->done.accept(input.getText().toString().trim())).show();
    }

    private void choiceDialog(String title, String[] items, Consumer<String> done) {
        new AlertDialog.Builder(activity).setTitle(title).setItems(items,(d,which)->done.accept(items[which])).show();
    }

    private void pickDate() {
        Calendar cal=Calendar.getInstance();cal.setTimeInMillis(draft.deadline);
        new DatePickerDialog(activity,(v,year,month,day)->{cal.set(Calendar.YEAR,year);cal.set(Calendar.MONTH,month);cal.set(Calendar.DAY_OF_MONTH,day);draft.deadline=cal.getTimeInMillis();invalidate();},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime() {
        Calendar cal=Calendar.getInstance();cal.setTimeInMillis(draft.deadline);
        new TimePickerDialog(activity,(v,h,m)->{cal.set(Calendar.HOUR_OF_DAY,h);cal.set(Calendar.MINUTE,m);draft.deadline=cal.getTimeInMillis();invalidate();},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();
    }

    private void showTaskPicker() {
        List<Task> available=visibleTasks(0,"");
        if(available.isEmpty()){showMessage("Tambahkan tugas terlebih dahulu");return;}
        String[] names=new String[available.size()];for(int i=0;i<names.length;i++)names[i]=available.get(i).title;
        new AlertDialog.Builder(activity).setTitle("Fokus untuk tugas").setItems(names,(d,i)->{selectedTask=available.get(i);invalidate();}).show();
    }

    private void login() {
        if(loginEmail.isEmpty()) loginEmail="zahra.sanjaya@gmail.com";
        prefs.edit().putBoolean("logged_in",true).apply();
        showScreen(HOME);
    }

    private void newDraft(boolean edit) {
        draft=new Task();draft.id=System.currentTimeMillis();draft.course=courses.isEmpty()?"":courses.get(0);draft.priority="Prioritas Sedang";draft.status="Belum Dikerjakan";draft.estimate="1 jam";draft.deadline=System.currentTimeMillis()+TimeUnit.DAYS.toMillis(3);draft.description="";draft.notes="";draft.title="";draft.xpReward=60;editing=edit;
    }

    private void saveDraft() {
        if(draft.title.trim().isEmpty()){showMessage("Judul tugas belum diisi");return;}
        if(draft.course.trim().isEmpty()){showMessage("Pilih mata kuliah terlebih dahulu");return;}
        draft.xpReward=rewardForPriority(draft.priority);
        if("Selesai".equals(draft.status)){draft.progress=100;if(draft.completedAt==0)draft.completedAt=System.currentTimeMillis();}
        else {draft.completedAt=0;draft.progress="Sedang Dikerjakan".equals(draft.status)?Math.max(40,draft.progress):0;}
        if(editing){int idx=indexOfTask(draft.id);if(idx>=0)tasks.set(idx,draft);selectedTask=draft;}
        else {tasks.add(0,draft);selectedTask=draft;}
        saveData();showMessage(editing?"Perubahan quest disimpan":"Quest baru diterima. Semangat menaklukkannya!");showScreen(DETAIL);
    }

    private void completeSelectedTask() {
        if(selectedTask==null)return;
        if(!selectedTask.status.equals("Selesai")||!selectedTask.rewardClaimed){
            int oldLevel=level();selectedTask.status="Selesai";selectedTask.progress=100;
            if(selectedTask.completedAt==0)selectedTask.completedAt=System.currentTimeMillis();
            if(!selectedTask.rewardClaimed){xp+=selectedTask.xpReward;coins+=Math.max(5,selectedTask.xpReward/5);selectedTask.rewardClaimed=true;}
            streak=Math.max(1,streak);saveData();int newLevel=level();
            activity.updateCompanion(companionActive,newLevel);
            String message="Quest selesai!\n+"+selectedTask.xpReward+" XP   +"+Math.max(5,selectedTask.xpReward/5)+" koin";
            if(newLevel>oldLevel)message+="\n\nLEVEL UP! Sekarang kamu Level "+newLevel+"\nKarakter berkembang menjadi "+characterRank()+".";
            new AlertDialog.Builder(activity).setTitle(newLevel>oldLevel?"Level Up!":"Quest Complete!").setMessage(message).setPositiveButton("Keren!",null).show();
        }
        invalidate();
    }

    private void toggleTimer() {
        timerRunning=!timerRunning;handler.removeCallbacks(timerTick);if(timerRunning){lastTimerTick=System.currentTimeMillis();handler.postDelayed(timerTick,1000);}invalidate();
    }

    private void resetTimer() {timerRunning=false;handler.removeCallbacks(timerTick);timerSeconds=timerPreset*60;invalidate();}

    private void finishFocusSession() {
        int studiedMinutes=Math.max(1,(timerPreset*60-timerSeconds)/60);
        boolean hadProgress=timerSeconds<timerPreset*60;
        timerRunning=false;handler.removeCallbacks(timerTick);timerSeconds=timerPreset*60;
        if(hadProgress){
            int oldLevel=level();
            xp+=25;
            coins+=10;
            focusSessions++;
            totalFocusMinutes+=studiedMinutes;
            streak=Math.max(1,streak);
            focusHistory.add(0,new FocusSession(selectedTask==null?"Sesi fokus bebas":selectedTask.title,studiedMinutes,System.currentTimeMillis()));
            while(focusHistory.size()>12)focusHistory.remove(focusHistory.size()-1);
            if(selectedTask!=null&&!selectedTask.status.equals("Selesai")){
                selectedTask.status="Sedang Dikerjakan";
                selectedTask.progress=Math.min(90,selectedTask.progress+20);
            }
            saveData();
            int newLevel=level();
            activity.updateCompanion(companionActive,newLevel);
            String msg = "Sesi fokus selesai! +25 XP & +10 koin";
            if (newLevel > oldLevel) {
                msg += " • Level Up! Karakter berkembang.";
            }
            showMessage(msg);
        }
        invalidate();
    }

    private void showScreen(int next) {
        screen=next;
        if(next!=TASKS)taskScroll=0f;
        if(next!=PROFILE)profileScroll=0f;
        activity.configureSystemBars(next==SPLASH);
        if(next==LOGIN){activity.updateCompanion(false,1);activity.showAuth(!prefs.getBoolean("account_created",false));}
        else{
            activity.hideAuth();
            activity.hideProfileEditor();
            if(next==ADD_TASK){activity.hideCourseManager();activity.showQuestForm(draft,editing,courses.toArray(new String[0]));}
            else{
                activity.hideQuestForm();
                if(next==COURSES)activity.showCourseManager(courses.toArray(new String[0]));
                else activity.hideCourseManager();
            }
            activity.updateCompanion(companionActive,level());
        }
        invalidate();
        ViewCompat.setAccessibilityPaneTitle(this,screenNarration(next));
    }

    private String screenNarration(int value){
        switch(value){
            case HOME:return "Beranda. Level "+level()+". "+pendingCount()+" quest aktif dan "+xp+" total XP.";
            case TASKS:return "Quest Board. Terdapat "+visibleTasks(filter,search).size()+" quest pada daftar.";
            case ADD_TASK:return editing?"Halaman edit quest.":"Halaman quest baru. Isi semua data yang diperlukan.";
            case DETAIL:return selectedTask==null?"Detail quest.":"Detail quest "+selectedTask.title+". Deadline "+relativeDeadline(selectedTask)+". Status "+selectedTask.status+".";
            case FOCUS:return "Mode fokus. Timer pilihan saat ini "+timerPreset+" menit.";
            case PROGRESS:return "Hall of Achievement. "+doneCount()+" quest selesai, "+overdueCount()+" terlambat, dan streak "+streak+" hari.";
            case PROFILE:return "Profil "+profileName+". Level "+level()+". Buka menu aksesibilitas untuk mengatur bantuan penggunaan.";
            case COURSES:return "Kelola mata kuliah. Terdapat "+courses.size()+" mata kuliah tersimpan.";
            case CALENDAR:return "Kalender deadline. Ada "+upcomingTasks().size()+" quest aktif yang perlu dipantau.";
            case SHOP:return "Reward shop. Kamu memiliki "+coins+" koin untuk dibelanjakan.";
            default:return "StudyMate Quest";
        }
    }

    void completeAuth(String name, String email) {
        loadAccountData(name,email,true);
        updateDailyStreak();
        saveData();
        activity.enableDeadlineNotifications();
        if(shortcutAddPending){shortcutAddPending=false;newDraft(false);showScreen(ADD_TASK);}
        else showScreen(HOME);
        showMessage("Selamat datang, " + firstName() + "! Petualanganmu dimulai.");
    }

    void openAddQuestShortcut(){
        shortcutAddPending=true;
        if(screen>=HOME&&prefs.getBoolean("logged_in",false)){
            shortcutAddPending=false;newDraft(false);showScreen(ADD_TASK);
        }
    }

    void submitQuestForm(Task value){
        draft=value;
        activity.hideQuestForm();
        saveDraft();
    }

    void cancelQuestForm(){showScreen(editing?DETAIL:TASKS);}

    void closeProfileEditor(){activity.hideProfileEditor();showScreen(PROFILE);}

    void closeCourseManager(){showScreen(PROFILE);}

    void applyProfileUpdate(String name,String email,String university,String major,String semester, String characterClass){
        loadAccountData(name,email,false);profileName=name;loginEmail=email.toLowerCase(Locale.ROOT);
        profileUniversity=university;profileMajor=major;profileSemester=semester;
        this.characterClass=characterClass;
        saveData();showScreen(PROFILE);
        showMessage("Profil berhasil diperbarui");
    }

    void applyProfileImage(String uriValue){
        profileImageUri=uriValue==null?"":uriValue;
        profileImageBitmap=null;
        saveData();
        invalidate();
        showMessage(profileImageUri.isEmpty()?"Foto profil dihapus":"Foto profil diperbarui");
    }

    void addCourse(String value){
        String clean=value.trim();if(clean.isEmpty())return;
        for(String course:courses)if(course.equalsIgnoreCase(clean)){showMessage("Mata kuliah sudah tersimpan");return;}
        courses.add(clean);saveData();activity.showCourseManager(courses.toArray(new String[0]));showMessage("Mata kuliah ditambahkan");
    }

    void renameCourse(String oldValue,String newValue){
        String clean=newValue.trim();if(clean.isEmpty())return;
        for(String course:courses)if(!course.equalsIgnoreCase(oldValue)&&course.equalsIgnoreCase(clean)){
            showMessage("Nama mata kuliah sudah digunakan");return;}
        int index=courses.indexOf(oldValue);if(index<0)return;courses.set(index,clean);
        for(Task task:tasks)if(task.course.equals(oldValue))task.course=clean;
        saveData();activity.showCourseManager(courses.toArray(new String[0]));showMessage("Mata kuliah diperbarui");
    }

    void deleteCourse(String value){
        courses.remove(value);saveData();activity.showCourseManager(courses.toArray(new String[0]));showMessage("Mata kuliah dihapus");
    }

    void acceptVoiceText(String value) {
        if(screen==ADD_TASK&&draft!=null){if(draft.title.isEmpty())draft.title=value;else draft.description=value;invalidate();showMessage("Teks suara berhasil ditambahkan");}
    }

    void showMessage(String value) {Toast.makeText(activity,value,Toast.LENGTH_SHORT).show();}
    boolean isDarkModeEnabled(){return darkMode;}
    boolean isVoiceInputEnabled(){return voiceEnabled;}
    boolean handleBack() {
        if (screen == SPLASH || screen == LOGIN || screen == HOME) return false;
        if (screen == ADD_TASK) showScreen(editing ? DETAIL : TASKS);
        else if (screen == DETAIL) showScreen(TASKS);
        else if (screen == COURSES) showScreen(PROFILE);
        else if (screen == CALENDAR || screen == SHOP) showScreen(PROGRESS);
        else showScreen(HOME);
        return true;
    }
    private void announce(String value){announceForAccessibility(value);}
    // endregion

    // region Data and helpers
    private void loadData() {
        String email=prefs.getString("current_account",prefs.getString("email",""));
        if(prefs.getBoolean("logged_in",false)&&!email.isEmpty()){
            loadAccountData(prefs.getString("name","Petualang"),email,true);
            updateDailyStreak();
        }else resetAccountData();
    }

    private void resetAccountData(){
        tasks.clear();courses.clear();focusHistory.clear();selectedTask=null;draft=null;dataPrefs=null;
        xp=0;streak=0;coins=0;focusSessions=0;totalFocusMinutes=0;profileName="Petualang Baru";loginEmail="";
        profileUniversity="";profileMajor="";profileSemester="";
        fontLarge=false;highContrast=false;darkMode=false;voiceEnabled=true;companionActive=false;profileImageUri="";profileImageBitmap=null;
        activeTheme="classic";
        characterClass="Ksatria";
    }

    private void loadAccountData(String fallbackName,String email,boolean migrateLegacy){
        resetAccountData();
        loginEmail=email.toLowerCase(Locale.ROOT);
        dataPrefs=activity.getSharedPreferences(MainActivity.accountStoreName(loginEmail),Context.MODE_PRIVATE);
        if(!dataPrefs.getBoolean("initialized",false)){
            SharedPreferences.Editor editor=dataPrefs.edit().putBoolean("initialized",true)
                    .putString("name",fallbackName).putString("email",loginEmail)
                    .putString("character_class", "Ksatria");
            boolean hasLegacy=migrateLegacy&&loginEmail.equalsIgnoreCase(prefs.getString("email",""))&&
                    (prefs.contains("tasks")||prefs.contains("courses")||prefs.contains("xp"));
            if(hasLegacy){
                editor.putString("tasks",prefs.getString("tasks","[]"))
                        .putString("courses",prefs.getString("courses","[]"))
                        .putString("university",prefs.getString("university",""))
                        .putString("major",prefs.getString("major",""))
                        .putString("semester",prefs.getString("semester",""))
                        .putString("character_class",prefs.getString("character_class","Ksatria"))
                        .putInt("xp",prefs.getInt("xp",0)).putInt("streak",prefs.getInt("streak",0))
                        .putInt("coins",prefs.getInt("coins",0))
                        .putBoolean("font_large",prefs.getBoolean("font_large",false))
                        .putBoolean("high_contrast",prefs.getBoolean("high_contrast",false))
                        .putBoolean("tts",prefs.getBoolean("tts",false))
                        .putBoolean("voice",prefs.getBoolean("voice",true));
            }else editor.putString("tasks","[]").putString("courses","[]")
                    .putInt("xp",0).putInt("streak",0).putInt("coins",0);
            editor.apply();
        }
        xp=dataPrefs.getInt("xp",0);streak=dataPrefs.getInt("streak",0);coins=dataPrefs.getInt("coins",0);focusSessions=dataPrefs.getInt("focus_sessions",0);totalFocusMinutes=dataPrefs.getInt("focus_minutes",0);
        profileName=dataPrefs.getString("name",fallbackName);loginEmail=dataPrefs.getString("email",loginEmail);
        profileUniversity=dataPrefs.getString("university","");profileMajor=dataPrefs.getString("major","");
        profileSemester=dataPrefs.getString("semester","");
        fontLarge=dataPrefs.getBoolean("font_large",false);highContrast=dataPrefs.getBoolean("high_contrast",false);darkMode=dataPrefs.getBoolean("dark_mode",false);
        voiceEnabled=dataPrefs.getBoolean("voice",true);
        companionActive=dataPrefs.getBoolean("companion_active",false);
        activeTheme=dataPrefs.getString("active_theme","classic");
        profileImageUri=dataPrefs.getString("profile_image_uri","");
        characterClass=dataPrefs.getString("character_class","Ksatria");
        profileImageBitmap=null;
        try{JSONArray arr=new JSONArray(dataPrefs.getString("courses","[]"));for(int i=0;i<arr.length();i++)courses.add(arr.getString(i));}catch(Exception ignored){}
        try{JSONArray arr=new JSONArray(dataPrefs.getString("tasks","[]"));for(int i=0;i<arr.length();i++)tasks.add(Task.fromJson(arr.getJSONObject(i)));}catch(Exception ignored){}
        try{JSONArray arr=new JSONArray(dataPrefs.getString("focus_history","[]"));for(int i=0;i<arr.length();i++)focusHistory.add(FocusSession.fromJson(arr.getJSONObject(i)));}catch(Exception ignored){}
    }

    private void updateDailyStreak() {
        String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
        if(dataPrefs==null)return;
        String last=dataPrefs.getString("last_active_date","");
        if(today.equals(last))return;
        if(last.isEmpty()){
            dataPrefs.edit().putString("last_active_date",today).putInt("streak",0).apply();
            return;
        }
        Calendar yesterday=Calendar.getInstance();yesterday.add(Calendar.DAY_OF_YEAR,-1);
        String expected=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(yesterday.getTime());
        streak=expected.equals(last)?Math.max(1,streak+1):1;
        dataPrefs.edit().putString("last_active_date",today).putInt("streak",streak).apply();
    }

    private void seedTasks() {
        long now=System.currentTimeMillis();
        tasks.add(new Task(1,"Laporan Praktikum P14","Mobile Programming","Buat laporan aplikasi mobile yang mencakup analisis kebutuhan, perancangan sistem, implementasi fitur utama, dan pengujian aplikasi.",now+TimeUnit.DAYS.toMillis(1),"Prioritas Tinggi","Sedang Dikerjakan","2 jam","Sertakan screenshot dan kesimpulan.",40));
        tasks.add(new Task(2,"Ringkasan Materi Sistem Operasi","Sistem Operasi","Rangkum materi manajemen proses dan memori untuk persiapan ujian.",now+TimeUnit.DAYS.toMillis(4),"Prioritas Sedang","Belum Dikerjakan","1 jam","Gunakan catatan perkuliahan.",20));
        tasks.add(new Task(3,"Latihan Soal Struktur Data","Struktur Data dan Algoritma","Kerjakan latihan tree, graph, dan sorting pada modul minggu ini.",now+TimeUnit.DAYS.toMillis(2),"Prioritas Rendah","Sedang Dikerjakan","2 jam","Fokus pada soal graph.",60));
        tasks.add(new Task(4,"Quiz Pemrograman Dasar","Algoritma dan Pemrograman","Selesaikan quiz pemrograman dasar.",now-TimeUnit.DAYS.toMillis(1),"Prioritas Rendah","Selesai","30 menit","",100));
        saveData();
    }

    private void saveData() {
        if(dataPrefs==null)return;
        JSONArray tarr=new JSONArray();for(Task t:tasks)tarr.put(t.toJson());JSONArray carr=new JSONArray();for(String s:courses)carr.put(s);
        JSONArray historyArr=new JSONArray();for(FocusSession session:focusHistory)historyArr.put(session.toJson());
        dataPrefs.edit().putString("tasks",tarr.toString()).putString("courses",carr.toString()).putString("focus_history",historyArr.toString()).putInt("xp",xp).putInt("streak",streak).putInt("coins",coins).putInt("focus_sessions",focusSessions).putInt("focus_minutes",totalFocusMinutes)
                .putString("name",profileName).putString("email",loginEmail).putString("university",profileUniversity)
                .putString("major",profileMajor).putString("semester",profileSemester)
                .putString("character_class",characterClass)
                .putBoolean("font_large",fontLarge).putBoolean("high_contrast",highContrast).putBoolean("dark_mode",darkMode)
                .putBoolean("voice",voiceEnabled)
                .putString("profile_image_uri",profileImageUri)
                .putString("active_theme",activeTheme)
                .putBoolean("theme_owned_ocean",isThemeOwned("ocean"))
                .putBoolean("theme_owned_sunset",isThemeOwned("sunset"))
                .putBoolean("companion_active",companionActive).apply();
        DeadlineNotificationService.schedule(activity);
    }

    private List<Task> visibleTasks(int mode,String query) {
        List<Task> result=new ArrayList<>();long now=System.currentTimeMillis();
        for(Task t:tasks){boolean ok=true;if(mode==1)ok=Math.abs(t.deadline-now)<TimeUnit.DAYS.toMillis(1);else if(mode==2)ok=t.priority.equals("Prioritas Tinggi");else if(mode==3)ok=t.status.equals("Selesai");else if(mode==4)ok=t.deadline<now&&!t.status.equals("Selesai");if(!query.isEmpty())ok&=(t.title+" "+t.course).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));if(ok)result.add(t);}return result;
    }

    private int pendingCount(){int n=0;for(Task t:tasks)if(!t.status.equals("Selesai"))n++;return n;}
    private int doneCount(){int n=0;for(Task t:tasks)if(t.status.equals("Selesai"))n++;return n;}
    private int[] weeklyProductivity(){
        int[] result=new int[7];
        Calendar start=Calendar.getInstance();int offset=(start.get(Calendar.DAY_OF_WEEK)+5)%7;
        start.add(Calendar.DAY_OF_YEAR,-offset);start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
        long from=start.getTimeInMillis(),until=from+TimeUnit.DAYS.toMillis(7);
        for(Task task:tasks)if(task.completedAt>=from&&task.completedAt<until){Calendar completed=Calendar.getInstance();completed.setTimeInMillis(task.completedAt);int index=(completed.get(Calendar.DAY_OF_WEEK)+5)%7;result[index]++;}
        return result;
    }
    private int overdueCount(){int n=0;long now=System.currentTimeMillis();for(Task t:tasks)if(t.deadline<now&&!t.status.equals("Selesai"))n++;return n;}
    private List<Task> upcomingTasks(){
        List<Task> result=new ArrayList<>();
        for(Task task:tasks)if(!"Selesai".equals(task.status))result.add(task);
        Collections.sort(result,(a,b)->Long.compare(a.deadline,b.deadline));
        return result;
    }
    private void updateTaskScroll(float delta){
        List<Task> list = visibleTasks(filter, search);
        float contentHeight = Math.max(0, list.size() * 91f - (logicalHeight - 310f));
        taskScroll = Math.max(0f, Math.min(contentHeight, taskScroll + delta));
        invalidate();
    }
    private void updateProfileScroll(float delta){
        float contentBottom = accessibilityExpanded ? 828f : 603f;
        float visibleBottom = logicalHeight - 78f;
        float maxScroll = Math.max(0f, contentBottom - visibleBottom);
        profileScroll = Math.max(0f, Math.min(maxScroll, profileScroll + delta));
        invalidate();
    }
    private int taskCountForCourse(String s){int n=0;for(Task t:tasks)if(t.course.equals(s))n++;return n;}
    private int indexOfTask(long id){for(int i=0;i<tasks.size();i++)if(tasks.get(i).id==id)return i;return -1;}
    private int level(){return GameRules.levelForXp(xp);}
    private int unlockedBadges(){int n=0;if(doneCount()>=1)n++;if(focusSessions>=1)n++;if(streak>=7)n++;if(level()>=3)n++;if(overdueCount()==0&&doneCount()>0)n++;return Math.min(5,n);}
    static int rewardForPriority(String priority){return GameRules.rewardForPriority(priority);}
    private String difficulty(Task task){if(task.priority.contains("Tinggi"))return "EPIC";if(task.priority.contains("Sedang"))return "RARE";return "EASY";}
    private String firstName(){String[] p=profileName.split(" ");return p.length==0?"Kamu":p[0];}
    private boolean between(float value,float a,float b){return value>=a&&value<=b;}
    private String ellipsize(String s,int max){if(s==null)return "";return s.length()>max?s.substring(0,Math.max(0,max-1))+"…":s;}
    private Rect logicalRect(float left,float top,float right,float bottom){
        return new Rect(
                Math.round(offsetX + left * scale),
                Math.round(top * scale),
                Math.round(offsetX + right * scale),
                Math.round(bottom * scale)
        );
    }
    private void addAccessibilityElement(List<AccessibilityElement> elements,int id,String label,float left,float top,float right,float bottom){
        addAccessibilityElement(elements,id,label,left,top,right,bottom,null);
    }
    private void addAccessibilityElement(List<AccessibilityElement> elements,int id,String label,float left,float top,float right,float bottom,String hint){
        if(right <= left || bottom <= top)return;
        Rect bounds = logicalRect(left, top, right, bottom);
        if(bounds.bottom < 0 || bounds.top > getHeight())return;
        elements.add(new AccessibilityElement(id, label, hint, bounds));
    }
    private List<AccessibilityElement> buildAccessibilityElements(){
        List<AccessibilityElement> elements = new ArrayList<>();
        if(screen == LOGIN){
            addAccessibilityElement(elements, 10, "Email " + (loginEmail.isEmpty() ? "kosong" : loginEmail), 31, 292, 359, 347);
            addAccessibilityElement(elements, 11, "Password " + (loginPassword.isEmpty() ? "kosong" : "terisi"), 31, 360, 359, 415);
            addAccessibilityElement(elements, 12, "Masuk", 31, 432, 359, 484);
            addAccessibilityElement(elements, 13, "Lupa password", 240, 490, 359, 526);
            addAccessibilityElement(elements, 14, "Masuk dengan Google", 31, 557, 359, 609);
            addAccessibilityElement(elements, 15, "Daftar akun baru", 120, 625, 270, 660);
        }else if(screen == HOME){
            addAccessibilityElement(elements, 100, "Quest Board. Buka daftar quest", 22, 136, 368, 270);
            List<Task> pending = visibleTasks(0, "");
            for(int i=0;i<Math.min(2, pending.size());i++){
                Task task = pending.get(i);
                addAccessibilityElement(elements, 110 + i, "Quest terdekat " + task.title + ". " + relativeDeadline(task), 22, 448 + i * 72, 368, 508 + i * 72);
            }
        }else if(screen == TASKS){
            addAccessibilityElement(elements, 200, "Cari quest atau mata kuliah", 22, 132, 368, 174);
            float[] starts = {22,91,163,242,310};
            float[] ends = {84,156,235,303,365};
            String[] filters = {"Filter semua", "Filter hari ini", "Filter prioritas", "Filter selesai", "Filter telat"};
            for(int i=0;i<filters.length;i++) addAccessibilityElement(elements, 210 + i, filters[i], starts[i], 188, ends[i], 216);
            List<Task> list = visibleTasks(filter, search);
            for(int i=0;i<list.size();i++){
                float top = 230 + i * 91 - taskScroll;
                float bottom = top + 79;
                if(bottom < 230 || top > logicalHeight - 70) continue;
                Task task = list.get(i);
                addAccessibilityElement(elements, 300 + i, task.title + ". " + task.course + ". " + relativeDeadline(task) + ". Progress " + task.progress + " persen", 22, top, 368, bottom);
            }
            addAccessibilityElement(elements, 400, "Tambah quest baru", 316, logicalHeight - 132, 380, logicalHeight - 70);
        }else if(screen == ADD_TASK){
            addAccessibilityElement(elements, 450, editing ? "Kembali ke detail quest" : "Kembali ke daftar quest", 20, 15, 52, 48);
            addAccessibilityElement(elements, 451, "Isi judul quest", 22, 92, 368, 137);
            addAccessibilityElement(elements, 452, "Pilih mata kuliah", 22, 170, 368, 215);
            addAccessibilityElement(elements, 453, "Isi deskripsi quest", 22, 248, 368, 303);
            addAccessibilityElement(elements, 454, "Pilih tanggal deadline", 22, 339, 187, 382);
            addAccessibilityElement(elements, 455, "Pilih jam deadline", 202, 339, 368, 382);
            addAccessibilityElement(elements, 456, "Pilih prioritas", 22, 418, 368, 461);
            addAccessibilityElement(elements, 457, "Pilih status quest", 22, 497, 368, 540);
            addAccessibilityElement(elements, 458, "Isi catatan tambahan", 22, 576, 368, 617);
            addAccessibilityElement(elements, 459, editing ? "Simpan perubahan quest" : "Terima quest baru", 22, logicalHeight - 58, 368, logicalHeight - 13);
        }else if(screen == DETAIL && selectedTask != null){
            addAccessibilityElement(elements, 500, "Kembali ke daftar quest", 20, 15, 52, 48);
            addAccessibilityElement(elements, 501, "Mulai fokus untuk " + selectedTask.title, 22, 483, 368, 522);
            addAccessibilityElement(elements, 502, "Edit quest " + selectedTask.title, 22, 528, 368, 563);
            addAccessibilityElement(elements, 503, "Tandai quest selesai", 22, 569, 368, 604);
            addAccessibilityElement(elements, 504, "Hapus quest " + selectedTask.title, 22, 610, 368, 649);
        }else if(screen == FOCUS){
            addAccessibilityElement(elements, 600, "Preset fokus 25 menit", 22, 391, 127, 449);
            addAccessibilityElement(elements, 601, "Preset fokus 15 menit", 142, 391, 247, 449);
            addAccessibilityElement(elements, 602, "Preset fokus 50 menit", 262, 391, 368, 449);
            addAccessibilityElement(elements, 603, "Pilih tugas untuk sesi fokus", 22, 465, 368, 525);
            addAccessibilityElement(elements, 604, timerRunning ? "Jeda timer fokus" : "Mulai timer fokus", 22, 544, 128, 590);
            addAccessibilityElement(elements, 605, "Reset timer fokus", 142, 544, 248, 590);
            addAccessibilityElement(elements, 606, "Selesaikan sesi fokus", 262, 544, 368, 590);
        }else if(screen == PROGRESS){
            addAccessibilityElement(elements, 700, "Buka kalender deadline. Lihat jadwal quest dalam kalender", 22, 623, 188, 669);
            addAccessibilityElement(elements, 701, "Buka reward shop. Tukar koin dengan tema", 202, 623, 368, 669);
        }else if(screen == PROFILE){
            addAccessibilityElement(elements, 800, darkMode ? "Mode terang. Ketuk untuk menonaktifkan mode malam" : "Mode malam. Ketuk untuk mengaktifkan mode malam", 306, 66, 358, 106);
            float[][] rows = {{22,108,368,225},{22,240,368,285},{22,293,368,338},{22,346,368,391},{22,399,368,444},{22,452,368,496},{22,505,368,550},{22,558,368,603}};
            String[] labels = {"Foto profil " + (profileImageUri.isEmpty() ? "belum diatur" : "sudah dipasang"),"Gunakan foto profil dari galeri",companionActive ? "Karakter virtual aktif" : "Aktifkan karakter virtual","Edit profil","Kelola mata kuliah",accessibilityExpanded ? "Tutup mode aksesibilitas" : "Buka mode aksesibilitas","Tentang aplikasi","Logout"};
            for(int i=0;i<rows.length;i++){
                float top = rows[i][1] - profileScroll;
                float bottom = rows[i][3] - profileScroll;
                if(bottom < 108 || top > logicalHeight - 70) continue;
                addAccessibilityElement(elements, 810 + i, labels[i], rows[i][0], top, rows[i][2], bottom);
            }
            if(accessibilityExpanded){
                float[][] accessRows = {{37,497,352,542},{37,542,352,587},{37,587,352,632},{37,632,352,677}};
                String[] accessLabels = {"Font besar " + (fontLarge ? "aktif" : "nonaktif"),"Kontras tinggi " + (highContrast ? "aktif" : "nonaktif"),"Buka pengaturan TalkBack","Voice input " + (voiceEnabled ? "aktif" : "nonaktif")};
                for(int i=0;i<accessRows.length;i++){
                    float top = accessRows[i][1] - profileScroll;
                    float bottom = accessRows[i][3] - profileScroll;
                    if(bottom < 108 || top > logicalHeight - 70) continue;
                    addAccessibilityElement(elements, 830 + i, accessLabels[i], accessRows[i][0], top, accessRows[i][2], bottom);
                }
            }
        }else if(screen == COURSES){
            addAccessibilityElement(elements, 880, "Kembali ke profil", 20, 15, 52, 48);
            addAccessibilityElement(elements, 881, "Tambah mata kuliah", 22, 122, 368, 164);
            for (int i = 0; i < Math.min(courses.size(), 6); i++) {
                float y = 183 + i * 65;
                addAccessibilityElement(elements, 890 + i, "Ubah mata kuliah " + courses.get(i), 22, y, 333, y + 53);
                addAccessibilityElement(elements, 940 + i, "Hapus mata kuliah " + courses.get(i), 334, y, 368, y + 53);
            }
        }else if(screen == CALENDAR){
            addAccessibilityElement(elements, 930, "Kembali ke halaman prestasi", 20, 15, 52, 48);
            List<Task> upcoming = upcomingTasks();
            for(int i=0;i<Math.min(3, upcoming.size());i++){
                float y = 561 + i * 64;
                Task task = upcoming.get(i);
                addAccessibilityElement(elements, 931 + i, task.title + ". " + task.course + ". " + deadlineLabel(task), 22, y, 368, y + 60);
            }
        }else if(screen == SHOP){
            addAccessibilityElement(elements, 949, "Kembali ke halaman prestasi", 20, 15, 52, 48);
            addAccessibilityElement(elements, 950, "Tema Classic", 22, 126, 368, 214);
            addAccessibilityElement(elements, 951, "Tema Ocean", 22, 234, 368, 322);
            addAccessibilityElement(elements, 952, "Tema Sunset", 22, 342, 368, 430);
        }
        if(screen >= HOME && screen != ADD_TASK && screen != COURSES && screen != CALENDAR && screen != SHOP){
            String[] nav = {"Beranda", "Quest", "Fokus", "Prestasi", "Profil"};
            for(int i=0;i<5;i++){
                float left = i * 78f;
                addAccessibilityElement(elements, 1000 + i, "Navigasi " + nav[i], left, logicalHeight - 66, left + 78, logicalHeight);
            }
        }
        return elements;
    }
    private boolean activateAccessibilityElement(int id){
        if(id == 10){ inputDialog("Email", loginEmail, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, s -> { loginEmail = s; invalidate(); }); return true; }
        if(id == 11){ inputDialog("Password", loginPassword, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, s -> { loginPassword = s; invalidate(); }); return true; }
        if(id == 12 || id == 14){ login(); return true; }
        if(id == 13){ showMessage("Tautan pemulihan password akan dikirim ke email Anda"); return true; }
        if(id == 15){ inputDialog("Daftar dengan Email", loginEmail, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, s -> { loginEmail = s; login(); }); return true; }
        if(id == 100){ showScreen(TASKS); return true; }
        if(id >= 110 && id < 120){
            List<Task> pending = visibleTasks(0, "");
            int index = id - 110;
            if(index < pending.size()){ selectedTask = pending.get(index); showScreen(DETAIL); return true; }
        }
        if(id == 200){ inputDialog("Cari Tugas", search, InputType.TYPE_CLASS_TEXT, s -> { search = s; invalidate(); }); return true; }
        if(id >= 210 && id <= 214){ filter = id - 210; taskScroll = 0f; invalidate(); return true; }
        if(id >= 300 && id < 400){
            List<Task> list = visibleTasks(filter, search);
            int index = id - 300;
            if(index < list.size()){ selectedTask = list.get(index); showScreen(DETAIL); return true; }
        }
        if(id == 400){ newDraft(false); showScreen(ADD_TASK); return true; }
        if(id == 450){ showScreen(editing ? DETAIL : TASKS); return true; }
        if(id == 451){ inputDialog("Judul Tugas", draft.title, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, s->{draft.title=s;invalidate();}); return true; }
        if(id == 452){ choiceDialog("Pilih Mata Kuliah", courses.toArray(new String[0]), s->{draft.course=s;invalidate();}); return true; }
        if(id == 453){ inputDialog("Deskripsi Tugas", draft.description, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, s->{draft.description=s;invalidate();}); return true; }
        if(id == 454){ pickDate(); return true; }
        if(id == 455){ pickTime(); return true; }
        if(id == 456){ choiceDialog("Pilih Prioritas", new String[]{"Prioritas Tinggi","Prioritas Sedang","Prioritas Rendah"}, s->{draft.priority=s;invalidate();}); return true; }
        if(id == 457){ choiceDialog("Status Tugas", new String[]{"Belum Dikerjakan","Sedang Dikerjakan","Selesai"}, s->{draft.status=s;draft.progress=s.equals("Selesai")?100:s.equals("Sedang Dikerjakan")?40:0;invalidate();}); return true; }
        if(id == 458){ inputDialog("Catatan Tambahan", draft.notes, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, s->{draft.notes=s;invalidate();}); return true; }
        if(id == 459){ saveDraft(); return true; }
        if(id == 500){ showScreen(TASKS); return true; }
        if(id == 501){ showScreen(FOCUS); return true; }
        if(id == 502 && selectedTask != null){ draft = selectedTask.copy(); editing = true; showScreen(ADD_TASK); return true; }
        if(id == 503){ completeSelectedTask(); return true; }
        if(id == 504 && selectedTask != null){ new AlertDialog.Builder(activity).setTitle("Hapus quest?").setMessage(selectedTask.title).setNegativeButton("Batal", null).setPositiveButton("Hapus", (d,w)->{tasks.remove(selectedTask);selectedTask=null;saveData();showScreen(TASKS);}).show(); return true; }
        if(id >= 600 && id <= 602){ int[] presets = {25,15,50}; timerPreset = presets[id - 600]; timerSeconds = timerPreset * 60; timerRunning = false; handler.removeCallbacks(timerTick); invalidate(); return true; }
        if(id == 603){ showTaskPicker(); return true; }
        if(id == 604){ toggleTimer(); return true; }
        if(id == 605){ resetTimer(); return true; }
        if(id == 606){ finishFocusSession(); return true; }
        if(id == 700){ showScreen(CALENDAR); return true; }
        if(id == 701){ showScreen(SHOP); return true; }
        if(id == 800){ darkMode=!darkMode; saveData(); activity.configureSystemBars(false); invalidate(); return true; }
        if(id == 810 || id == 811){ activity.requestProfileImage(); return true; }
        if(id == 812){ companionActive=!companionActive; saveData(); activity.updateCompanion(companionActive,level()); invalidate(); return true; }
        if(id == 813){ activity.showProfileEditor(profileName,loginEmail,profileUniversity,profileMajor,profileSemester,characterClass); return true; }
        if(id == 814){ showScreen(COURSES); return true; }
        if(id == 815){ accessibilityExpanded=!accessibilityExpanded; invalidate(); return true; }
        if(id == 816){ new AlertDialog.Builder(activity).setTitle("Tentang StudyMate Quest").setMessage("Aplikasi manajemen tugas bergaya RPG. Selesaikan quest, kumpulkan XP, naik level, dan kembangkan karaktermu.\n\nData disimpan secara lokal di perangkat.").setPositiveButton("Tutup",null).show(); return true; }
        if(id == 817){ new AlertDialog.Builder(activity).setTitle("Logout?").setNegativeButton("Batal",null).setPositiveButton("Logout",(d,w)->{prefs.edit().putBoolean("logged_in",false).apply();showScreen(LOGIN);}).show(); return true; }
        if(id == 830){ fontLarge=!fontLarge; saveData(); invalidate(); return true; }
        if(id == 831){ highContrast=!highContrast; saveData(); invalidate(); return true; }
        if(id == 832){ activity.openTalkBackSettings(); return true; }
        if(id == 833){ voiceEnabled=!voiceEnabled; saveData(); invalidate(); return true; }
        if(id == 880){ showScreen(PROFILE); return true; }
        if(id == 881){ inputDialog("Tambah Mata Kuliah", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, s->{if(!s.trim().isEmpty()){courses.add(s.trim());saveData();invalidate();}}); return true; }
        if(id >= 890 && id < 896){
            int index = id - 890;
            if(index < courses.size()){
                String old = courses.get(index);
                inputDialog("Ubah Mata Kuliah",old,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,s->{int idx=courses.indexOf(old);if(idx>=0){courses.set(idx,s);for(Task t:tasks)if(t.course.equals(old))t.course=s;saveData();invalidate();}});
                return true;
            }
        }
        if(id >= 940 && id < 946){
            int index = id - 940;
            if(index < courses.size()){
                String old = courses.get(index);
                new AlertDialog.Builder(activity).setTitle("Hapus mata kuliah?").setMessage(old).setNegativeButton("Batal",null).setPositiveButton("Hapus",(d,w)->{courses.remove(old);saveData();invalidate();}).show();
                return true;
            }
        }
        if(id >= 931 && id < 935){
            List<Task> upcoming = upcomingTasks();
            int index = id - 931;
            if(index < upcoming.size()){ selectedTask = upcoming.get(index); showScreen(DETAIL); return true; }
        }
        if(id == 930 || id == 949){ showScreen(PROGRESS); return true; }
        if(id == 1000){ showScreen(HOME); return true; }
        if(id == 1001){ showScreen(TASKS); return true; }
        if(id == 1002){ showScreen(FOCUS); return true; }
        if(id == 1003){ showScreen(PROGRESS); return true; }
        if(id == 1004){ showScreen(PROFILE); return true; }
        if(id == 950){ applyOrBuyTheme("classic", 0); return true; }
        if(id == 951){ applyOrBuyTheme("ocean", 120); return true; }
        if(id == 952){ applyOrBuyTheme("sunset", 180); return true; }
        return false;
    }
    private String timeText(long time){return new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(time));}
    private String shortDateTime(long time){return new SimpleDateFormat("dd MMM, HH:mm", new Locale("id", "ID")).format(new Date(time));}
    private String dateTimeLabel(long time){return new SimpleDateFormat("dd MMM yyyy • HH:mm", new Locale("id", "ID")).format(new Date(time));}
    private String timerDisplay(){return String.format(Locale.getDefault(),"%02d:%02d",timerSeconds/60,timerSeconds%60);}
    private String deadlineLabel(Task t){return t.deadline<System.currentTimeMillis()?"Terlambat":deadlineShort(t);}
    private String relativeDeadline(Task t){long diff=t.deadline-System.currentTimeMillis();long days=Math.abs(TimeUnit.MILLISECONDS.toDays(diff));if(t.status.equals("Selesai"))return "Selesai";if(diff<0)return days<=1?"Terlambat 1 hari":"Terlambat "+days+" hari";if(diff<TimeUnit.DAYS.toMillis(1))return "Deadline hari ini";return "Deadline "+Math.max(1,days)+" hari lagi";}
    private String deadlineShort(Task t){long diff=t.deadline-System.currentTimeMillis();long days=Math.abs(TimeUnit.MILLISECONDS.toDays(diff));
        if(t.status.equals("Selesai"))return "Selesai";if(diff<0)return days<=1?"Terlambat 1 hari":"Terlambat "+days+" hari";
        if(diff<TimeUnit.DAYS.toMillis(1))return "Hari ini";return Math.max(1,days)+" hari lagi";}
    private String remainingDays(Task t){long diff=t.deadline-System.currentTimeMillis();if(diff<0)return "Terlambat";long d=TimeUnit.MILLISECONDS.toDays(diff);return d<1?"< 1 hari":d+" hari";}
    private String suggestion(Task t){if(t.status.equals("Selesai"))return "Hebat! Tugas ini sudah kamu selesaikan.";if(t.deadline-System.currentTimeMillis()<TimeUnit.DAYS.toMillis(2))return "Sebaiknya dikerjakan hari ini karena deadline sudah dekat.";return "Cicil tugas ini agar selesai nyaman sebelum deadline.";}
    private int priorityColor(String p){if(p.contains("Tinggi"))return RED;if(p.contains("Sedang"))return ORANGE;return BLUE;}
    private int priorityBg(String p){if(p.contains("Tinggi"))return Color.rgb(255,237,241);if(p.contains("Sedang"))return Color.rgb(255,246,232);return Color.rgb(239,242,255);}
    private int statusColor(String s){if(s.equals("Selesai"))return GREEN;if(s.contains("Belum"))return ORANGE;return BLUE;}
    private int statusBg(String s){if(s.equals("Selesai"))return Color.rgb(233,249,241);if(s.contains("Belum"))return Color.rgb(255,247,232);return Color.rgb(239,242,255);}
    private int resolveColor(int color){
        if(!darkMode)return color;
        if(color==Color.WHITE)return Color.rgb(16, 22, 34);
        if(color==NAVY)return Color.rgb(230, 236, 246);
        if(color==MUTED)return Color.rgb(157, 168, 189);
        if(color==LINE)return Color.rgb(49, 61, 83);
        if(color==BLUE)return themeAccentPrimary();
        if(color==BLUE_2)return themeAccentSecondary();
        return color;
    }
    private int resolveTextColor(int color){
        int resolved=resolveColor(highContrast && color == MUTED ? NAVY : color);
        if(darkMode && resolved==NAVY)return Color.rgb(230, 236, 246);
        return resolved;
    }
    private int themeAccentPrimary(){
        if("ocean".equals(activeTheme))return Color.rgb(18, 120, 160);
        if("sunset".equals(activeTheme))return Color.rgb(211, 96, 36);
        return BLUE;
    }
    private int themeAccentSecondary(){
        if("ocean".equals(activeTheme))return Color.rgb(69, 178, 198);
        if("sunset".equals(activeTheme))return Color.rgb(245, 146, 72);
        return BLUE_2;
    }
    private int themeAccentSurface(){
        if("ocean".equals(activeTheme))return Color.rgb(231, 247, 250);
        if("sunset".equals(activeTheme))return Color.rgb(255, 241, 231);
        return Color.rgb(240, 243, 255);
    }
    private int previewColorForTheme(String key){
        if("ocean".equals(key))return Color.rgb(69, 178, 198);
        if("sunset".equals(key))return Color.rgb(245, 146, 72);
        return BLUE;
    }
    private boolean isThemeOwned(String key){
        return "classic".equals(key) || (dataPrefs != null && dataPrefs.getBoolean("theme_owned_" + key, false));
    }
    private String activeThemeLabel(){
        if("ocean".equals(activeTheme))return "Ocean";
        if("sunset".equals(activeTheme))return "Sunset";
        return "Classic";
    }
    private void applyOrBuyTheme(String key,int cost){
        if(!isThemeOwned(key)){
            if(coins<cost){showMessage("Koin belum cukup");return;}
            coins-=cost;
            dataPrefs.edit().putBoolean("theme_owned_" + key, true).apply();
        }
        activeTheme=key;
        saveData();
        invalidate();
        showMessage("Tema " + activeThemeLabel() + " aktif");
    }
    private void drawProfileAvatar(Canvas c,float cx,float cy,float radius){
        Bitmap bitmap=getProfileBitmap();
        if(bitmap==null){
            drawCharacter(c, cx, cy-3, 63);
            return;
        }
        c.save();
        path.reset();
        path.addCircle(cx, cy, radius-3, Path.Direction.CW);
        c.clipPath(path);
        RectF dst = new RectF(cx-radius+3, cy-radius+3, cx+radius-3, cy+radius-3);
        c.drawBitmap(bitmap, null, dst, paint);
        c.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(themeAccentPrimary());
        c.drawCircle(cx, cy, radius-3, paint);
        paint.setStyle(Paint.Style.FILL);
    }
    private Bitmap getProfileBitmap(){
        if(profileImageUri.isEmpty())return null;
        if(profileImageBitmap!=null)return profileImageBitmap;
        try(java.io.InputStream input=activity.getContentResolver().openInputStream(Uri.parse(profileImageUri))){
            profileImageBitmap= BitmapFactory.decodeStream(input);
        }catch(Exception ignored){
            profileImageBitmap=null;
        }
        return profileImageBitmap;
    }

    private static class AccessibilityElement {
        final int id;
        final String label;
        final String hint;
        final Rect bounds;

        AccessibilityElement(int id, String label, String hint, Rect bounds) {
            this.id = id;
            this.label = label;
            this.hint = hint;
            this.bounds = bounds;
        }
    }

    private class ScreenAccessibilityHelper extends ExploreByTouchHelper {
        ScreenAccessibilityHelper(View host) { super(host); }

        @Override protected void onPopulateNodeForHost(AccessibilityNodeInfoCompat node) {
            super.onPopulateNodeForHost(node);
            node.setClassName("android.view.View");
            if (screen == TASKS || screen == PROFILE) {
                node.setScrollable(true);
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                node.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
            }
        }

        @Override protected int getVirtualViewAt(float x, float y) {
            for (AccessibilityElement element : buildAccessibilityElements()) {
                if (element.bounds.contains((int) x, (int) y)) return element.id;
            }
            return ExploreByTouchHelper.INVALID_ID;
        }

        @Override protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            for (AccessibilityElement element : buildAccessibilityElements()) virtualViewIds.add(element.id);
        }

        @Override protected void onPopulateNodeForVirtualView(int virtualViewId, AccessibilityNodeInfoCompat node) {
            AccessibilityElement target = null;
            for (AccessibilityElement element : buildAccessibilityElements()) {
                if (element.id == virtualViewId) { target = element; break; }
            }
            if (target == null) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect());
                return;
            }
            node.setContentDescription(target.label);
            if (target.hint != null) node.setHintText(target.hint);
            node.setClassName("android.widget.Button");
            node.setClickable(true);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.setBoundsInParent(target.bounds);
        }

        @Override protected boolean onPerformActionForVirtualView(int virtualViewId, int action, Bundle arguments) {
            return action == AccessibilityNodeInfoCompat.ACTION_CLICK && activateAccessibilityElement(virtualViewId);
        }
    }

    static class Task {
        long id,deadline,completedAt;String title="",course="",description="",priority="Prioritas Sedang",status="Belum Dikerjakan",estimate="1 jam",notes="";int progress,xpReward=60;boolean rewardClaimed;
        Task(){}
        Task(long id,String title,String course,String description,long deadline,String priority,String status,String estimate,String notes,int progress){this.id=id;this.title=title;this.course=course;this.description=description;this.deadline=deadline;this.priority=priority;this.status=status;this.estimate=estimate;this.notes=notes;this.progress=progress;this.xpReward=rewardForPriority(priority);this.rewardClaimed="Selesai".equals(status);}
        Task copy(){Task t=new Task(id,title,course,description,deadline,priority,status,estimate,notes,progress);t.xpReward=xpReward;t.rewardClaimed=rewardClaimed;t.completedAt=completedAt;return t;}
        JSONObject toJson(){JSONObject o=new JSONObject();try{o.put("id",id);o.put("title",title);o.put("course",course);o.put("description",description);o.put("deadline",deadline);o.put("completedAt",completedAt);o.put("priority",priority);o.put("status",status);o.put("estimate",estimate);o.put("notes",notes);o.put("progress",progress);o.put("xpReward",xpReward);o.put("rewardClaimed",rewardClaimed);}catch(Exception ignored){}return o;}
        static Task fromJson(JSONObject o){Task t=new Task();t.id=o.optLong("id");t.title=o.optString("title");t.course=o.optString("course");t.description=o.optString("description");t.deadline=o.optLong("deadline");t.completedAt=o.optLong("completedAt",0);t.priority=o.optString("priority","Prioritas Sedang");t.status=o.optString("status","Belum Dikerjakan");t.estimate=o.optString("estimate","1 jam");t.notes=o.optString("notes");t.progress=o.optInt("progress");t.xpReward=o.optInt("xpReward",rewardForPriority(t.priority));t.rewardClaimed=o.has("rewardClaimed")?o.optBoolean("rewardClaimed"):"Selesai".equals(t.status);return t;}
    }

    static class FocusSession {
        final String taskTitle;
        final int minutes;
        final long finishedAt;

        FocusSession(String taskTitle, int minutes, long finishedAt) {
            this.taskTitle = taskTitle;
            this.minutes = minutes;
            this.finishedAt = finishedAt;
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("taskTitle", taskTitle);
                object.put("minutes", minutes);
                object.put("finishedAt", finishedAt);
            } catch (Exception ignored) {}
            return object;
        }

        static FocusSession fromJson(JSONObject object) {
            return new FocusSession(
                    object.optString("taskTitle", "Sesi Fokus"),
                    object.optInt("minutes", 0),
                    object.optLong("finishedAt", 0)
            );
        }
    }
    // endregion
}
