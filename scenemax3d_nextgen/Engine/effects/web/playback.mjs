// Backend-independent reconciliation. Async loads never create playback handles.
export class PlaybackRegistry {
  constructor(context, load, report = () => {}) {
    this.context = context;
    this.load = load;
    this.report = report;
    this.assets = new Map();
    this.instances = new Map();
    this.desired = new Map();
    this.disposed = false;
    this.tasks = new Set();
  }

  sync(snapshot, delta) {
    if (this.disposed) return [];
    this.desired = new Map(snapshot.map(s => [s.id, s]));
    const paths = new Set(snapshot.map(s => s.path));
    for (const [id, instance] of this.instances) {
      const state = this.desired.get(id);
      if (!state?.playing || state.generation !== instance.generation || state.path !== instance.path) {
        instance.handle?.stop();
        this.instances.delete(id);
      }
    }
    for (const [path, asset] of this.assets) {
      if (!paths.has(path)) {
        if (asset.effect) this.context.releaseEffect(asset.effect);
        this.assets.delete(path);
      }
    }
    for (const path of paths) {
      if (this.assets.has(path)) continue;
      const asset = {};
      this.assets.set(path, asset);
      const task = Promise.resolve().then(() => this.load(path)).then(effect => {
        if (this.disposed || this.assets.get(path) !== asset) this.context.releaseEffect(effect);
        else asset.effect = effect;
      }).catch(error => {
        if (!this.disposed && this.assets.get(path) === asset) {
          asset.error = error;
          this.report(new Error(`Effect ${path}: ${error.message || error}`));
        }
      });
      this.tasks.add(task);
      task.finally(() => this.tasks.delete(task));
    }
    const completed = [];
    for (const state of snapshot) {
      if (!state.playing) continue;
      const asset = this.assets.get(state.path);
      if (!asset?.effect) continue;
      let instance = this.instances.get(state.id);
      if (!instance) {
        instance = {generation:state.generation, path:state.path, finished:false, handle:null};
        this.instances.set(state.id, instance);
      }
      if (instance.handle && !instance.handle.exists) {
        instance.handle = null;
        instance.finished = !state.looped;
      }
      if (instance.finished) {
        completed.push([state.id, state.generation]);
        continue;
      }
      const fresh = !instance.handle;
      if (fresh) {
        instance.handle = this.context.play(asset.effect, 0, 0, 0);
        if (!instance.handle) {
          this.report(new Error(`Effect ${state.path}: particle capacity exhausted`));
          continue;
        }
      }
      const handle = instance.handle;
      handle.setMatrix(state.matrix);
      state.inputs.forEach((value, index) => handle.setDynamicInput(index, value));
      if (fresh && !state.looped && state.elapsed > 0) {
        // Seek in effect frames, independent of playback speed. The next normal
        // update publishes the resulting draw set to the renderer.
        handle.setSpeed(1);
        handle.setFrame(state.elapsed * 60);
      }
      handle.setSpeed(state.speed);
    }
    this.context.update(Math.max(0, delta) * 60);
    return completed;
  }

  show(ids) {
    const visible = new Set(ids);
    for (const [id, instance] of this.instances) instance.handle?.setShown(visible.has(id));
  }

  get pending() {
    return [...this.assets.values()].filter(a => !a.effect && !a.error).length;
  }

  dispose() {
    if (this.disposed) return;
    for (const instance of this.instances.values()) instance.handle?.stop();
    for (const asset of this.assets.values()) if (asset.effect) this.context.releaseEffect(asset.effect);
    this.instances.clear();
    this.assets.clear();
    this.desired.clear();
    this.disposed = true;
  }

  async settled() { await Promise.allSettled([...this.tasks]); }
}
