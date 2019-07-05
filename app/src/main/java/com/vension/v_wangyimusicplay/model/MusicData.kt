package com.vension.v_wangyimusicplay.model

import java.io.Serializable

/**
 * ========================================================
 * 作 者：Vension
 * 日 期：2019/7/3 12:59
 * 更 新：2019/7/3 12:59
 * 描 述：
 * ========================================================
 */
class MusicData(
    val musicRes: Int, /*音乐资源id*/
    val musicPicRes: Int, /*专辑图片id*/
    val musicName: String, /*音乐名称*/
    val musicAuthor: String /*作者*/
) : Serializable
