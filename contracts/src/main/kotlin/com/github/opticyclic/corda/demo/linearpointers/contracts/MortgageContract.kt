package com.github.opticyclic.corda.demo.linearpointers.contracts

import com.github.opticyclic.corda.demo.linearpointers.states.MortgageState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * A basic contract to create a new [MortgageState] with minimal checks.
 */
class MortgageContract : Contract {
    companion object {
        @JvmStatic
        val MORTGAGE_CONTRACT_ID = MortgageContract::class.java.name
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Transfer : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val signers = command.signers.toSet()

        when (command.value) {
            is Commands.Create -> verifyCreate(tx, signers)
            is Commands.Transfer -> verifyTransfer(tx, signers)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Generic constraints around the transaction.
        "No inputs should be consumed when issuing a mortgage." using (tx.inputs.isEmpty())
        "Only one output state should be created." using (tx.outputs.size == 1)

        //Simple mortgage specific constraints.
        val out = tx.outputsOfType<MortgageState>().single()
        "The lender must be a signer." using (signers.containsAll(out.participants.map { it.owningKey }))
        "Only the lender can be a signer." using (signers.size == 1)
        "The mortgage amount must be non-negative." using (out.principal > 0)
        "The mortgage term must be less than a year." using (out.term > 1)
    }

    private fun verifyTransfer(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Generic constraints around the transaction.
        "A transfer should only consume one input state: " + tx.inputs.size using (tx.inputs.size == 1)
        "An transfer should only create one output state: " + tx.outputs.size using (tx.outputs.size == 1)

        //Check that there has been a transfer of ownership
        val input = tx.inputsOfType<MortgageState>().single()
        val output = tx.outputsOfType<MortgageState>().single()
        "Only the lender may change." using (input == output.withNewLender(input.lender))
        "The lender must change in order to be a transfer." using (input.lender != output.lender)
        "All of the participants must be signers." using (signers.containsAll(tx.outputStates.first().participants.map { it.owningKey }))
    }
}
