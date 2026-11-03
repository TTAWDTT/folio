import { createRoot } from 'react-dom/client';
import { App } from './App';
import { Bridge } from './bridge';
import { Drafts } from './drafts';
import './styles.css';

const bridge = new Bridge(window.folioHost);
const drafts = new Drafts(bridge);
const root = document.getElementById('root');
if (!root) throw new Error('Folio root is missing');
createRoot(root).render(<App bridge={bridge} drafts={drafts} />);
const disconnect = bridge.start(window);
window.addEventListener('pagehide', disconnect, { once: true });
