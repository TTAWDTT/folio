---- MODULE FolioWindow ----
\* Folio tool window, as the user sees it.
\* One activity at a time: indexing and a model call do not overlap.
\* The editor and VCS actions are not in this model. They can still call the panel
\* when the tool-window buttons are disabled.

VARIABLES
  key,        \* "none" | "typed" | "stored"
  index,      \* "empty" | "indexing" | "ready"
  call,       \* "idle" | "running" | "answered" | "failed"
  secret,     \* TRUE when PasswordSafe holds a key
  spinning,   \* TRUE while the footer spinner is showing
  llmButtons, \* TRUE when Ask, Preview test, and Draft commit are enabled
  preview,    \* "none" | "test"  (the Test transcript)
  written,    \* "none" | "test"  (the file that Write created)
  captured,   \* the preview text copied at the moment of Write
  purpose,    \* "ask" | "preview"  (what the running call is)
  opened      \* TRUE after the first action, so restore runs only at open

vars == <<key, index, call, secret, spinning, llmButtons, preview, written, captured, purpose, opened>>

Keys == {"none", "typed", "stored"}
Indexes == {"empty", "indexing", "ready"}
Calls == {"idle", "running", "answered", "failed"}
Texts == {"none", "test"}
Purposes == {"ask", "preview"}

TypeOK ==
  /\ key \in Keys
  /\ index \in Indexes
  /\ call \in Calls
  /\ secret \in BOOLEAN
  /\ spinning \in BOOLEAN
  /\ llmButtons \in BOOLEAN
  /\ preview \in Texts
  /\ written \in Texts
  /\ captured \in Texts
  /\ purpose \in Purposes
  /\ opened \in BOOLEAN

\* Ask, Preview test, and Draft commit are enabled exactly when a key is typed or stored.
LlmButtonsNeedKey == llmButtons <=> (key # "none")

\* The spinner is on while a model call or indexing is in progress, and off otherwise.
SpinnerMatchesWork == spinning <=> (call = "running" \/ index = "indexing")

\* A stored badge means a key is in PasswordSafe. Empty Save does not change `secret`.
EmptySaveKeepsKey == secret <=> (key = "stored")

\* A written file is a copy of the preview text from the moment Write ran.
WriteMatchesPreview == written = captured

\* `purpose` is "preview" only while that call is running.
PurposeOnlyWhileRunning == purpose = "ask" \/ call = "running"

idle == call # "running" /\ index # "indexing"

Init ==
  /\ key = "none"
  /\ index = "empty"
  /\ call = "idle"
  /\ secret = FALSE
  /\ spinning = FALSE
  /\ llmButtons = FALSE
  /\ preview = "none"
  /\ written = "none"
  /\ captured = "none"
  /\ purpose = "ask"
  /\ opened = FALSE

YouTypeKey ==
  /\ key = "none"
  /\ key' = "typed"
  /\ llmButtons' = TRUE
  /\ opened' = TRUE
  /\ UNCHANGED <<index, call, secret, spinning, preview, written, captured, purpose>>

YouEraseTypedKey ==
  /\ key = "typed"
  /\ key' = "none"
  /\ llmButtons' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<index, call, secret, spinning, preview, written, captured, purpose>>

YouSaveKey ==
  /\ key = "typed"
  /\ key' = "stored"
  /\ secret' = TRUE
  /\ llmButtons' = TRUE
  /\ opened' = TRUE
  /\ UNCHANGED <<index, call, spinning, preview, written, captured, purpose>>

\* The field is empty. The stored key is left as it is.
YouSaveEmpty ==
  /\ key \in {"none", "stored"}
  /\ secret' = secret
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, call, spinning, llmButtons, preview, written, captured, purpose>>

YouClearKey ==
  /\ key = "stored"
  /\ key' = "none"
  /\ secret' = FALSE
  /\ llmButtons' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<index, call, spinning, preview, written, captured, purpose>>

YouIndexFiles ==
  /\ idle
  /\ index \in {"empty", "ready"}
  /\ index' = "indexing"
  /\ spinning' = TRUE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, call, secret, llmButtons, preview, written, captured, purpose>>

YouRemoveFile ==
  /\ idle
  /\ index = "ready"
  /\ index' = "indexing"
  /\ spinning' = TRUE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, call, secret, llmButtons, preview, written, captured, purpose>>

IndexingFinishes ==
  /\ index = "indexing"
  /\ index' = "ready"
  /\ spinning' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, call, secret, llmButtons, preview, written, captured, purpose>>

IndexingFindsNothing ==
  /\ index = "indexing"
  /\ index' = "empty"
  /\ spinning' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, call, secret, llmButtons, preview, written, captured, purpose>>

StartCall(nextPurpose) ==
  /\ key # "none"
  /\ idle
  /\ call' = "running"
  /\ purpose' = nextPurpose
  /\ spinning' = TRUE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, secret, llmButtons, preview, written, captured>>

YouAsk == StartCall("ask")
YouDraft == StartCall("ask")
YouPreview == StartCall("preview")

TheCallAnswers ==
  /\ call = "running"
  /\ call' = "answered"
  /\ preview' = IF purpose = "preview" THEN "test" ELSE preview
  /\ purpose' = "ask"
  /\ spinning' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, secret, llmButtons, written, captured>>

TheCallFails ==
  /\ call = "running"
  /\ call' = "failed"
  /\ purpose' = "ask"
  /\ spinning' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, secret, llmButtons, preview, written, captured>>

YouCancelCall ==
  /\ call = "running"
  /\ call' = "idle"
  /\ purpose' = "ask"
  /\ spinning' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, secret, llmButtons, preview, written, captured>>

\* Write test file copies the current preview. It does not use the spinner.
YouWrite ==
  /\ preview = "test"
  /\ written' = preview
  /\ captured' = preview
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, call, secret, spinning, llmButtons, preview, purpose>>

atOpen ==
  /\ opened = FALSE
  /\ key = "none"
  /\ index = "empty"
  /\ call = "idle"

RestoreKey ==
  /\ atOpen
  /\ key' = "stored"
  /\ secret' = TRUE
  /\ llmButtons' = TRUE
  /\ opened' = TRUE
  /\ UNCHANGED <<index, call, spinning, preview, written, captured, purpose>>

RestoreFiles ==
  /\ atOpen
  /\ index' = "ready"
  /\ opened' = TRUE
  /\ UNCHANGED <<key, call, secret, spinning, llmButtons, preview, written, captured, purpose>>

RestoreBoth ==
  /\ atOpen
  /\ key' = "stored"
  /\ secret' = TRUE
  /\ llmButtons' = TRUE
  /\ index' = "ready"
  /\ opened' = TRUE
  /\ UNCHANGED <<call, spinning, preview, written, captured, purpose>>

Next ==
  \/ YouTypeKey
  \/ YouEraseTypedKey
  \/ YouSaveKey
  \/ YouSaveEmpty
  \/ YouClearKey
  \/ YouIndexFiles
  \/ YouRemoveFile
  \/ IndexingFinishes
  \/ IndexingFindsNothing
  \/ YouAsk
  \/ YouPreview
  \/ YouDraft
  \/ TheCallAnswers
  \/ TheCallFails
  \/ YouCancelCall
  \/ YouWrite
  \/ RestoreKey
  \/ RestoreFiles
  \/ RestoreBoth

Spec == Init /\ [][Next]_vars

====
