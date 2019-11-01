package com.github.opticyclic.corda.demo.linearpointers.contracts

import com.github.opticyclic.corda.demo.linearpointers.states.MortgageState
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DummyCommandData
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.testng.annotations.Test

class MortgageContractTests {
    private val ledgerServices = MockServices(listOf("com.github.opticyclic.corda.demo.linearpointers.contracts"))
    private val megaCorpBank = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val owner = "Joe Bloggs"
    private val mortgageAmount = 500000L
    private val rate = 5.5
    private val termInYears = 25

    @Test
    fun `transaction must include Create command`() {
        ledgerServices.ledger {
            transaction {
                output(MortgageContract.MORTGAGE_CONTRACT_ID, MortgageState(megaCorpBank.party, owner, mortgageAmount, rate, termInYears))
                command(listOf(megaCorpBank.publicKey), DummyCommandData)
                fails()
            }
        }
        ledgerServices.ledger {
            transaction {
                output(MortgageContract.MORTGAGE_CONTRACT_ID, MortgageState(megaCorpBank.party, owner, mortgageAmount, rate, termInYears))
                command(listOf(megaCorpBank.publicKey), MortgageContract.Commands.Create())
                verifies()
            }
        }
    }

    @Test
    fun `transaction must have no inputs`() {
        ledgerServices.ledger {
            transaction {
                input(MortgageContract.MORTGAGE_CONTRACT_ID, MortgageState(megaCorpBank.party, owner, mortgageAmount, rate, termInYears))
                output(MortgageContract.MORTGAGE_CONTRACT_ID, MortgageState(megaCorpBank.party, owner, mortgageAmount, rate, termInYears))
                command(listOf(megaCorpBank.publicKey), MortgageContract.Commands.Create())
                `fails with`("No inputs should be consumed when issuing a mortgage.")
            }
        }
    }

    @Test
    fun `transaction must have one output`() {
        ledgerServices.ledger {
            transaction {
                output(MortgageContract.MORTGAGE_CONTRACT_ID, MortgageState(megaCorpBank.party, owner, mortgageAmount, rate, termInYears))
                output(MortgageContract.MORTGAGE_CONTRACT_ID, MortgageState(megaCorpBank.party, owner, mortgageAmount, rate, termInYears))
                command(listOf(megaCorpBank.publicKey), MortgageContract.Commands.Create())
                `fails with`("Only one output state should be created.")
            }
        }
    }

    @Test
    fun `lender must sign transaction`() {
        ledgerServices.ledger {
            transaction {
                output(MortgageContract.MORTGAGE_CONTRACT_ID, MortgageState(megaCorpBank.party, owner, mortgageAmount, rate, termInYears))
                command(TestIdentity(DUMMY_BANK_A_NAME).publicKey, MortgageContract.Commands.Create())
                `fails with`("The lender must be a signer.")
            }
        }
        ledgerServices.ledger {
            transaction {
                output(MortgageContract.MORTGAGE_CONTRACT_ID, MortgageState(megaCorpBank.party, owner, mortgageAmount, rate, termInYears))
                command(listOf(megaCorpBank.publicKey, TestIdentity(DUMMY_BANK_A_NAME).publicKey), MortgageContract.Commands.Create())
                `fails with`("Only the lender can be a signer.")
            }
        }
        ledgerServices.ledger {
            transaction {
                output(MortgageContract.MORTGAGE_CONTRACT_ID, MortgageState(megaCorpBank.party, owner, mortgageAmount, rate, termInYears))
                command(megaCorpBank.publicKey, MortgageContract.Commands.Create())
                verifies()
            }
        }
    }

    @Test
    fun `cannot create negative-value mortgages`() {
        ledgerServices.ledger {
            transaction {
                output(MortgageContract.MORTGAGE_CONTRACT_ID, MortgageState(megaCorpBank.party, owner, -300000, rate, termInYears))
                command(listOf(megaCorpBank.publicKey), MortgageContract.Commands.Create())
                `fails with`("The mortgage amount must be non-negative.")
            }
        }
    }
}
