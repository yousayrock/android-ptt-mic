package io.github.teamclouday.androidMic.domain.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.Vibrator
import android.os.VibratorManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent
import android.util.Log
import io.github.teamclouday.androidMic.Mode
import io.github.teamclouday.androidMic.AndroidMicApp
import io.github.teamclouday.androidMic.R
import io.github.teamclouday.androidMic.domain.audio.MicAudioManager
import io.github.teamclouday.androidMic.domain.streaming.MicStreamManager
import io.github.teamclouday.androidMic.utils.ignore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


data class ServiceStates(
    var isStreamStarted: Boolean = false,
    var isAudioStarted: Boolean = false,
    var isMuted: Boolean = false,
    var mode: Mode = Mode.WIFI
)

private const val TAG = "ForegroundService"
const val WAIT_PERIOD = 500L


const val BIND_SERVICE_ACTION = "BIND_SERVICE_ACTION"
const val STOP_STREAM_ACTION = "STOP_STREAM_ACTION"
const val PTT_PRESS_ACTION = "PTT_PRESS_ACTION"
const val PTT_RELEASE_ACTION = "PTT_RELEASE_ACTION"
private const val MEDIA_SESSION_TAG = "AndroidPttMic"

class ForegroundService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun updateNotification() {
        val notificationManager =
            getSystemService(NotificationManager::class.java)
        val notification = messageui.getNotification(states.isMuted)
        notificationManager.notify(NOTIF_ID, notification)
    }

    private inner class ServiceHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {

            val commandData = CommandData.fromMessage(msg)

            when (Command.entries[msg.what]) {
                Command.StartStream -> startStream(commandData, msg.replyTo)
                Command.StopStream -> stopStream(msg.replyTo)
                Command.GetStatus -> getStatus(msg.replyTo)
                Command.BindCheck -> {
                    uiMessenger = msg.replyTo
                }

            }

        }
    }

    private fun reply(replyTo: Messenger?, resp: ResponseData) {
        replyTo?.send(resp.toResponseMsg())
    }

    private lateinit var handlerThread: HandlerThread
    private lateinit var serviceLooper: Looper
    private lateinit var serviceHandler: ServiceHandler
    private lateinit var serviceMessenger: Messenger

    private var managerAudio: MicAudioManager? = null
    private var managerStream: MicStreamManager? = null


    private val states = ServiceStates()
    private lateinit var messageui: MessageUi
    private var mediaSession: MediaSession? = null

    // This field is true if the UI is running
    private var isBind = false
    private var uiMessenger: Messenger? = null


    override fun onCreate() {
        Log.d(TAG, "onCreate")
        // create message handler
        handlerThread = HandlerThread("MicServiceStart", Process.THREAD_PRIORITY_BACKGROUND)
        handlerThread.start()
        serviceLooper = handlerThread.looper
        serviceHandler = ServiceHandler(handlerThread.looper)
        serviceMessenger = Messenger(serviceHandler)

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        messageui = MessageUi(this)

        // Side buttons mapped by Android to media keys can reach this active session
        // while the Activity is hidden or the screen is off.
        mediaSession = MediaSession(this, MEDIA_SESSION_TAG).apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?: return false
                    Log.i(TAG, "media key code=${event.keyCode} action=${event.action} repeat=${event.repeatCount} device=${event.deviceId}")
                    val supportedPttKey = event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                        event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                        event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                        event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                    if (!supportedPttKey) return false

                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        serviceHandler.post { setPttPressed(true) }
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        serviceHandler.post { setPttPressed(false) }
                    }
                    return true
                }
            }, Handler(serviceLooper))
            isActive = true
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                    .setState(PlaybackState.STATE_PAUSED, 0, 0f)
                    .build()
            )
        }
    }

    // note that onBind is only called on the first call of bind()
    // that's why we set isBind = true in onStartCommand
    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind")
        isBind = true

        return serviceMessenger.binder
    }

    private var serviceShouldStop = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        when (intent?.action) {
            STOP_STREAM_ACTION -> {
                Log.d(TAG, "STOP_STREAM_ACTION")
                stopStream(null)
            }

            BIND_SERVICE_ACTION -> {
                isBind = true
                serviceShouldStop = false
            }

            PTT_PRESS_ACTION -> serviceHandler.post { setPttPressed(true) }
            PTT_RELEASE_ACTION -> serviceHandler.post { setPttPressed(false) }

            else -> {
                Log.w(TAG, "unknown action for onStartCommand")
            }
        }
        return START_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)
        Log.d(TAG, "onUnbind")
        isBind = false
        uiMessenger = null

        if (!states.isStreamStarted) {
            // delay to handle reconfiguration
            // (Service is not destroy when the screen rotate)
            serviceShouldStop = true
            scope.launch {
                delay(3000.milliseconds)
                if (serviceShouldStop)
                    stopService()
            }
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        stopService()
        mediaSession?.release()
        mediaSession = null
    }

    private fun stopService() {
        Log.d(TAG, "stopService")
        managerAudio?.shutdown()
        managerAudio = null
        managerStream?.shutdown()
        managerStream = null
        serviceLooper.quitSafely()
        ignore { handlerThread.join(WAIT_PERIOD) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private fun setPttPressed(pressed: Boolean) {
        if (!states.isStreamStarted || !states.isAudioStarted) return
        if (states.isMuted == !pressed) return

        states.isMuted = !pressed
        if (pressed) {
            managerAudio?.unmute()
            vibrateForPttStart()
            mediaSession?.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                    .setState(PlaybackState.STATE_PLAYING, 0, 0f)
                    .build()
            )
        } else {
            managerAudio?.mute()
            mediaSession?.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                    .setState(PlaybackState.STATE_PAUSED, 0, 0f)
                    .build()
            )
        }
        updateNotification()
        reply(uiMessenger, ResponseData(isMuted = states.isMuted))
    }

    private fun vibrateForPttStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
                android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(35)
        }
    }


    private fun showMessage(msg: String) {
        if (!isBind) {
            messageui.showMessage(msg)
        }
    }

    // start streaming
    private fun startStream(msg: CommandData, replyTo: Messenger) {
        states.isMuted = true
        PttAccessibilityBridge.isStreamActive = false
        // check connection state
        if (states.isStreamStarted) {
            reply(
                replyTo,
                ResponseData(
                    msg = this.getString(R.string.stream_already_started),
                    isConnected = true,
                    isMuted = states.isMuted,
                )
            )
            return
        }
        shutdownStream()
        shutdownAudio()

        Log.d(TAG, "startStream [start]")

        try {
            managerStream =
                MicStreamManager(
                    applicationContext,
                    scope,
                    msg.mode!!,
                    msg.ip,
                    msg.port,
                    onConnectionChanged = { connected ->
                        serviceHandler.post {
                            if (!connected) setPttPressed(false)
                            if (states.isStreamStarted) updateNotification()
                        }
                    }
                )
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "start stream with mode ${msg.mode!!.name} failed:\n${e.message}")

            reply(
                replyTo,
                ResponseData(
                    msg = applicationContext.getString(R.string.error) + e.message,
                    isConnected = false,
                    isMuted = states.isMuted,
                )
            )
            return
        }

        if (managerStream?.connect() != true || managerStream?.isConnected() != true
        ) {

            reply(
                replyTo,
                ResponseData(
                    msg = applicationContext.getString(R.string.failed_to_connect),
                    isConnected = false,
                    isMuted = states.isMuted,
                )
            )
            shutdownStream()
            return
        }


        if (!startAudio(msg, replyTo)) {
            shutdownStream()
            shutdownAudio()
            return
        }

        managerStream?.start(
            managerAudio!!.audioStream(),
            serviceMessenger
        )

        states.isStreamStarted = true
        PttAccessibilityBridge.isStreamActive = true
        Log.d(TAG, "startStream [connected]")

        reply(
            replyTo,
            ResponseData(
                msg = applicationContext.getString(R.string.connected_device) + managerStream?.getInfo(),
                isConnected = true,
                isMuted = states.isMuted,
            )
        )

    }

    // stop streaming
    fun stopStream(replyTo: Messenger?) {
        Log.d(TAG, "stopStream")

        stopAudio(replyTo)

        shutdownStream()

        reply(
            uiMessenger,
            ResponseData(
                msg = applicationContext.getString(R.string.device_disconnected),
                isConnected = false,
                isMuted = states.isMuted,
            )
        )

        if (!isBind) {
            stopService()
        }
    }

    private fun shutdownStream() {
        PttAccessibilityBridge.isStreamActive = false
        setPttPressed(false)
        managerStream?.shutdown()
        managerStream = null
        states.isStreamStarted = false
    }


    // start mic
    private fun startAudio(msg: CommandData, replyTo: Messenger): Boolean {
        // check audio state
        if (states.isAudioStarted) {
            reply(replyTo, ResponseData(msg = this.getString(R.string.microphone_already_started)))
            return true
        }

        Log.d(TAG, "startAudio [start]")

        // start audio recording
        managerAudio?.shutdown()
        try {
            managerAudio = MicAudioManager(
                ctx = applicationContext,
                scope = scope,
                sampleRate = msg.sampleRate!!.value,
                audioFormat = msg.audioFormat!!.value,
                channelCount = msg.channelCount!!.value,
                audioSource = msg.audioSource!!,
                processingMode = AndroidMicApp.appModule.appPreferences.processingMode.getBlocking(),
                releaseCueEnabled = AndroidMicApp.appModule.appPreferences.releaseCueEnabled.getBlocking(),
            )
        } catch (e: IllegalArgumentException) {
            reply(replyTo, ResponseData(msg = application.getString(R.string.error) + e.message))
            return false
        }

        managerAudio?.start()
        managerAudio?.mute()

        // we need to start in foreground to use the mic
        // but no need to specify a flag because we declared
        // the type in manifest
        startForeground(NOTIF_ID, messageui.getNotification(states.isMuted))

        Log.d(TAG, "startAudio [recording]")
        states.isAudioStarted = true

        reply(replyTo, ResponseData(msg = application.getString(R.string.mic_start_recording)))

        return true
    }

    // stop mic
    private fun stopAudio(replyTo: Messenger?) {
        Log.d(TAG, "stopAudio")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        shutdownAudio()
        reply(replyTo, ResponseData(msg = application.getString(R.string.recording_stopped)))
    }


    private fun shutdownAudio() {
        managerAudio?.shutdown()
        managerAudio = null
        states.isAudioStarted = false
    }


    private fun getStatus(replyTo: Messenger) {
        Log.d(TAG, "getStatus")

        reply(
            replyTo,
            ResponseData(
                isConnected = states.isStreamStarted && managerStream?.isConnected() == true,
                isMuted = states.isMuted
            )
        )
    }
}
