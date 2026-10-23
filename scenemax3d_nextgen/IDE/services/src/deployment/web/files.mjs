export async function mountFiles(manifest, {fetchFile, mountFile, mountAlias, progress}) {
  const paths = new Set(manifest.files);
  if (paths.size !== manifest.files.length) throw Error('Duplicate manifest path');
  for (const path of paths) {
    if (!path.startsWith('game/') || path.split('/').some(p => !p || p === '..' || p === '.') || /[\\:]/.test(path))
      throw Error('Invalid packaged asset path');
  }
  const aliases = new Map(Object.entries(manifest.aliases || {}));
  for (const [path, source] of aliases) {
    if (!paths.has(path) || !paths.has(source) || aliases.has(source))
      throw Error('Invalid packaged asset alias');
  }
  const downloads = [...paths].filter(path => !aliases.has(path));
  let next = 0, complete = 0;
  progress(0, downloads.length);
  async function worker() {
    while (next < downloads.length) {
      const path = downloads[next++];
      const response = await fetchFile(path.split('/').map(encodeURIComponent).join('/'));
      if (!response.ok) throw Error(`${path}: HTTP ${response.status}`);
      mountFile(path, new Uint8Array(await response.arrayBuffer()));
      progress(++complete, downloads.length);
    }
  }
  await Promise.all(Array.from({length:4}, worker));
  for (const [path, source] of aliases) mountAlias(path, source);
}
