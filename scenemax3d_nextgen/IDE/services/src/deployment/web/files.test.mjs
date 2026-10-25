import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mountFiles} from './files.mjs';

test('download each unique file once, then mount aliases before completion',async()=>{
  const store=new Map(), fetched=[], progress=[];
  await mountFiles({files:['game/a.png','game/shared/a.png','game/space #%.bin'],aliases:{'game/shared/a.png':'game/a.png'}},{
    fetchFile:async path=>{fetched.push(path);return {ok:true,arrayBuffer:async()=>new Uint8Array([1,2,3]).buffer};},
    mountFile:(p,b)=>store.set(p,b),mountAlias:(p,s)=>{assert.ok(store.has(s));store.set(p,store.get(s));},progress:(...p)=>progress.push(p),
  });
  assert.deepEqual(fetched,['game/a.png','game/space%20%23%25.bin']);
  assert.equal(store.size,3);
  assert.equal(store.get('game/a.png'),store.get('game/shared/a.png'));
  assert.deepEqual(progress.at(-1),[2,2]);
});
test('invalid paths, missing targets and alias cycles fail before fetching',async()=>{
  for(const manifest of [
    {files:['../escape']},
    {files:['game/a'],aliases:{'game/a':'game/missing'}},
    {files:['game/a','game/b'],aliases:{'game/a':'game/b','game/b':'game/a'}},
  ]) {
    await assert.rejects(mountFiles(manifest,{fetchFile:()=>assert.fail('must not fetch')}),/Invalid/);
  }
});
test('failed canonical download cannot produce a successful alias mount',async()=>{
  await assert.rejects(mountFiles({files:['game/a','game/b'],aliases:{'game/b':'game/a'}},{
    fetchFile:async()=>({ok:false,status:404}),mountAlias:()=>assert.fail('missing bytes'),progress:()=>{},
  }),/game\/a: HTTP 404/);
});
test('recovers from failed fetch and interrupted body without duplicate mounts',async()=>{
  let calls=0;
  const mounts=[], retries=[], pauses=[], updates=[];
  await mountFiles({files:['game/large model.glb','game/copy.glb'],aliases:{'game/copy.glb':'game/large model.glb'}},{
    fetchFile:async()=>{
      calls++;
      if(calls===1) throw TypeError('Failed to fetch');
      return {ok:true,arrayBuffer:async()=>{
        if(calls===2) throw TypeError('connection interrupted');
        return new Uint8Array([42]).buffer;
      }};
    },
    mountFile:(p,b)=>mounts.push([p,...b]),mountAlias:()=>mounts.push('alias'),
    progress:(...p)=>updates.push(p),retry:(...r)=>retries.push(r),wait:async ms=>pauses.push(ms),
  });
  assert.equal(calls,3);
  assert.deepEqual(mounts,[['game/large model.glb',42],'alias']);
  assert.deepEqual(pauses,[250,500]);
  assert.deepEqual(retries.map(r=>r[1]),[2,3]);
  assert.deepEqual(updates,[[0,1],[1,1]]);
});
test('persistent network error identifies file and stops after three attempts',async()=>{
  let calls=0;
  await assert.rejects(mountFiles({files:['game/problem.bin']},{
    fetchFile:async()=>{calls++;throw TypeError('Failed to fetch');},
    mountFile:()=>assert.fail('no bytes'),mountAlias:()=>assert.fail('no alias'),
    progress:()=>{},wait:async()=>{},
  }),/game\/problem.bin: Failed to fetch.*3 attempt/);
  assert.equal(calls,3);
});
test('late successful downloads cannot overwrite a fatal loading error',async()=>{
  let resolve;
  const late=new Promise(r=>resolve=r), updates=[];
  await assert.rejects(mountFiles({files:['game/missing','game/late']},{
    fetchFile:async p=>p.endsWith('missing')?{ok:false,status:404}:late,
    mountFile:()=>assert.fail('late mount'),mountAlias:()=>assert.fail('alias'),
    progress:(...p)=>updates.push(p),
  }),/HTTP 404/);
  resolve({ok:true,arrayBuffer:async()=>new ArrayBuffer(1)});
  await new Promise(r=>setTimeout(r,0));
  assert.deepEqual(updates,[[0,2]]);
});
