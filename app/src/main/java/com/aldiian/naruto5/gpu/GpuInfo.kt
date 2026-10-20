package com.aldiian.naruto5.gpu

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import com.aldiian.naruto5.diag.AppLog
import com.aldiian.naruto5.storage.AppPrefs

/** GL vendor/renderer/version, read once from a throw-away 1×1 pbuffer context. */
data class GpuInfo(val vendor: String, val renderer: String, val version: String) {
    val isAdreno: Boolean get() = renderer.contains("Adreno", ignoreCase = true)

    /** Adreno 6xx/7xx: the generations the ARMSX2 Turnip builds target (Pad 6 = Adreno 650). */
    val turnipCapable: Boolean get() = Regex("""Adreno\D*([67]\d\d)(?!\d)""", RegexOption.IGNORE_CASE).containsMatchIn(renderer)

    fun serialize() = "$vendor\u0001$renderer\u0001$version"

    companion object {
        val UNKNOWN = GpuInfo("", "", "")

        fun get(prefs: AppPrefs): GpuInfo {
            prefs.gpuStrings?.split('\u0001')?.takeIf { it.size == 3 && it[1].isNotBlank() }?.let {
                return GpuInfo(it[0], it[1], it[2])
            }
            val info = query()
            if (info.renderer.isNotBlank()) prefs.gpuStrings = info.serialize()
            return info
        }

        private fun query(): GpuInfo = runCatching {
            val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2)
            EGL14.eglInitialize(dpy, ver, 0, ver, 1)
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT */,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE,
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            EGL14.eglChooseConfig(dpy, attribs, 0, configs, 0, 1, num, 0)
            val ctx = EGL14.eglCreateContext(dpy, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            val surf = EGL14.eglCreatePbufferSurface(dpy, configs[0],
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            val info = GpuInfo(
                GLES20.glGetString(GLES20.GL_VENDOR) ?: "",
                GLES20.glGetString(GLES20.GL_RENDERER) ?: "",
                GLES20.glGetString(GLES20.GL_VERSION) ?: "",
            )
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surf)
            EGL14.eglDestroyContext(dpy, ctx)
            EGL14.eglTerminate(dpy)
            AppLog.i("GPU: ${info.renderer} / ${info.version}")
            info
        }.getOrElse {
            AppLog.e("Query GPU gagal", it)
            UNKNOWN
        }
    }
}
