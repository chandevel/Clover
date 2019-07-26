package org.floens.chan.ui.captcha.v2;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.AppCompatImageView;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;

import org.floens.chan.R;

import java.util.ArrayList;
import java.util.List;

public class CaptchaNoJsV2Adapter extends BaseAdapter {
    private LayoutInflater inflater;
    private int imageSize = 0;

    private List<ImageChallengeInfo> imageList = new ArrayList<>();

    public CaptchaNoJsV2Adapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setImages(List<Bitmap> imageList) {
        cleanUpImages();

        for (Bitmap bitmap : imageList) {
            this.imageList.add(new ImageChallengeInfo(bitmap, false));
        }

        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return imageList.size();
    }

    @Override
    public Object getItem(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.layout_captcha_challenge_image, parent, false);
        }

        AppCompatImageView imageView = convertView.findViewById(R.id.captcha_challenge_image);
        imageView.setScaleX(1.0f);
        imageView.setScaleY(1.0f);
        ConstraintLayout blueCheckmarkHolder = convertView.findViewById(R.id.captcha_challenge_blue_checkmark_holder);
        blueCheckmarkHolder.setAlpha(0.0f);

        ConstraintLayout.LayoutParams layoutParams = new ConstraintLayout.LayoutParams(imageSize, imageSize);
        imageView.setLayoutParams(layoutParams);

        imageView.setOnClickListener((view) -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

            imageList.get(position).toggleChecked();
            boolean isChecked = imageList.get(position).isChecked;

            imageView.animate()
                    .scaleX(isChecked ? 0.8f : 1.0f)
                    .scaleY(isChecked ? 0.8f : 1.0f)
                    .setInterpolator(new DecelerateInterpolator(2.0f))
                    .setDuration(200)
                    .start();
            blueCheckmarkHolder.animate()
                    .alpha(isChecked ? 1.0f : 0.0f)
                    .setInterpolator(new DecelerateInterpolator(2.0f))
                    .setDuration(200)
                    .start();
        });

        if (position >= 0 && position <= imageList.size()) {
            imageView.setImageBitmap(imageList.get(position).getBitmap());
        }

        return convertView;
    }

    public List<Integer> getCheckedImageIds() {
        List<Integer> selectedList = new ArrayList<>();

        for (int i = 0; i < imageList.size(); i++) {
            ImageChallengeInfo info = imageList.get(i);

            if (info.isChecked()) {
                selectedList.add(i);
            }
        }

        return selectedList;
    }

    public void setImageSize(int imageSize) {
        this.imageSize = imageSize;
    }

    public void onDestroy() {
        cleanUpImages();
    }

    private void cleanUpImages() {
        for (ImageChallengeInfo imageChallengeInfo : imageList) {
            imageChallengeInfo.recycle();
        }

        imageList.clear();
    }

    public static class ImageChallengeInfo {
        private Bitmap bitmap;
        private boolean isChecked;

        public ImageChallengeInfo(Bitmap bitmap, boolean isChecked) {
            this.bitmap = bitmap;
            this.isChecked = isChecked;
        }

        public Bitmap getBitmap() {
            return bitmap;
        }

        public boolean isChecked() {
            return isChecked;
        }

        public void toggleChecked() {
            this.isChecked = !this.isChecked;
        }

        public void recycle() {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }
}
