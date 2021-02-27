package com.github.opticyclic.corda.demo.linearpointers.flows

import com.github.opticyclic.corda.demo.linearpointers.AgentListener
import com.github.opticyclic.corda.demo.linearpointers.states.MBSState
import com.github.opticyclic.corda.demo.linearpointers.states.MortgageState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
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
class EndToEndTest {
    private lateinit var network: MockNetwork
    private lateinit var investmentBank: StartedMockNode
    private lateinit var valleyFinancial: StartedMockNode
    private lateinit var pcmBank: StartedMockNode
    private lateinit var investor: StartedMockNode

    @BeforeClass
    fun setup() {
        network = MockNetwork(MockNetworkParameters(
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.github.opticyclic.corda.demo.linearpointers.contracts"),
                        TestCordapp.findCordapp("com.github.opticyclic.corda.demo.linearpointers.flows")
                )))
        investmentBank = network.createPartyNode(CordaX500Name("Investment Bank", "London", "GB"))
        valleyFinancial = network.createPartyNode(CordaX500Name("Valley Financial", "London", "GB"))
        pcmBank = network.createPartyNode(CordaX500Name("PCM Bank", "Dallas", "US"))
        investor = network.createPartyNode(CordaX500Name("Motus Corp", "London", "GB"))
        network.runNetwork()
    }

    @AfterClass
    fun tearDown() {
        network.stopNodes()
    }

    @Test
    fun `End To End Test`() {
        //Valley Financial creates 3 mortgages
        val vfLoan1 = createMortgage(valleyFinancial, "Jonah Ward", 320000, 1.78, 2)
        val vfLoan2 = createMortgage(valleyFinancial, "Nicholas Davis", 500000L, 1.64, 5)
        val vfLoan3 = createMortgage(valleyFinancial, "Sofia Beech", 900000L, 8.5, 25)

        //PCM Bank creates 2 mortgages
        val pcmLoan1 = createMortgage(pcmBank, "Emmett Ryan", 452000L, 1.99, 3)
        val pcmLoan2 = createMortgage(pcmBank, "Nicole Irving", 750000L, 1.76, 5)

        //Valley Financial sells their mortgages to the Investment Bank
        transferMortgage(valleyFinancial, vfLoan1)
        transferMortgage(valleyFinancial, vfLoan2)
        transferMortgage(valleyFinancial, vfLoan3)

        //PCM Bank sells their mortgages to the Investment Bank
        transferMortgage(pcmBank, pcmLoan1)
        transferMortgage(pcmBank, pcmLoan2)

        //Investment Bank creates an empty MBS
        val createFlow = CreateMBSFlow()
        val future = investmentBank.startFlow(createFlow)
        network.runNetwork()
        val transaction = future.getOrThrow()
        val data = transaction.tx.outputs.first().data as MBSState
        val mbsId = data.linearId

        //Investment Bank adds the mortgages to the MBS using Linear Pointers
        addMortgage(mbsId, vfLoan1)
        addMortgage(mbsId, vfLoan2)
        addMortgage(mbsId, vfLoan3)
        addMortgage(mbsId, pcmLoan1)
        addMortgage(mbsId, pcmLoan2)

        //Get the latest version and check that the mortgages are now on the MBS
        val mbsStates = investmentBank.services.vaultService.queryBy<MBSState>().states
        Assert.assertEquals(mbsStates.size, 1)
        val updatedMBS = mbsStates.first().state.data
        Assert.assertEquals(updatedMBS.mortgages.size, 5)

        //Investment Bank sells the MBS to an Investor
        val sellFlow = TransferMBSFlow(mbsId, investor.info.singleIdentity())
        val futureSell = investmentBank.startFlow(sellFlow)
        network.runNetwork()
        futureSell.getOrThrow()

        //Check that the Investor can see the MBS but not the Mortgages
        val transferredStates = investor.services.vaultService.queryBy<MBSState>().states
        Assert.assertEquals(transferredStates.size, 1)
        val soldMBS = transferredStates.first().state.data
        Assert.assertEquals(soldMBS.mortgages.size, 5)
        val stateAndRef = soldMBS.mortgages.first().resolve(investor.services)
        Assert.assertTrue(false, "The command above should have thrown an exception")
    }

    private fun createMortgage(bank: StartedMockNode, owner: String, mortgageAmount: Long, rate: Double, termInYears: Int): UniqueIdentifier {
        val flow = SelfIssueMortgageFlow(owner, mortgageAmount, rate, termInYears)
        val future = bank.startFlow(flow)
        network.runNetwork()
        val transaction = future.getOrThrow()
        val data = transaction.tx.outputs.first().data as MortgageState
        return data.linearId
    }

    private fun transferMortgage(bank: StartedMockNode, mortgageId: UniqueIdentifier) {
        val flow = TransferMortgageFlow(mortgageId, investmentBank.info.singleIdentity())
        val future = bank.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()
    }

    private fun addMortgage(mbsId: UniqueIdentifier, mortgageId: UniqueIdentifier) {
        val flow = AddToMBSFlow(mbsId, mortgageId)
        val future = investmentBank.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()
    }
}
