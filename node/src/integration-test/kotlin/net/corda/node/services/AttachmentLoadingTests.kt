package net.corda.node.services

import net.corda.core.flows.FlowLogic
import net.corda.core.internal.copyToDirectory
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.toPath
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.cordappForClasses
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.URL
import java.net.URLClassLoader

class AttachmentLoadingTests {
    private companion object {
        val isolatedJAR: URL = AttachmentLoadingTests::class.java.getResource("isolated.jar")
        val isolatedClassLoader = URLClassLoader(arrayOf(isolatedJAR))

        fun loadFromIsolated(className: String): Class<*> = Class.forName(className, false, isolatedClassLoader)
    }

    @Test
    fun `attachments retrieved over the network are not used for verification`() {
        val initiatorClass = loadFromIsolated("net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Initiator").asSubclass(FlowLogic::class.java)
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = emptySet())) {
            val bankACordappsDir = (baseDirectory(DUMMY_BANK_A_NAME) / "cordapps").createDirectories()
            isolatedJAR.toPath().copyToDirectory(bankACordappsDir)
            val bankA = startNode(providedName = DUMMY_BANK_A_NAME).getOrThrow()
            val bankB = startNode(NodeParameters(
                    providedName = DUMMY_BANK_B_NAME,
                    // Give B just the flows, not the contract
                    additionalCordapps = listOf(cordappForClasses(
                            initiatorClass,
                            loadFromIsolated("net.corda.finance.contracts.isolated.IsolatedDummyFlow\$Acceptor"),
                            loadFromIsolated("net.corda.finance.contracts.isolated.IsolatedDummyFlow")
                    ))
            )).getOrThrow()
            assertThatThrownBy {
                bankA.rpc.startFlowDynamic(initiatorClass, bankB.nodeInfo.singleIdentity()).returnValue.getOrThrow()
            }.hasMessageContaining("net.corda.finance.contracts.isolated.AnotherDummyContract")
        }
    }
}
