package kr.co.iefriends.pcsx2;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.view.Surface;

import com.armsx2.BiosInfo;

import java.lang.ref.WeakReference;

/**
 * JNI bridge to the ARMSX2 emulation core (libemucore_4k.so, built from the core/ submodule).
 *
 * The package and class name are fixed: the native library exports
 * Java_kr_co_iefriends_pcsx2_NativeApp_* symbols and calls back into the static methods at the
 * bottom of this class by name. Only the natives NarutoUltimate 5 uses are declared here; the
 * core exports more, which is harmless.
 *
 * Licensed GPL-3.0-or-later, like the core it binds to.
 */
public final class NativeApp {

	/** Callbacks from the core into the app. Set by the emulation runtime. */
	public interface Listener {
		void onVmPaused(boolean paused);
		void onPadRumble(int pad, int largeMotor, int smallMotor);
	}

	public static volatile Listener listener;
	public static boolean hasNoNativeBinary;
	public static String loadError = "";
	public static String loadedLibrary = "";

	static {
		String libraryName = getRuntimePageSize() >= 16384 ? "emucore_16k" : "emucore_4k";
		try {
			System.loadLibrary(libraryName);
			loadedLibrary = libraryName;
			hasNoNativeBinary = false;
		} catch (UnsatisfiedLinkError e) {
			hasNoNativeBinary = true;
			loadError = libraryName + ": " + e.getMessage();
		}
	}

	private NativeApp() {}

	public static long getRuntimePageSize() {
		try {
			long pageSize = Os.sysconf(OsConstants._SC_PAGESIZE);
			return pageSize > 0 ? pageSize : 4096;
		} catch (Throwable ignored) {
			return 4096;
		}
	}

	private static WeakReference<Context> mContext;

	public static void setContext(Context context) {
		mContext = new WeakReference<>(context.getApplicationContext());
	}

	public static Context getContext() {
		return mContext != null ? mContext.get() : null;
	}

	// ---- lifecycle / settings -------------------------------------------------------------
	public static native void initialize(String dataRoot, String biosFolder, int apiVer);
	public static native void setSetting(String section, String key, String type, String value);
	public static native void commitSettings();
	public static native void emulog(String msg);
	public static native boolean applyGSSettingsLive();
	public static native int reloadPatches();
	public static native void setEnabledPatches(boolean cheats, String[] allNames, String[] enabledNames, String serial);
	public static native String getLastBootError();
	public static native String getBuildVersion();
	public static native void setAdpfEnabled(boolean enabled);
	public static native void setAffinityMode(int mode);

	// ---- game info ------------------------------------------------------------------------
	public static native String getGameTitle(String path);
	public static native String getGameSerial();
	public static native String getGameCRC();
	public static native String getGameSerialFromFd(int fd);
	public static native int getCompatibilityForSerial(String serial);
	public static native String getRegionForSerial(String serial);
	public static native BiosInfo getBiosInfoFromFd(int fd);

	// ---- stats ----------------------------------------------------------------------------
	public static native float getFPS();
	public static native float getVPS();
	public static native float getEmuSpeedPercent();
	public static native float getCpuThreadUsage();
	public static native float getGsThreadUsage();
	public static native boolean isHardwareRenderer();
	public static native void osdShowFPS(boolean enabled);
	public static native void osdShowMessages(boolean enabled);

	// ---- graphics -------------------------------------------------------------------------
	public static native void setCustomVulkanDriver(String driverDir, String driverName, String redirectDir, String hookLibDir);
	public static native void setAutoRendererGpuStrings(String vendor, String renderer, String version);
	public static native void setAspectRatio(int type);
	public static native void setFpsCap(int fps);
	public static native void renderUpscalemultiplier(float value);
	public static native void renderSoftware();
	public static native void renderOpenGL();
	public static native void renderVulkan();
	public static native void renderAuto();
	public static native void flushShaderCache();
	public static native void setDisplayRefreshRate(float hz);

	// ---- input ----------------------------------------------------------------------------
	public static native void setPadButton(int index, int range, boolean pressed);
	public static native void resetKeyStatus();

	// ---- audio ----------------------------------------------------------------------------
	public static native void setAudioVolume(int volume);
	public static native void setAudioMuted(boolean muted);

	// ---- VM / surface ---------------------------------------------------------------------
	public static native void onNativeSurfaceCreated();
	public static native void onNativeSurfaceChanged(Surface surface, int w, int h);
	public static native void onNativeSurfaceDestroyed();
	public static native boolean runVMThread(String path);
	public static native void pause();
	public static native void resume();
	public static native void setAppBackgrounded(boolean backgrounded);
	public static native void shutdown();
	public static native boolean hasActiveVM();

	// ---- save states / memory cards -------------------------------------------------------
	public static native boolean saveStateToSlot(int slot);
	public static native boolean loadStateFromSlot(int slot);
	public static native boolean isMemcardBusy();
	public static native boolean createMemoryCard(String name, int type, int fileType);
	public static native boolean isMemoryCard(String name);

	// =======================================================================================
	// Called FROM native. Names and signatures are looked up by the core — do not rename.
	// =======================================================================================

	public static void vmSetPaused(boolean paused) {
		Listener l = listener;
		if (l != null) l.onVmPaused(paused);
	}

	public static void onPadRumble(int pad, int largeMotor, int smallMotor) {
		Listener l = listener;
		if (l != null) l.onPadRumble(pad, largeMotor, smallMotor);
	}

	public static void playSound(String path) {
		// RetroAchievements unlock sounds; not used by this app.
	}

	public static void onAutoTuneCommitted(String serial, String section, String key, String value) {
		// ARMSX2 auto-tune is not driven by this app; nothing to persist.
	}

	public static String[] findSiblingChds(String uriString) {
		return new String[0];
	}

	public static int openContentUri(String uriString) {
		Context context = getContext();
		if (context == null) return -1;
		ContentResolver resolver = context.getContentResolver();
		try {
			ParcelFileDescriptor pfd = resolver.openFileDescriptor(Uri.parse(uriString), "r");
			if (pfd != null) return pfd.detachFd();
		} catch (Exception ignored) {
		}
		return -1;
	}

	/** Fallback for FUSE storage where libc mkdir() is denied but java.io succeeds. */
	public static boolean createDirectoryPath(String path) {
		if (path == null || path.isEmpty()) return false;
		try {
			java.io.File dir = new java.io.File(path);
			if (dir.isDirectory()) return true;
			dir.mkdirs();
			return dir.isDirectory();
		} catch (Throwable t) {
			return false;
		}
	}

	/** Fallback for FUSE storage where libc fopen(O_CREAT) is denied but java.io succeeds. */
	public static boolean createFilePath(String path) {
		if (path == null || path.isEmpty()) return false;
		try {
			java.io.File file = new java.io.File(path);
			if (file.isFile()) return true;
			java.io.File parent = file.getParentFile();
			if (parent != null && !parent.isDirectory()) parent.mkdirs();
			return file.createNewFile() || file.isFile();
		} catch (Throwable t) {
			return false;
		}
	}
}
