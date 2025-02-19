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

package services

import auth.MtdItUser
import connectors.IncomeTaxViewChangeConnector
import config.featureswitch.{CodingOut, FeatureSwitching}
import models.financialDetails.{BalanceDetails, DocumentDetailWithDueDate, FinancialDetailsErrorModel, FinancialDetailsModel, WhatYouOweChargesList}
import models.outstandingCharges.{OutstandingChargesErrorModel, OutstandingChargesModel}
import uk.gov.hmrc.http.HeaderCarrier
import models.financialDetails.DocumentDetail
import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import config.FrontendAppConfig

@Singleton
class WhatYouOweService @Inject()(val financialDetailsService: FinancialDetailsService,
                                  val incomeTaxViewChangeConnector: IncomeTaxViewChangeConnector)
                                 (implicit ec: ExecutionContext, implicit val appConfig: FrontendAppConfig) extends FeatureSwitching {

  implicit lazy val localDateOrdering: Ordering[LocalDate] = Ordering.by(_.toEpochDay)

	val validChargeTypeCondition: String => Boolean = documentDescription => {
		documentDescription == "ITSA- POA 1" ||
			documentDescription == "ITSA - POA 2" ||
			documentDescription == "TRM New Charge" ||
			documentDescription == "TRM Amend Charge"
	}

  def getWhatYouOweChargesList()(implicit headerCarrier: HeaderCarrier, mtdUser: MtdItUser[_]): Future[WhatYouOweChargesList] = {
    (financialDetailsService.getAllUnpaidFinancialDetails) flatMap {
      case financialDetails if financialDetails.exists(_.isInstanceOf[FinancialDetailsErrorModel]) =>
        throw new Exception("[WhatYouOweService][getWhatYouOweChargesList] Error response while getting Unpaid financial details")
      case financialDetails =>
        val financialDetailsModelList = financialDetails.asInstanceOf[List[FinancialDetailsModel]]
        val balanceDetails = financialDetailsModelList.headOption
          .map(_.balanceDetails).getOrElse(BalanceDetails(0.00, 0.00, 0.00))
        val codedOutDocumentDetail: Option[DocumentDetail] = if (isEnabled(CodingOut))
            financialDetailsModelList.flatMap(fdm =>
            fdm.documentDetails.filter(_.isCodingOut)
          ).headOption else None

        val whatYouOweChargesList = WhatYouOweChargesList(balanceDetails = balanceDetails,
          overduePaymentList = getOverduePaymentsList(financialDetailsModelList).filter(!_.documentDetail.isCodingOut),
          dueInThirtyDaysList = getDueWithinThirtyDaysList(financialDetailsModelList).filter(!_.documentDetail.isCodingOut),
          futurePayments = getFuturePaymentsList(financialDetailsModelList).filter(!_.documentDetail.isCodingOut),
          codedOutDocumentDetail = codedOutDocumentDetail)

        callOutstandingCharges(mtdUser.saUtr, mtdUser.incomeSources.yearOfMigration, mtdUser.incomeSources.getCurrentTaxEndYear).map {
          case Some(outstandingChargesModel) => whatYouOweChargesList.copy(outstandingChargesModel = Some(outstandingChargesModel))
          case _ => whatYouOweChargesList
        }
    }
  }

  private def callOutstandingCharges(saUtr: Option[String], yearOfMigration: Option[String], currentTaxYear: Int)
                                    (implicit headerCarrier: HeaderCarrier): Future[Option[OutstandingChargesModel]] = {
    if (saUtr.isDefined && yearOfMigration.isDefined && yearOfMigration.get.toInt >= currentTaxYear - 1) {
      val saPreviousYear = yearOfMigration.get.toInt - 1
      incomeTaxViewChangeConnector.getOutstandingCharges("utr", saUtr.get.toLong, saPreviousYear.toString) map {
        case outstandingChargesModel: OutstandingChargesModel => Some(outstandingChargesModel)
        case outstandingChargesErrorModel: OutstandingChargesErrorModel if outstandingChargesErrorModel.code == 404 => None
        case _ => throw new Exception("[WhatYouOweService][callOutstandingCharges] Error response while getting outstanding charges")
      }
    } else {
      Future.successful(None)
    }
  }

  private def whatYouOwePageDataExists(documentDetailWithDueDate: DocumentDetailWithDueDate): Boolean = {
    documentDetailWithDueDate.documentDetail.documentDescription.isDefined && documentDetailWithDueDate.dueDate.isDefined
  }

  private def getDueWithinThirtyDaysList(financialDetailsList: List[FinancialDetailsModel]): List[DocumentDetailWithDueDate] = {
    financialDetailsList.flatMap(financialDetails =>
      financialDetails.getAllDocumentDetailsWithDueDates.filter(documentDetailWithDueDate => whatYouOwePageDataExists(documentDetailWithDueDate)
        && validChargeTypeCondition(documentDetailWithDueDate.documentDetail.documentDescription.get)
        && documentDetailWithDueDate.documentDetail.remainingToPay > 0
        && LocalDate.now().isAfter(documentDetailWithDueDate.dueDate.get.minusDays(31))
        && LocalDate.now().isBefore(documentDetailWithDueDate.dueDate.get.plusDays(1)))).sortBy(_.dueDate.get)
  }

  private def getFuturePaymentsList(financialDetailsList: List[FinancialDetailsModel]): List[DocumentDetailWithDueDate] = {
    financialDetailsList.flatMap(financialDetails =>
      financialDetails.getAllDocumentDetailsWithDueDates.filter(documentDetailWithDueDate => whatYouOwePageDataExists(documentDetailWithDueDate)
				&& validChargeTypeCondition(documentDetailWithDueDate.documentDetail.documentDescription.get)
        && documentDetailWithDueDate.documentDetail.remainingToPay > 0
        && LocalDate.now().isBefore(documentDetailWithDueDate.dueDate.get.minusDays(30)))).sortBy(_.dueDate.get)
  }

  private def getOverduePaymentsList(financialDetailsList: List[FinancialDetailsModel]): List[DocumentDetailWithDueDate] = {
    financialDetailsList.flatMap{financialDetails =>
      financialDetails.getAllDocumentDetailsWithDueDates.filter(documentDetailWithDueDate => whatYouOwePageDataExists(documentDetailWithDueDate)
				&& validChargeTypeCondition(documentDetailWithDueDate.documentDetail.documentDescription.get)
        && documentDetailWithDueDate.documentDetail.checkIfEitherChargeOrLpiHasRemainingToPay
        && documentDetailWithDueDate.dueDate.get.isBefore(LocalDate.now()))}.sortBy(_.dueDate.get)
  }

}
