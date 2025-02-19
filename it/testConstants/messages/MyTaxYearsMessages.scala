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

package testConstants.messages

object MyTaxYearsMessages {

  val agentTitle = "Tax years - Your client’s Income Tax details - GOV.UK"

  val taxYearsTitle = "Tax years - Business Tax account - GOV.UK"
  val viewTaxYears = "Below is a list of tax years you have submitted updates for. Click on the tax years to view a summary."
  val myTaxYearsLink: Int => String = year => s"${year - 1} to $year"
  val noEstimates = "You don’t have an estimate right now. We’ll show your next Income Tax estimate when you submit a report using software."

}
