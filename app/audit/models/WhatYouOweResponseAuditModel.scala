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

package audit.models

import audit.Utilities._
import auth.MtdItUser
import models.financialDetails.{DocumentDetail, DocumentDetailWithDueDate, WhatYouOweChargesList}
import models.outstandingCharges.OutstandingChargesModel
import play.api.libs.json._
import utils.Utilities.JsonUtil

case class WhatYouOweResponseAuditModel(user: MtdItUser[_],
                                        chargesList: WhatYouOweChargesList) extends ExtendedAuditModel {

  override val transactionName: String = "what-you-owe-response"
  override val auditType: String = "WhatYouOweResponse"

  private val docDetailsListJson: List[JsObject] =
    chargesList.allCharges.map(documentDetails) ++
      chargesList.overduePaymentList
        .filter(_.documentDetail.latePaymentInterestAmount.isDefined)
        .map(latePaymentInterestDetails) ++
      chargesList.outstandingChargesModel.map(outstandingChargeDetails)

  override val detail: JsValue = userAuditDetails(user) ++
    balanceDetailsJson ++
    Json.obj("charges" -> docDetailsListJson)


  private lazy val balanceDetailsJson: JsObject = {
    def onlyIfPositive(amount: BigDecimal): Option[BigDecimal] = Some(amount).filter(_ > 0)

    lazy val fields: JsObject = Json.obj() ++
      ("balanceDueWithin30Days", onlyIfPositive(chargesList.balanceDetails.balanceDueWithin30Days)) ++
      ("overDueAmount", onlyIfPositive(chargesList.balanceDetails.overDueAmount)) ++
      ("totalBalance", onlyIfPositive(chargesList.balanceDetails.totalBalance))

    val currentTaxYear = user.incomeSources.getCurrentTaxEndYear
    val secondOrMoreYearOfMigration = user.incomeSources.yearOfMigration.exists(currentTaxYear > _.toInt)

    if (secondOrMoreYearOfMigration && fields.values.nonEmpty) Json.obj("balanceDetails" -> fields)
    else Json.obj()
  }

  private def documentDetails(docDateDetail: DocumentDetailWithDueDate): JsObject = Json.obj(
    "chargeUnderReview" -> docDateDetail.dunningLock,
    "outstandingAmount" -> docDateDetail.documentDetail.remainingToPay
  ) ++
    ("chargeType", getChargeType(docDateDetail.documentDetail)) ++
    ("dueDate", docDateDetail.dueDate) ++
    accruingInterestJson(docDateDetail.documentDetail)

  private def accruingInterestJson(documentDetail: DocumentDetail): JsObject = {
    if (documentDetail.hasAccruingInterest) {
      Json.obj() ++
        ("accruingInterest", documentDetail.interestOutstandingAmount) ++
        ("interestRate", documentDetail.interestRate.map(ratePctString)) ++
        ("interestFromDate", documentDetail.interestFromDate) ++
        ("interestEndDate", documentDetail.interestEndDate)
    } else {
      Json.obj()
    }
  }

  private def latePaymentInterestDetails(docDateDetail: DocumentDetailWithDueDate): JsObject = Json.obj(
    "chargeUnderReview" -> docDateDetail.dunningLock,
    "outstandingAmount" -> docDateDetail.documentDetail.interestRemainingToPay
  ) ++
    ("chargeType", getChargeType(docDateDetail.documentDetail, latePaymentCharge = true)) ++
    ("dueDate", docDateDetail.documentDetail.interestEndDate)

  private def outstandingChargeDetails(outstandingCharge: OutstandingChargesModel) = Json.obj(
    "chargeType" -> "Remaining balance"
  ) ++
    ("outstandingAmount", outstandingCharge.bcdChargeType.map(_.chargeAmount)) ++
    ("dueDate", outstandingCharge.bcdChargeType.map(_.relevantDueDate)) ++
    ("accruingInterest", outstandingCharge.aciChargeType.map(_.chargeAmount))

}
