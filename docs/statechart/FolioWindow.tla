---- MODULE FolioWindow ----
\* Folio tool window, as the user sees it.
\* One activity at a time: indexing and a model call do not overlap.
\* The editor and VCS actions are not in this model. They can still call the panel
\* when the tool-window buttons are disabled.
\* A key is not cleared while a call is running. The window does allow that; the
\* in-flight call still finishes. Keeping the key lets CallNeedsKey mean
\* "this call was started with a key" and fail when StartCall loses its guard.

VARIABLES
  key,        \* "none" | "typed" | "stored"
  index,      \* "empty" | "indexing" | "ready"
  call,       \* "idle" | "running" | "answered" | "failed"
  secret,     \* TRUE when PasswordSafe holds a key
  spinning,   \* TRUE while the footer spinner is showing
  llmButtons, \* TRUE when Ask, Preview test, and Draft commit are enabled
  preview,    \* "empty" | "test" | "error"  (what the Test transcript shows)
  previewOk,  \* TRUE only after the last preview call returned a test
  written,    \* "empty" | "test" | "error"  (the file Write last copied)
  purpose,    \* "ask" | "preview"  (ask covers Ask and Draft; neither writes the transcript)
  opened      \* TRUE after the first action, so restore runs only at open

vars == <<key, index, call, secret, spinning, llmButtons, preview, previewOk, written, purpose, opened>>

Keys == {"none", "typed", "stored"}
Indexes == {"empty", "indexing", "ready"}
Calls == {"idle", "running", "answered", "failed"}
Texts == {"empty", "test", "error"}
Purposes == {"ask", "preview"}

TypeOK ==
  /\ key \in Keys
  /\ index \in Indexes
  /\ call \in Calls
  /\ secret \in BOOLEAN
  /\ spinning \in BOOLEAN
  /\ llmButtons \in BOOLEAN
  /\ preview \in Texts
  /\ previewOk \in BOOLEAN
  /\ written \in Texts
  /\ purpose \in Purposes
  /\ opened \in BOOLEAN

\* Ask, Preview test, and Draft commit are enabled exactly when a key is typed or stored.
LlmButtonsNeedKey == llmButtons <=> (key # "none")

\* A running call was started with a typed or stored key.
CallNeedsKey == call = "running" => key # "none"

\* The spinner is on while a model call or indexing is in progress, and off otherwise.
SpinnerMatchesWork == spinning <=> (call = "running" \/ index = "indexing")

\* A stored badge means a key is in PasswordSafe. Empty Save does not change `secret`.
EmptySaveKeepsKey == secret <=> (key = "stored")

\* Write copies the transcript. It may run only after a preview succeeded, so the
\* file is never the error text a failed preview left behind.
WriteMatchesPreview == written \in {"empty", "test"}

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
  /\ preview = "empty"
  /\ previewOk = FALSE
  /\ written = "empty"
  /\ purpose = "ask"
  /\ opened = FALSE

YouTypeKey ==
  /\ key = "none"
  /\ key' = "typed"
  /\ llmButtons' = TRUE
  /\ opened' = TRUE
  /\ UNCHANGED <<index, call, secret, spinning, preview, previewOk, written, purpose>>

YouEraseTypedKey ==
  /\ call # "running"
  /\ key = "typed"
  /\ key' = "none"
  /\ llmButtons' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<index, call, secret, spinning, preview, previewOk, written, purpose>>

YouSaveKey ==
  /\ key = "typed"
  /\ key' = "stored"
  /\ secret' = TRUE
  /\ llmButtons' = TRUE
  /\ opened' = TRUE
  /\ UNCHANGED <<index, call, spinning, preview, previewOk, written, purpose>>

\* The field is empty. The stored key is left as it is.
YouSaveEmpty ==
  /\ key \in {"none", "stored"}
  /\ secret' = secret
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, call, spinning, llmButtons, preview, previewOk, written, purpose>>

YouClearKey ==
  /\ call # "running"
  /\ key = "stored"
  /\ key' = "none"
  /\ secret' = FALSE
  /\ llmButtons' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<index, call, spinning, preview, previewOk, written, purpose>>

YouIndexFiles ==
  /\ idle
  /\ index \in {"empty", "ready"}
  /\ index' = "indexing"
  /\ spinning' = TRUE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, call, secret, llmButtons, preview, previewOk, written, purpose>>

YouRemoveFile ==
  /\ idle
  /\ index = "ready"
  /\ index' = "indexing"
  /\ spinning' = TRUE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, call, secret, llmButtons, preview, previewOk, written, purpose>>

IndexingFinishes ==
  /\ index = "indexing"
  /\ index' = "ready"
  /\ spinning' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, call, secret, llmButtons, preview, previewOk, written, purpose>>

IndexingFindsNothing ==
  /\ index = "indexing"
  /\ index' = "empty"
  /\ spinning' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, call, secret, llmButtons, preview, previewOk, written, purpose>>

\* Starting a preview clears the success flag immediately. The old transcript
\* stays until the call returns, and Write is off while the flag is off.
StartCall(nextPurpose) ==
  /\ key # "none"
  /\ idle
  /\ call' = "running"
  /\ purpose' = nextPurpose
  /\ spinning' = TRUE
  /\ previewOk' = IF nextPurpose = "preview" THEN FALSE ELSE previewOk
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, secret, llmButtons, preview, written>>

YouAsk == StartCall("ask")
YouDraft == StartCall("ask")
YouPreview == StartCall("preview")

TheCallAnswers ==
  /\ call = "running"
  /\ call' = "answered"
  /\ preview' = IF purpose = "preview" THEN "test" ELSE preview
  /\ previewOk' = IF purpose = "preview" THEN TRUE ELSE previewOk
  /\ purpose' = "ask"
  /\ spinning' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, secret, llmButtons, written>>

\* A failed preview leaves error text in the transcript and clears the success flag.
\* Ask and Draft do not touch the test transcript.
TheCallFails ==
  /\ call = "running"
  /\ call' = "failed"
  /\ preview' = IF purpose = "preview" THEN "error" ELSE preview
  /\ previewOk' = IF purpose = "preview" THEN FALSE ELSE previewOk
  /\ purpose' = "ask"
  /\ spinning' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, secret, llmButtons, written>>

YouCancelCall ==
  /\ call = "running"
  /\ call' = "idle"
  /\ purpose' = "ask"
  /\ spinning' = FALSE
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, secret, llmButtons, preview, previewOk, written>>

\* Write copies whatever the transcript currently shows. The only guard is that
\* the last preview succeeded. Removing it lets a failed preview be saved.
YouWrite ==
  /\ previewOk
  /\ written' = preview
  /\ opened' = TRUE
  /\ UNCHANGED <<key, index, call, secret, spinning, llmButtons, preview, previewOk, purpose>>

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
  /\ UNCHANGED <<index, call, spinning, preview, previewOk, written, purpose>>

RestoreFiles ==
  /\ atOpen
  /\ index' = "ready"
  /\ opened' = TRUE
  /\ UNCHANGED <<key, call, secret, spinning, llmButtons, preview, previewOk, written, purpose>>

RestoreBoth ==
  /\ atOpen
  /\ key' = "stored"
  /\ secret' = TRUE
  /\ llmButtons' = TRUE
  /\ index' = "ready"
  /\ opened' = TRUE
  /\ UNCHANGED <<call, spinning, preview, previewOk, written, purpose>>

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
