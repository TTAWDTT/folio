import { test, expect, answer, draft } from './host-fixture';

for (const width of [280, 360]) {
  for (const dark of [true, false]) {
    test(`compact layout ${width}px ${dark ? 'dark' : 'light'}`, async ({ page, host }) => {
      await page.setViewportSize({ width, height: 760 });
      host.shell.theme.dark = dark;
      await host.open();
      const controls = ['新对话', '课件', '设置'].map(name => page.getByRole('button', { name, exact: true }));
      const boxes = await Promise.all(controls.map(control => control.boundingBox()));
      expect(boxes.every(box => box !== null)).toBe(true);
      const centers = boxes.map(box => box!.y + box!.height / 2);
      expect(Math.max(...centers) - Math.min(...centers)).toBeLessThanOrEqual(1);
      const composer = page.getByRole('form', { name: '消息输入' });
      const before = await composer.boundingBox();
      expect(before!.x).toBeGreaterThanOrEqual(0);
      expect(before!.x + before!.width).toBeLessThanOrEqual(width);
      expect(before!.y + before!.height).toBeLessThanOrEqual(760);
      await page.getByRole('button', { name: '选择模式', exact: true }).click();
      const menu = page.getByRole('menu');
      await expect(menu).toBeVisible();
      const popup = await menu.boundingBox();
      expect(popup!.y + popup!.height).toBeLessThanOrEqual(before!.y + 1);
      expect(await composer.boundingBox()).toEqual(before);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    });
  }
}

test('mode trigger toggles once and dismissal stays closed', async ({ page, host }) => {
  await host.open();
  const trigger = page.getByRole('button', { name: '选择模式', exact: true });
  const box = (await trigger.boundingBox())!;
  await trigger.click();
  await expect(page.getByRole('menu')).toBeVisible();
  await expect(page.getByRole('menuitemradio')).toHaveText(['课程问答', '课程练习', '生成测试', '提交信息']);
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  await expect(page.getByRole('menu')).toBeHidden();
  await page.waitForTimeout(350);
  await expect(page.getByRole('menu')).toBeHidden();
  await trigger.click();
  await page.mouse.click(2, 100);
  await expect(page.getByRole('menu')).toBeHidden();
  await trigger.click();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('menu')).toBeHidden();
  await trigger.click();
  await page.getByRole('menuitemradio', { name: '课程问答', exact: true }).click();
  await expect(page.getByRole('menu')).toBeHidden();
});

test('mode drafts and settings cancellation preserve input without saving', async ({ page, host }) => {
  await host.open();
  const input = page.getByRole('textbox', { name: '消息', exact: true });
  await input.fill('未发送的问答');
  await input.evaluate(element => (element as HTMLTextAreaElement).setSelectionRange(2, 4, 'backward'));
  await input.dispatchEvent('select');
  await page.getByRole('button', { name: '选择模式', exact: true }).click();
  await page.getByRole('menuitemradio', { name: '课程练习', exact: true }).click();
  await input.fill('练习草稿');
  await page.getByRole('button', { name: '选择模式', exact: true }).click();
  await page.getByRole('menuitemradio', { name: '课程问答', exact: true }).click();
  await expect(input).toHaveValue('未发送的问答');
  expect(await input.evaluate(element => {
    const field = element as HTMLTextAreaElement;
    return [field.selectionStart, field.selectionEnd, field.selectionDirection];
  })).toEqual([2, 4, 'backward']);
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.getByLabel('API Key', { exact: true }).fill('new-private-key');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect(input).toHaveValue('未发送的问答');
  expect(await input.evaluate(element => {
    const field = element as HTMLTextAreaElement;
    return [field.selectionStart, field.selectionEnd, field.selectionDirection];
  })).toEqual([2, 4, 'backward']);
  expect(host.commands.some(command => command.kind === 'saveSettings' || command.kind === 'send')).toBe(false);
});

test('answer tools are icons with hover text and do not shift content', async ({ page, host }) => {
  host.cards = [answer()];
  await host.open();
  const copy = page.getByRole('button', { name: '复制回答', exact: true });
  const retry = page.getByRole('button', { name: '重新生成', exact: true });
  expect((await copy.innerText()).trim()).toBe('');
  expect((await retry.innerText()).trim()).toBe('');
  const box = await retry.boundingBox();
  const copyBox = (await copy.boundingBox())!;
  await page.mouse.move(2, copyBox.y + copyBox.height / 2);
  await page.mouse.move(copyBox.x + copyBox.width / 2, copyBox.y + copyBox.height / 2, { steps: 12 });
  await expect(page.getByRole('tooltip')).toContainText('复制回答');
  expect(await retry.boundingBox()).toEqual(box);
  await copy.click();
  await expect.poll(() => host.commands.some(command => command.kind === 'copy' && command.part === 'answer' && command.at.turn === 't-1')).toBe(true);
});

