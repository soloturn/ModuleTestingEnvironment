// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.moduletestingenvironment;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import org.joml.Matrix4f;
import org.joml.RoundingMode;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.config.Config;
import org.terasology.engine.config.SystemConfig;
import org.terasology.engine.context.Context;
import org.terasology.engine.core.GameEngine;
import org.terasology.engine.core.PathManager;
import org.terasology.engine.core.PathManagerProvider;
import org.terasology.engine.core.TerasologyConstants;
import org.terasology.engine.core.TerasologyEngine;
import org.terasology.engine.core.TerasologyEngineBuilder;
import org.terasology.engine.core.Time;
import org.terasology.engine.core.modes.GameState;
import org.terasology.engine.core.modes.StateIngame;
import org.terasology.engine.core.modes.StateLoading;
import org.terasology.engine.core.modes.StateMainMenu;
import org.terasology.engine.core.module.ModuleManager;
import org.terasology.engine.core.subsystem.EngineSubsystem;
import org.terasology.engine.core.subsystem.headless.HeadlessAudio;
import org.terasology.engine.core.subsystem.headless.HeadlessGraphics;
import org.terasology.engine.core.subsystem.headless.HeadlessInput;
import org.terasology.engine.core.subsystem.headless.HeadlessTimer;
import org.terasology.engine.core.subsystem.headless.mode.HeadlessStateChangeListener;
import org.terasology.engine.core.subsystem.lwjgl.LwjglAudio;
import org.terasology.engine.core.subsystem.lwjgl.LwjglGraphics;
import org.terasology.engine.core.subsystem.lwjgl.LwjglInput;
import org.terasology.engine.core.subsystem.lwjgl.LwjglTimer;
import org.terasology.engine.core.subsystem.openvr.OpenVRInput;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.network.JoinStatus;
import org.terasology.engine.network.NetworkSystem;
import org.terasology.engine.registry.CoreRegistry;
import org.terasology.engine.rendering.opengl.ScreenGrabber;
import org.terasology.engine.rendering.world.viewDistance.ViewDistance;
import org.terasology.engine.testUtil.WithUnittestModule;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.BlockRegion;
import org.terasology.engine.world.block.BlockRegionc;
import org.terasology.engine.world.chunks.Chunks;
import org.terasology.engine.world.chunks.localChunkProvider.RelevanceSystem;
import org.terasology.gestalt.module.Module;
import org.terasology.gestalt.module.ModuleMetadataJsonAdapter;
import org.terasology.gestalt.module.ModuleRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verifyNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Base class for tests involving full {@link TerasologyEngine} instances. View the tests included in this module for
 * simple usage examples.
 *
 * <h2>Introduction</h2>
 * If test classes extend this class will create a new host engine for each {@code @Test} method. If the testing
 * environment is used by composition {@link #setup()} and {@link #tearDown()} need to be called explicitly. This can be
 * done once for the test class or for each test.
 * <p>
 * The in-game {@link Context} for this engine can be accessed via {@link #getHostContext()}. The result of this getter
 * is equivalent to the CoreRegistry available to module code at runtime. However, it is very important that you do not
 * use CoreRegistry in your test code, as this is manipulated by the test environment to allow multiple instances of the
 * engine to peacefully coexist. You should always use the returned context reference to manipulate or inspect the
 * CoreRegistry of a given engine instance.
 *
 * <h2>Client Engine Instances</h2>
 * Client instances can be easily created via {@link #createClient()} which returns the in-game context of the created
 * engine instance. When this method returns, the client will be in the {@link StateIngame} state and connected to the
 * host. Currently all engine instances are headless, though it is possible to use headed engines in the future.
 * <p>
 * Engines can be run while a condition is true via {@link #runWhile(Supplier)} <br>{@code runWhile(()-> true);}
 * <p>
 * or conversely run until a condition is true via {@link #runUntil(Supplier)} <br>{@code runUntil(()-> false);}
 *
 * <h2>Specifying Dependencies</h2>
 * By default the environment will load only the engine itself. FIXME
 *
 * <h2>Specifying World Generator</h2>
 * By default the environment will use a dummy world generator which creates nothing but air. To specify a more useful
 * world generator you must FIXME
 *
 * <h2>Reuse the MTE for Multiple Tests</h2>
 * To use the same engine for multiple tests the testing environment can be set up explicitly and shared between tests.
 * To configure module dependencies or the world generator an anonymous class may be used.
 * <pre>
 * private static ModuleTestingEnvironment context;
 *
 * &#64;BeforeAll
 * public static void setup() throws Exception {
 *     context = new ModuleTestingEnvironment() {
 *     &#64;Override
 *     public Set&lt;String&gt; getDependencies() {
 *         return Sets.newHashSet("ModuleTestingEnvironment");
 *     }
 *     };
 *     context.setup();
 * }
 *
 * &#64;AfterAll
 * public static void tearDown() throws Exception {
 *     context.tearDown();
 * }
 *
 * &#64;Test
 * public void someTest() {
 *     Context hostContext = context.getHostContext();
 *     EntityManager entityManager = hostContext.get(EntityManager.class);
 *     // ...
 * }
 * </pre>
 *
 * @deprecated Use the {@link MTEExtension} or {@link IsolatedMTEExtension} instead with JUnit5.
 */
@Deprecated
public class ModuleTestingEnvironment {
    @Deprecated
    public static final long DEFAULT_TIMEOUT = 30000;

    public static final long DEFAULT_SAFETY_TIMEOUT = 60000;
    public static final long DEFAULT_GAME_TIME_TIMEOUT = 30000;
    public static final String DEFAULT_WORLD_GENERATOR = "moduletestingenvironment:dummy";

    private static final Logger logger = LoggerFactory.getLogger(ModuleTestingEnvironment.class);

    protected final Set<String> dependencies = Sets.newHashSet("engine");
    protected String worldGeneratorUri = DEFAULT_WORLD_GENERATOR;

    PathManager pathManager;
    PathManagerProvider.Cleaner pathManagerCleaner;

    private boolean doneLoading;
    private TerasologyEngine host;
    private Context hostContext;
    private final List<TerasologyEngine> engines = Lists.newArrayList();
    private long safetyTimeoutMs = DEFAULT_SAFETY_TIMEOUT;

    protected ModuleTestingEnvironment(Set<String> dependencies, String worldGeneratorUri) {
        this.dependencies.addAll(dependencies);

        if (worldGeneratorUri != null) {
            this.worldGeneratorUri = worldGeneratorUri;
        }
    }

    /**
     * Set up and start the engine as configured via this environment.
     * <p>
     * Every instance should be shut down properly by calling {@link #tearDown()}.
     */
    protected void setup() {
        mockPathManager();
        try {
            host = createHost();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ScreenGrabber grabber = mock(ScreenGrabber.class);
        hostContext.put(ScreenGrabber.class, grabber);
        CoreRegistry.put(GameEngine.class, host);
    }

    /**
     * Shut down a previously started testing environment.
     * <p>
     * Used to properly shut down and clean up a testing environment set up and started with {@link #setup()}.
     */
    protected void tearDown() {
        engines.forEach(TerasologyEngine::shutdown);
        engines.forEach(TerasologyEngine::cleanup);
        engines.clear();
        try {
            pathManagerCleaner.close();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        host = null;
        hostContext = null;
    }

    /**
     * Creates a dummy entity with RelevanceRegion component to force a chunk's generation and availability. Blocks
     * while waiting for the chunk to become loaded
     *
     * @param blockPos the block position of the dummy entity. Only the chunk containing this position will be
     *         available
     */
    public void forceAndWaitForGeneration(Vector3ic blockPos) {
        WorldProvider worldProvider = hostContext.get(WorldProvider.class);
        if (worldProvider.isBlockRelevant(blockPos)) {
            return;
        }

        ListenableFuture<ChunkRegionFuture> chunkRegion = makeBlocksRelevant(new BlockRegion(blockPos));
        runUntil(chunkRegion);
    }

    /**
     *
     * @param blocks blocks to mark as relevant
     * @return relevant chunks
     */
    public ListenableFuture<ChunkRegionFuture> makeBlocksRelevant(BlockRegionc blocks) {
        BlockRegion desiredChunkRegion = Chunks.toChunkRegion(new BlockRegion(blocks));
        return makeChunksRelevant(desiredChunkRegion, blocks.center(new Vector3f()));
    }

    @SuppressWarnings("unused")
    public ListenableFuture<ChunkRegionFuture> makeChunksRelevant(BlockRegion chunks) {
        // Pick a central point (in block coordinates).
        Vector3f centerPoint = chunkRegionToNewBlockRegion(chunks).center(new Vector3f());

        return makeChunksRelevant(chunks, centerPoint);
    }

    public ListenableFuture<ChunkRegionFuture> makeChunksRelevant(BlockRegion chunks, Vector3fc centerBlock) {
        checkArgument(chunks.contains(Chunks.toChunkPos(new Vector3i(centerBlock, RoundingMode.FLOOR))),
                "centerBlock should %s be within the region %s",
                centerBlock, chunkRegionToNewBlockRegion(chunks));
        Vector3i desiredSize = chunks.getSize(new Vector3i());

        EntityManager entityManager = verifyNotNull(hostContext.get(EntityManager.class));
        RelevanceSystem relevanceSystem = verifyNotNull(hostContext.get(RelevanceSystem.class));
        ChunkRegionFuture listener = ChunkRegionFuture.create(entityManager, relevanceSystem, centerBlock, desiredSize);
        return listener.getFuture();
    }

    private BlockRegionc chunkRegionToNewBlockRegion(BlockRegionc chunks) {
        BlockRegion blocks = new BlockRegion(chunks);
        return blocks.transform(new Matrix4f().scaling(new Vector3f(Chunks.CHUNK_SIZE)));
    }

    public <T> T runUntil(ListenableFuture<T> future) {
        boolean timedOut = runUntil(future::isDone);
        if (timedOut) {
            // TODO: if runUntil returns timedOut but does not throw an exception, it
            //     means it hit DEFAULT_GAME_TIME_TIMEOUT but not SAFETY_TIMEOUT, and
            //     that's a weird interface due for a revision.
            future.cancel(true);  // let it know we no longer expect results
            throw new UncheckedTimeoutException("No result within default timeout.");
        }
        try {
            return future.get(0, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new UncheckedExecutionException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for " + future, e);
        } catch (TimeoutException e) {
            throw new UncheckedTimeoutException(
                    "Checked isDone before calling get, so this shouldn't happen.", e);
        }
    }

    /**
     * Runs tick() on the engine until f evaluates to true or DEFAULT_GAME_TIME_TIMEOUT milliseconds have passed in game time
     * @return true if execution timed out
     */
    public boolean runUntil(Supplier<Boolean> f) {
        return runWhile(() -> !f.get());
    }

    /**
     * Runs tick() on the engine until f evaluates to true or gameTimeTimeoutMs has passed in game time
     *
     * @return true if execution timed out
     */
    public boolean runUntil(long gameTimeTimeoutMs, Supplier<Boolean> f) {
        return runWhile(gameTimeTimeoutMs, () -> !f.get());
    }

    /**
     * Runs tick() on the engine while f evaluates to true or until DEFAULT_GAME_TIME_TIMEOUT milliseconds have passed
     * @return true if execution timed out
     */
    public boolean runWhile(Supplier<Boolean> f) {
        return runWhile(DEFAULT_GAME_TIME_TIMEOUT, f);
    }

    /**
     * Runs tick() on the engine while f evaluates to true or until gameTimeTimeoutMs has passed in game time.
     *
     * @return true if execution timed out
     */
    public boolean runWhile(long gameTimeTimeoutMs, Supplier<Boolean> f) {
        boolean timedOut = false;
        Time hostTime = getHostContext().get(Time.class);
        long startRealTime = System.currentTimeMillis();
        long startGameTime = hostTime.getGameTimeInMs();

        while (f.get() && !timedOut) {
            Thread.yield();
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException(String.format("Thread %s interrupted while waiting for %s.",
                        Thread.currentThread(), f));
            }
            for (TerasologyEngine terasologyEngine : engines) {
                boolean keepRunning = terasologyEngine.tick();
                if (!keepRunning && terasologyEngine == host) {
                    throw new RuntimeException("Host has shut down: " + host.getStatus());
                }
            }

            // handle safety timeout
            if (System.currentTimeMillis() - startRealTime > safetyTimeoutMs) {
                timedOut = true;
                // If we've passed the _safety_ timeout, throw an exception.
                throw new UncheckedTimeoutException("MTE Safety timeout exceeded. See setSafetyTimeoutMs()");
            }

            // handle game time timeout
            if (hostTime.getGameTimeInMs() - startGameTime > gameTimeTimeoutMs) {
                // If we've passed the user-specified timeout but are still under the
                // safety threshold, set timed-out status without throwing.
                timedOut = true;
            }
        }

        return timedOut;
    }

    /**
     * Creates a new client and connects it to the host
     *
     * @return the created client's context object
     */
    public Context createClient() throws IOException {
        TerasologyEngine terasologyEngine = createHeadlessEngine();
        terasologyEngine.getFromEngineContext(Config.class).getRendering().setViewDistance(ViewDistance.LEGALLY_BLIND);

        terasologyEngine.changeState(new StateMainMenu());
        connectToHost(terasologyEngine);
        Context context = terasologyEngine.getState().getContext();
        context.put(ScreenGrabber.class, hostContext.get(ScreenGrabber.class));
        return terasologyEngine.getState().getContext();
    }

    /**
     * The engines active in this instance of the module testing environment.
     * <p>
     * Engines are created for the host and connecting clients.
     *
     * @return list of active engines
     */
    public List<TerasologyEngine> getEngines() {
        return Lists.newArrayList(engines);
    }

    /**
     * Get the host context for this module testing environment.
     * <p>
     * The host context will be null if the testing environment has not been set up via {@link
     * ModuleTestingEnvironment#setup()} beforehand.
     *
     * @return the engine's host context, or null if not set up yet
     */
    public Context getHostContext() {
        return hostContext;
    }


    /**
     * @return the current safety timeout
     */
    public long getSafetyTimeoutMs() {
        return safetyTimeoutMs;
    }

    /**
     * Sets the safety timeout (default 30s).
     *
     * @param safetyTimeoutMs The safety timeout applies to {@link #runWhile runWhile} and related helpers, and
     * stops execution when the specified number of real time milliseconds has passed. Note that this is different from
     * the timeout parameter of those methods, which is specified in game time.
     * <p>
     * When a single {@code run*} helper invocation exceeds the safety timeout, MTE asserts false to explicitly fail the test.
     * <p>
     * The safety timeout exists to prevent indefinite execution in Jenkins or long IDE test runs, and should be
     * adjusted as needed so that tests pass reliably in all environments.
     */
    public void setSafetyTimeoutMs(long safetyTimeoutMs) {
        this.safetyTimeoutMs = safetyTimeoutMs;
    }

    private TerasologyEngine createHeadlessEngine() throws IOException {
        TerasologyEngineBuilder terasologyEngineBuilder = new TerasologyEngineBuilder();
        terasologyEngineBuilder
                .add(new WithUnittestModule())
                .add(new HeadlessGraphics())
                .add(new HeadlessTimer())
                .add(new HeadlessAudio())
                .add(new HeadlessInput());

        return createEngine(terasologyEngineBuilder);
    }

    private TerasologyEngine createHeadedEngine() throws IOException {
        EngineSubsystem audio = new LwjglAudio();
        TerasologyEngineBuilder terasologyEngineBuilder = new TerasologyEngineBuilder()
                .add(new WithUnittestModule())
                .add(audio)
                .add(new LwjglGraphics())
                .add(new LwjglTimer())
                .add(new LwjglInput())
                .add(new OpenVRInput());

        return createEngine(terasologyEngineBuilder);
    }

    private TerasologyEngine createEngine(TerasologyEngineBuilder terasologyEngineBuilder) throws IOException {
        System.setProperty(ModuleManager.LOAD_CLASSPATH_MODULES_PROPERTY, "true");

        // create temporary home paths so the MTE engines don't overwrite config/save files in your real home path
        // FIXME: Collisions when attempting to do multiple simultaneous createEngines.
        //    (PathManager will need to be set in Context, not a process-wide global.)
        Path path = Files.createTempDirectory("terasology-mte-engine");
        PathManager.getInstance().useOverrideHomePath(path);
        logger.info("Created temporary engine home path: {}", path);

        // JVM will delete these on normal termination but not exceptions.
        path.toFile().deleteOnExit();

        TerasologyEngine terasologyEngine = terasologyEngineBuilder.build();
        terasologyEngine.initialize();
        registerCurrentDirectoryIfModule(terasologyEngine);

        engines.add(terasologyEngine);
        return terasologyEngine;
    }

    /**
     * In standalone module environments (i.e. Jenkins CI builds) the CWD is the module under test. When it uses MTE
     * it very likely needs to load itself as a module, but it won't be loadable from the typical path such as
     * ./modules. This means that modules using MTE would always fail CI tests due to failing to load themselves.
     * <p>
     * For these cases we try to load the CWD (via the installPath) as a module and put it in the global module
     * registry.
     * <p>
     * This process is based on how ModuleManagerImpl uses ModulePathScanner to scan for available modules.
     *
     * @param terasologyEngine
     */
    private void registerCurrentDirectoryIfModule(TerasologyEngine terasologyEngine) {
        Path installPath = PathManager.getInstance().getInstallPath();
        ModuleManager moduleManager = terasologyEngine.getFromEngineContext(ModuleManager.class);
        ModuleRegistry registry = moduleManager.getRegistry();
        ModuleMetadataJsonAdapter metadataReader = moduleManager.getModuleMetadataReader();
        moduleManager.getModuleFactory().getModuleMetadataLoaderMap()
                .put(TerasologyConstants.MODULE_INFO_FILENAME.toString(), metadataReader);


        try {
            Module module = moduleManager.getModuleFactory().createModule(installPath.toFile());
            if (module != null) {
                registry.add(module);
                logger.info("Added install path as module: {}", installPath);
            } else {
                logger.info("Install path does not appear to be a module: {}", installPath);
            }
        } catch (IOException e) {
            logger.warn("Could not read install path as module at " + installPath);
        }
    }

    protected void mockPathManager() {
        PathManager originalPathManager = PathManager.getInstance();
        pathManager = spy(originalPathManager);
        when(pathManager.getModulePaths()).thenReturn(Collections.emptyList());
        pathManagerCleaner = new PathManagerProvider.Cleaner(originalPathManager, pathManager);
        PathManagerProvider.setPathManager(pathManager);
    };

    private TerasologyEngine createHost() throws IOException {
        TerasologyEngine terasologyEngine = createHeadlessEngine();
        terasologyEngine.getFromEngineContext(SystemConfig.class).writeSaveGamesEnabled.set(false);
        terasologyEngine.subscribeToStateChange(new HeadlessStateChangeListener(terasologyEngine));
        terasologyEngine.changeState(new TestingStateHeadlessSetup(dependencies, worldGeneratorUri));

        doneLoading = false;
        terasologyEngine.subscribeToStateChange(() -> {
            GameState newState = terasologyEngine.getState();
            logger.debug("New engine state is {}", terasologyEngine.getState());
            if (newState instanceof org.terasology.engine.core.modes.StateIngame) {
                hostContext = newState.getContext();
                if (hostContext == null) {
                    logger.warn("hostContext is NULL in engine state {}", newState);
                }
                doneLoading = true;
            } else if (newState instanceof org.terasology.engine.core.modes.StateLoading) {
                org.terasology.engine.registry.CoreRegistry.put(GameEngine.class, terasologyEngine);
            }
        });

        boolean keepTicking;
        while (!doneLoading) {
            keepTicking = terasologyEngine.tick();
            if (!keepTicking) {
                throw new RuntimeException(String.format(
                        "Engine stopped ticking before we got in game. Current state: %s",
                        terasologyEngine.getState()
                ));
            }
        }
        return terasologyEngine;
    }

    private void connectToHost(TerasologyEngine client) {
        CoreRegistry.put(Config.class, client.getFromEngineContext(Config.class));
        JoinStatus joinStatus = null;
        try {
            joinStatus = client.getFromEngineContext(NetworkSystem.class).join("localhost", 25777);
        } catch (InterruptedException e) {
            logger.warn("Interrupted while joining: ", e);
        }

        client.changeState(new StateLoading(joinStatus));
        CoreRegistry.put(GameEngine.class, client);

        runUntil(() -> client.getState() instanceof StateIngame);
    }
}
