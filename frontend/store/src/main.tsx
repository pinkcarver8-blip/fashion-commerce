import '@commerce/tokens/tokens.css';
import './base.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { rememberAdminPath } from './demo';

const root = document.getElementById('root');
if (!root) {
  throw new Error('root 요소가 없다');
}
createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
rememberAdminPath();
