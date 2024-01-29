package com.boostlingo.android.quickstart

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.boostlingo.android.AdditionalField
import com.boostlingo.android.BLLogLevel
import com.boostlingo.android.BoostlingoSDK
import com.boostlingo.android.BoostlingoSDK.Companion.baseURLs
import com.boostlingo.android.CallRequest
import com.boostlingo.android.quickstart.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import retrofit2.HttpException

class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val region: String
        get() = binding.regionTextView.text.toString()

    private val authToken: String
        get() = binding.tokenTextView.text.toString()

    private val boostlingoSdk: BoostlingoSDK by lazy {
        BoostlingoSDK(
            authToken,
            this,
            BLLogLevel.DEBUG,
            region,
        )
    }

    private val compositeDisposable: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        updateUI(State.NOT_INIT)

        binding.initializeButton.setOnClickListener {
            if (!baseURLs.containsKey(region) || authToken.isBlank()) {
                Snackbar.make(
                    binding.rootView,
                    "Error: invalid region or token",
                    Snackbar.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            boostlingoSdk.initialize()
                .subscribeOn(Schedulers.io())
                .andThen(
                    boostlingoSdk.getCallDictionaries()
                        .subscribeOn(Schedulers.io())
                )
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { updateUI(State.LOADING) }
                .subscribe(
                    { callDictionaries ->
                        updateUI(State.INIT)
                        val language1 = callDictionaries.languages.first { language -> language.englishName == "Afrikaans" }
                        val language2 = callDictionaries.languages.first { language -> language.englishName == "Amharic" }
                        val serviceType = callDictionaries.serviceTypes.first { language -> language.name == "Medical" }
                        val gender = callDictionaries.genders.first()
                        binding.languageToTextView.text = "${language1.englishName} ${language1.id}"
                        binding.languageFromTextView.text = "${language2.englishName} ${language2.id}"
                        binding.serviceTypeTextView.text = "${serviceType.name} ${serviceType.id}"
                        binding.genderTextView.text = "${gender.name} ${gender.id}"
                    },
                    {
                        updateUI(State.NOT_INIT)
                        val httpException = it as? HttpException
                        if (httpException != null) {
                            Snackbar.make(
                                binding.rootView,
                                "Error: " + httpException.localizedMessage + " StatusCode: " + httpException.code(),
                                Snackbar.LENGTH_LONG
                            ).show()
                        } else {
                            Snackbar.make(
                                binding.rootView,
                                "Error: " + it.localizedMessage,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                )
                .let { compositeDisposable.add(it) }
        }

        binding.voiceCallButton.setOnClickListener {
            val callRequest = CallRequest(
                languageFromId = 45,
                languageToId = 42,
                serviceTypeId = 2,
                genderId = null,
                data = listOf(
                    AdditionalField(
                        key = "MyInternalId",
                        value = "1984"
                    ),
                    AdditionalField(
                        key = "CustomCallField",
                        value = "CustomCallFieldValue"
                    )
                ),
                isVideo = false
            )
            boostlingoSdk.validateCallReq(callRequest)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { updateUI(State.LOADING) }
                .subscribe(
                    {
                        val intent = Intent(this, VoiceActivity::class.java)
                        intent.putExtra(CALL_REQUEST_EXTRA, callRequest)
                        intent.putExtra(REGION_EXTRA, region)
                        intent.putExtra(AUTH_TOKEN_EXTRA, authToken)
                        updateUI(State.INIT)
                        startActivity(intent)
                    },
                    {
                        updateUI(State.INIT)
                        val httpException = it as? HttpException?
                        if (httpException != null) {
                            Snackbar.make(
                                binding.rootView,
                                "Error: " + httpException.localizedMessage + " StatusCode: " + httpException.code(),
                                Snackbar.LENGTH_LONG
                            ).show()
                        } else {
                            Snackbar.make(
                                binding.rootView,
                                "Error: " + it.localizedMessage,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                )
                .let { compositeDisposable.add(it) }
        }

        binding.videoCallButton
            .setOnClickListener {
                val callRequest = CallRequest(
                    languageFromId = 149,
                    languageToId = 42,
                    serviceTypeId = 2,
                    genderId = null,
                    data = listOf(
                        AdditionalField(
                            key = "MyInternalId",
                            value = "1984"
                        ),
                        AdditionalField(
                            key = "CustomCallField",
                            value = "CustomCallFieldValue"
                        )
                    ),
                    isVideo = true
                )
                boostlingoSdk.validateCallReq(callRequest)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { updateUI(State.LOADING) }
                    .subscribe(
                        {
                            val intent = Intent(this, VideoActivity::class.java)
                            intent.putExtra(CALL_REQUEST_EXTRA, callRequest)
                            intent.putExtra(REGION_EXTRA, region)
                            intent.putExtra(AUTH_TOKEN_EXTRA, authToken)
                            updateUI(State.INIT)
                            startActivity(intent)
                        },
                        {
                            updateUI(State.INIT)
                            val httpException = it as? HttpException?
                            if (httpException != null) {
                                Snackbar.make(
                                    binding.rootView,
                                    "Error: " + httpException.localizedMessage + " StatusCode: " + httpException.code(),
                                    Snackbar.LENGTH_LONG
                                ).show()
                            } else {
                                Snackbar.make(
                                    binding.rootView,
                                    "Error: " + it.localizedMessage,
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                    .let { compositeDisposable.add(it) }
            }

        binding.callDetailsButton
            .setOnClickListener {
                boostlingoSdk.getCallDetails(214)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { updateUI(State.LOADING) }
                    .subscribe(
                        {
                            Log.d(null, it.duration.toString())
                        },
                        {
                            updateUI(State.INIT)
                            val httpException = it as? HttpException?
                            if (httpException != null) {
                                Snackbar.make(
                                    binding.rootView,
                                    "Error: " + httpException.localizedMessage + " StatusCode: " + httpException.code(),
                                    Snackbar.LENGTH_LONG
                                ).show()
                            } else {
                                Snackbar.make(
                                    binding.rootView,
                                    "Error: " + it.localizedMessage,
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                    .let { compositeDisposable.add(it) }
            }
    }

    private fun updateUI(state: State) {
        when (state) {
            State.NOT_INIT -> {
                binding.initializeButton.isEnabled = true
                binding.voiceCallButton.isEnabled = false
                binding.videoCallButton.isEnabled = false
                binding.callDetailsButton.isEnabled = false
            }
            State.INIT -> {
                binding.initializeButton.isEnabled = false
                binding.voiceCallButton.isEnabled = true
                binding.videoCallButton.isEnabled = true
                binding.callDetailsButton.isEnabled = true
            }
            State.LOADING -> {
                binding.initializeButton.isEnabled = false
                binding.voiceCallButton.isEnabled = false
                binding.videoCallButton.isEnabled = false
                binding.callDetailsButton.isEnabled = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        compositeDisposable.dispose()
    }

    private enum class State {
        NOT_INIT,
        INIT,
        LOADING
    }

    internal companion object {

        const val CALL_REQUEST_EXTRA = "CALL_REQUEST_EXTRA"
        const val REGION_EXTRA = "REGION_EXTRA"
        const val AUTH_TOKEN_EXTRA = "AUTH_TOKEN_EXTRA"
    }
}