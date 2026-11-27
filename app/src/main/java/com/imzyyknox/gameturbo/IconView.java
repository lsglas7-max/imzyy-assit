package com.imzyyknox.gameturbo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Ikon vektor buatan IMZYY KNOX (tanpa emoji). Semua digambar di kanvas 24x24. */
final class IconView extends View {
    static final int LOGO = 0, BOLT = 1, FPS = 2, STYLE = 3, TOOLS = 4, APPS = 5, CACHE = 6, INFO = 7, CLOSE = 8,
            MIN = 9, BACK = 10, GAME = 11, PING = 12, RAM = 13, BATTERY = 14, THERMO = 15, FLOAT = 16, STOP = 17,
            PLAY = 18, CHECK = 19, SHIELD = 20, MIC = 21, DOT = 22, PLUS = 23, GPU = 24, FAN = 25;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), hole = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path();
    private final RectF rf = new RectF();
    private final Shader silver = new LinearGradient(0, 0, 24, 24, 0xFFFFFFFF, 0xFF8E8E96, Shader.TileMode.CLAMP);
    private int type, color = 0xFFFFFFFF;
    private boolean lux;

    IconView(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE); line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND); line.setStrokeWidth(1.8f);
        fill.setStyle(Paint.Style.FILL);
        hole.setStyle(Paint.Style.FILL); hole.setColor(0xFF0B0B0C);
    }

    IconView set(int type, int color, boolean lux) { this.type = type; this.color = color; this.lux = lux; invalidate(); return this; }
    void setColor(int color, boolean lux) { this.color = color; this.lux = lux; invalidate(); }

    @Override protected void onMeasure(int w, int h) {
        int d = (int) (24 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(d, w), resolveSize(d, h));
    }

    private void ln(Canvas c, float a, float b, float x, float y) { c.drawLine(a, b, x, y, line); }
    private void rr(Canvas c, float l, float t, float r, float b, float rad, boolean soft) {
        rf.set(l, t, r, b);
        if (soft) { int a = fill.getAlpha(); fill.setAlpha(46); c.drawRoundRect(rf, rad, rad, fill); fill.setAlpha(a); }
        c.drawRoundRect(rf, rad, rad, line);
    }
    private void rrFill(Canvas c, float l, float t, float r, float b, float rad) { rf.set(l, t, r, b); c.drawRoundRect(rf, rad, rad, fill); }
    private void closePath(Canvas c, boolean soft) {
        if (soft) { int a = fill.getAlpha(); fill.setAlpha(46); c.drawPath(p, fill); fill.setAlpha(a); }
        c.drawPath(p, line);
    }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
        c.scale(s, s);
        line.setColor(color); fill.setColor(color);
        line.setShader(lux ? silver : null); fill.setShader(lux ? silver : null);
        p.reset();
        switch (type) {
            case LOGO:
                p.moveTo(12, 2); p.lineTo(20.2f, 6.8f); p.lineTo(20.2f, 17.2f); p.lineTo(12, 22); p.lineTo(3.8f, 17.2f); p.lineTo(3.8f, 6.8f); p.close();
                closePath(c, true);
                p.reset();
                p.moveTo(13.2f, 6.6f); p.lineTo(9, 12.6f); p.lineTo(11.7f, 12.6f); p.lineTo(10.8f, 17.4f); p.lineTo(15, 11.4f); p.lineTo(12.3f, 11.4f); p.close();
                c.drawPath(p, fill);
                break;
            case BOLT:
                p.moveTo(13, 2.5f); p.lineTo(5, 13.5f); p.lineTo(11, 13.5f); p.lineTo(10, 21.5f); p.lineTo(19, 10); p.lineTo(13, 10); p.close();
                closePath(c, true);
                break;
            case FPS:
                rf.set(3, 4, 21, 22); c.drawArc(rf, 150, 240, false, line);
                ln(c, 12, 13, 16.5f, 8.5f);
                c.drawCircle(12, 13, 1.7f, fill);
                break;
            case STYLE: {
                float[] ys = {6, 12, 18}, xs = {15, 8, 13};
                for (int i = 0; i < 3; i++) {
                    ln(c, 4, ys[i], 20, ys[i]);
                    c.drawCircle(xs[i], ys[i], 2.4f, hole);
                    c.drawCircle(xs[i], ys[i], 2.2f, line);
                }
                break;
            }
            case TOOLS:
                rr(c, 3, 8, 21, 20, 3, true);
                p.moveTo(9, 8); p.lineTo(9, 6); p.quadTo(9, 5, 10, 5); p.lineTo(14, 5); p.quadTo(15, 5, 15, 6); p.lineTo(15, 8);
                c.drawPath(p, line);
                ln(c, 3, 13.5f, 21, 13.5f);
                rrFill(c, 10.4f, 12.2f, 13.6f, 15.2f, 1);
                break;
            case APPS:
                rr(c, 4, 4, 10.5f, 10.5f, 2, true); rr(c, 13.5f, 4, 20, 10.5f, 2, true);
                rr(c, 4, 13.5f, 10.5f, 20, 2, true); rr(c, 13.5f, 13.5f, 20, 20, 2, true);
                break;
            case CACHE:
                ln(c, 19.5f, 4.5f, 11.85f, 12.65f);
                p.moveTo(9.2f, 10); p.lineTo(14.5f, 15.3f); p.lineTo(10.3f, 19.8f); p.lineTo(4.2f, 13.7f); p.close();
                closePath(c, true);
                ln(c, 8.2f, 14.2f, 10.9f, 16.9f);
                ln(c, 19, 14.8f, 19, 19.2f); ln(c, 16.8f, 17, 21.2f, 17);
                break;
            case INFO:
                c.drawCircle(12, 12, 9, line);
                c.drawCircle(12, 7.9f, 1.15f, fill);
                ln(c, 12, 11, 12, 16.8f);
                break;
            case CLOSE:
                ln(c, 6.5f, 6.5f, 17.5f, 17.5f); ln(c, 17.5f, 6.5f, 6.5f, 17.5f);
                break;
            case MIN:
                ln(c, 6.5f, 16.5f, 17.5f, 16.5f);
                break;
            case BACK:
                p.moveTo(14.5f, 5.5f); p.lineTo(8, 12); p.lineTo(14.5f, 18.5f);
                c.drawPath(p, line);
                break;
            case GAME:
                rr(c, 2.5f, 7, 21.5f, 18, 5.5f, true);
                ln(c, 7.5f, 10.5f, 7.5f, 14.5f); ln(c, 5.5f, 12.5f, 9.5f, 12.5f);
                c.drawCircle(15.8f, 11.3f, 1.15f, fill); c.drawCircle(18.2f, 13.7f, 1.15f, fill);
                break;
            case PING:
                for (float r : new float[]{4.5f, 8.5f, 12.5f}) { rf.set(12 - r, 19 - r, 12 + r, 19 + r); c.drawArc(rf, 225, 90, false, line); }
                c.drawCircle(12, 19, 1.3f, fill);
                break;
            case RAM:
                rr(c, 6, 6, 18, 18, 2.5f, false);
                rr(c, 9.5f, 9.5f, 14.5f, 14.5f, 1, true);
                for (float v : new float[]{9, 12, 15}) { ln(c, v, 3, v, 6); ln(c, v, 18, v, 21); ln(c, 3, v, 6, v); ln(c, 18, v, 21, v); }
                break;
            case BATTERY:
                rr(c, 2.5f, 7.5f, 19, 16.5f, 2.5f, false);
                rrFill(c, 20, 10.5f, 21.8f, 13.5f, .8f);
                rrFill(c, 4.8f, 9.8f, 12, 14.2f, 1);
                break;
            case THERMO:
                rr(c, 9.5f, 2.5f, 14.5f, 15, 2.5f, false);
                c.drawCircle(12, 17.8f, 3.6f, line);
                ln(c, 12, 7, 12, 17.8f);
                c.drawCircle(12, 17.8f, 1.8f, fill);
                break;
            case FLOAT:
                rr(c, 3, 3, 15, 13, 2.2f, false);
                rf.set(9, 10, 21, 21); c.drawRoundRect(rf, 2.2f, 2.2f, hole);
                rr(c, 9, 10, 21, 21, 2.2f, true);
                ln(c, 9, 13.5f, 21, 13.5f);
                break;
            case STOP:
                c.drawCircle(12, 12, 9, line);
                rrFill(c, 8.8f, 8.8f, 15.2f, 15.2f, 1.4f);
                break;
            case PLAY:
                p.moveTo(8.5f, 5.5f); p.lineTo(18.5f, 12); p.lineTo(8.5f, 18.5f); p.close();
                closePath(c, true);
                break;
            case CHECK:
                p.moveTo(5, 12.5f); p.lineTo(10, 17.5f); p.lineTo(19, 7.5f);
                c.drawPath(p, line);
                break;
            case SHIELD:
                p.moveTo(12, 2.5f); p.lineTo(19.5f, 5.5f); p.lineTo(19.5f, 11.5f);
                p.quadTo(19.5f, 17.5f, 12, 21.5f); p.quadTo(4.5f, 17.5f, 4.5f, 11.5f); p.lineTo(4.5f, 5.5f); p.close();
                closePath(c, true);
                p.reset(); p.moveTo(8.5f, 12); p.lineTo(11, 14.5f); p.lineTo(15.5f, 9.5f);
                c.drawPath(p, line);
                break;
            case MIC:
                rr(c, 9, 3, 15, 14, 3, true);
                rf.set(6, 7, 18, 19.5f); c.drawArc(rf, 0, 180, false, line);
                ln(c, 12, 19.5f, 12, 22);
                break;
            case GPU:
                rr(c, 2.5f, 6.5f, 21.5f, 17.5f, 2.5f, true);
                c.drawCircle(9, 12, 3.2f, line);
                ln(c, 9, 9.8f, 9, 14.2f); ln(c, 6.8f, 12, 11.2f, 12);
                ln(c, 15, 10, 19, 10); ln(c, 15, 12.5f, 19, 12.5f); ln(c, 15, 15, 17.5f, 15);
                ln(c, 5, 19.8f, 8, 19.8f); ln(c, 10.5f, 19.8f, 13.5f, 19.8f);
                break;
            case FAN:
                c.drawCircle(12, 12, 9.5f, line); c.drawCircle(12, 12, 1.6f, fill);
                ln(c, 12, 10.4f, 12, 4.2f); ln(c, 13.5f, 12.9f, 18.9f, 15.9f); ln(c, 10.5f, 12.9f, 5.1f, 15.9f);
                break;
            case PLUS:
                ln(c, 12, 5, 12, 19); ln(c, 5, 12, 19, 12);
                break;
            default:
                c.drawCircle(12, 12, 4, fill);
        }
        c.restore();
    }
}
