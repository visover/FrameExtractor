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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.roundToInt
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

                                val allFramesList = remember{mutableListOf<ArrayList<Bitmap?>>()}
                                val zoomIn = remember{ mutableStateOf(0)}
                                val pagerState = rememberPagerState()
                                val currSliderValue = remember{ mutableStateOf(0f)}
                                var capturedTime = remember{mutableStateOf(0L)}
                                var pageCount by remember{mutableStateOf(0)}
                                val currPage = remember(key1 = currSliderValue.value){ getCurrPage(currSliderValue.value,pageCount)}
                                val changeCurrPage = rememberCoroutineScope()
                                var fps by remember{mutableStateOf(0)}

                                val isExtracting = remember{mutableStateOf(false)}
                                val progress = remember{mutableStateOf(0f) }

                                val expanded = remember{ mutableStateOf(false) }

                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Cyan),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally) {

                                    if(zoomIn.value != 0)
                                    {
                                        Log.d("mert", "isExtracting at Start: ${isExtracting.value}")
                                        val curFrames = allFramesList[zoomIn.value - 1]
                                        pageCount = curFrames.size

                                        if(expanded.value)
                                        {
                                            var grabFileName by remember{mutableStateOf(TextFieldValue("Name of the file"))}
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                TextField(modifier = Modifier.background(Color.Green).border(BorderStroke(1.dp,Color.White)),
                                                    value = grabFileName, onValueChange = {grabFileName = it}
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Button(onClick = {
                                                    if(grabFileName.text == "")
                                                        Toast.makeText(this@MainActivity,"Name of file can not be empty !!",Toast.LENGTH_SHORT).show()
                                                    if(grabFileName.text == "Name of the file")
                                                        Toast.makeText(this@MainActivity,"Please enter a name to file !!",Toast.LENGTH_SHORT).show()
                                                    else
                                                    {
                                                        val file = File("${Environment.getExternalStorageDirectory().absolutePath}/FrameSelector","${grabFileName.text}.jpg")
                                                        val fos = FileOutputStream(file)
                                                        curFrames[pagerState.currentPage]!!.compress(Bitmap.CompressFormat.JPEG,100,fos)
                                                        fos.flush()
                                                        fos.close()
                                                        Log.d("mert","grabbing done")
                                                        Toast.makeText(this@MainActivity,"Frame Grabbed successfully...",Toast.LENGTH_SHORT).show()
                                                        expanded.value = false
                                                    }
                                                }) {
                                                    Text("FINISH GRAB")
                                                }
                                            }
                                        }
                                        HorizontalPager(
                                            count = pageCount,
                                            state = pagerState
                                        ) { index ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(360.dp)
                                            ) {
                                                Image(
                                                    bitmap = curFrames[index]!!.asImageBitmap(),
                                                    contentDescription = ""
                                                )
                                            }
                                        }

                                        Slider(value = currSliderValue.value, onValueChange = {
                                            currSliderValue.value = it
                                            changeCurrPage.launch {
                                                pagerState.scrollToPage(currPage.value)
                                            }
                                        })
                                        Spacer(modifier = Modifier.size(5.dp))
                                        MyIndicator(indicatorProgress = progress.value)
                                        Spacer(modifier = Modifier.size(5.dp))
                                        Text(text = if(isExtracting.value) "Changing FPS: ${fps-6} => ${fps}" else "FPS: ${fps}",color = Color.Blue)
                                        Row{
                                            Button(onClick = {
                                                if(!isExtracting.value)
                                                {
                                                    if(zoomIn.value == 1)
                                                    {
                                                        allFramesList.clear()
                                                    }
                                                    fps-=6
                                                    zoomIn.value -= 1
                                                }
                                            }, colors = ButtonDefaults.buttonColors(Color.Gray)) {
                                                if(zoomIn.value == 1)
                                                {
                                                    Text(text = "Go Back to Video", color = Color.White)
                                                }
                                                else
                                                {
                                                    Text(text = "Zoom OUT", color = Color.White)
                                                }

                                            }
                                            Spacer(modifier = Modifier.size(5.dp))
                                            Button(onClick = {
                                                if(!isExtracting.value)
                                                {
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
                                                                milSecToMicroSec(capturedTime.value)-secToMicroSec(1),
                                                                milSecToMicroSec(capturedTime.value)+secToMicroSec(1),
                                                                progress,
                                                                false,
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
                                                }
                                            }, colors = ButtonDefaults.buttonColors(Color.Gray)){
                                                Text(text = "Zoom IN", color = Color.White)
                                            }
                                            Spacer(modifier = Modifier.size(5.dp))
                                            OutlinedButton(onClick = {
                                                expanded.value = !expanded.value
                                            },
                                                modifier= Modifier.size(50.dp),  //avoid the oval shape
                                                shape = CircleShape,
                                                border= BorderStroke(1.dp, Color.Black),
                                                contentPadding = PaddingValues(0.dp),  //avoid the little icon
                                                colors = ButtonDefaults.outlinedButtonColors(Color.Gray)
                                            ) {
                                                Text("GRAB",color = Color.White)
                                            }
                                        }
                                    }
                                    else
                                    {
                                        /*if(!isExtracting.value) progress.value = 0f*/
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
                                        MyIndicator(indicatorProgress = progress.value)
                                        Spacer(modifier = Modifier.size(5.dp))
                                        Row{
                                            //Capture Mode
                                            Button(onClick = {
                                                if(!isExtracting.value)
                                                {
                                                    Log.d("mert","current position: ${exoPlayer.currentPosition}")
                                                    isExtracting.value = true
                                                    fps+=6
                                                    capturedTime.value = exoPlayer.currentPosition

                                                    MainScope().launch(Main) {
                                                        val result = extractFramesMediaRetriever_2(
                                                            allFramesList,
                                                            video,
                                                            fps,
                                                            if(capturedTime.value < 1000) 0
                                                            else milSecToMicroSec(capturedTime.value)-secToMicroSec(1)
                                                            ,
                                                            if((exoPlayer.duration - capturedTime.value) < 1000) milSecToMicroSec(exoPlayer.duration)
                                                            else milSecToMicroSec(capturedTime.value)+secToMicroSec(1)
                                                            ,
                                                            progress,
                                                            true,
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
                                            },
                                                colors = ButtonDefaults.buttonColors(Color.Gray)) {
                                                Text(text = "Capture Mode", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        requestLauncher.launch(listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,android.Manifest.permission.WRITE_EXTERNAL_STORAGE).toTypedArray())
    }
}
@Composable
fun MyIndicator(indicatorProgress: Float) {
    val progressAnimDuration = 1500
    val progressAnimation by animateFloatAsState(
        targetValue = indicatorProgress,
        animationSpec = tween(durationMillis = progressAnimDuration, easing = FastOutSlowInEasing)
    )

    BoxWithConstraints(modifier = Modifier.background(Color.Transparent)){
        Box{
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .height(15.dp), // Rounded edges
                progress = progressAnimation,
                color = Color.Red
            )
        }
        Box(modifier = Modifier.fillMaxWidth()){
            Row(modifier = Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.Center){
                Text("%${(indicatorProgress*100).roundToInt()}",color = Color.Blue, modifier = Modifier.height(15.dp), fontSize = 12.sp)
            }
        }

    }
}

suspend fun extractFramesMediaRetriever_2
            (allFramesList: MutableList<ArrayList<Bitmap?>>,video: File, fps: Int, startMicroSec: Long, endMicroSec: Long,
             progress: MutableState<Float>, isFirstExtract: Boolean,context: Context): Boolean
= withContext(Dispatchers.Default){
    val frames:ArrayList<Bitmap?>
    val concatAmount:Long = secToMicroSec(1)/fps
    var i:Long = startMicroSec
    var ctr = 0
    val iterableList = ArrayList<Pair<Long,Int>>()
    while(i <= endMicroSec)
    {
        Log.d("mert","iterable ${ctr} : ${i}")
        iterableList.add(Pair(i,ctr))
        i+=concatAmount
        ctr++
    }


    val addToProgress = 1f/iterableList.size
    val asyncList = ArrayList<Deferred<Boolean>>()

    if(isFirstExtract)
        frames = ArrayList<Bitmap?>().run {
            for(x in 1..ctr) add(null)
            this
        }
    else
    {
        frames = allFramesList.run{ArrayList(this[this.size-1])}
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
    progress.value = 0f
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








