import {test} from 'node:test';
import assert from 'node:assert/strict';
import {startWithAudio} from './audio.mjs';

function fixture() {
  const events = new Map();
  class Context {
    state = 'suspended';
    attempts = 0;
    listeners = new Set();
    addEventListener(_, fn) { this.listeners.add(fn); }
    removeEventListener(_, fn) { this.listeners.delete(fn); }
    async resume() {
      this.attempts++;
      this.state = 'running';
      for (const listener of this.listeners) listener();
    }
  }
  const host = {AudioContext: Context,
    addEventListener(type, fn, capture) { assert.equal(capture,true); events.set(type,fn); },
    removeEventListener(type) { events.delete(type); }};
  return {host, Context, events};
}
test('first ordinary interaction resumes both engine and effect contexts', async () => {
  const {host,Context,events} = fixture();
  let engine, effect;
  const states=[];
  startWithAudio(() => { engine=new host.AudioContext(); effect=new host.AudioContext(); },host,pending=>states.push(pending));
  assert.equal(host.AudioContext,Context);
  assert.equal(engine.attempts,0);
  assert.equal(states.at(-1),true);
  events.get('keydown')();
  await Promise.resolve();
  assert.equal(engine.state,'running');
  assert.equal(effect.state,'running');
  assert.equal(states.at(-1),false);
  assert.equal(events.size,0);
});
test('bootstrap failures restore original constructor', () => {
  const {host,Context,events}=fixture();
  assert.throws(()=>startWithAudio(()=>{new host.AudioContext();throw Error('startup');},host),/startup/);
  assert.equal(host.AudioContext,Context);
  assert.equal(events.size,0);
});
test('captures the constructor cached by an asynchronously imported Wasm module', async () => {
  const {host,Context,events}=fixture();
  let engine;
  await startWithAudio(async()=>{
    await Promise.resolve();
    const Cached = host.AudioContext;
    await Promise.resolve();
    engine = new Cached();
  },host);
  assert.equal(host.AudioContext,Context);
  events.get('pointerdown')();
  await Promise.resolve();
  assert.equal(engine.state,'running');
  await assert.rejects(startWithAudio(async()=>{throw Error('import failed');},host),/import failed/);
  assert.equal(host.AudioContext,Context);
});
test('failed resume remains retryable and startup without audio needs no gesture',async()=>{
  const {host,events}=fixture();
  let context;
  startWithAudio(()=>{context=new host.AudioContext();context.resume=()=>Promise.reject(Error('denied'));},host);
  events.get('pointerdown')();
  await new Promise(resolve=>setImmediate(resolve));
  assert.ok(events.has('pointerdown'));
  const other=fixture();
  startWithAudio(()=>{},other.host);
  assert.equal(other.events.size,0);
});
