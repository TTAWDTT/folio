import { Button, Dropdown, TextArea, Tooltip } from '@heroui/react';
import { useLayoutEffect, useRef, useState, useSyncExternalStore } from 'react';
import type { Attempt, DraftValue, Mode, Shell } from '../../protocol/folio-v1';
import type { Bridge } from './bridge';
import { Drafts, modes, modeNames, modeShortNames } from './drafts';
import { Icon, modeIcons } from './icons';
import { ActionButton, IconButton } from './controls';

type Intent = { kind: 'stop'; at: Attempt } | { kind: 'send'; mode: Mode; epoch: number; value: DraftValue };
const placeholders: Record<Mode, string> = { ask: '继续提问，或粘贴代码…', drill: '想练习哪个知识点？', tests: '描述要覆盖的测试场景…', commit: '补充提交意图，可留空…' };

export function Composer({ shell, bridge, drafts, disabled, onSent }: { shell: Shell; bridge: Bridge; drafts: Drafts; disabled: boolean; onSent: () => void }) {
  useSyncExternalStore(drafts.subscribe, drafts.getSnapshot);
  const mode = shell.mode;
  const editor = drafts.get(mode)!;
  const form = useRef<HTMLFormElement>(null);
  const textarea = useRef<HTMLTextAreaElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const intent = useRef<Intent | null>(null);
  const composing = useRef(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [menuOffset, setMenuOffset] = useState(48);
  const [problem, setProblem] = useState('');
  const canSend = !disabled && !editor.conflict && (mode === 'commit' || editor.value.text.trim().length > 0);
  const busy = shell.generation !== null;

  const value = (): DraftValue => {
    const input = textarea.current;
    if (!input) return editor.value;
    return { text: input.value, dot: input.selectionDirection === 'backward' ? input.selectionStart : input.selectionEnd, mark: input.selectionDirection === 'backward' ? input.selectionEnd : input.selectionStart };
  };
  useLayoutEffect(() => {
    const input = textarea.current;
    if (!input) return;
    input.style.height = '0px';
    const lineHeight = parseFloat(getComputedStyle(input).lineHeight);
    input.style.height = `${Math.min(input.scrollHeight, lineHeight * 6 + 16)}px`;
  }, [editor.value.text, shell.theme.fontPx, shell.theme.scale]);
  useLayoutEffect(() => {
    const input = textarea.current;
    if (!input) return;
    const { dot, mark } = editor.value;
    input.setSelectionRange(Math.min(dot, mark), Math.max(dot, mark), dot < mark ? 'backward' : 'forward');
  }, [mode]);
  useLayoutEffect(() => {
    const measure = () => {
      if (form.current && trigger.current) setMenuOffset(trigger.current.getBoundingClientRect().top - form.current.getBoundingClientRect().top + 8);
    };
    measure();
    let width = 0;
    const observer = new ResizeObserver(() => {
      const input = textarea.current;
      const nextWidth = form.current?.clientWidth ?? 0;
      if (input && nextWidth !== width) {
        width = nextWidth;
        input.style.height = '0px';
        input.style.height = `${Math.min(input.scrollHeight, parseFloat(getComputedStyle(input).lineHeight) * 6 + 16)}px`;
      }
      measure();
    });
    if (form.current) observer.observe(form.current);
    return () => observer.disconnect();
  }, []);
  const capture = () => {
    intent.current = shell.generation ? { kind: 'stop', at: shell.generation } : canSend ? { kind: 'send', mode, epoch: editor.canonical.epoch, value: value() } : null;
  };
  const activate = () => {
    const captured = intent.current;
    intent.current = null;
    if (!captured) return;
    if (captured.kind === 'stop') { void bridge.command({ kind: 'stop', at: captured.at }); return; }
    void drafts.send(captured.mode, captured.epoch, captured.value).then(result => {
      if (result.kind === 'rejected') setProblem(result.message); else { setProblem(''); onSent(); }
    });
  };
  return <div className="composer-area">
    {editor.conflict && <div className="draft-conflict" role="alert"><p>IDE 已更换此模式的内容或代码上下文。你的未确认草稿已保留。</p><div className="form-actions">
      <ActionButton variant="secondary" onPress={() => drafts.resolveConflict(mode, false)}>使用 IDE 草稿</ActionButton>
      <ActionButton variant="secondary" onPress={() => drafts.resolveConflict(mode, true)}>重新应用我的草稿</ActionButton>
    </div></div>}
    {problem && <p className="composer-problem" role="status">{problem}</p>}
    <form aria-label="消息输入" className="composer" ref={form} onSubmit={event => event.preventDefault()}>
      {editor.canonical.context && <div className="context-chip"><Icon name="file" /><span>{editor.canonical.context}</span><IconButton name="移除代码上下文" icon="close" disabled={disabled} onPress={() => void bridge.command({ kind: 'removeContext', mode, revision: editor.canonical.revision })} /></div>}
      <div className="composer-field">
        <TextArea ref={textarea} value={editor.value.text} onChange={() => drafts.edit(mode, value())} onBlur={() => drafts.edit(mode, value())} aria-label="消息" aria-description="Enter 发送，Shift+Enter 换行" className="composer-textarea" placeholder={placeholders[mode]} rows={2} disabled={disabled} spellCheck={false}
          onSelect={() => { if (!composing.current) drafts.edit(mode, value()); }}
          onCompositionStart={() => { composing.current = true; }} onCompositionEnd={() => { composing.current = false; drafts.edit(mode, value()); }}
          onKeyDown={event => {
            if (event.key !== 'Enter' || event.shiftKey || composing.current || event.nativeEvent.isComposing || event.keyCode === 229) return;
            event.preventDefault();
            if (!busy && canSend && !event.repeat) { capture(); activate(); }
          }} />
      </div>
      <div className="composer-controls">
        <Dropdown isOpen={menuOpen} onOpenChange={setMenuOpen}>
          <Tooltip delay={350} closeDelay={0} isDisabled={menuOpen}>
            <Dropdown.Trigger ref={trigger} aria-label="选择模式" className="mode-trigger" isDisabled={disabled}><Icon name={modeIcons[mode]} /><span>{modeShortNames[mode]}</span><Icon name="chevron" /></Dropdown.Trigger>
            <Tooltip.Content className="help-tooltip">选择模式</Tooltip.Content>
          </Tooltip>
          <Dropdown.Popover className="mode-popover" placement="top start" offset={menuOffset} shouldFlip={false} containerPadding={8}>
            <Dropdown.Menu aria-label="任务模式" selectionMode="single" disallowEmptySelection selectedKeys={[mode]} onAction={key => {
              const selected = modes.find(item => item === key);
              if (selected) void bridge.command({ kind: 'mode', mode: selected });
              setMenuOpen(false);
            }}>
              {modes.map(item => <Dropdown.Item id={item} textValue={modeNames[item]} className="mode-item" key={item}><Icon name={modeIcons[item]} /><span className="mode-label">{modeNames[item]}</span><Dropdown.ItemIndicator /></Dropdown.Item>)}
            </Dropdown.Menu>
          </Dropdown.Popover>
        </Dropdown>
        <Tooltip delay={350} closeDelay={0}>
          <Button className={`send-button ${busy ? 'is-stopping' : ''}`} isIconOnly aria-label={busy ? '停止生成' : '发送消息'} aria-disabled={disabled || (!busy && !canSend)}
            onPressStart={capture} onPress={activate}><Icon name={busy ? 'stop' : 'up'} /></Button>
          <Tooltip.Content className="help-tooltip">{busy ? '停止生成' : editor.conflict ? '请先处理草稿冲突' : !canSend ? '输入消息后发送' : '发送消息 · Enter，Shift+Enter 换行'}</Tooltip.Content>
        </Tooltip>
      </div>
    </form>
  </div>;
}
