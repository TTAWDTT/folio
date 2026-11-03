export type MarkdownPart = { kind: 'markdown' | 'literal'; text: string } | { kind: 'code'; text: string; language: string; index: number };
const separators = '\r\n\u000b\f\u0085\u2028\u2029';
const javaBlank = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/u;

export function markdownParts(text: string): MarkdownPart[] {
  const parts: MarkdownPart[] = [];
  let next = 0;
  let cursor = 0;
  let start = -1;
  let fenceStart = -1;
  let language = '';
  let index = 0;
  while (next < text.length) {
    const line = next;
    let end = line;
    while (end < text.length && !separators.includes(text[end])) end++;
    next = end;
    if (next < text.length && text[next++] === '\r' && text[next] === '\n') next++;
    if (!text.startsWith('```', line)) continue;
    if (start < 0) {
      if (next > end) { start = next; fenceStart = line; language = text.slice(line + 3, end).trim(); }
    } else if (javaBlank.test(text.slice(line + 3, end))) {
      if (fenceStart > cursor) parts.push({ kind: 'markdown', text: text.slice(cursor, fenceStart) });
      parts.push({ kind: 'code', text: text.slice(start, line), language, index: index++ });
      cursor = next;
      start = -1;
    }
  }
  if (start >= 0) {
    if (fenceStart > cursor) parts.push({ kind: 'markdown', text: text.slice(cursor, fenceStart) });
    parts.push({ kind: 'literal', text: text.slice(fenceStart) });
  } else if (cursor < text.length) parts.push({ kind: 'markdown', text: text.slice(cursor) });
  return parts;
}
