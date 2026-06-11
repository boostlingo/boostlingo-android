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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.boostlingo.android.*
import com.boostlingo.android.quickstart.ui.theme.BoostlingoTheme
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class AIActivity : ComponentActivity() {

    private enum class State { NO_CALL, CALLING, IN_PROGRESS }

    private lateinit var callRequest: CallRequest
    private lateinit var region: String
    private lateinit var token: String

    private val boostlingoSdk: BoostlingoSDK by lazy {
        BoostlingoSDK(token, this, BLLogLevel.DEBUG, region)
    }

    private val compositeDisposable = CompositeDisposable()
    private var currentCall: BLAICall? = null
    private var audioSwitch: AudioSwitch? = null
    private var savedVolumeControlStream = 0

    // --- Compose-observable UI state ---
    private var status by mutableStateOf("No active call")
    private var controlsEnabled by mutableStateOf(false)
    private var muteChecked by mutableStateOf(false)
    private var speakerChecked by mutableStateOf(false)
    private var speakingText by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            startAudioSwitch()
            makeCall()
        } else {
            message = "Microphone permission needed. Please allow in your application settings."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.hasExtra(MainActivity.CALL_REQUEST_EXTRA)) {
            callRequest = intent.getParcelableExtra(MainActivity.CALL_REQUEST_EXTRA)!!
            region = intent.getStringExtra(MainActivity.REGION_EXTRA)!!
            token = intent.getStringExtra(MainActivity.AUTH_TOKEN_EXTRA)!!
        }

        setContent {
            BoostlingoTheme {
                AIScreen(
                    status = status,
                    speakingText = speakingText,
                    controlsEnabled = controlsEnabled,
                    muteChecked = muteChecked,
                    onMuteChange = { muteChecked = it; currentCall?.isMuted = it },
                    speakerChecked = speakerChecked,
                    onSpeakerChange = { speakerChecked = it; toggleSpeaker() },
                    onInterrupt = ::interrupt,
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

        audioSwitch = AudioSwitch(applicationContext)
        savedVolumeControlStream = volumeControlStream
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        ensurePermissionsThenCall()
    }

    private fun ensurePermissionsThenCall() {
        val needed = buildList {
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
        audioSwitch?.start { _: List<AudioDevice?>?, _: AudioDevice? -> Unit }
    }

    private fun toggleSpeaker() {
        if (audioSwitch?.selectedAudioDevice is AudioDevice.Earpiece) {
            audioSwitch?.selectDevice(audioSwitch?.availableAudioDevices?.firstOrNull { it is AudioDevice.Speakerphone })
        } else {
            audioSwitch?.selectDevice(audioSwitch?.availableAudioDevices?.firstOrNull { it is AudioDevice.Earpiece })
        }
    }

    private fun makeCall() {
        boostlingoSdk.makeAIInterpreterCall(callRequest)
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { updateState(State.CALLING) }
            .subscribe({ currentCall = it }, ::handleError)
            .let { compositeDisposable.add(it) }
    }

    private fun interrupt() {
        currentCall?.interrupt()
            ?.subscribeOn(AndroidSchedulers.mainThread())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe({ Log.d(null, "interrupt success") }, { Log.d(null, it.localizedMessage.orEmpty()) })
            ?.let { compositeDisposable.add(it) }
    }

    private fun hangUp() {
        boostlingoSdk.hangUp()
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateState(State.NO_CALL) }, ::handleError)
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
        when (state) {
            State.NO_CALL -> { status = "No active call"; speakingText = ""; controlsEnabled = false }
            State.CALLING -> { status = "Calling"; controlsEnabled = false }
            State.IN_PROGRESS -> { status = "AI Interpreter call in progress"; controlsEnabled = true }
        }
    }

    private fun handleCallEvent(callEvent: BLCallEvent) {
        when (callEvent) {
            is BLCallEvent.CallConnected -> {
                audioSwitch?.activate()
                currentCall = callEvent.call as BLAICall
                muteChecked = callEvent.call.isMuted
                speakerChecked = false
                updateState(State.IN_PROGRESS)
            }
            is BLCallEvent.CallDisconnected -> {
                audioSwitch?.deactivate()
                currentCall = null
                updateState(State.NO_CALL)
                message = "Call did disconnect with error: ${callEvent.e?.localizedMessage}"
            }
            is BLCallEvent.CallFailedToConnect -> {
                audioSwitch?.deactivate()
                currentCall = null
                updateState(State.NO_CALL)
                message = "Call did fail to connect with error: ${callEvent.e?.localizedMessage}"
            }
            is BLCallEvent.AIInterpreterStartedSpeaking -> speakingText = "AI Interpreter is speaking…"
            is BLCallEvent.AIInterpreterStoppedSpeaking -> speakingText = ""
            BLCallEvent.ChatConnected -> Unit
            BLCallEvent.ChatDisconnected -> Unit
            is BLCallEvent.ChatMessageReceived -> Unit
            is BLCallEvent.ParticipantAdded -> Log.d(null, "ParticipantAdded: ${callEvent.participant.accountId}")
            is BLCallEvent.ParticipantRemoved -> Log.d(null, "ParticipantRemoved: ${callEvent.participant.accountId}")
            is BLCallEvent.ParticipantUpdated -> Log.d(null, "ParticipantUpdated: ${callEvent.participant.accountId}")
        }
    }

    private fun handleError(t: Throwable) {
        updateState(State.NO_CALL)
        message = "Error: ${t.localizedMessage}"
    }

    override fun onDestroy() {
        audioSwitch?.stop()
        volumeControlStream = savedVolumeControlStream
        compositeDisposable.dispose()
        boostlingoSdk.dispose()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIScreen(
    status: String,
    speakingText: String,
    controlsEnabled: Boolean,
    muteChecked: Boolean,
    onMuteChange: (Boolean) -> Unit,
    speakerChecked: Boolean,
    onSpeakerChange: (Boolean) -> Unit,
    onInterrupt: () -> Unit,
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI Interpreter Call") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(status, color = MaterialTheme.colorScheme.primary)
            Text(speakingText, color = MaterialTheme.colorScheme.secondary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mute")
                    Switch(checked = muteChecked, onCheckedChange = onMuteChange, enabled = controlsEnabled)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Speaker")
                    Switch(checked = speakerChecked, onCheckedChange = onSpeakerChange, enabled = controlsEnabled)
                }
            }
            Button(onClick = onInterrupt, enabled = controlsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Interrupt AI")
            }
            Button(onClick = onDialThirdParty, enabled = controlsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Dial Third-Party")
            }
            Button(onClick = onMuteThirdParty, enabled = controlsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Mute Third-Party")
            }
            Button(onClick = onHangUpThirdParty, enabled = controlsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Hang Up Third-Party")
            }
            Button(onClick = onHangUp, enabled = controlsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Hang Up")
            }
        }
    }
}

@Preview(showBackground = true, name = "AI — speaking")
@Composable
private fun AIScreenPreview() {
    BoostlingoTheme {
        AIScreen(
            status = "AI Interpreter call in progress",
            speakingText = "AI Interpreter is speaking…",
            controlsEnabled = true,
            muteChecked = false,
            onMuteChange = {},
            speakerChecked = false,
            onSpeakerChange = {},
            onInterrupt = {},
            onDialThirdParty = {},
            onMuteThirdParty = {},
            onHangUpThirdParty = {},
            onHangUp = {},
            message = null,
            onMessageShown = {},
        )
    }
}
