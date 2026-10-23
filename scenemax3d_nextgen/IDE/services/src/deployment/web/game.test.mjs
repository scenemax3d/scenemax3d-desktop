import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const source = (await readFile(new URL('./game.js', import.meta.url),'utf8')).replace(/^import .*;\r?\n/gm,'').replaceAll('import.meta.url',JSON.stringify(import.meta.url));
const run = new (Object.getPrototypeOf(async function(){}).constructor)('init','mount_file','mount_alias','read_file','start_game','mountFiles','document','navigator','fetch','window','console','startWithAudio',source);
async function boot(downloadFailure=false) {
  const order=[];
  const nodes = Object.fromEntries(['loading','effect-status','status','error','progress','game'].map(id=>[id,{hidden:false,dataset:{label:'Please wait'},focus(){order.push('focus');}}]));
  const document = {getElementById(id){assert.ok(nodes[id],`Unexpected element ${id}`);return nodes[id];}};
  const events={};
  await run(async()=>order.push('init'),()=>{},()=>{},()=>{},()=>order.push('start'),async(manifest,options)=>{
    order.push('download');options.progress(1,2);
    assert.equal(nodes.status.textContent,'Please wait: 1 / 2');
    assert.ok(!order.includes('start'));
    if(downloadFailure) throw Error('download failed');
    order.push('mounted');
  }, document,{gpu:{requestAdapter:async()=>({})}},async()=>({ok:true,json:async()=>({entry:'game/scripts/main',files:[]})}),{addEventListener(type,fn){events[type]=fn;}},{error(){}},start=>start());
  return {nodes,order,events};
}
test('starts exactly once after mounting without a button or user gesture',async()=>{
  const {nodes,order}=await boot();
  assert.deepEqual(order,['init','download','mounted','start','focus']);
  assert.equal(nodes.loading.hidden,true);
});
test('download failure keeps error visible and never starts game',async()=>{
  const {nodes,order}=await boot(true);
  assert.ok(!order.includes('start'));
  assert.equal(nodes.loading.hidden,false);
  assert.equal(nodes.error.textContent,'download failed');
});
