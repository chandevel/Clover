package org.floens.chan.fragment;

import java.io.File;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.manager.ReplyManager;
import org.floens.chan.manager.ReplyManager.ReplyResponse;
import org.floens.chan.model.Loadable;
import org.floens.chan.model.Reply;
import org.floens.chan.net.ChanUrls;
import org.floens.chan.utils.ImageDecoder;
import org.floens.chan.utils.LoadView;
import org.floens.chan.utils.ViewFlipperAnimations;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.android.volley.Request.Method;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.NetworkImageView;
import com.android.volley.toolbox.StringRequest;

public class ReplyFragment extends DialogFragment {
    private int page = 0;
    
    private Loadable loadable;
    
    private final Reply draft = new Reply();
    private boolean shouldSaveDraft = true;
    
    private boolean gettingCaptcha = false;
    private String captchaChallenge = "";
    
    // Views
    private View container;
    private ViewFlipper flipper;
    private Button cancelButton;
    private Button fileButton;
    private Button fileDeleteButton;
    private Button submitButton;
    private TextView nameView;
    private TextView emailView;
    private TextView subjectView;
    private TextView commentView;
    private LoadView imageViewContainer;
    private LoadView captchaContainer;
    private TextView captchaText;
    private LoadView responseContainer;
    
    public static ReplyFragment newInstance(Loadable loadable) {
        ReplyFragment reply = new ReplyFragment();
        reply.loadable = loadable;
        return reply;
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	
    	loadable.writeToBundle(getActivity(), outState);
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        if (loadable == null && savedInstanceState != null) {
        	loadable = new Loadable();
        	loadable.readFromBundle(getActivity(), savedInstanceState);
        }
        
        if (loadable != null) {
        	setClosable(true);
        	
        	Dialog dialog = getDialog();
            Context context = getActivity();
            String title = loadable.isThreadMode() ?
            	context.getString(R.string.reply) + " /" + loadable.board + "/" + loadable.no : 
            	context.getString(R.string.reply_to_board) + " /" + loadable.board + "/";
            
            if (dialog == null) {
            	getActivity().getActionBar().setTitle(title);
            } else {
            	dialog.setTitle(title);
            }
            
            if (getDialog() != null) {
            	getDialog().setOnKeyListener(new Dialog.OnKeyListener() {
    	            @Override
    	            public boolean onKey(DialogInterface dialogInterface, int keyCode, KeyEvent event) {
    	                if (keyCode == KeyEvent.KEYCODE_BACK) {
    	                    if (page == 1) flipPage(0);
    	                    else if (page == 2) closeReply();
    	                    return true;
    	                } else return false;
    	            }
    	        });
            }
            
            Reply draft = ReplyManager.getInstance().getReplyDraft();
            
            if (TextUtils.isEmpty(draft.name)) {
            	draft.name = ChanApplication.getPreferences().getString("preference_default_name", "");
            }
            
            if (TextUtils.isEmpty(draft.email)) {
            	draft.email = ChanApplication.getPreferences().getString("preference_default_email", "");
            }
            
            nameView.setText(draft.name);
            emailView.setText(draft.email);
            subjectView.setText(draft.subject);
            commentView.setText(draft.comment);
            setFile(draft.file);
            
            getCaptcha();
        } else {
        	Log.e("Chan", "Loadable in ReplyFragment was null");
        	closeReply();
        }
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	
    	ReplyManager replyManager = ReplyManager.getInstance();
    	
    	if (shouldSaveDraft) {
            draft.name = nameView.getText().toString();
            draft.email = emailView.getText().toString();
            draft.subject = subjectView.getText().toString();
            draft.comment = commentView.getText().toString();
            
            replyManager.setReplyDraft(draft);
        } else {
            replyManager.removeReplyDraft();
            setFile(null);
        }
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	
    	ReplyManager replyManager = ReplyManager.getInstance();
    	replyManager.removeFileListener();
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        // Setup the views with listeners
        container = inflater.inflate(R.layout.reply_view, null);
        flipper = (ViewFlipper)container.findViewById(R.id.reply_flipper);
        
        nameView = (TextView)container.findViewById(R.id.reply_name);
        emailView = (TextView)container.findViewById(R.id.reply_email);
        subjectView = (TextView)container.findViewById(R.id.reply_subject);
        commentView = (TextView)container.findViewById(R.id.reply_comment);
        imageViewContainer = (LoadView)container.findViewById(R.id.reply_image);
        responseContainer = (LoadView)container.findViewById(R.id.reply_response);
        captchaContainer = (LoadView)container.findViewById(R.id.reply_captcha_container);
        captchaContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                getCaptcha();
            }
        });
        captchaText = (TextView)container.findViewById(R.id.reply_captcha);
        
        cancelButton = (Button)container.findViewById(R.id.reply_cancel);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (page == 1) {
                    flipPage(0);
                } else {
                    closeReply();
                }
            }
        });
        
        fileButton = (Button)container.findViewById(R.id.reply_file);
        fileButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                ReplyManager.getInstance().pickFile(new ReplyManager.FileListener() {
                    @Override
                    public void onFile(File file) {
                        setFile(file);
                    }
                    
                    @Override
                    public void onFileLoading() {
                        imageViewContainer.setView(null);
                    }
                });
            }
        });
        
        fileDeleteButton = (Button)container.findViewById(R.id.reply_file_delete);
        fileDeleteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                setFile(null);
            }
        });
        
        submitButton = (Button)container.findViewById(R.id.reply_submit);
        submitButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (page == 0) {
                    flipPage(1);
                } else if (page == 1) {
                    flipPage(2);
                    submit();
                };
            }
        });
        
        return container;
    }
    
    private void closeReply() {
    	if (getDialog() != null) {
    		dismiss();
    	} else {
    		getActivity().finish();
    	}
    }
    
    /**
     * Set if the dialog is able to be closed, by pressing outside of the dialog, or something else.
     */
    private void setClosable(boolean e) {
    	if (getDialog() != null) {
	        getDialog().setCanceledOnTouchOutside(e);
	        setCancelable(e);
    	}
    }
    
    /**
     * Flip to an page with an animation.
     * Sets the correct text on the cancelButton:
     * @param position 0-2
     */
    private void flipPage(int position) {
        boolean flipBack = position < page;
        
        page = position;
        
        if (flipBack) {
            flipper.setInAnimation(ViewFlipperAnimations.BACK_IN);
            flipper.setOutAnimation(ViewFlipperAnimations.BACK_OUT);
            flipper.showPrevious();
        } else {
            flipper.setInAnimation(ViewFlipperAnimations.NEXT_IN);
            flipper.setOutAnimation(ViewFlipperAnimations.NEXT_OUT);
            flipper.showNext();
        }
        
        if (page == 0) {
            cancelButton.setText(R.string.reply_cancel);
        } else if (page == 1) {
            cancelButton.setText(R.string.reply_back);
        } else if (page == 2) {
            cancelButton.setText(R.string.reply_close);
        }
    }
    
    /**
     * Set the picked image in the imageView.
     * Sets the file in the draft.
     * Call null on the file to empty the imageView.
     * @param imagePath file to image to send or null to clear
     */
    private void setFile(final File file) {
        draft.file = file;
        
        if (file == null) {
            fileDeleteButton.setEnabled(false);
            imageViewContainer.removeAllViews();
        } else {
            fileDeleteButton.setEnabled(true);
            // UI Thread
            
            final ImageView imageView = new ImageView(getActivity());
            
            new Thread(new Runnable() {
                @Override
                public void run() {
                    // Other thread
                    final Bitmap bitmap = ImageDecoder.decodeFile(file, imageViewContainer.getWidth(), 3000);
                    
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI Thread
                            if (bitmap == null) {
                                Toast.makeText(getActivity(), R.string.image_preview_failed, Toast.LENGTH_LONG).show();
                            } else {
                                imageView.setScaleType(ScaleType.CENTER_CROP);
                                imageView.setImageBitmap(bitmap);
                                
                                imageViewContainer.setView(imageView);
                            }
                        }
                    });
                }
            }).start();
        }
    }
    
    private void getCaptcha() {
        if (gettingCaptcha) return;
        gettingCaptcha = true;
        
        captchaContainer.setView(null);
        
        String url = ChanUrls.getCaptchaChallengeUrl();
        
        ChanApplication.getVolleyRequestQueue().add(new StringRequest(Method.GET, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String result) {
                if (!isVisible()) return;
                
                String challenge = ReplyManager.getChallenge(result);
                if (challenge != null) {
                    captchaChallenge = challenge;
                    String imageUrl = ChanUrls.getCaptchaImageUrl(challenge);
                    
                    NetworkImageView captchaImage = new NetworkImageView(getActivity());
                    captchaImage.setImageUrl(imageUrl, ChanApplication.getImageLoader());
                    captchaContainer.setView(captchaImage);
                    
                    gettingCaptcha = false;
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                gettingCaptcha = false;
                TextView text = new TextView(getActivity());
                text.setGravity(Gravity.CENTER);
                text.setText(R.string.reply_captcha_load_error);
                captchaContainer.setView(text);
            }
        }));
    }
    
    /**
     * Submit button clicked at page 1
     */
    private void submit() {
        submitButton.setEnabled(false);
        cancelButton.setEnabled(false);
        setClosable(false);
        
        responseContainer.setView(null);
        
        draft.name = nameView.getText().toString();
        draft.email = emailView.getText().toString();
        draft.subject = subjectView.getText().toString();
        draft.comment = commentView.getText().toString();
        draft.captchaChallenge = captchaChallenge;
        draft.captchaResponse = captchaText.getText().toString();
        draft.fileName = "image";
        
        draft.resto = loadable.isBoardMode() ? -1 : loadable.no;
        draft.board = loadable.board;
        
        ReplyManager.getInstance().sendReply(draft, new ReplyManager.ReplyListener() {
            @Override
            public void onResponse(ReplyResponse response) {
                handleSubmitResponse(response);
            }
        });
    }
    
    /**
     * Got response about or reply from ReplyManager
     * @param response
     */
    private void handleSubmitResponse(ReplyResponse response) {      
        if (response.isNetworkError || response.isUserError) {
            int resId = response.isCaptchaError ? R.string.reply_error_captcha : (response.isFileError ? R.string.reply_error_file : R.string.reply_error);
            Toast.makeText(getActivity(), resId, Toast.LENGTH_LONG).show();
            submitButton.setEnabled(true);
            cancelButton.setEnabled(true);
            setClosable(true);
            flipPage(1);
            getCaptcha();
            captchaText.setText("");
        } else if (response.isSuccessful) {
            shouldSaveDraft = false;
            Toast.makeText(getActivity(), R.string.reply_success, Toast.LENGTH_SHORT).show();
//            threadFragment.reload(); // TODO
            closeReply();
        } else {
            if (isVisible()) {
                cancelButton.setEnabled(true);
                setClosable(true);
                
                WebView webView = new WebView(getActivity());
                WebSettings settings = webView.getSettings();
                settings.setSupportZoom(true);
                
                webView.loadData(response.responseData, "text/html", null);
                
                responseContainer.setView(webView);
            }
        }
    }
}





