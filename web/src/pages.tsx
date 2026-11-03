import { Input, Label, TextField } from '@heroui/react';
import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import type { Attempt, Credential, Provider, Route, Shell } from '../../protocol/folio-v1';
import type { Bridge } from './bridge';
import { ActionButton, IconButton } from './controls';
import { Icon } from './icons';

export function Settings({ provider, edit, bridge }: { provider: Provider; edit: string; bridge: Bridge }) {
  const [endpoint, setEndpoint] = useState(provider.endpoint);
  const [model, setModel] = useState(provider.model);
  const [key, setKey] = useState('');
  const [keyAction, setKeyAction] = useState<'keep' | 'replace' | 'remove'>('keep');
  const [showKey, setShowKey] = useState(false);
  const [revision, setRevision] = useState(0);
  const [saving, setSaving] = useState<number | null>(null);
  const [problem, setProblem] = useState('');
  const baseProvider = useRef(provider.revision);
  const settled = useRef<number | null>(null);
  const change = (apply: () => void) => { apply(); setRevision(value => value + 1); setProblem(''); };
  useEffect(() => {
    if (revision === 0 && saving === null && provider.phase === 'ready') {
      setEndpoint(provider.endpoint); setModel(provider.model); baseProvider.current = provider.revision;
    }
  }, [provider, revision, saving]);
  useEffect(() => {
    if (provider.phase !== 'ready' || saving === null || provider.settledForm !== saving || settled.current === saving) return;
    settled.current = saving;
    baseProvider.current = provider.revision;
    setSaving(null);
    if (revision === saving) {
      setKey(''); setKeyAction('keep'); setShowKey(false);
      void bridge.command({ kind: 'route', to: 'chat' });
    }
  }, [provider, saving, revision, bridge]);
  const cancel = () => { setKey(''); setShowKey(false); void bridge.command({ kind: 'cancelSettings', edit }); };
  return <section className="aux-page settings-page" aria-label="连接设置">
    <p className="page-intro">设置在当前 IDE 的项目间共享。</p>
    <form autoComplete="off" onSubmit={event => {
      event.preventDefault();
      if (saving !== null && revision === saving) return;
      const credential: Credential = keyAction === 'replace' ? { kind: 'replace', value: key } : { kind: keyAction };
      if (keyAction === 'replace' && !key.trim()) { setProblem('请输入新 API Key，或选择保留。'); return; }
      setSaving(revision);
      void bridge.command({ kind: 'saveSettings', edit, providerRevision: baseProvider.current, formRevision: revision, endpoint, model, credential }).then(result => {
        if (result.kind === 'rejected') { setSaving(null); setProblem(result.message); }
      });
    }}>
      <TextField value={endpoint} onChange={value => change(() => setEndpoint(value))} className="settings-field"><Label>API 地址</Label><Input type="url" autoComplete="off" spellCheck={false} /></TextField>
      <TextField value={model} onChange={value => change(() => setModel(value))} className="settings-field"><Label>模型</Label><Input autoComplete="off" spellCheck={false} /></TextField>
      <TextField value={key} onChange={value => change(() => { setKey(value); setKeyAction(value ? 'replace' : 'keep'); })} className="settings-field"><Label>API Key</Label><div className="key-input"><Input type={showKey ? 'text' : 'password'} autoComplete="new-password" spellCheck={false} /><IconButton name={showKey ? '隐藏本次输入的 Key' : '显示本次输入的 Key'} icon="eye" onPress={() => setShowKey(value => !value)} /></div></TextField>
      <p className="muted key-status">{provider.key === 'bound' ? '已保存当前地址的 Key。' : provider.key === 'otherOrigin' ? '已有 Key 绑定其他地址，不会发送至新地址。' : '尚未保存 API Key。'}</p>
      <fieldset className="credential-actions"><legend>保存时处理 Key</legend>{([{ kind: 'keep', label: '保留' }, { kind: 'replace', label: '替换' }, { kind: 'remove', label: '移除' }] as const).map(action => <label key={action.kind}><input type="radio" name="credential" value={action.kind} checked={keyAction === action.kind} onChange={() => change(() => setKeyAction(action.kind))} />{action.label}</label>)}</fieldset>
      {keyAction === 'remove' && <p className="warning">保存后移除已保存的 Key。</p>}
      {provider.warning && <p className="warning">{provider.warning}</p>}
      {problem && <p className="failure" role="alert">{problem}</p>}
      <div className="form-actions"><ActionButton variant="secondary" onPress={cancel}>取消</ActionButton><ActionButton type="submit" isDisabled={provider.phase === 'loading' || (saving !== null && revision === saving)}>{saving !== null && revision === saving ? '正在保存…' : '保存并返回'}</ActionButton></div>
    </form>
  </section>;
}

