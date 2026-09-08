/*
 * Copyright 2026 HM Revenue & Customs
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

import config.FrontendAppConfig
import models.StrideAuthUser
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class StrideEnrolmentService @Inject() (config: FrontendAppConfig) extends StrideEnrolmentServiceAlgebra {

  // Set of SDEC enrolments
  private lazy val sdecSRSEnrolments = Enrolments(
    Set(
      Enrolment(config.strideRole)
    )
  )

  override def extractSdecEnrolments(enrolments: Enrolments): Enrolments = {
    val filtered =
      enrolments.enrolments.filter(enrol => enrol.key.toUpperCase.startsWith(config.sdecAccessPrefixMatch))
    Enrolments(filtered)
  }

  /** This method for now uses the config value, but it should use either the AD, LDAP or HCP database to verify that
    * the user has a matching SRS role
    * @param strideUser
    * @return
    */
  override def verifyEnrollment(strideUser: StrideAuthUser): Future[Boolean] =

    Future.successful(
      strideUser.enrolments.enrolments.exists(enrol =>
        sdecSRSEnrolments.enrolments.exists(_.key.equalsIgnoreCase(enrol.key))
      )
    )
}
