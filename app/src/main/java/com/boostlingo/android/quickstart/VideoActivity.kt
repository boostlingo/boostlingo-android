package com.boostlingo.android.quickstart

import android.Manifest
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.boostlingo.android.*
import com.boostlingo.android.quickstart.ui.theme.BoostlingoTheme
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch
import com.twilio.video.VideoView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class VideoActivity : ComponentActivity() {

    private enum class State { NO_CALL, CALLING, IN_PROGRESS }

    private lateinit var callRequest: CallRequest
    private lateinit var region: String
    private lateinit var token: String

    private val boostlingoSdk: BoostlingoSDK by lazy {
        BoostlingoSDK(token, this, BLLogLevel.DEBUG, region)
    }

    private val compositeDisposable = CompositeDisposable()
    private var currentCall: BLVideoCall? = null
    private val audioSwitch by lazy {
        AudioSwitch(
            applicationContext, preferredDeviceList = listOf(
                AudioDevice.BluetoothHeadset::class.java,
                AudioDevice.WiredHeadset::class.java,
                AudioDevice.Speakerphone::class.java,
                AudioDevice.Earpiece::class.java,
            )
        )
    }
    private var savedVolumeControlStream = 0

    // Twilio renderer surfaces, hosted in Compose via AndroidView.
    private lateinit var primaryVideoView: VideoView
    private lateinit var thumbnailVideoView: VideoView

    private var status by mutableStateOf("No active call")
    private var message by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cameraAndMic = grants[Manifest.permission.CAMERA] == true &&
            grants[Manifest.permission.RECORD_AUDIO] == true
        if (cameraAndMic) {
            startAudioSwitch()
            makeCall()
        } else {
            message = "Camera and microphone permissions needed."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.hasExtra(MainActivity.CALL_REQUEST_EXTRA)) {
            callRequest = intent.getParcelableExtra(MainActivity.CALL_REQUEST_EXTRA)!!
            region = intent.getStringExtra(MainActivity.REGION_EXTRA)!!
            token = intent.getStringExtra(MainActivity.AUTH_TOKEN_EXTRA)!!
        }

        primaryVideoView = VideoView(this)
        thumbnailVideoView = VideoView(this)

        setContent {
            BoostlingoTheme {
                VideoScreen(
                    status = status,
                    primaryVideoView = primaryVideoView,
                    thumbnailVideoView = thumbnailVideoView,
                    onSwitchCamera = { currentCall?.switchCameraSource() },
                    onToggleMute = { currentCall?.let { it.isMuted = !it.isMuted } },
                    onToggleVideo = { currentCall?.let { it.isVideoEnabled = !it.isVideoEnabled } },
                    onSendTestMessage = ::sendTestMessage,
                    onDialThirdParty = ::dialThirdParty,
                    onMuteThirdParty = ::muteThirdParty,
                    onHangUpThirdParty = ::hangUpThirdParty,
                    onHangUp = ::hangUp,
                    message = message,
                    onMessageShown = { message = null },
                )
            }
        }

        boostlingoSdk.callEventObservable
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::handleCallEvent, ::handleError)
            .let { compositeDisposable.add(it) }

        savedVolumeControlStream = volumeControlStream
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        ensurePermissionsThenCall()
    }

    private fun ensurePermissionsThenCall() {
        val needed = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (needed.all { ContextCompat.checkSelfPermission(this, it) == PERMISSION_GRANTED }) {
            startAudioSwitch()
            makeCall()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        startAudioSwitch()
    }

    private fun startAudioSwitch() {
        audioSwitch.start { _: List<AudioDevice?>?, _: AudioDevice? -> Unit }
    }

    private fun makeCall() {
        boostlingoSdk.makeVideoCall(callRequest, thumbnailVideoView)
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { updateState(State.CALLING) }
            .subscribe({ currentCall = it }, ::handleError)
            .let { compositeDisposable.add(it) }
    }

    private fun hangUp() {
        boostlingoSdk.hangUp()
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateState(State.NO_CALL) }, ::handleError)
            .let { compositeDisposable.add(it) }
    }

    private fun sendTestMessage() {
        boostlingoSdk.sendChatMessage("TEST")
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ message = "Chat message sent: ${it.text}" }, ::handleError)
            .let { compositeDisposable.add(it) }
    }

    private fun dialThirdParty() {
        currentCall?.dialThirdParty("18004444444")
            ?.subscribeOn(AndroidSchedulers.mainThread())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({ Log.d(null, "dialThirdParty success") }, { Log.d(null, it.localizedMessage.orEmpty()) })
            ?.let { compositeDisposable.add(it) }
    }

    private fun muteThirdParty() {
        currentCall?.participants
            ?.firstOrNull { it.participantType == BLParticipantType.THIRD_PARTY }
            ?.let { participant ->
                currentCall?.muteThirdPartyParticipant(participant.identity, participant.isAudioEnabled)
                    ?.subscribeOn(AndroidSchedulers.mainThread())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.subscribe({ Log.d(null, "muteThirdPartyParticipant success") }, { Log.d(null, it.localizedMessage.orEmpty()) })
                    ?.let { compositeDisposable.add(it) }
            }
    }

    private fun hangUpThirdParty() {
        currentCall?.participants
            ?.firstOrNull { it.participantType == BLParticipantType.THIRD_PARTY }
            ?.let { participant ->
                currentCall?.hangupThirdPartyParticipant(participant.identity)
                    ?.subscribeOn(AndroidSchedulers.mainThread())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.subscribe({ Log.d(null, "hangupThirdPartyParticipant success") }, { Log.d(null, it.localizedMessage.orEmpty()) })
                    ?.let { compositeDisposable.add(it) }
            }
    }

    private fun updateState(state: State) {
        status = when (state) {
            State.NO_CALL -> "No active call"
            State.CALLING -> "Calling"
            State.IN_PROGRESS -> "Call in progress"
        }
    }

    private fun handleCallEvent(callEvent: BLCallEvent) {
        when (callEvent) {
            is BLCallEvent.CallConnected -> {
                audioSwitch.activate()
                currentCall = callEvent.call as BLVideoCall
                updateState(State.IN_PROGRESS)
                callEvent.call.participants.firstOrNull()?.let {
                    currentCall?.addRenderer(it.identity, primaryVideoView)
                }
            }
            is BLCallEvent.CallDisconnected -> {
                audioSwitch.deactivate()
                currentCall = null
                updateState(State.NO_CALL)
                message = "Call did disconnect with error: ${callEvent.e?.localizedMessage}"
            }
            is BLCallEvent.CallFailedToConnect -> {
                audioSwitch.deactivate()
                currentCall = null
                updateState(State.NO_CALL)
                message = "Call did fail to connect with error: ${callEvent.e?.localizedMessage}"
            }
            BLCallEvent.ChatConnected -> Log.d(null, "BLCallEvent.ChatConnected")
            BLCallEvent.ChatDisconnected -> Log.d(null, "BLCallEvent.ChatDisconnected")
            is BLCallEvent.ChatMessageReceived -> Log.d(null, "ChatMessageReceived: ${callEvent.message}")
            is BLCallEvent.ParticipantAdded -> {
                currentCall?.addRenderer(callEvent.participant.identity, primaryVideoView)
            }
            is BLCallEvent.ParticipantRemoved -> Log.d(null, "ParticipantRemoved: ${callEvent.participant.accountId}")
            is BLCallEvent.ParticipantUpdated -> Log.d(null, "ParticipantUpdated: ${callEvent.participant.accountId}")
            is BLCallEvent.AIInterpreterStartedSpeaking -> Unit
            is BLCallEvent.AIInterpreterStoppedSpeaking -> Unit
        }
    }

    private fun handleError(t: Throwable) {
        updateState(State.NO_CALL)
        message = "Error: ${t.localizedMessage}"
    }

    override fun onDestroy() {
        audioSwitch.stop()
        volumeControlStream = savedVolumeControlStream
        compositeDisposable.dispose()
        boostlingoSdk.dispose()
        super.onDestroy()
    }
}

