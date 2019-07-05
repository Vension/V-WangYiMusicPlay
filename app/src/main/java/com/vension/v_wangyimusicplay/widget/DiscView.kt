package com.vension.v_wangyimusicplay.widget

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.vension.v_wangyimusicplay.R
import com.vension.v_wangyimusicplay.model.MusicData
import com.vension.v_wangyimusicplay.utils.DisplayUtil
import java.util.*

/**
 * Created by AchillesL on 2016/11/15.
 */

class DiscView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    RelativeLayout(context, attrs, defStyleAttr) {

    private var mIvNeedle: ImageView? = null
    private var mVpContain: ViewPager? = null
    private var mViewPagerAdapter: ViewPagerAdapter? = null
    private var mNeedleAnimator: ObjectAnimator? = null

    private val mDiscLayouts = ArrayList<View>()

    private val mMusicDatas = ArrayList<MusicData>()
    private val mDiscAnimators = ArrayList<ObjectAnimator>()
    /*标记ViewPager是否处于偏移的状态*/
    private var mViewPagerIsOffset = false

    /*标记唱针复位后，是否需要重新偏移到唱片处*/
    private var mIsNeed2StartPlayAnimator = false
    private var musicStatus = MusicStatus.STOP
    private var needleAnimatorStatus = NeedleAnimatorStatus.IN_FAR_END

    private var mIPlayInfo: IPlayInfo? = null

    private val mScreenWidth: Int
    private val mScreenHeight: Int

    /*得到唱盘背后半透明的圆形背景*/
    private val discBlackgroundDrawable: Drawable
        get() {
            val discSize = (mScreenWidth * DisplayUtil.SCALE_DISC_SIZE).toInt()
            val bitmapDisc = Bitmap.createScaledBitmap(
                BitmapFactory.decodeResource(
                    resources, R
                        .drawable.ic_disc_blackground
                ), discSize, discSize, false
            )
            return RoundedBitmapDrawableFactory.create(resources, bitmapDisc)
        }

    val isPlaying: Boolean
        get() = musicStatus == MusicStatus.PLAY

    /*唱针当前所处的状态*/
    private enum class NeedleAnimatorStatus {
        /*移动时：从唱盘往远处移动*/
        TO_FAR_END,
        /*移动时：从远处往唱盘移动*/
        TO_NEAR_END,
        /*静止时：离开唱盘*/
        IN_FAR_END,
        /*静止时：贴近唱盘*/
        IN_NEAR_END
    }

    /*音乐当前的状态：只有播放、暂停、停止三种*/
    enum class MusicStatus {
        PLAY, PAUSE, STOP
    }

    /*DiscView需要触发的音乐切换状态：播放、暂停、上/下一首、停止*/
    enum class MusicChangedStatus {
        PLAY, PAUSE, NEXT, LAST, STOP
    }

    interface IPlayInfo {
        /*用于更新标题栏变化*/
        fun onMusicInfoChanged(musicName: String, musicAuthor: String)

        /*用于更新背景图片*/
        fun onMusicPicChanged(musicPicRes: Int)

        /*用于更新音乐播放状态*/
        fun onMusicChanged(musicChangedStatus: MusicChangedStatus)
    }

    init {
        mScreenWidth = DisplayUtil.getScreenWidth(context)
        mScreenHeight = DisplayUtil.getScreenHeight(context)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        initDiscBlackground()
        initViewPager()
        initNeedle()
        initObjectAnimator()
    }

    private fun initDiscBlackground() {
        val mDiscBlackground = findViewById<View>(R.id.ivDiscBlackgound) as ImageView
        mDiscBlackground.setImageDrawable(discBlackgroundDrawable)

        val marginTop = (DisplayUtil.SCALE_DISC_MARGIN_TOP * mScreenHeight).toInt()
        val layoutParams = mDiscBlackground
            .layoutParams as RelativeLayout.LayoutParams
        layoutParams.setMargins(0, marginTop, 0, 0)

        mDiscBlackground.layoutParams = layoutParams
    }