const units = { pages: '页', slides: '张', sections: '片段' };
export function Files({ shell, bridge }: { shell: Shell; bridge: Bridge }) {
  const task = shell.fileTask;
  return <section className="aux-page files-page" aria-label="课件库">
    <p className="page-intro">课件库由当前 IDE 的全部项目共享。导入与重新索引会保留其他资料。</p>
    <ActionButton variant="secondary" isDisabled={task !== null} onPress={() => void bridge.command({ kind: 'chooseFiles' })}><Icon name="plus" />选择课件并导入</ActionButton>
    {task && <div className="file-progress" role="status"><span>{task.phase}</span><IconButton name="取消课件任务" icon="stop" disabled={!task.cancellable} reason="正在结算已提交的索引事务" onPress={() => void bridge.command({ kind: 'cancelFileTask', id: task.id })} /></div>}
    {shell.fileWarnings.length > 0 && <div className="warnings" role="status">{shell.fileWarnings.map((warning, index) => <p key={index}>{warning}</p>)}</div>}
    {shell.files.length === 0 ? <div className="empty-files"><Icon name="files" /><p>尚未导入课件</p><p className="muted">支持 PDF、PPTX 和文本资料。</p></div> : <ul className="file-list">{shell.files.map(file => <li key={file.id}>
      <div className="file-main"><button className="source-title" onClick={() => void bridge.command({ kind: 'openSource', id: file.id, version: file.version })}>{file.title}</button><p className="muted source-location">{file.location}</p><p className="muted">{file.count} {units[file.unit]}</p></div>
      <div className="file-actions"><IconButton name={`重新索引 ${file.title}`} icon="retry" disabled={task !== null} reason="请等待当前课件任务结束" onPress={() => void bridge.command({ kind: 'reindex', id: file.id, version: file.version })} /><IconButton name={`移除 ${file.title}`} icon="remove" disabled={task !== null} reason="请等待当前课件任务结束" onPress={() => void bridge.command({ kind: 'removeSource', id: file.id, version: file.version })} /></div>
    </li>)}</ul>}
    <p className="muted">移除前将确认全部项目的影响，原始文件会保留。</p>
  </section>;
}

export function TestPreview({ at, tab, bridge }: { at: Attempt; tab: 'code' | 'raw'; bridge: Bridge }) {
  const card = useSyncExternalStore(listener => bridge.subscribeCard(at.turn, listener), () => bridge.getCard(at.turn));
  if (!card || card.at.number !== at.number || card.body.kind !== 'test') return <section className="aux-page"><p>此测试预览已失效。请返回对话查看当前结果。</p></section>;
  const body = card.body;
  return <section className="aux-page test-preview" aria-label="测试预览">
    <div className="capture-summary"><span>捕获来源</span><div>{body.source || '未提供'}</div><span>捕获目标</span><div>{body.target || '未找到测试目录'}</div></div>
    <div className="preview-tabs" role="group" aria-label="预览内容"><ActionButton aria-pressed={tab === 'code'} variant="secondary" onPress={() => void bridge.command({ kind: 'previewTest', at, tab: 'code' })}>Java 预览</ActionButton><ActionButton aria-pressed={tab === 'raw'} variant="secondary" onPress={() => void bridge.command({ kind: 'previewTest', at, tab: 'raw' })}>原始回复</ActionButton></div>
    {body.problem && <p className="warning">{body.problem}</p>}
    <div role="region" aria-label={tab === 'code' ? 'Java 预览' : '原始回复'}><pre className="preview-code">{tab === 'raw' ? body.raw : body.code ?? '无法解析为有效测试，请查看原始回复。'}</pre></div>
    <div className="preview-actions"><IconButton name={tab === 'code' ? '复制测试代码' : '复制原始回复'} icon="copy" disabled={tab === 'code' && body.code === null} reason="没有有效的测试代码" onPress={() => void bridge.command({ kind: 'copy', at, part: tab === 'code' ? 'code' : 'answer', index: 0 })} />
      <ActionButton isDisabled={!card.writeTest.enabled} onPress={() => void bridge.command({ kind: 'writeTest', at })}>写入文件</ActionButton></div>
    {!card.writeTest.enabled && <p className="muted">{card.writeTest.reason}</p>}
    <p className="muted">仅经 PSI 语法与 JUnit 识别检查，未编译或运行。写入前会重新检查原始来源、目标目录和同名文件，并可在 IDE 中撤销。</p>
  </section>;
}

export function SourcePreview({ route }: { route: Extract<Route, { kind: 'sourcePreview' }> }) {
  return <section className="aux-page source-preview" aria-label="来源摘录"><h2>{route.title}</h2><p className="muted source-location">{route.location}</p><pre className="excerpt">{route.excerpt}</pre></section>;
}
