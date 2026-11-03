import { Disclosure } from '@heroui/react';
import { memo, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import type { Attempt, Body, Card, Citation } from '../../protocol/folio-v1';
import type { Bridge } from './bridge';
import { IconButton, ActionButton } from './controls';
import { SafeMarkdown } from './markdown';

export type Expansion = { expanded: string[]; set: (id: string, expanded: boolean) => void };
function Details({ id, title, expansion, children }: { id: string; title: string; expansion: Expansion; children: React.ReactNode }) {
  return <Disclosure className="details" isExpanded={expansion.expanded.includes(id)} onExpandedChange={expanded => expansion.set(id, expanded)}>
    <Disclosure.Heading><Disclosure.Trigger className="details-trigger"><span>{title}</span><Disclosure.Indicator /></Disclosure.Trigger></Disclosure.Heading>
    <Disclosure.Content><Disclosure.Body className="details-body">{children}</Disclosure.Body></Disclosure.Content>
  </Disclosure>;
}
function excerptPreview(text: string) {
  const flat = text.replace(/\s+/g, ' ').trim();
  return flat.length > 180 ? flat.slice(0, 180) + '…' : flat;
}
function References({ citations, at, bridge, expansion }: { citations: Citation[]; at: Attempt; bridge: Bridge; expansion: Expansion }) {
  if (!citations.length) return null;
  return <Details id={`${at.turn}:references`} title={`课件 · ${citations.length} 处引用`} expansion={expansion}>
    <ol className="citations">{citations.map((citation, index) => <li key={`${citation.number}-${index}`}>
      <button className="source-title" onClick={() => void bridge.command({ kind: 'citation', at, index })}>[{citation.number}] {citation.title}</button>
      <div className="muted source-location">{citation.location}</div><p className="excerpt">{excerptPreview(citation.excerpt)}</p>
    </li>)}</ol>
  </Details>;
}
const evidenceText = { GENERAL: '', INLINE: '引用了检索到的课件', RETRIEVED: '', INVALID: '引用编号未能匹配课件，请核对来源。', AMBIGUOUS: '引用存在歧义，请核对课件。', CONFIGURATION: '当前为本地配置或课件检索结果。' };

function AnswerBody({ body, card, bridge, expansion }: { body: Body; card: Card; bridge: Bridge; expansion: Expansion }) {
  const at = card.at;
  switch (body.kind) {
    case 'stream': return <>
      {body.reasoning ? <details className="thinking" open={body.phase === '正在思考'}><summary>思考</summary><div className="thinking-text">{body.reasoning}</div></details> : null}
      <div className="stream-status" role="status"><span className="activity-dot" />{body.phase}</div>
      {body.text ? <div className="stream-text">{body.text}</div> : null}
    </>;
    case 'answer': return <><SafeMarkdown text={body.raw} at={at} bridge={bridge} />{evidenceText[body.evidence] && <p className="muted evidence">{evidenceText[body.evidence]}</p>}<References citations={body.citations} at={at} bridge={bridge} expansion={expansion} /></>;
    case 'practice': return <><div className="exercises">{body.exercises.map((exercise, index) => <section className="exercise" key={index}>
      <div className="exercise-number">练习 {index + 1}</div><SafeMarkdown text={exercise.question} at={at} bridge={bridge} actions={false} />
      <Details id={`${at.turn}:exercise:${index}`} title="查看解析" expansion={expansion}><SafeMarkdown text={exercise.explanation} at={at} bridge={bridge} actions={false} /></Details>
    </section>)}</div><References citations={body.citations} at={at} bridge={bridge} expansion={expansion} /></>;
    case 'test': return <><p>{body.problem || '测试草稿已生成，请预览并确认后写入。'}</p><div className="capture-summary"><span>来源</span><div>{body.source || '未提供'}</div></div>
      <p className="muted">仅经 PSI 语法与 JUnit 识别检查，未编译或运行。</p>
      <ActionButton variant="secondary" onPress={() => void bridge.command({ kind: 'previewTest', at, tab: body.code === null ? 'raw' : 'code' })}>查看测试预览</ActionButton>
    </>;
    case 'commit': return <><p className="muted commit-estimate">已捕获暂存区 · {body.chars.toLocaleString()} 字符 · {body.batches} 批 · {body.calls} 次调用</p>
      <div className="commit-candidates">{body.candidates.map((candidate, index) => <section className="commit-candidate" key={index}><div className="candidate-number">候选 {index + 1}</div><pre>{candidate}</pre><div className="candidate-actions">
        <IconButton name="复制提交信息" icon="copy" onPress={() => void bridge.command({ kind: 'deliverCommit', at, index, delivery: 'copy' })} />
        <IconButton name="填入提交框" icon="download" disabled={!card.fillCommit.enabled} reason={card.fillCommit.reason} onPress={() => void bridge.command({ kind: 'deliverCommit', at, index, delivery: 'fill' })} />
      </div></section>)}</div>
      <Details id={`${at.turn}:diff`} title="查看捕获的暂存区差异" expansion={expansion}><pre className="raw-text">{body.diff}</pre></Details>
    </>;
    case 'failed': return <><p className="failure" role="status">{body.message}</p>{body.raw && <Details id={`${at.turn}:invalid`} title="查看原始回复" expansion={expansion}><pre className="raw-text">{body.raw}</pre></Details>}</>;
    case 'stopped': return <><div className="stream-text">{body.text}</div><p className="muted">已停止生成</p></>;
  }
}

function useSelectionBody(body: Body, element: React.RefObject<HTMLDivElement | null>) {
  const displayed = useRef(body);
  const latest = useRef(body);
  latest.current = body;
  const [, release] = useState(0);
  const selected = () => {
    const selection = document.getSelection();
    return selection && !selection.isCollapsed && element.current &&
      (element.current.contains(selection.anchorNode) || element.current.contains(selection.focusNode));
  };
  if (!selected()) displayed.current = body;
  useEffect(() => {
    const update = () => { if (!selected() && displayed.current !== latest.current) { displayed.current = latest.current; release(value => value + 1); } };
    document.addEventListener('selectionchange', update);
    return () => document.removeEventListener('selectionchange', update);
  }, []);
  return displayed.current;
}

export const Turn = memo(function Turn({ turn, bridge, busy, expansion }: { turn: string; bridge: Bridge; busy: boolean; expansion: Expansion }) {
  const card = useSyncExternalStore(listener => bridge.subscribeCard(turn, listener), () => bridge.getCard(turn));
  const content = useRef<HTMLDivElement>(null);
  const empty: Body = { kind: 'stream', phase: '', text: '' };
  const body = useSelectionBody(card?.body || empty, content);
  if (!card) return null;
  const terminal = body.kind !== 'stream';
  return <article className="turn" data-turn={turn}>
    <div className="user-row"><div className="user-message">{card.user || '根据暂存区生成提交信息'}</div>
      <IconButton name="复制问题" icon="copy" onPress={() => void bridge.command({ kind: 'copy', at: card.at, part: 'user', index: 0 })} />
    </div>
    <div className="answer" ref={content}><AnswerBody body={body} card={card} bridge={bridge} expansion={expansion} /></div>
    <div className="answer-tools">
      {body.kind !== 'commit' && <IconButton name="复制回答" icon="copy" disabled={!terminal} reason="回答仍在生成" onPress={() => void bridge.command({ kind: 'copy', at: card.at, part: 'answer', index: 0 })} />}
      {card.generateTests && <IconButton name="生成测试" icon="tests" disabled={busy || !card.generateTests.enabled} reason={busy ? '请先停止当前生成' : card.generateTests.reason} onPress={() => void bridge.command({ kind: 'generateTests', at: card.at })} />}
      <IconButton name="重新生成" icon="retry" disabled={busy || !card.retry.enabled} reason={busy ? '请先停止当前生成' : card.retry.reason} onPress={() => void bridge.command({ kind: 'retry', at: card.at })} />
    </div>
  </article>;
});
