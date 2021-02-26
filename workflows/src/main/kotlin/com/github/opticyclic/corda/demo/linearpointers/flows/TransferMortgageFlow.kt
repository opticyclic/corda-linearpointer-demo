package com.github.opticyclic.corda.demo.linearpointers.flows

import co.paralleluniverse.fibers.Suspendable
import com.github.opticyclic.corda.demo.linearpointers.contracts.MortgageContract
import com.github.opticyclic.corda.demo.linearpointers.states.MortgageState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class TransferMortgageFlow(private val mortgageId: UniqueIdentifier, private val newLender: Party) : FlowLogic<SignedTransaction>() {
    /**
     * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each checkpoint is reached.
     * See the 'progressTracker.currentStep' expressions within the call() function.
     */
    companion object {
        object BUILDING : ProgressTracker.Step("Building a new transaction.")
        object SIGNING : ProgressTracker.Step("Signing the transaction with our private key.")

        object FINALISING : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
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

        //Get the mortgage specified by the linearId from the vault.
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(mortgageId))
        val mortgageStateAndRef = serviceHub.vaultService.queryBy<MortgageState>(queryCriteria).states.single()
        val inputMortgage = mortgageStateAndRef.state.data

        //Validate that the current lender is doing the transfer
        if (ourIdentity != inputMortgage.lender) {
            throw IllegalArgumentException("A mortgage transfer can only be initiated by the current lender.")
        }

        //Transfer the mortgage to the new lender
        val outputMortgage = inputMortgage.withNewLender(newLender)
        val signers = (inputMortgage.participants + newLender).map { it.owningKey }
        val command = Command(MortgageContract.Commands.Transfer(), signers)

        //Build the transaction.
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val txBuilder = TransactionBuilder(notary)
                .addInputState(mortgageStateAndRef)
                .addOutputState(outputMortgage, MortgageContract.MORTGAGE_CONTRACT_ID)
                .addCommand(command)

        //Verify and sign the transaction with our identity.
        progressTracker.currentStep = SIGNING

        txBuilder.verify(serviceHub)
        val locallySignedTx = serviceHub.signInitialTransaction(txBuilder)

        //Notarise and record the transaction in both vaults.
        progressTracker.currentStep = FINALISING

        val lenderSession = initiateFlow(newLender)
        val sessions = listOf(lenderSession)
        val fullySignedTx = subFlow(CollectSignaturesFlow(locallySignedTx, sessions))
        return subFlow(FinalityFlow(fullySignedTx, sessions))
    }
}

@InitiatedBy(TransferMortgageFlow::class)
class MortgageTransferFlowResponder(private val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be a mortgage transaction" using (output is MortgageState)
            }
        }
        val txId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(counterpartySession, expectedTxId = txId.id))
    }
}
