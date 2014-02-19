package org.floens.chan.entity;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.floens.chan.net.ChanUrls;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

/**
 * Contains all data needer to represent a single post. 
 */
public class Post {
    public String board;
    public boolean isOP = false;
    public int no = -1;
    public int resto = -1;
    public String date;
    public String name = "";
    private String rawComment;
    public CharSequence comment = "";
    public String subject = "";
    public String tim;
    public String ext;
    public String filename;
    public int replies = -1;
    public int imageWidth;
    public int imageHeight;
    public boolean hasImage = false;
    public String thumbnailUrl;
    public String imageUrl;
    public boolean sticky = false;
    public boolean closed = false;
    public String tripcode = "";
    public String id = "";
    public String country = "";
    public String countryName = "";
    public long time = 0;
    public String email = "";
    
    public final ArrayList<PostLinkable> linkables = new ArrayList<PostLinkable>();
    
    public Post() {
    }
    
    public void setComment(String e) {
        rawComment = e;
    }
    
    /**
     * Finish up the data
     * @return false if this data is invalid
     */
    public boolean finish() {
        if (board == null) return false;
        
        if (no < 0 || resto < 0 || date == null) return false;
        
        isOP = resto == 0;
        
        if (isOP && replies < 0) return false;
        
        if (ext != null) hasImage = true;
        
        if (hasImage) {
            if (filename == null || tim == null || ext == null || imageWidth <= 0 || imageHeight <= 0) return false;
            
            thumbnailUrl = ChanUrls.getThumbnailUrl(board, tim);
            imageUrl = ChanUrls.getImageUrl(board, tim, ext);
        }
        
        if (rawComment != null) {
            comment = parseComment(rawComment);
        }
        
        try {
            if (!TextUtils.isEmpty(name)) {
                name = Parser.unescapeEntities(name, false);
            }
            
            if (!TextUtils.isEmpty(subject)) {
                subject = Parser.unescapeEntities(subject, false);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        return true;
    }
    
    private CharSequence parseComment(String commentRaw) {
        CharSequence total = new SpannableString("");
        
        try {
            String comment = commentRaw.replace("<wbr>", "");
            
            Document document = Jsoup.parseBodyFragment(comment);
            
            List<Node> nodes = document.body().childNodes();
            
            for (Node node : nodes) {
                String nodeName = node.nodeName();
                
                if (node instanceof TextNode) {
                    String text = ((TextNode)node).text();
                    
                    // Find url's in the text node                    
                    if (text.contains("://")) {
                        String[] parts = text.split("\\s");
                        
                        for (String item : parts) {
                            try {
                                URL url = new URL(item);
                                
                                SpannableString link = new SpannableString(url.toString());
                                link.setSpan(new ForegroundColorSpan(Color.argb(255, 0, 0, 180)), 0, link.length(), 0);
                                
                                linkables.add(new PostLinkable(item, item, PostLinkable.Type.LINK));
                                
                                total = TextUtils.concat(total, link, " ");
                            } catch(Exception e) {
                                total = TextUtils.concat(total, item, " ");
                            }
                        }
                    } else {
                        total = TextUtils.concat(total, text);
                    }
                } else if (nodeName.equals("br")) {
                    total = TextUtils.concat(total, "\n");
                } else if (nodeName.equals("span")){
                    Element span = (Element)node;
                    
                    SpannableString quote = new SpannableString(span.text());
                    quote.setSpan(new ForegroundColorSpan(Color.argb(255, 120, 153, 34)), 0, quote.length(), 0);
                    
                    total = TextUtils.concat(total, quote);
                } else if (nodeName.equals("a")) {
                    Element anchor = (Element)node;
                    
                    SpannableString link = new SpannableString(anchor.text());
                    link.setSpan(new ForegroundColorSpan(Color.argb(255, 221, 0, 0)), 0, link.length(), 0);
                    
                    total = TextUtils.concat(total, link);
                    
                    linkables.add(new PostLinkable(anchor.text(), anchor.attr("href"), PostLinkable.Type.QUOTE));
                } else {
                    // Unknown tag, add the inner part
                    if (node instanceof Element) {
                        total = TextUtils.concat(total, ((Element)node).text());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return total;
    }
}