    private fun initViewPager() {
        mViewPagerAdapter = ViewPagerAdapter()
        mVpContain = findViewById<View>(R.id.vpDiscContain) as ViewPager
        mVpContain!!.overScrollMode = View.OVER_SCROLL_NEVER
        mVpContain!!.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            internal var lastPositionOffsetPixels = 0
            internal var currentItem = 0
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                //左滑
                if (lastPositionOffsetPixels > positionOffsetPixels) {
                    if (positionOffset < 0.5) {
                        notifyMusicInfoChanged(position)
                    } else {
                        notifyMusicInfoChanged(mVpContain!!.currentItem)
                    }
                } else if (lastPositionOffsetPixels < positionOffsetPixels) {
                    if (positionOffset > 0.5) {
                        notifyMusicInfoChanged(position + 1)
                    } else {
                        notifyMusicInfoChanged(position)
                    }
                }//右滑
                lastPositionOffsetPixels = positionOffsetPixels
            }

            override fun onPageSelected(position: Int) {
                resetOtherDiscAnimation(position)
                notifyMusicPicChanged(position)
                if (position > currentItem) {
                    notifyMusicStatusChanged(MusicChangedStatus.NEXT)
                } else {
                    notifyMusicStatusChanged(MusicChangedStatus.LAST)
                }
                currentItem = position
            }

