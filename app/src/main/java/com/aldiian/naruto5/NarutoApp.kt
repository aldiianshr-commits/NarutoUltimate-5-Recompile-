package com.aldiian.naruto5

import android.app.Application
import android.os.Process
import com.aldiian.naruto5.diag.AppLog
import kr.co.iefriends.pcsx2.NativeApp

class NarutoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val isEmuProcess = getProcessName().endsWith(":emu")
        if (!isEmuProcess) AppLog.mainPid = Process.myPid()
        AppLog.init(this)
        NativeApp.setContext(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                AppLog.crashFile(this).writeText(
                    "Proses: ${getProcessName()} / thread ${t.name}\n" + e.stackTraceToString()
                )
            }
            previous?.uncaughtException(t, e)
        }
        if (NativeApp.hasNoNativeBinary) AppLog.e("Core native gagal dimuat: ${NativeApp.loadError}")
    }
}
