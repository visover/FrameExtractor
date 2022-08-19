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
import java.io.*
import java.nio.channels.AsynchronousFileChannel
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.collections.ArrayList
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

                val video = getVideo("FrameSelector","LostInTheEcho.mp4")

                if(video != null)
                {
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

                                val frames = remember{mutableListOf<Bitmap?>()}
                                val zoomIn = remember{ mutableStateOf(false)}
                                val pagerState = rememberPagerState()
                                val currSliderValue = remember{ mutableStateOf(0f)}
                                var pageCount by remember{mutableStateOf(0)}
                                val currPage = remember(key1 = currSliderValue.value){ getCurrPage(currSliderValue.value,pageCount)}
                                val changeCurrPage = rememberCoroutineScope()

                                val bitmapSource = remember{mutableStateOf<Bitmap?>(null)}

                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Yellow),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    if(zoomIn.value)
                                    {
                                        HorizontalPager(count = pageCount, state = pagerState) { index ->
                                            Card(modifier = Modifier
                                                .fillMaxWidth()
                                                .height(360.dp)) {
                                                // TODO: Glide Image
                                                Image(bitmap = frames[index]!!.asImageBitmap(), contentDescription = "")
                                            }
                                        }
                                        Slider(value = currSliderValue.value, onValueChange = {
                                            currSliderValue.value = it
                                            changeCurrPage.launch {
                                                pagerState.scrollToPage(currPage.value)
                                            }
                                        })
                                        Spacer(modifier = Modifier.size(5.dp))
                                        Button(onClick = {
                                                frames.clear()
                                                zoomIn.value = false
                                             }, colors = ButtonDefaults.buttonColors(Color.White)) {
                                            Text(text = "Go Back to Video", color = Color.Green)
                                        }
                                    }
                                    else
                                    {
                                        AndroidView(modifier = Modifier
                                            .height(360.dp)
                                            .fillMaxWidth()
                                            .background(Color.Green)
                                            /*.onGloballyPositioned { coordinates ->
                                                realSize.value = coordinates.size
                                            }*/,
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
                                        Row(){
                                            Button(onClick = {
                                                exoPlayer.seekTo(14000L)
                                            },
                                                colors = ButtonDefaults.buttonColors(Color.White)) {
                                                Text(text = "Seek To", color = Color.Red)
                                            }
                                            Spacer(Modifier.size(3.dp))
                                            Button(onClick = {
                                                Log.d("mert","current position: ${exoPlayer.currentPosition}")

                                                //extractFramesGlide (or) extractFramesMediaRetriever
                                                extractFramesMediaRetriever(frames,
                                                    video,
                                                    10,
                                                    milSecToMicroSec(exoPlayer.currentPosition)-secToMicroSec(8),
                                                    milSecToMicroSec(exoPlayer.currentPosition)+secToMicroSec(8),
                                                    context)
                                                Log.d("mert","frames size after assigning: ${frames.size}")
                                                pageCount = frames.size
                                                val f = frames.groupingBy { it }.eachCount().filter { it.value>1 }
                                                Log.d("mert","f: ${f}")
                                                zoomIn.value = true
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

fun extractFramesCoil(iterableList: ArrayList<Pair<Long,Int>>, video: File, fps: Int, startMicroSec: Long, endMicroSec: Long, context: Context)
{
    Log.d("mert","extractFrames To array started...")
    Log.d("mert","start sec: $startMicroSec , end sec: $endMicroSec")
    //.override()


    val concatAmount:Long = secToMicroSec(1)/fps
    var i:Long = startMicroSec
    var ctr = 0
    while(i <= endMicroSec) {
        iterableList.add(Pair(i, ctr))
        i += concatAmount
        ctr++
    }

    /*val mediaMetadataRetriever = MediaMetadataRetriever()
    mediaMetadataRetriever.setDataSource(video.absolutePath)*/
}
fun extractFramesGlide(frames: MutableList<Bitmap?>, video: File, fps: Int, startMicroSec: Long, endMicroSec: Long, context: Context)
{
    Log.d("mert","extractFrames To array started...")
    Log.d("mert","start sec: $startMicroSec , end sec: $endMicroSec")
    //.override()


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
    /*val mediaMetadataRetriever = MediaMetadataRetriever()
    mediaMetadataRetriever.setDataSource(video.absolutePath)*/
    for(x in 0..iterableList.size-1)
    {
        frames.add(null)
    }
    val y = CountDownLatch(iterableList.size)
    for(time in iterableList)
    {
        val insideLatch = CountDownLatch(1)
        Log.d("mert","time: ${time.first}")

        Glide.with(context)
            .asBitmap()
            .load(video.absolutePath)
            .apply(RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .frame(time.first)
                .override(300)
            )
            .listener(object: RequestListener<Bitmap>{
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    isFirstResource: Boolean
                ): Boolean {
                    TODO("Not yet implemented")
                }

                override fun onResourceReady(
                    resource: Bitmap?,
                    model: Any?,
                    target: Target<Bitmap>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    frames.removeAt(time.second)
                    frames.add(time.second,resource)
                    Log.d("mert","frame successful: ${resource.hashCode()} at ${time.second}")
                    y.countDown()
                    insideLatch.countDown()
                    return true
                }

            }).submit()
        insideLatch.await()
        /*frames.add(mediaMetadataRetriever.getFrameAtTime(time.first,MediaMetadataRetriever.OPTION_CLOSEST)!!)*/
    }
    y.await()
}
@RequiresApi(Build.VERSION_CODES.P)
fun extractFramesMediaRetriever(frames: MutableList<Bitmap?>,video: File, fps: Int, startMicroSec: Long, endMicroSec: Long, context: Context)
{

    val concatAmount:Long = secToMicroSec(1)/fps
    var i:Long = startMicroSec
    var ctr = 0
    val iterableList = ArrayList<Pair<Long,Int>>()
    while(i< endMicroSec)
    {
        iterableList.add(Pair(i,ctr))
        i+=concatAmount
        ctr++
    }

    val mediaMetadataRetriever = MediaMetadataRetriever()
    mediaMetadataRetriever.setDataSource(video.absolutePath)

    for(it in iterableList){
        Log.d("mert","${it.second} started...")
        frames.add(
            mediaMetadataRetriever.getFrameAtTime(it.first,MediaMetadataRetriever.OPTION_CLOSEST)!!
        )
    }
}



fun sa(pair: Pair<Long,Int>){
    Log.d("mert","i: ${pair.first}  ctr: ${pair.second}")
}
fun bitmapToFile(bitmap: Bitmap, directory: String,fileNameToSave: String): File?
{
    // File name like "image.png"
    //create a file to write bitmap data
    var file: File? = null
    return try {
        file = File(directory + File.separator + fileNameToSave)
        if(!file.exists())
        {
            Log.d("mert","bitmap writing...")
            file.createNewFile()

            //Convert bitmap to byte array
            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG,100,bos) // YOU can also save it in JPEG
            val bitmapdata = bos.toByteArray()


            //write the bytes in file
            val fos = FileOutputStream(file)
            fos.write(bitmapdata)
            fos.flush()
            fos.close()
            Log.d("mert","bitmap written successfully!!")
        }
        else
        {
            Log.d("mert","bitmap found!!!")
        }
        file
    } catch (e: Exception) {
        e.printStackTrace()
        file // it will return null
    }
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

fun getFramesAsArray(video: File):ArrayList<Bitmap>
{
    val frameList = ArrayList<Bitmap>()

    val theDirectory = "${video.absolutePath.dropLastWhile { it != '/' }}${video.name.split(".")[0]+"_frames"}"
    val theDirectoryFile = File(theDirectory)
    if(theDirectoryFile.exists())
    {
        val files = theDirectoryFile.listFiles()

        if (files != null) {
            files.sortBy { it.name.split("_")[1].split(".")[0].toInt() }
            for(i in files)
            {
                Log.d("mert","file: "+i.name)
                frameList.add(BitmapFactory.decodeFile(i.absolutePath))
            }
        }
    }
    return frameList
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



fun getFPS(video:File)
{
    val extractor = MediaExtractor()
    var frameRate = 24 //may be default
    try {
        //Adjust data source as per the requirement if file, URI, etc.
        extractor.setDataSource(video.absolutePath)
        val numTracks = extractor.getTrackCount()
        Log.d("mert",numTracks.toString())
        var i = 0
        while(i<numTracks){
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime != null) {
                if (mime.startsWith("video/")) {
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
                        Log.d("mert",frameRate.toString())
                    }
                }
            }
            i++
        }
    }
    catch (e: IOException) {
        e.printStackTrace();
    }
    finally {
        //Release stuff
        extractor.release();
    }
}








