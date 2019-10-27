package com.github.adamantcheese.chan.core.mapper;

import android.text.SpannableString;
import android.text.style.CharacterStyle;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.save.spans.SerializableAbsoluteSizeSpan;
import com.github.adamantcheese.chan.core.model.save.spans.SerializableBackgroundColorSpan;
import com.github.adamantcheese.chan.core.model.save.spans.SerializableForegroundColorSpan;
import com.github.adamantcheese.chan.core.model.save.spans.SerializablePostLinkableSpan;
import com.github.adamantcheese.chan.core.model.save.spans.SerializableSpannableString;
import com.github.adamantcheese.chan.core.model.save.spans.SerializableStyleSpan;
import com.github.adamantcheese.chan.core.model.save.spans.SerializableTypefaceSpan;
import com.github.adamantcheese.chan.core.model.save.spans.linkable.PostLinkThreadLinkValue;
import com.github.adamantcheese.chan.core.model.save.spans.linkable.PostLinkableBoardLinkValue;
import com.github.adamantcheese.chan.core.model.save.spans.linkable.PostLinkableLinkValue;
import com.github.adamantcheese.chan.core.model.save.spans.linkable.PostLinkableQuoteValue;
import com.github.adamantcheese.chan.core.model.save.spans.linkable.PostLinkableSearchLinkValue;
import com.github.adamantcheese.chan.core.model.save.spans.linkable.PostLinkableSpoilerValue;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.BackgroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.google.gson.Gson;

public class SpannableStringMapper {
    private static final String TAG = "SpannableStringMapper";
    private static final Gson gson = new Gson()
            .newBuilder()
            .create();

    @Nullable
    public static SerializableSpannableString serializeSpannableString(@Nullable CharSequence charSequence) {
        if (charSequence == null || charSequence.length() == 0) {
            return null;
        }

        SerializableSpannableString serializableSpannableString = new SerializableSpannableString();
        SpannableString spannableString = new SpannableString(charSequence);
        CharacterStyle[] spans = spannableString.getSpans(0, spannableString.length(), CharacterStyle.class);

        for (CharacterStyle span : spans) {
            int spanStart = spannableString.getSpanStart(span);
            int spanEnd = spannableString.getSpanEnd(span);
            int flags = spannableString.getSpanFlags(span);

            if (span instanceof ForegroundColorSpanHashed) {
                ForegroundColorSpanHashed fcsh = (ForegroundColorSpanHashed) span;

                SerializableForegroundColorSpan serializableForegroundColorSpan
                        = new SerializableForegroundColorSpan(fcsh.getForegroundColor());

                SerializableSpannableString.SpanInfo spanInfo = new SerializableSpannableString.SpanInfo(
                        SerializableSpannableString.SpanType.ForegroundColorSpanHashedType,
                        spanStart,
                        spanEnd,
                        flags);

                spanInfo.setSpanData(gson.toJson(serializableForegroundColorSpan));
                serializableSpannableString.addSpanInfo(spanInfo);
            }

            if (span instanceof BackgroundColorSpanHashed) {
                BackgroundColorSpanHashed bcsh = (BackgroundColorSpanHashed) span;

                SerializableBackgroundColorSpan serializableBackgroundColorSpan
                        = new SerializableBackgroundColorSpan(bcsh.getBackgroundColor());

                SerializableSpannableString.SpanInfo spanInfo = new SerializableSpannableString.SpanInfo(
                        SerializableSpannableString.SpanType.BackgroundColorSpanHashedType,
                        spanStart,
                        spanEnd,
                        flags);

                spanInfo.setSpanData(gson.toJson(serializableBackgroundColorSpan));
                serializableSpannableString.addSpanInfo(spanInfo);
            }

            if (span instanceof StrikethroughSpan) {
                SerializableSpannableString.SpanInfo spanInfo = new SerializableSpannableString.SpanInfo(
                        SerializableSpannableString.SpanType.StrikethroughSpanType,
                        spanStart,
                        spanEnd,
                        flags);

                spanInfo.setSpanData(null);
                serializableSpannableString.addSpanInfo(spanInfo);
            }

            if (span instanceof StyleSpan) {
                StyleSpan ss = (StyleSpan) span;

                SerializableStyleSpan serializableStyleSpan = new SerializableStyleSpan(
                        ss.getStyle());

                SerializableSpannableString.SpanInfo spanInfo = new SerializableSpannableString.SpanInfo(
                        SerializableSpannableString.SpanType.StyleSpanType,
                        spanStart,
                        spanEnd,
                        flags);

                spanInfo.setSpanData(gson.toJson(serializableStyleSpan));
                serializableSpannableString.addSpanInfo(spanInfo);
            }

            if (span instanceof TypefaceSpan) {
                TypefaceSpan ts = (TypefaceSpan) span;

                SerializableTypefaceSpan serializableTypefaceSpan = new SerializableTypefaceSpan(
                        ts.getFamily());

                SerializableSpannableString.SpanInfo spanInfo = new SerializableSpannableString.SpanInfo(
                        SerializableSpannableString.SpanType.TypefaceSpanType,
                        spanStart,
                        spanEnd,
                        flags);

                spanInfo.setSpanData(gson.toJson(serializableTypefaceSpan));
                serializableSpannableString.addSpanInfo(spanInfo);
            }

            if (span instanceof AbsoluteSizeSpanHashed) {
                AbsoluteSizeSpanHashed assh = (AbsoluteSizeSpanHashed) span;

                SerializableAbsoluteSizeSpan serializableAbsoluteSizeSpan =
                        new SerializableAbsoluteSizeSpan(assh.getSize());

                SerializableSpannableString.SpanInfo spanInfo = new SerializableSpannableString.SpanInfo(
                        SerializableSpannableString.SpanType.AbsoluteSizeSpanHashed,
                        spanStart,
                        spanEnd,
                        flags);

                spanInfo.setSpanData(gson.toJson(serializableAbsoluteSizeSpan));
                serializableSpannableString.addSpanInfo(spanInfo);
            }

            if (span instanceof PostLinkable) {
                PostLinkable pl = (PostLinkable) span;
                serializePostLinkable(serializableSpannableString, pl, spanStart, spanEnd, flags);
            }
        }

        serializableSpannableString.setText(charSequence.toString());
        return serializableSpannableString;
    }

