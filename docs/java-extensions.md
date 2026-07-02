# Java Extensions

SceneMax3D Java extensions let a project add native Java runtime code beside SceneMax script. They are intended for advanced users who need direct access to jMonkeyEngine objects, lower-level runtime APIs, or performance-sensitive logic that does not belong in the SceneMax language itself.

Java extensions are not a replacement for SceneMax script. Use SceneMax for game flow, object creation, content wiring, animation commands, camera setup, and readable gameplay logic. Use Java when the work benefits from native APIs, reusable Java libraries, custom algorithms, or tight per-frame control.

## When To Use Java

Good use-cases:

- Per-frame logic that needs to be very fast or allocation-aware.
- Direct manipulation of JME `Spatial`, `Node`, `Geometry`, `Material`, `Control`, `AppState`, or physics objects.
- Custom runtime systems such as steering, procedural animation, pathfinding, perception, combat simulation, terrain sampling, or object pooling policies.
- Integration with Java libraries that are not part of SceneMax script.
- Features that should run as a reusable native runtime component across multiple SceneMax projects.
- Debugging and instrumentation systems that inspect native state.

Avoid Java when:

- The behavior is already simple and readable in SceneMax script.
- You only need to call existing SceneMax commands such as `move`, `rotate`, `animate`, `show`, `hide`, or `play`.
- Designers or non-programmers must frequently edit the logic.
- The code depends on high-level SceneMax command scheduling, easing, or language semantics.

The intended split is:

```scenemax
// SceneMax creates and wires the scene.
player => sinbad
camera => Camera.System.follow(player)

// Java adds native behavior at the exact point where the script wants it.
Java.attach "PlayerNativeLogic"
```

## Creating A Java Extension

In the IDE, create a new entity of type `Java extension`.

The IDE creates:

```text
scripts/
  <your script folder>/
    PlayerNativeLogic/
      .scenemax-java-extension
      PlayerNativeLogic.java
```

Current conventions:

- The folder name, primary Java file name, and primary class name should match.
- The primary app state class currently uses the default package.
- The primary class should extend `com.scenemaxeng.projector.SceneMaxBaseAppState`.
- Additional `.java` files may be added inside the extension folder.
- The extension is compiled with Java 11 source/target compatibility.
- Building Java extensions requires running SceneMax with a JDK, not only a JRE.

Example primary class:

```java
import com.scenemaxeng.projector.SceneMaxApp;
import com.scenemaxeng.projector.SceneMaxBaseAppState;

public class PlayerNativeLogic extends SceneMaxBaseAppState {
    @Override
    protected void onSceneMaxInitialize(SceneMaxApp app) {
        // Optional one-time setup.
    }

    @Override
    public void update(float tpf) {
        // Called once per frame while the app state is attached and enabled.
    }
}
```

## Attaching Java From SceneMax

Java code is attached explicitly from SceneMax script:

```scenemax
Java.attach "PlayerNativeLogic"
```

The command attaches a new instance of the named Java app state at that exact point in script execution.

This is important. If the script says:

```scenemax
s => sinbad
Java.attach "logic"
```

then `logic` receives the same runtime scope that contains `s`. If the command runs before `s` is created, that scope will not yet contain `s`.

Avoid placing the same `Java.attach` command inside a repeating block unless you intentionally want to attach multiple app state instances.

## SceneMaxBaseAppState

All SceneMax Java app states should extend `SceneMaxBaseAppState`.

`SceneMaxBaseAppState` is a thin base class over JME's `BaseAppState`. It stores:

- the parent `SceneMaxApp`
- the current `SceneMaxScope`

It also exposes helper methods:

```java
protected final SceneMaxApp getSceneMaxApp()
protected final SceneMaxScope getSceneMaxScope()
protected final EntityInstBase getEntity(String name)
protected final Spatial getEntitySpatial(String name)
```

Use `getEntitySpatial("name")` when you want the native JME `Spatial` for a SceneMax object visible in the attached scope.

Use `getEntity("name")` when you need metadata about the SceneMax runtime entity, such as its variable definition or scoped runtime name.

## What SceneMaxScope Represents

`SceneMaxScope` is the runtime execution context for a block of SceneMax code.

For professional Java developers, the closest analogy is a stack frame plus a SceneMax entity registry. It tells native Java code what SceneMax names mean at the exact point where the app state was attached.

A scope contains:

- local/runtime variables
- entities created in that block
- function parameters
- groups
- a parent-scope link for outer-name resolution
- the block's controller queue
- a unique `scopeId`

SceneMax runtime objects are stored internally with scoped names such as:

```text
s@1
enemy@4
projectile@9
```

The plain SceneMax source name is `s`, but the native runtime registry needs `s@1` to distinguish objects created in different scopes.

Java extension code should not guess or search for `s@1`. Instead, use the injected scope:

```java
Spatial sinbad = getEntitySpatial("s");
```

Internally, `SceneMaxBaseAppState` asks the current `SceneMaxScope` for the entity named `s`, receives the correct `EntityInstBase`, reads its runtime name, and then asks `SceneMaxApp` for the native `Spatial`.

This preserves SceneMax's normal visibility rules without doing a global search.

## Full Example: Rotate Sinbad 360 Degrees In 10 Seconds

SceneMax script:

```scenemax
s => sinbad
Java.attach "logic"
```

Java extension folder:

```text
logic/
  .scenemax-java-extension
  logic.java
```

`logic.java`:

