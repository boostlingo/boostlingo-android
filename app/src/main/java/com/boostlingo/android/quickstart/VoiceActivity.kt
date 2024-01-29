package com.boostlingo.android.quickstart

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.media.AudioManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import com.boostlingo.android.*
import com.boostlingo.android.quickstart.MainActivity.Companion.AUTH_TOKEN_EXTRA
import com.boostlingo.android.quickstart.MainActivity.Companion.CALL_REQUEST_EXTRA
import com.boostlingo.android.quickstart.MainActivity.Companion.REGION_EXTRA
import com.boostlingo.android.quickstart.databinding.ActivityVoiceBinding
import com.google.android.material.snackbar.Snackbar
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class VoiceActivity : AppCompatActivity() {

    private lateinit var callRequest: CallRequest
    private lateinit var region: String
    private lateinit var token: String

    private val binding: ActivityVoiceBinding by lazy {
        ActivityVoiceBinding.inflate(layoutInflater)
    }

    private val boostlingoSdk: BoostlingoSDK by lazy {
        BoostlingoSDK(
            token,
            this,
            BLLogLevel.DEBUG,
            region
        )
    }

    private val compositeDisposable: CompositeDisposable = CompositeDisposable()
    private var currentCall: BLVoiceCall? = null
    private var audioSwitch: AudioSwitch? = null
    private var savedVolumeControlStream = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.hasExtra(CALL_REQUEST_EXTRA)) {
            callRequest = intent.getParcelableExtra(CALL_REQUEST_EXTRA)!!
            region = intent.getStringExtra(REGION_EXTRA)!!
            token = intent.getStringExtra(AUTH_TOKEN_EXTRA)!!
        }

        setContentView(binding.root)

        boostlingoSdk.callEventObservable
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                ::handleCallEvent,
                ::handleError
            )
            .let { compositeDisposable.add(it) }

        binding.muteSwitch
            .setOnCheckedChangeListener { _, b ->
                currentCall?.isMuted = b
            }

        binding.speakerSwitch
            .setOnCheckedChangeListener { _, _ ->
                if (audioSwitch?.selectedAudioDevice is AudioDevice.Earpiece) {
                    audioSwitch?.selectDevice(
                        audioSwitch?.availableAudioDevices?.firstOrNull {
                            it is AudioDevice.Speakerphone
                        }
                    )
                } else {
                    audioSwitch?.selectDevice(
                        audioSwitch?.availableAudioDevices?.firstOrNull {
                            it is AudioDevice.Earpiece
                        }
                    )
                }
            }

        binding.hangupButton
            .setOnClickListener {
                boostlingoSdk.hangUp()
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            updateUI(State.NO_CALL)
                        },
                        ::handleError
                    )
                    .let { compositeDisposable.add(it) }
            }

        binding.chatButton
            .setOnClickListener {
                boostlingoSdk.sendChatMessage("TEST")
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        {
                            Snackbar.make(
                                binding.rootView,
                                "Chat message sent: " + it.text,
                                Snackbar.LENGTH_LONG
                            ).show()
                        },
                        ::handleError
                    )
                    .let { compositeDisposable.add(it) }
            }

        binding.dialThirdPartyButton
            .setOnClickListener {
                currentCall?.dialThirdParty("18004444444")
                    ?.subscribeOn(AndroidSchedulers.mainThread())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.subscribe(
                        {
                            Log.d(null, "dialThirdParty success")
                        },
                        {
                            Log.d(null, it.localizedMessage.orEmpty())
                        }
                    )
                    ?.let { compositeDisposable.add(it) }
            }

        binding.muteThirdPartyButton
            .setOnClickListener {
                currentCall?.participants
                    ?.firstOrNull { it.participantType == BLParticipantType.THIRD_PARTY }
                    ?.let {
                        currentCall?.muteThirdPartyParticipant(it.identity, it.isAudioEnabled)
                            ?.subscribeOn(AndroidSchedulers.mainThread())
                            ?.observeOn(AndroidSchedulers.mainThread())
                            ?.subscribe(
                                {
                                    Log.d(null, "muteThirdPartyParticipant success")
                                },
                                {
                                    Log.d(null, it.localizedMessage.orEmpty())
                                }
                            )
                            ?.let { compositeDisposable.add(it) }
                    }
            }

        binding.hangUpThirdPartyButton
            .setOnClickListener {
                currentCall?.participants
                    ?.firstOrNull { it.participantType == BLParticipantType.THIRD_PARTY }
                    ?.let {
                        currentCall?.hangupThirdPartyParticipant(it.identity)
                            ?.subscribeOn(AndroidSchedulers.mainThread())
                            ?.observeOn(AndroidSchedulers.mainThread())
                            ?.subscribe(
                                {
                                    Log.d(null, "hangupThirdPartyParticipant success")
                                },
                                {
                                    Log.d(null, it.localizedMessage.orEmpty())
                                }
                            )
                            ?.let { compositeDisposable.add(it) }
                    }
            }

        /*
         * Ensure required permissions are enabled
         */
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            if (!hasPermissions(this, Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.BLUETOOTH_CONNECT)) {
                requestPermissionForMicrophoneAndBluetooth()
            } else {
                startAudioSwitch()
                makeCall()
            }
        } else {
            if (!hasPermissions(this, Manifest.permission.RECORD_AUDIO)) {
                requestPermissionForMicrophone()
            } else {
                startAudioSwitch()
                makeCall()
            }
        }

        /*
         * Setup audio device management and set the volume control stream
         */
        audioSwitch = AudioSwitch(applicationContext);
        savedVolumeControlStream = volumeControlStream;
        volumeControlStream = AudioManager.STREAM_VOICE_CALL;

        updateUI(State.NO_CALL);
    }

    private fun requestPermissionForMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.RECORD_AUDIO
            )
        ) {
            Snackbar.make(
                binding.rootView,
                "Microphone permissions needed. Please allow in your application settings.",
                Snackbar.LENGTH_LONG
            ).show()
        } else {
            requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO),
                MIC_PERMISSION_REQUEST_CODE
            )
        }
    }

    @RequiresApi(api = VERSION_CODES.M)
    private fun requestPermissionForMicrophoneAndBluetooth() {
        if (!hasPermissions(
                this, Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            startAudioSwitch()
            makeCall()
        }
    }

    private fun hasPermissions(context: Context?, vararg permissions: String?): Boolean {
        if (context != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        permission!!
                    ) != PERMISSION_GRANTED
                ) {
                    return false
                }
            }
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        startAudioSwitch()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        /*
         * Check if required permissions are granted
         */
        if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
            if (!hasPermissions(this, Manifest.permission.RECORD_AUDIO)) {
                Snackbar.make(
                    binding.rootView,
                    "Microphone permission needed. Please allow in your application settings.",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                if (!hasPermissions(this, Manifest.permission.BLUETOOTH_CONNECT)) {
                    Snackbar.make(
                        binding.rootView,
                        "Without bluetooth permission app will fail to use bluetooth.",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                /*
                 * Due to bluetooth permissions being requested at the same time as mic
                 * permissions, AudioSwitch should be started after providing the user the option
                 * to grant the necessary permissions for bluetooth.
                 */
                startAudioSwitch()
                makeCall()
            }
        } else {
            if (!hasPermissions(this, Manifest.permission.RECORD_AUDIO)) {
                Snackbar.make(
                    binding.rootView,
                    "Microphone permissions needed. Please allow in your application settings.",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                startAudioSwitch()
                makeCall()
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun makeCall() {
        boostlingoSdk.makeVoiceCall(callRequest)
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { updateUI(State.CALLING) }
            .subscribe(
                {
                    currentCall = it
                },
                ::handleError
            )
            .let { compositeDisposable.add(it) }
    }

    private fun updateUI(state: State) {
        when (state) {
            State.NO_CALL -> {
                binding.statusTextView.text = "No active call"
                binding.muteSwitch.isEnabled = false
                binding.speakerSwitch.isEnabled = false
                binding.hangupButton.isEnabled = false
                binding.chatButton.isEnabled = false
            }
            State.CALLING -> {
                binding.statusTextView.text = "Calling"
                binding.muteSwitch.isEnabled = false
                binding.speakerSwitch.isEnabled = false
                binding.hangupButton.isEnabled = true
                binding.chatButton.isEnabled = false
            }
            State.IN_PROGRESS -> {
                binding.statusTextView.text = "Call with: " + currentCall?.interlocutorInfo?.requiredName
                binding.muteSwitch.isEnabled = true
                binding.speakerSwitch.isEnabled = true
                binding.hangupButton.isEnabled = true
                binding.chatButton.isEnabled = true
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        updateUI(State.NO_CALL)
        boostlingoSdk.hangUp()
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    updateUI(State.NO_CALL)
                },
                ::handleError
            )
            .let { compositeDisposable.add(it) }

        super.onBackPressed()
    }

    private fun handleCallEvent(callEvent: BLCallEvent) {
        when (callEvent) {
            is BLCallEvent.CallConnected -> {
                Log.d(null, "BLCallEvent.CallConnected")
                audioSwitch?.activate()
                currentCall = callEvent.call as BLVoiceCall
                binding.muteSwitch.isChecked = callEvent.call.isMuted
                binding.speakerSwitch.isChecked = false
                updateUI(State.IN_PROGRESS)

                Log.d(null, "Participants: ${callEvent.call.participants.count()}")
            }
            is BLCallEvent.CallDisconnected -> {
                Log.d(null, "BLCallEvent.CallDisconnected")
                audioSwitch?.deactivate()
                currentCall = null
                updateUI(State.NO_CALL)
                Snackbar.make(
                    binding.rootView,
                    "Call did disconnect with error: " + callEvent.e?.localizedMessage,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            is BLCallEvent.CallFailedToConnect -> {
                Log.d(null, "BLCallEvent.CallFailedToConnect")
                audioSwitch?.deactivate()
                currentCall = null
                updateUI(State.NO_CALL)
                Snackbar.make(
                    binding.rootView,
                    "Call did fail to connect with error: " + callEvent.e?.localizedMessage,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            BLCallEvent.ChatConnected -> {
                Log.d(null, "BLCallEvent.ChatConnected")
            }
            BLCallEvent.ChatDisconnected -> {
                Log.d(null, "BLCallEvent.ChatDisconnected")
            }
            is BLCallEvent.ChatMessageReceived -> {
                Log.d(null, "BLCallEvent.ChatMessageReceived: ${callEvent.message.text}")
            }
            is BLCallEvent.ParticipantAdded -> {
                Log.d(null, "BLCallEvent.ParticipantAdded: ${callEvent.participant.accountId}")
            }
            is BLCallEvent.ParticipantRemoved -> {
                Log.d(null, "BLCallEvent.ParticipantRemoved: ${callEvent.participant.accountId}")
            }
            is BLCallEvent.ParticipantUpdated -> {
                Log.d(null, "BLCallEvent.ParticipantUpdated:  ${callEvent.participant.accountId}")
            }
        }
    }

    private fun handleError(t: Throwable) {
        updateUI(State.NO_CALL)
        Snackbar.make(
            binding.rootView,
            "Error: " + t.localizedMessage,
            Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        /*
         * Tear down audio device management and restore previous volume stream
         */
        audioSwitch?.stop()
        volumeControlStream = savedVolumeControlStream

        compositeDisposable.dispose()
        boostlingoSdk.dispose()
        super.onDestroy()
    }

    private fun startAudioSwitch() {
        audioSwitch?.start { _: List<AudioDevice?>?, _: AudioDevice? ->
            return@start Unit
        }
    }

    private enum class State {
        NO_CALL,
        CALLING,
        IN_PROGRESS
    }

    private companion object {

        const val MIC_PERMISSION_REQUEST_CODE = 1
        const val PERMISSIONS_REQUEST_CODE = 100
    }
}