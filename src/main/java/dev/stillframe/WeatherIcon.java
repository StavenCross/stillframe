package dev.stillframe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Draws one original local vector symbol for the current condition. */
final class WeatherIcon extends View {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private NwsWeather.Icon condition = NwsWeather.Icon.THERMOMETER;

    WeatherIcon(Context context, int color) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(1.65f);
        stroke.setColor(color);
    }

    void condition(NwsWeather.Icon condition) {
        if (this.condition != condition) {
            this.condition = condition;
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        canvas.save();
        canvas.translate((getWidth() - side) / 2, (getHeight() - side) / 2);
        canvas.scale(side / 24, side / 24);
        switch (condition) {
            case CLEAR:
                sun(canvas, 12, 12, 4, 7, 9.5f);
                break;
            case NIGHT:
                path.reset();
                path.moveTo(12.5f, 3);
                path.cubicTo(3, 2, 1, 15, 8, 19);
                path.cubicTo(13, 22, 19, 19, 21, 14.5f);
                path.cubicTo(13, 17, 8, 10, 12.5f, 3);
                path.close();
                canvas.drawPath(path, stroke);
                break;
            case PARTLY_CLOUDY:
                canvas.drawArc(4.2f, 4.2f, 9.8f, 9.8f, 115, 210, false, stroke);
                line(canvas, 7, 1.4f, 7, 2.5f);
                line(canvas, 3, 3, 3.8f, 3.8f);
                line(canvas, 1.4f, 7, 2.5f, 7);
                cloud(canvas, 0);
                break;
            case CLOUD:
                cloud(canvas, 0);
                break;
            case RAIN:
                cloud(canvas, -4);
                line(canvas, 7, 18, 6, 21);
                line(canvas, 12, 18, 11, 21);
                line(canvas, 17, 18, 16, 21);
                break;
            case SNOW:
                canvas.save();
                for (int step = 0; step < 3; step++) {
                    canvas.rotate(60, 12, 12);
                    line(canvas, 12, 3, 12, 21);
                    line(canvas, 9, 5.5f, 12, 8);
                    line(canvas, 15, 5.5f, 12, 8);
                    line(canvas, 9, 18.5f, 12, 16);
                    line(canvas, 15, 18.5f, 12, 16);
                }
                canvas.restore();
                break;
            case THUNDER:
                cloud(canvas, -4);
                path.reset();
                path.moveTo(13, 15);
                path.lineTo(10, 19);
                path.lineTo(14, 19);
                path.lineTo(11, 22);
                canvas.drawPath(path, stroke);
                break;
            case FOG:
                cloud(canvas, -4);
                line(canvas, 4, 18, 20, 18);
                line(canvas, 7, 21, 17, 21);
                break;
            case WIND:
                path.reset();
                path.moveTo(3, 8);
                path.lineTo(15, 8);
                path.cubicTo(20, 8, 19, 3, 16, 3);
                path.moveTo(3, 12);
                path.lineTo(18, 12);
                path.cubicTo(22, 12, 22, 16, 19, 16);
                path.moveTo(3, 16);
                path.lineTo(11, 16);
                path.cubicTo(15, 16, 15, 21, 12, 21);
                canvas.drawPath(path, stroke);
                break;
            case THERMOMETER:
                path.reset();
                path.moveTo(10, 14.2f);
                path.lineTo(10, 5);
                path.cubicTo(10, 2.5f, 14, 2.5f, 14, 5);
                path.lineTo(14, 14.2f);
                path.cubicTo(18, 17, 15, 21, 12, 21);
                path.cubicTo(9, 21, 6, 17, 10, 14.2f);
                canvas.drawPath(path, stroke);
                line(canvas, 12, 9, 12, 17.5f);
                break;
        }
        canvas.restore();
    }

    private void sun(Canvas canvas, float x, float y, float radius, float inner, float outer) {
        canvas.drawCircle(x, y, radius, stroke);
        canvas.save();
        for (int ray = 0; ray < 8; ray++) {
            line(canvas, x, y - inner, x, y - outer);
            canvas.rotate(45, x, y);
        }
        canvas.restore();
    }

    private void cloud(Canvas canvas, float offset) {
        canvas.save();
        canvas.translate(0, offset);
        path.reset();
        path.moveTo(7.5f, 18.5f);
        path.cubicTo(2.2f, 18.5f, 2.2f, 12, 7.6f, 12);
        path.cubicTo(8.8f, 6.1f, 16, 6.2f, 17.4f, 11.6f);
        path.cubicTo(23, 11.6f, 23, 18.5f, 18.5f, 18.5f);
        path.close();
        canvas.drawPath(path, stroke);
        canvas.restore();
    }

    private void line(Canvas canvas, float x1, float y1, float x2, float y2) {
        canvas.drawLine(x1, y1, x2, y2, stroke);
    }
}
