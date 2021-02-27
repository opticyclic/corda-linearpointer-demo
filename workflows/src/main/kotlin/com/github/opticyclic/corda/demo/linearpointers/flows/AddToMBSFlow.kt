package com.github.opticyclic.corda.demo.linearpointers.flows

import co.paralleluniverse.fibers.Suspendable
import com.github.opticyclic.corda.demo.linearpointers.contracts.MBSContract
import com.github.opticyclic.corda.demo.linearpointers.states.MBSState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

/**
 * Add a mortgage to the MBS using a LinearPointer
 */
@InitiatingFlow
@StartableByRPC
class AddToMBSFlow(private val mbsId: UniqueIdentifier, private val mortgageId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each checkpoint is reached.
     * See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object FINDING : Step("Finding the states.")
        object BUILDING : Step("Building a new transaction.")
        object SIGNING : Step("Signing the transaction with our private key.")

        object FINALISING : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                FINDING,
                BUILDING,
                SIGNING,
                FINALISING
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        //Lookup the MBS in the vault
        progressTracker.currentStep = FINDING
        val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(mbsId))
        val stateAndRef = serviceHub.vaultService.queryBy<MBSState>(inputCriteria).states.single()
        val mbs = stateAndRef.state.data

        progressTracker.currentStep = BUILDING

        //Create the output.
        val outputState = mbs.withNewMortgage(mortgageId)
        val command = Command(MBSContract.Commands.Add(), outputState.participants.map { it.owningKey })

        //Build the transaction.
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txBuilder = TransactionBuilder(notary)
                .addInputState(stateAndRef)
                .addOutputState(outputState, MBSContract.MBS_CONTRACT_ID)
                .addCommand(command)

        // Verify that the transaction is valid.
        txBuilder.verify(serviceHub)

        //Sign the transaction with our identity.
        progressTracker.currentStep = SIGNING
        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

        //Notarise and record the transaction in our vault.
        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(partSignedTx, emptyList(), FINALISING.childProgressTracker()))
    }
}
