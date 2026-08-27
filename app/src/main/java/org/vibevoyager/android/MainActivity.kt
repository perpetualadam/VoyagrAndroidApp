package org.vibevoyager.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Process
import android.speech.tts.TextToSpeech
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    /*
     * Android location permission result.
     */
    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val fineGranted =
                permissions[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true

            val coarseGranted =
                permissions[
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ] == true

            VoyagrLogger.log(
                this,
                "VOYAGR_GPS",
                "Location permission result: " +
                        "fine=$fineGranted, coarse=$coarseGranted"
            )

            if (fineGranted || coarseGranted) {

                VoyagrLogger.log(
                    this,
                    "VOYAGR_GPS",
                    "Android location permission granted"
                )

                loadVoyagr()

            } else {

                VoyagrLogger.log(
                    this,
                    "VOYAGR_GPS",
                    "Android location permission denied"
                )
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logLifecycle(
            "MainActivity onCreate",
            savedInstanceState
        )

        setContentView(R.layout.activity_main)

        /*
         * Start Android native Text-to-Speech.
         */
        VoyagrLogger.log(
            this,
            "VOYAGR_TTS",
            "Creating Android TextToSpeech engine"
        )

        textToSpeech =
            TextToSpeech(
                this,
                this
            )

        /*
         * WebView setup.
         */
        webView =
            findViewById(
                R.id.webView
            )

        VoyagrLogger.log(
            this,
            "VOYAGR_WEB",
            "Configuring WebView"
        )

        webView.settings.apply {

            javaScriptEnabled = true
            domStorageEnabled = true

            setGeolocationEnabled(true)

            mediaPlaybackRequiresUserGesture = false

            cacheMode =
                WebSettings.LOAD_NO_CACHE
        }

        /*
         * Expose native Android TTS to Voyagr JavaScript.
         *
         * Voyagr can call:
         *
         * window.AndroidTTS.speak("Turn left...")
         */
        webView.addJavascriptInterface(
            NativeTtsBridge(),
            "AndroidTTS"
        )

        VoyagrLogger.log(
            this,
            "VOYAGR_TTS",
            "AndroidTTS JavaScript bridge installed"
        )

        /*
         * WebView navigation callbacks.
         */
        webView.webViewClient =
            object : WebViewClient() {

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    super.onPageStarted(
                        view,
                        url,
                        favicon
                    )

                    VoyagrLogger.log(
                        this@MainActivity,
                        "VOYAGR_WEB",
                        "Page started loading: $url"
                    )
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    super.onPageFinished(
                        view,
                        url
                    )

                    VoyagrLogger.log(
                        this@MainActivity,
                        "VOYAGR_WEB",
                        "Page finished loading: $url"
                    )
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {

                    VoyagrLogger.log(
                        this@MainActivity,
                        "VOYAGR_WEB",
                        "WebView renderer gone " +
                                "didCrash=${detail?.didCrash()} " +
                                "priority=${detail?.rendererPriorityAtExit()}"
                    )

                    return super.onRenderProcessGone(
                        view,
                        detail
                    )
                }
            }

        /*
         * JavaScript console + geolocation handling.
         */
        webView.webChromeClient =
            object : WebChromeClient() {

                /*
                 * Save Voyagr's JavaScript console output
                 * into our persistent log file.
                 */
                override fun onConsoleMessage(
                    consoleMessage: ConsoleMessage
                ): Boolean {

                    val message =
                        consoleMessage.message()

                    val source =
                        consoleMessage.sourceId()

                    val line =
                        consoleMessage.lineNumber()

                    val level =
                        consoleMessage.messageLevel()

                    VoyagrLogger.log(
                        this@MainActivity,
                        "VOYAGR_WEB",
                        "JS [$level] $message " +
                                "[$source:$line]"
                    )

                    return true
                }

                /*
                 * Voyagr browser geolocation permission.
                 */
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {

                    val fineGranted =
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) ==
                                PackageManager.PERMISSION_GRANTED

                    val coarseGranted =
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) ==
                                PackageManager.PERMISSION_GRANTED

                    val cleanOrigin =
                        origin?.removeSuffix("/")

                    val allowedOrigin =
                        cleanOrigin ==
                                "https://vibevoyager.org" ||
                                cleanOrigin ==
                                "https://www.vibevoyager.org"

                    VoyagrLogger.log(
                        this@MainActivity,
                        "VOYAGR_GPS",
                        "Website requested geolocation: " +
                                "origin=$origin, " +
                                "fine=$fineGranted, " +
                                "coarse=$coarseGranted, " +
                                "allowedOrigin=$allowedOrigin"
                    )

                    if (
                        allowedOrigin &&
                        (fineGranted || coarseGranted)
                    ) {

                        VoyagrLogger.log(
                            this@MainActivity,
                            "VOYAGR_GPS",
                            "WebView geolocation permission GRANTED"
                        )

                        callback?.invoke(
                            origin,
                            true,
                            true
                        )

                    } else {

                        VoyagrLogger.log(
                            this@MainActivity,
                            "VOYAGR_GPS",
                            "WebView geolocation permission DENIED"
                        )

                        callback?.invoke(
                            origin,
                            false,
                            false
                        )
                    }
                }
            }

        /*
         * Ask Android for GPS permission,
         * then load Voyagr.
         */
        requestLocationPermission()

        /*
         * Android back gesture/button support.
         */
        onBackPressedDispatcher.addCallback(
            this,
            object :
                OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (webView.canGoBack()) {

                        VoyagrLogger.log(
                            this@MainActivity,
                            "VOYAGR_APP",
                            "Back pressed: WebView going back"
                        )

                        webView.goBack()

                    } else {

                        VoyagrLogger.log(
                            this@MainActivity,
                            "VOYAGR_APP",
                            "Back pressed: closing activity"
                        )

                        isEnabled = false

                        onBackPressedDispatcher
                            .onBackPressed()
                    }
                }
            }
        )
    }

    /*
     * Native Android TTS initialization callback.
     */
    override fun onInit(status: Int) {

        VoyagrLogger.log(
            this,
            "VOYAGR_TTS",
            "TextToSpeech onInit status=$status"
        )

        if (
            status ==
            TextToSpeech.SUCCESS
        ) {

            val languageResult =
                textToSpeech?.setLanguage(
                    Locale.UK
                )

            textToSpeech?.setSpeechRate(
                1.0f
            )

            textToSpeech?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        AudioAttributes
                            .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                    )
                    .setContentType(
                        AudioAttributes
                            .CONTENT_TYPE_SPEECH
                    )
                    .build()
            )

            ttsReady =
                languageResult !=
                        TextToSpeech.LANG_MISSING_DATA &&
                        languageResult !=
                        TextToSpeech.LANG_NOT_SUPPORTED

            VoyagrLogger.log(
                this,
                "VOYAGR_TTS",
                "Android TTS ready=$ttsReady, " +
                        "languageResult=$languageResult, " +
                        "locale=${Locale.UK}"
            )

        } else {

            ttsReady = false

            VoyagrLogger.log(
                this,
                "VOYAGR_TTS",
                "Android TTS initialization FAILED"
            )
        }
    }

    /*
     * JavaScript → Kotlin bridge.
     */
    inner class NativeTtsBridge {

        @JavascriptInterface
        fun speak(text: String) {

            VoyagrLogger.log(
                this@MainActivity,
                "VOYAGR_TTS",
                "Received from website: $text"
            )

            runOnUiThread {

                if (ttsReady) {

                    VoyagrLogger.log(
                        this@MainActivity,
                        "VOYAGR_TTS",
                        "Speaking native TTS: $text"
                    )

                    val result =
                        textToSpeech?.speak(
                            text,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "voyagr-navigation"
                        )

                    VoyagrLogger.log(
                        this@MainActivity,
                        "VOYAGR_TTS",
                        "TextToSpeech.speak result=$result"
                    )

                } else {

                    VoyagrLogger.log(
                        this@MainActivity,
                        "VOYAGR_TTS",
                        "TTS requested before engine ready: $text"
                    )
                }
            }
        }

        @JavascriptInterface
        fun stop() {

            VoyagrLogger.log(
                this@MainActivity,
                "VOYAGR_TTS",
                "Website requested TTS stop"
            )

            runOnUiThread {

                textToSpeech?.stop()

                VoyagrLogger.log(
                    this@MainActivity,
                    "VOYAGR_TTS",
                    "Native TTS stopped"
                )
            }
        }
    }

    /*
     * Android location permission.
     */
    private fun requestLocationPermission() {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED

        VoyagrLogger.log(
            this,
            "VOYAGR_GPS",
            "Checking Android location permission: " +
                    "fine=$fineGranted, coarse=$coarseGranted"
        )

        if (
            fineGranted ||
            coarseGranted
        ) {

            VoyagrLogger.log(
                this,
                "VOYAGR_GPS",
                "Location already permitted"
            )

            loadVoyagr()

        } else {

            VoyagrLogger.log(
                this,
                "VOYAGR_GPS",
                "Requesting Android location permission"
            )

            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /*
     * Load Voyagr.
     */
    private fun loadVoyagr() {

        if (webView.url == null) {

            VoyagrLogger.log(
                this,
                "VOYAGR_WEB",
                "Loading https://vibevoyager.org/"
            )

            webView.loadUrl(
                "https://vibevoyager.org/"
            )

            /*
             * Temporary behaviour:
             *
             * Start the foreground navigation service
             * when Voyagr loads.
             *
             * Later we'll make the website explicitly
             * start/stop this when navigation begins/ends.
             */
            startNavigationService()

        } else {

            VoyagrLogger.log(
                this,
                "VOYAGR_WEB",
                "loadVoyagr skipped because WebView already has URL: " +
                        webView.url
            )
        }
    }

    /*
     * Start Android foreground navigation service.
     */
    private fun startNavigationService() {

        VoyagrLogger.log(
            this,
            "VOYAGR_SERVICE",
            "Requesting NavigationService start"
        )

        try {

            val serviceIntent =
                Intent(
                    this,
                    NavigationService::class.java
                )

            ContextCompat.startForegroundService(
                this,
                serviceIntent
            )

            VoyagrLogger.log(
                this,
                "VOYAGR_SERVICE",
                "NavigationService start request sent successfully"
            )

        } catch (e: Exception) {

            VoyagrLogger.log(
                this,
                "VOYAGR_SERVICE",
                "NavigationService start FAILED: " +
                        "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        logLifecycle(
            "MainActivity onNewIntent",
            incomingIntent = intent
        )
    }

    override fun onStart() {
        super.onStart()

        logLifecycle(
            "MainActivity onStart"
        )
    }

    override fun onResume() {
        super.onResume()

        logLifecycle(
            "MainActivity onResume"
        )
    }

    override fun onPause() {

        logLifecycle(
            "MainActivity onPause"
        )

        super.onPause()
    }

    override fun onStop() {

        logLifecycle(
            "MainActivity onStop"
        )

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        logLifecycle(
            "MainActivity onSaveInstanceState"
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        VoyagrLogger.log(
            this,
            "VOYAGR_APP",
            "MainActivity onTrimMemory level=$level"
        )
    }

    override fun onDestroy() {

        logLifecycle(
            "MainActivity onDestroy"
        )

        VoyagrLogger.log(
            this,
            "VOYAGR_TTS",
            "Shutting down Android TTS"
        )

        textToSpeech?.stop()
        textToSpeech?.shutdown()

        webView.removeJavascriptInterface(
            "AndroidTTS"
        )

        VoyagrLogger.log(
            this,
            "VOYAGR_TTS",
            "AndroidTTS JavaScript bridge removed"
        )

        super.onDestroy()
    }

    private fun logLifecycle(
        event: String,
        savedInstanceState: Bundle? = null,
        incomingIntent: Intent? = intent
    ) {
        val config =
            resources.configuration

        VoyagrLogger.log(
            this,
            "VOYAGR_APP",
            "$event " +
                    "instance=${System.identityHashCode(this)} " +
                    "pid=${Process.myPid()} " +
                    "task=$taskId " +
                    "isFinishing=$isFinishing " +
                    "isChangingConfigurations=$isChangingConfigurations " +
                    "hasSavedState=${savedInstanceState != null} " +
                    "action=${incomingIntent?.action} " +
                    "flags=0x${Integer.toHexString(incomingIntent?.flags ?: 0)} " +
                    "uiMode=${config.uiMode} " +
                    "orientation=${config.orientation} " +
                    "screen=${config.screenWidthDp}x${config.screenHeightDp}"
        )
    }
}

