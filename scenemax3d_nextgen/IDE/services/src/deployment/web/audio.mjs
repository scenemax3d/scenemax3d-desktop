// Capture contexts while importing and starting the game (Bevy caches its constructor).
// Restore the browser constructors immediately; no prototype changes are needed.
export function startWithAudio(start, host = window, status = () => {}) {
  const contexts = new Set();
  const originals = new Map();
  const update = () => {
    const pending = [...contexts].some(context => context.state === 'suspended');
    status(pending);
    if (!pending) cleanup();
  };
  const resume = () => {
    for (const context of contexts) {
      if (context.state === 'suspended') {
        // Must execute directly in the gesture handler, before any await.
        try { context.resume().then(update).catch(() => status(true)); }
        catch { status(true); }
      }
    }
  };
  const cleanup = () => {
    host.removeEventListener('pointerdown', resume, true);
    host.removeEventListener('keydown', resume, true);
    for (const context of contexts) context.removeEventListener('statechange', update);
  };
  for (const name of ['AudioContext', 'webkitAudioContext']) {
    const Original = host[name];
    if (!Original) continue;
    originals.set(name, Original);
    host[name] = new Proxy(Original, {construct(target, args) {
      const context = Reflect.construct(target, args);
      contexts.add(context);
      return context;
    }});
  }
  const restore = () => { for (const [name, Original] of originals) host[name] = Original; };
  const ready = () => {
    restore();
    for (const context of contexts) context.addEventListener('statechange', update);
    update();
    return cleanup;
  };
  // Register before the engine's input handlers, which may consume key events.
  host.addEventListener('pointerdown', resume, true);
  host.addEventListener('keydown', resume, true);
  try {
    const result = start();
    if (result?.then) return Promise.resolve(result).then(ready, error => { restore(); cleanup(); throw error; });
    return ready();
  } catch (error) { restore(); cleanup(); throw error; }
}
