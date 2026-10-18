package com.hrdcoreee.tabletizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;



import java.util.Locale;

public class TabletAreaView extends View {
    public static final float DIGITIZER_WIDTH_MM = 160.0f;
    public static final float DIGITIZER_HEIGHT_MM = 90.0f;
    public static final int MAX_X = 32000;
    public static final int MAX_Y = 18000;

    public enum AspectRatio {
        RATIO_16_9("16:9", 16f / 9f),
        RATIO_4_3("4:3", 4f / 3f),
        RATIO_FULL("Fullscreen", 0f);

        public final String title;
        public final float ratio;

        AspectRatio(String title, float ratio) {
            this.title = title;
            this.ratio = ratio;
        }
    }

    public enum AreaScale {
        SCALE_100("100% (Full)", 100),
        SCALE_80("80%", 80),
        SCALE_65("65% (Recommended)", 65),
        SCALE_50("50% (Medium)", 50),
        SCALE_35("35% (Small)", 35);

        public final String title;
        public final int percent;

        AreaScale(String title, int percent) {
            this.title = title;
            this.percent = percent;
        }
    }

    public enum AreaPosition {
        CENTER("Center"),
        RIGHT_BOTTOM("Bottom right"),
        LEFT_BOTTOM("Bottom left"),
        RIGHT_CENTER("Right center");

        public final String title;

        AreaPosition(String title) {
            this.title = title;
        }
    }

    private enum DragMode {
        NONE,
        MOVE,
        RESIZE_TOP_LEFT,
        RESIZE_TOP_RIGHT,
        RESIZE_BOTTOM_LEFT,
        RESIZE_BOTTOM_RIGHT
    }

    public interface OnAreaChangedListener {
        void onAreaChanged(float widthMm, float heightMm, float posXMm, float posYMm);
    }

    public interface OnEditModeChangeListener {
        void onEditModeChanged(boolean isEditMode);
    }

    private AspectRatio currentRatio = AspectRatio.RATIO_16_9;
    private AreaScale currentScale = AreaScale.SCALE_100;
    private AreaPosition currentPosition = AreaPosition.CENTER;

    // Synchronized area dimensions in millimeters for OpenTabletDriver
    private float currentOtdWidthMm = 160.0f;
    private float currentOtdHeightMm = 90.0f;
    private float currentOtdPosXMm = 80.0f;
    private float currentOtdPosYMm = 45.0f;

    private final RectF activeRect = new RectF();
    private final RectF digitizerBounds = new RectF();
    private final RectF initialRect = new RectF();
    private final RectF doneButtonRect = new RectF();

    private boolean isEditMode = false;
    private DragMode dragMode = DragMode.NONE;
    private float dragStartX = -1;
    private float dragStartY = -1;

    private boolean isPenActive = false;
    private float lastTouchX = -1;
    private float lastTouchY = -1;
    private String cachedLabel = "";

    private OnAreaChangedListener onAreaChangedListener;
    private OnEditModeChangeListener onEditModeChangeListener;

    private final Paint bgPaint = new Paint();
    private final Paint activeAreaPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint borderEditPaint = new Paint();
    private final Paint digitizerBoundsPaint = new Paint();
    private final Paint handlePaint = new Paint();
    private final Paint handleBorderPaint = new Paint();
    private final Paint handleInnerDotPaint = new Paint();
    private final Paint doneButtonPaint = new Paint();
    private final Paint doneButtonTextPaint = new Paint();
    private final Paint editHintPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint touchPaint = new Paint();

    public TabletAreaView(Context context) {
        super(context);
        init();
    }

    public TabletAreaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TabletAreaView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint.setColor(Color.parseColor("#000000")); // Pure AMOLED black
        bgPaint.setStyle(Paint.Style.FILL);

