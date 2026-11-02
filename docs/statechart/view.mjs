export const title = "Folio tool window";

const keyLabel = {
  none: "No API key",
  typed: "Key typed, not saved",
  stored: "Key ready",
};

const indexLabel = {
  empty: "No files",
  indexing: "Indexing",
  ready: "Files ready",
};

const callLabel = {
  idle: "Idle",
  running: "Working",
  answered: "Answered",
  failed: "Failed",
};

export const state = (v) => ({
  "API key": keyLabel[v.key] ?? "Bad key",
  "Course files": indexLabel[v.index],
  "Model call": callLabel[v.call],
});

const labels = {
  YouTypeKey: "you type an API key",
  YouEraseTypedKey: "you clear the field",
  YouSaveKey: "you save settings",
  YouSaveEmpty: "you save settings with the key field empty",
  YouClearKey: "you clear the API key",
  YouIndexFiles: "you index files",
  YouRemoveFile: "you remove a file",
  IndexingFinishes: "indexing finishes",
  IndexingFindsNothing: "the index is empty",
  StartCall: "you ask, preview, or draft",
  YouAsk: "you ask, preview, or draft",
  YouPreview: "you ask, preview, or draft",
  YouDraft: "you ask, preview, or draft",
  TheCallAnswers: "the model answers",
  TheCallFails: "the call fails",
  YouCancelCall: "the call is canceled",
  YouWrite: "you write the test file",
  RestoreKey: "the window restores a saved key",
  RestoreFiles: "the window restores indexed files",
  RestoreBoth: "the window restores a saved key and indexed files",
};

export const step = (action) => (Object.hasOwn(labels, action) ? labels[action] : action);

export const group = (v) => {
  if (v.call === "running" || v.index === "indexing") return "Working";
  if (v.call === "failed") return "Failed";
  if (v.key === "none" && v.index === "empty" && v.call === "idle") return "Setup";
  return "Ready";
};

export const colors = {
  Setup: "tan",
  Ready: "teal",
  Working: "orange",
  Failed: "pink",
};