    private static void serializePostLinkable(
            SerializableSpannableString serializableSpannableString,
            PostLinkable postLinkable,
            int spanStart,
            int spanEnd,
            int flags) {
        SerializableSpannableString.SpanInfo spanInfo = new SerializableSpannableString.SpanInfo(
                SerializableSpannableString.SpanType.PostLinkable,
                spanStart,
                spanEnd,
                flags);
        SerializablePostLinkableSpan serializablePostLinkableSpan = new SerializablePostLinkableSpan(
                postLinkable.key.toString());
        String postLinkableValueJson;

        switch (postLinkable.type) {
            case QUOTE:
                postLinkableValueJson = gson.toJson(new PostLinkableQuoteValue(
                        SerializablePostLinkableSpan.PostLinkableType.Quote,
                        (int) postLinkable.value));
                serializablePostLinkableSpan.setPostLinkableType(
                        SerializablePostLinkableSpan.PostLinkableType.Quote.getTypeValue());
                break;
            case LINK:
                postLinkableValueJson = gson.toJson(new PostLinkableLinkValue(
                        SerializablePostLinkableSpan.PostLinkableType.Link,
                        (String) postLinkable.value));
                serializablePostLinkableSpan.setPostLinkableType(
                        SerializablePostLinkableSpan.PostLinkableType.Link.getTypeValue());
                break;
            case SPOILER:
                postLinkableValueJson = gson.toJson(new PostLinkableSpoilerValue(
                        SerializablePostLinkableSpan.PostLinkableType.Spoiler));
                serializablePostLinkableSpan.setPostLinkableType(
                        SerializablePostLinkableSpan.PostLinkableType.Spoiler.getTypeValue());
                break;
            case THREAD:
                if (!(postLinkable.value instanceof CommentParser.ThreadLink)) {
                    throw new RuntimeException("PostLinkable value is not of ThreadLink type, key = "
                            + postLinkable.key + ", type = " + postLinkable.type.name());
                }

                CommentParser.ThreadLink threadLink = (CommentParser.ThreadLink) postLinkable.value;
                postLinkableValueJson = gson.toJson(new PostLinkThreadLinkValue(
                        SerializablePostLinkableSpan.PostLinkableType.Thread,
                        threadLink.board,
                        threadLink.threadId,
                        threadLink.postId));
                serializablePostLinkableSpan.setPostLinkableType(
                        SerializablePostLinkableSpan.PostLinkableType.Thread.getTypeValue());
                break;
            case BOARD:
                postLinkableValueJson = gson.toJson(new PostLinkableBoardLinkValue(
                        SerializablePostLinkableSpan.PostLinkableType.Board,
                        (String) postLinkable.value));
                serializablePostLinkableSpan.setPostLinkableType(
                        SerializablePostLinkableSpan.PostLinkableType.Board.getTypeValue());
                break;
            case SEARCH:
                if (!(postLinkable.value instanceof CommentParser.SearchLink)) {
                    throw new RuntimeException("PostLinkable value is not of SearchLink type, key = "
                            + postLinkable.key + ", type = " + postLinkable.type.name());
                }

                CommentParser.SearchLink searchLink = (CommentParser.SearchLink) postLinkable.value;
                postLinkableValueJson = gson.toJson(new PostLinkableSearchLinkValue(
                        SerializablePostLinkableSpan.PostLinkableType.Search,
                        searchLink.board,
                        searchLink.search));
                serializablePostLinkableSpan.setPostLinkableType(
                        SerializablePostLinkableSpan.PostLinkableType.Search.getTypeValue());
                break;
            default:
                throw new IllegalArgumentException("Not implemented for type " + postLinkable.type.name());
        }

        serializablePostLinkableSpan.setPostLinkableValueJson(postLinkableValueJson);
        spanInfo.setSpanData(gson.toJson(serializablePostLinkableSpan));

        serializableSpannableString.addSpanInfo(spanInfo);
    }

