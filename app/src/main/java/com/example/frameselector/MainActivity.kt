package com.example.frameselector


import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.*
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.size
import androidx.lifecycle.ViewModelProvider

import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.*
import com.bumptech.glide.load.resource.file.FileDecoder
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.example.frameselector.ui.theme.FrameSelectorTheme
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import java.io.*
import java.nio.channels.AsynchronousFileChannel
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.math.floor
import kotlin.system.measureTimeMillis


class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.P)
    @OptIn(ExperimentalPagerApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            if(it[android.Manifest.permission.READ_EXTERNAL_STORAGE] == true &&
                it[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] == true){

                Log.d("mert","izin verildi")

                val video = getVideo("FrameSelector","Transformers.mp4")

                if(video != null)
                {
                    val mediaMetadataRetriever = MediaMetadataRetriever()
                    mediaMetadataRetriever.setDataSource(video.absolutePath)
                    setContent {
                        FrameSelectorTheme {
                            // A surface container using the 'background' color from the theme
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colors.background
                            ) {
                                val context = this@MainActivity
                                val exoPlayer = remember{
                                    SimpleExoPlayer.Builder(context).build()
                                }
                                LaunchedEffect(video.absolutePath){
                                    val dataSourceFactory = DefaultDataSourceFactory(context,
                                        Util.getUserAgent(context, context.packageName))
                                    val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                                        .createMediaSource(MediaItem.fromUri(video.absolutePath))
                                    exoPlayer.setMediaSource(source)
                                }

                                /*val realSize = remember{mutableStateOf(IntSize.Zero)}
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(video.absolutePath)
                                Log.d("mert","qwe: ${retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                                )} - ${retriever.extractMetadata(
                                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                                )}")
                                Log.d("mert","x: ${realSize.value.height} - ${realSize.value.width}")
                                Log.d("mert","density: $")*/

                                val allFramesList = remember{mutableListOf<ArrayList<Bitmap?>>()}
                                val zoomIn = remember{ mutableStateOf(0)}
                                val pagerState = rememberPagerState()
                                val currSliderValue = remember{ mutableStateOf(0f)}
                                var pageCount by remember{mutableStateOf(0)}
                                val currPage = remember(key1 = currSliderValue.value){ getCurrPage(currSliderValue.value,pageCount)}
                                val changeCurrPage = rememberCoroutineScope()
                                var fps by remember{mutableStateOf(0)}

                                val isExtracting = remember{mutableStateOf(false)}
                                val progress = remember{mutableStateOf(0f) }

                                val frames = remember{ArrayList<Bitmap?>()}
                                val iterableList = remember{ArrayList<Pair<Long,Int>>()}
                                var addToProgress by remember{mutableStateOf(0f)}
                                var iterator by remember{mutableStateOf(0)}

                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Yellow),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally) {

                                    if(zoomIn.value != 0)
                                    {
                                        Log.d("mert","isExtracting at Start: ${isExtracting.value}")
                                        if(!isExtracting.value) progress.value = 0f
                                        val curFrames = allFramesList[zoomIn.value-1]
                                        pageCount = curFrames.size

                                        HorizontalPager(count = pageCount, state = pagerState) { index ->
                                            Card(modifier = Modifier
                                                .fillMaxWidth()
                                                .height(360.dp)) {
                                                Image(bitmap = curFrames[index]!!.asImageBitmap(), contentDescription = "")
                                            }
                                        }

                                        Slider(value = currSliderValue.value, onValueChange = {
                                            currSliderValue.value = it
                                            changeCurrPage.launch {
                                                pagerState.scrollToPage(currPage.value)
                                            }
                                        })
                                        Spacer(modifier = Modifier.size(5.dp))
                                        /*if(isExtracting.value)
                                        {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(15.dp),
                                                backgroundColor = Color.LightGray,
                                                color = Color.Red,
                                                progress = progress.value
                                            )

                                            val pair = iterableList[iterator]
                                            Log.d("mert", "${pair.second} started...")

                                            frames.add(mediaMetadataRetriever.getFrameAtTime(pair.first, MediaMetadataRetriever.OPTION_CLOSEST)!!)
                                            iterator++
                                            progress.value += addToProgress
                                            Log.d("mert","progress value: ${progress.value}")
                                            Log.d("mert","frames size: ${frames.size}")
                                            if(iterator >= iterableList.size)
                                            {
                                                Log.d("mert","finish")
                                                allFramesList.add(ArrayList(frames))
                                                Log.d("mert","a_0: ${allFramesList.last().size}")
                                                frames.clear()
                                                Log.d("mert","a_1: ${allFramesList.last().size}")
                                                progress.value = 0f
                                                isExtracting.value = false
                                                zoomIn.value++
                                            }
                                        }*/
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(15.dp),
                                            backgroundColor = Color.LightGray,
                                            color = Color.Red,
                                            progress = progress.value
                                        )
                                        Spacer(modifier = Modifier.size(5.dp))
                                        Text(text = "FPS: ${fps}",color = Color.Blue)
                                        Row{
                                            Button(onClick = {
                                                if(zoomIn.value == 1)
                                                {
                                                    allFramesList.clear()
                                                }
                                                fps-=6
                                                zoomIn.value -= 1
                                            }, colors = ButtonDefaults.buttonColors(Color.White)) {
                                                if(zoomIn.value == 1)
                                                {
                                                    Text(text = "Go Back to Video", color = Color.Green)
                                                }
                                                else
                                                {
                                                    Text(text = "Zoom OUT", color = Color.Green)
                                                }
                                            }

                                            Spacer(modifier = Modifier.size(5.dp))
                                            Button(onClick = {
                                                Log.d("mert","current position: ${exoPlayer.currentPosition}")
                                                fps+=6
                                                if(zoomIn.value < allFramesList.size)
                                                {
                                                    zoomIn.value++
                                                }
                                                else
                                                {
                                                    isExtracting.value = true
                                                    MainScope().launch(Main) {
                                                        val result = extractFramesMediaRetriever_2(
                                                            allFramesList,
                                                            video,
                                                            fps,
                                                            milSecToMicroSec(exoPlayer.currentPosition)-secToMicroSec(1),
                                                            milSecToMicroSec(exoPlayer.currentPosition)+secToMicroSec(1),
                                                            progress,
                                                            context
                                                        )
                                                        if(result){
                                                            Log.d("mert","result came with true")
                                                            isExtracting.value = false
                                                            zoomIn.value++
                                                        }
                                                    }
                                                    Log.d("mert","launch/async cikisi....")
                                                }
                                            }){
                                                Text(text = "Zoom IN", color = Color.Red)
                                            }
                                        }
                                    }
                                    else
                                    {
                                        if(!isExtracting.value) progress.value = 0f

                                        AndroidView(modifier = Modifier
                                            .height(360.dp)
                                            .fillMaxWidth()
                                            .background(Color.Green),
                                            factory = { this_context ->
                                                val playerView = StyledPlayerView(this_context).apply {
                                                    player = exoPlayer
                                                    player!!.prepare()
                                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                    useController = false
                                                }
                                                playerView
                                            }
                                        )
                                        Spacer(modifier = Modifier.size(5.dp))
                                        AndroidView(factory = { con ->
                                            PlayerControlView(con).apply{
                                                player = exoPlayer
                                                showTimeoutMs = 0
                                            }
                                        })
                                        Spacer(modifier = Modifier.size(5.dp))

                                        /*if(isExtracting.value)
                                        {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(15.dp),
                                                backgroundColor = Color.LightGray,
                                                color = Color.Red,
                                                progress = progress.value
                                            )
                                            *//*ExtractFramesAndProgressBar(
                                                allFramesList = allFramesList,
                                                video = video,
                                                fps = fPS,
                                                startMicroSec = milSecToMicroSec(exoPlayer.currentPosition)-secToMicroSec(1),
                                                endMicroSec = milSecToMicroSec(exoPlayer.currentPosition)+secToMicroSec(1),
                                                context = context
                                            )*//*
                                            val pair = iterableList[iterator]
                                            Log.d("mert", "${pair.second} started...")

                                            frames.add(mediaMetadataRetriever.getFrameAtTime(pair.first, MediaMetadataRetriever.OPTION_CLOSEST)!!)
                                            iterator++
                                            progress.value += addToProgress
                                            Log.d("mert","progress value: ${progress.value}")
                                            Log.d("mert","frames size: ${frames.size}")
                                            if(iterator >= iterableList.size)
                                            {
                                                Log.d("mert","finish")
                                                allFramesList.add(ArrayList(frames))
                                                Log.d("mert","a_0: ${allFramesList.last().size}")
                                                frames.clear()
                                                iterableList.clear()
                                                iterator = 0
                                                addToProgress = 0f
                                                Log.d("mert","a_1: ${allFramesList.last().size}")
                                                progress.value = 0f
                                                isExtracting.value = false
                                                zoomIn.value++
                                            }
                                        }*/
                                        LinearProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(15.dp),
                                            backgroundColor = Color.LightGray,
                                            color = Color.Red,
                                            progress = progress.value
                                        )
                                        Spacer(modifier = Modifier.size(5.dp))

                                        Row(){
                                            Button(onClick = {
                                                Log.d("mert","current position: ${exoPlayer.currentPosition}")
                                                isExtracting.value = true
                                                fps+=6

                                                MainScope().launch(Main) {
                                                    val result = extractFramesMediaRetriever_2(
                                                        allFramesList,
                                                        video,
                                                        fps,
                                                        milSecToMicroSec(exoPlayer.currentPosition)-secToMicroSec(1),
                                                        milSecToMicroSec(exoPlayer.currentPosition)+secToMicroSec(1),
                                                        progress,
                                                        context
                                                    )
                                                    if(result){
                                                        Log.d("mert","result came with true")
                                                        isExtracting.value = false
                                                        zoomIn.value++
                                                    }
                                                }
                                                Log.d("mert","launch/async cikisi....")
                                            },
                                                colors = ButtonDefaults.buttonColors(Color.White)) {
                                                Text(text = "Capture Mode", color = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else{
                Log.d("mert","izin ver ama ya")
            }
        }
        requestLauncher.launch(listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE).toTypedArray())
    }
}

suspend fun extractFramesMediaRetriever_2
            (allFramesList: MutableList<ArrayList<Bitmap?>>,video: File, fps: Int, startMicroSec: Long, endMicroSec: Long,
             progress: MutableState<Float>, context: Context): Boolean
= withContext(Dispatchers.Default){
    val frames:ArrayList<Bitmap?>
    val concatAmount:Long = secToMicroSec(1)/fps
    var i:Long = startMicroSec
    var ctr = 0
    val iterableList = ArrayList<Pair<Long,Int>>()
    while(i <= endMicroSec)
    {
        iterableList.add(Pair(i,ctr))
        i+=concatAmount
        ctr++
    }


    val addToProgress = 1f/iterableList.size
    val asyncList = ArrayList<Deferred<Boolean>>()

    frames = ArrayList<Bitmap?>().run {
        for(x in 1..ctr) add(null)
        this
    }
    val mediaMetadataRetriever = MediaMetadataRetriever()
    mediaMetadataRetriever.setDataSource(video.absolutePath)
    Log.d("mert","frames initial capacity: ${frames.size}")
    val time = measureTimeMillis {
        for(it in iterableList) {
            asyncList.add(
                async {
                    Log.d("mert", "${it.second} started...")
                    frames[it.second] = mediaMetadataRetriever.getFrameAtTime(it.first, MediaMetadataRetriever.OPTION_CLOSEST)!!
                    progress.value += addToProgress
                    Log.d("mert","${it.second} finished !! (progress value: ${progress.value})")
                    true
                }
            )
        }
        Log.d("mert","extracting for cikisi...")
        asyncList.awaitAll()
    }
    Log.d("mert","time: $time")
    allFramesList.add(frames)
    true
}

//theFolderPathWithoutRoot => root/<parameter>
fun getVideo(theFolderPathWithoutRoot: String, theNameOfFile: String):File?
{

    val absPath = Environment.getExternalStorageDirectory().absolutePath
    val theFolderPath = "$absPath/$theFolderPathWithoutRoot"
    val theFilePath = "$theFolderPath/$theNameOfFile"
    Log.d("mert","theFolderPath: "+theFolderPath)

    val folder = File(theFolderPath)
    val files = folder.listFiles()
    if (files != null) {
        for (i in files){
            Log.d("mert",i.name)
        }
    }
    else{
        Log.d("mert","folder is empty")
    }

    val video = File(theFilePath)
    if(video.exists()) return video
    return null
}

fun getCurrPage(currSliderValue: Float, pageCount: Int):MutableState<Int>
{
    return mutableStateOf(floor(currSliderValue*pageCount).toInt())
}

fun milSecToMicroSec(milSec:Long):Long = milSec*1000
fun microSecToMilSec(microSec:Long):Long = microSec/1000
fun secToMilSec(sec:Long):Long = sec*1000
fun milSecToSec(milSec:Long):Long = milSec/1000
fun secToMicroSec(sec:Long):Long = sec*1000000
fun microSecToSec(microSec:Long):Long = microSec/1000000








