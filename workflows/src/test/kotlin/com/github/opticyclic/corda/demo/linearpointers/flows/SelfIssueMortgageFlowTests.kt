package com.github.opticyclic.corda.demo.linearpointers.flows

import com.github.opticyclic.corda.demo.linearpointers.AgentListener
import com.github.opticyclic.corda.demo.linearpointers.states.MortgageState
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.testng.Assert
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.Listeners
import org.testng.annotations.Test

@Listeners(AgentListener::class)
class SelfIssueMortgageFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var pcmBank: StartedMockNode

    @BeforeClass
    fun setup() {
        network = MockNetwork(MockNetworkParameters(
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.github.opticyclic.corda.demo.linearpointers.contracts"),
                        TestCordapp.findCordapp("com.github.opticyclic.corda.demo.linearpointers.flows")
                )))
        pcmBank = network.createPartyNode()
        network.runNetwork()
    }

    @AfterClass
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `flow records the mortgage in the  vault`() {
        val owner = "Joe Bloggs"
        val mortgageAmount = 500000L
        val rate = 5.5
        val termInYears = 25
        val flow = SelfIssueMortgageFlow(owner, mortgageAmount, rate, termInYears)
        val future = pcmBank.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        val output = signedTx.tx.outputs.single().data as MortgageState
        //Check for the mortgage in the vault
        val linearStateCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(output.linearId))
        val states = pcmBank.services.vaultService.queryBy<MortgageState>(linearStateCriteria).states
        Assert.assertEquals(1, states.size)
        val recordedState = states.single().state.data
        Assert.assertEquals(recordedState.principal, mortgageAmount)
        Assert.assertEquals(recordedState.lender, pcmBank.info.singleIdentity())
    }
}
