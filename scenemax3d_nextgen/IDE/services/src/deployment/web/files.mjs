export async function mountFiles(manifest, {fetchFile, mountFile, mountAlias, progress,
  retry = () => {}, wait = ms => new Promise(resolve => setTimeout(resolve, ms))}) {
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
  let next = 0, complete = 0, failed = false;
  progress(0, downloads.length);
  async function download(path) {
    const url = path.split('/').map(encodeURIComponent).join('/');
    for (let attempt = 1; attempt <= 3; attempt++) {
      let retryable = true;
      try {
        const response = await fetchFile(url);
        if (!response.ok) {
          retryable = response.status === 408 || response.status === 429 || response.status >= 500;
          throw Error(`HTTP ${response.status}`);
        }
        return new Uint8Array(await response.arrayBuffer());
      } catch (cause) {
        if (!retryable || attempt === 3 || failed)
          throw Error(`${path}: ${cause?.message || cause}. Download failed after ${attempt} attempt(s). Keep the local preview running and reload to retry.`, {cause});
        retry(path, attempt + 1, 3);
        await wait(250 * attempt);
        if (failed) throw cause;
      }
    }
  }
  async function worker() {
    while (!failed && next < downloads.length) {
      const path = downloads[next++];
      try {
        const bytes = await download(path);
        if (failed) return;
        mountFile(path, bytes);
        progress(++complete, downloads.length);
      } catch (error) {
        failed = true;
        throw error;
      }
    }
  }
  await Promise.all(Array.from({length:4}, worker));
  for (const [path, source] of aliases) mountAlias(path, source);
}
