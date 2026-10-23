import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createResolver, createEffectLoader} from './resources.mjs';

test('local files and relative dependencies under subdirectory hosting', () => {
  const resolve=createResolver('https://example.test/releases/demo/', ['game/resources/fx/effect.efkefc','game/resources/Texture/火 #20%?.png']);
  assert.equal(resolve('game/resources/fx/effect.efkefc'),'https://example.test/releases/demo/game/resources/fx/effect.efkefc');
  assert.equal(resolve('https:/example.test/releases/demo/game/resources/fx/effect.efkefc'),'https://example.test/releases/demo/game/resources/fx/effect.efkefc');
  assert.equal(resolve('https://example.test/releases/demo/game/resources/fx/../Texture/火 #20%?.png'), 'https://example.test/releases/demo/game/resources/Texture/%E7%81%AB%20%2320%25%3F.png');
  assert.equal(resolve('game\\resources\\texture\\火 #20%?.png'), 'https://example.test/releases/demo/game/resources/Texture/%E7%81%AB%20%2320%25%3F.png');
});
test('literal percent escapes in filenames are preserved', () => {
  const resolve=createResolver('https://example.test/sub/', ['game/20%20.png']);
  assert.equal(resolve('game/20%20.png'),'https://example.test/sub/game/20%2520.png');
  assert.equal(resolve('https://example.test/sub/game/20%2520.png'),'https://example.test/sub/game/20%2520.png');
});
test('missing files, remote requests and case collisions fail explicitly', () => {
  const resolve=createResolver('https://example.test/sub/', ['game/texture.png']);
  assert.throws(()=>resolve('https://other.test/game/texture.png'),/Missing packaged/);
  assert.throws(()=>resolve('game/missing.png'),/missing.png/);
  assert.throws(()=>createResolver('https://example.test/', ['game/A.png','game/a.png']),/Ambiguous/);
});

test('effect and dependencies load from mounted bytes, including replay and exact byte ranges', async () => {
  const files = new Map([
    ['game/fx/demo/effect.efkefc', new Uint8Array([99,1,2,99]).subarray(1,3)],
    ['game/fx/Texture/火 #20%?.png', new Uint8Array([3,4])],
    ['game/fx/demo/Model/model.efkmodel', new Uint8Array([5,6])],
  ]);
  const reads=[];
  const load=createEffectLoader('https://example.test/sub/', [...files.keys()], path=>{
    reads.push(path); return files.get(path);
  });
  const context={async loadEffect(bytes, options) {
    assert.deepEqual([...new Uint8Array(bytes)],[1,2]);
    assert.deepEqual([...new Uint8Array(await options.resourceLoader(options.redirect('../Texture/火 #20%?.png'),'image'))],[3,4]);
    assert.deepEqual([...new Uint8Array(await options.resourceLoader(options.redirect('Model/model.efkmodel'),'binary'))],[5,6]);
    return {loaded:true};
  }};
  assert.deepEqual(await load(context,'game/fx/demo/effect.efkefc'),{loaded:true});
  assert.deepEqual(await load(context,'game/fx/demo/effect.efkefc'),{loaded:true});
  assert.equal(reads.length,6);
});

test('identical dependency filenames remain relative to their own effect', async () => {
  const files=['game/a/effect.efkefc','game/a/image.png','game/b/effect.efkefc','game/b/image.png'];
  const load=createEffectLoader('https://example.test/sub/',files,path=>new Uint8Array([files.indexOf(path)]));
  const context={async loadEffect(bytes,options) {
    return [...new Uint8Array(await options.resourceLoader(options.redirect('image.png'),'image'))];
  }};
  assert.deepEqual(await load(context,files[0]),[1]);
  assert.deepEqual(await load(context,files[2]),[3]);
});

test('missing mounted dependencies fail explicitly without falling back to the network', async () => {
  const load=createEffectLoader('https://example.test/', ['game/fx/effect.efkefc'],()=>new Uint8Array([1]));
  const context={async loadEffect(bytes,options) { return options.resourceLoader(options.redirect('missing.png')); }};
  await assert.rejects(load(context,'game/fx/effect.efkefc'),/Missing packaged effect dependency/);
});