            override fun onPageScrollStateChanged(state: Int) {
                doWithAnimatorOnPageScroll(state)
            }
        })
        mVpContain!!.adapter = mViewPagerAdapter

        val layoutParams = mVpContain!!.layoutParams as RelativeLayout.LayoutParams
        val marginTop = (DisplayUtil.SCALE_DISC_MARGIN_TOP * mScreenHeight).toInt()
        layoutParams.setMargins(0, marginTop, 0, 0)
        mVpContain!!.layoutParams = layoutParams
    }

    /**
     * 取消其他页面上的动画，并将图片旋转角度复原
     */
    private fun resetOtherDiscAnimation(position: Int) {
        for (i in mDiscLayouts.indices) {
            if (position == i) continue
            mDiscAnimators[position].cancel()
            val imageView = mDiscLayouts[i].findViewById<View>(R.id.ivDisc) as ImageView
            imageView.rotation = 0f
        }
    }

    private fun doWithAnimatorOnPageScroll(state: Int) {
        when (state) {
            ViewPager.SCROLL_STATE_IDLE, ViewPager.SCROLL_STATE_SETTLING -> {
                mViewPagerIsOffset = false
                if (musicStatus == MusicStatus.PLAY) {
                    playAnimator()
                }
            }
            ViewPager.SCROLL_STATE_DRAGGING -> {
                mViewPagerIsOffset = true
                pauseAnimator()
            }
        }
    }

    private fun initNeedle() {
        mIvNeedle = findViewById<View>(R.id.ivNeedle) as ImageView

        val needleWidth = (DisplayUtil.SCALE_NEEDLE_WIDTH * mScreenWidth).toInt()
        val needleHeight = (DisplayUtil.SCALE_NEEDLE_HEIGHT * mScreenHeight).toInt()

        /*设置手柄的外边距为负数，让其隐藏一部分*/
        val marginTop = (DisplayUtil.SCALE_NEEDLE_MARGIN_TOP * mScreenHeight).toInt() * -1
        val marginLeft = (DisplayUtil.SCALE_NEEDLE_MARGIN_LEFT * mScreenWidth).toInt()

        val originBitmap = BitmapFactory.decodeResource(
            resources, R.drawable
                .ic_needle
        )
        val bitmap = Bitmap.createScaledBitmap(originBitmap, needleWidth, needleHeight, false)

        val layoutParams = mIvNeedle!!.layoutParams as RelativeLayout.LayoutParams
        layoutParams.setMargins(marginLeft, marginTop, 0, 0)

        val pivotX = (DisplayUtil.SCALE_NEEDLE_PIVOT_X * mScreenWidth).toInt()
        val pivotY = (DisplayUtil.SCALE_NEEDLE_PIVOT_Y * mScreenWidth).toInt()

        mIvNeedle!!.pivotX = pivotX.toFloat()
        mIvNeedle!!.pivotY = pivotY.toFloat()
        mIvNeedle!!.rotation = DisplayUtil.ROTATION_INIT_NEEDLE
        mIvNeedle!!.setImageBitmap(bitmap)
        mIvNeedle!!.layoutParams = layoutParams
    }

    private fun initObjectAnimator() {
        mNeedleAnimator = ObjectAnimator.ofFloat<View>(mIvNeedle, View.ROTATION, DisplayUtil.ROTATION_INIT_NEEDLE, 0f)
        mNeedleAnimator!!.duration = DURATION_NEEDLE_ANIAMTOR.toLong()
        mNeedleAnimator!!.interpolator = AccelerateInterpolator()
        mNeedleAnimator!!.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator) {
                /**
                 * 根据动画开始前NeedleAnimatorStatus的状态，
                 * 即可得出动画进行时NeedleAnimatorStatus的状态
                 */
                if (needleAnimatorStatus == NeedleAnimatorStatus.IN_FAR_END) {
                    needleAnimatorStatus = NeedleAnimatorStatus.TO_NEAR_END
                } else if (needleAnimatorStatus == NeedleAnimatorStatus.IN_NEAR_END) {
                    needleAnimatorStatus = NeedleAnimatorStatus.TO_FAR_END
                }
            }

            override fun onAnimationEnd(animator: Animator) {

                if (needleAnimatorStatus == NeedleAnimatorStatus.TO_NEAR_END) {
                    needleAnimatorStatus = NeedleAnimatorStatus.IN_NEAR_END
                    val index = mVpContain!!.currentItem
                    playDiscAnimator(index)
                    musicStatus = MusicStatus.PLAY
                } else if (needleAnimatorStatus == NeedleAnimatorStatus.TO_FAR_END) {
                    needleAnimatorStatus = NeedleAnimatorStatus.IN_FAR_END
                    if (musicStatus == MusicStatus.STOP) {
                        mIsNeed2StartPlayAnimator = true
                    }
                }

                if (mIsNeed2StartPlayAnimator) {
                    mIsNeed2StartPlayAnimator = false
                    /**
                     * 只有在ViewPager不处于偏移状态时，才开始唱盘旋转动画
                     */
                    if (!mViewPagerIsOffset) {
                        /*延时500ms*/
                        this@DiscView.postDelayed({ playAnimator() }, 50)
                    }
                }
            }

            override fun onAnimationCancel(animator: Animator) {

            }

            override fun onAnimationRepeat(animator: Animator) {

            }
        })
    }

    fun setPlayInfoListener(listener: IPlayInfo) {
        this.mIPlayInfo = listener
    }

    /**
     * 得到唱盘图片
     * 唱盘图片由空心圆盘及音乐专辑图片“合成”得到
     */
    private fun getDiscDrawable(musicPicRes: Int): Drawable {
        val discSize = (mScreenWidth * DisplayUtil.SCALE_DISC_SIZE).toInt()
        val musicPicSize = (mScreenWidth * DisplayUtil.SCALE_MUSIC_PIC_SIZE).toInt()

        val bitmapDisc = Bitmap.createScaledBitmap(
            BitmapFactory.decodeResource(
                resources, R
                    .drawable.ic_disc
            ), discSize, discSize, false
        )
        val bitmapMusicPic = getMusicPicBitmap(musicPicSize, musicPicRes)
        val discDrawable = BitmapDrawable(bitmapDisc)
        val roundMusicDrawable = RoundedBitmapDrawableFactory.create(resources, bitmapMusicPic)

        //抗锯齿
        discDrawable.setAntiAlias(true)
        roundMusicDrawable.setAntiAlias(true)

        val drawables = arrayOfNulls<Drawable>(2)
        drawables[0] = roundMusicDrawable
        drawables[1] = discDrawable

        val layerDrawable = LayerDrawable(drawables)
        val musicPicMargin =
            ((DisplayUtil.SCALE_DISC_SIZE - DisplayUtil.SCALE_MUSIC_PIC_SIZE) * mScreenWidth / 2).toInt()
        //调整专辑图片的四周边距，让其显示在正中
        layerDrawable.setLayerInset(
            0, musicPicMargin, musicPicMargin, musicPicMargin,
            musicPicMargin
        )

        return layerDrawable
    }

    private fun getMusicPicBitmap(musicPicSize: Int, musicPicRes: Int): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        BitmapFactory.decodeResource(resources, musicPicRes, options)
        val imageWidth = options.outWidth

        val sample = imageWidth / musicPicSize
        var dstSample = 1
        if (sample > dstSample) {
            dstSample = sample
        }
        options.inJustDecodeBounds = false
        //设置图片采样率
        options.inSampleSize = dstSample
        //设置图片解码格式
        options.inPreferredConfig = Bitmap.Config.RGB_565

        return Bitmap.createScaledBitmap(
            BitmapFactory.decodeResource(
                resources,
                musicPicRes, options
            ), musicPicSize, musicPicSize, true
        )
    }

    fun setMusicDataList(musicDataList: List<MusicData>) {
        if (musicDataList.isEmpty()) return

        mDiscLayouts.clear()
        mMusicDatas.clear()
        mDiscAnimators.clear()
        mMusicDatas.addAll(musicDataList)

        var i = 0
        for (musicData in mMusicDatas) {
            val discLayout = LayoutInflater.from(context).inflate(
                R.layout.layout_disc,
                mVpContain, false
            )

            val disc = discLayout.findViewById<View>(R.id.ivDisc) as ImageView
            disc.setImageDrawable(getDiscDrawable(musicData.musicPicRes))

            mDiscAnimators.add(getDiscObjectAnimator(disc, i++))
            mDiscLayouts.add(discLayout)
        }
        mViewPagerAdapter!!.notifyDataSetChanged()

        val musicData = mMusicDatas[0]
        if (mIPlayInfo != null) {
            mIPlayInfo!!.onMusicInfoChanged(musicData.musicName, musicData.musicAuthor)
            mIPlayInfo!!.onMusicPicChanged(musicData.musicPicRes)
        }
    }

    private fun getDiscObjectAnimator(disc: ImageView, i: Int): ObjectAnimator {
        val objectAnimator = ObjectAnimator.ofFloat<View>(disc, View.ROTATION, 0f, 360f)
        objectAnimator.repeatCount = ValueAnimator.INFINITE
        objectAnimator.duration = (20 * 1000).toLong()
        objectAnimator.interpolator = LinearInterpolator()
        return objectAnimator
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    /*播放动画*/
    private fun playAnimator() {
        /*唱针处于远端时，直接播放动画*/
        if (needleAnimatorStatus == NeedleAnimatorStatus.IN_FAR_END) {
            mNeedleAnimator!!.start()
        } else if (needleAnimatorStatus == NeedleAnimatorStatus.TO_FAR_END) {
            mIsNeed2StartPlayAnimator = true
        }/*唱针处于往远端移动时，设置标记，等动画结束后再播放动画*/
    }

    /*暂停动画*/
    private fun pauseAnimator() {
        /*播放时暂停动画*/
        if (needleAnimatorStatus == NeedleAnimatorStatus.IN_NEAR_END) {
            val index = mVpContain!!.currentItem
            pauseDiscAnimatior(index)
        } else if (needleAnimatorStatus == NeedleAnimatorStatus.TO_NEAR_END) {
            mNeedleAnimator!!.reverse()
            /**
             * 若动画在没结束时执行reverse方法，则不会执行监听器的onStart方法，此时需要手动设置
             */
            needleAnimatorStatus = NeedleAnimatorStatus.TO_FAR_END
        }/*唱针往唱盘移动时暂停动画*/
        /**
         * 动画可能执行多次，只有音乐处于停止 / 暂停状态时，才执行暂停命令
         */
        if (musicStatus == MusicStatus.STOP) {
            notifyMusicStatusChanged(MusicChangedStatus.STOP)
        } else if (musicStatus == MusicStatus.PAUSE) {
            notifyMusicStatusChanged(MusicChangedStatus.PAUSE)
        }
    }

    /*播放唱盘动画*/
    private fun playDiscAnimator(index: Int) {
        val objectAnimator = mDiscAnimators[index]
        if (objectAnimator.isPaused) {
            objectAnimator.resume()
        } else {
            objectAnimator.start()
        }
        /**
         * 唱盘动画可能执行多次，只有不是音乐不在播放状态，在回调执行播放
         */
        if (musicStatus != MusicStatus.PLAY) {
            notifyMusicStatusChanged(MusicChangedStatus.PLAY)
        }
    }

    /*暂停唱盘动画*/
    private fun pauseDiscAnimatior(index: Int) {
        val objectAnimator = mDiscAnimators[index]
        objectAnimator.pause()
        mNeedleAnimator!!.reverse()
    }

    fun notifyMusicInfoChanged(position: Int) {
        if (mIPlayInfo != null) {
            val musicData = mMusicDatas[position]
            mIPlayInfo!!.onMusicInfoChanged(musicData.musicName, musicData.musicAuthor)
        }
    }

    fun notifyMusicPicChanged(position: Int) {
        if (mIPlayInfo != null) {
            val musicData = mMusicDatas[position]
            mIPlayInfo!!.onMusicPicChanged(musicData.musicPicRes)
        }
    }

    fun notifyMusicStatusChanged(musicChangedStatus: MusicChangedStatus) {
        if (mIPlayInfo != null) {
            mIPlayInfo!!.onMusicChanged(musicChangedStatus)
        }
    }

    private fun play() {
        playAnimator()
    }

    private fun pause() {
        musicStatus = MusicStatus.PAUSE
        pauseAnimator()
    }

    fun stop() {
        musicStatus = MusicStatus.STOP
        pauseAnimator()
    }

    fun playOrPause() {
        if (musicStatus == MusicStatus.PLAY) {
            pause()
        } else {
            play()
        }
    }

    operator fun next() {
        val currentItem = mVpContain!!.currentItem
        if (currentItem == mMusicDatas.size - 1) {
            Toast.makeText(context, "已经到达最后一首", Toast.LENGTH_SHORT).show()
        } else {
            selectMusicWithButton()
            mVpContain!!.setCurrentItem(currentItem + 1, true)
        }
    }

    fun last() {
        val currentItem = mVpContain!!.currentItem
        if (currentItem == 0) {
            Toast.makeText(context, "已经到达第一首", Toast.LENGTH_SHORT).show()
        } else {
            selectMusicWithButton()
            mVpContain!!.setCurrentItem(currentItem - 1, true)
        }
    }

    private fun selectMusicWithButton() {
        if (musicStatus == MusicStatus.PLAY) {
            mIsNeed2StartPlayAnimator = true
            pauseAnimator()
        } else if (musicStatus == MusicStatus.PAUSE) {
            play()
        }
    }

    internal inner class ViewPagerAdapter : PagerAdapter() {

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val discLayout = mDiscLayouts[position]
            container.addView(discLayout)
            return discLayout
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(mDiscLayouts[position])
        }

        override fun getCount(): Int {
            return mDiscLayouts.size
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view === `object`
        }
    }

    companion object {

        val DURATION_NEEDLE_ANIAMTOR = 500
    }
}
