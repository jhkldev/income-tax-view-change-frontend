/*
 * Copyright 2021 HM Revenue & Customs
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

package models.calculation

import org.scalatest.{MustMatchers, WordSpec}

class CalcOverviewSpec extends WordSpec with MustMatchers {

  val calculation: Calculation = Calculation(
    crystallised = true,
    timestamp = Some("testTimestamp"),
    totalIncomeReceived = Some(1.01),
    totalTaxableIncome = Some(2.02),
    totalIncomeTaxAndNicsDue = Some(3.03),
    allowancesAndDeductions = AllowancesAndDeductions(
      totalAllowancesAndDeductions = Some(4.04),
      totalReliefs = Some(5.05)
    )
  )

  val emptyCalculation: Calculation = Calculation(crystallised = true)

  "CalcOverview" must {
    "be created with the correct data using a calculation" when {
      "the calculation has data" in {
        val overview: CalcOverview = CalcOverview(calculation)

        overview.income mustBe calculation.totalIncomeReceived.get
        overview.deductions mustBe calculation.allowancesAndDeductions.totalAllowancesDeductionsReliefs.get
        overview.totalTaxableIncome mustBe calculation.totalTaxableIncome.get
        overview.taxDue mustBe calculation.totalIncomeTaxAndNicsDue.get
        overview.payment mustBe 0.00
        overview.totalRemainingDue mustBe 3.03
      }
      "the calculation has no data" in {
        val overview: CalcOverview = CalcOverview(emptyCalculation)

        overview.income mustBe 0.00
        overview.deductions mustBe 0.00
        overview.totalTaxableIncome mustBe 0.00
        overview.taxDue mustBe 0.00
        overview.payment mustBe 0.00
        overview.totalRemainingDue mustBe 0.00
      }
    }
  }

}
