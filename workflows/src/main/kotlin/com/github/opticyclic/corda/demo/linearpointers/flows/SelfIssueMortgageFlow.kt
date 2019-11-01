package com.github.opticyclic.corda.demo.linearpointers.flows

import co.paralleluniverse.fibers.Suspendable
import com.github.opticyclic.corda.demo.linearpointers.contracts.MortgageContract
import com.github.opticyclic.corda.demo.linearpointers.states.MortgageState
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
 * This flow allows a bank to save a mortgage on ledger.
 */
@InitiatingFlow
@StartableByRPC
class SelfIssueMortgageFlow(val owner: String, val mortgageAmount: Long, val rate: Double, val termInYears: Int) : FlowLogic<SignedTransaction>() {
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
        val mortgageState = MortgageState(serviceHub.myInfo.legalIdentities.first(), owner, mortgageAmount, rate, termInYears)
        val command = Command(MortgageContract.Commands.Create(), mortgageState.participants.map { it.owningKey })

        //Build the transaction.
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(mortgageState, MortgageContract.MORTGAGE_CONTRACT_ID)
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