@Composable
private fun VideoScreen(
    status: String,
    primaryVideoView: VideoView,
    thumbnailVideoView: VideoView,
    onSwitchCamera: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onSendTestMessage: () -> Unit,
    onDialThirdParty: () -> Unit,
    onMuteThirdParty: () -> Unit,
    onHangUpThirdParty: () -> Unit,
    onHangUp: () -> Unit,
    message: String?,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { primaryVideoView }, modifier = Modifier.fillMaxSize())

        AndroidView(
            factory = { thumbnailVideoView },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .width(120.dp)
                .height(160.dp),
        )

        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Text(status, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.primary)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onSwitchCamera, modifier = Modifier.fillMaxWidth()) { Text("Switch Camera") }
            Button(onClick = onToggleMute, modifier = Modifier.fillMaxWidth()) { Text("Toggle Mute") }
            Button(onClick = onToggleVideo, modifier = Modifier.fillMaxWidth()) { Text("Toggle Local Video") }
            Button(onClick = onSendTestMessage, modifier = Modifier.fillMaxWidth()) { Text("Send Test Message") }
            Button(onClick = onDialThirdParty, modifier = Modifier.fillMaxWidth()) { Text("Dial Third-Party") }
            Button(onClick = onMuteThirdParty, modifier = Modifier.fillMaxWidth()) { Text("Mute Third-Party") }
            Button(onClick = onHangUpThirdParty, modifier = Modifier.fillMaxWidth()) { Text("Hang Up Third-Party") }
            Button(onClick = onHangUp, modifier = Modifier.fillMaxWidth()) { Text("Hang Up") }
        }
    }
}

@Preview(showBackground = true, name = "Video — in progress")
@Composable
private fun VideoScreenPreview() {
    val context = LocalContext.current
    BoostlingoTheme {
        VideoScreen(
            status = "Call in progress",
            primaryVideoView = remember { VideoView(context) },
            thumbnailVideoView = remember { VideoView(context) },
            onSwitchCamera = {},
            onToggleMute = {},
            onToggleVideo = {},
            onSendTestMessage = {},
            onDialThirdParty = {},
            onMuteThirdParty = {},
            onHangUpThirdParty = {},
            onHangUp = {},
            message = null,
            onMessageShown = {},
        )
    }
}
