package org.floens.chan.manager;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.floens.chan.activity.ImagePickActivity;
import org.floens.chan.model.Reply;
import org.floens.chan.net.ChanUrls;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntity;
import ch.boye.httpclientandroidlib.entity.mime.content.FileBody;
import ch.boye.httpclientandroidlib.entity.mime.content.StringBody;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import ch.boye.httpclientandroidlib.params.BasicHttpParams;
import ch.boye.httpclientandroidlib.params.HttpConnectionParams;
import ch.boye.httpclientandroidlib.params.HttpParams;
import ch.boye.httpclientandroidlib.util.EntityUtils;

/**
 * To send an reply to 4chan.
 */
public class ReplyManager {
    private static ReplyManager instance;
    
    private static final Pattern challengePattern = Pattern.compile("challenge.?:.?'([\\w-]+)'");
    private static final int POST_TIMEOUT = 10000;
    
    private final Context context;
    private Reply draft;
    private FileListener fileListener;
    
    public ReplyManager(Context context) {
        ReplyManager.instance = this;
        this.context = context;
        draft = new Reply();
    }
    
    public static ReplyManager getInstance() {
        return instance;
    }
    
    /**
     * Clear the draft
     */
    public void removeReplyDraft() {
        draft = new Reply();
    }
    
    /**
     * Set an reply draft. 
     * @param value the draft to save.
     */
    public void setReplyDraft(Reply value) {
        draft = value;
    }
    
    /**
     * Gets the saved reply draft.
     * @return the saved draft or an empty draft.
     */
    public Reply getReplyDraft() {
        return draft;
    }
    
    /**
     * Add an quote to the comment field. Looks like >>123456789\n
     * @param no the raw no to quote to.
     */
    public void quote(int no) {
        draft.comment = draft.comment + ">>" + no + "\n";
    }
    
    /**
     * Pick an file. Starts up the ImagePickActivity.
     * @param listener FileListener to listen on.
     */
    public void pickFile(FileListener listener) {
        fileListener = listener;
        
        Intent intent = new Intent(context, ImagePickActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
    
    /**
     * Called from ImagePickActivity, sends onFileLoading to the fileListener.
     */
    public void _onPickedFileLoading() {
        if (fileListener != null) {
            fileListener.onFileLoading();
        }
    }
    
    /**
     * Called from ImagePickActivity. Sends the file to the listening fileListener, and deletes the fileListener.
     */
    public void _onPickedFile(File file) {
        if (fileListener != null) {
            fileListener.onFile(file);
        }
        fileListener = null;
    }
    
    /**
     * Delete the fileListener.
     */
    public void removeFileListener() {
        fileListener = null;
    }
    
    /**
     * Get the CAPTCHA challenge hash from an JSON response.
     * @param total The total response from the server
     * @return The pattern, or null when none was found.
     */
    public static String getChallenge(String total) {
        Matcher matcher = challengePattern.matcher(total);
        matcher.find();
        
        if (matcher.groupCount() == 1) {
            return matcher.group(1);
        } else {
            return null;
        }
    }
    
    /**
     * Send an reply off to the server. 
     * @param reply The reply object with all data needed, like captcha and the file.
     * @param listener The listener, after server response.
     */
    public void sendReply(Reply reply, ReplyListener listener) {
        HttpPost httpPost = new HttpPost(ChanUrls.getPostUrl(reply.board));
        
        MultipartEntity entity = new MultipartEntity();
        
        try {
            entity.addPart("name", new StringBody(reply.name));
            entity.addPart("email", new StringBody(reply.email));
        
            entity.addPart("sub", new StringBody(reply.subject));
            entity.addPart("com", new StringBody(reply.comment));
            
            if (reply.resto >= 0) {
                entity.addPart("resto", new StringBody(Integer.toString(reply.resto)));
            }
            
            entity.addPart("recaptcha_challenge_field", new StringBody(reply.captchaChallenge));
            entity.addPart("recaptcha_response_field", new StringBody(reply.captchaResponse));
            
            entity.addPart("mode", new StringBody("regist"));
            entity.addPart("pwd", new StringBody(""));
            
            if (reply.file != null) {
                entity.addPart("upfile", new FileBody(reply.file, reply.fileName, "application/octet-stream", "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return;
        }
        
        httpPost.setEntity(entity);
        
        new SendTask().execute(httpPost, listener);
    }
    
    /**
     * Async task to send an reply to the server.
     * Uses HttpClient. Since Android 4.4 there is an updated version of HttpClient, 4.2, given with Android.
     * However, that version causes problems with file uploading. Version 4.3 of HttpClient has been given with a library,
     * that has another namespace: ch.boye.httpclientandroidlib
     * This lib also has some fixes/improvements of HttpClient for Android.
     */
    private class SendTask extends AsyncTask<Object, Void, ReplyResponse> {
        private ReplyListener listener;
        
        @Override
        protected ReplyResponse doInBackground(Object... params) {
            HttpPost post = (HttpPost) params[0];
            listener = (ReplyListener) params[1];
            
            HttpParams httpParameters = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParameters, POST_TIMEOUT);
            HttpConnectionParams.setSoTimeout(httpParameters, POST_TIMEOUT);
            
            DefaultHttpClient client = new DefaultHttpClient(httpParameters);
            
            String responseString = null;
            
            try {
                HttpResponse response = client.execute(post);
                responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            ReplyResponse e = new ReplyResponse();
            
            if (responseString == null) {
                e.isNetworkError = true;
            } else {
                e.responseData = responseString;
                
                if (responseString.contains("No file selected")) {
                    e.isUserError = true;
                    e.isFileError = true;
                } else if (responseString.contains("You forgot to solve the CAPTCHA") || responseString.contains("You seem to have mistyped the CAPTCHA")) {
                    e.isUserError = true;
                    e.isCaptchaError = true;
                } else if (responseString.toLowerCase(Locale.ENGLISH).contains("post successful")) {
                    e.isSuccessful = true;
                }
            }
            
            return e;
        }
        
        @Override
        public void onPostExecute(ReplyResponse response) {
            listener.onResponse(response);
        }
    }
    
    public static abstract class FileListener {
        /**
         * When a file is picked.
         * @param the picked file
         */
        public abstract void onFile(File file);
        /**
         * When the file has started loading.
         */
        public abstract void onFileLoading();
    }
    
    public static abstract class ReplyListener {
        public abstract void onResponse(ReplyResponse response);
    }
    
    public static class ReplyResponse {
        /**
         * No response from server.
         */
        public boolean isNetworkError = false;
        
        /**
         * Some user error, like no file or captcha wrong.
         */
        public boolean isUserError = false;
        
        /**
         * The userError was an fileError
         */
        public boolean isFileError = false;
        
        /**
         * The userError was an captchaError
         */
        public boolean isCaptchaError = false;
        
        /**
         * Received 'post successful'
         */
        public boolean isSuccessful = false;
        
        /**
         * Raw html from the response. Used to set html in an WebView to the client, when the error was not
         * recognized by Chan.
         */
        public String responseData = "";
    }
}





