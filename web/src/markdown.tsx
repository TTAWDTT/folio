import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Attempt } from '../../protocol/folio-v1';
import type { Bridge } from './bridge';
import { IconButton } from './controls';
import { markdownParts } from './fences';

export function SafeMarkdown({ text, at, bridge, actions = true }: { text: string; at: Attempt; bridge: Bridge; actions?: boolean }) {
  return <div className="markdown">{markdownParts(text).map((part, key) => {
    if (part.kind === 'literal') return <pre className="unclosed-fence" key={key}>{part.text}</pre>;
    if (part.kind === 'code') return <div className="code-block" key={key}>
      {actions && <div className="code-tools"><span className="code-language">{part.language}</span>
        <IconButton name="复制代码" icon="copy" onPress={() => void bridge.command({ kind: 'copy', at, part: 'code', index: part.index })} />
      </div>}
      <pre><code>{part.text.replace(/\r\n|[\r\v\f\u0085\u2028\u2029]/g, '\n')}</code></pre>
    </div>;
    return <Markdown key={key} remarkPlugins={[remarkGfm]} skipHtml components={{
      img: ({ alt }) => <span className="muted">[图片已阻止{alt ? `：${alt}` : ''}]</span>,
      a: ({ children }) => <span className="markdown-link">{children}</span>,
      pre: ({ children }) => <div className="code-block"><pre>{children}</pre></div>,
    }}>{part.text}</Markdown>;
  })}</div>;
}
