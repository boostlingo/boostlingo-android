package com.boostlingo.android.quickstart

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.boostlingo.android.*
import com.boostlingo.android.quickstart.databinding.ActivityVideoBinding
import com.google.android.material.snackbar.Snackbar
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlin.properties.Delegates


class VideoActivity : AppCompatActivity() {

    private lateinit var callRequest: CallRequest
    private lateinit var region: String
    private lateinit var token: String

    private val binding: ActivityVideoBinding by lazy {
        ActivityVideoBinding.inflate(layoutInflater)
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
    private var currentCall: BLVideoCall? = null
    private val audioSwitch by lazy {
        AudioSwitch(
            applicationContext, preferredDeviceList = listOf(
                AudioDevice.BluetoothHeadset::class.java,
                AudioDevice.WiredHeadset::class.java,
                AudioDevice.Speakerphone::class.java,
                AudioDevice.Earpiece::class.java
            )
        )
    }
    private var savedVolumeControlStream by Delegates.notNull<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.hasExtra(MainActivity.CALL_REQUEST_EXTRA)) {
            callRequest = intent.getParcelableExtra(MainActivity.CALL_REQUEST_EXTRA)!!
            region = intent.getStringExtra(MainActivity.REGION_EXTRA)!!
            token = intent.getStringExtra(MainActivity.AUTH_TOKEN_EXTRA)!!
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

        binding.switchCameraActionFab
            .setOnClickListener {
                currentCall?.switchCameraSource()
            }

        binding.muteActionFab
            .setOnClickListener {
                currentCall?.let {
                    it.isMuted = !it.isMuted
                }
            }

        binding.localVideoActionFab
            .setOnClickListener {
                currentCall?.isVideoEnabled = !(currentCall?.isVideoEnabled ?: false)
            }

        binding.hangupActionFab
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

        binding.chatActionFab
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

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        savedVolumeControlStream = volumeControlStream
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        updateUI(State.NO_CALL);

        // Ensure the microphone permission is enabled.
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraMicrophoneAndBluetooth()
        } else {
            startAudioSwitch()
            makeCall()
        }
    }

    override fun onResume() {
        super.onResume()
        startAudioSwitch()
    }

    private fun startAudioSwitch() {
        audioSwitch.start { _: List<AudioDevice?>?, _: AudioDevice? ->
            return@start Unit
        }
    }

    private fun checkPermissions(permissions: Array<String>): Boolean {
        var shouldCheck = true
        for (permission in permissions) {
            shouldCheck = shouldCheck and (PackageManager.PERMISSION_GRANTED ==
                    ContextCompat.checkSelfPermission(this, permission))
        }
        return shouldCheck
    }

    private fun requestPermissions(permissions: Array<String>) {
        var displayRational = false
        for (permission in permissions) {
            displayRational =
                displayRational or ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    permission
                )
        }
        if (displayRational) {
            Toast.makeText(this, "Permissions needed", Toast.LENGTH_LONG).show()
        } else {
            ActivityCompat.requestPermissions(
                this, permissions, CAMERA_MIC_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun checkPermissionForCameraAndMicrophone(): Boolean {
        return checkPermissions(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun requestPermissionForCameraMicrophoneAndBluetooth() {
        val permissionsList: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        }
        requestPermissions(permissionsList)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            /*
             * The first two permissions are Camera & Microphone, bluetooth isn't required but
             * enabling it enables bluetooth audio routing functionality.
             */
            val cameraAndMicPermissionGranted =
                ((PackageManager.PERMISSION_GRANTED == grantResults[0])
                        and (PackageManager.PERMISSION_GRANTED == grantResults[1]))

            if (cameraAndMicPermissionGranted) {
                startAudioSwitch()
                makeCall()
            } else {
                Toast.makeText(
                    this,
                    "Permissions needed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun makeCall() {
        boostlingoSdk.makeVideoCall(
            callRequest,
            binding.thumbnailVideoView
        )
            .subscribeOn(AndroidSchedulers.mainThread())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { updateUI(State.CALLING) }
            .subscribe(
                {
                    currentCall = it
                },
                ::handleError
            )
    }

    private fun updateUI(state: State) {
        when (state) {
            State.NO_CALL -> {
                binding.videoStatusTextview.text = "No active call"
            }
            State.CALLING -> {
                binding.videoStatusTextview.text = "Calling"
            }
            State.IN_PROGRESS -> {
                binding.videoStatusTextview.text = "Call in progress"
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
                audioSwitch.activate()
                currentCall = callEvent.call as BLVideoCall
                updateUI(State.IN_PROGRESS)

                Log.d(null, "Participants: ${callEvent.call.participants.count()}")
                callEvent.call
                    .participants
                    .firstOrNull()
                    ?.let {
                        currentCall?.addRenderer(it.identity, binding.primaryVideoView)
                    }
            }
            is BLCallEvent.CallDisconnected -> {
                Log.d(null, "BLCallEvent.CallDisconnected")
                audioSwitch.deactivate()
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
                audioSwitch.deactivate()
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
                Log.d(null, "BLCallEvent.ChatMessageReceived: ${callEvent.message}")
            }
            is BLCallEvent.ParticipantAdded -> {
                Log.d(null, "BLCallEvent.ParticipantAdded ${callEvent.participant.accountId}")
                Log.d(null, "Participants: ${callEvent.call.participants.count()}")
                callEvent.participant
                    .let {
                        currentCall?.addRenderer(it.identity, binding.primaryVideoView)
                    }
            }
            is BLCallEvent.ParticipantRemoved -> {
                Log.d(null, "BLCallEvent.ParticipantUpdated: " + callEvent.participant.accountId)
            }
            is BLCallEvent.ParticipantUpdated -> {
                Log.d(null, "BLCallEvent.ParticipantUpdated: " + callEvent.participant.accountId)
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
        audioSwitch.stop()
        volumeControlStream = savedVolumeControlStream

        compositeDisposable.dispose()
        boostlingoSdk.dispose()

        super.onDestroy()
    }

    private enum class State {
        NO_CALL,
        CALLING,
        IN_PROGRESS
    }

    private companion object {

        const val CAMERA_MIC_PERMISSION_REQUEST_CODE = 1
    }
}