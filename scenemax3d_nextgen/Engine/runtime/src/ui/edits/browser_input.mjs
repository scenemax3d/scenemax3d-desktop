// Inputs exist before the tap: focus stays inside the browser's user gesture,
// including iOS Safari. No synthetic keyboard events or delayed focus calls.
const controls = new Map();
const pending = new Map();
let canvas;
let installed = false;

function emit(control) {
  const el = control.el;
  const start = el.selectionStart ?? el.value.length;
  const end = el.selectionEnd ?? start;
  const backward = el.selectionDirection === 'backward';
  pending.set(control.id, {
    entity: control.id, text: el.value, focus: document.activeElement === el,
    caret: [...el.value.slice(0, backward ? start : end)].length,
    anchor: [...el.value.slice(0, backward ? end : start)].length,
  });
}

function paint(control) {
  const focused = document.activeElement === control.el;
  control.el.style.color = focused ? control.state.color : 'transparent';
  control.el.style.backgroundColor = focused ? control.state.background : 'transparent';
  control.el.style.caretColor = focused ? control.state.cursor : 'transparent';
}

function create(state) {
  const el = document.createElement(state.multiline ? 'textarea' : 'input');
  if (!state.multiline) el.type = 'text';
  el.className = 'scenemax-edit-text';
  el.dataset.scenemaxEdit = state.id;
  el.autocomplete = 'off';
  el.spellcheck = false;
  el.setAttribute('autocapitalize', 'off');
  el.setAttribute('inputmode', 'text');
  el.enterKeyHint = state.multiline ? 'enter' : 'done';
  el.maxLength = 16384;
  el.style.cssText = 'position:fixed;left:0;top:0;margin:0;box-sizing:border-box;border:0;outline:0;border-radius:0;padding:4px 5px;resize:none;transform-origin:0 0;font-family:sans-serif;line-height:normal;pointer-events:auto;touch-action:manipulation;';
  const control = {id:state.id, el, state, composing:false};
  for (const name of ['pointerdown','pointerup','mousedown','mouseup','click','touchstart','touchend','keyup']) {
    el.addEventListener(name, event => event.stopPropagation());
  }
  el.addEventListener('keydown', event => {
    event.stopPropagation();
    if (event.isComposing || control.composing) return;
    if (event.key === 'Escape' || (event.key === 'Enter' && !control.state.multiline)) {
      event.preventDefault();
      el.blur();
      canvas?.focus({preventScroll:true});
    }
  });
  el.addEventListener('focus', () => { emit(control); paint(control); });
  el.addEventListener('blur', () => { control.composing = false; emit(control); paint(control); });
  el.addEventListener('input', event => {
    if (!event.isComposing && !control.composing) emit(control);
  });
  el.addEventListener('compositionstart', () => { control.composing = true; });
  el.addEventListener('compositionend', () => { control.composing = false; emit(control); });
  el.addEventListener('select', () => { if (!control.composing) emit(control); });
  el.addEventListener('paste', event => event.stopPropagation());
  document.body.appendChild(el);
  return control;
}

function position(control) {
  if (!canvas) return;
  const rect = canvas.getBoundingClientRect();
  const sx = rect.width / canvas.width, sy = rect.height / canvas.height;
  const s = control.state, m = s.matrix, style = control.el.style;
  style.width = `${s.width}px`; style.height = `${s.height}px`;
  style.transform = `matrix(${m[0]*sx},${m[1]*sy},${m[2]*sx},${m[3]*sy},${rect.left+m[4]*sx},${rect.top+m[5]*sy})`;
  style.fontSize = `${Math.max(s.fontSize, 16 / Math.max(sy, 0.01))}px`;
  style.zIndex = String(100 + s.z);
  // The runtime intersects ancestor clips in the input's local coordinates.
  const clip = s.clip;
  style.clipPath = clip ? `polygon(${clip.map(p => `${p[0]}px ${p[1]}px`).join(',')})` : '';
  style.display = rect.width > 0 && rect.height > 0 ? 'block' : 'none';
}

export function sync(json) {
  canvas = document.querySelector('canvas#game') || document.querySelector('canvas');
  if (!canvas) return;
  if (!installed) {
    const style = document.createElement('style');
    style.textContent = '.scenemax-edit-text::placeholder{color:transparent}.scenemax-edit-text:focus::placeholder{color:currentColor;opacity:.5}';
    document.head.appendChild(style);
    const reposition = () => { for (const control of controls.values()) position(control); };
    window.addEventListener('resize', reposition);
    window.addEventListener('scroll', reposition, true);
    window.visualViewport?.addEventListener('resize', reposition);
    window.visualViewport?.addEventListener('scroll', reposition);
    installed = true;
  }
  const states = JSON.parse(json), alive = new Set(states.map(s => s.id));
  for (const [id, control] of controls) {
    if (!alive.has(id)) {
      control.el.blur(); emit(control);
      control.el.remove(); controls.delete(id);
    }
  }
  for (const state of states) {
    let control = controls.get(state.id);
    let replaced = false;
    if (control && control.state.multiline !== state.multiline) {
      control.el.blur(); control.el.remove(); controls.delete(state.id); pending.delete(state.id);
      control = null; replaced = true;
    }
    if (!control) { control = create(state); controls.set(state.id, control); }
    control.state = state;
    const el = control.el;
    if (replaced) { el.value = state.value; emit(control); }
    el.setAttribute('aria-label', state.placeholder || state.name);
    el.placeholder = state.placeholder;
    // Never replace composition text or a user change not yet consumed by Rust.
    if (!control.composing && !pending.has(state.id) && el.value !== state.value) {
      const start = el.selectionStart, end = el.selectionEnd, direction = el.selectionDirection;
      el.value = state.value;
      if (document.activeElement === el && start !== null) el.setSelectionRange(Math.min(start, el.value.length), Math.min(end, el.value.length), direction);
    }
    position(control); paint(control);
  }
}

export function takeEvents() {
  const result = JSON.stringify([...pending.values()]);
  pending.clear();
  return result;
}
