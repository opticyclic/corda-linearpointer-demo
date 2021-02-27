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
        class Add : TypeOnlyCommandData(), Commands
        class Transfer : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val signers = command.signers.toSet()

        when (command.value) {
            is Commands.Create -> verifyCreate(tx, signers)
            is Commands.Add -> verifyAdd(tx, signers)
            is Commands.Transfer -> verifyTransfer(tx, signers)
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

    private fun verifyAdd(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Generic constraints around the transaction.
        "Only one input should be consumed when adding a mortgage to an MBS: " + tx.inputs using (tx.inputs.size == 1)
        "Only one output state should be created: " + tx.outputs using (tx.outputs.size == 1)

        //MBS specific constraints.
        val out = tx.outputsOfType<MBSState>().single()
        "The issuer must be a signer." using (signers.containsAll(out.participants.map { it.owningKey }))
        "Only the issuer can be a signer." using (signers.size == 1)
    }

    private fun verifyTransfer(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        //Generic constraints around the transaction.
        "A transfer should only consume one input state: " + tx.inputs.size using (tx.inputs.size == 1)
        "An transfer should only create one output state: " + tx.outputs.size using (tx.outputs.size == 1)

        //Check that there has been a transfer of ownership
        val input = tx.inputsOfType<MBSState>().single()
        val output = tx.outputsOfType<MBSState>().single()
        "The issuer must change in order to be a transfer." using (input.issuer != output.issuer)
    }

}
