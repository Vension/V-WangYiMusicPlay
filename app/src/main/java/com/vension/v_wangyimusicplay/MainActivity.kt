package com.vension.v_wangyimusicplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.vension.v_wangyimusicplay.model.MusicData
import com.vension.v_wangyimusicplay.service.MusicService
import com.vension.v_wangyimusicplay.utils.DisplayUtil
import com.vension.v_wangyimusicplay.utils.FastBlurUtil
import com.vension.v_wangyimusicplay.widget.BackgourndAnimationRelativeLayout
import com.vension.v_wangyimusicplay.widget.DiscView
import com.vension.v_wangyimusicplay.widget.DiscView.DURATION_NEEDLE_ANIAMTOR
import java.io.Serializable
import java.util.*
/**
 * ========================================================
 * 作 者：Vension
 * 日 期：2019/7/3 14:20
 * 更 新：2019/7/3 14:20
 * 描 述：
 * ========================================================
 */

@RequiresApi(Build.VERSION_CODES.M)
class MainActivity : AppCompatActivity(), DiscView.IPlayInfo, View.OnClickListener {

    private var mDisc: DiscView? = null
    private var mToolbar: Toolbar? = null
    private var mSeekBar: SeekBar? = null
    private var mIvPlayOrPause: ImageView? = null
    private var mIvNext: ImageView? = null
    private var mIvLast: ImageView? = null
    private var mTvMusicDuration: TextView? = null
    private var mTvTotalMusicDuration: TextView? = null
    private var mRootLayout: BackgourndAnimationRelativeLayout? = null

