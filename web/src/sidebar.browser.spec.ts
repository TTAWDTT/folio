import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { test, expect, answer, draft } from '../e2e/host-fixture';
import type { Mode } from '../../protocol/folio-v1';

const names: Record<Mode, string> = { ask: '课程问答', drill: '课程练习', tests: '生成测试', commit: '提交信息' };

for (const width of [280, 360]) for (const dark of [true, false]) {
  test(`approved hierarchy and independent indicator ${width} ${dark ? 'dark' : 'light'}`, async ({ page, host }) => {
    await page.setViewportSize({ width, height: 760 }); host.shell.theme.dark = dark;
    host.cards = [answer('alignment', '这里的 `user` 可能为 `null`，直接调用它的方法就会触发异常。\n\n可以先处理缺失值，再继续访问：\n\n```java\nif (user == null) {\n    return Optional.empty();\n}\nreturn Optional.of(user.getName());\n```')];
    await host.open();
    const measurements = await page.locator('.turn').evaluate(article => {
      const user = article.querySelector('.user-message')!; const answer = article.querySelector('.answer')!;
      const rect = user.getBoundingClientRect();
      return { gap: answer.getBoundingClientRect().top - rect.bottom, weight: getComputedStyle(answer.querySelector('p')!).fontWeight, utilityHeight: document.querySelector('.utility-row')!.getBoundingClientRect().height };
    });
    expect(measurements.gap).toBeGreaterThanOrEqual(20); expect(measurements.gap).toBeLessThanOrEqual(28);
    expect(measurements.weight).toBe('400'); expect(measurements.utilityHeight).toBe(28);
    expect(await page.getByRole('button', { name: '选择模式', exact: true }).count()).toBe(1);
    const evidence = process.env.FOLIO_WEB_EVIDENCE;
    if (evidence) { await mkdir(evidence, { recursive: true }); await page.screenshot({ path: path.join(evidence, `chat-${width}-${dark ? 'dark' : 'light'}.png`) }); }
    await page.getByRole('button', { name: '选择模式', exact: true }).click();
    const row = page.getByRole('menuitemradio', { name: '课程问答', exact: true });
    const alignment = await row.evaluate(element => {
      const icon = element.querySelector('svg')!.getBoundingClientRect(); const indicator = element.querySelector('.menu-item__indicator')!.getBoundingClientRect();
      return { iconRight: icon.right, indicatorLeft: indicator.left, rowRight: element.getBoundingClientRect().right };
    });
    expect(alignment.indicatorLeft).toBeGreaterThan(alignment.iconRight + 80);
    expect(alignment.indicatorLeft).toBeLessThan(alignment.rowRight);
    if (evidence) await page.screenshot({ path: path.join(evidence, `menu-${width}-${dark ? 'dark' : 'light'}.png`) });
  });
}

test('stage control sends one stage command', async ({ page, host }) => {
  await host.open();
  await page.getByRole('button', { name: '暂存', exact: true }).click();
  await expect.poll(() => host.commands.filter(command => command.kind === 'stage')).toEqual([{ kind: 'stage' }]);
});

test('all four drafts retain their own caret and files return keeps the editor node', async ({ page, host }) => {
  await host.open(); const input = page.getByRole('textbox', { name: '消息', exact: true });
  for (const mode of ['ask', 'drill', 'tests', 'commit'] as const) {
    if (mode !== 'ask') { await page.getByRole('button', { name: '选择模式', exact: true }).click(); await page.getByRole('menuitemradio', { name: names[mode], exact: true }).click(); }
    await input.fill(`${mode} independent draft`);
    await input.evaluate(element => { const field = element as HTMLTextAreaElement; field.setSelectionRange(1, 3, 'backward'); field.dispatchEvent(new Event('select', { bubbles: true })); });
  }
  for (const mode of ['ask', 'drill', 'tests', 'commit'] as const) {
    await page.getByRole('button', { name: '选择模式', exact: true }).click(); await page.getByRole('menuitemradio', { name: names[mode], exact: true }).click();
    await expect(input).toHaveValue(`${mode} independent draft`);
    expect(await input.evaluate(element => { const field = element as HTMLTextAreaElement; return [field.selectionStart, field.selectionEnd, field.selectionDirection]; })).toEqual([1, 3, 'backward']);
  }
  const editor = await input.elementHandle();
  await page.getByRole('button', { name: '课件', exact: true }).click(); await page.getByRole('button', { name: '返回对话', exact: true }).click();
  expect(await editor!.evaluate(element => element.isConnected)).toBe(true); await expect(input).toHaveValue('commit independent draft');
  expect(host.commands.some(command => command.kind === 'send')).toBe(false);
});

