package org.akvo.mapexperiments;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.annotation.NonNull;

class BitmapGenerator {

    private static final int POINT_COLOR_FILL = 0x55FFFFFF;
    private static final int SOLID_COLOR = 0xFF00A79D;
    private static final int BITMAP_SIZE = 20;

    BitmapGenerator() {
    }

    @NonNull
    Bitmap getBitmap() {
        Bitmap bmp = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint solid = new Paint();
        solid.setColor(SOLID_COLOR);
        solid.setAntiAlias(true);
        Paint fill = new Paint();
        fill.setAntiAlias(true);
        fill.setColor(POINT_COLOR_FILL);

        final float center = BITMAP_SIZE / 2f;
        canvas.drawCircle(center, center, center, solid); // Outer circle
        canvas.drawCircle(center, center, center * 0.9f, fill); // Fill circle
        canvas.drawCircle(center, center, center * 0.25f, solid); // Inner circle
        return bmp;
    }
}