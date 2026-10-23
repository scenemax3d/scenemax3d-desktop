import {test} from 'node:test';
import assert from 'node:assert/strict';
import {PlaybackRegistry} from './playback.mjs';

const state = (change = {}) => ({id:'18446744073709551610', generation:'18446744073709551611', path:'game/resources/effect.efkefc', playing:true, looped:false, speed:2, elapsed:0, inputs:[1,2,3,4], matrix:Array(16).fill(0), ...change});
function fixture(load = async path => ({path})) {
  const handles = [], released = [], errors = [];
  const context = {
    play() {
      const calls=[];
      const handle={exists:true, calls, stop(){this.exists=false;calls.push(['stop']);}};
      for (const method of ['setMatrix','setDynamicInput','setSpeed','setFrame','setShown']) handle[method]=(...args)=>calls.push([method,...args]);
      handles.push(handle); return handle;
    },
    update(){}, releaseEffect(effect){released.push(effect);},
  };
  return {registry:new PlaybackRegistry(context,load,e=>errors.push(e)),handles,released,errors};
}
async function loaded(f, snapshot=[state()]) {f.registry.sync(snapshot,0);await f.registry.settled();return f.registry.sync(snapshot,1/60);}

test('one-shot completion retains exact u64 instance and generation; no auto replay', async () => {
  const f=fixture();await loaded(f);f.handles[0].exists=false;
  assert.deepEqual(f.registry.sync([state()],0),[[state().id,state().generation]]);
  f.registry.sync([state()],0);assert.equal(f.handles.length,1);
});
test('loop completion restarts and stop removes the current handle', async () => {
  const f=fixture();const s=state({looped:true});await loaded(f,[s]);f.handles[0].exists=false;
  assert.deepEqual(f.registry.sync([s],0),[]);assert.equal(f.handles.length,2);
  f.registry.sync([state({playing:false})],0);assert.equal(f.handles[1].exists,false);
});
test('replay stops the old generation and cannot inherit its completion', async () => {
  const f=fixture();await loaded(f);f.handles[0].exists=false;
  assert.deepEqual(f.registry.sync([state({generation:'7'})],0),[]);
  assert.equal(f.handles.length,2);assert.equal(f.registry.instances.get(state().id).generation,'7');
});
test('a late load after destruction releases the asset without starting playback', async () => {
  let resolve;const f=fixture(()=>new Promise(r=>resolve=r));f.registry.sync([state()],0);await Promise.resolve();
  f.registry.sync([],0);resolve({asset:1});await f.registry.settled();f.registry.sync([],0);
  assert.equal(f.handles.length,0);assert.equal(f.released.length,1);
});
test('a late load can only play the current generation', async () => {
  let resolve;const f=fixture(()=>new Promise(r=>resolve=r));f.registry.sync([state()],0);await Promise.resolve();
  f.registry.sync([state({generation:'9'})],0);resolve({asset:1});await f.registry.settled();
  f.registry.sync([state({generation:'9'})],0);
  assert.equal(f.handles.length,1);assert.equal(f.registry.instances.get(state().id).generation,'9');
});
test('replacement asset does not receive a previous asset load', async () => {
  const pending=new Map();const f=fixture(path=>new Promise(r=>pending.set(path,r)));
  f.registry.sync([state()],0);await Promise.resolve();
  const next=state({path:'game/resources/replacement.efkefc',generation:'10'});
  f.registry.sync([next],0);await Promise.resolve();pending.get(state().path)({old:true});pending.get(next.path)({new:true});
  await f.registry.settled();f.registry.sync([next],0);
  assert.equal(f.released.length,1);assert.equal(f.handles.length,1);
});
test('transform and dynamic inputs are applied before seek; speed is restored afterward', async () => {
  const f=fixture();await loaded(f,[state({elapsed:1.5})]);
  const calls=f.handles[0].calls;const seek=calls.findIndex(c=>c[0]==='setFrame');
  assert.deepEqual(calls[seek],['setFrame',90]);
  assert.ok(calls.findIndex(c=>c[0]==='setDynamicInput'&&c[1]===3)<seek);
  assert.deepEqual(calls[0],['setMatrix',state().matrix]);
  assert.deepEqual(calls.at(-1),['setSpeed',2]);
});
test('failed loads report once and never create a handle', async () => {
  let attempts=0;const f=fixture(async()=>{attempts++;throw Error('HTTP 404');});
  await loaded(f);f.registry.sync([state()],0);await f.registry.settled();
  assert.equal(attempts,1);assert.equal(f.errors.length,1);assert.match(f.errors[0].message,/HTTP 404/);assert.equal(f.handles.length,0);
});
test('cleanup waits for stale loads and releases each effect once', async () => {
  let resolve;const f=fixture(()=>new Promise(r=>resolve=r));f.registry.sync([state()],0);await Promise.resolve();
  f.registry.dispose();resolve({asset:1});await f.registry.settled();f.registry.dispose();
  assert.equal(f.released.length,1);assert.equal(f.handles.length,0);
});
test('view visibility does not destroy playback', async () => {
  const f=fixture();await loaded(f);f.registry.show([]);f.registry.show([state().id]);
  assert.deepEqual(f.handles[0].calls.slice(-2),[['setShown',false],['setShown',true]]);assert.equal(f.handles[0].exists,true);
});
