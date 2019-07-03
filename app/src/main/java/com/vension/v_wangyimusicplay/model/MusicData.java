package com.vension.v_wangyimusicplay.model;

import java.io.Serializable;

/**
 * ========================================================
 * 作 者：Vension
 * 日 期：2019/7/3 12:59
 * 更 新：2019/7/3 12:59
 * 描 述：
 * ========================================================
 */
public class MusicData implements Serializable{
    /*音乐资源id*/
    private int mMusicRes;
    /*专辑图片id*/
    private int mMusicPicRes;
    /*音乐名称*/
    private String mMusicName;
    /*作者*/
    private String mMusicAuthor;

    public MusicData(int mMusicRes, int mMusicPicRes, String mMusicName, String mMusicAuthor) {
        this.mMusicRes = mMusicRes;
        this.mMusicPicRes = mMusicPicRes;
        this.mMusicName = mMusicName;
        this.mMusicAuthor = mMusicAuthor;
    }

    public int getMusicRes() {
        return mMusicRes;
    }

    public int getMusicPicRes() {
        return mMusicPicRes;
    }

    public String getMusicName() {
        return mMusicName;
    }

    public String getMusicAuthor() {
        return mMusicAuthor;
    }
}
