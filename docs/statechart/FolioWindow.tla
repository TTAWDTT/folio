---- MODULE FolioWindow ----
\* Folio tool window as a path the user walks.
\* Setup, then course files, then Ask, Test, and Commit as separate steps.
\* One activity at a time. Editor and VCS actions are not in this model.
\* A key is not cleared while a call is running. The window does allow that;
\* the in-flight call still finishes. Keeping the key lets CallNeedsKey fail
\* when the call guard is removed.
\* Indexing here starts only after a key is stored. The Index files button
\* does not need a key; that path is left out.
\* Starting Ask, Preview, or Draft clears the test transcript. The panel
\* keeps the last preview on screen until the next preview replaces it.
\* Cancelling a call is left out.

VARIABLES
  screen,     \* where the user is in the story
  key,        \* "none" | "typed" | "stored"
  index,      \* "empty" | "indexing" | "ready"
  secret,     \* TRUE when PasswordSafe holds a key
  spinning,   \* TRUE while the footer spinner is showing
  llmButtons, \* TRUE when Ask, Preview test, and Draft commit are enabled
  call,       \* "idle" | "running" | "answered" | "failed"
  purpose,    \* "idle" | "ask" | "preview" | "draft"
  preview,    \* "empty" | "test" | "error"  (the Test transcript)
  previewOk,  \* TRUE only after the last preview returned a test
  written     \* "empty" | "test" | "error"  (the file Write last copied)

vars == <<screen, key, index, secret, spinning, llmButtons, call, purpose, preview, previewOk, written>>

Screens == {
  "noKey", "typed", "saved",
  "filesNoKey", "typedFiles", "savedFiles",
  "indexing",
  "asking", "askCited", "askFailed",
  "generating", "previewShown", "previewFailed", "writtenFile",
  "drafting", "commitPlaced", "commitFailed"
  }
Keys == {"none", "typed", "stored"}
Indexes == {"empty", "indexing", "ready"}
Calls == {"idle", "running", "answered", "failed"}
Purposes == {"idle", "ask", "preview", "draft"}
Texts == {"empty", "test", "error"}

TypeOK ==
  /\ screen \in Screens
  /\ key \in Keys
  /\ index \in Indexes
  /\ secret \in BOOLEAN
  /\ spinning \in BOOLEAN
  /\ llmButtons \in BOOLEAN
  /\ call \in Calls
  /\ purpose \in Purposes
  /\ preview \in Texts
  /\ previewOk \in BOOLEAN
  /\ written \in Texts

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

\* A feature purpose is set only while that call is running.
PurposeOnlyWhileRunning == purpose = "idle" \/ call = "running"

atHub == screen \in {
  "noKey", "typed", "saved",
  "filesNoKey", "typedFiles", "savedFiles",
  "askCited", "askFailed",
  "previewShown", "previewFailed", "writtenFile",
  "commitPlaced", "commitFailed"
  }

\* The guard a mutation removes. Ask, Preview, and Draft all use it.
MayCall == key # "none" /\ atHub

rest ==
  /\ call' = "idle"
  /\ purpose' = "idle"
  /\ spinning' = FALSE
  /\ preview' = "empty"
  /\ previewOk' = FALSE

hub(k, ix) ==
  CASE k = "none" /\ ix = "empty" -> "noKey"
    [] k = "typed" /\ ix = "empty" -> "typed"
    [] k = "stored" /\ ix = "empty" -> "saved"
    [] k = "none" /\ ix = "ready" -> "filesNoKey"
    [] k = "typed" /\ ix = "ready" -> "typedFiles"
    [] OTHER -> "savedFiles"

base ==
  /\ call = "idle"
  /\ purpose = "idle"
  /\ spinning = FALSE
  /\ preview = "empty"
  /\ previewOk = FALSE
  /\ written = "empty"

Init ==
  \/ /\ screen = "noKey" /\ key = "none" /\ index = "empty"
     /\ secret = FALSE /\ llmButtons = FALSE /\ base
  \/ /\ screen = "saved" /\ key = "stored" /\ index = "empty"
     /\ secret = TRUE /\ llmButtons = TRUE /\ base
  \/ /\ screen = "savedFiles" /\ key = "stored" /\ index = "ready"
     /\ secret = TRUE /\ llmButtons = TRUE /\ base

YouTypeKey ==
  /\ key = "none"
  /\ atHub
  /\ key' = "typed"
  /\ llmButtons' = TRUE
  /\ screen' = hub("typed", index)
  /\ rest
  /\ UNCHANGED <<index, secret, written>>

YouEraseTypedKey ==
  /\ key = "typed"
  /\ index = "empty"
  /\ atHub
  /\ key' = "none"
  /\ llmButtons' = FALSE
  /\ secret' = FALSE
  /\ screen' = "noKey"
  /\ rest
  /\ UNCHANGED <<index, written>>

