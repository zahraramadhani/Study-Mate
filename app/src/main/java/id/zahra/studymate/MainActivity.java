package id.zahra.studymate;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.speech.RecognizerIntent;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    static final String ACTION_ADD_QUEST="id.zahra.studymate.ADD_QUEST";
    private static final int VOICE_REQUEST = 41;
    private static final int NOTIFICATION_PERMISSION_REQUEST=42;
    private static final int PROFILE_IMAGE_REQUEST=43;
    private static final int BLUE = Color.rgb(64, 82, 244);
    private static final int NAVY = Color.rgb(12, 24, 84);
    private static final int MUTED = Color.rgb(100, 113, 151);
    private static final int LINE = Color.rgb(225, 229, 242);

    private StudyMateView studyMateView;
    private FrameLayout root;
    private ScrollView authView;
    private ScrollView questView;
    private ScrollView profileView;
    private ScrollView courseView;
    private CompanionView companionView;
    private boolean registrationMode;
    private Consumer<String> voiceCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars(true);
        root = new FrameLayout(this);
        View launchPlaceholder = new View(this);
        launchPlaceholder.setBackgroundColor(Color.parseColor("#4D55F5"));
        root.addView(launchPlaceholder, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){
                if(handleBackNavigation())return;
                setEnabled(false);getOnBackPressedDispatcher().onBackPressed();
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() |
                    WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);

        // Draw one extremely cheap application frame first. Android can then dismiss
        // its system splash immediately, while the actual StudyMate view is attached
        // on the following frame and starts its animation from progress zero.
        final ViewTreeObserver.OnDrawListener[] firstDraw = new ViewTreeObserver.OnDrawListener[1];
        firstDraw[0] = () -> root.post(() -> {
            if (root.getViewTreeObserver().isAlive())
                root.getViewTreeObserver().removeOnDrawListener(firstDraw[0]);
            if (studyMateView != null) return;
            studyMateView = new StudyMateView(this);
            root.addView(studyMateView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            root.removeView(launchPlaceholder);
            handleLaunchIntent(getIntent());
        });
        root.getViewTreeObserver().addOnDrawListener(firstDraw[0]);

        // Let the first splash frame reach the display before doing persistence and
        // scheduler work.  This avoids a visible pause directly after cold launch.
        root.postDelayed(() -> {
            migrateLegacyAccount();
            DeadlineNotificationService.schedule(this);
            if(getSharedPreferences("studymate",MODE_PRIVATE).getBoolean("logged_in",false))
                enableDeadlineNotifications();
        }, 2400L);
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);handleLaunchIntent(intent);
    }

    private void handleLaunchIntent(Intent intent){
        if(intent!=null&&ACTION_ADD_QUEST.equals(intent.getAction())&&studyMateView!=null)
            studyMateView.openAddQuestShortcut();
    }

    void enableDeadlineNotifications(){
        DeadlineNotificationService.schedule(this);
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION_REQUEST);
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==NOTIFICATION_PERMISSION_REQUEST&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)
            DeadlineNotificationService.schedule(this);
    }

    void configureSystemBars(boolean splash) {
        Window window = getWindow();
        boolean darkUi = !splash && studyMateView != null && studyMateView.isDarkModeEnabled();
        window.setStatusBarColor(Color.parseColor(splash ? "#4D55F5" : darkUi ? "#101622" : "#FFFFFF"));
        window.setNavigationBarColor(darkUi ? Color.parseColor("#101622") : Color.WHITE);
        int appearance = darkUi ? 0 : View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if (!splash && !darkUi) appearance |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        window.getDecorView().setSystemUiVisibility(appearance);
    }

    void requestProfileImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PROFILE_IMAGE_REQUEST);
        } catch (Exception ignored) {
            if (studyMateView != null) studyMateView.showMessage("Galeri tidak tersedia di perangkat ini");
        }
    }

    /** Native, directly editable auth form. It scrolls safely on small phones and with the keyboard open. */
    void showAuth(boolean register) {
        registrationMode = register;
        hideQuestForm();
        hideProfileEditor();
        hideCourseManager();
        updateCompanion(false, 1);
        hideAuth();
        configureSystemBars(false);

        authView = new ScrollView(this);
        authView.setFillViewport(true);
        authView.setBackgroundColor(Color.WHITE);
        authView.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(28), dp(22), dp(28), dp(42));
        authView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        logoParams.bottomMargin = dp(10);
        content.addView(logo, logoParams);

        TextView brand = label("StudyMate Quest", 24, NAVY, true);
        brand.setGravity(Gravity.CENTER);
        content.addView(brand, matchWrap(dp(2)));

        TextView title = label(register ? "Mulai Petualanganmu" : "Selamat Datang Kembali", 27, NAVY, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap(dp(8));
        titleParams.topMargin = dp(12);
        content.addView(title, titleParams);

        int savedAccounts = readAccounts().length();
        TextView subtitle = label(register
                ? "Lengkapi profil untuk membuat karakter dan memulai quest pertamamu."
                : "Masuk untuk melanjutkan quest dan perkembangan karaktermu.\n" + savedAccounts + " akun tersimpan di perangkat ini.", 13, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setMaxLines(3);
        LinearLayout.LayoutParams subtitleParams = matchWrap(dp(20));
        content.addView(subtitle, subtitleParams);

        ArrayList<Field> fields = new ArrayList<>();
        if (register) fields.add(addField(content, "Nama lengkap", R.drawable.ic_auth_person,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, false));
        fields.add(addField(content, "Email", R.drawable.ic_auth_email,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, false));
        fields.add(addField(content, "Password", R.drawable.ic_auth_lock,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, true));
        if (register) {
            fields.add(addField(content, "Konfirmasi password", R.drawable.ic_auth_lock,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, true));
            fields.add(addField(content, "Universitas", R.drawable.ic_auth_school,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, false));
            fields.add(addField(content, "Program studi", R.drawable.ic_auth_book,
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, false));
            fields.add(addField(content, "Semester", R.drawable.ic_auth_level,
                    InputType.TYPE_CLASS_NUMBER, false));
            Field classField = addSelectField(content, "Kelas Karakter", R.drawable.ic_auth_person, new String[]{"Ksatria", "Penyihir", "Pemanah"});
            ((MaterialAutoCompleteTextView)classField.input).setText("Ksatria", false);
            fields.add(classField);
        }

        MaterialButton primary = new MaterialButton(this);
        primary.setText(register ? "Buat Karakter & Mulai" : "Masuk");
        primary.setTextSize(16);
        primary.setTextColor(Color.WHITE);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        primary.setAllCaps(false);
        primary.setCornerRadius(dp(15));
        primary.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        primary.setInsetTop(0);
        primary.setInsetBottom(0);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        buttonParams.topMargin = dp(10);
        content.addView(primary, buttonParams);

        TextView switchMode = label(register
                ? "Sudah punya akun?  Masuk"
                : "Belum punya akun?  Daftar lengkap", 13, BLUE, true);
        switchMode.setGravity(Gravity.CENTER);
        switchMode.setPadding(dp(8), dp(18), dp(8), dp(12));
        content.addView(switchMode, matchWrap(0));

        primary.setOnClickListener(v -> {
            if (validateAuth(fields, register)) {
                if (register) register(fields);
                else login(fields);
            }
        });
        switchMode.setOnClickListener(v -> showAuth(!register));

        root.addView(authView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        authView.setAlpha(0f);
        authView.setTranslationY(dp(18));
        authView.animate().alpha(1f).translationY(0f).setDuration(280).start();
    }

    void hideAuth() {
        if (authView != null) {
            root.removeView(authView);
            authView = null;
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(root.getWindowToken(), 0);
        }
    }

    boolean isAuthVisible() {
        return authView != null;
    }

    /** Direct-entry, accessible quest form with a microphone action on every field. */
    void showQuestForm(StudyMateView.Task draft, boolean editing, String[] savedCourses) {
        hideQuestForm();
        hideProfileEditor();
        hideCourseManager();
        hideAuth();
        configureSystemBars(false);

        questView = new ScrollView(this);
        questView.setFillViewport(true);
        questView.setBackgroundColor(Color.WHITE);
        questView.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(12), dp(22), dp(30));
        questView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        AppCompatImageButton back = iconButton(R.drawable.ic_arrow_back, "Kembali", dp(12));
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView heading = label(editing ? "Edit Quest" : "Quest Baru", 23, NAVY, true);
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams headerParams = matchWrap(dp(18));
        content.addView(header, headerParams);
        back.setOnClickListener(v -> studyMateView.cancelQuestForm());

        QuestText title = questText("Judul quest", R.drawable.ic_quest_title,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 1);
        title.input.setText(draft.title);
        addQuestRow(content, title.layout, "Isi judul quest dengan suara",
                spoken -> title.input.setText(spoken));

        String[] courses = savedCourses.length == 0
                ? new String[]{"Belum ada mata kuliah"} : savedCourses;
        QuestSelect course = questSelect("Mata kuliah", R.drawable.ic_quest_course, courses);
        course.input.setText(draft.course, false);
        addQuestRow(content, course.layout, "Pilih mata kuliah dengan suara",
                spoken -> selectSpokenOption(course.input, courses, spoken));

        QuestText description = questText("Deskripsi", R.drawable.ic_quest_description,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES |
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE, 3);
        description.input.setText(draft.description);
        addQuestRow(content, description.layout, "Isi deskripsi dengan suara",
                spoken -> description.input.setText(spoken));

        Calendar deadline = Calendar.getInstance();
        deadline.setTimeInMillis(draft.deadline > 0 ? draft.deadline : System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID"));
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        QuestText date = questText("Tanggal deadline", R.drawable.ic_quest_calendar,
                InputType.TYPE_NULL, 1);
        date.input.setText(dateFormat.format(new Date(deadline.getTimeInMillis())));
        date.input.setFocusable(false);
        date.input.setOnClickListener(v -> new DatePickerDialog(this, (picker, year, month, day) -> {
            deadline.set(Calendar.YEAR, year); deadline.set(Calendar.MONTH, month);
            deadline.set(Calendar.DAY_OF_MONTH, day);
            date.input.setText(dateFormat.format(deadline.getTime()));
        }, deadline.get(Calendar.YEAR), deadline.get(Calendar.MONTH),
                deadline.get(Calendar.DAY_OF_MONTH)).show());
        addQuestRow(content, date.layout, "Isi tanggal deadline dengan suara", spoken -> {
            if (applySpokenDate(deadline, spoken)) date.input.setText(dateFormat.format(deadline.getTime()));
        });

        QuestText time = questText("Jam deadline", R.drawable.ic_quest_clock,
                InputType.TYPE_NULL, 1);
        time.input.setText(timeFormat.format(deadline.getTime()));
        time.input.setFocusable(false);
        time.input.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> {
            deadline.set(Calendar.HOUR_OF_DAY, hour); deadline.set(Calendar.MINUTE, minute);
            time.input.setText(timeFormat.format(deadline.getTime()));
        }, deadline.get(Calendar.HOUR_OF_DAY), deadline.get(Calendar.MINUTE), true).show());
        addQuestRow(content, time.layout, "Isi jam deadline dengan suara", spoken -> {
            if (applySpokenTime(deadline, spoken)) time.input.setText(timeFormat.format(deadline.getTime()));
        });

        String[] priorities = {"Prioritas Tinggi", "Prioritas Sedang", "Prioritas Rendah"};
        QuestSelect priority = questSelect("Prioritas", R.drawable.ic_quest_flag, priorities);
        priority.input.setText(draft.priority, false);
        addQuestRow(content, priority.layout, "Pilih prioritas dengan suara",
                spoken -> selectSpokenOption(priority.input, priorities, spoken));

        String[] statuses = {"Belum Dikerjakan", "Sedang Dikerjakan", "Selesai"};
        QuestSelect status = questSelect("Status", R.drawable.ic_quest_status, statuses);
        status.input.setText(draft.status, false);
        addQuestRow(content, status.layout, "Pilih status dengan suara",
                spoken -> selectSpokenOption(status.input, statuses, spoken));

        QuestText notes = questText("Catatan tambahan (opsional)", R.drawable.ic_quest_notes,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES |
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE, 2);
        notes.input.setText(draft.notes);
        addQuestRow(content, notes.layout, "Isi catatan dengan suara",
                spoken -> notes.input.setText(spoken));

        MaterialButton submit = new MaterialButton(this);
        submit.setText(editing ? "Simpan Perubahan" : "Terima Quest");
        submit.setTextSize(16); submit.setTextColor(Color.WHITE);
        submit.setTypeface(Typeface.DEFAULT_BOLD); submit.setAllCaps(false);
        submit.setCornerRadius(dp(15)); submit.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        submit.setInsetTop(0); submit.setInsetBottom(0);
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        submitParams.topMargin = dp(6); content.addView(submit, submitParams);
        submit.setOnClickListener(v -> {
            title.layout.setError(null); course.layout.setError(null);
            if (title.value().isEmpty()) {
                title.layout.setError("Judul quest wajib diisi"); title.input.requestFocus(); return;
            }
            if (course.value().isEmpty() || course.value().equals("Belum ada mata kuliah")) {
                course.layout.setError("Tambahkan dan pilih mata kuliah terlebih dahulu"); return;
            }
            draft.title=title.value(); draft.course=course.value();
            draft.description=description.value(); draft.deadline=deadline.getTimeInMillis();
            draft.priority=priority.value();
            draft.status=status.value(); draft.notes=notes.value();
            studyMateView.submitQuestForm(draft);
        });

        root.addView(questView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        questView.setAlpha(0f); questView.setTranslationY(dp(12));
        questView.animate().alpha(1f).translationY(0).setDuration(220).start();
        bringCompanionToFront();
    }

    void hideQuestForm() {
        if (questView != null) {
            root.removeView(questView); questView=null;
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(root.getWindowToken(),0);
        }
    }

    /** Full-page profile editor. All account identity fields stay editable. */
    void showProfileEditor(String name,String email,String university,String major,String semester, String characterClass){
        hideProfileEditor();hideCourseManager();hideQuestForm();
        profileView=new ScrollView(this);profileView.setFillViewport(true);
        profileView.setBackgroundColor(Color.WHITE);profileView.setClipToPadding(false);
        LinearLayout content=pageContent(profileView);
        addPageHeader(content,"Edit Profil",v->studyMateView.closeProfileEditor());
 
        TextView intro=label("Perbarui seluruh data petualangmu.",13,MUTED,false);
        LinearLayout.LayoutParams introParams=matchWrap(dp(16));content.addView(intro,introParams);
        QuestText fullName=questText("Nama lengkap",R.drawable.ic_auth_person,
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,1);
        fullName.input.setText(name);addQuestRow(content,fullName.layout,"Isi nama lengkap dengan suara",fullName.input::setText);
        QuestText emailField=questText("Email",R.drawable.ic_auth_email,
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,1);
        emailField.input.setText(email);addQuestRow(content,emailField.layout,"Isi email dengan suara",emailField.input::setText);
        QuestText universityField=questText("Universitas",R.drawable.ic_auth_school,
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,1);
        universityField.input.setText(university);addQuestRow(content,universityField.layout,"Isi universitas dengan suara",universityField.input::setText);
        QuestText majorField=questText("Program studi",R.drawable.ic_auth_book,
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,1);
        majorField.input.setText(major);addQuestRow(content,majorField.layout,"Isi program studi dengan suara",majorField.input::setText);
        QuestText semesterField=questText("Semester",R.drawable.ic_auth_level,
                InputType.TYPE_CLASS_NUMBER,1);
        semesterField.input.setText(semester);addQuestRow(content,semesterField.layout,"Isi semester dengan suara",
                spoken->semesterField.input.setText(spoken.replaceAll("[^0-9]","")));
 
        String[] classes = {"Ksatria", "Penyihir", "Pemanah"};
        QuestSelect classField = questSelect("Kelas Karakter", R.drawable.ic_auth_person, classes);
        classField.input.setText(characterClass, false);
        addQuestRow(content, classField.layout, "Pilih kelas karakter dengan suara",
                spoken -> selectSpokenOption(classField.input, classes, spoken));

        MaterialButton save=primaryButton("Simpan Profil");
        LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,dp(56));saveParams.topMargin=dp(8);
        content.addView(save,saveParams);
        save.setOnClickListener(v->{
            QuestText[] required={fullName,emailField,universityField,majorField,semesterField};
            for(QuestText field:required)field.layout.setError(null);
            classField.layout.setError(null);
            boolean valid=true;for(QuestText field:required)if(field.value().isEmpty()){
                field.layout.setError("Wajib diisi");valid=false;}
            if(classField.value().isEmpty()){
                classField.layout.setError("Wajib diisi");valid=false;}
            if(!emailField.value().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")){
                emailField.layout.setError("Format email belum valid");valid=false;}
            int semesterNumber=0;try{semesterNumber=Integer.parseInt(semesterField.value());}
            catch(Exception ignored){}
            if(semesterNumber<1||semesterNumber>14){semesterField.layout.setError("Semester harus 1–14");valid=false;}
            if(!valid)return;
            String selectedClass = classField.value();
            if(updateStoredProfile(email,emailField.value(),fullName.value(),universityField.value(),
                    majorField.value(),semesterField.value(),selectedClass)){
                hideProfileEditor();
                studyMateView.applyProfileUpdate(fullName.value(),emailField.value(),universityField.value(),
                        majorField.value(),semesterField.value(),selectedClass);
            }
        });
        root.addView(profileView,new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        profileView.setAlpha(0f);profileView.setTranslationY(dp(12));
        profileView.animate().alpha(1f).translationY(0).setDuration(220).start();
        bringCompanionToFront();
    }

    void hideProfileEditor(){if(profileView!=null){root.removeView(profileView);profileView=null;hideKeyboard();}}

    /** Inline course manager with voice entry, edit and delete actions. */
    void showCourseManager(String[] courses){
        hideCourseManager();hideProfileEditor();hideQuestForm();
        courseView=new ScrollView(this);courseView.setFillViewport(true);
        courseView.setBackgroundColor(Color.WHITE);courseView.setClipToPadding(false);
        LinearLayout content=pageContent(courseView);
        addPageHeader(content,"Kelola Mata Kuliah",v->studyMateView.closeCourseManager());
        TextView intro=label("Tambah mata kuliah agar dapat dipilih saat membuat quest.",13,MUTED,false);
        LinearLayout.LayoutParams introParams=matchWrap(dp(16));content.addView(intro,introParams);

        QuestText courseName=questText("Nama mata kuliah",R.drawable.ic_quest_course,
                InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,1);
        addQuestRow(content,courseName.layout,"Isi nama mata kuliah dengan suara",courseName.input::setText);
        final String[] editingCourse={null};
        MaterialButton add=primaryButton("Tambah Mata Kuliah");
        LinearLayout.LayoutParams addParams=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,dp(52));addParams.bottomMargin=dp(18);
        content.addView(add,addParams);
        add.setOnClickListener(v->{
            String value=courseName.value();courseName.layout.setError(null);
            if(value.isEmpty()){courseName.layout.setError("Nama mata kuliah wajib diisi");return;}
            if(editingCourse[0]==null)studyMateView.addCourse(value);
            else studyMateView.renameCourse(editingCourse[0],value);
        });

        TextView listTitle=label("Mata Kuliah Semester Ini",18,NAVY,true);
        content.addView(listTitle,matchWrap(dp(10)));
        if(courses.length==0){
            TextView empty=label("Belum ada mata kuliah tersimpan.",13,MUTED,false);
            empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(34),0,dp(34));content.addView(empty,matchWrap(0));
        }
        for(String course:courses){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(8),dp(8),dp(8));row.setBackground(roundedBackground(Color.WHITE,14,LINE));
            ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_quest_course);
            icon.setColorFilter(BLUE);icon.setPadding(dp(10),dp(10),dp(10),dp(10));
            icon.setBackground(roundedBackground(Color.rgb(242,244,255),11,Color.TRANSPARENT));
            row.addView(icon,new LinearLayout.LayoutParams(dp(46),dp(46)));
            LinearLayout textGroup=new LinearLayout(this);textGroup.setOrientation(LinearLayout.VERTICAL);
            TextView courseTitle=label(course,14,NAVY,true);TextView count=label("Ketuk pensil untuk mengubah",10,MUTED,false);
            textGroup.addView(courseTitle);textGroup.addView(count);
            LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,1);textParams.leftMargin=dp(10);row.addView(textGroup,textParams);
            AppCompatImageButton edit=iconButton(R.drawable.ic_edit,"Ubah "+course,dp(12));
            AppCompatImageButton delete=iconButton(R.drawable.ic_delete,"Hapus "+course,dp(12));
            row.addView(edit,new LinearLayout.LayoutParams(dp(42),dp(42)));
            LinearLayout.LayoutParams deleteParams=new LinearLayout.LayoutParams(dp(42),dp(42));deleteParams.leftMargin=dp(6);
            row.addView(delete,deleteParams);
            edit.setOnClickListener(v->{editingCourse[0]=course;courseName.input.setText(course);
                courseName.input.setSelection(course.length());add.setText("Simpan Perubahan");courseName.input.requestFocus();});
            delete.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Hapus mata kuliah?")
                    .setMessage(course).setNegativeButton("Batal",null)
                    .setPositiveButton("Hapus",(dialog,which)->studyMateView.deleteCourse(course)).show());
            LinearLayout.LayoutParams rowParams=matchWrap(dp(10));content.addView(row,rowParams);
        }
        root.addView(courseView,new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        courseView.setAlpha(0f);courseView.setTranslationY(dp(12));
        courseView.animate().alpha(1f).translationY(0).setDuration(220).start();
        bringCompanionToFront();
    }

    void hideCourseManager(){if(courseView!=null){root.removeView(courseView);courseView=null;hideKeyboard();}}

    private LinearLayout pageContent(ScrollView page){
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22),dp(12),dp(22),dp(34));page.addView(content,new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,ScrollView.LayoutParams.WRAP_CONTENT));return content;
    }

    private void addPageHeader(LinearLayout content,String title,View.OnClickListener backAction){
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        AppCompatImageButton back=iconButton(R.drawable.ic_arrow_back,"Kembali",dp(12));back.setOnClickListener(backAction);
        header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView heading=label(title,23,NAVY,true);heading.setGravity(Gravity.CENTER);
        header.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));
        header.addView(new View(this),new LinearLayout.LayoutParams(dp(48),dp(48)));
        content.addView(header,matchWrap(dp(18)));
    }

    private MaterialButton primaryButton(String text){
        MaterialButton button=new MaterialButton(this);button.setText(text);button.setTextSize(16);
        button.setTextColor(Color.WHITE);button.setTypeface(Typeface.DEFAULT_BOLD);button.setAllCaps(false);
        button.setCornerRadius(dp(15));button.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        button.setInsetTop(0);button.setInsetBottom(0);return button;
    }

    private GradientDrawable roundedBackground(int color,float radius,int stroke){
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));
        if(stroke!=Color.TRANSPARENT)drawable.setStroke(dp(1),stroke);return drawable;
    }

    private void hideKeyboard(){((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(root.getWindowToken(),0);}

    private QuestText questText(String hint, int icon, int inputType, int lines) {
        TextInputLayout box = questBox(hint, icon);
        TextInputEditText input = new TextInputEditText(box.getContext());
        input.setTextSize(15); input.setTextColor(NAVY); input.setInputType(inputType);
        input.setGravity(lines>1 ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL);
        input.setSingleLine(lines==1); input.setMinLines(lines); input.setMaxLines(Math.max(lines,4));
        input.setPadding(0, lines>1?dp(16):dp(4), dp(8), lines>1?dp(16):dp(4));
        int fieldHeight = lines > 1 ? dp(lines == 2 ? 104 : 120) : dp(58);
        box.setMinimumHeight(fieldHeight);
        box.addView(input,new TextInputLayout.LayoutParams(
                TextInputLayout.LayoutParams.MATCH_PARENT, fieldHeight));
        return new QuestText(box,input);
    }

    private QuestSelect questSelect(String hint, int icon, String[] options) {
        TextInputLayout box=questBox(hint,icon);
        box.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        MaterialAutoCompleteTextView input=new MaterialAutoCompleteTextView(box.getContext());
        input.setTextSize(15);input.setTextColor(NAVY);input.setInputType(InputType.TYPE_NULL);
        input.setPadding(0,dp(4),dp(8),dp(4));
        input.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,options));
        box.addView(input,new TextInputLayout.LayoutParams(
                TextInputLayout.LayoutParams.MATCH_PARENT,dp(58)));
        return new QuestSelect(box,input);
    }

    private TextInputLayout questBox(String hint,int icon){
        TextInputLayout box=new TextInputLayout(this);box.setHint(hint);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setBoxBackgroundColor(Color.WHITE);box.setBoxStrokeColor(BLUE);
        box.setBoxStrokeWidth(dp(1));box.setBoxStrokeWidthFocused(dp(2));
        box.setBoxCornerRadii(dp(14),dp(14),dp(14),dp(14));
        box.setHintTextColor(ColorStateList.valueOf(MUTED));box.setStartIconDrawable(icon);
        box.setStartIconTintList(ColorStateList.valueOf(BLUE));return box;
    }

    private void addQuestRow(LinearLayout parent,TextInputLayout field,String micDescription,Consumer<String> voice){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);
        row.addView(field,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        AppCompatImageButton mic=iconButton(R.drawable.ic_mic,micDescription,dp(14));
        if(studyMateView!=null&&!studyMateView.isVoiceInputEnabled())mic.setAlpha(.45f);
        LinearLayout.LayoutParams micParams=new LinearLayout.LayoutParams(dp(52),dp(58));
        micParams.leftMargin=dp(8);row.addView(mic,micParams);
        mic.setOnClickListener(v->{
            if(studyMateView!=null&&!studyMateView.isVoiceInputEnabled()){
                Toast.makeText(this,"Aktifkan Voice Input di Mode Aksesibilitas",Toast.LENGTH_SHORT).show();return;}
            requestVoiceInput(micDescription,voice);
        });
        LinearLayout.LayoutParams rowParams=matchWrap(dp(10));parent.addView(row,rowParams);
    }

    private AppCompatImageButton iconButton(int icon,String description,int padding){
        AppCompatImageButton button=new AppCompatImageButton(this);
        button.setContentDescription(description);button.setImageResource(icon);
        button.setColorFilter(BLUE);button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(padding,padding,padding,padding);button.setMinimumWidth(0);button.setMinimumHeight(0);
        GradientDrawable background=new GradientDrawable();background.setColor(Color.rgb(242,244,255));
        background.setCornerRadius(dp(14));button.setBackground(background);return button;
    }

    private void selectSpokenOption(MaterialAutoCompleteTextView input,String[] options,String spoken){
        String heard=spoken.toLowerCase(Locale.ROOT).trim();
        for(String option:options){String candidate=option.toLowerCase(Locale.ROOT);
            if(candidate.contains(heard)||heard.contains(candidate)||
                    (candidate.contains("tinggi")&&heard.contains("tinggi"))||
                    (candidate.contains("sedang")&&heard.contains("sedang"))||
                    (candidate.contains("rendah")&&heard.contains("rendah"))){input.setText(option,false);return;}}
        Toast.makeText(this,"Pilihan tidak dikenali: "+spoken,Toast.LENGTH_SHORT).show();
    }

    private boolean applySpokenDate(Calendar target,String spoken){
        String value=spoken.toLowerCase(Locale.ROOT);Calendar parsed=Calendar.getInstance();
        if(value.contains("lusa"))parsed.add(Calendar.DAY_OF_YEAR,2);
        else if(value.contains("besok"))parsed.add(Calendar.DAY_OF_YEAR,1);
        else if(value.contains("hari ini")){}
        else{Matcher matcher=Pattern.compile("(\\d{1,2})[\\-/ ](\\d{1,2})[\\-/ ](\\d{4})").matcher(value);
            if(!matcher.find()){Toast.makeText(this,"Ucapkan misalnya: besok, lusa, atau 25/06/2026",Toast.LENGTH_LONG).show();return false;}
            parsed.set(Calendar.DAY_OF_MONTH,Integer.parseInt(matcher.group(1)));
            parsed.set(Calendar.MONTH,Integer.parseInt(matcher.group(2))-1);
            parsed.set(Calendar.YEAR,Integer.parseInt(matcher.group(3)));}
        target.set(Calendar.YEAR,parsed.get(Calendar.YEAR));target.set(Calendar.MONTH,parsed.get(Calendar.MONTH));
        target.set(Calendar.DAY_OF_MONTH,parsed.get(Calendar.DAY_OF_MONTH));return true;
    }

    private boolean applySpokenTime(Calendar target,String spoken){
        Matcher matcher=Pattern.compile("(\\d{1,2})(?:[:. ](\\d{1,2}))?").matcher(spoken);
        if(!matcher.find()){Toast.makeText(this,"Ucapkan misalnya: jam 14 30",Toast.LENGTH_LONG).show();return false;}
        int hour=Integer.parseInt(matcher.group(1));int minute=matcher.group(2)==null?0:Integer.parseInt(matcher.group(2));
        if(hour>23||minute>59){Toast.makeText(this,"Waktu belum valid",Toast.LENGTH_SHORT).show();return false;}
        target.set(Calendar.HOUR_OF_DAY,hour);target.set(Calendar.MINUTE,minute);return true;
    }

    private Field addField(LinearLayout parent, String hint, int icon, int inputType, boolean password) {
        TextInputLayout box = new TextInputLayout(this);
        box.setHint(hint);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setBoxBackgroundColor(Color.WHITE);
        box.setBoxStrokeColor(BLUE);
        box.setBoxStrokeWidth(dp(1));
        box.setBoxStrokeWidthFocused(dp(2));
        box.setBoxCornerRadii(dp(14), dp(14), dp(14), dp(14));
        box.setHintTextColor(ColorStateList.valueOf(MUTED));
        box.setStartIconDrawable(icon);
        box.setStartIconTintList(ColorStateList.valueOf(BLUE));
        if (password) box.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        TextInputEditText input = new TextInputEditText(box.getContext());
        input.setTextSize(15);
        input.setTextColor(NAVY);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setPadding(0, dp(4), dp(8), dp(4));
        box.addView(input, new TextInputLayout.LayoutParams(
                TextInputLayout.LayoutParams.MATCH_PARENT, dp(56)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        parent.addView(box, params);
        return new Field(box, input, hint);
    }

    private Field addSelectField(LinearLayout parent, String hint, int icon, String[] options) {
        TextInputLayout box = new TextInputLayout(this);
        box.setHint(hint);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setBoxBackgroundColor(Color.WHITE);
        box.setBoxStrokeColor(BLUE);
        box.setBoxStrokeWidth(dp(1));
        box.setBoxStrokeWidthFocused(dp(2));
        box.setBoxCornerRadii(dp(14), dp(14), dp(14), dp(14));
        box.setHintTextColor(ColorStateList.valueOf(MUTED));
        box.setStartIconDrawable(icon);
        box.setStartIconTintList(ColorStateList.valueOf(BLUE));
        box.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);

        MaterialAutoCompleteTextView input = new MaterialAutoCompleteTextView(box.getContext());
        input.setTextSize(15);
        input.setTextColor(NAVY);
        input.setInputType(InputType.TYPE_NULL);
        input.setPadding(0, dp(4), dp(8), dp(4));
        input.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options));
        box.addView(input, new TextInputLayout.LayoutParams(
                TextInputLayout.LayoutParams.MATCH_PARENT, dp(56)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        parent.addView(box, params);
        return new Field(box, input, hint);
    }


    private boolean validateAuth(ArrayList<Field> fields, boolean register) {
        boolean valid = true;
        for (Field field : fields) {
            field.layout.setError(null);
            if (field.value().isEmpty()) {
                field.layout.setError(field.name + " wajib diisi");
                valid = false;
            }
        }
        int emailIndex = register ? 1 : 0;
        int passwordIndex = register ? 2 : 1;
        if (!fields.get(emailIndex).value().isEmpty() &&
                !android.util.Patterns.EMAIL_ADDRESS.matcher(fields.get(emailIndex).value()).matches()) {
            fields.get(emailIndex).layout.setError("Format email belum valid");
            valid = false;
        }
        if (!fields.get(passwordIndex).value().isEmpty() && fields.get(passwordIndex).value().length() < 6) {
            fields.get(passwordIndex).layout.setError("Password minimal 6 karakter");
            valid = false;
        }
        if (register && !fields.get(2).value().equals(fields.get(3).value())) {
            fields.get(3).layout.setError("Konfirmasi password tidak sama");
            valid = false;
        }
        if (register && !fields.get(6).value().isEmpty()) {
            try {
                int semester = Integer.parseInt(fields.get(6).value());
                if (semester < 1 || semester > 14) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                fields.get(6).layout.setError("Semester harus antara 1–14");
                valid = false;
            }
        }
        if (!valid) {
            for (Field field : fields) if (field.layout.getError() != null) {
                field.input.requestFocus();
                break;
            }
        }
        return valid;
    }

    private void register(ArrayList<Field> fields) {
        SharedPreferences prefs = getSharedPreferences("studymate", MODE_PRIVATE);
        String email = fields.get(1).value().toLowerCase(Locale.ROOT);
        JSONArray accounts = readAccounts();
        for (int i = 0; i < accounts.length(); i++) {
            if (email.equalsIgnoreCase(accounts.optJSONObject(i).optString("email"))) {
                fields.get(1).layout.setError("Email ini sudah terdaftar. Silakan masuk.");
                fields.get(1).input.requestFocus();
                return;
            }
        }
        JSONObject account = new JSONObject();
        try {
            account.put("name", fields.get(0).value());
            account.put("email", email);
            account.put("password_hash", hash(fields.get(2).value()));
            account.put("university", fields.get(4).value());
            account.put("major", fields.get(5).value());
            account.put("semester", fields.get(6).value());
            account.put("character_class", fields.get(7).value());
            accounts.put(account);
        } catch (Exception ignored) {}
        getSharedPreferences(accountStoreName(email), MODE_PRIVATE).edit().clear()
                .putBoolean("initialized", true)
                .putString("name", fields.get(0).value())
                .putString("email", email)
                .putString("university", fields.get(4).value())
                .putString("major", fields.get(5).value())
                .putString("semester", fields.get(6).value())
                .putString("character_class", fields.get(7).value())
                .putString("tasks", "[]")
                .putString("courses", "[]")
                .putInt("xp", 0).putInt("coins", 0).putInt("streak", 0)
                .apply();
        prefs.edit()
                .putBoolean("account_created", true)
                .putBoolean("logged_in", true)
                .putString("accounts", accounts.toString())
                .putString("current_account", email)
                .putString("name", fields.get(0).value())
                .putString("email", email)
                .putString("university", fields.get(4).value())
                .putString("major", fields.get(5).value())
                .putString("semester", fields.get(6).value())
                .putString("character_class", fields.get(7).value())
                .apply();
        hideAuth();
        studyMateView.completeAuth(fields.get(0).value(), email);
    }

    private void login(ArrayList<Field> fields) {
        SharedPreferences prefs = getSharedPreferences("studymate", MODE_PRIVATE);
        String email = fields.get(0).value();
        String password = fields.get(1).value();
        JSONArray accounts = readAccounts();
        if (accounts.length() == 0) {
            fields.get(0).layout.setError("Akun belum terdaftar");
            return;
        }
        JSONObject matched = null;
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject candidate = accounts.optJSONObject(i);
            if (candidate != null && email.equalsIgnoreCase(candidate.optString("email")) &&
                    hash(password).equals(candidate.optString("password_hash"))) {
                matched = candidate;
                break;
            }
        }
        if (matched == null) {
            fields.get(1).layout.setError("Email atau password tidak sesuai");
            return;
        }
        String accountEmail = matched.optString("email");
        String accountName = matched.optString("name", "Petualang");
        prefs.edit().putBoolean("logged_in", true)
                .putBoolean("account_created", true)
                .putString("current_account", accountEmail)
                .putString("email", accountEmail)
                .putString("name", accountName)
                .apply();
        hideAuth();
        studyMateView.completeAuth(accountName, accountEmail);
    }

    private JSONArray readAccounts() {
        try {
            return new JSONArray(getSharedPreferences("studymate", MODE_PRIVATE)
                    .getString("accounts", "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void migrateLegacyAccount() {
        SharedPreferences prefs = getSharedPreferences("studymate", MODE_PRIVATE);
        if (readAccounts().length() > 0 || !prefs.getBoolean("account_created", false)) return;
        String email = prefs.getString("email", "").toLowerCase(Locale.ROOT);
        String passwordHash = prefs.getString("password_hash", "");
        if (email.isEmpty() || passwordHash.isEmpty()) return;
        JSONArray accounts = new JSONArray();
        JSONObject account = new JSONObject();
        try {
            account.put("name", prefs.getString("name", "Petualang"));
            account.put("email", email);
            account.put("password_hash", passwordHash);
            account.put("university", prefs.getString("university", ""));
            account.put("major", prefs.getString("major", ""));
            account.put("semester", prefs.getString("semester", ""));
            accounts.put(account);
            prefs.edit().putString("accounts", accounts.toString())
                    .putString("current_account", email).apply();
        } catch (Exception ignored) {}
    }

    static String accountStoreName(String email) {
        String key = hash(email.toLowerCase(Locale.ROOT));
        return "studymate_user_" + key.substring(0, Math.min(16, key.length()));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format(Locale.US, "%02x", b));
            return result.toString();
        } catch (Exception ignored) {
            return value;
        }
    }

    private boolean updateStoredProfile(String oldEmail,String newEmail,String name,String university,
                                        String major,String semester,String characterClass){
        newEmail=newEmail.toLowerCase(Locale.ROOT);oldEmail=oldEmail.toLowerCase(Locale.ROOT);
        JSONArray accounts=readAccounts();JSONObject target=null;
        for(int i=0;i<accounts.length();i++){
            JSONObject candidate=accounts.optJSONObject(i);if(candidate==null)continue;
            String candidateEmail=candidate.optString("email");
            if(candidateEmail.equalsIgnoreCase(newEmail)&&!candidateEmail.equalsIgnoreCase(oldEmail)){
                Toast.makeText(this,"Email tersebut sudah digunakan akun lain",Toast.LENGTH_LONG).show();return false;
            }
            if(candidateEmail.equalsIgnoreCase(oldEmail))target=candidate;
        }
        if(target==null)return false;
        try{target.put("name",name);target.put("email",newEmail);target.put("university",university);
            target.put("major",major);target.put("semester",semester);target.put("character_class",characterClass);}catch(Exception ignored){}
 
        SharedPreferences oldStore=getSharedPreferences(accountStoreName(oldEmail),MODE_PRIVATE);
        SharedPreferences newStore=getSharedPreferences(accountStoreName(newEmail),MODE_PRIVATE);
        if(!oldEmail.equalsIgnoreCase(newEmail)){
            SharedPreferences.Editor copy=newStore.edit().clear();
            for(Map.Entry<String,?> entry:oldStore.getAll().entrySet()){
                Object value=entry.getValue();String key=entry.getKey();
                if(value instanceof String)copy.putString(key,(String)value);
                else if(value instanceof Integer)copy.putInt(key,(Integer)value);
                else if(value instanceof Long)copy.putLong(key,(Long)value);
                else if(value instanceof Float)copy.putFloat(key,(Float)value);
                else if(value instanceof Boolean)copy.putBoolean(key,(Boolean)value);
            }
            copy.apply();oldStore.edit().clear().apply();
        }
        newStore.edit().putBoolean("initialized",true).putString("name",name).putString("email",newEmail)
                .putString("university",university).putString("major",major).putString("semester",semester)
                .putString("character_class",characterClass).apply();
        getSharedPreferences("studymate",MODE_PRIVATE).edit().putString("accounts",accounts.toString())
                .putString("current_account",newEmail).putString("email",newEmail).putString("name",name)
                .putString("university",university).putString("major",major).putString("semester",semester)
                .putString("character_class",characterClass).apply();
        return true;
    }

    void updateCompanion(boolean active,int level){
        if(!active){if(companionView!=null)companionView.setVisibility(View.GONE);return;}
        if(companionView==null){
            companionView=new CompanionView(this);companionView.setContentDescription(
                    "Karakter virtual. Ketuk dua kali untuk animasi atau geser untuk memindahkan");
            FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(90),dp(105));
            root.addView(companionView,params);
            companionView.setOnTouchListener(new View.OnTouchListener(){
                float downX,downY,startX,startY;boolean moved;long lastTapAt;
                Runnable singleTapHint;
                @Override public boolean onTouch(View view,MotionEvent event){
                    if(event.getAction()==MotionEvent.ACTION_DOWN){downX=event.getRawX();downY=event.getRawY();
                        ((CompanionView)view).stopReaction();
                        startX=view.getX();startY=view.getY();moved=false;view.bringToFront();return true;}
                    if(event.getAction()==MotionEvent.ACTION_MOVE){
                        float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                        moved|=Math.abs(dx)>dp(4)||Math.abs(dy)>dp(4);
                        float maxX=Math.max(0,root.getWidth()-view.getWidth());
                        float maxY=Math.max(0,root.getHeight()-view.getHeight());
                        view.setX(Math.max(0,Math.min(maxX,startX+dx)));
                        view.setY(Math.max(0,Math.min(maxY,startY+dy)));return true;
                    }
                    if(event.getAction()==MotionEvent.ACTION_UP){
                        persistCompanionPosition(view.getX(),view.getY());
                        if(!moved){
                            view.performClick();long now=android.os.SystemClock.uptimeMillis();
                            if(now-lastTapAt<=450L){
                                if(singleTapHint!=null)view.removeCallbacks(singleTapHint);
                                lastTapAt=0L;((CompanionView)view).playNextReaction();
                            }else{
                                lastTapAt=now;
                                singleTapHint=()->Toast.makeText(MainActivity.this,
                                        "Ketuk dua kali untuk animasi • geser untuk memindahkan",
                                        Toast.LENGTH_SHORT).show();
                                view.postDelayed(singleTapHint,460L);
                            }
                        }
                        return true;
                    }
                    if(event.getAction()==MotionEvent.ACTION_CANCEL){((CompanionView)view).stopReaction();return true;}
                    return false;
                }
            });
        }
        companionView.stopReaction();
        companionView.setLevel(level);
        if (studyMateView != null) {
            companionView.setCharacterClass(studyMateView.getCharacterClass());
        }
        companionView.setVisibility(View.VISIBLE);bringCompanionToFront();
        root.post(()->{
            SharedPreferences user=currentAccountStore();
            float xRatio=user.contains("companion_x")?user.getFloat("companion_x",.88f):.88f;
            float yRatio=user.contains("companion_y")?user.getFloat("companion_y",.62f):.62f;
            companionView.setX(Math.max(0,(root.getWidth()-companionView.getWidth())*xRatio));
            companionView.setY(Math.max(0,(root.getHeight()-companionView.getHeight())*yRatio));
            companionView.bringToFront();
        });
    }

    private void persistCompanionPosition(float x,float y){
        float w = companionView != null ? companionView.getWidth() : dp(90);
        float h = companionView != null ? companionView.getHeight() : dp(105);
        float maxX=Math.max(1,root.getWidth()-w),maxY=Math.max(1,root.getHeight()-h);
        currentAccountStore().edit().putFloat("companion_x",x/maxX).putFloat("companion_y",y/maxY).apply();
    }

    private SharedPreferences currentAccountStore(){
        String email=getSharedPreferences("studymate",MODE_PRIVATE).getString("current_account","");
        return getSharedPreferences(accountStoreName(email),MODE_PRIVATE);
    }

    private void bringCompanionToFront(){if(companionView!=null&&companionView.getVisibility()==View.VISIBLE)companionView.bringToFront();}

    private static class CompanionView extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();private int level=1;
        private String characterClass = "Ksatria";
        private AnimatorSet reaction;private int reactionIndex=-1;private float restTranslationX,restTranslationY;
        private float wavePose,sickPose,sparkPose;private int expressionMode;
        private static final String[] REACTIONS={"Pendamping melambai","Pendamping pura-pura sakit",
                "Pendamping kaget bahagia","Pendamping melompat ceria","Pendamping berputar kecil"};

        private float idleY = 0f;
        private ObjectAnimator idleAnimator;
        private boolean blinking = false;
        private final Runnable blinkRunnable = new Runnable() {
            @Override public void run() {
                blinking = true;
                invalidate();
                postDelayed(() -> {
                    blinking = false;
                    invalidate();
                }, 150);
                postDelayed(this, 3000 + (long)(Math.random() * 2000));
            }
        };

        public float getIdleY() { return idleY; }
        public void setIdleY(float value) { this.idleY = value; invalidate(); }

        CompanionView(android.content.Context context){
            super(context);
            setLayerType(View.LAYER_TYPE_HARDWARE,null);
            setupIdleAnimator();
        }

        private void setupIdleAnimator() {
            idleAnimator = ObjectAnimator.ofFloat(this, "idleY", 0f, -dpValue(5), 0f);
            idleAnimator.setDuration(2400);
            idleAnimator.setRepeatCount(ValueAnimator.INFINITE);
            idleAnimator.setRepeatMode(ValueAnimator.RESTART);
            idleAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (idleAnimator != null && !idleAnimator.isStarted()) {
                idleAnimator.start();
            }
            postDelayed(blinkRunnable, 3000);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (idleAnimator != null) {
                idleAnimator.cancel();
            }
            removeCallbacks(blinkRunnable);
        }

        void setLevel(int value){level=value;invalidate();}
        void setCharacterClass(String value){characterClass=value;invalidate();}
        void playNextReaction(){
            stopReaction();reactionIndex=(reactionIndex+1)%REACTIONS.length;
            restTranslationX=getTranslationX();restTranslationY=getTranslationY();
            ArrayList<Animator> animators=new ArrayList<>();
            switch(reactionIndex){
                case 0:
                    expressionMode=1;
                    animators.add(ObjectAnimator.ofFloat(this,View.ROTATION,0f,-3f,3f,0f));
                    animators.add(ObjectAnimator.ofFloat(this,View.TRANSLATION_Y,restTranslationY,restTranslationY-dpValue(5),restTranslationY));
                    animators.add(poseAnimator(0,1f,0f,1f,0f,1f,0f));break;
                case 1:
                    expressionMode=2;
                    animators.add(ObjectAnimator.ofFloat(this,View.ROTATION,0f,-10f,10f,-7f,7f,0f));
                    animators.add(ObjectAnimator.ofFloat(this,View.TRANSLATION_Y,restTranslationY,restTranslationY+dpValue(3),restTranslationY-dpValue(3),restTranslationY));
                    animators.add(sickAnimator(0f,1f,.3f,1f,0f));break;
                case 2:
                    expressionMode=3;
                    animators.add(ObjectAnimator.ofFloat(this,View.SCALE_X,1f,1.18f,.94f,1.08f,1f));
                    animators.add(ObjectAnimator.ofFloat(this,View.SCALE_Y,1f,.90f,1.16f,.98f,1f));
                    animators.add(sparkAnimator(0f,1f,.2f,1f,0f));break;
                case 3:
                    expressionMode=1;
                    animators.add(ObjectAnimator.ofFloat(this,View.TRANSLATION_Y,restTranslationY,restTranslationY-dpValue(19),restTranslationY));
                    animators.add(ObjectAnimator.ofFloat(this,View.ROTATION,0f,-8f,8f,0f));
                    animators.add(ObjectAnimator.ofFloat(this,View.SCALE_Y,1f,.88f,1.12f,1f));
                    animators.add(poseAnimator(0f,.8f,0f));break;
                default:
                    expressionMode=1;
                    animators.add(ObjectAnimator.ofFloat(this,View.ROTATION_Y,0f,180f,360f));
                    animators.add(ObjectAnimator.ofFloat(this,View.TRANSLATION_Y,restTranslationY,restTranslationY-dpValue(12),restTranslationY));
                    animators.add(ObjectAnimator.ofFloat(this,View.SCALE_X,1f,.86f,1f));
                    animators.add(sparkAnimator(0f,1f,0f));break;
            }
            final AnimatorSet current=new AnimatorSet();reaction=current;
            current.playTogether(animators);
            current.setDuration(reactionIndex==0?940L:reactionIndex==1?860L:720L);
            current.setInterpolator(reactionIndex==0||reactionIndex==3
                    ?new OvershootInterpolator(.8f):new DecelerateInterpolator());
            current.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(Animator animation){
                    restoreRestPose();if(reaction==current)reaction=null;
                }
                @Override public void onAnimationCancel(Animator animation){restoreRestPose();}
            });
            announceForAccessibility(REACTIONS[reactionIndex]);current.start();
        }
        void stopReaction(){
            AnimatorSet running=reaction;if(running==null)return;
            reaction=null;running.cancel();restoreRestPose();
        }
        private void restoreRestPose(){
            setRotation(0f);setRotationY(0f);setScaleX(1f);setScaleY(1f);
            setTranslationX(restTranslationX);setTranslationY(restTranslationY);
            wavePose=0f;sickPose=0f;sparkPose=0f;expressionMode=0;invalidate();
        }
        private ValueAnimator poseAnimator(float...values){ValueAnimator a=ValueAnimator.ofFloat(values);a.addUpdateListener(v->{wavePose=(float)v.getAnimatedValue();invalidate();});return a;}
        private ValueAnimator sickAnimator(float...values){ValueAnimator a=ValueAnimator.ofFloat(values);a.addUpdateListener(v->{sickPose=(float)v.getAnimatedValue();invalidate();});return a;}
        private ValueAnimator sparkAnimator(float...values){ValueAnimator a=ValueAnimator.ofFloat(values);a.addUpdateListener(v->{sparkPose=(float)v.getAnimatedValue();invalidate();});return a;}
        private float dpValue(float value){return value*getResources().getDisplayMetrics().density;}
        @Override public boolean performClick(){super.performClick();return true;}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight(),cx=w*.5f,cy=h*.47f + idleY,s=w*.88f;
            int cream=Color.rgb(246,241,229);
            
            // Draw shadow at fixed height and scale width with hover height
            float shadowCY = h * 0.47f;
            float shadowScale = 1.0f + (idleY / dpValue(25));
            paint.setColor(0x260C1854);
            canvas.drawOval(new RectF(cx - s * .34f * shadowScale, shadowCY + s * .38f, cx + s * .34f * shadowScale, shadowCY + s * .51f), paint);
            
            if(level>=10){
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2,s*.025f));
                paint.setColor(0xAAFFD23D);
                canvas.drawCircle(cx,cy,s*.47f,paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if(level>=4){
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2,s*.02f));
                paint.setColor(0x884052F4);
                canvas.drawCircle(cx,cy,s*.41f,paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if(level>=7){
                paint.setStyle(Paint.Style.FILL);
                if("Ksatria".equals(characterClass)){
                    paint.setColor(Color.rgb(220,230,245));
                } else if("Penyihir".equals(characterClass)){
                    paint.setColor(Color.rgb(190,180,255));
                } else {
                    paint.setColor(Color.rgb(210,245,220));
                }
                
                // Wing flap angle linked to bobbing height
                float wingAngle = (idleY / dpValue(5)) * 12f;
                
                // Left Wing
                canvas.save();
                canvas.rotate(wingAngle, cx - s * .18f, cy - s * .12f);
                path.reset();path.moveTo(cx-s*.18f,cy-s*.12f);path.lineTo(cx-s*.47f,cy-s*.30f);path.lineTo(cx-s*.39f,cy+s*.17f);path.lineTo(cx-s*.18f,cy+s*.10f);path.close();canvas.drawPath(path,paint);
                canvas.restore();
                
                // Right Wing
                canvas.save();
                canvas.rotate(-wingAngle, cx + s * .18f, cy - s * .12f);
                path.reset();path.moveTo(cx+s*.18f,cy-s*.12f);path.lineTo(cx+s*.47f,cy-s*.30f);path.lineTo(cx+s*.39f,cy+s*.17f);path.lineTo(cx+s*.18f,cy+s*.10f);path.close();canvas.drawPath(path,paint);
                canvas.restore();
            }
            if(level>=3){
                if("Ksatria".equals(characterClass)){
                    paint.setColor(Color.rgb(200,35,55));
                } else if("Penyihir".equals(characterClass)){
                    paint.setColor(Color.rgb(80,35,180));
                } else {
                    paint.setColor(Color.rgb(45,110,65));
                }
                path.reset();path.moveTo(cx-s*.20f,cy-s*.05f);path.lineTo(cx-s*.40f,cy+s*.22f);path.lineTo(cx-s*.18f,cy+s*.31f);path.close();canvas.drawPath(path,paint);
            }
            
            int bodyColor;
            if("Ksatria".equals(characterClass)){
                bodyColor=Color.rgb(130,145,160);
            } else if("Penyihir".equals(characterClass)){
                bodyColor=Color.rgb(90,70,150);
            } else {
                bodyColor=Color.rgb(65,120,80);
            }
            paint.setColor(bodyColor);
            canvas.drawRoundRect(new RectF(cx-s*.27f,cy-s*.05f,cx+s*.27f,cy+s*.36f),s*.14f,s*.14f,paint);
            
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(Math.max(3,s*.075f));paint.setColor(bodyColor);
            canvas.drawLine(cx-s*.23f,cy+s*.04f,cx-s*.39f,cy+s*.22f,paint);
            paint.setStyle(Paint.Style.FILL);
            
            if(level>=5){
                if("Ksatria".equals(characterClass)){
                    paint.setColor(Color.rgb(180, 110, 50));
                    path.reset();
                    path.moveTo(cx - s*.26f, cy + s*.15f);
                    path.lineTo(cx - s*.42f, cy + s*.15f);
                    path.lineTo(cx - s*.39f, cy + s*.36f);
                    path.lineTo(cx - s*.34f, cy + s*.41f);
                    path.lineTo(cx - s*.29f, cy + s*.36f);
                    path.close();
                    canvas.drawPath(path, paint);
                    paint.setColor(Color.rgb(255, 205, 38));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2f);
                    canvas.drawPath(path, paint);
                    canvas.drawLine(cx - s*.34f, cy + s*.18f, cx - s*.34f, cy + s*.38f, paint);
                    canvas.drawLine(cx - s*.39f, cy + s*.26f, cx - s*.29f, cy + s*.26f, paint);
                    paint.setStyle(Paint.Style.FILL);
                } else if("Penyihir".equals(characterClass)){
                    round(canvas, cx-s*.44f, cy+s*.18f, cx-s*.24f, cy+s*.38f, s*.03f, Color.rgb(160,50,60));
                    round(canvas, cx-s*.41f, cy+s*.21f, cx-s*.27f, cy+s*.35f, s*.02f, Color.rgb(245,240,220));
                    paint.setColor(Color.rgb(255,205,38));
                    canvas.drawCircle(cx-s*.34f, cy+s*.28f, s*.04f, paint);
                } else {
                    paint.setColor(Color.rgb(130,85,40));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3f);
                    canvas.drawArc(new RectF(cx-s*.44f, cy+s*.08f, cx-s*.24f, cy+s*.42f), 100, 160, false, paint);
                    paint.setStrokeWidth(1f);
                    paint.setColor(Color.rgb(230,235,245));
                    canvas.drawLine(cx-s*.36f, cy+s*.10f, cx-s*.36f, cy+s*.40f, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
            
            canvas.save();
            canvas.rotate(-30f-50f*wavePose,cx+s*.23f,cy+s*.03f);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(Math.max(3,s*.075f));paint.setColor(bodyColor);
            canvas.drawLine(cx+s*.23f,cy+s*.03f,cx+s*.43f,cy-s*.12f,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(cream);
            canvas.drawCircle(cx+s*.45f,cy-s*.14f,s*.05f,paint);
            
            if(level>=3){
                if("Ksatria".equals(characterClass)){
                    paint.setColor(Color.rgb(180, 140, 30));
                    canvas.drawRect(cx+s*.38f, cy-s*.18f, cx+s*.52f, cy-s*.14f, paint);
                    canvas.drawRect(cx+s*.43f, cy-s*.14f, cx+s*.47f, cy-s*.08f, paint);
                    paint.setColor(Color.rgb(220, 225, 235));
                    path.reset();
                    path.moveTo(cx+s*.43f, cy-s*.18f);
                    path.lineTo(cx+s*.43f, cy-s*.52f);
                    path.lineTo(cx+s*.45f, cy-s*.58f);
                    path.lineTo(cx+s*.47f, cy-s*.52f);
                    path.lineTo(cx+s*.47f, cy-s*.18f);
                    path.close();
                    canvas.drawPath(path, paint);
                } else if("Penyihir".equals(characterClass)){
                    paint.setColor(Color.rgb(110, 75, 45));
                    canvas.drawRect(cx+s*.43f, cy-s*.48f, cx+s*.47f, cy-s*.06f, paint);
                    if(level>=5){
                        paint.setColor(Color.rgb(0, 230, 245));
                        path.reset();
                        path.moveTo(cx+s*.45f, cy-s*.60f);
                        path.lineTo(cx+s*.50f, cy-s*.53f);
                        path.lineTo(cx+s*.45f, cy-s*.46f);
                        path.lineTo(cx+s*.40f, cy-s*.53f);
                        path.close();
                        canvas.drawPath(path, paint);
                    }
                }
            }
            canvas.restore();
            
            if(level>=3 && "Pemanah".equals(characterClass)){
                paint.setColor(Color.rgb(130, 85, 40));
                canvas.drawRoundRect(new RectF(cx+s*.16f, cy+s*.08f, cx+s*.28f, cy+s*.30f), s*.02f, s*.02f, paint);
                paint.setColor(Color.rgb(200, 50, 60));
                canvas.drawRect(cx+s*.18f, cy, cx+s*.21f, cy+s*.08f, paint);
                canvas.drawRect(cx+s*.23f, cy-s*.03f, cx+s*.26f, cy+s*.08f, paint);
            }
            
            paint.setStrokeCap(Paint.Cap.BUTT);paint.setStyle(Paint.Style.FILL);
            paint.setColor(cream);canvas.drawCircle(cx,cy-s*.15f,s*.29f,paint);
            canvas.drawCircle(cx-s*.22f,cy-s*.34f,s*.10f,paint);canvas.drawCircle(cx+s*.22f,cy-s*.34f,s*.10f,paint);
            
            paint.setColor(Color.rgb(12,24,84));
            canvas.drawRoundRect(new RectF(cx-s*.22f,cy-s*.29f,cx+s*.22f,cy-s*.04f),s*.10f,s*.10f,paint);
            
            paint.setColor(Color.rgb(91,229,208));
            if (blinking && expressionMode == 0) {
                paint.setStrokeWidth(Math.max(2, s * .018f)); paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx - s * .12f, cy - s * .17f, cx - s * .04f, cy - s * .17f, paint);
                canvas.drawLine(cx + s * .04f, cy - s * .17f, cx + s * .12f, cy - s * .17f, paint);
            } else if(expressionMode==2){
                paint.setStrokeWidth(Math.max(2,s*.02f));paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx-s*.11f,cy-s*.19f,cx-s*.05f,cy-s*.14f,paint);canvas.drawLine(cx-s*.05f,cy-s*.19f,cx-s*.11f,cy-s*.14f,paint);
                canvas.drawLine(cx+s*.05f,cy-s*.19f,cx+s*.11f,cy-s*.14f,paint);canvas.drawLine(cx+s*.11f,cy-s*.19f,cx+s*.05f,cy-s*.14f,paint);
            }else if(expressionMode==3){
                canvas.drawCircle(cx-s*.08f,cy-s*.17f,s*.040f,paint);canvas.drawCircle(cx+s*.08f,cy-s*.17f,s*.040f,paint);
            }else{
                canvas.drawCircle(cx-s*.08f,cy-s*.17f,s*.028f,paint);canvas.drawCircle(cx+s*.08f,cy-s*.17f,s*.028f,paint);
            }
            
            if(expressionMode!=2 && !(blinking && expressionMode == 0)){
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx-s*.07f,cy-s*.18f,s*.010f,paint);
                canvas.drawCircle(cx+s*.09f,cy-s*.18f,s*.010f,paint);
            }
            
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(2,s*.018f));
            paint.setColor(Color.rgb(91,229,208));
            if(expressionMode==2)canvas.drawArc(new RectF(cx-s*.06f,cy-s*.08f,cx+s*.06f,cy),200,140,false,paint);
            else canvas.drawArc(new RectF(cx-s*.07f,cy-s*.15f,cx+s*.07f,cy-s*.06f),15,150,false,paint);paint.setStyle(Paint.Style.FILL);
            
            if("Ksatria".equals(characterClass)){
                round(canvas, cx-s*.18f, cy-s*.30f, cx+s*.18f, cy-s*.22f, s*.04f, Color.rgb(100,115,130));
                paint.setColor(Color.rgb(255,205,38));
                canvas.drawRect(cx-s*.13f, cy-s*.28f, cx+s*.13f, cy-s*.25f, paint);
            } else if("Penyihir".equals(characterClass)){
                paint.setColor(Color.rgb(105,75,200));
                path.reset();
                path.moveTo(cx-s*.34f, cy-s*.26f);
                path.lineTo(cx-s*.06f, cy-s*.58f);
                path.lineTo(cx+s*.06f, cy-s*.58f);
                path.lineTo(cx+s*.34f, cy-s*.26f);
                path.close();
                canvas.drawPath(path, paint);
                round(canvas, cx-s*.36f, cy-s*.27f, cx+s*.36f, cy-s*.23f, s*.02f, Color.rgb(255,205,38));
                if(level>=2){
                    paint.setColor(Color.rgb(255,220,50));
                    star(canvas, cx, cy-s*.64f, s*.07f);
                }
            } else {
                paint.setColor(Color.rgb(65,120,80));
                path.reset();
                path.moveTo(cx-s*.34f, cy-s*.18f);
                path.lineTo(cx-s*.28f, cy-s*.42f);
                path.lineTo(cx, cy-s*.50f);
                path.lineTo(cx+s*.28f, cy-s*.42f);
                path.lineTo(cx+s*.34f, cy-s*.18f);
                path.lineTo(cx+s*.20f, cy-s*.04f);
                path.lineTo(cx-s*.20f, cy-s*.04f);
                path.close();
                canvas.drawPath(path, paint);
                paint.setColor(Color.rgb(40,80,50));
                canvas.drawArc(new RectF(cx-s*.24f, cy-s*.44f, cx+s*.24f, cy-s*.24f), 180, 180, true, paint);
            }
            
            paint.setColor(cream);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(s*.15f);
            canvas.drawText("M",cx,cy+s*.24f,paint);canvas.drawCircle(cx-s*.18f,cy+s*.39f,s*.08f,paint);canvas.drawCircle(cx+s*.18f,cy+s*.39f,s*.08f,paint);
            
            if(level>=2 && !"Penyihir".equals(characterClass)){
                paint.setColor(Color.rgb(255,205,38));
                star(canvas,cx-s*.31f,cy-s*.35f,s*.09f);
            }
            
            if(level>=5){
                paint.setColor(Color.rgb(255,190,30));
                path.reset();
                path.moveTo(cx-s*.14f,cy-s*.38f);path.lineTo(cx-s*.08f,cy-s*.50f);path.lineTo(cx,cy-s*.40f);path.lineTo(cx+s*.08f,cy-s*.50f);path.lineTo(cx+s*.14f,cy-s*.38f);
                path.close();canvas.drawPath(path,paint);
            }
            
            if(level>=10){
                paint.setColor(Color.WHITE);
                star(canvas,cx+s*.38f,cy-s*.34f,s*.08f);
            }
            
            if(sickPose>0f){
                paint.setColor(0xAAFFD23D);canvas.drawCircle(cx+s*(.26f+.08f*sickPose),cy-s*(.42f+.03f*sickPose),s*.045f,paint);canvas.drawCircle(cx+s*(.37f-.04f*sickPose),cy-s*(.31f+.04f*sickPose),s*.032f,paint);
            }
            
            if(sparkPose>0f){
                paint.setColor(0xFFFFD23D);star(canvas,cx-s*.36f,cy-s*(.40f+.08f*sparkPose),s*.08f*sparkPose);
                paint.setColor(0xFF8EA0FF);star(canvas,cx+s*.38f,cy-s*(.45f+.06f*sparkPose),s*.065f*sparkPose);
            }
        }
        private void round(Canvas c, float l, float t, float r, float b, float radius, int color) {
            paint.setColor(color);
            c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
        }
        private void star(Canvas canvas,float x,float y,float r){if(r<=0)return;path.reset();path.moveTo(x,y-r);path.lineTo(x+r*.22f,y-r*.22f);path.lineTo(x+r,y);path.lineTo(x+r*.22f,y+r*.22f);path.lineTo(x,y+r);path.lineTo(x-r*.22f,y+r*.22f);path.lineTo(x-r,y);path.lineTo(x-r*.22f,y-r*.22f);path.close();canvas.drawPath(path,paint);}
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottomMargin;
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    void openTalkBackSettings(){
        // Open the phone manufacturer's Accessibility overview. This matches the
        // familiar screen where TalkBack appears alongside other accessibility
        // services and can be selected and enabled by the user.
        try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
        catch(Exception ignored){Toast.makeText(this,"Pengaturan aksesibilitas tidak tersedia",Toast.LENGTH_LONG).show();}
    }

    void requestVoiceInput() {
        requestVoiceInput("Ucapkan teks quest",null);
    }

    private void requestVoiceInput(String prompt,Consumer<String> callback) {
        voiceCallback=callback;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
        try {
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (Exception ignored) {
            voiceCallback=null;
            studyMateView.showMessage("Pengenal suara tidak tersedia di perangkat ini");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                Consumer<String> callback=voiceCallback;voiceCallback=null;
                if(callback!=null)callback.accept(results.get(0));
                else studyMateView.acceptVoiceText(results.get(0));
            }
        } else if (requestCode == PROFILE_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (Exception ignored) {}
            if (studyMateView != null) studyMateView.applyProfileImage(uri.toString());
        }else if(requestCode==VOICE_REQUEST){
            voiceCallback=null;
        }
    }

    private boolean handleBackNavigation() {
        if(questView!=null){studyMateView.cancelQuestForm();return true;}
        if(profileView!=null){studyMateView.closeProfileEditor();return true;}
        if(courseView!=null){studyMateView.closeCourseManager();return true;}
        if (authView != null && registrationMode &&
                getSharedPreferences("studymate", MODE_PRIVATE).getBoolean("account_created", false)) {
            showAuth(false);
            return true;
        }
        return studyMateView != null && studyMateView.handleBack();
    }

    private static class Field {
        final TextInputLayout layout;
        final android.widget.EditText input;
        final String name;

        Field(TextInputLayout layout, android.widget.EditText input, String name) {
            this.layout = layout;
            this.input = input;
            this.name = name;
        }

        String value() {
            return input.getText() == null ? "" : input.getText().toString().trim();
        }
    }

    private static class QuestText{
        final TextInputLayout layout;final TextInputEditText input;
        QuestText(TextInputLayout layout,TextInputEditText input){this.layout=layout;this.input=input;}
        String value(){return input.getText()==null?"":input.getText().toString().trim();}
    }

    private static class QuestSelect{
        final TextInputLayout layout;final MaterialAutoCompleteTextView input;
        QuestSelect(TextInputLayout layout,MaterialAutoCompleteTextView input){this.layout=layout;this.input=input;}
        String value(){return input.getText()==null?"":input.getText().toString().trim();}
    }
}
