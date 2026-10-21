package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    /** Frosted glass over a wallpaper: its blurred copy (WallpaperPlayer.backdrop), a white tint, a lit edge. */
    private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint litEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix frostMatrix = new Matrix();
    private final int[] location = new int[2];
    private Bitmap backdrop;
    private float edgeTop = Float.NaN, edgeBottom = Float.NaN;

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names) {
        this.dockSize = dockSize;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        band.setColor(translucent ? 0x8C121014 : 0xFF141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(translucent ? 0x26FFFFFF : 0x1FFFFFFF);
        tint.setColor(0x1FFFFFFF);
        litEdge.setStyle(Paint.Style.STROKE);
        litEdge.setStrokeWidth(1.5f * density);
    }

    /** Switches the band to frosted glass over this blurred wallpaper copy; null goes back to the plain band. */
    void setBackdrop(Bitmap backdrop) {
        this.backdrop = backdrop;
        frost.setShader(backdrop == null ? null
                : new BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        float side = Math.max(0f, Math.min(pad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        rect.inset(-side, -pad);
        rect.bottom += namesRoom;
        float radius = 26 * density;
        if (backdrop == null) {
            c.drawRoundRect(rect, radius, radius, band);
            c.drawRoundRect(rect, radius, radius, edge);
            return;
        }
        // The backdrop covers the whole screen; map it from screen to this grid's canvas.
        parent.getLocationOnScreen(location);
        frostMatrix.setScale(WallpaperPlayer.BACKDROP_SCALE, WallpaperPlayer.BACKDROP_SCALE);
        frostMatrix.postTranslate(-location[0], -location[1]);
        frost.getShader().setLocalMatrix(frostMatrix);
        c.drawRoundRect(rect, radius, radius, frost);
        c.drawRoundRect(rect, radius, radius, tint);
        if (rect.top != edgeTop || rect.bottom != edgeBottom) {
            // tvOS glass: the edge catches light at the top and fades towards the bottom.
            edgeTop = rect.top;
            edgeBottom = rect.bottom;
            litEdge.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    0x73FFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP));
        }
        c.drawRoundRect(rect, radius, radius, litEdge);
    }
}