        activeAreaPaint.setColor(Color.parseColor("#181818"));
        activeAreaPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(Color.parseColor("#00E5FF"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
        borderPaint.setAntiAlias(true);

        borderEditPaint.setColor(Color.parseColor("#00E5FF"));
        borderEditPaint.setStyle(Paint.Style.STROKE);
        borderEditPaint.setStrokeWidth(6f);
        borderEditPaint.setAntiAlias(true);

        digitizerBoundsPaint.setColor(Color.parseColor("#25FFFFFF"));
        digitizerBoundsPaint.setStyle(Paint.Style.STROKE);
        digitizerBoundsPaint.setStrokeWidth(2f);
        digitizerBoundsPaint.setAntiAlias(true);

        handlePaint.setColor(Color.parseColor("#00E5FF"));
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setAntiAlias(true);

        handleBorderPaint.setColor(Color.parseColor("#FFFFFF"));
        handleBorderPaint.setStyle(Paint.Style.STROKE);
        handleBorderPaint.setStrokeWidth(3f);
        handleBorderPaint.setAntiAlias(true);

        handleInnerDotPaint.setColor(Color.parseColor("#000000"));
        handleInnerDotPaint.setStyle(Paint.Style.FILL);
        handleInnerDotPaint.setAntiAlias(true);

        doneButtonPaint.setColor(Color.parseColor("#00C853"));
        doneButtonPaint.setStyle(Paint.Style.FILL);
        doneButtonPaint.setAntiAlias(true);

        doneButtonTextPaint.setColor(Color.parseColor("#FFFFFF"));
        doneButtonTextPaint.setTextSize(28f);
        doneButtonTextPaint.setTextAlign(Paint.Align.CENTER);
        doneButtonTextPaint.setFakeBoldText(true);
        doneButtonTextPaint.setAntiAlias(true);

        editHintPaint.setColor(Color.parseColor("#B3FFFFFF"));
        editHintPaint.setTextSize(26f);
        editHintPaint.setTextAlign(Paint.Align.CENTER);
        editHintPaint.setAntiAlias(true);

        textPaint.setColor(Color.parseColor("#80FFFFFF"));
        textPaint.setTextSize(32f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        touchPaint.setColor(Color.parseColor("#8000E5FF"));
        touchPaint.setStyle(Paint.Style.FILL);
        touchPaint.setAntiAlias(true);

        loadPreferences();
        updateCachedLabel();
        syncSettingsToOtd();
    }

    public void setOnAreaChangedListener(OnAreaChangedListener listener) {
        this.onAreaChangedListener = listener;
    }

    public void setOnEditModeChangeListener(OnEditModeChangeListener listener) {
        this.onEditModeChangeListener = listener;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        TabletServerService.setAreaSyncListener(this::applyAreaFromOtd);
    }

    @Override
    protected void onDetachedFromWindow() {
        TabletServerService.setAreaSyncListener(null);
        super.onDetachedFromWindow();
    }

    private void updateCachedLabel() {
        float ratio = currentOtdWidthMm / Math.max(0.1f, currentOtdHeightMm);
        String ratioStr;
        if (Math.abs(ratio - (16f / 9f)) < 0.05f) {
            ratioStr = "16:9";
        } else if (Math.abs(ratio - (4f / 3f)) < 0.05f) {
            ratioStr = "4:3";
        } else {
            ratioStr = String.format(Locale.US, "%.2f:1", ratio);
        }
        cachedLabel = String.format(Locale.US, "%s \u2022 %.1f\u00D7%.1f mm", ratioStr, currentOtdWidthMm, currentOtdHeightMm);
    }

    public String getCachedLabel() {
        return cachedLabel;
    }

    private void loadPreferences() {
        SharedPreferences prefs = getContext().getSharedPreferences("tablet_prefs", Context.MODE_PRIVATE);
        int ratioIdx = prefs.getInt("aspect_ratio", 0);
        if (ratioIdx >= 0 && ratioIdx < AspectRatio.values().length) {
            currentRatio = AspectRatio.values()[ratioIdx];
        }

        int scaleIdx = prefs.getInt("area_scale", 0);
        if (scaleIdx >= 0 && scaleIdx < AreaScale.values().length) {
            currentScale = AreaScale.values()[scaleIdx];
        }

        int posIdx = prefs.getInt("area_position", 0);
        if (posIdx >= 0 && posIdx < AreaPosition.values().length) {
            currentPosition = AreaPosition.values()[posIdx];
        }

        currentOtdWidthMm = prefs.getFloat("otd_width_mm", 160.0f);
        currentOtdHeightMm = prefs.getFloat("otd_height_mm", 90.0f);
        currentOtdPosXMm = prefs.getFloat("otd_pos_x_mm", 80.0f);
        currentOtdPosYMm = prefs.getFloat("otd_pos_y_mm", 45.0f);
    }

    private void saveOtdDimensions() {
        getContext().getSharedPreferences("tablet_prefs", Context.MODE_PRIVATE).edit()
                .putFloat("otd_width_mm", currentOtdWidthMm)
                .putFloat("otd_height_mm", currentOtdHeightMm)
                .putFloat("otd_pos_x_mm", currentOtdPosXMm)
                .putFloat("otd_pos_y_mm", currentOtdPosYMm)
                .apply();
    }

    public AspectRatio getAspectRatio() {
        return currentRatio;
    }

    public void setAspectRatio(AspectRatio ratio) {
        this.currentRatio = ratio;
        getContext().getSharedPreferences("tablet_prefs", Context.MODE_PRIVATE)
                .edit().putInt("aspect_ratio", ratio.ordinal()).apply();
        applyPresetDimensions();
        updateActiveRectFromMm(getWidth(), getHeight());
        updateCachedLabel();
        syncSettingsToOtd();
        if (onAreaChangedListener != null) {
            onAreaChangedListener.onAreaChanged(currentOtdWidthMm, currentOtdHeightMm, currentOtdPosXMm, currentOtdPosYMm);
        }
        invalidate();
    }

    public AreaScale getAreaScale() {
        return currentScale;
    }

    public void setAreaScale(AreaScale scale) {
        this.currentScale = scale;
        getContext().getSharedPreferences("tablet_prefs", Context.MODE_PRIVATE)
                .edit().putInt("area_scale", scale.ordinal()).apply();
        applyPresetDimensions();
        updateActiveRectFromMm(getWidth(), getHeight());
        updateCachedLabel();
        syncSettingsToOtd();
        if (onAreaChangedListener != null) {
            onAreaChangedListener.onAreaChanged(currentOtdWidthMm, currentOtdHeightMm, currentOtdPosXMm, currentOtdPosYMm);
        }
        invalidate();
    }

    public AreaPosition getAreaPosition() {
        return currentPosition;
    }

    public void setAreaPosition(AreaPosition position) {
        this.currentPosition = position;
        getContext().getSharedPreferences("tablet_prefs", Context.MODE_PRIVATE)
                .edit().putInt("area_position", position.ordinal()).apply();
        applyPresetDimensions();
        updateActiveRectFromMm(getWidth(), getHeight());
        updateCachedLabel();
        syncSettingsToOtd();
        if (onAreaChangedListener != null) {
            onAreaChangedListener.onAreaChanged(currentOtdWidthMm, currentOtdHeightMm, currentOtdPosXMm, currentOtdPosYMm);
        }
        invalidate();
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        if (!editMode) {
            dragMode = DragMode.NONE;
            saveOtdDimensions();
            syncSettingsToOtd();
        }
        if (onEditModeChangeListener != null) {
            onEditModeChangeListener.onEditModeChanged(editMode);
        }
        invalidate();
    }

    public void applyAreaFromOtd(float widthMm, float heightMm, float posXMm, float posYMm) {
        if (widthMm <= 0 || heightMm <= 0) return;

        // Skip echo if identical
        if (Math.abs(this.currentOtdWidthMm - widthMm) < 0.1f &&
                Math.abs(this.currentOtdHeightMm - heightMm) < 0.1f &&
                Math.abs(this.currentOtdPosXMm - posXMm) < 0.1f &&
                Math.abs(this.currentOtdPosYMm - posYMm) < 0.1f) {
            return;
        }

        this.currentOtdWidthMm = widthMm;
        this.currentOtdHeightMm = heightMm;
        this.currentOtdPosXMm = posXMm;
        this.currentOtdPosYMm = posYMm;

        saveOtdDimensions();

        float ratio = widthMm / heightMm;
        if (Math.abs(ratio - (16f / 9f)) < 0.05f) {
            currentRatio = AspectRatio.RATIO_16_9;
        } else if (Math.abs(ratio - (4f / 3f)) < 0.05f) {
            currentRatio = AspectRatio.RATIO_4_3;
        }

        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            updateActiveRectFromMm(w, h);
        }

        updateCachedLabel();
        if (onAreaChangedListener != null) {
            onAreaChangedListener.onAreaChanged(currentOtdWidthMm, currentOtdHeightMm, currentOtdPosXMm, currentOtdPosYMm);
        }
        postInvalidate();
    }

    public void getDigitizerBounds(RectF out) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            out.set(0, 0, 1, 1);
            return;
        }

