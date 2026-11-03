import { describe, expect, it } from 'vitest';
import { markdownParts } from './fences';

describe('original raw fence identity shared with Java codeBlocks', () => {
  for (const separator of ['\n', '\r\n', '\r', '\v', '\f', '\u0085', '\u2028', '\u2029']) {
    it(`retains exact separator ${JSON.stringify(separator)} and original block indexes`, () => {
      const raw = `Before${separator}\`\`\`java${separator}class First {}${separator}\`\`\`${separator}Between${separator}\`\`\`text${separator}second${separator}\`\`\``;
      const code = markdownParts(raw).filter(part => part.kind === 'code');
      expect(code).toEqual([
        { kind: 'code', text: `class First {}${separator}`, language: 'java', index: 0 },
        { kind: 'code', text: `second${separator}`, language: 'text', index: 1 },
      ]);
    });
  }
  it('keeps nested nonclosing fence lines in the same original block', () => {
    const parts = markdownParts('```java\nouter\n```nested\ninner\n```\n');
    expect(parts).toEqual([{ kind: 'code', language: 'java', text: 'outer\n```nested\ninner\n', index: 0 }]);
  });
  it('renders unclosed fences literally without code commands', () => {
    expect(markdownParts('Before\n```java\nunfinished')).toEqual([{ kind: 'markdown', text: 'Before\n' }, { kind: 'literal', text: '```java\nunfinished' }]);
  });
  it('does not give indented or tilde Markdown blocks an original Java code index', () => {
    expect(markdownParts('  ```java\nindented\n  ```\n~~~java\ntilde\n~~~').filter(part => part.kind === 'code')).toHaveLength(0);
  });
  it('matches Java blank closing rules rather than JavaScript trim', () => {
    expect(markdownParts('```\nkeep\n```\u00a0\nstill code\n```\n')).toEqual([{ kind: 'code', language: '', text: 'keep\n```\u00a0\nstill code\n', index: 0 }]);
  });
});