test('references start collapsed and only a source title navigates', async ({ page, host }) => {
  const card = answer(); card.body = { kind: 'answer', raw: '参考课件回答。', evidence: 'RETRIEVED', citations: [{ number: 1, title: 'Lesson.pdf', location: '第 2 页', excerpt: '这段摘录可以选择，不会触发来源跳转。' }] }; host.cards = [card];
  await host.open(); await expect(page.getByText('这段摘录可以选择，不会触发来源跳转。', { exact: true })).toBeHidden();
  await page.getByRole('button', { name: '课件 · 1 处引用', exact: true }).click();
  await page.getByText('这段摘录可以选择，不会触发来源跳转。', { exact: true }).click();
  expect(host.commands.some(command => command.kind === 'citation')).toBe(false);
  await page.getByRole('button', { name: '[1] Lesson.pdf', exact: true }).click();
  await expect.poll(() => host.commands.some(command => command.kind === 'citation' && command.index === 0 && command.at.turn === 't-1')).toBe(true);
});

test('raw fence buttons retain Java indexes across eight separators and never use DOM code text', async ({ page, host }) => {
  const separators = ['\n', '\r\n', '\r', '\v', '\f', '\u0085', '\u2028', '\u2029'];
  host.cards = separators.map((separator, index) => answer(`fence-${index}`, `Intro${separator}\`\`\`java${separator}first${separator}\`\`\`nested${separator}second${separator}\`\`\` \t${separator}Between${separator}\`\`\`${separator}third${separator}\`\`\`${separator}\`\`\`unfinished${separator}tail`));
  await host.open();
  for (let index = 0; index < separators.length; index++) {
    const article = page.locator(`[data-turn="fence-${index}"]`);
    const buttons = article.getByRole('button', { name: '复制代码', exact: true }); await expect(buttons).toHaveCount(2);
    await buttons.nth(0).click(); await buttons.nth(1).click();
    await expect(article.getByRole('button', { name: '根据此代码生成测试', exact: true })).toHaveCount(0);
    await expect(article.locator('.unclosed-fence')).toContainText('```unfinished');
  }
  await expect.poll(() => host.commands.filter(command => command.kind === 'copy' && command.part === 'code').length).toBe(16);
  for (let index = 0; index < separators.length; index++) {
    const copied = host.commands.filter(command => command.kind === 'copy' && command.part === 'code' && command.at.turn === `fence-${index}`);
    expect(copied.map(command => command.kind === 'copy' ? command.index : -1)).toEqual([0, 1]);
  }
  expect(host.commands.filter(command => command.kind === 'generateTests')).toEqual([]);
});

