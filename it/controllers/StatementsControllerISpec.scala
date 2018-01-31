/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import helpers.IntegrationTestConstants.GetReportDeadlinesData.singleReportDeadlinesDataSuccessModel
import helpers.{ComponentSpecBase, GenericStubMethods}
import utils.ImplicitDateFormatter
import utils.ImplicitCurrencyFormatter._
import helpers.IntegrationTestConstants._
import helpers.servicemocks._
import play.api.http.Status.OK

class StatementsControllerISpec extends ComponentSpecBase with ImplicitDateFormatter with GenericStubMethods {

  "Calling the StatementsController" when {

    "isAuthorisedUser with an active enrolment" which {

      "has a single statement with a single charge" should {

        "display the tax year for the statement and the associated charge" in {

          isAuthorisedUser(true)

          stubUserDetails()

          getBizDeets(GetBusinessDetails.successResponse(testSelfEmploymentId))

          getPropDeets(GetPropertyDetails.successResponse())

          And("I wiremock stub a successful Get Financial Transactions response")
          val statementResponse = GetStatementsData.singleFinancialTransactionsModel
          FinancialTransactionsStub.stubGetFinancialTransactions(testNino, statementResponse)

          When(s"I call GET /report-quarterly/income-and-expenses/view/view-statement")
          val res = IncomeTaxViewChangeFrontend.getStatements

          Then("I verify the Financial Transactions response has been wiremocked")
          FinancialTransactionsStub.verifyGetFinancialTransactions(testNino)

          verifyBizDeetsCall()
          verifyPropDeetsCall()

          Then("The view should have the correct headings and single statement")
          val model = GetStatementsData.singleChargeTransactionModel
          res should have(
            httpStatus(OK),
            pageTitle("Income Tax Statement"),
            elementTextByID(s"statement-$testYear")(s"Tax year: ${testYearInt - 1}-$testYear"),
            elementTextByID(s"$testYear-tax-year")(s"Tax year: ${testYearInt - 1}-$testYear"),
            elementTextByID(s"$testYear-total")(model.originalAmount.get.toCurrencyString),
            elementTextByID(s"$testYear-still-to-pay")(s"Still to pay: ${model.outstandingAmount.get.toCurrencyString}"),
            elementTextByID(s"$testYear-charge")(GetStatementsData.charge2018.amount.get.toCurrencyString),
            isElementVisibleById(s"$testYear-paid-0")(false)
          )

        }

      }

      "has 2 statements - one with a single charge & one with a charge and 2 payments" should {

        "display the tax year for the statements and the associated charge & payments" in {

          isAuthorisedUser(true)

          stubUserDetails()

          getBizDeets(GetBusinessDetails.successResponse(testSelfEmploymentId))

          getPropDeets(GetPropertyDetails.successResponse())

          And("I wiremock stub a successful Get Financial Transactions response")
          val statementResponse = GetStatementsData.singleFTModel1charge2payments
          FinancialTransactionsStub.stubGetFinancialTransactions(testNino, statementResponse)

          When(s"I call GET /report-quarterly/income-and-expenses/view/view-statement")
          val res = IncomeTaxViewChangeFrontend.getStatements

          Then("I verify the Financial Transactions response has been wiremocked")
          FinancialTransactionsStub.verifyGetFinancialTransactions(testNino)

          verifyBizDeetsCall()
          verifyPropDeetsCall()

          Then("The view should have the correct headings and single statement")
          val statement1Model = GetStatementsData.singleChargeTransactionModel
          val statement2Model = GetStatementsData.singleCharge2PaymentsTransactionModel
          res should have(
            httpStatus(OK),
            pageTitle("Income Tax Statement"),
            elementTextByID(s"statement-$testYear")(s"Tax year: ${testYearInt - 1}-$testYear"),
            elementTextByID(s"statement-$testYearPlusOne")(s"Tax year: ${testYearPlusOneInt - 1}-$testYearPlusOne"),
            elementTextByID(s"$testYear-tax-year")(s"Tax year: ${testYearInt - 1}-$testYear"),
            elementTextByID(s"$testYear-total")(statement1Model.originalAmount.get.toCurrencyString),
            elementTextByID(s"$testYear-still-to-pay")(s"Still to pay: ${statement1Model.outstandingAmount.get.toCurrencyString}"),
            elementTextByID(s"$testYear-charge")(GetStatementsData.charge2018.amount.get.toCurrencyString),
            isElementVisibleById(s"$testYear-paid-0")(false)
          )

        }

      }

    }
  }

}
