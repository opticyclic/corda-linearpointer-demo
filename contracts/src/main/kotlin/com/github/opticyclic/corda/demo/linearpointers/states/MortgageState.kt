package com.github.opticyclic.corda.demo.linearpointers.states

import com.github.opticyclic.corda.demo.linearpointers.contracts.MortgageContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * A deliberately simple model of a fixed rate mortgage.
 *
 * The [lender] is modelled as a node on the network but the mortgage [owner] is just a String for simplicity.
 * The [principal] and [rate] are the mortgage amount and fixed rate respectively.
 * The [term] is the length of the mortgage in years.
 */
@BelongsToContract(MortgageContract::class)
data class MortgageState(val lender: Party,
                         val owner: String,
                         val principal: Long,
                         val rate: Double,
                         val term: Int,
                         override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {

    override val participants: List<AbstractParty> get() = listOf(lender)

    fun withNewLender(newLender: Party) = copy(lender = newLender)
}
