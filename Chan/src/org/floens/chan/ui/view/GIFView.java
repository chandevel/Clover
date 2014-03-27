package org.floens.chan.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

public class GIFView extends View {
    private Movie movie;
    private long movieStart;
    
    public GIFView(Context activity) {
        super(activity);
        init();
    }
    
    public GIFView(Context activity, AttributeSet attbs) {
        super(activity, attbs);
        init();
    }
    
    public GIFView(Context activity, AttributeSet attbs, int style) {
        super(activity, attbs, style);
        init();
    }
    
    private void init() {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        setLayerType(LAYER_TYPE_SOFTWARE, paint);
    }
    
    public boolean setData(byte[] array) {
        Movie movie = Movie.decodeByteArray(array, 0, array.length);
        
        return onMovieLoaded(movie);
    }
    
    private boolean onMovieLoaded(Movie movie) {
        if (movie != null) {
            this.movie = movie;
            invalidate();
            return true;
        } else {
            return false;
        }
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);
        
        super.onDraw(canvas);
        
        long now = SystemClock.uptimeMillis();
        
        if (movieStart == 0) { // first time
            movieStart = now;
        }
        
        if (movie != null) {
            int dur = movie.duration();
            
            if (dur == 0) {
                dur = 1000;
            }
            
            int relTime = (int)((now - movieStart) % dur);
            movie.setTime(relTime);
            
            canvas.save();
            
            float width = (float)getWidth() / (float)movie.width();
            float height = (float)getHeight() / (float)movie.height();
            
            float scale = width > height ? height : width;
            
            int widthPixels = (int) (movie.width() * scale);
            int heightPixels = (int) (movie.height() * scale);
            
            canvas.translate((getWidth() - widthPixels) / 2, (getHeight() - heightPixels) / 2);
            
            canvas.scale(scale, scale);
            
            movie.draw(canvas, 0, 0);
            
            canvas.restore();
            
            invalidate();
        }
    }
}





