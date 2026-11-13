package net.m1227.music;

/**
 * Validation of the Spotify and YouTube links you paste: only real playlist / album / track /
 * video links pass, so nothing else is ever opened. Pure code (tested in PureLogicTest).
 */
public final class Links {
    private Links() {}

    /** Accepts open.spotify.com links and spotify: URIs for playlists/albums/tracks. */
    public static String spotifyUri(String link) {
        String l = link.trim();
        if (l.startsWith("spotify:")) return l.matches("spotify:(playlist|album|track|artist):[A-Za-z0-9]+") ? l : null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^https://open\\.spotify\\.com/(?:intl-[a-z]+/)?(playlist|album|track|artist)/([A-Za-z0-9]+)").matcher(l);
        return m.find() ? "spotify:" + m.group(1) + ":" + m.group(2) : null;
    }

    public static String spotifyWeb(String uri) {
        String[] p = uri.split(":");
        return "https://open.spotify.com/" + p[1] + "/" + p[2];
    }

    /** Accepts youtube.com / music.youtube.com playlist or video links. */
    public static String youtubeUrl(String link) {
        String l = link.trim();
        if (l.matches("^https://(www\\.|music\\.)?youtube\\.com/(playlist|watch)\\?[A-Za-z0-9_=&%.-]+$")) return l;
        if (l.matches("^https://youtu\\.be/[A-Za-z0-9_-]+(\\?[A-Za-z0-9_=&%.-]+)?$")) return l;
        return null;
    }

}
