package com.boostlingo.android.quickstart

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.boostlingo.android.AdditionalField
import com.boostlingo.android.BLLogLevel
import com.boostlingo.android.BoostlingoSDK
import com.boostlingo.android.BoostlingoSDK.Companion.baseURLs
import com.boostlingo.android.CallRequest
import com.boostlingo.android.Gender
import com.boostlingo.android.Language
import com.boostlingo.android.ServiceType
import com.boostlingo.android.data.dto.customForm.CustomFieldEntity
import com.boostlingo.android.domain.models.precall.CheckBoxCustomField
import com.boostlingo.android.domain.models.precall.CustomField
import com.boostlingo.android.domain.models.precall.EditTextCustomField
import com.boostlingo.android.domain.models.precall.FieldType
import com.boostlingo.android.domain.models.precall.ListMultipleCustomField
import com.boostlingo.android.domain.models.precall.ListSingleCustomField
import com.boostlingo.android.domain.models.precall.RadioButtonCustomField
import com.boostlingo.android.quickstart.ui.theme.BoostlingoTheme
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import retrofit2.HttpException

class MainActivity : ComponentActivity() {

    private enum class State { NOT_INIT, INIT, LOADING }

    private val compositeDisposable = CompositeDisposable()

    // --- Compose-observable UI state, owned by the Activity controller ---
    private var region by mutableStateOf("")
    private var token by mutableStateOf("")
    private var state by mutableStateOf(State.NOT_INIT)
    private var companyId by mutableStateOf("Company Id")
    private var languages by mutableStateOf<List<Language>>(emptyList())
    private var selectedLanguageFrom by mutableStateOf<Language?>(null)
    private var selectedLanguageTo by mutableStateOf<Language?>(null)
    private var serviceTypes by mutableStateOf<List<ServiceType>>(emptyList())
    private var selectedServiceType by mutableStateOf<ServiceType?>(null)
    private var genders by mutableStateOf<List<Gender>>(emptyList())
    private var selectedGender by mutableStateOf<Gender?>(null)
    private var callDetailsId by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)

    private val boostlingoSdk: BoostlingoSDK by lazy {
        BoostlingoSDK(token, this, BLLogLevel.DEBUG, region)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BoostlingoTheme {
                MainScreen(
                    regions = BoostlingoSDK.getRegions(),
                    region = region,
                    onRegionChange = { region = it },
                    token = token,
                    onTokenChange = { token = it },
                    initEnabled = state == State.NOT_INIT,
                    callsEnabled = state == State.INIT,
                    companyId = companyId,
                    languages = languages,
                    selectedLanguageFrom = selectedLanguageFrom,
                    onLanguageFromSelected = { selectedLanguageFrom = it },
                    selectedLanguageTo = selectedLanguageTo,
                    onLanguageToSelected = { selectedLanguageTo = it },
                    serviceTypes = serviceTypes,
                    selectedServiceType = selectedServiceType,
                    onServiceTypeSelected = { selectedServiceType = it },
                    genders = genders,
                    selectedGender = selectedGender,
                    onGenderSelected = { selectedGender = it },
                    callDetailsId = callDetailsId,
                    onCallDetailsIdChange = { callDetailsId = it },
                    message = message,
                    onMessageShown = { message = null },
                    onInitialize = ::initialize,
                    onVoiceCall = ::startVoiceCall,
                    onVideoCall = ::startVideoCall,
                    onAiCall = ::startAiCall,
                    onCallDetails = ::callDetails,
                )
            }
        }
    }

    private fun initialize() {
        if (!baseURLs.containsKey(region) || token.isBlank()) {
            message = "Error: invalid region or token"
            return
        }
        boostlingoSdk.initialize()
            .subscribeOn(Schedulers.io())
            .andThen(
                boostlingoSdk.getProfile()
                    .subscribeOn(Schedulers.io())
                    .doOnSuccess { companyId = it.companyAccountId.toString() }
            )
            .flatMap {
                boostlingoSdk.getCallDictionaries().subscribeOn(Schedulers.io())
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { state = State.LOADING }
            .subscribe(
                { callDictionaries ->
                    state = State.INIT
                    languages = callDictionaries.languages
                    selectedLanguageFrom = callDictionaries.languages.firstOrNull { it.englishName == "English" }
                        ?: callDictionaries.languages.firstOrNull()
                    selectedLanguageTo = callDictionaries.languages.firstOrNull { it.englishName == "Spanish" }
                        ?: callDictionaries.languages.firstOrNull()
                    serviceTypes = callDictionaries.serviceTypes
                    selectedServiceType = callDictionaries.serviceTypes.firstOrNull { it.name == "Personal" }
                        ?: callDictionaries.serviceTypes.firstOrNull()
                    genders = callDictionaries.genders
                    selectedGender = callDictionaries.genders.firstOrNull()
                },
                {
                    state = State.NOT_INIT
                    message = errorMessage(it)
                }
            )
            .let { compositeDisposable.add(it) }
    }

    private fun startVoiceCall() {
        boostlingoSdk.getPreCallForm()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { state = State.LOADING }
            .subscribe(
                { customForm ->
                    val fieldData = customForm.fields.map {
                        CustomFieldEntity(fieldId = it.fieldId, value = provideDefaultValue(it))
                    }
                    validateAndStart(
                        CallRequest(
                            languageFromId = selectedLanguageFrom?.id ?: 45,
                            languageToId = selectedLanguageTo?.id ?: 42,
                            serviceTypeId = selectedServiceType?.id ?: 2,
                            genderId = selectedGender?.id?.toInt(),
                            data = defaultData(),
                            isVideo = false,
                            fieldData = fieldData,
                        ),
                        VoiceActivity::class.java,
                    )
                },
                {
                    state = State.INIT
                    message = errorMessage(it)
                }
            )
            .let { compositeDisposable.add(it) }
    }

    private fun startVideoCall() {
        boostlingoSdk.getPreCallForm()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { state = State.LOADING }
            .subscribe(
                { customForm ->
                    val fieldData = customForm.fields.map {
                        CustomFieldEntity(fieldId = it.fieldId, value = provideDefaultValue(it))
                    }
                    validateAndStart(
                        CallRequest(
                            languageFromId = selectedLanguageFrom?.id ?: 149,
                            languageToId = selectedLanguageTo?.id ?: 42,
                            serviceTypeId = selectedServiceType?.id ?: 2,
                            genderId = selectedGender?.id?.toInt(),
                            data = defaultData(),
                            isVideo = true,
                            fieldData = fieldData,
                        ),
                        VideoActivity::class.java,
                    )
                },
                {
                    state = State.INIT
                    message = errorMessage(it)
                }
            )
            .let { compositeDisposable.add(it) }
    }

    private fun startAiCall() {
        boostlingoSdk.isAIInterpreterAvailable()
            .subscribeOn(Schedulers.io())
            .flatMap { available ->
                if (!available) {
                    throw IllegalStateException("AI Interpreter is not available for this account")
                }
                Single.zip(
                    boostlingoSdk.getAIInterpreterLanguages().subscribeOn(Schedulers.io()),
                    boostlingoSdk.getAIInterpreterServiceTypes().subscribeOn(Schedulers.io()),
                    { languages, serviceTypes -> Pair(languages, serviceTypes) }
                )
            }
            .map { (aiLanguages, aiServiceTypes) ->
                // The dropdowns are populated from getCallDictionaries(); AI Interpreter
                // exposes its own language/service lists, so a selection valid in the
                // dropdown may not exist for AI. Distinguish "AI offers nothing" from
                // "your selection isn't in the AI list" so the error is actionable.
                val from = aiLanguages.requireAiSelection(selectedLanguageFrom?.id, "language") { it.id }
                val to = aiLanguages.requireAiSelection(selectedLanguageTo?.id, "language") { it.id }
                val service = aiServiceTypes.requireAiSelection(selectedServiceType?.id, "service type") { it.id }
                CallRequest(
                    languageFromId = from.id,
                    languageToId = to.id,
                    serviceTypeId = service.id,
                    genderId = null,
                    isAIInterpretation = true,
                )
            }
            .flatMap { request ->
                boostlingoSdk.validateCallReq(request)
                    .subscribeOn(Schedulers.io())
                    .andThen(Single.just(request))
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { state = State.LOADING }
            .subscribe(
                { request -> launchCall(request, AIActivity::class.java) },
                {
                    state = State.INIT
                    message = errorMessage(it)
                }
            )
            .let { compositeDisposable.add(it) }
    }

    private fun callDetails() {
        val callId = callDetailsId.trim().toLongOrNull()
        if (callId == null) {
            message = "Error: enter a valid numeric call id"
            return
        }
        boostlingoSdk.getCallDetails(callId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { state = State.LOADING }
            .subscribe(
                { state = State.INIT; message = "Call duration: ${it.duration}" },
                { state = State.INIT; message = errorMessage(it) }
            )
            .let { compositeDisposable.add(it) }
    }

    private fun validateAndStart(callRequest: CallRequest, target: Class<*>) {
        boostlingoSdk.validateCallReq(callRequest)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { state = State.LOADING }
            .subscribe(
                { launchCall(callRequest, target) },
                { state = State.INIT; message = errorMessage(it) }
            )
            .let { compositeDisposable.add(it) }
    }

    private fun launchCall(callRequest: CallRequest, target: Class<*>) {
        state = State.INIT
        val intent = Intent(this, target).apply {
            putExtra(CALL_REQUEST_EXTRA, callRequest)
            putExtra(REGION_EXTRA, region)
            putExtra(AUTH_TOKEN_EXTRA, token)
        }
        startActivity(intent)
    }

    private fun defaultData() = listOf(
        AdditionalField(key = "MyInternalId", value = "1984"),
        AdditionalField(key = "CustomCallField", value = "CustomCallFieldValue"),
    )

    private fun errorMessage(t: Throwable): String {
        val httpException = t as? HttpException
        return if (httpException != null) {
            "Error: ${httpException.localizedMessage} StatusCode: ${httpException.code()}"
        } else {
            "Error: ${t.localizedMessage}"
        }
    }

    private fun provideDefaultValue(field: CustomField): Any {
        return when (field) {
            is EditTextCustomField ->
                if (FieldType.getFieldType(field.fieldTypeId) == FieldType.EDIT_TEXT_SINGLE_LINE) {
                    "Single line default text value"
                } else {
                    "Multiline\ndefault text value"
                }
            is ListMultipleCustomField -> field.options.firstOrNull()?.let { listOf(it.id) } ?: listOf<Long>()
            is CheckBoxCustomField -> field.options.firstOrNull()?.let { listOf(it.id) } ?: listOf<Long>()
            is ListSingleCustomField -> field.options.firstOrNull()?.id ?: -1L
            is RadioButtonCustomField -> field.options.firstOrNull()?.id ?: -1L
            else -> true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.dispose()
    }

    internal companion object {
        const val CALL_REQUEST_EXTRA = "CALL_REQUEST_EXTRA"
        const val REGION_EXTRA = "REGION_EXTRA"
        const val AUTH_TOKEN_EXTRA = "AUTH_TOKEN_EXTRA"
    }
}

/**
 * Resolves the dropdown-selected [selectedId] against an AI Interpreter list, throwing an
 * actionable error: one message when AI offers no options of this kind, a different one when
 * AI has options but the current selection isn't among them. [label] is the human noun
 * ("language", "service type").
 */
private fun <T> List<T>.requireAiSelection(selectedId: Int?, label: String, id: (T) -> Int): T {
    if (isEmpty()) {
        throw IllegalStateException("No AI Interpreter ${label}s available")
    }
    return firstOrNull { id(it) == selectedId }
        ?: throw IllegalStateException("Selected $label is not available for AI Interpreter")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    regions: List<String>,
    region: String,
    onRegionChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    initEnabled: Boolean,
    callsEnabled: Boolean,
    companyId: String,
    languages: List<Language>,
    selectedLanguageFrom: Language?,
    onLanguageFromSelected: (Language) -> Unit,
    selectedLanguageTo: Language?,
    onLanguageToSelected: (Language) -> Unit,
    serviceTypes: List<ServiceType>,
    selectedServiceType: ServiceType?,
    onServiceTypeSelected: (ServiceType) -> Unit,
    genders: List<Gender>,
    selectedGender: Gender?,
    onGenderSelected: (Gender) -> Unit,
    callDetailsId: String,
    onCallDetailsIdChange: (String) -> Unit,
    message: String?,
    onMessageShown: () -> Unit,
    onInitialize: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onAiCall: () -> Unit,
    onCallDetails: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Boostlingo SDK QuickStart") }) },
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
            LabeledDropdown(
                label = "Region",
                options = regions,
                selected = region.takeIf { it.isNotBlank() },
                enabled = initEnabled,
                optionLabel = { it },
                onSelected = onRegionChange,
            )
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                label = { Text("Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onInitialize, enabled = initEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Initialize")
            }
            LabeledDropdown(
                label = "Language From",
                options = languages,
                selected = selectedLanguageFrom,
                enabled = callsEnabled,
                optionLabel = { "${it.englishName} ${it.id}" },
                onSelected = onLanguageFromSelected,
            )
            LabeledDropdown(
                label = "Language To",
                options = languages,
                selected = selectedLanguageTo,
                enabled = callsEnabled,
                optionLabel = { "${it.englishName} ${it.id}" },
                onSelected = onLanguageToSelected,
            )
            LabeledDropdown(
                label = "Service Type",
                options = serviceTypes,
                selected = selectedServiceType,
                enabled = callsEnabled,
                optionLabel = { "${it.name} ${it.id}" },
                onSelected = onServiceTypeSelected,
            )
            LabeledDropdown(
                label = "Gender",
                options = genders,
                selected = selectedGender,
                enabled = callsEnabled,
                optionLabel = { "${it.name} ${it.id}" },
                onSelected = onGenderSelected,
            )
            Text(companyId, color = MaterialTheme.colorScheme.primary)
            Button(onClick = onVoiceCall, enabled = callsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Voice Call")
            }
            Button(onClick = onVideoCall, enabled = callsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Video Call")
            }
            Button(onClick = onAiCall, enabled = callsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("AI Interpreter Call")
            }
            OutlinedTextField(
                value = callDetailsId,
                onValueChange = onCallDetailsIdChange,
                label = { Text("Call Id") },
                singleLine = true,
                enabled = callsEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onCallDetails, enabled = callsEnabled, modifier = Modifier.fillMaxWidth()) {
                Text("Call Details")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LabeledDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    enabled: Boolean,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Main — initialized")
@Composable
private fun MainScreenPreview() {
    BoostlingoTheme {
        MainScreen(
            regions = listOf("us", "eu", "uk"),
            region = "us",
            onRegionChange = {},
            token = "sample-token",
            onTokenChange = {},
            initEnabled = false,
            callsEnabled = true,
            companyId = "Company Id 1984",
            languages = listOf(SAMPLE_LANGUAGE),
            selectedLanguageFrom = SAMPLE_LANGUAGE,
            onLanguageFromSelected = {},
            selectedLanguageTo = SAMPLE_LANGUAGE,
            onLanguageToSelected = {},
            serviceTypes = listOf(SAMPLE_SERVICE_TYPE),
            selectedServiceType = SAMPLE_SERVICE_TYPE,
            onServiceTypeSelected = {},
            genders = listOf(SAMPLE_GENDER),
            selectedGender = SAMPLE_GENDER,
            onGenderSelected = {},
            callDetailsId = "1111",
            onCallDetailsIdChange = {},
            message = null,
            onMessageShown = {},
            onInitialize = {},
            onVoiceCall = {},
            onVideoCall = {},
            onAiCall = {},
            onCallDetails = {},
        )
    }
}

private val SAMPLE_LANGUAGE = Language(
    id = 42,
    code = "am",
    name = "Amharic",
    englishName = "Amharic",
    nativeName = "አማርኛ",
    localizedName = "Amharic",
    enabled = true,
    isSignLanguage = false,
    isVideoBackstopStaffed = false,
    vriPolicyOrder = null,
    opiPolicyOrder = 1,
)

private val SAMPLE_SERVICE_TYPE = ServiceType(id = 2, name = "Medical", enable = true)

private val SAMPLE_GENDER = Gender(id = 9, name = "Female")