test('whole-answer test action follows shell busy state without replacing the retained card', async ({ page, host }) => {
  const card = answer('original', 'A complete answer with no code fence.');
  card.generateTests = { enabled: true, reason: '' }; host.cards = [card]; await host.open();
  const generate = page.getByRole('button', { name: '生成测试', exact: true });
  await expect(generate).toHaveAttribute('aria-disabled', 'false');
  host.shell.generation = { turn: 'other', number: 1 }; await host.publish();
  await expect(generate).toHaveAttribute('aria-disabled', 'true');
  expect(host.commands.filter(command => command.kind === 'generateTests')).toEqual([]);
  await page.mouse.move(0, 0); await generate.hover();
  await expect(page.getByRole('tooltip')).toHaveText('生成测试 · 请先停止当前生成');
  host.shell.generation = null; await host.publish(); await generate.click();
  await expect.poll(() => host.commands.filter(command => command.kind === 'generateTests')).toEqual([{ kind: 'generateTests', at: card.at }]);
  card.generateTests = { enabled: false, reason: '原始源码不可用' }; await host.card(card);
  await expect(generate).toHaveAttribute('aria-disabled', 'true');
  expect(host.commands.filter(command => command.kind === 'generateTests')).toHaveLength(1);
});

test('untrusted HTML, image URLs and link schemes cannot navigate or execute', async ({ page, host, baseURL }) => {
  host.cards = [answer('untrusted', '<script>window.compromised=true</script>\n<img src="https://remote.invalid/pixel" onerror="window.compromised=true">\n\n![tracking](https://remote.invalid/pixel)\n\n[bad](javascript:alert(1))\n\n```html\n<img src=x onerror=alert(1)>\n```')];
  const external: string[] = []; page.on('request', request => { if (new URL(request.url()).origin !== new URL(baseURL!).origin) external.push(request.url()); });
  await host.open(); await expect(page.locator('img')).toHaveCount(0); await expect(page.locator('.answer a')).toHaveCount(0);
  expect(await page.evaluate(() => Object.hasOwn(window, 'compromised'))).toBe(false); expect(external).toEqual([]);
});

test('native context replacement keeps an unacknowledged buffer until explicit discard', async ({ page, host }) => {
  await host.open();
  await page.evaluate(() => { const transport = window.folioHost!; const original = transport.postMessage; transport.postMessage = json => { const message = JSON.parse(json); if (message.kind !== 'command' || message.command.kind !== 'edit') original(json); }; });
  const input = page.getByRole('textbox', { name: '消息', exact: true }); await input.fill('我的未确认草稿');
  host.shell.drafts.ask = { ...draft('来自 IDE 的新问题'), epoch: 2, revision: 8, context: 'Captured.java' }; await host.publish();
  await expect(input).toHaveValue('我的未确认草稿'); await expect(page.getByRole('alert')).toContainText('已保留');
  await page.getByRole('button', { name: '使用 IDE 草稿', exact: true }).click(); await expect(input).toHaveValue('来自 IDE 的新问题');
  expect(host.commands.some(command => command.kind === 'send')).toBe(false);
});

test('reading above the tail survives updates and auxiliary routes until return to latest', async ({ page, host }) => {
  host.cards = Array.from({ length: 10 }, (_, index) => answer(`reading-${index}`, `第${index}段。` + '阅读内容。'.repeat(40)));
  await host.open(); const log = page.getByRole('log', { name: '对话记录', exact: true });
  await log.evaluate(element => { element.scrollTop = 120; element.dispatchEvent(new Event('scroll')); });
  await page.getByRole('button', { name: '设置', exact: true }).click(); await page.getByRole('button', { name: '取消', exact: true }).click();
  expect(await log.evaluate(element => element.scrollTop)).toBeCloseTo(120, 0);
  await host.card(answer('new-tail', '新的尾部内容。'.repeat(20)));
  expect(await log.evaluate(element => element.scrollTop)).toBeCloseTo(120, 0);
  await page.getByRole('button', { name: /回到最新/ }).click();
  expect(await log.evaluate(element => element.scrollHeight - element.clientHeight - element.scrollTop)).toBeLessThanOrEqual(1);
});