        if (currentRatio == AspectRatio.RATIO_FULL) {
            out.set(0, 0, w, h);
        } else {
            float scale = Math.min((float) w / DIGITIZER_WIDTH_MM, (float) h / DIGITIZER_HEIGHT_MM);
            float dw = DIGITIZER_WIDTH_MM * scale;
            float dh = DIGITIZER_HEIGHT_MM * scale;
            float left = (w - dw) / 2f;
            float top = (h - dh) / 2f;
            out.set(left, top, left + dw, top + dh);
        }
    }

    private void updateActiveRectFromMm(int w, int h) {
        if (w <= 0 || h <= 0) return;
        getDigitizerBounds(digitizerBounds);

        float scaleX = digitizerBounds.width() / DIGITIZER_WIDTH_MM;
        float scaleY = digitizerBounds.height() / DIGITIZER_HEIGHT_MM;

        float leftMm = currentOtdPosXMm - (currentOtdWidthMm / 2f);
        float topMm = currentOtdPosYMm - (currentOtdHeightMm / 2f);

        float left = digitizerBounds.left + leftMm * scaleX;
        float top = digitizerBounds.top + topMm * scaleY;
        float right = left + currentOtdWidthMm * scaleX;
        float bottom = top + currentOtdHeightMm * scaleY;

        activeRect.set(left, top, right, bottom);
    }

    private void updateMmFromActiveRect() {
        getDigitizerBounds(digitizerBounds);
        float scaleX = digitizerBounds.width() / DIGITIZER_WIDTH_MM;
        float scaleY = digitizerBounds.height() / DIGITIZER_HEIGHT_MM;

        float wMm = activeRect.width() / scaleX;
        float hMm = activeRect.height() / scaleY;
        float leftMm = (activeRect.left - digitizerBounds.left) / scaleX;
        float topMm = (activeRect.top - digitizerBounds.top) / scaleY;
        float xMm = leftMm + (wMm / 2f);
        float yMm = topMm + (hMm / 2f);

        currentOtdWidthMm = Math.round(wMm * 10f) / 10f;
        currentOtdHeightMm = Math.round(hMm * 10f) / 10f;
        currentOtdPosXMm = Math.round(xMm * 10f) / 10f;
        currentOtdPosYMm = Math.round(yMm * 10f) / 10f;
    }

    private void applyPresetDimensions() {
        float targetRatio;
        if (currentRatio == AspectRatio.RATIO_4_3) {
            targetRatio = 4.0f / 3.0f;
        } else if (currentRatio == AspectRatio.RATIO_16_9) {
            targetRatio = 16.0f / 9.0f;
        } else {
            int w = getWidth();
            int h = getHeight();
            if (w > 0 && h > 0) {
                targetRatio = (float) w / (float) h;
            } else {
                targetRatio = 16.0f / 9.0f;
            }
        }

        float baseWidthMm;
        float baseHeightMm;

        if (targetRatio >= (16.0f / 9.0f)) {
            baseWidthMm = DIGITIZER_WIDTH_MM;
            baseHeightMm = DIGITIZER_WIDTH_MM / targetRatio;
        } else {
            baseHeightMm = DIGITIZER_HEIGHT_MM;
            baseWidthMm = DIGITIZER_HEIGHT_MM * targetRatio;
        }

        float scale = currentScale.percent / 100.0f;
        currentOtdWidthMm = Math.round(baseWidthMm * scale * 10.0f) / 10.0f;
        currentOtdHeightMm = Math.round(baseHeightMm * scale * 10.0f) / 10.0f;

        switch (currentPosition) {
            case RIGHT_BOTTOM:
                currentOtdPosXMm = DIGITIZER_WIDTH_MM - (currentOtdWidthMm / 2.0f);
                currentOtdPosYMm = DIGITIZER_HEIGHT_MM - (currentOtdHeightMm / 2.0f);
                break;
            case LEFT_BOTTOM:
                currentOtdPosXMm = currentOtdWidthMm / 2.0f;
                currentOtdPosYMm = DIGITIZER_HEIGHT_MM - (currentOtdHeightMm / 2.0f);
                break;
            case RIGHT_CENTER:
                currentOtdPosXMm = DIGITIZER_WIDTH_MM - (currentOtdWidthMm / 2.0f);
                currentOtdPosYMm = DIGITIZER_HEIGHT_MM / 2.0f;
                break;
            case CENTER:
            default:
                currentOtdPosXMm = DIGITIZER_WIDTH_MM / 2.0f;
                currentOtdPosYMm = DIGITIZER_HEIGHT_MM / 2.0f;
                break;
        }

        saveOtdDimensions();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateActiveRectFromMm(w, h);
    }

    public void syncSettingsToOtd() {
        TabletServerService.sendConfig(
                currentRatio.ordinal(),
                currentScale.percent,
                currentPosition.ordinal(),
                currentOtdWidthMm,
                currentOtdHeightMm,
                currentOtdPosXMm,
                currentOtdPosYMm
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        if (isEditMode) {
            getDigitizerBounds(digitizerBounds);
            canvas.drawRoundRect(digitizerBounds, 16f, 16f, digitizerBoundsPaint);
        }

        canvas.drawRoundRect(activeRect, 20f, 20f, activeAreaPaint);
        canvas.drawRoundRect(activeRect, 20f, 20f, isEditMode ? borderEditPaint : borderPaint);

        canvas.drawText(cachedLabel, activeRect.centerX(), activeRect.centerY(), textPaint);

        if (isEditMode) {
            // Draw resize corner handles
            drawHandle(canvas, activeRect.left, activeRect.top);
            drawHandle(canvas, activeRect.right, activeRect.top);
            drawHandle(canvas, activeRect.left, activeRect.bottom);
            drawHandle(canvas, activeRect.right, activeRect.bottom);

            // Draw Done button
            float btnW = 180f;
            float btnH = 64f;
            doneButtonRect.set(getWidth() - btnW - 32f, 32f, getWidth() - 32f, 32f + btnH);
            canvas.drawRoundRect(doneButtonRect, 24f, 24f, doneButtonPaint);
            canvas.drawText("Done", doneButtonRect.centerX(), doneButtonRect.centerY() + 10f, doneButtonTextPaint);

            // Draw top hint
            canvas.drawText("Drag to move - Drag corners to resize", getWidth() / 2f, 60f, editHintPaint);
        }

        if (isPenActive && lastTouchX >= 0 && lastTouchY >= 0) {
            canvas.drawCircle(lastTouchX, lastTouchY, 32f, touchPaint);
        }
    }

    private void drawHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, 22f, handlePaint);
        canvas.drawCircle(x, y, 22f, handleBorderPaint);
        canvas.drawCircle(x, y, 6f, handleInnerDotPaint);
    }

    private boolean isSupportedTool(int toolType) {
        return toolType == MotionEvent.TOOL_TYPE_STYLUS
                || toolType == MotionEvent.TOOL_TYPE_FINGER
                || toolType == MotionEvent.TOOL_TYPE_UNKNOWN;
    }

    private void sendPosition(float touchX, float touchY, float rawPressure, boolean buttonPrimary, boolean buttonSecondary) {
        if (activeRect.width() <= 0 || activeRect.height() <= 0) return;

        float relX = (touchX - activeRect.left) / activeRect.width();
        float relY = (touchY - activeRect.top) / activeRect.height();

        relX = Math.max(0f, Math.min(1f, relX));
        relY = Math.max(0f, Math.min(1f, relY));

        float leftMm = currentOtdPosXMm - (currentOtdWidthMm / 2.0f);
        float topMm = currentOtdPosYMm - (currentOtdHeightMm / 2.0f);

        float touchMmX = leftMm + (relX * currentOtdWidthMm);
        float touchMmY = topMm + (relY * currentOtdHeightMm);

        int normX = Math.round(touchMmX * 200.0f);
        int normY = Math.round(touchMmY * 200.0f);
        normX = Math.max(0, Math.min(MAX_X, normX));
        normY = Math.max(0, Math.min(MAX_Y, normY));

        int pressure = Math.round(rawPressure * 4095);
        if (pressure <= 0) {
            pressure = 2048;
        } else {
            pressure = Math.min(4095, Math.max(0, pressure));
        }

        TabletServerService.sendTouch(true, normX, normY, pressure, buttonPrimary, buttonSecondary);
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private boolean handleEditTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float touchX = event.getX();
        float touchY = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            if (doneButtonRect.contains(touchX, touchY)) {
                setEditMode(false);
                return true;
            }

            getDigitizerBounds(digitizerBounds);
            initialRect.set(activeRect);
            dragStartX = touchX;
            dragStartY = touchY;

            float slop = 64f;
            if (dist(touchX, touchY, activeRect.left, activeRect.top) < slop) {
                dragMode = DragMode.RESIZE_TOP_LEFT;
            } else if (dist(touchX, touchY, activeRect.right, activeRect.top) < slop) {
                dragMode = DragMode.RESIZE_TOP_RIGHT;
            } else if (dist(touchX, touchY, activeRect.left, activeRect.bottom) < slop) {
                dragMode = DragMode.RESIZE_BOTTOM_LEFT;
            } else if (dist(touchX, touchY, activeRect.right, activeRect.bottom) < slop) {
                dragMode = DragMode.RESIZE_BOTTOM_RIGHT;
            } else if (activeRect.contains(touchX, touchY)) {
                dragMode = DragMode.MOVE;
            } else {
                dragMode = DragMode.NONE;
            }
            invalidate();
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (dragMode == DragMode.NONE) return true;

            float dx = touchX - dragStartX;
            float dy = touchY - dragStartY;
            float minW = 60f;
            float minH = 40f;

            switch (dragMode) {
                case MOVE: {
                    float w = initialRect.width();
                    float h = initialRect.height();
                    float left = initialRect.left + dx;
                    float top = initialRect.top + dy;
                    left = Math.max(digitizerBounds.left, Math.min(digitizerBounds.right - w, left));
                    top = Math.max(digitizerBounds.top, Math.min(digitizerBounds.bottom - h, top));
                    activeRect.set(left, top, left + w, top + h);
                    break;
                }
                case RESIZE_TOP_LEFT: {
                    float left = Math.min(initialRect.right - minW, Math.max(digitizerBounds.left, initialRect.left + dx));
                    float top = Math.min(initialRect.bottom - minH, Math.max(digitizerBounds.top, initialRect.top + dy));
                    activeRect.left = left;
                    activeRect.top = top;
                    break;
                }
                case RESIZE_TOP_RIGHT: {
                    float right = Math.max(initialRect.left + minW, Math.min(digitizerBounds.right, initialRect.right + dx));
                    float top = Math.min(initialRect.bottom - minH, Math.max(digitizerBounds.top, initialRect.top + dy));
                    activeRect.right = right;
                    activeRect.top = top;
                    break;
                }
                case RESIZE_BOTTOM_LEFT: {
                    float left = Math.min(initialRect.right - minW, Math.max(digitizerBounds.left, initialRect.left + dx));
                    float bottom = Math.max(initialRect.top + minH, Math.min(digitizerBounds.bottom, initialRect.bottom + dy));
                    activeRect.left = left;
                    activeRect.bottom = bottom;
                    break;
                }
                case RESIZE_BOTTOM_RIGHT: {
                    float right = Math.max(initialRect.left + minW, Math.min(digitizerBounds.right, initialRect.right + dx));
                    float bottom = Math.max(initialRect.top + minH, Math.min(digitizerBounds.bottom, initialRect.bottom + dy));
                    activeRect.right = right;
                    activeRect.bottom = bottom;
                    break;
                }
            }

            updateMmFromActiveRect();
            updateCachedLabel();
            syncSettingsToOtd();
            if (onAreaChangedListener != null) {
                onAreaChangedListener.onAreaChanged(currentOtdWidthMm, currentOtdHeightMm, currentOtdPosXMm, currentOtdPosYMm);
            }
            invalidate();
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            dragMode = DragMode.NONE;
            saveOtdDimensions();
            syncSettingsToOtd();
            invalidate();
            return true;
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            if (isEditMode) {
                return handleEditTouchEvent(event);
            }

            if (!isSupportedTool(event.getToolType(0))) {
                return super.onTouchEvent(event);
            }

            int action = event.getActionMasked();
            boolean buttonPrimary = event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY);
            boolean buttonSecondary = event.isButtonPressed(MotionEvent.BUTTON_STYLUS_SECONDARY);

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float touchX = event.getX();
                float touchY = event.getY();

                if (activeRect.contains(touchX, touchY)) {
                    lastTouchX = touchX;
                    lastTouchY = touchY;
                    isPenActive = true;
                    invalidate();

                    // Process historical batch points for maximum touch sampling rate (e.g. 240Hz/360Hz)
                    try {
                        int historySize = event.getHistorySize();
                        for (int i = 0; i < historySize; i++) {
                            float hX = event.getHistoricalX(i);
                            float hY = event.getHistoricalY(i);
                            if (activeRect.contains(hX, hY)) {
                                sendPosition(hX, hY, event.getHistoricalPressure(i), buttonPrimary, buttonSecondary);
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    sendPosition(touchX, touchY, event.getPressure(), buttonPrimary, buttonSecondary);
                    return true;
                } else {
                    if (isPenActive) {
                        handleOutOfRange();
                    }
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (isPenActive) {
                    handleOutOfRange();
                }
                return true;
            }

            return super.onTouchEvent(event);
        } catch (Throwable t) {
            android.util.Log.e("TabletAreaView", "Error in onTouchEvent", t);
            return true;
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        try {
            if (isEditMode) return false;

            if (!isSupportedTool(event.getToolType(0))) {
                return super.onGenericMotionEvent(event);
            }

            int action = event.getActionMasked();
            float touchX = event.getX();
            float touchY = event.getY();

            if (action == MotionEvent.ACTION_HOVER_MOVE) {
                if (activeRect.contains(touchX, touchY)) {
                    lastTouchX = touchX;
                    lastTouchY = touchY;
                    isPenActive = true;
                    invalidate();

                    sendPosition(touchX, touchY, 0f,
                            event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY),
                            event.isButtonPressed(MotionEvent.BUTTON_STYLUS_SECONDARY));
                    return true;
                } else if (isPenActive) {
                    handleOutOfRange();
                    return true;
                }
            } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
                if (isPenActive) {
                    handleOutOfRange();
                    return true;
                }
            }

            return super.onGenericMotionEvent(event);
        } catch (Throwable t) {
            android.util.Log.e("TabletAreaView", "Error in onGenericMotionEvent", t);
            return true;
        }
    }

    private void handleOutOfRange() {
        isPenActive = false;
        lastTouchX = -1;
        lastTouchY = -1;
        invalidate();
        TabletServerService.sendTouch(false, 0, 0, 0, false, false);
    }
}