    @NonNull
    public static CharSequence deserializeSpannableString(
            @Nullable SerializableSpannableString serializableSpannableString) {
        if (serializableSpannableString == null || serializableSpannableString.getText().isEmpty()) {
            return "";
        }

        SpannableString spannableString = new SpannableString(serializableSpannableString.getText());

        for (SerializableSpannableString.SpanInfo spanInfo : serializableSpannableString.getSpanInfoList()) {
            switch (SerializableSpannableString.SpanType.from(spanInfo.getSpanType())) {
                case ForegroundColorSpanHashedType:
                    SerializableForegroundColorSpan serializableForegroundColorSpan = gson.fromJson(
                            spanInfo.getSpanData(),
                            SerializableForegroundColorSpan.class);

                    spannableString.setSpan(
                            new ForegroundColorSpanHashed(serializableForegroundColorSpan.getForegroundColor()),
                            spanInfo.getSpanStart(),
                            spanInfo.getSpanEnd(),
                            spanInfo.getFlags());
                    break;
                case BackgroundColorSpanHashedType:
                    SerializableBackgroundColorSpan serializableBackgroundColorSpan = gson.fromJson(
                            spanInfo.getSpanData(),
                            SerializableBackgroundColorSpan.class);

                    spannableString.setSpan(
                            new BackgroundColorSpanHashed(serializableBackgroundColorSpan.getBackgroundColor()),
                            spanInfo.getSpanStart(),
                            spanInfo.getSpanEnd(),
                            spanInfo.getFlags());
                    break;
                case StrikethroughSpanType:
                    spannableString.setSpan(
                            new StrikethroughSpan(),
                            spanInfo.getSpanStart(),
                            spanInfo.getSpanEnd(),
                            spanInfo.getFlags());
                    break;
                case StyleSpanType:
                    SerializableStyleSpan serializableStyleSpan = gson.fromJson(
                            spanInfo.getSpanData(),
                            SerializableStyleSpan.class);

                    spannableString.setSpan(
                            new StyleSpan(serializableStyleSpan.getStyle()),
                            spanInfo.getSpanStart(),
                            spanInfo.getSpanEnd(),
                            spanInfo.getFlags());
                    break;
                case TypefaceSpanType:
                    SerializableTypefaceSpan serializableTypefaceSpan = gson.fromJson(
                            spanInfo.getSpanData(),
                            SerializableTypefaceSpan.class);

                    spannableString.setSpan(
                            new TypefaceSpan(serializableTypefaceSpan.getFamily()),
                            spanInfo.getSpanStart(),
                            spanInfo.getSpanEnd(),
                            spanInfo.getFlags());
                    break;
                case AbsoluteSizeSpanHashed:
                    SerializableAbsoluteSizeSpan serializableAbsoluteSizeSpan = gson.fromJson(
                            spanInfo.getSpanData(),
                            SerializableAbsoluteSizeSpan.class);

                    spannableString.setSpan(
                            new AbsoluteSizeSpanHashed(serializableAbsoluteSizeSpan.getSize()),
                            spanInfo.getSpanStart(),
                            spanInfo.getSpanEnd(),
                            spanInfo.getFlags());
                    break;
                case PostLinkable:
                    deserializeAndApplyPostLinkableSpan(spannableString, spanInfo);
                    break;
            }
        }

        return spannableString;
    }

