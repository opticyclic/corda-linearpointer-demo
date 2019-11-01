package com.github.opticyclic.corda.demo.linearpointers.flows

import co.paralleluniverse.fibers.Suspendable
import com.github.opticyclic.corda.demo.linearpointers.contracts.MBSContract
import com.github.opticyclic.corda.demo.linearpointers.states.MBSState
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

/**
 * This flow creates an MBS on ledger.
 * It should only be run by the issuer but no such checks are made for this demo.
 */
@InitiatingFlow
@StartableByRPC
class CreateMBSFlow() : FlowLogic<SignedTransaction>() {
    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each checkpoint is reached.
     * See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object BUILDING : Step("Building a new transaction.")
        object SIGNING : Step("Signing the transaction with our private key.")

        object FINALISING : Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                BUILDING,
                SIGNING,
                FINALISING
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = BUILDING

        //Create the output.
        val outputState = MBSState(serviceHub.myInfo.legalIdentities.first())
        val command = Command(MBSContract.Commands.Create(), outputState.participants.map { it.owningKey })

        //Build the transaction.
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txBuilder = TransactionBuilder(notary)
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
