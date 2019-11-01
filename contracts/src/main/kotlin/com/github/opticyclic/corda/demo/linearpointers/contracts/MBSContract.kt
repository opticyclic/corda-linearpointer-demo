package com.github.opticyclic.corda.demo.linearpointers.contracts

import com.github.opticyclic.corda.demo.linearpointers.states.MBSState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * A basic contract to create a new [MBSState] with minimal checks.
 */
class MBSContract : Contract {
    companion object {
        @JvmStatic
        val MBS_CONTRACT_ID = MBSContract::class.java.name
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
        "No inputs should be consumed when issuing an MBS." using (tx.inputs.isEmpty())
        "Only one output state should be created." using (tx.outputs.size == 1)

        //MBS specific constraints.
        val out = tx.outputsOfType<MBSState>().single()
        "The issuer must be a signer." using (signers.containsAll(out.participants.map { it.owningKey }))
        "Only the issuer can be a signer." using (signers.size == 1)
    }

}