test('stop pressed before completion cannot release as send', async ({ page, host }) => {
  const card = answer();
  card.body = { kind: 'stream', phase: '正在生成', text: '部分回答' };
  host.cards = [card];
  host.shell.generation = card.at;
  host.shell.drafts.ask = draft('下一条草稿');
  await host.open();
  const stop = page.getByRole('button', { name: '停止生成', exact: true });
  const box = (await stop.boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
  await page.mouse.down();
  host.shell.generation = null;
  await host.card(answer());
  await host.publish();
  await page.mouse.up();
  await page.waitForTimeout(150);
  expect(host.commands.filter(command => command.kind === 'send')).toHaveLength(0);
  await expect(page.getByRole('textbox', { name: '消息', exact: true })).toHaveValue('下一条草稿');
});

test('retry addresses the original row and preserves next draft', async ({ page, host }) => {
  host.cards = [answer('old-turn')];
  host.shell.drafts.ask = draft('继续编辑');
  await host.open();
  await page.getByRole('button', { name: '重新生成', exact: true }).click();
  await expect.poll(() => host.commands.some(command => command.kind === 'retry' && command.at.turn === 'old-turn' && command.at.number === 1)).toBe(true);
  await expect(page.getByRole('textbox', { name: '消息', exact: true })).toHaveValue('继续编辑');
  await expect(page.getByText('示例问题', { exact: true })).toHaveCount(1);
});

test('a completed answer without code fences keeps its captured-source test action', async ({ page, host }) => {
  const card = { ...answer('source-answer', '这里应该补充空值边界测试。'), generateTests: { enabled: true, reason: '' } };
  host.cards = [card];
  await host.open();
  const generate = page.getByRole('button', { name: '生成测试', exact: true });
  await expect(generate).toBeVisible();
  await generate.click();
  await expect.poll(() => host.commands.find(command => command.kind === 'generateTests')).toEqual({ kind: 'generateTests', at: card.at });
});

test('practice explanations are independent and untrusted HTML stays text', async ({ page, host }) => {
  const card = answer();
  card.mode = 'drill';
  card.body = { kind: 'practice', raw: '<img src=x onerror="window.pwned=true">', evidence: 'GENERAL', citations: [], exercises: [1, 2, 3].map(number => ({ question: `题目 ${number}`, explanation: `解析 ${number}` })) };
  host.cards = [card];
  await host.open();
  await expect(page.getByText('题目 1', { exact: true })).toBeVisible();
  await expect(page.getByText('题目 2', { exact: true })).toBeVisible();
  await expect(page.getByText('题目 3', { exact: true })).toBeVisible();
  const explanations = page.getByRole('button', { name: '查看解析', exact: true });
  await explanations.nth(0).click();
  await expect(page.getByText('解析 1', { exact: true })).toBeVisible();
  await expect(page.getByText('解析 2', { exact: true })).toBeHidden();
  await expect(page.locator('img')).toHaveCount(0);
});

test('IME confirmation never sends and multiline input stays within six rows', async ({ page, host }) => {
  await page.setViewportSize({ width: 280, height: 760 });
  await host.open();
  const input = page.getByRole('textbox', { name: '消息', exact: true });
  await input.fill('中文输入');
  await input.dispatchEvent('compositionstart', { data: '中文' });
  await input.dispatchEvent('keydown', { key: 'Enter', code: 'Enter', isComposing: true });
  expect(host.commands.some(command => command.kind === 'send')).toBe(false);
  await input.dispatchEvent('compositionend', { data: '中文' });
  await input.fill(Array.from({ length: 15 }, (_, i) => `第 ${i + 1} 行`).join('\n'));
  const geometry = await input.evaluate(element => {
    const field = element as HTMLTextAreaElement;
    const style = getComputedStyle(field);
    return { height: field.clientHeight, line: parseFloat(style.lineHeight), padding: parseFloat(style.paddingTop) + parseFloat(style.paddingBottom), scroll: field.scrollHeight };
  });
  expect(geometry.height).toBeLessThanOrEqual(geometry.line * 6 + geometry.padding + 2);
  expect(geometry.scroll).toBeGreaterThan(geometry.height);
  await expect(page.getByRole('button', { name: '发送消息', exact: true })).toBeInViewport();
});

test('stream updates preserve a selected substring and reader position', async ({ page, host }) => {
  host.cards = Array.from({ length: 12 }, (_, i) => answer(`history-${i}`, `第 ${i} 条。` + '可阅读的内容。'.repeat(30)));
  const streaming = answer('stream-turn');
  streaming.body = { kind: 'stream', phase: '正在生成', text: '保持这段选中文字，后面正在生成。' };
  host.cards.push(streaming);
  host.shell.generation = streaming.at;
  await host.open();
  const log = page.getByRole('log', { name: '对话记录', exact: true });
  await log.evaluate(element => { element.scrollTop = element.scrollHeight; });
  const text = page.getByText('保持这段选中文字，后面正在生成。', { exact: true });
  await text.evaluate(element => {
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
    const node = walker.nextNode();
    if (!node) throw new Error('Selectable answer text is missing');
    const range = document.createRange();
    range.setStart(node, 0); range.setEnd(node, 9);
    const selection = getSelection()!;
    selection.removeAllRanges(); selection.addRange(range);
  });
  const selected = await page.evaluate(() => getSelection()!.toString());
  expect(selected).toBe('保持这段选中文字，');
  const before = await log.evaluate(element => element.scrollTop);
  await host.send({ kind: 'text', at: streaming.at, offset: streaming.body.text.length, text: '新增内容。'.repeat(100) });
  await expect.poll(() => page.evaluate(() => getSelection()!.toString())).toBe(selected);
  expect(await log.evaluate(element => element.scrollTop)).toBeCloseTo(before, 0);
  host.shell.generation = null;
  await host.card(answer('stream-turn', '保持这段选中文字，后面正在生成。最终回答。'));
  await host.publish();
  expect(await page.evaluate(() => getSelection()!.toString())).toBe(selected);
});

test('late settings save does not erase a newer local key edit', async ({ page, host }) => {
  await host.open();
  await page.getByRole('button', { name: '设置', exact: true }).click();
  const key = page.getByLabel('API Key', { exact: true });
  await expect(key).toHaveValue('');
  await key.fill('first-synthetic-key');
  await page.getByRole('button', { name: '保存并返回', exact: true }).click();
  await expect.poll(() => host.commands.filter(command => command.kind === 'saveSettings').length).toBe(1);
  const saved = host.commands.find(command => command.kind === 'saveSettings');
  if (!saved || saved.kind !== 'saveSettings') throw new Error('Save command is missing');
  expect(saved.credential).toEqual({ kind: 'replace', value: 'first-synthetic-key' });
  await key.fill('newer-synthetic-key');
  host.shell.provider = { ...host.shell.provider, revision: 1, phase: 'ready', settledForm: saved.formRevision };
  await host.publish();
  await expect(key).toHaveValue('newer-synthetic-key');
  expect(JSON.stringify(host.shell)).not.toContain('synthetic-key');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  expect(host.commands.filter(command => command.kind === 'saveSettings')).toHaveLength(1);
});

test('preview and commit candidates require explicit protected actions', async ({ page, host }) => {
  const preview = answer('test-turn');
  preview.mode = 'tests';
  preview.body = { kind: 'test', raw: '```java\r\nclass SampleTest {}\r\n```\r\n', code: 'class SampleTest {}\r\n', source: 'Sample.java', target: 'SampleTest.java', problem: null };
  preview.writeTest = { enabled: true, reason: '' };
  const commit = answer('commit-turn');
  commit.mode = 'commit';
  commit.body = { kind: 'commit', candidates: ['feat: sample'], diff: 'sample staged diff', chars: 18, batches: 1, calls: 1 };
  host.cards = [preview, commit];
  await host.open();
  expect(host.commands.some(command => command.kind === 'writeTest' || command.kind === 'deliverCommit')).toBe(false);
  await page.getByRole('button', { name: '复制提交信息', exact: true }).click();
  await expect.poll(() => host.commands.some(command => command.kind === 'deliverCommit' && command.at.turn === 'commit-turn' && command.index === 0 && command.delivery === 'copy')).toBe(true);
  await page.getByRole('button', { name: '查看测试预览', exact: true }).click();
  await expect(page.getByText('SampleTest.java', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: '写入文件', exact: true })).toBeVisible();
  expect(host.commands.some(command => command.kind === 'writeTest')).toBe(false);
  await page.getByRole('button', { name: '写入文件', exact: true }).click();
  await expect.poll(() => host.commands.some(command => command.kind === 'writeTest' && command.at.turn === 'test-turn')).toBe(true);
});
