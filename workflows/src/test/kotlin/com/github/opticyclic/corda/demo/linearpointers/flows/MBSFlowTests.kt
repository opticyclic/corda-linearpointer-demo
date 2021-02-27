package com.github.opticyclic.corda.demo.linearpointers.flows

import com.github.opticyclic.corda.demo.linearpointers.AgentListener
import com.github.opticyclic.corda.demo.linearpointers.states.MBSState
import com.github.opticyclic.corda.demo.linearpointers.states.MortgageState
import net.corda.core.identity.CordaX500Name
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
class MBSFlowTests {
    private lateinit var network: MockNetwork
    private lateinit var investmentBank: StartedMockNode

    @BeforeClass
    fun setup() {
        network = MockNetwork(MockNetworkParameters(
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.github.opticyclic.corda.demo.linearpointers.contracts"),
                        TestCordapp.findCordapp("com.github.opticyclic.corda.demo.linearpointers.flows")
                )))
        investmentBank = network.createPartyNode(CordaX500Name("Investment Bank", "London", "GB"))
        network.runNetwork()
    }

    @AfterClass
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `Create an MBS with no mortgages`() {
        val flow = CreateMBSFlow()
        val future = investmentBank.startFlow(flow)
        network.runNetwork()
        val signedTx = future.getOrThrow()

        val output = signedTx.tx.outputs.single().data as MBSState
        //Check for the MBS in the vault
        val linearStateCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(output.linearId))
        val states = investmentBank.services.vaultService.queryBy<MBSState>(linearStateCriteria).states
        Assert.assertEquals(1, states.size)
        val recordedState = states.single().state.data
        Assert.assertEquals(recordedState.issuer, investmentBank.info.singleIdentity())
        Assert.assertEquals(recordedState.mortgages.size, 0)
        Assert.assertEquals(recordedState.price, 0.0)
    }

    @Test(dependsOnMethods = ["Create an MBS with no mortgages"])
    fun `Add a mortgage to an MBS`() {
        //Find the MBS
        val states = investmentBank.services.vaultService.queryBy<MBSState>().states
        Assert.assertEquals(1, states.size)
        val mbs = states.single().state.data

        //Verify that it has no mortgages associated with it
        Assert.assertEquals(mbs.mortgages.size, 0)

        //Create a new Mortgage
        val flow = SelfIssueMortgageFlow("Owe Knerr", 500000, 1.9, 10)
        val future = investmentBank.startFlow(flow)
        network.runNetwork()
        val transaction = future.getOrThrow()
        val data = transaction.tx.outputs.first().data as MortgageState
        val mortgageId = data.linearId

        //Add it to the MBS
        val flowAdd = AddToMBSFlow(mbs.linearId, mortgageId)
        val futureAdd = investmentBank.startFlow(flowAdd)
        network.runNetwork()
        futureAdd.getOrThrow()

        //Get the latest version and check that there is now a mortgage on the MBS
        val mbsStates = investmentBank.services.vaultService.queryBy<MBSState>().states
        Assert.assertEquals(mbsStates.size, 1)
        val updatedMBS = mbsStates.first().state.data
        Assert.assertEquals(updatedMBS.mortgages.size, 1)

        //Check that we can resolve the mortgage
        val linearPointer = updatedMBS.mortgages.first()
        val stateAndRef = linearPointer.resolve(investmentBank.services)
        val mortgage = stateAndRef.state.data

        //Check that it is the correct one
        Assert.assertEquals(mortgage.rate, 1.9)
        Assert.assertEquals(mortgage.term, 10)
    }
}