    private static void deserializeAndApplyPostLinkableSpan(
            SpannableString spannableString,
            SerializableSpannableString.SpanInfo spanInfo) {

        SerializablePostLinkableSpan serializablePostLinkableSpan = gson.fromJson(
                spanInfo.getSpanData(),
                SerializablePostLinkableSpan.class);

        Theme currentTheme = ThemeHelper.getTheme();
        PostLinkable postLinkable;

        switch (serializablePostLinkableSpan.getPostLinkableType()) {
            case Quote:
                PostLinkableQuoteValue postLinkableQuoteValue = gson.fromJson(
                        serializablePostLinkableSpan.getPostLinkableValueJson(),
                        PostLinkableQuoteValue.class);
                postLinkable = new PostLinkable(
                        currentTheme,
                        serializablePostLinkableSpan.getKey(),
                        postLinkableQuoteValue.getPostId(),
                        PostLinkable.Type.QUOTE);
                break;
            case Link:
                PostLinkableLinkValue postLinkableLinkValue = gson.fromJson(
                        serializablePostLinkableSpan.getPostLinkableValueJson(),
                        PostLinkableLinkValue.class);
                postLinkable = new PostLinkable(
                        currentTheme,
                        serializablePostLinkableSpan.getKey(),
                        postLinkableLinkValue.getLink(),
                        PostLinkable.Type.LINK);
                break;
            case Spoiler:
                postLinkable = new PostLinkable(
                        currentTheme,
                        serializablePostLinkableSpan.getKey(),
                        currentTheme.spoilerColor,
                        PostLinkable.Type.SPOILER);
                break;
            case Thread:
                PostLinkThreadLinkValue postLinkThreadLinkValue = gson.fromJson(
                        serializablePostLinkableSpan.getPostLinkableValueJson(),
                        PostLinkThreadLinkValue.class);
                postLinkable = new PostLinkable(
                        currentTheme,
                        serializablePostLinkableSpan.getKey(),
                        new CommentParser.ThreadLink(
                                postLinkThreadLinkValue.getBoard(),
                                postLinkThreadLinkValue.getThreadId(),
                                postLinkThreadLinkValue.getPostId()
                        ),
                        PostLinkable.Type.THREAD);
                break;
            case Board:
                PostLinkableBoardLinkValue postLinkableBoardLinkValue = gson.fromJson(
                        serializablePostLinkableSpan.getPostLinkableValueJson(),
                        PostLinkableBoardLinkValue.class);
                postLinkable = new PostLinkable(
                        currentTheme,
                        serializablePostLinkableSpan.getKey(),
                        postLinkableBoardLinkValue.getBoardLink(),
                        PostLinkable.Type.BOARD);
                break;
            case Search:
                PostLinkableSearchLinkValue postLinkableSearchLinkValue = gson.fromJson(
                        serializablePostLinkableSpan.getPostLinkableValueJson(),
                        PostLinkableSearchLinkValue.class);
                postLinkable = new PostLinkable(
                        currentTheme,
                        serializablePostLinkableSpan.getKey(),
                        new CommentParser.SearchLink(
                                postLinkableSearchLinkValue.getBoard(),
                                postLinkableSearchLinkValue.getSearch()
                        ),
                        PostLinkable.Type.SEARCH);
                break;
            default:
                throw new IllegalArgumentException(
                        "Not implemented for type " +
                                serializablePostLinkableSpan.getPostLinkableType().name());
        }

        spannableString.setSpan(
                postLinkable,
                spanInfo.getSpanStart(),
                spanInfo.getSpanEnd(),
                spanInfo.getFlags());
    }

}