test('library warnings, task cancellation and Java confirmation stay reachable without automatic changes', async ({ page, host }) => {
  host.shell.files = [{ id: 'source-1', version: 4, title: 'Shared.pdf', location: '/shared/Shared.pdf', count: 8, unit: 'pages' }];
  host.shell.fileWarnings = ['其中一页未能识别文本']; await host.open();
  await page.getByRole('button', { name: '课件', exact: true }).click(); await expect(page.getByText('其中一页未能识别文本', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '移除 Shared.pdf', exact: true }).click();
  await expect.poll(() => host.commands.some(command => command.kind === 'removeSource' && command.id === 'source-1' && command.version === 4)).toBe(true);
  host.shell.confirm = { id: 'confirm-remove', text: '从全部项目的共享课件库移除此资料？原始文件保留。' }; await host.publish();
  await expect(page.getByRole('dialog', { name: '确认操作' })).toBeVisible(); await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect.poll(() => host.commands.some(command => command.kind === 'confirm' && command.id === 'confirm-remove' && !command.accept)).toBe(true);
  host.shell.confirm = null; host.shell.fileTask = { id: 'files-9', phase: '正在建立索引', cancellable: true }; await host.publish();
  await page.getByRole('button', { name: '取消课件任务', exact: true }).click();
  await expect.poll(() => host.commands.some(command => command.kind === 'cancelFileTask' && command.id === 'files-9')).toBe(true);
});

test('credential removal remains an explicit save and cancellation clears the local form', async ({ page, host }) => {
  await host.open(); await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.getByRole('radio', { name: '移除', exact: true }).check(); expect(host.commands.some(command => command.kind === 'saveSettings')).toBe(false);
  await page.getByRole('button', { name: '保存并返回', exact: true }).click();
  await expect.poll(() => host.commands.some(command => command.kind === 'saveSettings' && command.credential.kind === 'remove')).toBe(true);
  await page.getByLabel('API Key', { exact: true }).fill('discarded-synthetic-key');
  await page.getByRole('button', { name: '返回对话', exact: true }).click(); await page.getByRole('button', { name: '设置', exact: true }).click();
  await expect(page.getByLabel('API Key', { exact: true })).toHaveValue('');
  expect(await page.evaluate(() => localStorage.length)).toBe(0);
});

test('host font scale and reduced motion apply to the actual DOM', async ({ page, host }) => {
  host.shell.theme = { dark: false, reducedMotion: true, font: 'monospace', fontPx: 15, scale: 1.2 }; await host.open();
  const style = await page.getByRole('textbox', { name: '消息', exact: true }).evaluate(element => ({ size: getComputedStyle(element).fontSize, family: getComputedStyle(element).fontFamily, transition: getComputedStyle(element).transitionDuration, reduced: document.documentElement.dataset.reducedMotion }));
  expect(style.size).toBe('20.4px'); expect(style.family).toBe('monospace'); expect(style.transition).toBe('0s'); expect(style.reduced).toBe('true');
});

test('disabled retry explains its reason and never admits another generation', async ({ page, host }) => {
  const card = answer(); host.cards = [card]; host.shell.generation = { turn: 'another-turn', number: 1 }; await host.open();
  const retry = page.getByRole('button', { name: '重新生成', exact: true }); await expect(retry).toHaveAttribute('aria-disabled', 'true');
  const rect = (await retry.boundingBox())!; await page.mouse.move(200, 400); await page.mouse.move(rect.x + 13, rect.y + 13, { steps: 12 });
  await expect(page.getByRole('tooltip')).toContainText('请先停止当前生成');
  await retry.dispatchEvent('click'); expect(host.commands.some(command => command.kind === 'retry')).toBe(false);
});

