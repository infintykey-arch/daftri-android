package com.cue.daftri;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.UUID;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class MainActivity extends Activity {
    private static final int REQUEST_TASK_IMAGES = 410;
    private static final int REQUEST_TITLE_IMAGE = 411;
    private static final int TASK_IMAGE_LIMIT = 10;
    private static final int INTERSTITIAL_EVERY_DETAILS_CLOSES = 3;
    private static final int MAX_AD_RETRIES = 2;
    private static final long MIN_BANNER_REQUEST_INTERVAL_MS = 30_000L;
    private static final String ADMOB_PREFS = "daftri_admob";
    private static final String PREF_LAST_BANNER_REQUEST_MS = "last_banner_request_ms";
    private static final String ADS_TAG = "DaftriAds";
    private static final String INTERSTITIAL_AD_UNIT_ID = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID;
    private static final String NATIVE_AD_UNIT_ID = BuildConfig.ADMOB_NATIVE_AD_UNIT_ID;
    private static final String BANNER_AD_UNIT_ID = BuildConfig.ADMOB_BANNER_AD_UNIT_ID;
    private static final int BG = Color.rgb(5, 8, 15);
    private static final int PANEL = Color.rgb(12, 17, 26);
    private static final int MUTED = Color.rgb(187, 198, 213);
    private static final int BLUE = Color.rgb(24, 145, 255);
    private static final int DESTINATION_TASKS = 0;
    private static final int DESTINATION_CALENDAR = 1;
    private static final int DESTINATION_TRASH = 2;
    private static final int[] CARD_COLORS = {
            Color.rgb(18, 91, 176), Color.rgb(22, 112, 207),
            Color.rgb(16, 69, 139), Color.rgb(42, 52, 67),
            Color.rgb(20, 96, 181), Color.rgb(16, 91, 145)
    };

    private final ArrayList<Notebook> notebooks = new ArrayList<>();
    private final ArrayList<TrashedNotebook> trashedNotebooks = new ArrayList<>();
    private final LinkedHashSet<String> selectedNotebookIds = new LinkedHashSet<>();
    private LinearLayout cardsContainer;
    private ScrollView cardsScroll;
    private LinearLayout contentHost;
    private LinearLayout selectionToolbar;
    private TextView selectionModeButton;
    private TextView selectionStatus;
    private TextView exportPdfButton;
    private TextView appTitle;
    private TextView appSubtitle;
    private TextView tasksNavigationButton;
    private TextView calendarNavigationButton;
    private TextView trashNavigationButton;
    private FrameLayout addFooter;
    private int currentDestination = DESTINATION_TASKS;
    private boolean selectingNotebooks;
    private boolean pdfExporting;
    private boolean storageHealthy = true;
    private Dialog detailsDialog;
    private TaskEntry pendingImageTask;
    private LinearLayout pendingTaskImageStrip;
    private TitleImageState pendingTitleImageState;
    private ValueAnimator addGlowAnimator;
    private ConsentInformation consentInformation;
    private TextView privacyOptionsButton;
    private FrameLayout bannerContainer;
    private TextView bannerPlaceholder;
    private AdView bannerAdView;
    private FrameLayout nativeAdCard;
    private TextView nativeAdPlaceholder;
    private NativeAd activeNativeAd;
    private InterstitialAd interstitialAd;
    private boolean adsInitializationStarted;
    private boolean adsInitialized;
    private boolean nativeAdLoading;
    private boolean nativeAdAttempted;
    private boolean interstitialLoading;
    private boolean interstitialShowing;
    private final Handler adRetryHandler = new Handler(Looper.getMainLooper());
    private int bannerRetryCount;
    private int nativeRetryCount;
    private Runnable bannerRetryRunnable;
    private Runnable nativeRetryRunnable;

    private long millisUntilNextBannerRequest() {
        long lastRequestMs = getSharedPreferences(ADMOB_PREFS, MODE_PRIVATE)
                .getLong(PREF_LAST_BANNER_REQUEST_MS, 0L);
        if (lastRequestMs <= 0L) return 0L;
        long elapsedMs = System.currentTimeMillis() - lastRequestMs;
        if (elapsedMs < 0L) elapsedMs = 0L;
        return Math.max(0L, MIN_BANNER_REQUEST_INTERVAL_MS - elapsedMs);
    }

    private void recordBannerRequestTime() {
        getSharedPreferences(ADMOB_PREFS, MODE_PRIVATE).edit()
                .putLong(PREF_LAST_BANNER_REQUEST_MS, System.currentTimeMillis()).apply();
    }

    private void scheduleBannerRequest(long requestedDelayMs, boolean countsAsFailureRetry) {
        if (!canRequestAds() || isFinishing() || isDestroyed() || bannerAdView != null) return;
        if (countsAsFailureRetry && bannerRetryCount >= MAX_AD_RETRIES) {
            Log.i(ADS_TAG, "Banner retry limit reached");
            return;
        }
        cancelBannerRetry();
        long delayMs = Math.max(Math.max(0L, requestedDelayMs), millisUntilNextBannerRequest());
        if (countsAsFailureRetry) bannerRetryCount++;
        Log.i(ADS_TAG, "Scheduling banner request "
                + (countsAsFailureRetry ? "retry " + bannerRetryCount : "replacement")
                + " in " + delayMs + "ms");
        bannerRetryRunnable = () -> {
            bannerRetryRunnable = null;
            if (adsInitialized && canRequestAds() && !isFinishing() && !isDestroyed()) loadBannerAd();
        };
        adRetryHandler.postDelayed(bannerRetryRunnable, delayMs);
    }

    private void scheduleBannerRetry() {
        scheduleBannerRequest(60_000L * (bannerRetryCount + 1), true);
    }

    private void scheduleBannerReplacement() {
        if (bannerContainer == null || !canRequestAds() || isFinishing() || isDestroyed()) return;
        scheduleBannerRequest(0L, false);
    }

    private void cancelBannerRetry() {
        if (bannerRetryRunnable != null) adRetryHandler.removeCallbacks(bannerRetryRunnable);
        bannerRetryRunnable = null;
    }

    private void scheduleNativeRetry() {
        if (!canRequestAds() || isFinishing() || isDestroyed() || nativeAdCard == null || cardsContainer == null
                || nativeAdCard.getParent() != cardsContainer) return;
        if (nativeRetryCount >= MAX_AD_RETRIES) {
            nativeAdAttempted = true;
            Log.i(ADS_TAG, "Native retry limit reached; further requests paused until the next consent cycle");
            return;
        }
        cancelNativeRetry();
        long delayMs = 60_000L * (nativeRetryCount + 1);
        nativeRetryCount++;
        Log.i(ADS_TAG, "Scheduling native retry " + nativeRetryCount + " in " + delayMs + "ms");
        nativeRetryRunnable = () -> {
            nativeRetryRunnable = null;
            if (adsInitialized && canRequestAds() && !isFinishing() && !isDestroyed()) loadNativeAdIfNeeded();
        };
        adRetryHandler.postDelayed(nativeRetryRunnable, delayMs);
    }

    private void cancelNativeRetry() {
        if (nativeRetryRunnable != null) adRetryHandler.removeCallbacks(nativeRetryRunnable);
        nativeRetryRunnable = null;
    }

    private void logAdLoadFailure(String format, LoadAdError error) {
        Log.w(ADS_TAG, format + " load failed: " + error);
        if (error.getResponseInfo() != null) {
            Log.w(ADS_TAG, format + " responseInfo: " + error.getResponseInfo());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        boolean loaded = loadNotebooks();
        renderHome();
        startConsentFlow();
        if (!loaded) {
            new AlertDialog.Builder(this)
                    .setTitle("تعذر قراءة البيانات المحفوظة")
                    .setMessage("لم تُستبدل بيانات الدفاتر أو الصور. أُوقفت التعديلات لحماية البيانات الحالية.")
                    .setPositiveButton("حسنًا", null)
                    .show();
        }
    }

    @Override
    protected void onDestroy() {
        if (addGlowAnimator != null) {
            addGlowAnimator.cancel();
            addGlowAnimator = null;
        }
        cancelBannerRetry();
        cancelNativeRetry();
        destroyBannerAd(false);
        destroyActiveNativeAd(false);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (bannerAdView != null) bannerAdView.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAdView != null) bannerAdView.resume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_TASK_IMAGES) {
            if (resultCode == RESULT_OK && data != null && pendingImageTask != null) {
                try {
                    int added = 0;
                    ArrayList<Uri> selected = selectedImageUris(data);
                    for (Uri uri : selected) {
                        if (pendingImageTask.imageFileNames.size() >= TASK_IMAGE_LIMIT) break;
                        try {
                            pendingImageTask.imageFileNames.add(saveTaskImageLocally(uri));
                            added++;
                        } catch (Exception ignored) {
                        }
                    }
                    if (added > 0) {
                        refreshTaskImageStrip(pendingImageTask, pendingTaskImageStrip, true);
                        Toast.makeText(this, "تم إرفاق " + added + " صورة بالمهمة", Toast.LENGTH_SHORT).show();
                    } else if (!selected.isEmpty()) {
                        Toast.makeText(this, "تعذر حفظ الصور على الجهاز", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception exception) {
                    Toast.makeText(this, "تعذر قراءة الصور المحددة", Toast.LENGTH_SHORT).show();
                }
            }
            clearPendingImageSelection();
            return;
        }
        if (requestCode == REQUEST_TITLE_IMAGE) {
            if (resultCode == RESULT_OK && data != null && pendingTitleImageState != null) {
                ArrayList<Uri> selected = selectedImageUris(data);
                if (!selected.isEmpty()) {
                    try {
                        pendingTitleImageState.imageFileName = saveTaskImageLocally(selected.get(0));
                        refreshTitleImagePreview(pendingTitleImageState);
                        Toast.makeText(this, "تم إرفاق صورة العنوان", Toast.LENGTH_SHORT).show();
                    } catch (Exception exception) {
                        Toast.makeText(this, "تعذر حفظ صورة العنوان على الجهاز", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            pendingTitleImageState = null;
        }
    }

    private ArrayList<Uri> selectedImageUris(Intent data) {
        ArrayList<Uri> selected = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) selected.add(uri);
            }
        } else if (data.getData() != null) {
            selected.add(data.getData());
        }
        return selected;
    }

    private void clearPendingImageSelection() {
        pendingImageTask = null;
        pendingTaskImageStrip = null;
    }

    private void renderHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(14), dp(18), 0);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ImageView logo = brandMark(48);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        logoParams.setMarginEnd(dp(11));
        heading.addView(logo, logoParams);
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        appTitle = text("دفتر مهامي", 25, Color.WHITE, true);
        appTitle.setShadowLayer(dp(8), 0, 0, 0x88218FFF);
        appSubtitle = text("كل أفكارك ومهامك في مكان واحد", 13, MUTED, false);
        appSubtitle.setPadding(0, dp(3), 0, 0);
        titleBlock.addView(appTitle);
        titleBlock.addView(appSubtitle);
        heading.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1));
        privacyOptionsButton = text("⋮", 25, MUTED, true);
        privacyOptionsButton.setGravity(Gravity.CENTER);
        privacyOptionsButton.setContentDescription("خيارات الخصوصية");
        privacyOptionsButton.setBackground(rippleBackground(0x33218FFF, dp(18)));
        privacyOptionsButton.setVisibility(View.GONE);
        privacyOptionsButton.setOnClickListener(v -> showPrivacyOptions());
        heading.addView(privacyOptionsButton, new LinearLayout.LayoutParams(dp(42), dp(46)));
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(64)));

        View energyLine = new View(this);
        energyLine.setBackground(roundedGradient(BLUE, Color.rgb(175, 192, 213), dp(2)));
        energyLine.setAlpha(0.82f);
        root.addView(energyLine, new LinearLayout.LayoutParams(-1, dp(2)));

        contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        contentHost.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(contentHost, new LinearLayout.LayoutParams(-1, 0, 1));

        bannerContainer = new FrameLayout(this);
        bannerContainer.setPadding(0, dp(4), 0, dp(4));
        bannerContainer.setVisibility(View.VISIBLE);
        bannerPlaceholder = text("مساحة إعلان", 12, MUTED, true);
        bannerPlaceholder.setGravity(Gravity.CENTER);
        bannerPlaceholder.setContentDescription("موضع مخصص لإعلان البانر");
        GradientDrawable bannerSlotBackground = roundedSolid(Color.rgb(16, 24, 37), dp(10));
        bannerSlotBackground.setStroke(dp(1), 0x7748A8FF);
        bannerPlaceholder.setBackground(bannerSlotBackground);
        bannerContainer.addView(bannerPlaceholder,
                new FrameLayout.LayoutParams(-1, dp(50), Gravity.CENTER));
        root.addView(bannerContainer, new LinearLayout.LayoutParams(-1, -2));

        addFooter = new FrameLayout(this);
        addFooter.setClipChildren(false);
        addFooter.setClipToPadding(false);
        View halo = new View(this);
        halo.setBackground(glowHaloBackground());
        halo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addFooter.addView(halo, new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER));
        startGlowPulse(halo);
        TextView add = text("＋", 31, Color.WHITE, false);
        add.setGravity(Gravity.CENTER);
        add.setBackground(circleBackground());
        add.setElevation(dp(8));
        applyElectricGlow(add, 8, true);
        add.setContentDescription("إضافة دفتر جديد");
        add.setEnabled(storageHealthy);
        if (!storageHealthy) add.setAlpha(0.45f);
        add.setOnClickListener(v -> showEditor(null));
        addFooter.addView(add, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
        root.addView(addFooter, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout bottomNavigation = new LinearLayout(this);
        bottomNavigation.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavigation.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomNavigation.setGravity(Gravity.CENTER_VERTICAL);
        bottomNavigation.setPadding(dp(4), dp(4), dp(4), dp(6));
        bottomNavigation.setBackground(roundedStroke(Color.rgb(12, 17, 26), 0x66547BA5, dp(18)));
        calendarNavigationButton = createNavigationButton("▦", "التقويم", DESTINATION_CALENDAR);
        tasksNavigationButton = createNavigationButton("⌂", "الرئيسية", DESTINATION_TASKS);
        trashNavigationButton = createNavigationButton("♲", "السلة", DESTINATION_TRASH);
        bottomNavigation.addView(calendarNavigationButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        bottomNavigation.addView(tasksNavigationButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        bottomNavigation.addView(trashNavigationButton, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(bottomNavigation, new LinearLayout.LayoutParams(-1, dp(62)));

        setContentView(root);
        renderDestinationContent();
    }

    private TextView createNavigationButton(String icon, String label, int destination) {
        TextView button = text(icon + "\n" + label, 11, Color.rgb(199, 214, 234), true);
        button.setGravity(Gravity.CENTER);
        button.setLineSpacing(0, 0.94f);
        button.setContentDescription(label);
        button.setBackground(rippleBackground(0x00000000, dp(13)));
        button.setOnClickListener(v -> navigateTo(destination));
        return button;
    }

    private void navigateTo(int destination) {
        if (destination != DESTINATION_TASKS) {
            selectingNotebooks = false;
            selectedNotebookIds.clear();
        }
        currentDestination = destination;
        renderDestinationContent();
    }

    private void renderDestinationContent() {
        if (contentHost == null) return;
        if (nativeAdCard != null && nativeAdCard.getParent() instanceof ViewGroup) {
            ((ViewGroup) nativeAdCard.getParent()).removeView(nativeAdCard);
        }
        contentHost.removeAllViews();
        if (currentDestination == DESTINATION_CALENDAR) {
            appTitle.setText("التقويم");
            appSubtitle.setText("تابع تواريخ إنشاء دفاترك ومهامك");
            addFooter.setVisibility(View.GONE);
            renderCalendarDestination();
        } else if (currentDestination == DESTINATION_TRASH) {
            appTitle.setText("سلة المحذوفات");
            appSubtitle.setText("يمكنك استعادة الدفاتر خلال ٣٠ يومًا");
            addFooter.setVisibility(View.GONE);
            renderTrashDestination();
        } else {
            appTitle.setText("دفتر مهامي");
            appSubtitle.setText("كل أفكارك ومهامك في مكان واحد");
            addFooter.setVisibility(View.VISIBLE);

            selectionToolbar = new LinearLayout(this);
            selectionToolbar.setOrientation(LinearLayout.HORIZONTAL);
            selectionToolbar.setGravity(Gravity.CENTER_VERTICAL);
            selectionToolbar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            selectionToolbar.setPadding(0, dp(6), 0, dp(4));
            selectionModeButton = actionButton("إلغاء التحديد", false);
            selectionModeButton.setOnClickListener(v -> {
                selectingNotebooks = false;
                selectedNotebookIds.clear();
                renderCards();
            });
            selectionStatus = text("", 12, MUTED, true);
            selectionStatus.setGravity(Gravity.CENTER);
            exportPdfButton = actionButton("إنشاء PDF", true);
            exportPdfButton.setOnClickListener(v -> beginPdfExport());
            selectionToolbar.addView(selectionModeButton,
                    new LinearLayout.LayoutParams(0, dp(42), 1));
            selectionToolbar.addView(selectionStatus,
                    new LinearLayout.LayoutParams(0, dp(42), 1));
            selectionToolbar.addView(exportPdfButton,
                    new LinearLayout.LayoutParams(0, dp(42), 1));
            contentHost.addView(selectionToolbar, new LinearLayout.LayoutParams(-1, dp(50)));

            cardsScroll = new ScrollView(this);
            cardsScroll.setFillViewport(true);
            cardsScroll.setClipToPadding(false);
            cardsScroll.setPadding(0, dp(6), 0, dp(8));
            cardsContainer = new LinearLayout(this);
            cardsContainer.setOrientation(LinearLayout.VERTICAL);
            cardsContainer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            cardsScroll.addView(cardsContainer, new ScrollView.LayoutParams(-1, -2));
            contentHost.addView(cardsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
            renderCards();
        }
        updateNavigationAppearance();
    }

    private void updateNavigationAppearance() {
        updateNavigationButton(calendarNavigationButton, DESTINATION_CALENDAR);
        updateNavigationButton(tasksNavigationButton, DESTINATION_TASKS);
        updateNavigationButton(trashNavigationButton, DESTINATION_TRASH);
    }

    private void updateNavigationButton(TextView button, int destination) {
        if (button == null) return;
        boolean selected = currentDestination == destination;
        button.setTextColor(selected ? Color.WHITE : Color.rgb(199, 214, 234));
        button.setBackground(selected
                ? roundedStroke(Color.rgb(17, 55, 91), 0xFF3E9FFF, dp(13))
                : roundedSolid(Color.TRANSPARENT, dp(13)));
    }

    private void toggleNotebookSelection(Notebook notebook) {
        if (selectedNotebookIds.contains(notebook.id)) selectedNotebookIds.remove(notebook.id);
        else selectedNotebookIds.add(notebook.id);
        updateSelectionToolbar();
        renderCards();
    }

    private void updateSelectionToolbar() {
        if (selectionToolbar == null || selectionModeButton == null) return;
        selectionToolbar.setVisibility(selectingNotebooks && currentDestination == DESTINATION_TASKS
                ? View.VISIBLE : View.GONE);
        selectionModeButton.setEnabled(storageHealthy);
        selectionStatus.setVisibility(selectingNotebooks ? View.VISIBLE : View.GONE);
        exportPdfButton.setVisibility(selectingNotebooks && selectedNotebookIds.size() > 1
                ? View.VISIBLE : View.GONE);
        selectionStatus.setText(getString(R.string.selected_count,
                toArabicDigits(String.valueOf(selectedNotebookIds.size()))));
        exportPdfButton.setEnabled(storageHealthy && !pdfExporting);
        if (pdfExporting) exportPdfButton.setText(R.string.pdf_exporting);
        else exportPdfButton.setText(R.string.pdf_export);
    }

    private void renderCards() {
        int previousScrollY = cardsScroll == null ? 0 : cardsScroll.getScrollY();
        updateSelectionToolbar();
        cardsContainer.removeAllViews();
        if (notebooks.isEmpty()) {
            destroyActiveNativeAd(false);
            nativeAdAttempted = false;
            TextView empty = text("ابدأ بدفترك الأول\nاضغط + لإضافة دفتر ومهامه", 16, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(7), 1f);
            cardsContainer.addView(empty, new LinearLayout.LayoutParams(-1, dp(250)));
            ensureNativeAdCard();
            LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(-1, -2);
            adParams.bottomMargin = dp(12);
            cardsContainer.addView(nativeAdCard, adParams);
            loadNativeAdIfNeeded();
            return;
        }
        for (int i = 0; i < notebooks.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(12);
            cardsContainer.addView(row, rowParams);
            addNotebookCard(row, notebooks.get(i));
            if (i + 1 < notebooks.size()) {
                addNotebookCard(row, notebooks.get(i + 1));
            } else {
                View spacer = new View(this);
                row.addView(spacer, new LinearLayout.LayoutParams(0, dp(8), 1));
            }
            if (i == 0) {
                ensureNativeAdCard();
                LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(-1, -2);
                adParams.bottomMargin = dp(12);
                cardsContainer.addView(nativeAdCard, adParams);
                loadNativeAdIfNeeded();
            }
        }
        if (cardsScroll != null) cardsScroll.post(() -> cardsScroll.scrollTo(0, previousScrollY));
    }

    private void startConsentFlow() {
        Log.i(ADS_TAG, "Requesting UMP consent information");
        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters parameters = new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(
                this,
                parameters,
                () -> {
                    Log.i(ADS_TAG, "UMP consent update succeeded; canRequestAds=" + canRequestAds());
                    refreshPrivacyOptionsButton();
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, formError -> {
                        if (formError != null) Log.w(ADS_TAG, "UMP form: " + formError.getMessage());
                        refreshPrivacyOptionsButton();
                        updateAdsForConsent();
                    });
                    updateAdsForConsent();
                },
                requestError -> {
                    Log.w(ADS_TAG, "UMP consent update failed: " + requestError.getMessage()
                            + "; canRequestAds=" + canRequestAds());
                    refreshPrivacyOptionsButton();
                    updateAdsForConsent();
                });
        updateAdsForConsent();
    }

    private void refreshPrivacyOptionsButton() {
        if (privacyOptionsButton == null || consentInformation == null) return;
        boolean required = consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        privacyOptionsButton.setVisibility(required ? View.VISIBLE : View.GONE);
    }

    private void showPrivacyOptions() {
        if (consentInformation == null
                || consentInformation.getPrivacyOptionsRequirementStatus()
                != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) return;
        UserMessagingPlatform.showPrivacyOptionsForm(this, formError -> {
            refreshPrivacyOptionsButton();
            if (formError != null) {
                Toast.makeText(this, "تعذر فتح خيارات الخصوصية", Toast.LENGTH_SHORT).show();
            }
            updateAdsForConsent();
        });
    }

    private boolean canRequestAds() {
        return consentInformation != null && consentInformation.canRequestAds();
    }

    private void updateAdsForConsent() {
        refreshPrivacyOptionsButton();
        if (canRequestAds()) {
            Log.i(ADS_TAG, "UMP permits ad requests");
            showBannerPlaceholder("مساحة إعلان · جارٍ التحميل");
            setNativeAdPlaceholder("مساحة إعلان أصلي · جارٍ التحميل");
            initializeAdsIfAllowed();
        } else {
            Log.i(ADS_TAG, "Ad requests paused until UMP allows them");
            showBannerPlaceholder("مساحة إعلان · بانتظار الموافقة");
            setNativeAdPlaceholder("مساحة إعلان أصلي · بانتظار الموافقة");
            if (!adsInitialized) return;
            cancelBannerRetry();
            cancelNativeRetry();
            bannerRetryCount = 0;
            nativeRetryCount = 0;
            destroyBannerAd(true);
            destroyActiveNativeAd(true);
            nativeAdAttempted = false;
            interstitialAd = null;
            interstitialLoading = false;
        }
    }

    private void initializeAdsIfAllowed() {
        if (!canRequestAds() || isFinishing() || isDestroyed()) return;
        if (adsInitializationStarted) {
            if (adsInitialized) loadAvailableAds();
            return;
        }
        adsInitializationStarted = true;
        Log.i(ADS_TAG, "Initializing Google Mobile Ads SDK");
        new Thread(() -> MobileAds.initialize(getApplicationContext(), status -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            adsInitialized = true;
            Log.i(ADS_TAG, "Google Mobile Ads SDK initialized; adapters=" + status.getAdapterStatusMap());
            if (canRequestAds()) loadAvailableAds();
        }))).start();
    }

    private void loadAvailableAds() {
        loadBannerAd();
        loadNativeAdIfNeeded();
        loadInterstitialAd();
    }

    private void loadBannerAd() {
        if (bannerContainer == null) return;
        if (!adsInitialized || !canRequestAds()) {
            showBannerPlaceholder("مساحة إعلان · بانتظار الموافقة");
            return;
        }
        if (bannerAdView != null || bannerRetryRunnable != null) return;
        long requestWaitMs = millisUntilNextBannerRequest();
        if (requestWaitMs > 0L) {
            Log.i(ADS_TAG, "Banner request held for minimum 30s interval; remaining=" + requestWaitMs + "ms");
            scheduleBannerRequest(requestWaitMs, false);
            return;
        }
        showBannerPlaceholder("مساحة إعلان · جارٍ التحميل");
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density) - 36;
        AdSize adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, Math.max(1, widthDp));
        AdView requestedView = new AdView(this);
        bannerAdView = requestedView;
        requestedView.setAdUnitId(BANNER_AD_UNIT_ID);
        requestedView.setAdSize(adSize);
        requestedView.setVisibility(View.INVISIBLE);
        final boolean[] wasAttached = {false};
        requestedView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                wasAttached[0] = true;
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                if (wasAttached[0] && bannerAdView == requestedView && !isFinishing() && !isDestroyed()) {
                    Log.i(ADS_TAG, "Displayed banner removed; scheduling rate-limited replacement");
                    destroyBannerAd(true);
                    scheduleBannerReplacement();
                }
            }
        });
        Log.i(ADS_TAG, "Requesting banner ad");
        requestedView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                if (bannerAdView != requestedView) return;
                if (isFinishing() || isDestroyed()) {
                    destroyBannerAd(false);
                } else if (canRequestAds() && bannerContainer != null && bannerAdView != null) {
                    cancelBannerRetry();
                    bannerRetryCount = 0;
                    recordBannerRequestTime();
                    Log.i(ADS_TAG, "Banner ad loaded; responseInfo=" + requestedView.getResponseInfo());
                    if (bannerPlaceholder != null) bannerPlaceholder.setVisibility(View.GONE);
                    requestedView.setVisibility(View.VISIBLE);
                    bannerContainer.setVisibility(View.VISIBLE);
                } else {
                    destroyBannerAd(true);
                }
            }

            @Override
            public void onAdClosed() {
                if (bannerAdView != requestedView) return;
                Log.i(ADS_TAG, "Banner closed; requesting a replacement after the minimum interval");
                destroyBannerAd(true);
                scheduleBannerReplacement();
            }

            @Override
            public void onAdImpression() {
                Log.i(ADS_TAG, "Banner impression recorded");
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                logAdLoadFailure("Banner", error);
                if (bannerAdView != requestedView) return;
                recordBannerRequestTime();
                if (isFinishing() || isDestroyed()) return;
                if (!canRequestAds()) {
                    showBannerPlaceholder("مساحة إعلان · بانتظار الموافقة");
                    destroyBannerAd(true);
                    return;
                }
                showBannerPlaceholder("مساحة إعلان · لا يتوفر إعلان حاليًا");
                destroyBannerAd(true);
                scheduleBannerRetry();
            }
        });
        bannerContainer.addView(requestedView,
                new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        recordBannerRequestTime();
        requestedView.loadAd(new AdRequest.Builder().build());
    }

    private void showBannerPlaceholder(String label) {
        if (bannerPlaceholder != null) {
            bannerPlaceholder.setText(label);
            bannerPlaceholder.setVisibility(View.VISIBLE);
        }
        if (bannerContainer != null) bannerContainer.setVisibility(View.VISIBLE);
    }

    private void destroyBannerAd(boolean keepReservedSlot) {
        AdView oldBannerAdView = bannerAdView;
        bannerAdView = null;
        if (oldBannerAdView != null) {
            android.view.ViewParent parent = oldBannerAdView.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(oldBannerAdView);
            oldBannerAdView.destroy();
        }
        if (bannerContainer != null) {
            bannerContainer.removeAllViews();
            if (keepReservedSlot && bannerPlaceholder != null) {
                bannerPlaceholder.setVisibility(View.VISIBLE);
                bannerContainer.addView(bannerPlaceholder,
                        new FrameLayout.LayoutParams(-1, dp(50), Gravity.CENTER));
                bannerContainer.setVisibility(View.VISIBLE);
            } else {
                bannerContainer.setVisibility(View.GONE);
            }
        }
    }

    private void ensureNativeAdCard() {
        if (nativeAdCard == null) {
            nativeAdCard = new FrameLayout(this);
            nativeAdCard.setBackground(roundedSolid(PANEL, dp(18)));
            nativeAdCard.setClipToPadding(false);
            nativeAdCard.setClipChildren(false);
            applyElectricGlow(nativeAdCard, 2, false);
        }
        if (activeNativeAd == null && nativeAdPlaceholder == null) {
            nativeAdPlaceholder = text("مساحة إعلان أصلي\nيظهر الإعلان هنا عند توفره", 13, MUTED, true);
            nativeAdPlaceholder.setGravity(Gravity.CENTER);
            nativeAdPlaceholder.setPadding(dp(14), dp(14), dp(14), dp(14));
            GradientDrawable nativeSlotBackground = roundedSolid(Color.rgb(16, 24, 37), dp(14));
            nativeSlotBackground.setStroke(dp(1), 0x7748A8FF);
            nativeAdPlaceholder.setBackground(nativeSlotBackground);
            nativeAdPlaceholder.setContentDescription("موضع مخصص للإعلان الأصلي");
            nativeAdCard.addView(nativeAdPlaceholder, new FrameLayout.LayoutParams(-1, dp(92)));
        }
        nativeAdCard.setVisibility(View.VISIBLE);
    }

    private void setNativeAdPlaceholder(String label) {
        if (nativeAdCard == null) return;
        if (activeNativeAd == null && nativeAdPlaceholder == null) ensureNativeAdCard();
        if (nativeAdPlaceholder != null) nativeAdPlaceholder.setText(label);
        nativeAdCard.setVisibility(View.VISIBLE);
    }

    private void loadNativeAdIfNeeded() {
        if (!adsInitialized || !canRequestAds() || nativeAdCard == null || cardsContainer == null
                || nativeAdCard.getParent() != cardsContainer
                || activeNativeAd != null || nativeAdLoading || nativeAdAttempted
                || nativeRetryRunnable != null) return;
        nativeAdLoading = true;
        nativeAdAttempted = true;
        setNativeAdPlaceholder("مساحة إعلان أصلي · جارٍ التحميل");
        Log.i(ADS_TAG, "Requesting native ad");
        AdLoader loader = new AdLoader.Builder(this, NATIVE_AD_UNIT_ID)
                .forNativeAd(ad -> {
                    nativeAdLoading = false;
                    if (!canRequestAds() || isFinishing() || isDestroyed() || nativeAdCard == null
                            || cardsContainer == null || nativeAdCard.getParent() != cardsContainer) {
                        ad.destroy();
                        nativeAdAttempted = false;
                        return;
                    }
                    cancelNativeRetry();
                    nativeRetryCount = 0;
                    Log.i(ADS_TAG, "Native ad loaded; responseInfo=" + ad.getResponseInfo());
                    nativeAdCard.removeAllViews();
                    nativeAdPlaceholder = null;
                    activeNativeAd = ad;
                    nativeAdCard.addView(createNativeAdView(ad), new FrameLayout.LayoutParams(-1, -2));
                    nativeAdCard.setVisibility(View.VISIBLE);
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdImpression() {
                        Log.i(ADS_TAG, "Native impression recorded");
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        nativeAdLoading = false;
                        nativeAdAttempted = false;
                        logAdLoadFailure("Native", error);
                        if (!canRequestAds()) {
                            setNativeAdPlaceholder("مساحة إعلان أصلي · بانتظار الموافقة");
                            return;
                        }
                        if (nativeAdCard == null || cardsContainer == null
                                || nativeAdCard.getParent() != cardsContainer) {
                            Log.i(ADS_TAG, "Native slot no longer needs an ad; retry skipped");
                            return;
                        }
                        setNativeAdPlaceholder("مساحة إعلان أصلي · لا يتوفر إعلان حاليًا");
                        scheduleNativeRetry();
                    }
                })
                .build();
        loader.loadAd(new AdRequest.Builder().build());
    }

    private NativeAdView createNativeAdView(NativeAd ad) {
        NativeAdView adView = new NativeAdView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(14), dp(12), dp(14), dp(14));
        content.setBackground(roundedSolid(Color.rgb(12, 18, 29), dp(18)));

        LinearLayout attributionRow = new LinearLayout(this);
        attributionRow.setGravity(Gravity.CENTER_VERTICAL);
        attributionRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView attribution = text("إعلان", 11, Color.rgb(135, 202, 255), true);
        attribution.setPadding(dp(9), dp(4), dp(9), dp(4));
        attribution.setBackground(roundedSolid(Color.rgb(20, 47, 76), dp(8)));
        attributionRow.addView(attribution, new LinearLayout.LayoutParams(-2, -2));
        TextView advertiser = text(ad.getAdvertiser() == null ? "" : ad.getAdvertiser(), 12, MUTED, false);
        LinearLayout.LayoutParams advertiserParams = new LinearLayout.LayoutParams(0, -2, 1);
        advertiserParams.setMarginStart(dp(8));
        attributionRow.addView(advertiser, advertiserParams);
        if (ad.getAdvertiser() == null || ad.getAdvertiser().trim().isEmpty()) advertiser.setVisibility(View.GONE);
        content.addView(attributionRow, new LinearLayout.LayoutParams(-1, -2));
        adView.setAdvertiserView(advertiser);

        LinearLayout headlineRow = new LinearLayout(this);
        headlineRow.setGravity(Gravity.CENTER_VERTICAL);
        headlineRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        NativeAd.Image adIcon = ad.getIcon();
        if (adIcon != null && adIcon.getDrawable() != null) {
            icon.setImageDrawable(adIcon.getDrawable());
        } else {
            icon.setVisibility(View.GONE);
        }
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconParams.setMarginEnd(dp(10));
        headlineRow.addView(icon, iconParams);
        TextView headline = text(ad.getHeadline() == null ? "" : ad.getHeadline(), 17, Color.WHITE, true);
        headline.setMaxLines(3);
        headline.setSingleLine(false);
        headline.setEllipsize(null);
        headlineRow.addView(headline, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(-1, -2);
        headlineParams.topMargin = dp(10);
        content.addView(headlineRow, headlineParams);
        adView.setIconView(icon);
        adView.setHeadlineView(headline);

        MediaView mediaView = new MediaView(this);
        mediaView.setBackground(roundedSolid(Color.rgb(24, 32, 44), dp(12)));
        adView.setMediaView(mediaView);
        if (ad.getMediaContent() == null) {
            mediaView.setVisibility(View.GONE);
        } else {
            LinearLayout.LayoutParams mediaParams = new LinearLayout.LayoutParams(-1, dp(112));
            mediaParams.topMargin = dp(10);
            content.addView(mediaView, mediaParams);
        }

        TextView body = text(ad.getBody() == null ? "" : ad.getBody(), 13, MUTED, false);
        body.setMaxLines(4);
        body.setSingleLine(false);
        body.setEllipsize(null);
        if (ad.getBody() == null || ad.getBody().trim().isEmpty()) body.setVisibility(View.GONE);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(8);
        content.addView(body, bodyParams);
        adView.setBodyView(body);

        TextView callToAction = text(ad.getCallToAction() == null ? "" : ad.getCallToAction(), 14, Color.WHITE, true);
        callToAction.setGravity(Gravity.CENTER);
        callToAction.setPadding(dp(14), dp(10), dp(14), dp(10));
        callToAction.setBackground(roundedGradient(BLUE, Color.rgb(20, 102, 210), dp(11)));
        if (ad.getCallToAction() == null || ad.getCallToAction().trim().isEmpty()) {
            callToAction.setVisibility(View.GONE);
        } else {
            LinearLayout.LayoutParams ctaParams = new LinearLayout.LayoutParams(-1, -2);
            ctaParams.topMargin = dp(10);
            content.addView(callToAction, ctaParams);
        }
        adView.setCallToActionView(callToAction);
        adView.addView(content, new FrameLayout.LayoutParams(-1, -2));
        adView.setNativeAd(ad);
        return adView;
    }

    private void destroyActiveNativeAd(boolean keepReservedSlot) {
        boolean hadActiveNativeAd = activeNativeAd != null;
        if (hadActiveNativeAd) {
            activeNativeAd.destroy();
            activeNativeAd = null;
            nativeAdAttempted = false;
            cancelNativeRetry();
        }
        if (nativeAdCard != null) {
            nativeAdCard.removeAllViews();
            nativeAdPlaceholder = null;
            if (keepReservedSlot) {
                ensureNativeAdCard();
                if (hadActiveNativeAd && adsInitialized && canRequestAds() && cardsContainer != null
                        && nativeAdCard.getParent() == cardsContainer) {
                    loadNativeAdIfNeeded();
                }
            }
            else nativeAdCard.setVisibility(View.GONE);
        }
    }

    private void loadInterstitialAd() {
        if (!adsInitialized || !canRequestAds() || interstitialAd != null || interstitialLoading
                || interstitialShowing || isFinishing() || isDestroyed()) return;
        interstitialLoading = true;
        InterstitialAd.load(this,
                INTERSTITIAL_AD_UNIT_ID,
                new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialLoading = false;
                        if (!canRequestAds() || isFinishing() || isDestroyed()) return;
                        Log.i(ADS_TAG, "Interstitial ad loaded; responseInfo=" + ad.getResponseInfo());
                        interstitialAd = ad;
                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdShowedFullScreenContent() {
                                interstitialShowing = true;
                                Log.i(ADS_TAG, "Interstitial displayed; replacement requests paused");
                            }

                            @Override
                            public void onAdImpression() {
                                Log.i(ADS_TAG, "Interstitial impression recorded");
                            }

                            @Override
                            public void onAdDismissedFullScreenContent() {
                                interstitialShowing = false;
                                interstitialAd = null;
                                loadInterstitialAd();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError error) {
                                Log.w(ADS_TAG, "Interstitial failed to show: " + error.getMessage());
                                interstitialShowing = false;
                                interstitialAd = null;
                                loadInterstitialAd();
                            }
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        interstitialLoading = false;
                        interstitialAd = null;
                        logAdLoadFailure("Interstitial", error);
                    }
                });
    }

    private void showInterstitialAtNaturalBreak() {
        if (!canRequestAds()) return;
        int closeCount = getSharedPreferences("daftri_admob", MODE_PRIVATE)
                .getInt("details_close_count", 0) + 1;
        getSharedPreferences("daftri_admob", MODE_PRIVATE).edit()
                .putInt("details_close_count", closeCount).apply();
        if (closeCount % INTERSTITIAL_EVERY_DETAILS_CLOSES != 0) return;
        if (interstitialAd == null) {
            loadInterstitialAd();
            return;
        }
        InterstitialAd readyAd = interstitialAd;
        interstitialAd = null;
        Log.i(ADS_TAG, "Showing interstitial after details close " + closeCount);
        interstitialShowing = true;
        readyAd.show(this);
    }

    private void addNotebookCard(LinearLayout row, Notebook notebook) {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable cardBackground = roundedGradient(notebook.color, darken(notebook.color), dp(20));
        cardBackground.setStroke(dp(1), 0x8848A8FF);
        card.setBackground(cardBackground);
        applyElectricGlow(card, 4, false);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            if (selectingNotebooks) toggleNotebookSelection(notebook);
            else showDetails(notebook);
        });
        card.setContentDescription("" + notebook.name + "؛ اضغط لفتحها أو اضغط مطولًا لتحديدها");
        card.setOnLongClickListener(v -> {
            if (!selectingNotebooks) {
                selectingNotebooks = true;
                selectedNotebookIds.clear();
            }
            if (!selectedNotebookIds.contains(notebook.id)) selectedNotebookIds.add(notebook.id);
            updateSelectionToolbar();
            renderCards();
            return true;
        });
        boolean selected = selectedNotebookIds.contains(notebook.id);
        if (selected) cardBackground.setStroke(dp(3), 0xFFB5E2FF);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, dp(176), 1);
        cardParams.setMargins(dp(5), dp(4), dp(5), dp(4));
        row.addView(card, cardParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(18), dp(16), dp(18), dp(8));
        TextView name = text(notebook.name, 19, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        TextView count = text(taskCount(notebook.tasks.size()), 13, Color.rgb(222, 236, 255), false);
        count.setGravity(Gravity.CENTER);
        count.setPadding(0, dp(9), 0, 0);
        info.addView(name, new LinearLayout.LayoutParams(-1, -2));
        if (notebook.titleImageFileName != null) {
            Bitmap titleThumbnail = loadTaskThumbnail(notebook.titleImageFileName);
            if (titleThumbnail != null) {
                ImageView titleImage = new ImageView(this);
                titleImage.setImageBitmap(titleThumbnail);
                titleImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams titleImageParams = new LinearLayout.LayoutParams(dp(42), dp(42));
                titleImageParams.topMargin = dp(4);
                info.addView(titleImage, titleImageParams);
            }
        }
        info.addView(count, new LinearLayout.LayoutParams(-1, -2));
        TextView createdDate = text("أُنشئ · " + formatLocalDate(notebook.createdAtMs), 10,
                Color.rgb(222, 236, 255), false);
        createdDate.setGravity(Gravity.CENTER);
        createdDate.setMaxLines(2);
        createdDate.setPadding(0, dp(4), 0, 0);
        info.addView(createdDate, new LinearLayout.LayoutParams(-1, -2));
        card.addView(info, new FrameLayout.LayoutParams(-1, -1));

        if (selectingNotebooks) {
            TextView check = text(selected ? "✓" : "○", 22, Color.WHITE, true);
            check.setGravity(Gravity.CENTER);
            check.setBackground(roundedSolid(selected ? 0xFF168EFF : 0x66303D50, dp(18)));
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(dp(38), dp(38),
                    Gravity.TOP | Gravity.START);
            card.addView(check, checkParams);
        }

        TextView more = text("⋮", 25, Color.WHITE, true);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("خيارات الدفتر");
        more.setBackground(rippleBackground(0x22FFFFFF, dp(18)));
        applyElectricGlow(more, 1, true);
        FrameLayout.LayoutParams moreParams = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP | Gravity.END);
        card.addView(more, moreParams);
        more.setOnClickListener(v -> showNotebookMenu(more, notebook));
    }

    private void showNotebookMenu(View anchor, Notebook notebook) {
        if (!storageHealthy) {
            Toast.makeText(this, "التخزين غير متاح؛ التعديل متوقف لحماية البيانات", Toast.LENGTH_LONG).show();
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("تعديل الدفتر").setOnMenuItemClickListener(item -> {
            showEditor(notebook);
            return true;
        });
        menu.getMenu().add("حذف الدفتر").setOnMenuItemClickListener(item -> {
            confirmDeleteNotebook(notebook);
            return true;
        });
        menu.show();
    }

    private void showCreationDatePicker() {
        Calendar today = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, dayOfMonth) ->
                showCreationDateResults(year, month, dayOfMonth),
                today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }

    private void renderCalendarDestination() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        page.setPadding(dp(2), dp(14), dp(2), dp(14));
        TextView intro = text("اختَر يومًا لعرض الدفاتر والمهام التي أُنشئت فيه.",
                14, MUTED, false);
        intro.setGravity(Gravity.CENTER);
        intro.setPadding(dp(8), dp(2), dp(8), dp(12));
        page.addView(intro, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout dateCard = new LinearLayout(this);
        dateCard.setOrientation(LinearLayout.VERTICAL);
        dateCard.setGravity(Gravity.CENTER_HORIZONTAL);
        dateCard.setPadding(dp(10), dp(10), dp(10), dp(12));
        dateCard.setBackground(roundedStroke(Color.rgb(12, 20, 32), 0x8848A8FF, dp(18)));
        Calendar today = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
        int[] selectedDate = {
                today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)
        };
        TextView selectedLabel = text("التاريخ المحدد: " + formatLocalDate(today.getTimeInMillis()),
                15, Color.WHITE, true);
        selectedLabel.setGravity(Gravity.CENTER);
        selectedLabel.setPadding(0, 0, 0, dp(6));
        dateCard.addView(selectedLabel, new LinearLayout.LayoutParams(-1, -2));

        DatePicker datePicker = new DatePicker(this);
        datePicker.init(selectedDate[0], selectedDate[1], selectedDate[2], (picker, year, month, day) -> {
            selectedDate[0] = year;
            selectedDate[1] = month;
            selectedDate[2] = day;
            Calendar localDate = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
            localDate.clear();
            localDate.set(year, month, day, 12, 0, 0);
            selectedLabel.setText("التاريخ المحدد: " + formatLocalDate(localDate.getTimeInMillis()));
        });
        dateCard.addView(datePicker, new LinearLayout.LayoutParams(-1, -2));

        TextView showDate = actionButton("عرض الدفاتر والمهام لهذا اليوم", true);
        LinearLayout.LayoutParams showDateParams = new LinearLayout.LayoutParams(-1, dp(48));
        showDateParams.topMargin = dp(8);
        dateCard.addView(showDate, showDateParams);
        showDate.setOnClickListener(v -> showCreationDateResults(
                selectedDate[0], selectedDate[1], selectedDate[2]));
        page.addView(dateCard, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        contentHost.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showCreationDateResults(int year, int month, int dayOfMonth) {
        TimeZone localTimeZone = TimeZone.getDefault();
        Calendar selectedDate = Calendar.getInstance(localTimeZone, Locale.getDefault());
        selectedDate.clear();
        selectedDate.set(year, month, dayOfMonth, 12, 0, 0);
        String localDateLabel = formatLocalDate(selectedDate.getTimeInMillis());

        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel();
        TextView heading = text("إنشاءات يوم " + localDateLabel, 19, Color.WHITE, true);
        heading.setPadding(0, 0, 0, dp(6));
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text("تُعرض البطاقات والمهام بحسب تاريخ إنشائها المحلي.", 12, MUTED, false);
        hint.setPadding(0, 0, 0, dp(8));
        panel.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(2), 0, dp(4));
        scroll.addView(results, new ScrollView.LayoutParams(-1, -2));

        addCreationResultsSection(results, "بطاقات الدفاتر المُنشأة في هذا اليوم");
        int notebookCount = 0;
        for (Notebook notebook : notebooks) {
            if (!LocalCreationDateQuery.matches(notebook.createdAtMs, year, month, dayOfMonth, localTimeZone)) {
                continue;
            }
            notebookCount++;
            LinearLayout entry = new LinearLayout(this);
            entry.setOrientation(LinearLayout.VERTICAL);
            entry.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            entry.setPadding(dp(13), dp(10), dp(13), dp(10));
            entry.setBackground(roundedStroke(Color.rgb(24, 43, 65), 0xAA48A8FF, dp(12)));
            TextView name = text(notebook.name, 16, Color.WHITE, true);
            entry.addView(name, new LinearLayout.LayoutParams(-1, -2));
            TextView created = text("بطاقة دفتر · " + formatLocalDate(notebook.createdAtMs), 11, MUTED, false);
            created.setPadding(0, dp(3), 0, 0);
            entry.addView(created, new LinearLayout.LayoutParams(-1, -2));
            addCreationResultSpacing(results, entry);
        }
        if (notebookCount == 0) addCreationResultsEmpty(results, "لا توجد بطاقات دفاتر أُنشئت في هذا اليوم");

        addCreationResultsSection(results, "المهام المُنشأة في هذا اليوم");
        int taskCount = 0;
        for (Notebook notebook : notebooks) {
            ArrayList<TaskEntry> matchingTasks = new ArrayList<>();
            for (TaskEntry task : notebook.tasks) {
                if (LocalCreationDateQuery.matches(task.createdAtMs, year, month, dayOfMonth, localTimeZone)) {
                    matchingTasks.add(task);
                }
            }
            if (matchingTasks.isEmpty()) continue;
            taskCount += matchingTasks.size();
            LinearLayout group = new LinearLayout(this);
            group.setOrientation(LinearLayout.VERTICAL);
            group.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            group.setPadding(dp(12), dp(9), dp(12), dp(5));
            group.setBackground(roundedStroke(Color.rgb(15, 26, 39), 0x7748A8FF, dp(12)));
            TextView groupTitle = text("داخل الدفتر: " + notebook.name, 14,
                    Color.rgb(135, 202, 255), true);
            groupTitle.setPadding(0, 0, 0, dp(6));
            group.addView(groupTitle, new LinearLayout.LayoutParams(-1, -2));
            for (TaskEntry task : matchingTasks) {
                LinearLayout taskEntry = new LinearLayout(this);
                taskEntry.setOrientation(LinearLayout.VERTICAL);
                taskEntry.setPadding(dp(10), dp(8), dp(10), dp(8));
                taskEntry.setBackground(roundedSolid(Color.rgb(25, 35, 52), dp(9)));
                TextView taskText = text("•  " + task.text, 15, Color.rgb(235, 242, 252), false);
                taskText.setGravity(Gravity.TOP | Gravity.END);
                taskText.setSingleLine(false);
                taskText.setMaxLines(Integer.MAX_VALUE);
                taskEntry.addView(taskText, new LinearLayout.LayoutParams(-1, -2));
                TextView taskDate = text("مهمة · " + formatLocalDate(task.createdAtMs), 11, MUTED, false);
                taskDate.setPadding(0, dp(3), 0, 0);
                taskEntry.addView(taskDate, new LinearLayout.LayoutParams(-1, -2));
                LinearLayout.LayoutParams taskParams = new LinearLayout.LayoutParams(-1, -2);
                taskParams.bottomMargin = dp(6);
                group.addView(taskEntry, taskParams);
            }
            addCreationResultSpacing(results, group);
        }
        if (taskCount == 0) addCreationResultsEmpty(results, "لا توجد مهام أُنشئت في هذا اليوم");

        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(370)));
        TextView close = actionButton("إغلاق", true);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(46));
        closeParams.topMargin = dp(10);
        panel.addView(close, closeParams);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(panel);
        configureDialog(dialog);
        dialog.show();
        sizeDialog(dialog);
    }

    private void addCreationResultsSection(LinearLayout results, String title) {
        TextView section = text(title, 14, Color.rgb(135, 202, 255), true);
        section.setPadding(dp(2), dp(10), dp(2), dp(7));
        results.addView(section, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addCreationResultsEmpty(LinearLayout results, String message) {
        TextView empty = text(message, 13, MUTED, false);
        empty.setPadding(dp(8), dp(3), dp(8), dp(7));
        results.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addCreationResultSpacing(LinearLayout results, View entry) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(7);
        results.addView(entry, params);
    }

    private void renderTrashDestination() {
        boolean trashLoaded;
        synchronized (TrashCleanupWorker.DATA_LOCK) {
            trashLoaded = loadTrashFromPreferences();
            if (trashLoaded) {
                if (purgeExpiredTrash()) saveNotebooks();
                else scheduleTrashCleanup();
            }
        }
        if (!trashLoaded) {
            TextView error = text("تعذر قراءة سلة المحذوفات. لم تُحذف محتوياتها، وأُوقفت التعديلات لحماية بياناتك.",
                    15, MUTED, false);
            error.setGravity(Gravity.CENTER);
            error.setPadding(dp(20), dp(30), dp(20), dp(30));
            contentHost.addView(error, new LinearLayout.LayoutParams(-1, -1));
            return;
        }

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        page.setPadding(0, dp(10), 0, dp(8));
        TextView hint = text("تُحذف الدفاتر نهائيًا بعد ٣٠ يومًا من نقلها إلى السلة.", 13, MUTED, false);
        hint.setPadding(dp(4), 0, dp(4), dp(10));
        page.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(2), dp(2), dp(2), dp(8));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        if (trashedNotebooks.isEmpty()) {
            TextView empty = text("السلة فارغة", 16, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
        } else {
            for (TrashedNotebook entry : new ArrayList<>(trashedNotebooks)) {
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(13), dp(12), dp(13), dp(12));
                item.setBackground(roundedStroke(Color.rgb(18, 27, 40), 0x77547BA5, dp(15)));
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
                itemParams.bottomMargin = dp(9);
                list.addView(item, itemParams);
                TextView name = text(entry.notebook.name, 17, Color.WHITE, true);
                item.addView(name, new LinearLayout.LayoutParams(-1, -2));
                TextView dates = text("نُقل إلى السلة: " + formatLocalDate(entry.deletedAtMs)
                                + "\nيُحذف نهائيًا: "
                                + formatLocalDate(TrashCleanupWorker.expiryTime(entry.deletedAtMs)),
                        12, MUTED, false);
                dates.setPadding(0, dp(5), 0, dp(10));
                item.addView(dates, new LinearLayout.LayoutParams(-1, -2));

                LinearLayout actions = new LinearLayout(this);
                actions.setGravity(Gravity.CENTER_VERTICAL);
                actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                TextView restore = actionButton("استعادة", true);
                TextView deleteForever = actionButton("حذف نهائي", false);
                LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(42), 1);
                actionParams.setMargins(dp(3), 0, dp(3), 0);
                actions.addView(restore, actionParams);
                actions.addView(deleteForever, new LinearLayout.LayoutParams(0, dp(42), 1));
                item.addView(actions, new LinearLayout.LayoutParams(-1, -2));

                restore.setEnabled(storageHealthy);
                deleteForever.setEnabled(storageHealthy);
                restore.setOnClickListener(v -> {
                    if (System.currentTimeMillis() >= TrashCleanupWorker.expiryTime(entry.deletedAtMs)) {
                        trashedNotebooks.remove(entry);
                        saveNotebooks();
                        renderDestinationContent();
                        Toast.makeText(this, "انتهت مدة الاحتفاظ بهذا الدفتر", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    trashedNotebooks.remove(entry);
                    notebooks.add(entry.notebook);
                    saveNotebooks();
                    renderDestinationContent();
                });
                deleteForever.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("حذف نهائي")
                        .setMessage("سيُحذف الدفتر وملفاته نهائيًا ولا يمكن استعادته.")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("حذف نهائيًا", (dialog, which) -> {
                            trashedNotebooks.remove(entry);
                            saveNotebooks();
                            cleanupUnusedTaskImages();
                            renderDestinationContent();
                        }).show());
            }
        }
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        contentHost.addView(page, new LinearLayout.LayoutParams(-1, -1));
    }

    private void showDetails(Notebook notebook) {
        Dialog dialog = new Dialog(this);
        detailsDialog = dialog;
        LinearLayout panel = dialogPanel();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ImageView brand = brandMark(34);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        brandParams.setMarginEnd(dp(8));
        header.addView(brand, brandParams);
        TextView name = text(notebook.name, 21, Color.WHITE, true);
        name.setMaxLines(2);
        header.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (notebook.titleImageFileName != null) {
            Bitmap titleThumbnail = loadTaskThumbnail(notebook.titleImageFileName);
            if (titleThumbnail != null) {
                ImageView titleImage = new ImageView(this);
                titleImage.setImageBitmap(titleThumbnail);
                titleImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams titleImageParams = new LinearLayout.LayoutParams(dp(52), dp(52));
                titleImageParams.setMargins(dp(6), 0, dp(6), 0);
                header.addView(titleImage, titleImageParams);
            }
        }
        TextView more = text("⋮", 26, Color.WHITE, true);
        more.setGravity(Gravity.CENTER);
        more.setBackground(rippleBackground(0x22FFFFFF, dp(20)));
        applyElectricGlow(more, 1, true);
        header.addView(more, new LinearLayout.LayoutParams(dp(42), dp(42)));
        more.setOnClickListener(v -> showNotebookMenu(more, notebook));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
        TextView count = text(taskCount(notebook.tasks.size()), 13, MUTED, false);
        count.setPadding(0, dp(3), 0, dp(10));
        panel.addView(count);
        TextView notebookDate = text("تاريخ إنشاء الدفتر: " + formatLocalDate(notebook.createdAtMs),
                12, MUTED, false);
        notebookDate.setPadding(0, 0, 0, dp(8));
        panel.addView(notebookDate);
        TextView reorderHint = text("اضغط ↔ على مهمتين لتبديل مكانيهما", 12, MUTED, false);
        reorderHint.setPadding(0, 0, 0, dp(6));
        panel.addView(reorderHint);

        ScrollView scroll = new ScrollView(this);
        LinearLayout taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        taskList.setPadding(0, dp(5), 0, dp(5));
        SwapSelection swapSelection = new SwapSelection();
        if (notebook.tasks.isEmpty()) {
            TextView empty = text("لا توجد مهام بعد", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            taskList.addView(empty, new LinearLayout.LayoutParams(-1, dp(80)));
        } else {
            for (int i = 0; i < notebook.tasks.size(); i++) {
                TaskEntry taskEntry = notebook.tasks.get(i);
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(12), dp(8), dp(12), dp(8));
                item.setBackground(roundedSolid(Color.rgb(25, 35, 52), dp(12)));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                TextView task = text("•  " + taskEntry.text, 16, Color.rgb(235, 242, 252), false);
                task.setSingleLine(false);
                task.setMaxLines(Integer.MAX_VALUE);
                task.setEllipsize(null);
                task.setHorizontallyScrolling(false);
                task.setGravity(Gravity.TOP | Gravity.END);
                row.addView(task, new LinearLayout.LayoutParams(0, -2, 1));
                TextView swapHandle = swapHandle();
                row.addView(swapHandle, new LinearLayout.LayoutParams(dp(38), dp(42)));
                item.addView(row, new LinearLayout.LayoutParams(-1, -2));
                TextView taskDate = text("تاريخ إنشاء المهمة: " + formatLocalDate(taskEntry.createdAtMs),
                        11, MUTED, false);
                taskDate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                taskDate.setPadding(0, dp(3), 0, dp(4));
                item.addView(taskDate, new LinearLayout.LayoutParams(-1, -2));
                item.setTag(taskEntry);
                HorizontalScrollView imageScroll = new HorizontalScrollView(this);
                imageScroll.setHorizontalScrollBarEnabled(false);
                LinearLayout imageStrip = new LinearLayout(this);
                imageStrip.setOrientation(LinearLayout.HORIZONTAL);
                imageScroll.addView(imageStrip, new HorizontalScrollView.LayoutParams(-2, -2));
                item.addView(imageScroll, new LinearLayout.LayoutParams(-1, -2));
                refreshTaskImageStrip(taskEntry, imageStrip, false);
                LinearLayout.LayoutParams taskParams = new LinearLayout.LayoutParams(-1, -2);
                taskParams.bottomMargin = dp(8);
                taskList.addView(item, taskParams);
                enableTapToSwap(taskList, item, swapHandle, swapSelection, () -> {
                    notebook.tasks.clear();
                    for (int taskIndex = 0; taskIndex < taskList.getChildCount(); taskIndex++) {
                        notebook.tasks.add((TaskEntry) taskList.getChildAt(taskIndex).getTag());
                    }
                    saveNotebooks();
                });
            }
        }
        scroll.addView(taskList);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(280)));
        TextView close = actionButton("تم", true);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(48));
        closeParams.topMargin = dp(14);
        panel.addView(close, closeParams);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(ignored -> {
            if (!isFinishing() && !isDestroyed()) showInterstitialAtNaturalBreak();
        });
        dialog.setContentView(panel);
        configureDialog(dialog);
        dialog.show();
        sizeDialog(dialog);
    }

    private void showEditor(Notebook existing) {
        if (!storageHealthy) return;
        final boolean isNew = existing == null;
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel();
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        ScrollView editorScroll = new ScrollView(this);
        editorScroll.setFillViewport(false);
        editorScroll.setClipToPadding(false);
        LinearLayout editorContent = new LinearLayout(this);
        editorContent.setOrientation(LinearLayout.VERTICAL);
        editorContent.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        editorContent.setClipToPadding(false);
        editorScroll.addView(editorContent, new ScrollView.LayoutParams(-1, -2));
        panel.addView(editorScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ImageView brand = brandMark(34);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        brandParams.setMarginEnd(dp(9));
        heading.addView(brand, brandParams);
        TextView headingText = text(isNew ? "دفتر جديد" : "تعديل الدفتر", 22, Color.WHITE, true);
        heading.addView(headingText);
        heading.setPadding(0, 0, 0, dp(14));
        editorContent.addView(heading);

        EditText nameInput = editField("اسم الدفتر");
        if (!isNew) nameInput.setText(existing.name);
        editorContent.addView(nameInput, new LinearLayout.LayoutParams(-1, dp(52)));

        TitleImageState titleImageState = new TitleImageState(isNew ? null : existing.titleImageFileName);
        TextView titleImageLabel = text("صورة العنوان (اختياري)", 12, MUTED, false);
        titleImageLabel.setPadding(0, dp(8), 0, dp(3));
        editorContent.addView(titleImageLabel);
        LinearLayout titleImageControls = new LinearLayout(this);
        titleImageControls.setGravity(Gravity.CENTER_VERTICAL);
        titleImageControls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ImageView titlePreview = new ImageView(this);
        titlePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        titlePreview.setBackground(roundedSolid(Color.rgb(25, 35, 52), dp(10)));
        titleImageControls.addView(titlePreview, new LinearLayout.LayoutParams(dp(52), dp(52)));
        titleImageState.preview = titlePreview;
        TextView titleImageMenu = text("⋮", 24, Color.rgb(93, 184, 255), true);
        titleImageMenu.setGravity(Gravity.CENTER);
        titleImageMenu.setContentDescription("خيارات صورة العنوان");
        titleImageMenu.setBackground(rippleBackground(0x223E78B8, dp(12)));
        applyElectricGlow(titleImageMenu, 1, true);
        LinearLayout.LayoutParams titleMenuParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        titleMenuParams.setMargins(dp(8), 0, 0, 0);
        titleImageControls.addView(titleImageMenu, titleMenuParams);
        editorContent.addView(titleImageControls, new LinearLayout.LayoutParams(-1, dp(56)));
        refreshTitleImagePreview(titleImageState);
        titleImageMenu.setOnClickListener(v -> showTitleImageMenu(titleImageMenu, titleImageState));

        TextView tasksLabel = text("المهام — أدوات كل مهمة أسفل حقل الكتابة", 14, MUTED, true);
        tasksLabel.setPadding(0, dp(15), 0, dp(7));
        editorContent.addView(tasksLabel);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        editorContent.addView(rows, new LinearLayout.LayoutParams(-1, -2));

        SwapSelection swapSelection = new SwapSelection();
        if (!isNew && !existing.tasks.isEmpty()) {
            for (TaskEntry task : existing.tasks) {
                addTaskRowAt(rows, swapSelection, task, rows.getChildCount());
            }
        } else {
            addTaskRowAt(rows, swapSelection, new TaskEntry("", null), 0);
        }
        TextView addTask = actionButton("＋  إضافة مهمة", false);
        LinearLayout.LayoutParams addTaskParams = new LinearLayout.LayoutParams(-1, dp(46));
        addTaskParams.topMargin = dp(4);
        editorContent.addView(addTask, addTaskParams);
        addTask.setOnClickListener(v -> {
            addTaskRowAt(rows, swapSelection, new TaskEntry("", null), rows.getChildCount());
            editorScroll.post(() -> editorScroll.smoothScrollTo(0, editorContent.getBottom()));
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView save = actionButton("حفظ", true);
        TextView cancel = actionButton("إلغاء", false);
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        controlParams.topMargin = dp(15);
        controlParams.setMargins(dp(4), dp(15), dp(4), 0);
        controls.addView(save, controlParams);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        cancelParams.setMargins(dp(4), dp(15), dp(4), 0);
        controls.addView(cancel, cancelParams);
        panel.addView(controls);

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String notebookName = nameInput.getText().toString().trim();
            if (notebookName.isEmpty()) {
                nameInput.setError("اكتب اسم الدفتر");
                return;
            }
            ArrayList<TaskEntry> tasks = new ArrayList<>();
            for (int i = 0; i < rows.getChildCount(); i++) {
                LinearLayout item = (LinearLayout) rows.getChildAt(i);
                TaskEntry task = (TaskEntry) item.getTag();
                LinearLayout row = (LinearLayout) item.getChildAt(0);
                EditText input = (EditText) row.getTag();
                String value = input.getText().toString().trim();
                task.text = value;
                if (!value.isEmpty() || !task.imageFileNames.isEmpty()) tasks.add(task);
            }
            if (tasks.isEmpty()) {
                Toast.makeText(this, "أضف مهمة واحدة على الأقل", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isNew) {
                notebooks.add(new Notebook(UUID.randomUUID().toString(), notebookName, tasks,
                        CARD_COLORS[(int) (Math.random() * CARD_COLORS.length)],
                        titleImageState.imageFileName));
            } else {
                existing.name = notebookName;
                existing.titleImageFileName = titleImageState.imageFileName;
                existing.tasks.clear();
                existing.tasks.addAll(tasks);
            }
            saveNotebooks();
            dialog.dismiss();
            renderCards();
        });

        dialog.setContentView(panel);
        configureDialog(dialog);
        dialog.setOnDismissListener(ignored -> cleanupUnusedTaskImages());
        dialog.show();
        sizeEditorDialog(dialog);
    }

    private void addTaskRowAt(LinearLayout rows, SwapSelection selection, TaskEntry initial, int requestedIndex) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        item.setPadding(dp(12), dp(10), dp(12), dp(8));
        item.setBackground(roundedStroke(Color.rgb(14, 23, 36), 0x774A8BC5, dp(15)));
        TaskEntry taskEntry = initial == null ? new TaskEntry("", null)
                : new TaskEntry(initial.text, null, initial.createdAtMs);
        if (initial != null) taskEntry.imageFileNames.addAll(initial.imageFileNames);

        LinearLayout fieldCard = new LinearLayout(this);
        fieldCard.setOrientation(LinearLayout.VERTICAL);
        TextView taskLabel = text("عنوان/تفاصيل المهمة", 13, Color.rgb(145, 204, 255), true);
        taskLabel.setPadding(dp(2), 0, dp(2), dp(5));
        fieldCard.addView(taskLabel, new LinearLayout.LayoutParams(-1, -2));
        EditText taskInput = editTaskField("اكتب عنوان المهمة أو تفاصيلها هنا");
        taskInput.setText(taskEntry.text);
        fieldCard.setTag(taskInput);
        fieldCard.addView(taskInput, new LinearLayout.LayoutParams(-1, -2));
        item.addView(fieldCard, new LinearLayout.LayoutParams(-1, -2));
        item.setTag(taskEntry);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        buttonParams.setMargins(dp(3), dp(8), dp(3), dp(2));
        TextView swapHandle = swapHandle();
        swapHandle.setText("↔ تبديل");
        swapHandle.setTextSize(12);
        controls.addView(swapHandle, buttonParams);
        TextView imageMenu = actionButton("صور  ⋮", false);
        imageMenu.setTextSize(12);
        imageMenu.setContentDescription("إدارة صور المهمة");
        controls.addView(imageMenu, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView remove = actionButton("حذف", false);
        remove.setTextSize(12);
        controls.addView(remove, new LinearLayout.LayoutParams(0, dp(42), 1));
        item.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        TextView taskDate = text("أُنشئت المهمة: " + formatLocalDate(taskEntry.createdAtMs),
                11, MUTED, false);
        taskDate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        taskDate.setPadding(dp(2), dp(3), dp(2), dp(5));
        item.addView(taskDate, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView imageScroll = new HorizontalScrollView(this);
        imageScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout imageStrip = new LinearLayout(this);
        imageStrip.setOrientation(LinearLayout.HORIZONTAL);
        imageScroll.addView(imageStrip, new HorizontalScrollView.LayoutParams(-2, -2));
        item.addView(imageScroll, new LinearLayout.LayoutParams(-1, -2));
        refreshTaskImageStrip(taskEntry, imageStrip, true);

        TextView insertHere = text("＋ إضافة هنا", 12, Color.rgb(93, 184, 255), true);
        insertHere.setGravity(Gravity.CENTER);
        insertHere.setBackground(roundedStroke(Color.rgb(20, 31, 48), 0xAA258FFF, dp(10)));
        applyElectricGlow(insertHere, 1, true);
        insertHere.setContentDescription("إضافة حقل مهمة بعد هذا الحقل");
        LinearLayout.LayoutParams insertParams = new LinearLayout.LayoutParams(-1, dp(30));
        insertParams.setMargins(dp(42), dp(3), dp(42), dp(7));
        item.addView(insertHere, insertParams);

        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
        int insertIndex = Math.max(0, Math.min(requestedIndex, rows.getChildCount()));
        rows.addView(item, insertIndex, itemParams);
        enableTapToSwap(rows, item, swapHandle, selection, () -> refreshInsertButtons(rows));
        refreshInsertButtons(rows);

        imageMenu.setOnClickListener(v -> showTaskImageMenu(imageMenu, taskEntry, imageStrip));

        insertHere.setOnClickListener(v -> {
            int currentIndex = rows.indexOfChild(item);
            if (currentIndex >= 0) addTaskRowAt(rows, selection,
                    new TaskEntry("", null), currentIndex + 1);
        });
        remove.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("حذف المهمة")
                .setMessage("هل تريد حذف هذه المهمة؟")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حذف", (d, which) -> {
                    if (selection.selectedItem == item) clearSwapSelection(selection);
                    rows.removeView(item);
                    refreshInsertButtons(rows);
                }).show());
    }

    private void showTaskImageMenu(TextView anchor, TaskEntry task, LinearLayout imageStrip) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("إضافة صور للمهمة").setOnMenuItemClickListener(item -> {
            launchTaskImagePicker(task, imageStrip);
            return true;
        });
        if (!task.imageFileNames.isEmpty()) {
            menu.getMenu().add("إزالة كل الصور").setOnMenuItemClickListener(item -> {
                task.imageFileNames.clear();
                refreshTaskImageStrip(task, imageStrip, true);
                return true;
            });
        }
        menu.show();
    }

    private void launchTaskImagePicker(TaskEntry task, LinearLayout imageStrip) {
        int remaining = TASK_IMAGE_LIMIT - task.imageFileNames.size();
        if (remaining <= 0) {
            Toast.makeText(this, "وصلت المهمة إلى الحد الأقصى: ١٠ صور", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingImageTask = task;
        pendingTaskImageStrip = imageStrip;
        try {
            startActivityForResult(createImagePickerIntent(remaining > 1, remaining, false), REQUEST_TASK_IMAGES);
        } catch (Exception exception) {
            clearPendingImageSelection();
            Toast.makeText(this, "تعذر فتح منتقي الصور", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchTitleImagePicker(TitleImageState state) {
        pendingTitleImageState = state;
        try {
            startActivityForResult(createImagePickerIntent(false, 1, true), REQUEST_TITLE_IMAGE);
        } catch (Exception exception) {
            pendingTitleImageState = null;
            Toast.makeText(this, "تعذر فتح منتقي الصور", Toast.LENGTH_SHORT).show();
        }
    }

    private Intent createImagePickerIntent(boolean allowMultiple, int maxImages, boolean titleImage) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
            if (allowMultiple) {
                intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, maxImages);
            }
            return intent;
        }
        intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (allowMultiple) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        String chooserTitle = titleImage ? "اختيار صورة للعنوان"
                : allowMultiple ? "اختيار صور للمهمة" : "اختيار صورة للمهمة";
        return Intent.createChooser(intent, chooserTitle);
    }

    private void showTitleImageMenu(TextView anchor, TitleImageState state) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(state.imageFileName == null ? "اختيار صورة العنوان" : "تغيير صورة العنوان")
                .setOnMenuItemClickListener(item -> {
                    launchTitleImagePicker(state);
                    return true;
                });
        if (state.imageFileName != null) {
            menu.getMenu().add("إزالة صورة العنوان").setOnMenuItemClickListener(item -> {
                state.imageFileName = null;
                refreshTitleImagePreview(state);
                return true;
            });
        }
        menu.show();
    }

    private void refreshTitleImagePreview(TitleImageState state) {
        if (state == null || state.preview == null) return;
        Bitmap thumbnail = loadTaskThumbnail(state.imageFileName);
        if (thumbnail == null) {
            state.preview.setImageDrawable(null);
            state.preview.setVisibility(View.GONE);
        } else {
            state.preview.setImageBitmap(thumbnail);
            state.preview.setVisibility(View.VISIBLE);
        }
    }

    private void refreshTaskImageStrip(TaskEntry task, LinearLayout imageStrip, boolean editable) {
        imageStrip.removeAllViews();
        for (String fileName : task.imageFileNames) {
            Bitmap thumbnail = loadTaskThumbnail(fileName);
            if (thumbnail == null) continue;
            FrameLayout imageFrame = new FrameLayout(this);
            ImageView image = new ImageView(this);
            image.setImageBitmap(thumbnail);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(roundedSolid(Color.rgb(25, 35, 52), dp(10)));
            imageFrame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            if (editable) {
                TextView remove = text("×", 17, Color.WHITE, true);
                remove.setGravity(Gravity.CENTER);
                remove.setBackground(roundedSolid(Color.rgb(35, 49, 68), dp(12)));
                FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(22), dp(22),
                        Gravity.TOP | Gravity.END);
                imageFrame.addView(remove, removeParams);
                remove.setOnClickListener(v -> {
                    task.imageFileNames.remove(fileName);
                    refreshTaskImageStrip(task, imageStrip, true);
                });
            }
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(64), dp(64));
            imageParams.setMargins(dp(3), dp(2), dp(3), dp(2));
            imageStrip.addView(imageFrame, imageParams);
        }
        View scroll = (View) imageStrip.getParent();
        scroll.setVisibility(imageStrip.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void refreshInsertButtons(LinearLayout rows) {
        int count = rows.getChildCount();
        for (int i = 0; i < count; i++) {
            LinearLayout item = (LinearLayout) rows.getChildAt(i);
            View insertHere = item.getChildAt(item.getChildCount() - 1);
            insertHere.setVisibility(i < count - 1 ? View.VISIBLE : View.GONE);
        }
    }

    private TextView swapHandle() {
        TextView handle = text("↔", 22, Color.rgb(93, 184, 255), true);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("اضغط لتحديد المهمة ثم اختر مهمة أخرى لتبديل مكانيهما");
        handle.setBackground(rippleBackground(0x334A9FFF, dp(12)));
        applyElectricGlow(handle, 1, true);
        return handle;
    }

    private void enableTapToSwap(LinearLayout container, View item, TextView handle,
                                 SwapSelection selection, Runnable onOrderChanged) {
        if (!storageHealthy) {
            handle.setEnabled(false);
            handle.setAlpha(0.45f);
            return;
        }
        Drawable originalBackground = item.getBackground();
        CharSequence originalHandleText = handle.getText();
        int originalHandleColor = handle.getCurrentTextColor();
        handle.setOnClickListener(v -> {
            if (selection.selectedItem != null && selection.selectedItem.getParent() != container) {
                clearSwapSelection(selection);
            }
            if (selection.selectedItem == null) {
                selection.selectedItem = item;
                selection.selectedHandle = handle;
                selection.originalBackground = originalBackground;
                selection.originalHandleText = originalHandleText;
                selection.originalHandleColor = originalHandleColor;
                item.setBackground(roundedStroke(Color.rgb(11, 48, 91), 0xFF3DA6FF, dp(12)));
                applyElectricGlow(item, 8, false);
                handle.setText("✓");
                handle.setTextColor(Color.WHITE);
                Toast.makeText(this, "تم تحديد المهمة — اختر ↔ لمهمة أخرى", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selection.selectedItem == item) {
                clearSwapSelection(selection);
                Toast.makeText(this, "تم إلغاء التحديد", Toast.LENGTH_SHORT).show();
                return;
            }

            View first = selection.selectedItem;
            int firstIndex = container.indexOfChild(first);
            int secondIndex = container.indexOfChild(item);
            if (firstIndex < 0 || secondIndex < 0) {
                clearSwapSelection(selection);
                return;
            }
            ViewGroup.LayoutParams firstParams = first.getLayoutParams();
            ViewGroup.LayoutParams secondParams = item.getLayoutParams();
            clearSwapSelection(selection);
            container.removeView(first);
            container.removeView(item);
            if (firstIndex < secondIndex) {
                container.addView(item, firstIndex, secondParams);
                container.addView(first, secondIndex, firstParams);
            } else {
                container.addView(first, secondIndex, firstParams);
                container.addView(item, firstIndex, secondParams);
            }
            container.requestLayout();
            container.invalidate();
            onOrderChanged.run();
            Toast.makeText(this, "تم تبديل موضعي المهمتين", Toast.LENGTH_SHORT).show();
        });
    }

    private void clearSwapSelection(SwapSelection selection) {
        if (selection.selectedItem != null) {
            selection.selectedItem.setBackground(selection.originalBackground);
            selection.selectedItem.setElevation(dp(0));
        }
        if (selection.selectedHandle != null) {
            selection.selectedHandle.setText(selection.originalHandleText);
            selection.selectedHandle.setTextColor(selection.originalHandleColor);
        }
        selection.selectedItem = null;
        selection.selectedHandle = null;
        selection.originalBackground = null;
        selection.originalHandleText = null;
    }

    private void confirmDeleteNotebook(Notebook notebook) {
        if (!storageHealthy) return;
        new AlertDialog.Builder(this)
                .setTitle("نقل الدفتر إلى سلة المحذوفات")
                .setMessage("سيبقى «" + notebook.name + "» في السلة لمدة ٣٠ يومًا، ويمكنك استعادته خلالها.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("نقل إلى السلة", (dialog, which) -> {
                    notebooks.remove(notebook);
                    selectedNotebookIds.remove(notebook.id);
                    trashedNotebooks.add(new TrashedNotebook(notebook, System.currentTimeMillis()));
                    saveNotebooks();
                    if (detailsDialog != null && detailsDialog.isShowing()) detailsDialog.dismiss();
                    renderCards();
                }).show();
    }

    private void showTrashDialog() {
        boolean trashLoaded;
        synchronized (TrashCleanupWorker.DATA_LOCK) {
            trashLoaded = loadTrashFromPreferences();
            if (trashLoaded) {
                if (purgeExpiredTrash()) saveNotebooks();
                else scheduleTrashCleanup();
            }
        }
        if (!trashLoaded) {
            updateSelectionToolbar();
            new AlertDialog.Builder(this)
                    .setTitle("تعذر قراءة سلة المحذوفات")
                    .setMessage("لم تُحذف محتويات السلة؛ أُوقفت التعديلات لحماية البيانات.")
                    .setPositiveButton("حسنًا", null)
                    .show();
            return;
        }
        updateSelectionToolbar();
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel();
        TextView title = text("سلة المحذوفات", 21, Color.WHITE, true);
        title.setPadding(0, 0, 0, dp(8));
        panel.addView(title);
        TextView hint = text("تُحذف العناصر تلقائيًا بعد ٣٠ يومًا من نقلها إلى السلة.", 12, MUTED, false);
        hint.setPadding(0, 0, 0, dp(10));
        panel.addView(hint);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(330)));
        TextView close = actionButton("إغلاق", false);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(46));
        closeParams.topMargin = dp(10);
        panel.addView(close, closeParams);
        close.setOnClickListener(v -> dialog.dismiss());

        Runnable[] refreshAction = new Runnable[1];
        refreshAction[0] = () -> {
            list.removeAllViews();
            if (trashedNotebooks.isEmpty()) {
                TextView empty = text("السلة فارغة", 15, MUTED, false);
                empty.setGravity(Gravity.CENTER);
                list.addView(empty, new LinearLayout.LayoutParams(-1, dp(100)));
                return;
            }
            for (TrashedNotebook entry : new ArrayList<>(trashedNotebooks)) {
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(12), dp(10), dp(12), dp(10));
                item.setBackground(roundedSolid(Color.rgb(25, 35, 52), dp(12)));
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
                itemParams.bottomMargin = dp(8);
                list.addView(item, itemParams);
                TextView name = text(entry.notebook.name, 16, Color.WHITE, true);
                item.addView(name);
                TextView dates = text("نُقل إلى السلة: " + formatLocalDate(entry.deletedAtMs)
                                + "\nيُحذف نهائيًا: " + formatLocalDate(TrashCleanupWorker.expiryTime(entry.deletedAtMs)),
                        11, MUTED, false);
                dates.setPadding(0, dp(4), 0, dp(8));
                item.addView(dates);
                LinearLayout actions = new LinearLayout(this);
                actions.setGravity(Gravity.CENTER_VERTICAL);
                actions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                TextView restore = actionButton("استعادة", true);
                TextView deleteForever = actionButton("حذف نهائي", false);
                LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(40), 1);
                actionParams.setMargins(dp(3), 0, dp(3), 0);
                actions.addView(restore, actionParams);
                actions.addView(deleteForever, new LinearLayout.LayoutParams(0, dp(40), 1));
                item.addView(actions);
                restore.setOnClickListener(v -> {
                    if (System.currentTimeMillis() >= TrashCleanupWorker.expiryTime(entry.deletedAtMs)) {
                        trashedNotebooks.remove(entry);
                        saveNotebooks();
                        refreshAction[0].run();
                        Toast.makeText(this, "انتهت مدة الاحتفاظ بهذا الدفتر", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    trashedNotebooks.remove(entry);
                    notebooks.add(entry.notebook);
                    saveNotebooks();
                    renderCards();
                    refreshAction[0].run();
                });
                deleteForever.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("حذف نهائي")
                        .setMessage("سيُحذف الدفتر وملفاته نهائيًا ولا يمكن استعادته.")
                        .setNegativeButton("إلغاء", null)
                        .setPositiveButton("حذف نهائيًا", (d, which) -> {
                            trashedNotebooks.remove(entry);
                            saveNotebooks();
                            renderCards();
                            refreshAction[0].run();
                        }).show());
            }
        };
        refreshAction[0].run();
        dialog.setContentView(panel);
        configureDialog(dialog);
        dialog.show();
        sizeDialog(dialog);
    }

    private boolean loadTrashFromPreferences() {
        synchronized (TrashCleanupWorker.DATA_LOCK) {
            trashedNotebooks.clear();
            String stored = getSharedPreferences("daftri", MODE_PRIVATE).getString("trash", "[]");
            try {
                JSONArray array = new JSONArray(stored);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject entry = array.optJSONObject(i);
                    if (entry == null) throw new IllegalStateException("Invalid trash entry");
                    JSONObject notebookObject = entry.optJSONObject("notebook");
                    if (notebookObject == null) throw new IllegalStateException("Missing trashed notebook");
                    trashedNotebooks.add(new TrashedNotebook(parseNotebook(notebookObject),
                            entry.optLong("deletedAt", System.currentTimeMillis())));
                }
                return true;
            } catch (Exception ignored) {
                trashedNotebooks.clear();
                storageHealthy = false;
                return false;
            }
        }
    }

    private boolean purgeExpiredTrash() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = trashedNotebooks.size() - 1; i >= 0; i--) {
            if (now >= TrashCleanupWorker.expiryTime(trashedNotebooks.get(i).deletedAtMs)) {
                trashedNotebooks.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    private void scheduleTrashCleanup() {
        long earliestExpiry = Long.MAX_VALUE;
        for (TrashedNotebook entry : trashedNotebooks) {
            earliestExpiry = Math.min(earliestExpiry, TrashCleanupWorker.expiryTime(entry.deletedAtMs));
        }
        TrashCleanupWorker.schedule(this, earliestExpiry == Long.MAX_VALUE ? 0L : earliestExpiry);
    }

    private String saveTaskImageLocally(Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Image could not be opened");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Unsupported image");

        int sampleSize = 1;
        while (Math.max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 1600) {
            sampleSize *= 2;
        }
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = sampleSize;
        Bitmap bitmap;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Image could not be reopened");
            bitmap = BitmapFactory.decodeStream(input, null, decodeOptions);
        }
        if (bitmap == null) throw new IOException("Image could not be decoded");

        File directory = taskImageDirectory();
        if (!directory.exists() && !directory.mkdirs()) {
            bitmap.recycle();
            throw new IOException("Image folder could not be created");
        }
        String fileName = UUID.randomUUID().toString() + ".jpg";
        File outputFile = new File(directory, fileName);
        boolean saved = false;
        try (OutputStream output = new FileOutputStream(outputFile)) {
            saved = bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output);
            if (!saved) throw new IOException("Image could not be compressed");
        } finally {
            bitmap.recycle();
            if (!saved) outputFile.delete();
        }
        return fileName;
    }

    private Bitmap loadTaskThumbnail(String fileName) {
        File imageFile = taskImageFile(fileName);
        if (imageFile == null || !imageFile.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sampleSize = 1;
        while (Math.max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 240) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
    }

    private File taskImageDirectory() {
        return new File(getFilesDir(), "task_images");
    }

    private File taskImageFile(String fileName) {
        if (fileName == null || fileName.contains("/") || fileName.contains("\\")) return null;
        return new File(taskImageDirectory(), fileName);
    }

    private void cleanupUnusedTaskImages() {
        synchronized (TrashCleanupWorker.DATA_LOCK) {
            Set<String> referenced = new HashSet<>();
            for (Notebook notebook : notebooks) collectNotebookImages(notebook, referenced);
            for (TrashedNotebook entry : trashedNotebooks) collectNotebookImages(entry.notebook, referenced);
            File[] imageFiles = taskImageDirectory().listFiles();
            if (imageFiles == null) return;
            for (File imageFile : imageFiles) {
                if (imageFile.isFile() && !referenced.contains(imageFile.getName())) imageFile.delete();
            }
        }
    }

    private void collectNotebookImages(Notebook notebook, Set<String> referenced) {
        if (notebook.titleImageFileName != null) referenced.add(notebook.titleImageFileName);
        for (TaskEntry task : notebook.tasks) referenced.addAll(task.imageFileNames);
    }

    private boolean loadNotebooks() {
        synchronized (TrashCleanupWorker.DATA_LOCK) {
            notebooks.clear();
            String stored = getSharedPreferences("daftri", MODE_PRIVATE).getString("notebooks", "[]");
            boolean notebooksRead = true;
            try {
                JSONArray array = new JSONArray(stored);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) throw new IllegalStateException("Invalid notebook entry");
                    notebooks.add(parseNotebook(object));
                }
            } catch (Exception ignored) {
                notebooks.clear();
                notebooksRead = false;
            }
            boolean trashRead = loadTrashFromPreferences();
            storageHealthy = notebooksRead && trashRead;
            if (!storageHealthy) return false;
            purgeExpiredTrash();
            saveNotebooks();
            return true;
        }
    }

    private Notebook parseNotebook(JSONObject object) {
        JSONArray taskArray = object.optJSONArray("tasks");
        ArrayList<TaskEntry> tasks = new ArrayList<>();
        if (taskArray != null) {
            for (int j = 0; j < taskArray.length(); j++) {
                Object savedTask = taskArray.opt(j);
                if (savedTask instanceof JSONObject) {
                    JSONObject taskObject = (JSONObject) savedTask;
                    TaskEntry task = new TaskEntry(taskObject.optString("text", ""), null,
                            CreationTimestampMigration.readCreatedAt(taskObject, "createdAt"));
                    JSONArray savedImages = taskObject.optJSONArray("images");
                    if (savedImages != null) {
                        for (int imageIndex = 0; imageIndex < savedImages.length(); imageIndex++) {
                            String fileName = savedImages.optString(imageIndex, null);
                            if (fileName != null && !fileName.isEmpty()) task.imageFileNames.add(fileName);
                        }
                    } else {
                        String oldImage = taskObject.optString("image", null);
                        if (oldImage != null && !oldImage.isEmpty()) task.imageFileNames.add(oldImage);
                    }
                    tasks.add(task);
                } else {
                    tasks.add(new TaskEntry(taskArray.optString(j, ""), null,
                            CreationTimestampMigration.readCreatedAt(savedTask, "createdAt")));
                }
            }
        }
        return new Notebook(object.optString("id", UUID.randomUUID().toString()),
                object.optString("name", "دفتر"), tasks, object.optInt("color", CARD_COLORS[0]),
                object.optString("titleImage", null),
                CreationTimestampMigration.readCreatedAt(object, "createdAt"));
    }

    private JSONObject notebookToJson(Notebook notebook) throws Exception {
        JSONObject object = new JSONObject();
        object.put("id", notebook.id);
        object.put("name", notebook.name);
        object.put("color", notebook.color);
        object.put("createdAt", notebook.createdAtMs == null ? JSONObject.NULL : notebook.createdAtMs);
        if (notebook.titleImageFileName != null) object.put("titleImage", notebook.titleImageFileName);
        JSONArray tasks = new JSONArray();
        for (TaskEntry task : notebook.tasks) {
            JSONObject taskObject = new JSONObject();
            taskObject.put("text", task.text);
            taskObject.put("createdAt", task.createdAtMs == null ? JSONObject.NULL : task.createdAtMs);
            JSONArray images = new JSONArray();
            for (String fileName : task.imageFileNames) images.put(fileName);
            taskObject.put("images", images);
            tasks.put(taskObject);
        }
        object.put("tasks", tasks);
        return object;
    }

    private void saveNotebooks() {
        if (!storageHealthy) return;
        synchronized (TrashCleanupWorker.DATA_LOCK) {
            JSONArray notebookArray = new JSONArray();
            JSONArray trashArray = new JSONArray();
            try {
                for (Notebook notebook : notebooks) notebookArray.put(notebookToJson(notebook));
                for (TrashedNotebook entry : trashedNotebooks) {
                    JSONObject trashObject = new JSONObject();
                    trashObject.put("deletedAt", entry.deletedAtMs);
                    trashObject.put("notebook", notebookToJson(entry.notebook));
                    trashArray.put(trashObject);
                }
            } catch (Exception ignored) {
            }
            getSharedPreferences("daftri", MODE_PRIVATE).edit()
                    .putString("notebooks", notebookArray.toString())
                    .putString("trash", trashArray.toString())
                    .apply();
            scheduleTrashCleanup();
            cleanupUnusedTaskImages();
        }
    }

    private String formatLocalDate(Long millis) {
        if (millis == null || millis <= 0L) return getString(R.string.date_unavailable);
        Locale locale = Locale.getDefault();
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEE d/M/y");
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
        formatter.setTimeZone(TimeZone.getDefault());
        return formatter.format(new Date(millis));
    }

    private String toArabicDigits(String value) {
        return value.replace('0', '٠').replace('1', '١').replace('2', '٢')
                .replace('3', '٣').replace('4', '٤').replace('5', '٥').replace('6', '٦')
                .replace('7', '٧').replace('8', '٨').replace('9', '٩');
    }

    private Notebook snapshotNotebook(Notebook source) {
        ArrayList<TaskEntry> copiedTasks = new ArrayList<>();
        for (TaskEntry task : source.tasks) {
            TaskEntry copy = new TaskEntry(task.text, null, task.createdAtMs);
            copy.imageFileNames.addAll(task.imageFileNames);
            copiedTasks.add(copy);
        }
        return new Notebook(source.id, source.name, copiedTasks, source.color,
                source.titleImageFileName, source.createdAtMs);
    }

    private void beginPdfExport() {
        if (pdfExporting) return;
        ArrayList<Notebook> selected = new ArrayList<>();
        for (String selectedId : selectedNotebookIds) {
            for (Notebook notebook : notebooks) {
                if (notebook.id.equals(selectedId)) {
                    selected.add(snapshotNotebook(notebook));
                    break;
                }
            }
        }
        if (selected.size() < 2) {
            Toast.makeText(this, "حدد دفترين أو أكثر لإنشاء PDF", Toast.LENGTH_SHORT).show();
            return;
        }
        File exportDirectory = new File(getCacheDir(), "pdf_exports");
        if (!exportDirectory.exists() && !exportDirectory.mkdirs()) {
            Toast.makeText(this, "تعذر تجهيز ملف PDF", Toast.LENGTH_SHORT).show();
            return;
        }
        File outputFile = new File(exportDirectory, "Daftri_" + System.currentTimeMillis() + ".pdf");
        pdfExporting = true;
        updateSelectionToolbar();
        new Thread(() -> {
            boolean generated = false;
            try {
                writeSelectionPdf(selected, outputFile);
                generated = outputFile.isFile() && outputFile.length() > 0;
            } catch (Exception ignored) {
                outputFile.delete();
            }
            boolean ready = generated;
            runOnUiThread(() -> {
                pdfExporting = false;
                updateSelectionToolbar();
                if (!ready || isFinishing() || isDestroyed()) {
                    outputFile.delete();
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, "تعذر إنشاء ملف PDF", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                Uri contentUri = androidx.core.content.FileProvider.getUriForFile(
                        this, getPackageName() + ".fileprovider", outputFile);
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("application/pdf");
                sendIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                sendIntent.putExtra(Intent.EXTRA_TITLE, outputFile.getName());
                sendIntent.setClipData(ClipData.newUri(
                        getContentResolver(), outputFile.getName(), contentUri));
                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(sendIntent, "مشاركة ملف PDF");
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(chooser);
                } catch (Exception error) {
                    outputFile.delete();
                    Toast.makeText(this, "لا يتوفر تطبيق لمشاركة PDF", Toast.LENGTH_SHORT).show();
                }
            });
        }, "daftri-pdf-export").start();
    }

    private void writeSelectionPdf(List<Notebook> selected, File outputFile) throws IOException {
        PdfDocument document = new PdfDocument();
        PdfPageState page = new PdfPageState(document);
        try {
            TextPaint h1 = pdfTextPaint(23, Color.rgb(18, 91, 176), true);
            TextPaint h2 = pdfTextPaint(16, Color.rgb(20, 40, 68), true);
            TextPaint body = pdfTextPaint(12, Color.rgb(30, 35, 42), false);
            TextPaint date = pdfTextPaint(10, Color.rgb(95, 105, 116), false);
            for (int cardIndex = 0; cardIndex < selected.size(); cardIndex++) {
                Notebook notebook = selected.get(cardIndex);
                if (cardIndex > 0) page.startNextPage();
                drawPdfParagraph(page, notebook.name, h1, 10);
                drawPdfParagraph(page, "تاريخ إنشاء الدفتر: " + formatLocalDate(notebook.createdAtMs), date, 12);
                if (notebook.titleImageFileName != null) {
                    drawPdfImage(page, notebook.titleImageFileName);
                }
                for (int taskIndex = 0; taskIndex < notebook.tasks.size(); taskIndex++) {
                    TaskEntry task = notebook.tasks.get(taskIndex);
                    drawPdfParagraph(page, "المهمة " + toArabicDigits(String.valueOf(taskIndex + 1)), h2, 3);
                    drawPdfParagraph(page, "تاريخ إنشاء المهمة: " + formatLocalDate(task.createdAtMs), date, 4);
                    if (!task.text.trim().isEmpty()) drawPdfParagraph(page, task.text, body, 7);
                    for (String imageFileName : task.imageFileNames) drawPdfImage(page, imageFileName);
                }
            }
            page.finish();
            try (FileOutputStream output = new FileOutputStream(outputFile)) {
                document.writeTo(output);
            }
        } finally {
            document.close();
        }
    }

    private TextPaint pdfTextPaint(float textSize, int color, boolean bold) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(textSize);
        paint.setColor(color);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        return paint;
    }

    private StaticLayout createPdfLayout(CharSequence text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, 1.08f)
                .setIncludePad(false)
                .build();
    }

    private void drawPdfParagraph(PdfPageState page, String value, TextPaint paint, int gapAfter) {
        if (value == null || value.isEmpty()) return;
        StaticLayout layout = createPdfLayout(value, paint, PdfPageState.CONTENT_WIDTH);
        for (int lineIndex = 0; lineIndex < layout.getLineCount(); lineIndex++) {
            int lineTop = layout.getLineTop(lineIndex);
            int lineBottom = layout.getLineBottom(lineIndex);
            int lineHeight = Math.max(1, lineBottom - lineTop);
            page.ensureRoom(lineHeight);
            Canvas canvas = page.page.getCanvas();
            int save = canvas.save();
            canvas.translate(PdfPageState.MARGIN, page.y - lineTop);
            canvas.clipRect(0, lineTop, PdfPageState.CONTENT_WIDTH, lineBottom);
            layout.draw(canvas);
            canvas.restoreToCount(save);
            page.y += lineHeight;
        }
        page.y += gapAfter;
    }

    private void drawPdfImage(PdfPageState page, String fileName) {
        File file = taskImageFile(fileName);
        if (file == null || !file.isFile()) return;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) return;
        try {
            float maxWidth = PdfPageState.CONTENT_WIDTH;
            float maxHeight = PdfPageState.PAGE_HEIGHT - PdfPageState.MARGIN * 2f;
            float scale = Math.min(1f, Math.min(maxWidth / bitmap.getWidth(), maxHeight / bitmap.getHeight()));
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            page.ensureRoom((int) height + 12);
            float left = PdfPageState.MARGIN + (PdfPageState.CONTENT_WIDTH - width) / 2f;
            Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            page.page.getCanvas().drawBitmap(bitmap, null,
                    new android.graphics.RectF(left, page.y, left + width, page.y + height), imagePaint);
            page.y += height + 12;
        } finally {
            bitmap.recycle();
        }
    }

    private static final class PdfPageState {
        static final int PAGE_WIDTH = 595;
        static final int PAGE_HEIGHT = 842;
        static final int MARGIN = 42;
        static final int CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;
        static final int BOTTOM = PAGE_HEIGHT - MARGIN;
        final PdfDocument document;
        PdfDocument.Page page;
        int pageNumber;
        float y;

        PdfPageState(PdfDocument document) {
            this.document = document;
            startNextPage();
        }

        void startNextPage() {
            if (page != null) document.finishPage(page);
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
            page = document.startPage(info);
            page.getCanvas().drawColor(Color.WHITE);
            y = MARGIN;
        }

        void ensureRoom(int height) {
            if (y + height > BOTTOM && y > MARGIN) startNextPage();
        }

        void finish() {
            if (page != null) {
                document.finishPage(page);
                page = null;
            }
        }
    }

    private LinearLayout dialogPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(18));
        panel.setBackground(roundedSolid(PANEL, dp(22)));
        return panel;
    }

    private EditText editField(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(MUTED);
        input.setHint(hint);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        input.setBackground(roundedSolid(Color.rgb(25, 35, 52), dp(13)));
        return input;
    }

    private EditText editTaskField(String hint) {
        EditText input = editField(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(false);
        input.setHorizontallyScrolling(false);
        input.setMinLines(4);
        input.setMaxLines(10);
        input.setMinHeight(dp(116));
        input.setVerticalScrollBarEnabled(true);
        input.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        input.setGravity(Gravity.TOP | Gravity.END);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        return input;
    }

    private ImageView brandMark(int sizeDp) {
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.daftri_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setContentDescription("شعار دفتر مهامي");
        logo.setBackground(roundedStroke(Color.rgb(5, 8, 15), 0xFF91A7C4, dp(14)));
        logo.setClipToOutline(true);
        logo.setElevation(dp(2));
        logo.setMinimumWidth(dp(sizeDp));
        logo.setMinimumHeight(dp(sizeDp));
        return logo;
    }

    private void applyElectricGlow(View view, int elevationDp, boolean glowText) {
        view.setElevation(dp(elevationDp));
        if (Build.VERSION.SDK_INT >= 28) {
            view.setOutlineAmbientShadowColor(0x9945A8FF);
            view.setOutlineSpotShadowColor(0xCC168EFF);
        }
        if (glowText && view instanceof TextView) {
            ((TextView) view).setShadowLayer(dp(5), 0, 0, 0xAA69BBFF);
        }
    }

    private GradientDrawable glowHaloBackground() {
        GradientDrawable halo = new GradientDrawable();
        halo.setShape(GradientDrawable.OVAL);
        halo.setColor(0x55349EFF);
        halo.setStroke(dp(1), 0xAA72C2FF);
        return halo;
    }

    private void startGlowPulse(View halo) {
        if (addGlowAnimator != null) addGlowAnimator.cancel();
        addGlowAnimator = ValueAnimator.ofFloat(0f, 1f);
        addGlowAnimator.setDuration(1450);
        addGlowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        addGlowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        addGlowAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        addGlowAnimator.addUpdateListener(animation -> {
            float phase = (float) animation.getAnimatedValue();
            float scale = 0.98f + (phase * 0.14f);
            halo.setScaleX(scale);
            halo.setScaleY(scale);
            halo.setAlpha(0.44f - (phase * 0.25f));
        });
        addGlowAnimator.start();
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = text(label, 15, primary ? Color.WHITE : Color.rgb(210, 223, 241), true);
        button.setGravity(Gravity.CENTER);
        if (primary) {
            GradientDrawable background = roundedGradient(BLUE, Color.rgb(8, 69, 158), dp(14));
            background.setStroke(dp(1), 0xFF7EC5FF);
            button.setBackground(background);
            applyElectricGlow(button, 4, true);
        } else {
            button.setBackground(roundedStroke(Color.rgb(23, 33, 49), 0xAA258FFF, dp(14)));
            applyElectricGlow(button, 1, true);
        }
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(true);
        return view;
    }

    private GradientDrawable circleBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF34A7FF, 0xFF075FCC});
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setStroke(dp(1), 0xFFDDF2FF);
        return drawable;
    }

    private GradientDrawable roundedGradient(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundedSolid(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundedStroke(int color, int stroke, int radius) {
        GradientDrawable drawable = roundedSolid(color, radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable rippleBackground(int color, int radius) {
        return roundedSolid(color, radius);
    }

    private int darken(int color) {
        return Color.rgb((int) (Color.red(color) * 0.62f), (int) (Color.green(color) * 0.62f),
                (int) (Color.blue(color) * 0.72f));
    }

    private String taskCount(int count) {
        String number = String.valueOf(count).replace('0', '٠').replace('1', '١').replace('2', '٢')
                .replace('3', '٣').replace('4', '٤').replace('5', '٥').replace('6', '٦')
                .replace('7', '٧').replace('8', '٨').replace('9', '٩');
        return number + (count == 1 ? " مهمة" : " مهام");
    }

    private void configureDialog(Dialog dialog) {
        dialog.getWindow();
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams attrs = window.getAttributes();
                attrs.dimAmount = 0.68f;
                window.setAttributes(attrs);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });
    }

    private void sizeEditorDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            int availableHeight = Math.max(dp(300), screenHeight - dp(96));
            int editorHeight = Math.min(dp(760), availableHeight);
            window.setLayout(getResources().getDisplayMetrics().widthPixels - dp(32), editorHeight);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void sizeDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(getResources().getDisplayMetrics().widthPixels - dp(32),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class SwapSelection {
        View selectedItem;
        TextView selectedHandle;
        Drawable originalBackground;
        CharSequence originalHandleText;
        int originalHandleColor;
    }

    private static class TitleImageState {
        String imageFileName;
        ImageView preview;

        TitleImageState(String imageFileName) {
            this.imageFileName = imageFileName;
        }
    }

    private static class TaskEntry {
        String text;
        final Long createdAtMs;
        final ArrayList<String> imageFileNames = new ArrayList<>();

        TaskEntry(String text, String imageFileName) {
            this(text, imageFileName, System.currentTimeMillis());
        }

        TaskEntry(String text, String imageFileName, long createdAtMs) {
            this(text, imageFileName, Long.valueOf(createdAtMs));
        }

        TaskEntry(String text, String imageFileName, Long createdAtMs) {
            this.text = text == null ? "" : text;
            this.createdAtMs = createdAtMs != null && createdAtMs > 0L ? createdAtMs : null;
            if (imageFileName != null && !imageFileName.isEmpty()) imageFileNames.add(imageFileName);
        }
    }

    private static class Notebook {
        final String id;
        String name;
        final ArrayList<TaskEntry> tasks;
        final Long createdAtMs;
        int color;
        String titleImageFileName;

        Notebook(String id, String name, List<TaskEntry> tasks, int color, String titleImageFileName) {
            this(id, name, tasks, color, titleImageFileName, System.currentTimeMillis());
        }

        Notebook(String id, String name, List<TaskEntry> tasks, int color, String titleImageFileName,
                 long createdAtMs) {
            this(id, name, tasks, color, titleImageFileName, Long.valueOf(createdAtMs));
        }

        Notebook(String id, String name, List<TaskEntry> tasks, int color, String titleImageFileName,
                 Long createdAtMs) {
            this.id = id;
            this.name = name;
            this.tasks = new ArrayList<>(tasks);
            this.color = color;
            this.titleImageFileName = titleImageFileName;
            this.createdAtMs = createdAtMs != null && createdAtMs > 0L ? createdAtMs : null;
        }
    }

    private static class TrashedNotebook {
        final Notebook notebook;
        final long deletedAtMs;

        TrashedNotebook(Notebook notebook, long deletedAtMs) {
            this.notebook = notebook;
            this.deletedAtMs = deletedAtMs > 0L ? deletedAtMs : System.currentTimeMillis();
        }
    }
}
