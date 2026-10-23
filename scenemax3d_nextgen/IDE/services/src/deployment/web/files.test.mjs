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