test('a preview reacts to acceptance invalidation without needing a whole shell update', async ({ page, host }) => {
  const card = answer('preview-live'); card.mode = 'tests'; card.body = { kind: 'test', raw: 'class ExampleTest {}', code: 'class ExampleTest {}', source: 'Example.java', target: 'ExampleTest.java', problem: null }; card.writeTest = { enabled: true, reason: '' }; host.cards = [card]; await host.open();
  await page.getByRole('button', { name: '查看测试预览', exact: true }).click(); await expect(page.getByRole('button', { name: '写入文件', exact: true })).toBeEnabled();
  card.writeTest = { enabled: false, reason: '来源文件已修改，请重新生成。' }; await host.card(card);
  await expect(page.getByRole('button', { name: '写入文件', exact: true })).toBeDisabled(); await expect(page.getByText('来源文件已修改，请重新生成。', { exact: true })).toBeVisible();
});

test('an auxiliary route preserves selected answer text through terminal replacement', async ({ page, host }) => {
  const card = answer('selection-route'); card.body = { kind: 'stream', phase: '生成中', text: '选择保留的原始文字，仍在生成。' }; host.cards = [card]; host.shell.generation = card.at; await host.open();
  await page.getByText('选择保留的原始文字，仍在生成。', { exact: true }).evaluate(element => {
    const node = document.createTreeWalker(element, NodeFilter.SHOW_TEXT).nextNode()!; const range = document.createRange(); range.setStart(node, 0); range.setEnd(node, 9); const selection = getSelection()!; selection.removeAllRanges(); selection.addRange(range);
  });
  const selected = await page.evaluate(() => getSelection()!.toString());
  host.shell.route = { kind: 'files' }; await host.publish();
  host.shell.generation = null; await host.card(answer('selection-route', '选择保留的原始文字，已完成。'));
  host.shell.route = { kind: 'chat' }; await host.publish();
  await expect.poll(() => page.evaluate(() => getSelection()!.toString())).toBe(selected);
});

test('keyboard help remains available on disabled icons and Stop keeps its pressed attempt', async ({ page, host }) => {
  const card = answer('keyboard-stop'); card.body = { kind: 'stream', phase: '生成中', text: '部分内容' }; host.cards = [card]; host.shell.generation = card.at; host.shell.drafts.ask = draft('下一条内容'); await host.open();
  for (let index = 0; index < 12; index++) { await page.keyboard.press('Tab'); if (await page.evaluate(() => document.activeElement?.getAttribute('aria-label') === '重新生成')) break; }
  await expect(page.getByRole('tooltip')).toContainText('请先停止当前生成');
  for (let index = 0; index < 6; index++) { await page.keyboard.press('Tab'); if (await page.evaluate(() => document.activeElement?.getAttribute('aria-label') === '停止生成')) break; }
  expect(await page.evaluate(() => document.activeElement?.getAttribute('aria-label'))).toBe('停止生成');
  await page.keyboard.down('Space'); host.shell.generation = null; await host.card(answer('keyboard-stop')); await host.publish(); await page.keyboard.up('Space');
  await expect.poll(() => host.commands.filter(command => command.kind === 'stop' && command.at.turn === 'keyboard-stop' && command.at.number === 1).length).toBe(1);
  expect(host.commands.some(command => command.kind === 'send')).toBe(false);
});

test('late provider loading fills a pristine settings form but preserves a changed form', async ({ page, host }) => {
  host.shell.provider.phase = 'loading'; host.shell.provider.endpoint = ''; host.shell.provider.model = ''; await host.open();
  await page.getByRole('button', { name: '设置', exact: true }).click();
  host.shell.provider = { ...host.shell.provider, phase: 'ready', endpoint: 'https://loaded.invalid/v1', model: 'loaded-model' }; await host.publish();
  await expect(page.getByLabel('API 地址', { exact: true })).toHaveValue('https://loaded.invalid/v1');
  await page.getByLabel('模型', { exact: true }).fill('local-model');
  host.shell.provider = { ...host.shell.provider, revision: 1, model: 'external-model' }; await host.publish();
  await expect(page.getByLabel('模型', { exact: true })).toHaveValue('local-model');
  expect(host.commands.some(command => command.kind === 'saveSettings')).toBe(false);
});
