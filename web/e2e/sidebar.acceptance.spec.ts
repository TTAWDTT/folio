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
  await trigger.click();
  await expect(page.getByRole('menu')).toBeVisible();
  await expect(page.getByRole('menuitemradio')).toHaveText(['课程问答', '课程练习', '生成测试', '提交信息']);
  await trigger.click();
  await expect(page.getByRole('menu')).toBeHidden();
  await page.waitForTimeout(350);
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
  await page.getByRole('button', { name: '选择模式', exact: true }).click();
  await page.getByRole('menuitemradio', { name: '课程练习', exact: true }).click();
  await input.fill('练习草稿');
  await page.getByRole('button', { name: '选择模式', exact: true }).click();
  await page.getByRole('menuitemradio', { name: '课程问答', exact: true }).click();
  await expect(input).toHaveValue('未发送的问答');
  await page.getByRole('button', { name: '设置', exact: true }).click();
  await page.getByLabel('API Key', { exact: true }).fill('new-private-key');
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await expect(input).toHaveValue('未发送的问答');
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
  await copy.hover();
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
