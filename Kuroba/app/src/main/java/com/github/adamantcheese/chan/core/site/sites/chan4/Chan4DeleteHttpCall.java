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
package com.github.adamantcheese.chan.core.site.sites.chan4;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.ProgressRequestBody;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.http.HttpCall;

import org.jsoup.Jsoup;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

public class Chan4DeleteHttpCall
        extends HttpCall<DeleteResponse> {
    private final DeleteRequest deleteRequest;

    public Chan4DeleteHttpCall(
            @NonNull NetUtilsClasses.ResponseResult<DeleteResponse> callback,
            DeleteRequest deleteRequest
    ) {
        super(callback);
        this.deleteRequest = deleteRequest;
    }

    @Override
    public void setup(
            Request.Builder requestBuilder, @Nullable ProgressRequestBody.ProgressRequestListener progressListener
    ) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        formBuilder.add(Integer.toString(deleteRequest.post.no), "delete");
        if (deleteRequest.imageOnly) {
            formBuilder.add("onlyimgdel", "on");
        }
        formBuilder.add("mode", "usrdel");
        formBuilder.add("pwd", deleteRequest.savedReply.password);

        requestBuilder.url(deleteRequest.post.board.site.endpoints().delete(deleteRequest.post));
        requestBuilder.post(formBuilder.build());
    }

    @Override
    public DeleteResponse convert(Response response)
            throws IOException {
        DeleteResponse deleteResponse = new DeleteResponse();
        String responseString = response.body().string();
        if (responseString.contains("errmsg")) {
            deleteResponse.errorMessage = Jsoup.parse(responseString).select("#errmsg").html();
        } else {
            deleteResponse.deleted = true;
        }
        return deleteResponse;
    }
}
