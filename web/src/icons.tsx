import type { Mode } from '../../protocol/folio-v1';

const paths = {
  plus: 'M8 2v12M2 8h12',
  files: 'M1.5 4.5V3A1.5 1.5 0 0 1 3 1.5h3L7.5 3H13a1.5 1.5 0 0 1 1.5 1.5v8A1.5 1.5 0 0 1 13 14H3a1.5 1.5 0 0 1-1.5-1.5Z',
  settings: 'm6.5 1-.6 1.7-1.5.9-1.8-.3-1.5 2.5L2.3 7v2l-1.2 1.2 1.5 2.5 1.8-.3 1.5.9.6 1.7h3l.6-1.7 1.5-.9 1.8.3 1.5-2.5L13.7 9V7l1.2-1.2-1.5-2.5-1.8.3-1.5-.9L9.5 1ZM10.5 8a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0Z',
  back: 'm9.5 3-5 5 5 5M5 8h9',
  copy: 'M5.5 5.5H13V14H5.5ZM10.5 3V1.5H2V10h1.5',
  retry: 'M13.5 5.5A6 6 0 1 0 14 9M13.5 1v4.5H9',
  chevron: 'm4 6 4 4 4-4',
  up: 'M8 13V3m-5 5 5-5 5 5',
  stop: 'M4 4h8v8H4Z',
  ask: 'M2 2.5h12V11H6l-4 3Z',
  drill: 'm2 11 8.5-8.5 3 3L5 14H2Zm7-7 3 3',
  tests: 'm5.5 4-4 4 4 4m5-8 4 4-4 4',
  commit: 'M4 4v8m0-6h5a3 3 0 0 1 3 3v3M2.5 1.5h3v3h-3Zm0 10h3v3h-3Zm8 0h3v3h-3Z',
  close: 'm4 4 8 8m0-8-8 8',
  eye: 'M1 8s2.5-5 7-5 7 5 7 5-2.5 5-7 5-7-5-7-5Zm9 0a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z',
  remove: 'M2 4h12M6 1.5h4M4 4l.5 10h7L12 4M6.5 6.5v5M9.5 6.5v5',
  file: 'M3 1.5h6l4 4v9H3Zm6 0v4h4M5.5 8h5m-5 3h5',
  download: 'M8 1v9m-3-3 3 3 3-3M2 11v3h12v-3',
  stage: 'M3 13.5h10M8 2.5v7.5m-3.2-3.2L8 10.2l3.2-3.4',
};
export type IconName = keyof typeof paths;
export const modeIcons: Record<Mode, IconName> = { ask: 'ask', drill: 'drill', tests: 'tests', commit: 'commit' };
export function Icon({ name }: { name: IconName }) {
  return <svg aria-hidden="true" width="20" height="20" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.35" strokeLinecap="round" strokeLinejoin="round"><path d={paths[name]} /></svg>;
}
