package com.example

import android.app.Application
import android.util.Log
import com.example.extractor.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe

class AudioStreamerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            NewPipe.init(OkHttpDownloader.getInstance())
            Log.d("AudioStreamerApp", "NewPipeExtractor initialized successfully")
        } catch (e: Exception) {
            Log.e("AudioStreamerApp", "Failed to initialize NewPipeExtractor", e)
        }
    }
}
