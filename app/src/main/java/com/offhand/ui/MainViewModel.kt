package com.offhand.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offhand.AppContainer
import com.offhand.action.ConfirmResult
import com.offhand.action.StoredSlots
import com.offhand.data.ActionEntity
import com.offhand.data.ContactEntity
import com.offhand.debug.DebugCommand
import com.offhand.parse.ActionDraft
import com.offhand.parse.ActionType
import com.offhand.parse.Confidence
import com.offhand.parse.Contact
import com.offhand.parse.ContactResolver
import com.offhand.parse.FieldStatus
import com.offhand.parse.Slots
import com.offhand.parse.ValidatedDraft
import com.offhand.parse.ValidatedField
import com.offhand.parse.Validator
import com.offhand.audio.AsrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

enum class Tab { HOME, OUTBOX }

enum class DraftFieldKey { RECIPIENT, SUBJECT, BODY, DATETIME, PATH_HINT }

data class DraftField(
    val text: String,
    val status: FieldStatus,
    val hint: String? = null,
)

data class DraftUi(
    val actionId: String,
    val type: ActionType,
    val transcript: String,
    val recipient: DraftField,
    val resolvedEmail: String?,
    val subject: DraftField,
    val body: DraftField,
    val datetime: DraftField,
    val pathHint: DraftField,
    val candidates: List<Contact>,
    val confidence: Confidence,
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    val online: StateFlow<Boolean> = container.connectivityObserver.online

    val actions: StateFlow<List<ActionEntity>> = container.actionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contacts: StateFlow<List<ContactEntity>> = container.contactDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _tab = MutableStateFlow(Tab.HOME)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _asrReady = MutableStateFlow(false)
    val asrReady: StateFlow<Boolean> = _asrReady.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _draft = MutableStateFlow<DraftUi?>(null)
    val draft: StateFlow<DraftUi?> = _draft.asStateFlow()

    private val _ocrOpen = MutableStateFlow(false)
    val ocrOpen: StateFlow<Boolean> = _ocrOpen.asStateFlow()

    /** One-shot user-facing message; UI consumes it. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            container.debugBus.collect { cmd ->
                when (cmd) {
                    is DebugCommand.InjectTranscript -> handleFinalTranscript(cmd.text)
                    is DebugCommand.InjectWav -> {
                        val text = withContext(Dispatchers.IO) {
                            container.asrEngine.transcribeWavFile(cmd.path)
                        }
                        if (text.isNullOrBlank()) {
                            _message.value = "WAV transcription produced nothing"
                        } else {
                            handleFinalTranscript(text)
                        }
                    }

                    is DebugCommand.InjectOcrImage -> onOcrPhoto(cmd.path)
                }
            }
        }
        viewModelScope.launch {
            // The engine initialises in the background at app start.
            repeat(240) {
                if (container.asrEngine.state is AsrEngine.EngineState.Ready) {
                    _asrReady.value = true
                    return@launch
                }
                delay(500)
            }
        }
    }

    fun selectTab(tab: Tab) {
        _tab.value = tab
    }

    // ---- push-to-talk --------------------------------------------------

    fun startPtt() {
        when (container.asrEngine.state) {
            is AsrEngine.EngineState.Ready -> Unit
            is AsrEngine.EngineState.Error ->
                return run { _message.value = (container.asrEngine.state as AsrEngine.EngineState.Error).message }
            AsrEngine.EngineState.NotReady ->
                return run { _message.value = "Speech engine is still warming up" }
        }
        _partial.value = ""
        val started = container.asrEngine.startListening(
            onPartial = { p -> _partial.value = p },
            onFinal = { text ->
                viewModelScope.launch {
                    _listening.value = false
                    if (text.isBlank()) {
                        _partial.value = ""
                        _message.value = "Didn't catch that — try again"
                    } else {
                        handleFinalTranscript(text)
                    }
                }
            },
        )
        _listening.value = started
        if (!started) _message.value = "Microphone unavailable"
    }

    fun stopPtt() {
        container.asrEngine.stopListening()
    }

    // ---- transcript -> validated draft ---------------------------------

    private suspend fun handleFinalTranscript(transcript: String) {
        _partial.value = ""
        _listening.value = false
        val validated = validate(transcript)
        val entity = container.actionRepository.createDraft(
            validated.type, StoredSlots.from(validated), transcript,
        )
        _draft.value = validated.toUi(entity.id)
    }

    private suspend fun validate(transcript: String): ValidatedDraft {
        val contactList = container.contactDao.getAll().map { Contact(it.name, it.email) }
        val validator = Validator(ContactResolver(contactList), container.dateResolver)
        return validator.validate(container.parser.parse(transcript))
    }

    // ---- draft editing -------------------------------------------------

    fun updateField(key: DraftFieldKey, text: String) {
        val d = _draft.value ?: return
        _draft.value = when (key) {
            DraftFieldKey.RECIPIENT ->
                d.copy(recipient = d.recipient.copy(text = text), resolvedEmail = null)
            DraftFieldKey.SUBJECT -> d.copy(subject = d.subject.copy(text = text))
            DraftFieldKey.BODY -> d.copy(body = d.body.copy(text = text))
            DraftFieldKey.DATETIME -> d.copy(datetime = d.datetime.copy(text = text))
            DraftFieldKey.PATH_HINT -> d.copy(pathHint = d.pathHint.copy(text = text))
        }
    }

    fun pickCandidate(contact: Contact) {
        val d = _draft.value ?: return
        _draft.value = d.copy(
            recipient = DraftField(contact.name, FieldStatus.OK),
            resolvedEmail = contact.email,
            candidates = emptyList(),
        )
    }

    fun confirmDraft() {
        val d = _draft.value ?: return
        viewModelScope.launch {
            val contactList = container.contactDao.getAll().map { Contact(it.name, it.email) }
            val validator = Validator(ContactResolver(contactList), container.dateResolver)

            val datetimeIso = resolveDatetimeText(d.datetime.text)
            val raw = ActionDraft(
                type = d.type,
                slots = Slots(
                    recipient = d.recipient.text.trim().ifBlank { null },
                    subject = d.subject.text.trim().ifBlank { null },
                    body = d.body.text.trim().ifBlank { null },
                    datetime = datetimeIso,
                    pathHint = d.pathHint.text.trim().ifBlank { null },
                ),
                confidence = Confidence.HIGH,
                transcript = d.transcript,
            )
            var validated = validator.validate(raw)

            // The user's edited datetime wins over transcript re-resolution.
            val datetimeRequired =
                d.type == ActionType.CREATE_EVENT || d.type == ActionType.SET_REMINDER
            val datetimeField = when {
                datetimeIso != null -> ValidatedField(datetimeIso, FieldStatus.OK)
                datetimeRequired -> ValidatedField(null, FieldStatus.NEEDS_INPUT, "When?")
                else -> ValidatedField(null, FieldStatus.OK)
            }
            validated = validated.copy(datetime = datetimeField)

            // A candidate the user tapped stays resolved.
            if (validated.recipient.status == FieldStatus.NEEDS_INPUT && d.resolvedEmail != null) {
                validated = validated.copy(
                    recipient = ValidatedField(d.resolvedEmail, FieldStatus.OK),
                    recipientDisplay = d.recipient.text,
                    recipientCandidates = emptyList(),
                )
            }

            if (!validated.ready) {
                _draft.value = validated.toUi(d.actionId)
                _message.value = "Check the highlighted fields"
                return@launch
            }

            container.actionRepository.updateSlots(d.actionId, StoredSlots.from(validated))
            when (val result = container.coordinator.confirm(d.actionId)) {
                ConfirmResult.Executed -> {
                    _message.value = executedMessage(d.type)
                    _draft.value = null
                }
                ConfirmResult.Queued -> {
                    _message.value =
                        if (online.value) "Queued — sending shortly"
                        else "Queued — will send when connected"
                    _draft.value = null
                    _tab.value = Tab.OUTBOX
                }
                is ConfirmResult.Failed -> {
                    _message.value = "Couldn't finish: ${result.message}"
                    _draft.value = null
                    _tab.value = Tab.OUTBOX
                }
            }
        }
    }

    fun cancelDraft() {
        val d = _draft.value ?: return
        viewModelScope.launch {
            container.coordinator.cancel(d.actionId)
            _draft.value = null
        }
    }

    // ---- OCR (photo -> prefilled note draft) ---------------------------

    fun openOcr() {
        _ocrOpen.value = true
    }

    fun closeOcr() {
        _ocrOpen.value = false
    }

    fun onOcrPhoto(path: String) {
        viewModelScope.launch {
            val text = com.offhand.ocr.OcrEngine.recognizeFile(container.appContext, path)
            _ocrOpen.value = false
            if (text.isNullOrBlank()) {
                _message.value = "No text found in the photo"
                return@launch
            }
            val validated = Validator(
                ContactResolver(container.contactDao.getAll().map { Contact(it.name, it.email) }),
                container.dateResolver,
            ).validate(
                ActionDraft(
                    type = ActionType.CAPTURE_NOTE,
                    slots = Slots(body = text),
                    confidence = Confidence.HIGH,
                    transcript = "(captured from camera)",
                ),
            )
            val entity = container.actionRepository.createDraft(
                validated.type, StoredSlots.from(validated), validated.transcript,
            )
            _draft.value = validated.toUi(entity.id)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---- demo reset ----------------------------------------------------

    fun resetDemoData() {
        viewModelScope.launch {
            com.offhand.demo.DemoSeeder(container).reset()
            _draft.value = null
            _message.value = "Demo data reset"
        }
    }

    // ---- contacts ------------------------------------------------------

    fun addContact(name: String, email: String) {
        if (name.isBlank() || email.isBlank()) return
        viewModelScope.launch {
            container.contactDao.upsert(ContactEntity(name = name.trim(), email = email.trim()))
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch { container.contactDao.delete(contact) }
    }

    // ---- helpers -------------------------------------------------------

    private fun resolveDatetimeText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null
        return try {
            LocalDateTime.parse(trimmed).toString()
        } catch (e: Exception) {
            container.dateResolver.resolve(trimmed)?.dateTime?.toString()
        }
    }

    private fun executedMessage(type: ActionType): String = when (type) {
        ActionType.CREATE_EVENT -> "Event added to your calendar"
        ActionType.SET_REMINDER -> "Reminder set"
        ActionType.CAPTURE_NOTE -> "Note saved"
        else -> "Done"
    }

    private fun ValidatedDraft.toUi(actionId: String): DraftUi = DraftUi(
        actionId = actionId,
        type = type,
        transcript = transcript,
        recipient = DraftField(
            text = recipientDisplay ?: recipient.value.orEmpty(),
            status = recipient.status,
            hint = recipient.hint,
        ),
        resolvedEmail = if (recipient.status == FieldStatus.OK) recipient.value else null,
        subject = DraftField(subject.value.orEmpty(), subject.status, subject.hint),
        body = DraftField(body.value.orEmpty(), body.status, body.hint),
        datetime = DraftField(datetime.value.orEmpty(), datetime.status, datetime.hint),
        pathHint = DraftField(pathHint.value.orEmpty(), pathHint.status, pathHint.hint),
        candidates = recipientCandidates,
        confidence = confidence,
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(container) }
        }
    }
}
