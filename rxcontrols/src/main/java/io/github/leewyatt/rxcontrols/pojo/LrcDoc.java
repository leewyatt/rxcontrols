package io.github.leewyatt.rxcontrols.pojo;

import io.github.leewyatt.rxcontrols.utils.LrcUtil;

import java.util.ArrayList;
import java.util.Objects;

/**
 *
 *
 *
 * LRC的格式参考如下:
 * #ID Tags 标签 :主要记录歌词的
 * [ti:笑红尘]
 * [ar:陈淑桦]
 * [al:爱的进行式]
 * [by:孟德良]
 * [offset:500]
 *
 * #Time Tags 标签 主要是歌词 (现在很多的lrc歌词, 使用Time Tags来输出ID Tags)
 * [00:00.00]《笑红尘》
 * [00:02.00]演唱：陈淑桦
 * [00:04.00]
 * [00:06.00]作词：厉曼婷
 * [00:07.80]作曲：李宗盛
 * [00:09.60]
 * [00:11.15]红尘多可笑 痴情最无聊
 */
public class LrcDoc {
    /**
     * 歌手/艺术家
     */
    private String artist;
    /**
     * 歌曲标题
     */
    private String title;
    /**
     * 专辑
     */
    private String album;
    /**
     * 编辑Lrc歌词的人
     */
    private String by;
    /**
     * 时间补偿值 其单位是毫秒，正值表示整体提前，负值相反。
     */
    private long offset;
    /**
     * 歌词
     */
    private ArrayList<LrcLine> lrcLines;

    public LrcDoc() {
    }

    public LrcDoc(String artist, String title, String album, String by, long offset, ArrayList<LrcLine> lrcLines) {
        this.artist = artist;
        this.title = title;
        this.album = album;
        this.by = by;
        this.offset = offset;
        this.lrcLines = lrcLines;
    }

    public static LrcDoc parseLrcDoc(String lrc){
        return LrcUtil.parseLrcDoc(lrc);
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getBy() {
        return by;
    }

    public void setBy(String by) {
        this.by = by;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public ArrayList<LrcLine> getLrcLines() {
        return lrcLines;
    }

    public void setLrcLines(ArrayList<LrcLine> lrcLines) {
        this.lrcLines = lrcLines;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LrcDoc doc = (LrcDoc) o;
        return offset == doc.offset &&
                Objects.equals(artist, doc.artist) &&
                Objects.equals(title, doc.title) &&
                Objects.equals(album, doc.album) &&
                Objects.equals(by, doc.by) &&
                Objects.equals(lrcLines, doc.lrcLines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artist, title, album, by, offset, lrcLines);
    }

    @Override
    public String toString() {
        return "LrcDoc{" +
                "artist='" + artist + '\'' +
                ", title='" + title + '\'' +
                ", album='" + album + '\'' +
                ", by='" + by + '\'' +
                ", offset=" + offset +
                ", lrcLines=" + lrcLines +
                '}';
    }

}
