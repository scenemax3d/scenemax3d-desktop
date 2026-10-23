import {startWithAudio} from './audio.mjs';
try {
  await startWithAudio(() => import('./game.js'), window, pending => {
    document.getElementById('audio-status').hidden = !pending;
  });
} catch (error) {
  document.getElementById('loading').hidden = false;
  document.getElementById('error').textContent = error?.message || String(error);
  console.error(error);
}
