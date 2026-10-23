// Resolve only files in this export; preserve subdirectory hosting and filenames
// containing spaces, Unicode, #, ?, or %. Windows imports are case-insensitive.
// Read the same mounted files as Bevy. Loading/replaying effects must not depend
// on another network request after the startup download has completed.
export function createEffectLoader(siteBase, manifestFiles, readFile) {
  const resolve = createResolver(siteBase, manifestFiles);
  const paths = new Map(manifestFiles.map(path => [resolve(path), path]));
  function read(url) {
    const path = paths.get(url);
    if (!path) throw Error(`Missing packaged effect dependency: ${url}`);
    const bytes = readFile(path);
    // wasm-bindgen returns an owned Uint8Array. Preserve its exact range.
    return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
  }
  return (context, path) => {
    const url = resolve(path);
    const file = paths.get(url);
    const directory = file.slice(0, file.lastIndexOf('/') + 1);
    return context.loadEffect(read(url), {
      // Buffer loading has no baseDir; retain the original effect's directory
      // when resolving all textures, sounds, models and other dependencies.
      redirect: dependency => resolve(/^(?:[a-z][a-z0-9+.-]*:|\/)/i.test(dependency)
        ? dependency : directory + dependency),
      resourceLoader: read,
    });
  };
}

export function createResolver(siteBase, manifestFiles) {
  const base = new URL(siteBase);
  const files = new Map();
  for (const path of manifestFiles) {
    if (!path.startsWith('game/') || path.split('/').includes('..')) throw Error('Invalid packaged effect path');
    const url = new URL(path.split('/').map(encodeURIComponent).join('/'), base).href;
    const key = decodeURIComponent(url).toLowerCase();
    if (files.has(key)) throw Error(`Ambiguous packaged effect path: ${path}`);
    files.set(key, url);
  }
  return path => {
    path = path.replaceAll('\\', '/');
    // Effekseer 0.1.0's resource join collapses repeated slashes, including
    // the protocol separator. Restore it before comparing manifest URLs.
    path = path.replace(/^(https?):\/(?!\/)/i, '$1://');
    // First match raw names, then percent-encoded URLs returned by the loader.
    const raw = files.get(path.toLowerCase()) || files.get((base.href + path).toLowerCase());
    if (raw) return raw;
    let decoded;
    try { decoded = decodeURIComponent(path); } catch { decoded = path; }
    const direct = files.get(decoded.toLowerCase());
    if (direct) return direct;
    const url = new URL(encodeURI(decoded).replaceAll('#', '%23').replaceAll('?', '%3F'), base);
    const canonical = files.get(decodeURIComponent(url.href).toLowerCase());
    if (!canonical) throw Error(`Missing packaged effect dependency: ${path}`);
    return canonical;
  };
}
