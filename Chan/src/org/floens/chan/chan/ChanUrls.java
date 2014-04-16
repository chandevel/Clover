package org.floens.chan.chan;

import java.util.Locale;

public class ChanUrls {
    public static String getCatalogUrl(String board) {
        return "https://a.4cdn.org/" + board + "/catalog.json";
    }

    public static String getPageUrl(String board, int pageNumber) {
        return "https://a.4cdn.org/" + board + "/" + pageNumber + ".json";
    }

    public static String getThreadUrl(String board, int no) {
        return "https://a.4cdn.org/" + board + "/res/" + no + ".json";
    }

    public static String getCaptchaChallengeUrl() {
        return "https://www.google.com/recaptcha/api/challenge?k=6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc";
    }

    public static String getCaptchaImageUrl(String challenge) {
        return "https://www.google.com/recaptcha/api/image?c=" + challenge;
    }

    public static String getImageUrl(String board, String code, String extension) {
        return "https://i.4cdn.org/" + board + "/src/" + code + "." + extension;
    }

    public static String getThumbnailUrl(String board, String code) {
        return "https://t.4cdn.org/" + board + "/thumb/" + code + "s.jpg";
    }

    public static String getCountryFlagUrl(String countryCode) {
        return "https://s.4cdn.org/image/country/" + countryCode.toLowerCase(Locale.ENGLISH) + ".gif";
    }

    public static String getBoardsUrl() {
        return "https://a.4cdn.org/boards.json";
    }

    public static String getPostUrl(String board) {
        return "https://sys.4chan.org/" + board + "/post";
        //        return "http://192.168.6.214/Testing/PostEchoer/post.php";
    }

    public static String getDeleteUrl(String board) {
        return "https://sys.4chan.org/" + board + "/imgboard.php";
        //        return "http://192.168.6.214/Testing/PostEchoer/post.php";
    }

    public static String getBoardUrlDesktop(String board) {
        return "https://boards.4chan.org/" + board + "/";
    }

    public static String getThreadUrlDesktop(String board, int no) {
        return "https://boards.4chan.org/" + board + "/res/" + no;
    }

    public static String getCatalogUrlDesktop(String board) {
        return "https://boards.4chan.org/" + board + "/catalog";
    }
}
