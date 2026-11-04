import { Modal } from '@heroui/react';
import { useEffect, useLayoutEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import type { Reading, Route, Shell } from '../../protocol/folio-v1';
import { Bridge } from './bridge';
import { Drafts } from './drafts';
import { ActionButton, IconButton } from './controls';
import { Turn, type Expansion } from './cards';
import { Composer } from './composer';
import { Files, Settings, SourcePreview, TestPreview } from './pages';

const titles: Record<Route['kind'], string> = { chat: '', files: '课件', settings: '设置', testPreview: '测试预览', sourcePreview: '来源摘录' };

function Chat({ shell, bridge, drafts, turns, hydrated }: { shell: Shell; bridge: Bridge; drafts: Drafts; turns: string[]; hydrated: boolean }) {
  const log = useRef<HTMLDivElement>(null);
  const reading = useRef<Reading>(shell.reading);
  const [following, setFollowing] = useState(shell.reading.following);
  const [unseen, setUnseen] = useState(shell.reading.unseen);
  const [expanded, setExpanded] = useState(shell.reading.expanded);
  const programmatic = useRef(false);
  const lastTop = useRef(0);
  const savedRange = useRef<Range | null>(null);
  const visible = shell.route.kind === 'chat';
  const visibleRef = useRef(visible);
  visibleRef.current = visible;
  const notifyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const publishReading = () => {
    if (notifyTimer.current) clearTimeout(notifyTimer.current);
    notifyTimer.current = setTimeout(() => { void bridge.command({ kind: 'reading', value: reading.current }); }, 80);
  };
  const anchor = () => {
    const viewport = log.current;
    if (!viewport) return null;
    const top = viewport.getBoundingClientRect().top;
    const articles = Array.from(viewport.querySelectorAll<HTMLElement>('[data-turn]'));
    const first = articles.find(article => article.getBoundingClientRect().bottom > top);
    return first ? { turn: first.dataset.turn!, offset: first.getBoundingClientRect().top - top } : null;
  };
  const stopFollowing = () => {
    if (!reading.current.following) return;
    reading.current = { ...reading.current, following: false, anchor: anchor() };
    setFollowing(false);
    publishReading();
  };
  const latest = () => {
    const viewport = log.current;
    if (!viewport) return;
    programmatic.current = true;
    viewport.scrollTop = viewport.scrollHeight;
    lastTop.current = viewport.scrollTop;
    reading.current = { ...reading.current, following: true, anchor: null, unseen: 0 };
    setFollowing(true); setUnseen(0); publishReading();
    requestAnimationFrame(() => { programmatic.current = false; });
  };
  useEffect(() => {
    const selection = () => {
      const selected = document.getSelection();
      if (selected && !selected.isCollapsed && log.current?.contains(selected.anchorNode)) {
        savedRange.current = selected.getRangeAt(0).cloneRange();
        stopFollowing();
      }
    };
    document.addEventListener('selectionchange', selection);
    return () => { document.removeEventListener('selectionchange', selection); if (notifyTimer.current) clearTimeout(notifyTimer.current); };
  }, []);
  useLayoutEffect(() => {
    const viewport = log.current;
    if (!viewport) return;
    if (visible) {
      programmatic.current = true;
      if (reading.current.following) viewport.scrollTop = viewport.scrollHeight;
      else viewport.scrollTop = lastTop.current;
      if (savedRange.current && !reading.current.following) {
        const selection = document.getSelection();
        selection?.removeAllRanges(); selection?.addRange(savedRange.current);
      }
      requestAnimationFrame(() => { programmatic.current = false; });
    }
  }, [visible]);
  useEffect(() => {
    const viewport = log.current;
    const content = viewport?.firstElementChild;
    if (!viewport || !content) return;
    let initial = true;
    const observer = new ResizeObserver(() => {
      if (!visibleRef.current) return;
      if (reading.current.following) {
        programmatic.current = true;
        viewport.scrollTop = viewport.scrollHeight;
        lastTop.current = viewport.scrollTop;
        requestAnimationFrame(() => { programmatic.current = false; });
      } else if (!initial) {
        const retained = reading.current.anchor;
        const article = retained ? Array.from(viewport.querySelectorAll<HTMLElement>('[data-turn]')).find(element => element.dataset.turn === retained.turn) : null;
        if (retained && article) viewport.scrollTop += article.getBoundingClientRect().top - viewport.getBoundingClientRect().top - retained.offset;
        else viewport.scrollTop = lastTop.current;
        lastTop.current = viewport.scrollTop;
        reading.current = { ...reading.current, unseen: reading.current.unseen + 1 };
        setUnseen(reading.current.unseen);
      }
      initial = false;
    });
    observer.observe(content);
    return () => observer.disconnect();
  }, []);
  useLayoutEffect(() => {
    const page = log.current?.parentElement;
    const composer = page?.querySelector('.composer-area');
    if (!page || !composer) return;
    const observer = new ResizeObserver(() => page.style.setProperty('--composer-height', `${composer.getBoundingClientRect().height}px`));
    observer.observe(composer);
    return () => observer.disconnect();
  }, []);
  const previousTurnCount = useRef(turns.length);
  useEffect(() => {
    if (previousTurnCount.current > 0 && turns.length === 0) {
      savedRange.current = null;
      setExpanded([]);
      reading.current = { following: true, anchor: null, unseen: 0, expanded: [] };
      setFollowing(true); setUnseen(0); lastTop.current = 0;
    }
    previousTurnCount.current = turns.length;
  }, [turns.length]);
  const expansion: Expansion = useMemo(() => ({ expanded, set: (id, isExpanded) => {
    setExpanded(current => {
      const next = isExpanded ? [...current.filter(value => value !== id), id] : current.filter(value => value !== id);
      reading.current = { ...reading.current, expanded: next };
      publishReading();
      return next;
    });
  } }), [expanded]);
  return <section className={`chat-page ${visible ? '' : 'inactive'}`} aria-hidden={!visible} inert={!visible} aria-label="对话">
    <div role="log" aria-label="对话记录" aria-live="off" className="chat-log" ref={log}
      onWheel={event => { if (event.deltaY < 0) stopFollowing(); }}
      onTouchStart={stopFollowing}
      onKeyDown={event => { if (['PageUp', 'Home', 'ArrowUp'].includes(event.key)) stopFollowing(); }}
      onScroll={() => {
        if (!log.current || programmatic.current || !visible) return;
        if (log.current.scrollTop < lastTop.current - 1) stopFollowing();
        lastTop.current = log.current.scrollTop;
        if (!reading.current.following) { reading.current = { ...reading.current, anchor: anchor() }; publishReading(); }
      }}>
      <div className="chat-content">{turns.length === 0 ? <div className="empty-chat"><p>从一个问题开始</p></div> : turns.map(turn => <Turn key={turn} turn={turn} bridge={bridge} busy={shell.generation !== null} expansion={expansion} />)}</div>
    </div>
    {!following && <div className="latest-row"><ActionButton variant="secondary" onPress={latest}>回到最新{unseen ? ' · 有新内容' : ''}</ActionButton></div>}
    <Composer shell={shell} bridge={bridge} drafts={drafts} disabled={!hydrated} onSent={latest} />
  </section>;
}

export function App({ bridge, drafts }: { bridge: Bridge; drafts: Drafts }) {
  const view = useSyncExternalStore(bridge.subscribe, bridge.getSnapshot);
  const shell = view.shell;
  const fontSize = shell ? Math.max(15, (shell.theme.fontPx + 2) * shell.theme.scale) : 15;
  useEffect(() => {
    if (!shell) return;
    document.documentElement.classList.toggle('dark', shell.theme.dark);
    document.documentElement.classList.toggle('light', !shell.theme.dark);
    document.documentElement.dataset.theme = shell.theme.dark ? 'dark' : 'light';
    document.documentElement.dataset.reducedMotion = String(shell.theme.reducedMotion);
    document.documentElement.style.setProperty('--ide-font', shell.theme.font);
    document.documentElement.style.setProperty('--ide-font-size', `${fontSize}px`);
    document.documentElement.style.colorScheme = shell.theme.dark ? 'dark' : 'light';
  }, [shell?.theme, fontSize]);
  if (!bridge.transport) return <main className="host-unavailable"><p>请在 IntelliJ IDEA 的 Folio 侧栏中打开。</p><span>此页面需要 IDE 提供连接与工作流，独立浏览器不执行操作。</span></main>;
  if (!shell) return <main className="host-unavailable" role="status"><p>正在连接 IDE…</p></main>;
  const auxiliary = shell.route.kind !== 'chat';
  const back = () => shell.route.kind === 'settings' ? void bridge.command({ kind: 'cancelSettings', edit: shell.route.edit }) : void bridge.command({ kind: 'route', to: 'chat' });
  return <main className="folio-app" style={{ fontSize, fontFamily: shell.theme.font }} aria-busy={!view.hydrated}>
    <header className="utility-row" aria-label="侧栏工具">
      {auxiliary && <><IconButton name="返回对话" icon="back" onPress={back} /><span className="route-title">{titles[shell.route.kind]}</span></>}
      <div className="utility-spacer" />
      <IconButton name="暂存" icon="stage" disabled={!view.hydrated} onPress={() => void bridge.command({ kind: 'stage' })} />
      <IconButton name="新对话" icon="plus" disabled={!view.hydrated} onPress={() => void bridge.command({ kind: 'newChat' })} />
      <IconButton name="课件" icon="files" disabled={!view.hydrated} onPress={() => void bridge.command({ kind: 'route', to: shell.route.kind === 'files' ? 'chat' : 'files' })} />
      <IconButton name="设置" icon="settings" disabled={!view.hydrated} onPress={() => shell.route.kind === 'settings' ? back() : void bridge.command({ kind: 'route', to: 'settings' })} />
    </header>
    {(shell.notice || view.problem) && <div className={`notice${view.problem || !/^(设置已保存|导入完成|已复制|已写入|已填入|已打开|已暂存|没有可暂存)/.test(shell.notice) ? '' : ' info'}`} role="status">{view.problem || shell.notice}</div>}
    <Chat shell={shell} bridge={bridge} drafts={drafts} turns={view.turns} hydrated={view.hydrated} />
    {shell.route.kind === 'settings' && <Settings key={shell.route.edit} edit={shell.route.edit} provider={shell.provider} bridge={bridge} />}
    {shell.route.kind === 'files' && <Files shell={shell} bridge={bridge} />}
    {shell.route.kind === 'testPreview' && <TestPreview at={shell.route.at} tab={shell.route.tab} bridge={bridge} />}
    {shell.route.kind === 'sourcePreview' && <SourcePreview route={shell.route} />}
    <Modal.Backdrop className="confirm-backdrop" isOpen={shell.confirm !== null} onOpenChange={open => { if (!open && shell.confirm) void bridge.command({ kind: 'confirm', id: shell.confirm.id, accept: false }); }}>
<Modal.Container className="confirm-container" size="sm"><Modal.Dialog aria-label="确认操作" className="confirm-dialog"><Modal.Heading>确认操作</Modal.Heading><Modal.Body><p>{shell.confirm?.text}</p></Modal.Body><Modal.Footer><ActionButton variant="secondary" onPress={() => { if (shell.confirm) void bridge.command({ kind: 'confirm', id: shell.confirm.id, accept: false }); }}>取消</ActionButton><ActionButton onPress={() => { if (shell.confirm) void bridge.command({ kind: 'confirm', id: shell.confirm.id, accept: true }); }}>确认</ActionButton></Modal.Footer></Modal.Dialog></Modal.Container></Modal.Backdrop>

  </main>;
}
