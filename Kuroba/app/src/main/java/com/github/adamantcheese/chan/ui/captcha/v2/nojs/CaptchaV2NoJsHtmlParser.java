/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.captcha.v2.nojs;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.features.html_styling.impl.HtmlNodeTreeAction;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import kotlin.io.FilesKt;
import okhttp3.Request;
import okhttp3.Response;

public class CaptchaV2NoJsHtmlParser {
    @NonNull
    public CaptchaV2NoJsInfo parseDocument(Document captchaPage)
            throws Exception {
        BackgroundUtils.ensureBackgroundThread();

        CaptchaV2NoJsInfo captchaV2NoJsInfo = new CaptchaV2NoJsInfo();
        captchaV2NoJsInfo.captchaType =
                CaptchaV2NoJsInfo.CaptchaType.fromCheckboxesCount(captchaPage.select("input[type=checkbox]").size());
        captchaV2NoJsInfo.cParameter = captchaPage.select("input[name=c]").attr("value");

        String bareTitle = captchaPage
                .select(".rc-imageselect-desc-no-canonical,.rc-imageselect-desc")
                .first()
                .wholeText()
                .replace("Select all images", "Tap all");
        captchaV2NoJsInfo.captchaTitle = HtmlNodeTreeAction.fromHtml(bareTitle, captchaPage.baseUri());

        downloadAndStoreImage(captchaPage.select(".fbc-imageselect-payload").first().absUrl("src"));
        captchaV2NoJsInfo.challengeImages =
                decodeImagesFromFile(captchaV2NoJsInfo.captchaType.columnCount, captchaV2NoJsInfo.captchaType.rowCount);

        if (!captchaV2NoJsInfo.isValid()) {
            throw new Exception("Captcha info invalid! " + captchaV2NoJsInfo);
        }

        return captchaV2NoJsInfo;
    }

    private void downloadAndStoreImage(String fullUrl)
            throws Exception {
        Request request = new Request.Builder().url(fullUrl).build();

        try (Response response = NetUtils.applicationClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Could not download challenge image, status code = " + response.code());
            }

            FilesKt.writeBytes(getChallengeImageFile(), response.body().bytes());
        }
    }

    private File getChallengeImageFile()
            throws IOException {
        File imageFile = new File(getCacheDir(), "challenge_image_file");

        if (!imageFile.exists()) {
            imageFile.createNewFile();
        }

        return imageFile;
    }

    private List<Bitmap> decodeImagesFromFile(int columns, int rows)
            throws IOException {
        File challengeImageFile = getChallengeImageFile();
        Bitmap originalBitmap = BitmapFactory.decodeFile(challengeImageFile.getAbsolutePath());
        List<Bitmap> resultImages = new ArrayList<>(columns * rows);

        try {
            int imageWidth = originalBitmap.getWidth() / columns;
            int imageHeight = originalBitmap.getHeight() / rows;

            for (int column = 0; column < columns; ++column) {
                for (int row = 0; row < rows; ++row) {
                    Bitmap imagePiece = Bitmap.createBitmap(originalBitmap,
                            row * imageWidth,
                            column * imageHeight,
                            imageWidth,
                            imageHeight
                    );

                    resultImages.add(imagePiece);
                }
            }

            return resultImages;
        } catch (Throwable error) {
            for (Bitmap bitmap : resultImages) {
                bitmap.recycle();
            }

            resultImages.clear();
            return resultImages;
        } finally {
            if (originalBitmap != null) {
                originalBitmap.recycle();
            }
        }
    }
}
