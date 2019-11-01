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
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val signers = command.signers.toSet()

        when (command.value) {
            is Commands.Create -> verifyCreate(tx, signers)
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

}
