// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.moduletestingenvironment;

import com.google.common.collect.Lists;
import org.joml.Vector3i;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.terasology.engine.context.Context;
import org.terasology.engine.core.Time;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.logic.players.LocalPlayer;
import org.terasology.engine.logic.players.event.ResetCameraEvent;
import org.terasology.moduletestingenvironment.extension.Dependencies;
import org.terasology.engine.network.ClientComponent;
import org.terasology.engine.registry.In;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.BlockManager;


@ExtendWith(MTEExtension.class)
@Dependencies({"engine", "ModuleTestingEnvironment"})
public class ExampleTest {

    @In
    private WorldProvider worldProvider;
    @In
    private BlockManager blockManager;
    @In
    private EntityManager entityManager;
    @In
    private Time time;
    @In
    private ModuleTestingHelper helper;

    @Test
    public void testClientConnection() {
        int currentClients = Lists.newArrayList(entityManager.getEntitiesWith(ClientComponent.class)).size();

        // create some clients (the library connects them automatically)
        Context clientContext1 = helper.createClient();
        Context clientContext2 = helper.createClient();

        int expectedClients = currentClients + 2;

        // wait for both clients to be known to the server
        helper.runUntil(() -> Lists.newArrayList(entityManager.getEntitiesWith(ClientComponent.class)).size() >= expectedClients);
        Assertions.assertEquals(expectedClients,
                Lists.newArrayList(entityManager.getEntitiesWith(ClientComponent.class)).size());
    }

    @Test
    public void testRunWhileTimeout() {
        // run while a condition is true or until a timeout passes
        long expectedTime = time.getGameTimeInMs() + 500;
        boolean timedOut = helper.runWhile(500, () -> true);
        Assertions.assertTrue(timedOut);
        long currentTime = time.getGameTimeInMs();
        Assertions.assertTrue(currentTime >= expectedTime);
    }

    @Test
    public void testSendEvent() {
        Context clientContext = helper.createClient();

        // send an event to a client's local player just for fun
        clientContext.get(LocalPlayer.class).getClientEntity().send(new ResetCameraEvent());
    }

    @Test
    public void testWorldProvider() {
        // wait for a chunk to be generated
        helper.forceAndWaitForGeneration(new Vector3i());

        // set a block's type and immediately read it back
        worldProvider.setBlock(new org.joml.Vector3i(), blockManager.getBlock("engine:air"));
        Assertions.assertEquals("engine:air", worldProvider.getBlock(new org.joml.Vector3i()).getURI().toString());
    }
}