    private val mMusicHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            mSeekBar!!.progress = mSeekBar!!.progress + 1000
            mTvMusicDuration!!.text = duration2Time(mSeekBar!!.progress)
            startUpdateSeekBarProgress()
        }
    }

    private val mMusicReceiver = MusicReceiver()
    private val mMusicDatas = ArrayList<MusicData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initMusicDatas()
        initView()
        initMusicReceiver()
        makeStatusBarTransparent()
    }

    private fun initMusicReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(MusicService.ACTION_STATUS_MUSIC_PLAY)
        intentFilter.addAction(MusicService.ACTION_STATUS_MUSIC_PAUSE)
        intentFilter.addAction(MusicService.ACTION_STATUS_MUSIC_DURATION)
        intentFilter.addAction(MusicService.ACTION_STATUS_MUSIC_COMPLETE)
        /*注册本地广播*/
        LocalBroadcastManager.getInstance(this).registerReceiver(mMusicReceiver, intentFilter)
    }

    private fun initView() {
        mDisc = findViewById(R.id.discview) as DiscView
        mIvNext = findViewById(R.id.ivNext) as ImageView
        mIvLast = findViewById(R.id.ivLast) as ImageView
        mIvPlayOrPause = findViewById(R.id.ivPlayOrPause) as ImageView
        mTvMusicDuration = findViewById(R.id.tvCurrentTime) as TextView
        mTvTotalMusicDuration = findViewById(R.id.tvTotalTime) as TextView
        mSeekBar = findViewById(R.id.musicSeekBar) as SeekBar
        mRootLayout = findViewById(R.id.rootLayout) as BackgourndAnimationRelativeLayout

        mToolbar = findViewById(R.id.toolBar) as Toolbar
        setSupportActionBar(mToolbar)

        mDisc!!.setPlayInfoListener(this)
        mIvLast!!.setOnClickListener(this)
        mIvNext!!.setOnClickListener(this)
        mIvPlayOrPause!!.setOnClickListener(this)

        mSeekBar!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                mTvMusicDuration!!.text = duration2Time(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                stopUpdateSeekBarProgree()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                seekTo(seekBar.progress)
                startUpdateSeekBarProgress()
            }
        })

        mTvMusicDuration!!.text = duration2Time(0)
        mTvTotalMusicDuration!!.text = duration2Time(0)
        mDisc!!.setMusicDataList(mMusicDatas)
    }

    private fun stopUpdateSeekBarProgree() {
        mMusicHandler.removeMessages(MUSIC_MESSAGE)
    }

    /*设置透明状态栏*/
    private fun makeStatusBarTransparent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = window
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
    }

    private fun initMusicDatas() {
        val musicData1 = MusicData(R.raw.music1, R.raw.ic_music1, "寻", "三亩地")
        val musicData2 = MusicData(R.raw.music2, R.raw.ic_music2, "Nightingale", "YANI")
        val musicData3 = MusicData(R.raw.music3, R.raw.ic_music3, "Cornfield Chase", "Hans Zimmer")

        mMusicDatas.add(musicData1)
        mMusicDatas.add(musicData2)
        mMusicDatas.add(musicData3)

        val intent = Intent(this, MusicService::class.java)
        intent.putExtra(PARAM_MUSIC_LIST, mMusicDatas as Serializable)
        startService(intent)
    }


    private fun try2UpdateMusicPicBackground(musicPicRes: Int) {
        if (mRootLayout!!.isNeed2UpdateBackground(musicPicRes)) {
            Thread(Runnable {
                val foregroundDrawable = getForegroundDrawable(musicPicRes)
                runOnUiThread {
                    mRootLayout!!.foreground = foregroundDrawable
                    mRootLayout!!.beginAnimation()
                }
            }).start()
        }
    }

    private fun getForegroundDrawable(musicPicRes: Int): Drawable {
        /*得到屏幕的宽高比，以便按比例切割图片一部分*/
        val widthHeightSize =
            (DisplayUtil.getScreenWidth(this@MainActivity) * 1.0 / DisplayUtil.getScreenHeight(this) * 1.0).toFloat()

        val bitmap = getForegroundBitmap(musicPicRes)
        val cropBitmapWidth = (widthHeightSize * bitmap.height).toInt()
        val cropBitmapWidthX = ((bitmap.width - cropBitmapWidth) / 2.0).toInt()

        /*切割部分图片*/
        val cropBitmap = Bitmap.createBitmap(
            bitmap, cropBitmapWidthX, 0, cropBitmapWidth,
            bitmap.height
        )
        /*缩小图片*/
        val scaleBitmap = Bitmap.createScaledBitmap(
            cropBitmap, bitmap.width / 50, bitmap
                .height / 50, false
        )
        /*模糊化*/
        val blurBitmap = FastBlurUtil.doBlur(scaleBitmap, 8, true)

        val foregroundDrawable = BitmapDrawable(blurBitmap)
        /*加入灰色遮罩层，避免图片过亮影响其他控件*/
        foregroundDrawable.setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY)
        return foregroundDrawable
    }

    private fun getForegroundBitmap(musicPicRes: Int): Bitmap {
        val screenWidth = DisplayUtil.getScreenWidth(this)
        val screenHeight = DisplayUtil.getScreenHeight(this)

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        BitmapFactory.decodeResource(resources, musicPicRes, options)
        val imageWidth = options.outWidth
        val imageHeight = options.outHeight

        if (imageWidth < screenWidth && imageHeight < screenHeight) {
            return BitmapFactory.decodeResource(resources, musicPicRes)
        }

        var sample = 2
        val sampleX = imageWidth / DisplayUtil.getScreenWidth(this)
        val sampleY = imageHeight / DisplayUtil.getScreenHeight(this)

        if (sampleX > sampleY && sampleY > 1) {
            sample = sampleX
        } else if (sampleY > sampleX && sampleX > 1) {
            sample = sampleY
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        options.inPreferredConfig = Bitmap.Config.RGB_565

        return BitmapFactory.decodeResource(resources, musicPicRes, options)
    }

    override fun onMusicInfoChanged(musicName: String, musicAuthor: String) {
        supportActionBar!!.title = musicName
        supportActionBar!!.subtitle = musicAuthor
    }

    override fun onMusicPicChanged(musicPicRes: Int) {
        try2UpdateMusicPicBackground(musicPicRes)
    }

    override fun onMusicChanged(musicChangedStatus: DiscView.MusicChangedStatus) {
        when (musicChangedStatus) {
            DiscView.MusicChangedStatus.PLAY -> {
                play()
            }
            DiscView.MusicChangedStatus.PAUSE -> {
                pause()
            }
            DiscView.MusicChangedStatus.NEXT -> {
                next()
            }
            DiscView.MusicChangedStatus.LAST -> {
                last()
            }
            DiscView.MusicChangedStatus.STOP -> {
                stop()
            }
        }
    }

    override fun onClick(v: View) {
        if (v === mIvPlayOrPause) {
            mDisc!!.playOrPause()
        } else if (v === mIvNext) {
            mDisc!!.next()
        } else if (v === mIvLast) {
            mDisc!!.last()
        }
    }

    private fun play() {
        optMusic(MusicService.ACTION_OPT_MUSIC_PLAY)
        startUpdateSeekBarProgress()
    }

    private fun pause() {
        optMusic(MusicService.ACTION_OPT_MUSIC_PAUSE)
        stopUpdateSeekBarProgree()
    }

    private fun stop() {
        stopUpdateSeekBarProgree()
        mIvPlayOrPause!!.setImageResource(R.drawable.ic_play)
        mTvMusicDuration!!.text = duration2Time(0)
        mTvTotalMusicDuration!!.text = duration2Time(0)
        mSeekBar!!.progress = 0
    }

    private operator fun next() {
        mRootLayout!!.postDelayed({ optMusic(MusicService.ACTION_OPT_MUSIC_NEXT) }, DURATION_NEEDLE_ANIAMTOR.toLong())
        stopUpdateSeekBarProgree()
        mTvMusicDuration!!.text = duration2Time(0)
        mTvTotalMusicDuration!!.text = duration2Time(0)
    }

    private fun last() {
        mRootLayout!!.postDelayed({ optMusic(MusicService.ACTION_OPT_MUSIC_LAST) }, DURATION_NEEDLE_ANIAMTOR.toLong())
        stopUpdateSeekBarProgree()
        mTvMusicDuration!!.text = duration2Time(0)
        mTvTotalMusicDuration!!.text = duration2Time(0)
    }

    private fun complete(isOver: Boolean) {
        if (isOver) {
            mDisc!!.stop()
        } else {
            mDisc!!.next()
        }
    }

    private fun optMusic(action: String) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(action))
    }

    private fun seekTo(position: Int) {
        val intent = Intent(MusicService.ACTION_OPT_MUSIC_SEEK_TO)
        intent.putExtra(MusicService.PARAM_MUSIC_SEEK_TO, position)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun startUpdateSeekBarProgress() {
        /*避免重复发送Message*/
        stopUpdateSeekBarProgree()
        mMusicHandler.sendEmptyMessageDelayed(0, 1000)
    }

    /*根据时长格式化称时间文本*/
    private fun duration2Time(duration: Int): String {
        val min = duration / 1000 / 60
        val sec = duration / 1000 % 60

        return (if (min < 10) "0$min" else min.toString() + "") + ":" + if (sec < 10) "0$sec" else sec.toString() + ""
    }

    private fun updateMusicDurationInfo(totalDuration: Int) {
        mSeekBar!!.progress = 0
        mSeekBar!!.max = totalDuration
        mTvTotalMusicDuration!!.text = duration2Time(totalDuration)
        mTvMusicDuration!!.text = duration2Time(0)
        startUpdateSeekBarProgress()
    }

    internal inner class MusicReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == MusicService.ACTION_STATUS_MUSIC_PLAY) {
                mIvPlayOrPause!!.setImageResource(R.drawable.ic_pause)
                val currentPosition = intent.getIntExtra(MusicService.PARAM_MUSIC_CURRENT_POSITION, 0)
                mSeekBar!!.progress = currentPosition
                if (!mDisc!!.isPlaying) {
                    mDisc!!.playOrPause()
                }
            } else if (action == MusicService.ACTION_STATUS_MUSIC_PAUSE) {
                mIvPlayOrPause!!.setImageResource(R.drawable.ic_play)
                if (mDisc!!.isPlaying) {
                    mDisc!!.playOrPause()
                }
            } else if (action == MusicService.ACTION_STATUS_MUSIC_DURATION) {
                val duration = intent.getIntExtra(MusicService.PARAM_MUSIC_DURATION, 0)
                updateMusicDurationInfo(duration)
            } else if (action == MusicService.ACTION_STATUS_MUSIC_COMPLETE) {
                val isOver = intent.getBooleanExtra(MusicService.PARAM_MUSIC_IS_OVER, true)
                complete(isOver)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMusicReceiver)
    }

    companion object {
        val MUSIC_MESSAGE = 0

        val PARAM_MUSIC_LIST = "PARAM_MUSIC_LIST"
    }
}