YouSaveKey ==
  /\ key = "typed"
  /\ atHub
  /\ key' = "stored"
  /\ secret' = TRUE
  /\ llmButtons' = TRUE
  /\ screen' = hub("stored", index)
  /\ rest
  /\ UNCHANGED <<index, written>>

\* The field is empty. The stored key is left as it is.
YouSaveEmpty ==
  /\ key \in {"none", "stored"}
  /\ atHub
  /\ secret' = secret
  /\ UNCHANGED <<screen, key, index, spinning, llmButtons, call, purpose, preview, previewOk, written>>

\* Clearing a stored key with no indexed files returns to No API key.
\* The model does not clear the key once files are ready; the window does,
\* and the indexed files stay.
YouClearKey ==
  /\ key = "stored"
  /\ index = "empty"
  /\ atHub
  /\ key' = "none"
  /\ secret' = FALSE
  /\ llmButtons' = FALSE
  /\ screen' = "noKey"
  /\ rest
  /\ UNCHANGED <<index, written>>

YouIndexFiles ==
  /\ key = "stored"
  /\ atHub
  /\ index \in {"empty", "ready"}
  /\ screen' = "indexing"
  /\ index' = "indexing"
  /\ spinning' = TRUE
  /\ call' = "idle"
  /\ purpose' = "idle"
  /\ preview' = "empty"
  /\ previewOk' = FALSE
  /\ UNCHANGED <<key, secret, llmButtons, written>>

YouRemoveFile ==
  /\ key = "stored"
  /\ atHub
  /\ index = "ready"
  /\ screen' = "indexing"
  /\ index' = "indexing"
  /\ spinning' = TRUE
  /\ call' = "idle"
  /\ purpose' = "idle"
  /\ preview' = "empty"
  /\ previewOk' = FALSE
  /\ UNCHANGED <<key, secret, llmButtons, written>>

IndexingFinishes ==
  /\ screen = "indexing"
  /\ index' = "ready"
  /\ spinning' = FALSE
  /\ screen' = hub(key, "ready")
  /\ UNCHANGED <<key, secret, llmButtons, call, purpose, preview, previewOk, written>>

IndexingFindsNothing ==
  /\ screen = "indexing"
  /\ index' = "empty"
  /\ spinning' = FALSE
  /\ screen' = hub(key, "empty")
  /\ UNCHANGED <<key, secret, llmButtons, call, purpose, preview, previewOk, written>>

BeginCall(nextScreen, nextPurpose) ==
  /\ MayCall
  /\ screen' = nextScreen
  /\ purpose' = nextPurpose
  /\ call' = "running"
  /\ spinning' = TRUE
  /\ preview' = "empty"
  /\ previewOk' = FALSE
  /\ UNCHANGED <<key, index, secret, llmButtons, written>>

YouAsk == BeginCall("asking", "ask")
YouPreview == BeginCall("generating", "preview")
YouDraft == BeginCall("drafting", "draft")

TheCallAnswers ==
  /\ call = "running"
  /\ call' = "answered"
  /\ purpose' = "idle"
  /\ spinning' = FALSE
  /\ screen' = CASE purpose = "ask" -> "askCited"
                 [] purpose = "preview" -> "previewShown"
                 [] OTHER -> "commitPlaced"
  /\ preview' = IF purpose = "preview" THEN "test" ELSE "empty"
  /\ previewOk' = (purpose = "preview")
  /\ UNCHANGED <<key, index, secret, llmButtons, written>>

\* A failed preview leaves error text in the transcript and clears the success flag.
\* Ask and Draft do not leave a test in the transcript.
TheCallFails ==
  /\ call = "running"
  /\ call' = "failed"
  /\ purpose' = "idle"
  /\ spinning' = FALSE
  /\ screen' = CASE purpose = "ask" -> "askFailed"
                 [] purpose = "preview" -> "previewFailed"
                 [] OTHER -> "commitFailed"
  /\ preview' = IF purpose = "preview" THEN "error" ELSE "empty"
  /\ previewOk' = FALSE
  /\ UNCHANGED <<key, index, secret, llmButtons, written>>

\* Write copies whatever the transcript currently shows. The only guard is that
\* the last preview succeeded. The failed-preview screen is included so that
\* removing the guard lets error text be saved.
YouWrite ==
  /\ screen \in {"previewShown", "previewFailed", "writtenFile"}
  /\ previewOk
  /\ written' = preview
  /\ screen' = "writtenFile"
  /\ call' = "idle"
  /\ purpose' = "idle"
  /\ spinning' = FALSE
  /\ UNCHANGED <<key, index, secret, llmButtons, preview, previewOk>>

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
  \/ YouWrite

Spec == Init /\ [][Next]_vars

====
