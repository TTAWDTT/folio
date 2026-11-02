export const title = "Folio tool window";

const place = {
  noKey: ["Setup", "No API key"],
  typed: ["Setup", "Key typed, not saved"],
  saved: ["Ready", "Key saved"],
  filesNoKey: ["Course files", "Files ready, no key"],
  typedFiles: ["Course files", "Files ready, key not saved"],
  savedFiles: ["Ready", "Files ready"],
  indexing: ["Course files", "Indexing"],
  asking: ["Ask", "Asking"],
  askCited: ["Ask", "Finished", "Answer with citations"],
  askFailed: ["Ask", "Finished", "The ask failed"],
  generating: ["Test", "Generating the preview"],
  previewShown: ["Test", "Preview shown"],
  previewFailed: ["Test", "Finished", "Preview failed"],
  previewNotATest: ["Test", "Finished", "Not a test"],
  writtenFile: ["Test", "Finished", "Test file written"],
  drafting: ["Commit", "Drafting a message"],
  commitPlaced: ["Commit", "Finished", "Message in the commit box"],
  commitFailed: ["Commit", "Finished", "The draft failed"],
};

export const state = (v) => place[v.screen] ?? ["Setup", "Bad key"];

const leaf = (v) => state(v).at(-1);

export const group = (v) => {
  const name = leaf(v);
  if (
    name === "Indexing" ||
    name === "Asking" ||
    name === "Generating the preview" ||
    name === "Drafting a message"
  )
    return "Working";
  if (
    name === "The ask failed" ||
    name === "Preview failed" ||
    name === "Not a test" ||
    name === "The draft failed"
  )
    return "Error";
  if (
    name === "No API key" ||
    name === "Key typed, not saved" ||
    name === "Files ready, no key" ||
    name === "Files ready, key not saved"
  )
    return "Setup";
  return "Ready";
};

export const colors = {
  Setup: "tan",
  Working: "orange",
  Ready: "teal",
  Error: "pink",
};

const answerLabel = {
  askCited: "the model answers",
  previewShown: "the model shows a preview",
  commitPlaced: "the message is in the commit box",
  askFailed: "the ask fails",
  previewFailed: "the preview fails",
  previewNotATest: "the reply is not a test",
  commitFailed: "the draft fails",
};

function beginLabel(screen) {
  if (screen === "asking") return "you click Ask";
  if (screen === "generating") return "you click Preview test";
  if (screen === "drafting") return "you click Draft commit messages";
  return null;
}

function showBegin(from, to) {
  if (from === "saved" || from === "savedFiles" || from === "noKey") return true;
  if ((from === "askCited" || from === "askFailed") && to === "asking") return true;
  if (
    (from === "previewFailed" || from === "previewNotATest" || from === "writtenFile") &&
    to === "generating"
  )
    return true;
  if ((from === "commitPlaced" || from === "commitFailed") && to === "drafting") return true;
  return false;
}

export const step = (action, from, to) => {
  if (action === "YouSaveEmpty") return "you save settings with the key field empty";
  if (action === "YouTypeKey") return from.screen === "noKey" ? "you type an API key" : null;
  if (action === "YouEraseTypedKey") return from.screen === "typed" ? "you clear the field" : null;
  if (action === "YouSaveKey") return from.screen === "typed" ? "you save settings" : null;
  if (action === "YouClearKey") return from.screen === "saved" ? "you clear the API key" : null;
  if (action === "YouIndexFiles")
    return from.screen === "saved" || from.screen === "savedFiles" ? "you index files" : null;
  if (action === "YouRemoveFile") return null;
  if (action === "IndexingFinishes") return "indexing finishes";
  if (action === "IndexingFindsNothing") return "the index is empty";
  if (action === "BeginCall" || action === "YouAsk" || action === "YouPreview" || action === "YouDraft") {
    const label = beginLabel(to.screen);
    return label && showBegin(from.screen, to.screen) ? label : null;
  }
  if (action === "YouWrite") return "you click Write test file";
  if (action === "TheCallAnswers" || action === "TheCallFails") return answerLabel[to.screen] ?? action;
  return action;
};
