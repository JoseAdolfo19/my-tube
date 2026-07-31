package com.miappvideos

import android.app.Application
import com.miappvideos.api.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe

class MiAppVideosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeDownloader())
    }
}