```java
import com.jme3.math.FastMath;
import com.jme3.scene.Spatial;
import com.scenemaxeng.projector.SceneMaxBaseAppState;

public class logic extends SceneMaxBaseAppState {
    private Spatial sinbad;
    private float elapsedSeconds;

    @Override
    public void update(float tpf) {
        if (sinbad == null) {
            sinbad = getEntitySpatial("s");
            if (sinbad == null) {
                return;
            }
        }

        if (elapsedSeconds >= 10f) {
            return;
        }

        float step = Math.min(tpf, 10f - elapsedSeconds);
        float radiansPerSecond = FastMath.TWO_PI / 10f;
        sinbad.rotate(0f, radiansPerSecond * step, 0f);
        elapsedSeconds += step;
    }
}
```

Notes:

- `tpf` is "time per frame" in seconds.
- `FastMath.TWO_PI` is 360 degrees in radians.
- After 10 seconds the object has completed a full turn, so it ends at its original orientation.
- If another SceneMax command also controls the same object's rotation at the same time, the final visible result depends on update order and on which transform is applied last.

## Accessing Native JME Objects

Get a spatial:

```java
Spatial player = getEntitySpatial("player");
```

Get a node when you know the object is represented by a `Node`:

```java
Spatial spatial = getEntitySpatial("enemy");
if (spatial instanceof Node) {
    Node enemyNode = (Node) spatial;
    enemyNode.setLocalScale(1.2f);
}
```

Traverse geometries:

```java
Spatial object = getEntitySpatial("crate");
if (object != null) {
    object.depthFirstTraversal(spatial -> {
        if (spatial instanceof Geometry) {
            Geometry geometry = (Geometry) spatial;
            // Inspect or modify geometry here.
        }
    });
}
```

Access the parent app:

```java
SceneMaxApp app = getSceneMaxApp();
Node root = app.getRootNode();
```

Access the scope directly:

```java
EntityInstBase entity = getSceneMaxScope().getEntityInst("player");
if (entity != null) {
    String runtimeName = entity.getVarRunTimeName();
}
```

## Lifecycle

`SceneMaxBaseAppState` follows JME app-state lifecycle conventions:

```java
protected void onSceneMaxInitialize(SceneMaxApp app)
public void update(float tpf)
protected void cleanup(Application app)
protected void onEnable()
protected void onDisable()
```

Most extensions only need `update(float tpf)`.

Use `onSceneMaxInitialize(SceneMaxApp app)` for optional one-time setup after the app state is attached. The scope is already available through `getSceneMaxScope()`, so it is not passed as a parameter.

Use `cleanup(Application app)` to release references or detach native objects created by the extension. If you override it, call `super.cleanup(app)`:

```java
@Override
protected void cleanup(Application app) {
    super.cleanup(app);
    cachedSpatial = null;
}
```

## Build And Packaging

When the game is run or packaged, SceneMax compiles Java extensions found under the project's scripts folder.

For each Java extension folder:

1. SceneMax compiles all `.java` files in the folder.
2. SceneMax creates a runtime extension jar.
3. SceneMax writes an extension index.
4. The run/package flow makes the jar available to the projector.
5. `Java.attach "<name>"` loads and attaches the requested app state at runtime.

Compilation errors are shown in a scrollable IDE error dialog and written to a compile log:

- run flow: `running/java-extensions/java-extension-compile.log`
- package flow: `build_games/<game>/java-extension-compile.log`

The log includes source file paths, line/column numbers, source-line context, and compiler classpath entries.

## Threading And Performance

`update(float tpf)` runs as part of the JME application update loop. Treat it as performance-sensitive code.

Guidelines:

- Avoid heavy allocation every frame.
- Cache resolved spatials after the first lookup.
- Do not block in `update`.
- Do not perform file I/O, network I/O, or long computations on the render/update thread.
- If background work is required, marshal changes back onto the JME thread before touching scene graph objects.
- Be careful when modifying the same transform that SceneMax commands or physics controls are also modifying.

Example caching pattern:

```java
private Spatial player;

@Override
public void update(float tpf) {
    if (player == null) {
        player = getEntitySpatial("player");
        if (player == null) {
            return;
        }
    }

    // Fast per-frame logic here.
}
```

## Relationship To SceneMax Commands

Java extensions should not simply reimplement SceneMax commands in Java. For example, this is usually better in SceneMax:

```scenemax
door.rotate(y 90) in 1 second
```

Java makes more sense when you need native behavior that SceneMax does not model directly:

```java
// Custom procedural wobble using direct Spatial access.
float y = FastMath.sin(elapsed * 12f) * 0.08f;
spatial.setLocalTranslation(baseX, baseY + y, baseZ);
```

The clean pattern is:

- SceneMax script creates and names the game objects.
- SceneMax script chooses when Java should attach.
- Java uses the injected scope to resolve those names to native objects.
- Java performs low-level or specialized runtime behavior.

## Troubleshooting

### `Java.attach "logic"` does nothing

Check:

- The Java extension folder is named `logic`.
- The primary file is `logic.java`.
- The primary class is `public class logic extends SceneMaxBaseAppState`.
- The script executes `Java.attach "logic"` after the objects it needs are created.
- The run/package compile log has no Java errors.
- The launcher/projector jar has been rebuilt after adding Java-extension runtime support.

### `getEntitySpatial("s")` returns null

Possible causes:

- The script attached the Java app state before creating `s`.
- The object is created in a different scope that is not visible from the attach command's scope.
- The name is misspelled.
- The object has been deleted or not yet loaded.

Prefer attaching immediately after the objects the extension needs:

```scenemax
s => sinbad
Java.attach "logic"
```

### The object compiles but does not visually change

Possible causes:

- A SceneMax command, animation controller, physics control, or another app state is overwriting the same transform.
- The object rotates a full 360 degrees and ends at the same orientation.
- You are rotating a wrapper node while visible child animation or physics uses another transform.
- The app is running with an old launcher/projector jar.

### Java compiler is unavailable

Run SceneMax with a JDK. The Java extension build uses the Java compiler API, which is not available in a plain JRE.

